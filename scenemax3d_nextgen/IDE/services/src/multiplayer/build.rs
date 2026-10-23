//! Source builds run only in the settings worker and own only their compiler.
use super::read;
use std::{
    path::Path,
    process::{Command, Stdio},
    sync::atomic::{AtomicBool, Ordering},
    time::{Duration, Instant},
};

pub(super) fn check(cancel: &AtomicBool) -> Result<(), String> {
    if cancel.load(Ordering::Relaxed) {
        Err("Multiplayer build cancelled".into())
    } else {
        Ok(())
    }
}

pub(super) fn prepare(
    installation: &Path,
    platform: &str,
    cancel: &AtomicBool,
) -> Result<(Vec<u8>, &'static str), String> {
    let compiler = std::env::var_os("SCENEMAX_ZIG").unwrap_or_else(|| "zig".into());
    prepare_with(installation, platform, Path::new(&compiler), cancel)
}

fn prepare_with(
    installation: &Path,
    platform: &str,
    compiler: &Path,
    cancel: &AtomicBool,
) -> Result<(Vec<u8>, &'static str), String> {
    check(cancel)?;
    let target = match platform {
        "windows-x64" => "x86_64-windows",
        "linux-x64" => "x86_64-linux",
        "macos-x64" => "x86_64-macos",
        _ => return Err("Unsupported multiplayer server platform".into()),
    };
    let executable = if platform == "windows-x64" {
        "scenemax-mp-server.exe"
    } else {
        "scenemax-mp-server"
    };
    let server = installation.join("tools/multiplayer-server");
    let source = server.join("zig/scenemax_multiplayer_server.zig");
    if source.is_file() {
        let scratch = tempfile::tempdir().map_err(|e| e.to_string())?;
        let output = scratch.path().join(executable);
        let mut emit = std::ffi::OsString::from("-femit-bin=");
        emit.push(&output);
        let mut command = Command::new(compiler);
        command
            .args(["build-exe"])
            .arg(&source)
            .args(["-O", "ReleaseFast", "-target", target])
            .arg(emit)
            .arg("--cache-dir")
            .arg(scratch.path().join("cache"))
            .current_dir(&server)
            .env_remove("SCENEMAX_MULTIPLAYER_PASSWORD");
        if compile(&mut command, cancel, Duration::from_secs(300))? {
            return Ok((read(&output, 32 * 1024 * 1024)?, "Compiled from Zig source"));
        }
    }
    check(cancel)?;
    let template = server.join("bin").join(platform).join(executable);
    Ok((
        read(&template, 32 * 1024 * 1024)
            .map_err(|e| format!("Zig/source unavailable and prebuilt server unavailable: {e}"))?,
        "Used prebuilt template (Zig/source unavailable)",
    ))
}

// A temporary output file avoids pipe deadlocks. Size and time are checked on
// every poll; only the final bounded diagnostic is presented to the user.
fn compile(command: &mut Command, cancel: &AtomicBool, timeout: Duration) -> Result<bool, String> {
    check(cancel)?;
    let log = tempfile::NamedTempFile::new().map_err(|e| e.to_string())?;
    let stream = log.reopen().map_err(|e| e.to_string())?;
    command
        .stdin(Stdio::null())
        .stdout(Stdio::from(stream.try_clone().map_err(|e| e.to_string())?))
        .stderr(Stdio::from(stream));
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x08000000);
    }
    #[cfg(unix)]
    {
        use std::os::unix::process::CommandExt;
        command.process_group(0);
    }
    let mut child = match command.spawn() {
        Ok(child) => child,
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => return Ok(false),
        Err(e) => return Err(format!("Could not start Zig: {e}")),
    };
    let started = Instant::now();
    let result = (|| {
        loop {
            check(cancel)?;
            if started.elapsed() >= timeout {
                return Err("Zig build exceeded its five-minute timeout".into());
            }
            if log.as_file().metadata().map_err(|e| e.to_string())?.len() > 4 * 1024 * 1024 {
                return Err("Zig output exceeded 4 MiB".into());
            }
            if let Some(status) = child.try_wait().map_err(|e| e.to_string())? {
                return if status.success() {
                    Ok(true)
                } else {
                    let bytes = read(log.path(), 4 * 1024 * 1024)?;
                    let tail = &bytes[bytes.len().saturating_sub(16384)..];
                    Err(format!(
                        "Zig build failed ({status}). Install Zig 0.16.x.\n{}",
                        String::from_utf8_lossy(tail)
                    ))
                };
            }
            std::thread::sleep(Duration::from_millis(50));
        }
    })();
    if result.is_err() && child.try_wait().ok().flatten().is_none() {
        #[cfg(windows)]
        {
            use std::os::windows::process::CommandExt;
            let _ = Command::new("taskkill")
                .args(["/PID", &child.id().to_string(), "/T", "/F"])
                .creation_flags(0x08000000)
                .stdout(Stdio::null())
                .stderr(Stdio::null())
                .status();
        }
        #[cfg(unix)]
        {
            let _ = Command::new("kill")
                .args(["-TERM", "--", &format!("-{}", child.id())])
                .status();
        }
        let _ = child.kill();
    }
    let _ = child.wait();
    result
}

#[cfg(test)]
mod tests {
    use super::*;
    fn fixture(mode: &str) -> Command {
        let mut command = Command::new(std::env::current_exe().unwrap());
        command
            .args([
                "--exact",
                "multiplayer::build::tests::compiler_process_fixture",
                "--ignored",
                "--nocapture",
            ])
            .env("SCENEMAX_COMPILER_TEST_MODE", mode);
        command
    }

