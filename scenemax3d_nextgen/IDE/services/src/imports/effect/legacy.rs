//! Official offline conversion of authoring projects into native runtime data.
use super::*;
use std::process::{Command, Stdio};
use std::time::{Duration, Instant};

// The legacy .NET editor does not understand Rust's Windows verbatim paths.
fn tool_path(path: &Path) -> PathBuf {
    let text = path.to_string_lossy();
    if let Some(rest) = text.strip_prefix(r"\\?\UNC\") {
        PathBuf::from(format!(r"\\{rest}"))
    } else if let Some(rest) = text.strip_prefix(r"\\?\") {
        PathBuf::from(rest)
    } else {
        path.to_path_buf()
    }
}

fn executable() -> io::Result<PathBuf> {
    if let Some(path) = std::env::var_os("SCENEMAX_EFFEKSEER_TOOL") {
        let path = PathBuf::from(path);
        return path
            .canonicalize()
            .map_err(|e| error(format!("SCENEMAX_EFFEKSEER_TOOL is not accessible: {e}")));
    }
    let mut roots = vec![PathBuf::from(env!("CARGO_MANIFEST_DIR"))];
    if let Ok(path) = std::env::current_exe() {
        roots.push(path);
    }
    if let Ok(path) = std::env::current_dir() {
        roots.push(path);
    }
    for root in roots {
        for parent in root.ancestors() {
            let folder = parent.join("tools");
            if let Ok(entries) = fs::read_dir(folder) {
                let mut paths: Vec<_> = entries.filter_map(Result::ok).map(|e| e.path()).collect();
                paths.sort();
                for path in paths.into_iter().rev() {
                    if !path
                        .file_name()
                        .is_some_and(|n| n.to_string_lossy().starts_with("Effekseer"))
                    {
                        continue;
                    }
                    let exe = path.join("Tool/Effekseer.exe");
                    if exe.is_file() {
                        return exe.canonicalize();
                    }
                }
            }
        }
    }
    if let Some(paths) = std::env::var_os("PATH") {
        for path in std::env::split_paths(&paths) {
            let exe = path.join(if cfg!(windows) {
                "Effekseer.exe"
            } else {
                "Effekseer"
            });
            if exe.is_file() {
                return exe.canonicalize();
            }
        }
    }
    Err(error(
        "Legacy .efkproj conversion needs the official Effekseer tool. Set SCENEMAX_EFFEKSEER_TOOL to its executable, or export a same-named .efkefc beside the project, then reload.",
    ))
}

pub(super) fn export(source: &Path, output: &Path) -> io::Result<PathBuf> {
    let tool = match executable() {
        Ok(tool) => tool,
        Err(e) => {
            let modified = fs::metadata(source)?.modified()?;
            for companion in [
                source.with_extension("efkefc"),
                source.with_extension("efk"),
            ] {
                if fs::metadata(&companion)
                    .is_ok_and(|m| m.is_file() && m.modified().is_ok_and(|t| t >= modified))
                {
                    return Ok(companion);
                }
            }
            return Err(e);
        }
    };
    if fs::metadata(source)?.len() > 32 * 1024 * 1024 {
        return Err(error("Effect project exceeds 32 MiB"));
    }
    let destination = output
        .join(
            source
                .file_name()
                .ok_or_else(|| error("Missing effect name"))?,
        )
        .with_extension("efk");
    let tool = tool_path(&tool);
    let mut command = Command::new(&tool);
    // Export directly: saving with -o would rebase resource paths to the temporary folder.
    command
        .arg("-cui")
        .arg("-in")
        .arg(tool_path(source))
        .arg("-e")
        .arg(tool_path(&destination))
        .current_dir(tool_path(output))
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null());
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x08000000);
    }
    let mut child = command.spawn()?;
    let started = Instant::now();
    loop {
        match child.try_wait() {
            Ok(Some(status)) => {
                if !status.success() || !destination.is_file() {
                    return Err(error(format!(
                        "Effekseer could not export this project ({status}). Open it in Effekseer to inspect compatibility or missing resources."
                    )));
                }
                return Ok(destination);
            }
            Ok(None) if started.elapsed() < Duration::from_secs(180) => {
                std::thread::sleep(Duration::from_millis(50))
            }
            result => {
                let _ = child.kill();
                let _ = child.wait();
                return Err(match result {
                    Err(e) => e,
                    _ => error("Effekseer export timed out after 180 seconds"),
                });
            }
        }
    }
}
