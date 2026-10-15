use super::Context;
use std::{
    fs::File,
    io::{Read, Seek, SeekFrom},
    path::Path,
    process::{Command, Stdio},
    time::{Duration, Instant},
};

pub(super) fn run(
    root: &Path,
    args: &[&str],
    ctx: &Context,
) -> Result<(bool, Vec<u8>, String), String> {
    ctx.check()?;
    let mut cmd = Command::new("git");
    cmd.current_dir(root)
        .args(["--no-pager", "--literal-pathspecs", "-c", "color.ui=false"])
        .env("GIT_TERMINAL_PROMPT", "0")
        .env("GIT_EDITOR", "true")
        .env("GIT_SEQUENCE_EDITOR", "true")
        .env("GCM_INTERACTIVE", "never")
        .stdin(Stdio::null());
    for arg in args {
        #[cfg(test)]
        if *arg == "--global"
            && let Some(path) = &ctx.global
        {
            cmd.arg("--file").arg(path);
            continue;
        }
        cmd.arg(arg);
    }
    run_command(cmd, ctx)
}
fn run_command(mut cmd: Command, ctx: &Context) -> Result<(bool, Vec<u8>, String), String> {
    let stdout = tempfile::NamedTempFile::new().map_err(|e| e.to_string())?;
    let stderr = tempfile::NamedTempFile::new().map_err(|e| e.to_string())?;
    cmd.stdout(Stdio::from(stdout.reopen().map_err(|e| e.to_string())?))
        .stderr(Stdio::from(stderr.reopen().map_err(|e| e.to_string())?));
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        cmd.creation_flags(0x08000000);
    }
    #[cfg(unix)]
    {
        use std::os::unix::process::CommandExt;
        cmd.process_group(0);
    }
    let mut child = cmd.spawn().map_err(|e| format!("Could not start Git: {e}. Install Git from https://git-scm.com/downloads and restart the IDE."))?;
    let started = Instant::now();
    let result: Result<bool, String> = (|| {
        loop {
            ctx.check()?;
            if started.elapsed() > Duration::from_secs(300) {
                return Err("Git exceeded the five-minute timeout".into());
            }
            if stdout
                .as_file()
                .metadata()
                .map_err(|e| e.to_string())?
                .len()
                > 4 * 1024 * 1024
                || stderr
                    .as_file()
                    .metadata()
                    .map_err(|e| e.to_string())?
                    .len()
                    > 4 * 1024 * 1024
            {
                return Err(
                    "Git output exceeded 4 MiB; use a narrower diff or smaller history".into(),
                );
            }
            if let Some(status) = child.try_wait().map_err(|e| e.to_string())? {
                return Ok(status.success());
            }
            if ctx.progress {
                ctx.log(&read_tail(stderr.as_file())?);
            }
            std::thread::sleep(Duration::from_millis(80));
        }
    })();
    if result.is_err() {
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
    let success = result?;
    let mut out = Vec::new();
    stdout
        .reopen()
        .map_err(|e| e.to_string())?
        .take(4 * 1024 * 1024)
        .read_to_end(&mut out)
        .map_err(|e| e.to_string())?;
    let err = read_tail(stderr.as_file())?;
    Ok((success, out, err))
}
fn read_tail(file: &File) -> Result<String, String> {
    let mut file = file.try_clone().map_err(|e| e.to_string())?;
    let len = file.metadata().map_err(|e| e.to_string())?.len();
    file.seek(SeekFrom::Start(len.saturating_sub(24 * 1024)))
        .map_err(|e| e.to_string())?;
    let mut bytes = Vec::new();
    file.take(24 * 1024)
        .read_to_end(&mut bytes)
        .map_err(|e| e.to_string())?;
    Ok(String::from_utf8_lossy(&bytes).replace('\r', "\n"))
}

pub(super) fn browse(ctx: &Context) -> Result<String, String> {
    #[cfg(windows)]
    let mut cmd = {
        let mut c = Command::new("powershell.exe");
        c.args(["-NoProfile","-STA","-Command",r#"Add-Type -AssemblyName System.Windows.Forms; [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false); $d = New-Object System.Windows.Forms.FolderBrowserDialog; if ($d.ShowDialog() -eq 'OK') { [Console]::Write($d.SelectedPath) }"#]);
        c
    };
    #[cfg(target_os = "macos")]
    let mut cmd = {
        let mut c = Command::new("osascript");
        c.args([
            "-e",
            "try\nPOSIX path of (choose folder)\non error number -128\nreturn \"\"\nend try",
        ]);
        c
    };
    #[cfg(all(not(windows), not(target_os = "macos")))]
    let mut cmd = {
        let mut c = Command::new("zenity");
        c.args(["--file-selection", "--directory"]);
        c
    };
    cmd.stdin(Stdio::null());
    let (ok, out, err) = run_command(cmd, ctx)?;
    if ok {
        Ok(String::from_utf8_lossy(&out).trim().into())
    } else {
        Err(err)
    }
}