    #[test]
    #[ignore = "owned subprocess fixture invoked by compiler lifecycle tests"]
    fn compiler_process_fixture() {
        match std::env::var("SCENEMAX_COMPILER_TEST_MODE").as_deref() {
            Ok("wait") => std::thread::sleep(Duration::from_secs(30)),
            Ok("fail") => std::process::exit(7),
            Ok("output") => {
                use std::io::Write;
                let mut output = std::io::stdout().lock();
                for _ in 0..2048 {
                    output.write_all(&[b'x'; 4096]).unwrap();
                }
                std::thread::sleep(Duration::from_secs(30));
            }
            _ => {} // A standalone --ignored run has no fixture work to perform.
        }
    }

    #[test]
    fn compiler_failure_timeout_output_limit_and_active_cancellation_are_bounded() {
        let cancel = std::sync::Arc::new(AtomicBool::new(false));
        assert!(
            compile(&mut fixture("fail"), &cancel, Duration::from_secs(5))
                .unwrap_err()
                .contains("Zig build failed")
        );
        assert!(
            compile(&mut fixture("wait"), &cancel, Duration::from_millis(150))
                .unwrap_err()
                .contains("timeout")
        );
        assert!(
            compile(&mut fixture("output"), &cancel, Duration::from_secs(5))
                .unwrap_err()
                .contains("4 MiB")
        );
        let flag = cancel.clone();
        let trigger = std::thread::spawn(move || {
            std::thread::sleep(Duration::from_millis(150));
            flag.store(true, Ordering::Relaxed);
        });
        let started = Instant::now();
        assert!(
            compile(&mut fixture("wait"), &cancel, Duration::from_secs(5))
                .unwrap_err()
                .contains("cancelled")
        );
        trigger.join().unwrap();
        assert!(started.elapsed() < Duration::from_secs(5));
    }
    #[test]
    fn missing_compiler_falls_back_without_changing_template_and_cancel_prevents_work() {
        let dir = tempfile::tempdir().unwrap();
        let root = dir.path();
        let server = root.join("tools/multiplayer-server");
        std::fs::create_dir_all(server.join("zig")).unwrap();
        std::fs::create_dir_all(server.join("bin/windows-x64")).unwrap();
        std::fs::write(
            server.join("zig/scenemax_multiplayer_server.zig"),
            "invalid",
        )
        .unwrap();
        let template = server.join("bin/windows-x64/scenemax-mp-server.exe");
        std::fs::write(&template, b"template").unwrap();
        let cancel = AtomicBool::new(false);
        let (bytes, origin) =
            prepare_with(root, "windows-x64", &root.join("missing-compiler"), &cancel).unwrap();
        assert_eq!(bytes, b"template");
        assert!(origin.contains("prebuilt"));
        cancel.store(true, Ordering::Relaxed);
        assert!(
            prepare_with(root, "windows-x64", &root.join("missing-compiler"), &cancel)
                .unwrap_err()
                .contains("cancelled")
        );
        assert_eq!(std::fs::read(template).unwrap(), b"template");
    }

    #[test]
    #[ignore = "requires Zig 0.16.x (SCENEMAX_ZIG or PATH)"]
    fn real_zig_compiles_current_source_and_does_not_hide_source_errors() {
        let cancel = AtomicBool::new(false);
        let compiler = std::env::var_os("SCENEMAX_ZIG").unwrap_or_else(|| "zig".into());
        let project = tempfile::tempdir().unwrap();
        let root = project.path().canonicalize().unwrap();
        let mut snapshot = super::super::load(&root).unwrap();
        snapshot.settings.port = 9345;
        let report = super::super::run(
            &root,
            super::super::Operation::BuildWithPassword(snapshot, "fixture-password".into()),
            &cancel,
        )
        .unwrap();
        assert!(report.message.contains("Compiled"));
        let server = report.server.unwrap();
        assert_eq!(server.parent().unwrap(), root.join("multiplayer_server"));
        let bytes = std::fs::read(server).unwrap();
        let marker = b"SCENEMAX_MP_CONFIG_BEGIN";
        let start = bytes
            .windows(marker.len())
            .position(|b| b == marker)
            .unwrap()
            + marker.len();
        assert_eq!(&bytes[start + 12..start + 16], &9345u32.to_le_bytes());
        assert_eq!(super::super::load(&root).unwrap().settings.port, 9345);
        use sha2::{Digest, Sha256};
        assert_eq!(
            &bytes[start + 400..start + 432],
            Sha256::digest(b"fixture-password").as_slice()
        );
        assert!(
            !bytes
                .windows(b"fixture-password".len())
                .any(|w| w == b"fixture-password")
        );
        assert!(
            !std::fs::read_to_string(root.join(".scenemax-studio/multiplayer.json"))
                .unwrap()
                .contains("fixture-password")
        );
        let bad = tempfile::tempdir().unwrap();
        let source_dir = bad.path().join("tools/multiplayer-server/zig");
        std::fs::create_dir_all(&source_dir).unwrap();
        std::fs::write(
            source_dir.join("scenemax_multiplayer_server.zig"),
            "invalid Zig source",
        )
        .unwrap();
        assert!(
            prepare_with(bad.path(), "windows-x64", Path::new(&compiler), &cancel)
                .unwrap_err()
                .contains("Zig build failed")
        );
    }
}
