//! Player-supplied connection overrides beside the distribution executable.
use super::*;
use std::io::{ErrorKind, Read};

#[derive(serde::Deserialize)]
#[serde(deny_unknown_fields)]
struct Config {
    host: String,
    port: u16,
    project_guid: Option<String>,
    session_id: Option<u32>,
    session_name: Option<String>,
}

fn load(executable: &Path) -> Result<HashMap<String, String>, String> {
    let path = executable
        .parent()
        .ok_or("Executable has no parent folder")?
        .join("network.json");
    let file = match fs::File::open(&path) {
        Ok(file) => file,
        Err(error) if error.kind() == ErrorKind::NotFound => return Ok(HashMap::new()),
        Err(error) => return Err(format!("{}: {error}", path.display())),
    };
    let parse = || -> Result<HashMap<String, String>, String> {
        let mut bytes = Vec::new();
        file.take(16385)
            .read_to_end(&mut bytes)
            .map_err(|error| error.to_string())?;
        if bytes.len() > 16384 {
            return Err("Configuration exceeds 16 KiB".into());
        }
        // Accept a UTF-8 BOM from Windows text editors.
        let bytes = bytes.strip_prefix(&[0xef, 0xbb, 0xbf]).unwrap_or(&bytes);
        let config: Config = serde_json::from_slice(bytes).map_err(|error| error.to_string())?;
        let host = config.host.trim();
        if host.is_empty()
            || host.len() > 253
            || host.chars().any(char::is_whitespace)
            || host.contains(['\0', '/', '\\'])
            || (host.contains(':') && host.parse::<std::net::Ipv6Addr>().is_err())
        {
            return Err(
                "host must be a hostname or IP address, without a URL scheme or port".into(),
            );
        }
        if config.port == 0 {
            return Err("port must be 1–65535".into());
        }
        let mut values = HashMap::from([
            ("SCENEMAX_MULTIPLAYER_SERVER".into(), host.to_owned()),
            ("SCENEMAX_MULTIPLAYER_PORT".into(), config.port.to_string()),
        ]);
        for (key, value) in [
            ("PROJECT_GUID", config.project_guid),
            ("SESSION_NAME", config.session_name),
        ] {
            if let Some(value) = value {
                if value.trim().is_empty()
                    || value.len() >= 64
                    || value.contains(['\0', '\r', '\n'])
                {
                    return Err(format!(
                        "{key} must contain 1–63 UTF-8 bytes without line breaks"
                    ));
                }
                values.insert(format!("SCENEMAX_MULTIPLAYER_{key}"), value);
            }
        }
        if let Some(id) = config.session_id {
            values.insert("SCENEMAX_MULTIPLAYER_SESSION_ID".into(), id.to_string());
        }
        Ok(values)
    };
    parse().map_err(|error| format!("{}: {error}", path.display()))
}

pub(super) fn settings(
    project: Option<&Path>,
    script: Option<&Path>,
    defaults: Option<&str>,
    executable: &Path,
    environment: impl Fn(&str) -> Option<String>,
) -> Result<Option<(String, u16, Login)>, String> {
    let overrides = load(executable)?;
    super::configured_settings(project, script, defaults, |name| {
        overrides.get(name).cloned().or_else(|| environment(name))
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn executable_sidecar_overrides_packaged_metadata_and_keeps_login_identity() {
        let distribution = tempfile::tempdir().unwrap();
        let extracted = tempfile::tempdir().unwrap();
        let exe = distribution.path().join("game.exe");
        let script = extracted.path().join("main");
        fs::write(&script, "//$[multiplayer_server]=embedded;\n//$[multiplayer_port]=9001;\n//$[project_guid]=game-guid;\n//$[multiplayer_session_id]=1000;\n//$[multiplayer_session_name]=game-room;\n").unwrap();
        // Neither the extraction folder nor its project settings choose the sidecar.
        fs::write(
            extracted.path().join("network.json"),
            r#"{"host":"wrong","port":1}"#,
        )
        .unwrap();
        let resolve = || {
            settings(Some(extracted.path()), Some(&script), None, &exe, |_| None)
                .unwrap()
                .unwrap()
        };
        assert_eq!(resolve().0, "embedded");
        fs::write(
            distribution.path().join("network.json"),
            "\u{feff}{\"host\":\"127.0.0.1\",\"port\":9999}",
        )
        .unwrap();
        let (host, port, login) = resolve();
        assert_eq!((host.as_str(), port), ("127.0.0.1", 9999));
        assert_eq!(login.project_guid, "game-guid");
        assert_eq!(login.session_id, 1000);
        assert_eq!(login.session_name, "game-room");
        fs::write(distribution.path().join("network.json"), r#"{"host":"::1","port":9002,"project_guid":"private-guid","session_id":42,"session_name":"Private room"}"#).unwrap();
        let (host, port, login) =
            settings(Some(extracted.path()), Some(&script), None, &exe, |key| {
                (key == "SCENEMAX_MULTIPLAYER_SERVER").then(|| "environment".into())
            })
            .unwrap()
            .unwrap();
        assert_eq!((host.as_str(), port), ("::1", 9002));
        assert_eq!(
            (
                login.project_guid.as_str(),
                login.session_id,
                login.session_name.as_str()
            ),
            ("private-guid", 42, "Private room")
        );
    }

    #[test]
    fn invalid_sidecar_is_rejected_instead_of_connecting_to_embedded_server() {
        let dir = tempfile::tempdir().unwrap();
        let exe = dir.path().join("game.exe");
        for source in [
            "not json",
            "{}",
            r#"{"host":"x","port":0}"#,
            r#"{"host":"x","port":65536}"#,
            r#"{"host":"x","port":"9001"}"#,
            r#"{"host":" ","port":9001}"#,
            r#"{"host":"https://x","port":9001}"#,
            r#"{"host":"localhost:9001","port":9001}"#,
            r#"{"host":"x","port":9001,"session_id":-1}"#,
            r#"{"host":"x","port":9001,"project_guid":""}"#,
            r#"{"host":"x","port":9001,"platform":"linux"}"#,
        ] {
            fs::write(dir.path().join("network.json"), source).unwrap();
            let error = settings(None, None, None, &exe, |_| Some("embedded".into())).unwrap_err();
            assert!(error.contains("network.json"), "{error}");
        }
        fs::write(dir.path().join("network.json"), vec![b' '; 16385]).unwrap();
        assert!(load(&exe).unwrap_err().contains("16 KiB"));
    }
}
