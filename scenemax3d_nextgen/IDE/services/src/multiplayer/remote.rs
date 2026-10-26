//! SSH transport and immutable, checksummed Linux/systemd releases.
use super::{build, read};
use scenemax_ide_core::multiplayer::{RemoteDeployment, Settings};
use sha2::{Digest, Sha256};
use std::{
    collections::BTreeMap,
    fs,
    path::{Path, PathBuf},
    process::Command,
    sync::atomic::AtomicBool,
    time::{Duration, SystemTime, UNIX_EPOCH},
};

#[derive(Clone, Copy)]
/// Remote operation requested explicitly from the deployment tab.
pub enum RemoteAction {
    /// Check authentication and server prerequisites without installing anything.
    Test,
    /// Read the configured systemd service status.
    Status,
    /// Upload, verify and install a previously built bundle.
    Deploy,
}
fn digest(bytes: &[u8]) -> String {
    format!("{:x}", Sha256::digest(bytes))
}
fn build_settings(s: &Settings) -> serde_json::Value {
    let mut s = s.clone();
    s.deployment = RemoteDeployment::default();
    serde_json::to_value(s).expect("serializable settings")
}
fn payload(s: &Settings) -> Vec<&'static str> {
    let mut files = vec![if s.platform == "windows-x64" {
        "scenemax-mp-server.exe"
    } else {
        "scenemax-mp-server"
    }];
    if s.browser_gateway.enabled {
        files.extend([super::gateway::executable_name(&s.platform), "gateway.json"]);
    }
    files
}
pub(super) fn record_bundle(dir: &Path, s: &Settings) -> Result<(), String> {
    let hashes: BTreeMap<_, _> = payload(s)
        .into_iter()
        .map(|name| Ok((name, digest(&read(&dir.join(name), 64 * 1024 * 1024)?))))
        .collect::<Result<_, String>>()?;
    fs::write(
        dir.join("build-manifest.json"),
        serde_json::to_vec_pretty(
            &serde_json::json!({"settings":build_settings(s),"files":hashes}),
        )
        .map_err(|e| e.to_string())?,
    )
    .map_err(|e| e.to_string())
}
fn validate_bundle(dir: &Path, s: &Settings) -> Result<(), String> {
    if s.platform != "linux-x64" {
        return Err("Remote installation supports Linux x64 with systemd. Select linux-x64 on the Build tab and rebuild.".into());
    }
    let manifest: serde_json::Value =
        serde_json::from_slice(&read(&dir.join("build-manifest.json"), 1024 * 1024)?)
            .map_err(|e| e.to_string())?;
    if manifest["settings"] != build_settings(s) {
        return Err(
            "Settings changed since this bundle was built. Build again before deploying.".into(),
        );
    }
    for name in payload(s) {
        let bytes = read(&dir.join(name), 64 * 1024 * 1024)?;
        if manifest["files"][name].as_str() != Some(&digest(&bytes)) {
            return Err(format!(
                "Built file {name} changed. Rebuild before deploying."
            ));
        }
        if name != "gateway.json" && !bytes.starts_with(b"\x7fELF") {
            return Err(format!("{name} is not a Linux executable"));
        }
    }
    Ok(())
}
fn quote(s: &str) -> String {
    format!("'{}'", s.replace('\'', "'\"'\"'"))
}
fn tool(name: &str, putty: bool) -> PathBuf {
    if cfg!(windows) && putty {
        let path = PathBuf::from(
            std::env::var_os("ProgramFiles").unwrap_or_else(|| "C:\\Program Files".into()),
        )
        .join("PuTTY")
        .join(format!("{name}.exe"));
        if path.is_file() {
            return path;
        }
    }
    name.into()
}
fn transport(d: &RemoteDeployment, copy: bool) -> Command {
    let putty = d.method == "putty";
    let mut c = Command::new(tool(
        if putty {
            if copy { "pscp" } else { "plink" }
        } else if copy {
            "scp"
        } else {
            "ssh"
        },
        putty,
    ));
    if putty {
        c.arg("-batch");
        if copy {
            // PSCP loads the saved session from the target's host component.
            // Combining that with -load treats the session name as a DNS host.
            c.arg("-sftp");
        } else {
            c.args(["-load", &d.host, "-ssh"]);
        }
    } else {
        c.args([
            "-o",
            "BatchMode=yes",
            "-o",
            "StrictHostKeyChecking=yes",
            "-o",
            "ConnectTimeout=15",
            "-o",
            "ServerAliveInterval=15",
            "-o",
            "ServerAliveCountMax=2",
        ]);
    }
    if d.port != 0 {
        c.arg(if copy || putty { "-P" } else { "-p" })
            .arg(d.port.to_string());
    }
    if !d.user.is_empty() {
        if putty || !copy {
            c.args(["-l", &d.user]);
        } else {
            c.arg("-o").arg(format!("User={}", d.user));
        }
    }
    if !d.identity_file.is_empty() {
        c.arg("-i").arg(&d.identity_file);
    }
    c.env_remove("SCENEMAX_MULTIPLAYER_PASSWORD");
    c
}
fn invoke(c: &mut Command, cancel: &AtomicBool, label: &str) -> Result<String, String> {
    build::run_output(c, cancel, Duration::from_secs(180), label)?
        .map(|output| {
            let skip = output.len().saturating_sub(16 * 1024);
            let start = output.char_indices().find(|(i, _)| *i >= skip).map(|(i, _)| i).unwrap_or(0);
            output[start..].to_owned()
        })
        .ok_or_else(|| "SSH tools unavailable. Install OpenSSH client (ssh/scp) or PuTTY (plink/pscp). Load an encrypted key in ssh-agent/Pageant and trust the server host key in your SSH client first.".into())
}
fn ssh(d: &RemoteDeployment, script: &str, cancel: &AtomicBool) -> Result<String, String> {
    let mut c = transport(d, false);
    if d.method != "putty" {
        c.arg(&d.host);
    }
    c.arg(format!("sh -c {}", quote(script)));
    invoke(&mut c, cancel, "SSH")
}
fn privileged(d: &RemoteDeployment) -> &'static str {
    if d.sudo { "sudo -n " } else { "" }
}
fn preflight(d: &RemoteDeployment) -> String {
    format!(
        "set -eu; test \"$(uname -s)\" = Linux; test \"$(uname -m)\" = x86_64; command -v systemctl; command -v systemd-run; command -v sha256sum; command -v flock; command -v ss; test -d /run/systemd/system; {}sh -c 'test \"$(id -u)\" = 0'; printf 'SSH connection and Linux/systemd prerequisites verified\\n'",
        privileged(d)
    )
}
fn status_script(d: &RemoteDeployment) -> String {
    include_str!("remote_status.sh")
        .replace("@SERVER@", &quote(&format!("{}.service", d.service)))
        .replace("@GATEWAY@", &quote(&format!("{}-gateway.service", d.service)))
}
fn status_summary(output: &str) -> String {
    let current = output.split("----- HISTORICAL LOGS").next().unwrap_or("");
    let states: Vec<_> = current.lines().filter(|line| {
        line.starts_with("Zig UDP server:") || line.starts_with("Browser WebSocket gateway:")
    }).collect();
    if states.len() == 2 {
        states.join(" | ")
    } else {
        "Current service status unavailable. See the console for details.".into()
    }
}
pub(super) fn execute(
    s: &Settings,
    bundle: Option<&Path>,
    action: RemoteAction,
    cancel: &AtomicBool,
) -> Result<String, String> {
    let d = &s.deployment;
    d.validate()?;
    super::progress::stage(10, "Connecting over SSH");
    if !d.identity_file.is_empty() && !Path::new(&d.identity_file).is_file() {
        return Err("SSH identity file was not found on this computer".into());
    }
    match action {
        RemoteAction::Test => ssh(d, &preflight(d), cancel),
        RemoteAction::Status => ssh(d, &status_script(d), cancel).map(|output| status_summary(&output)),
        RemoteAction::Deploy => {
            super::progress::stage(15, "Validating Linux bundle");
            s.validate()?;
            let bundle = bundle.ok_or("Build a Linux server bundle on the Build tab first")?;
            validate_bundle(bundle, s)?;
            super::progress::stage(25, "Checking remote server requirements");
            ssh(d, &preflight(d), cancel)?;
            let id = format!(
                "{:x}-{:x}",
                SystemTime::now()
                    .duration_since(UNIX_EPOCH)
                    .map_err(|e| e.to_string())?
                    .as_nanos(),
                std::process::id()
            );
            let stage = format!("/tmp/scenemax-deploy-{id}");
            let scratch = tempfile::tempdir().map_err(|e| e.to_string())?;
            super::progress::stage(40, "Preparing service configuration and checksums");
            let mut names: Vec<String> = payload(s).into_iter().map(str::to_owned).collect();
            for name in &names {
                fs::write(
                    scratch.path().join(name),
                    read(&bundle.join(name), 64 * 1024 * 1024)?,
                )
                .map_err(|e| e.to_string())?;
            }
            fs::write(
                scratch.path().join("build-manifest.json"),
                read(&bundle.join("build-manifest.json"), 1024 * 1024)?,
            )
            .map_err(|e| e.to_string())?;
            validate_bundle(scratch.path(), s)?;
            for gateway in [false, true] {
                if gateway && !s.browser_gateway.enabled {
                    continue;
                }
                let name = if gateway {
                    "gateway.service"
                } else {
                    "server.service"
                };
                fs::write(scratch.path().join(name), unit(s, gateway))
                    .map_err(|e| e.to_string())?;
                names.push(name.into());
            }
            let sums = names
                .iter()
                .map(|name| {
                    Ok(format!(
                        "{}  {name}\n",
                        digest(&read(&scratch.path().join(name), 64 * 1024 * 1024)?)
                    ))
                })
                .collect::<Result<Vec<_>, String>>()?
                .concat();
            fs::write(scratch.path().join("SHA256SUMS"), sums).map_err(|e| e.to_string())?;
            fs::write(
                scratch.path().join("install.sh"),
                install_script(s, &stage, &id),
            )
            .map_err(|e| e.to_string())?;
            names.extend(["SHA256SUMS".into(), "install.sh".into()]);
            ssh(d, &format!("umask 077; mkdir {}", quote(&stage)), cancel)?;
            let transfer = (|| {
                let mut c = transport(d, true);
                for name in &names {
                    c.arg(scratch.path().join(name));
                }
                let host = if d.host.contains(':') {
                    format!("[{}]", d.host)
                } else {
                    d.host.clone()
                };
                c.arg(format!("{host}:{stage}/"));
                super::progress::stage(55, "Uploading server bundle");
                invoke(&mut c, cancel, "SSH upload")?;
                super::progress::stage(75, "Installing and starting services; checking health");
                // A transient systemd unit owns installation, even if the SSH connection drops.
                ssh(
                    d,
                    &format!(
                        "{}systemd-run --quiet --wait --pipe --collect --unit=scenemax-deploy-{id} /bin/sh {stage}/install.sh",
                        privileged(d)
                    ),
                    cancel,
                )
            })();
            match transfer {
                Ok(output) => Ok(format!("Deployment completed.\n{output}")),
                Err(error) => Err(format!(
                    "{error}\nIf installation started, it continues safely as scenemax-deploy-{id}. Use Check status before retrying. Staging: {stage}"
                )),
            }
        }
    }
}
fn unit(s: &Settings, gateway: bool) -> String {
    let d = &s.deployment;
    let name = if gateway {
        "scenemax_web_gateway"
    } else {
        "scenemax-mp-server"
    };
    let env = if gateway {
        String::new()
    } else {
        format!(
            "Environment=SCENEMAX_MP_BIND_ADDRESS=0.0.0.0\nEnvironment=SCENEMAX_MP_PORT={}\n",
            s.port
        )
    };
    format!(
        "# SceneMax managed {}\n[Unit]\nDescription=SceneMax {}\nAfter=network.target\n[Service]\nType=simple\nDynamicUser=yes\nWorkingDirectory={}/current\nExecStart={}/current/{name}\n{env}Restart=on-failure\nRestartSec=2\nNoNewPrivileges=yes\nProtectSystem=strict\nProtectHome=yes\nPrivateTmp=yes\nAmbientCapabilities=CAP_NET_BIND_SERVICE\n[Install]\nWantedBy=multi-user.target\n",
        digest(s.project_guid.as_bytes()),
        if gateway {
            "browser gateway"
        } else {
            "multiplayer server"
        },
        d.directory,
        d.directory
    )
}
fn install_script(s: &Settings, stage: &str, id: &str) -> String {
    let d = &s.deployment;
    include_str!("remote_install.sh")
        .replace("\r\n", "\n")
        .replace("@ROOT@", &quote(&d.directory))
        .replace("@STAGE@", &quote(stage))
        .replace("@SERVICE@", &quote(&d.service))
        .replace("@PROJECT@", &quote(&digest(s.project_guid.as_bytes())))
        .replace("@RELEASE@", &quote(id))
        .replace("@UDP_PORT@", &s.port.to_string())
        .replace(
            "@TCP_PORT@",
            &s.browser_gateway
                .listen_address()
                .map(|a| a.port())
                .unwrap_or(0)
                .to_string(),
        )
        .replace(
            "@GATEWAY@",
            if s.browser_gateway.enabled { "1" } else { "0" },
        )
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn status_banner_contains_both_current_states_and_never_historical_errors() {
        let output = "CURRENT SERVICE STATUS\nZig UDP server: RUNNING\nBrowser WebSocket gateway: STOPPED\n----- HISTORICAL LOGS\nZig UDP server: FAILED\nold protocol error\n";
        assert_eq!(status_summary(output), "Zig UDP server: RUNNING | Browser WebSocket gateway: STOPPED");
        assert!(status_summary("unexpected output").contains("unavailable"));
    }
    fn settings() -> Settings {
        let mut s = Settings::default();
        s.project_guid = "fixture-project".into();
        s.platform = "linux-x64".into();
        s.browser_gateway.enabled = true;
        s.deployment.host = "test-host".into();
        s
    }
    #[test]
    fn bundle_rejects_stale_settings_and_tampering_but_allows_connection_changes() {
        let root = tempfile::tempdir().unwrap();
        let mut s = settings();
        for name in payload(&s) {
            fs::write(
                root.path().join(name),
                if name == "gateway.json" {
                    b"{}".as_slice()
                } else {
                    b"\x7fELFfixture".as_slice()
                },
            )
            .unwrap();
        }
        record_bundle(root.path(), &s).unwrap();
        validate_bundle(root.path(), &s).unwrap();
        s.deployment.host = "another-host".into();
        validate_bundle(root.path(), &s).unwrap();
        s.port += 1;
        assert!(
            validate_bundle(root.path(), &s)
                .unwrap_err()
                .contains("Settings changed")
        );
        s.port -= 1;
        fs::write(root.path().join("scenemax_web_gateway"), b"\x7fELFchanged").unwrap();
        assert!(
            validate_bundle(root.path(), &s)
                .unwrap_err()
                .contains("changed")
        );
    }
    #[test]
    fn ssh_arguments_keep_auth_and_host_verification_consistent() {
        let mut d = settings().deployment;
        d.identity_file = "C:/keys/a key.pem".into();
        d.user = "deploy".into();
        d.port = 2222;
        for copy in [false, true] {
            let c = transport(&d, copy);
            let args: Vec<_> = c
                .get_args()
                .map(|s| s.to_string_lossy().into_owned())
                .collect();
            assert!(args.contains(&"StrictHostKeyChecking=yes".into()));
            assert!(args.contains(&"BatchMode=yes".into()));
            assert!(args.contains(&d.identity_file));
            assert!(args.contains(&"2222".into()));
        }
        d.method = "putty".into();
        d.host = "saved session".into();
        for copy in [false, true] {
            let c = transport(&d, copy);
            let args: Vec<_> = c
                .get_args()
                .map(|s| s.to_string_lossy().into_owned())
                .collect();
            assert_eq!(
                args.windows(2).any(|a| a == ["-load", "saved session"]),
                !copy
            );
            assert_eq!(args.contains(&"-sftp".into()), copy);
            assert!(args.contains(&"-batch".into()));
            assert!(!args.contains(&"-pw".into()));
        }
    }
    #[test]
    fn installation_uses_generic_units_checksums_and_rollback() {
        let s = settings();
        let script = install_script(&s, "/tmp/scenemax-deploy-fixture", "fixture");
        assert!(!script.contains('@'));
        assert!(!script.contains('\r'));
        assert!(script.contains("sha256sum -c"));
        assert!(script.contains("trap rollback EXIT"));
        assert!(script.contains("sport = :9001"));
        assert!(script.contains("sport = :9002"));
        let udp = unit(&s, false);
        let gateway = unit(&s, true);
        assert!(udp.contains("DynamicUser=yes"));
        assert!(gateway.contains("/current/scenemax_web_gateway"));
        assert!(!udp.contains("test-host"));
    }
    #[test]
    #[ignore = "explicit opt-in: deploys the supplied project's bundle to its configured server"]
    fn deploy_configured_bundle() {
        let root = std::path::PathBuf::from(
            std::env::var("SCENEMAX_TEST_DEPLOY_PROJECT").expect("project"),
        );
        let bundle =
            std::path::PathBuf::from(std::env::var("SCENEMAX_TEST_DEPLOY_BUNDLE").expect("bundle"));
        let snapshot = super::super::load(&root).unwrap();
        let result = execute(
            &snapshot.settings,
            Some(&bundle),
            RemoteAction::Deploy,
            &AtomicBool::new(false),
        )
        .unwrap();
        println!("{result}");
        let status = execute(
            &snapshot.settings,
            None,
            RemoteAction::Status,
            &AtomicBool::new(false),
        )
        .unwrap();
        println!("{status}");
        assert!(status.contains("Zig UDP server: RUNNING"));
        assert!(status.contains("Browser WebSocket gateway: RUNNING"));
    }
    #[test]
    #[ignore = "requires configured SSH client/session; read-only remote preflight"]
    fn real_ssh_preflight() {
        let mut s = settings();
        s.deployment.method = std::env::var("SCENEMAX_TEST_SSH_METHOD").unwrap_or("openssh".into());
        s.deployment.host = std::env::var("SCENEMAX_TEST_SSH_HOST").expect("test SSH host/session");
        let result = execute(&s, None, RemoteAction::Test, &AtomicBool::new(false)).unwrap();
        assert!(result.contains("prerequisites verified"));
    }
    #[test]
    #[ignore = "requires Zig and Rust; builds actual Linux cloud bundle"]
    fn linux_cloud_bundle_builds_and_validates() {
        let project = tempfile::tempdir().unwrap();
        let mut snapshot = super::super::load(project.path()).unwrap();
        snapshot.settings.platform = "linux-x64".into();
        snapshot.settings.browser_gateway.enabled = true;
        let report = super::super::run(
            project.path(),
            super::super::Operation::BuildWithPassword(snapshot, String::new()),
            &AtomicBool::new(false),
        )
        .unwrap();
        let path = report.server.unwrap();
        validate_bundle(path.parent().unwrap(), &report.snapshot.settings).unwrap();
    }
}
