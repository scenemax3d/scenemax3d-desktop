use super::Context;
use std::{
    fs::File,
    io::{self, Read, Seek, SeekFrom, Write},
    path::Path,
    process::{Command, Stdio},
    time::{Duration, Instant},
};

pub(super) fn run(command: &mut Command, context: &Context, log: &Path) -> io::Result<()> {
    context.check()?;
    // Raw subprocess output is private and removed after this command. Only redacted
    // output enters the persistent log and the UI.
    let scratch = tempfile::NamedTempFile::new()?;
    let stream = File::options().append(true).open(scratch.path())?;
    command
        .stdout(Stdio::from(stream.try_clone()?))
        .stderr(Stdio::from(stream))
        .stdin(Stdio::null());
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
    let mut reader = scratch.reopen()?;
    let mut disk = File::options().create(true).append(true).open(log)?;
    let mut written = disk.metadata()?.len();
    let mut redactor = Redactor {
        secret: std::env::var("BUTLER_API_KEY")
            .unwrap_or_default()
            .into_bytes(),
        pending: Vec::new(),
    };
    let mut child = command.spawn().map_err(|e| {
        io::Error::other(format!(
            "Could not start {}: {e}",
            command.get_program().to_string_lossy()
        ))
    })?;
    let started = Instant::now();
    let mut offset = 0;
    let mut exited = false;
    let result = (|| {
        loop {
            let read = drain(
                &mut reader,
                &mut offset,
                &mut disk,
                &mut written,
                &mut redactor,
                context,
            );
            if let Err(error) = read {
                break Err(error);
            }
            if let Err(error) = context.check() {
                break Err(error);
            }
            if started.elapsed() > Duration::from_secs(7200) {
                break Err(io::Error::other(
                    "Build tool exceeded the two-hour timeout.",
                ));
            }
            if reader.metadata()?.len() > 64 * 1024 * 1024 {
                break Err(io::Error::other(
                    "Build tool exceeded the 64 MiB output limit.",
                ));
            }
            match child.try_wait() {
                Ok(Some(status)) => {
                    exited = true;
                    while offset < reader.metadata()?.len() {
                        context.check()?;
                        drain(
                            &mut reader,
                            &mut offset,
                            &mut disk,
                            &mut written,
                            &mut redactor,
                            context,
                        )?;
                    }
                    break if status.success() {
                        Ok(())
                    } else {
                        Err(io::Error::other(format!(
                            "{} exited with {status}. See build logs.",
                            command.get_program().to_string_lossy()
                        )))
                    };
                }
                Ok(None) => std::thread::sleep(Duration::from_millis(80)),
                Err(error) => break Err(error),
            }
        }
    })();
    if result.is_err() && !exited {
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
    emit(redactor.push(&[], true), &mut disk, &mut written, context)?;
    result
}
fn drain(
    reader: &mut File,
    offset: &mut u64,
    disk: &mut File,
    written: &mut u64,
    redactor: &mut Redactor,
    context: &Context,
) -> io::Result<()> {
    reader.seek(SeekFrom::Start(*offset))?;
    let mut bytes = [0; 8192];
    for _ in 0..16 {
        let n = reader.read(&mut bytes)?;
        if n == 0 {
            break;
        }
        *offset += n as u64;
        emit(redactor.push(&bytes[..n], false), disk, written, context)?;
    }
    Ok(())
}
pub(super) fn login(butler: &str, context: &Context) -> io::Result<()> {
    context.stage(
        10.,
        "Complete Butler sign-in in your browser. Cancel remains available.",
    )?;
    let log = tempfile::NamedTempFile::new()?;
    run(Command::new(butler).arg("login"), context, log.path())?;
    context.stage(
        100.,
        "Butler login completed. Credentials stay in Butler's local store.",
    )
}
fn emit(text: String, disk: &mut File, written: &mut u64, context: &Context) -> io::Result<()> {
    if text.is_empty() {
        return Ok(());
    }
    if *written + text.len() as u64 <= 16 * 1024 * 1024 {
        disk.write_all(text.as_bytes())?;
        *written += text.len() as u64;
    }
    context.log(text);
    Ok(())
}
struct Redactor {
    pending: Vec<u8>,
    secret: Vec<u8>,
}
impl Redactor {
    fn push(&mut self, bytes: &[u8], finished: bool) -> String {
        self.pending.extend_from_slice(bytes);
        let limit = if finished {
            self.pending.len()
        } else {
            self.pending.len().saturating_sub(self.secret.len().max(4))
        };
        let mut output = Vec::new();
        let mut i = 0;
        while i < limit {
            if !self.secret.is_empty() && self.pending[i..].starts_with(&self.secret) {
                output.extend_from_slice(b"[redacted]");
                i += self.secret.len();
            } else {
                output.push(self.pending[i]);
                i += 1;
            }
        }
        // Keep a split UTF-8 character for the next read rather than damaging logs.
        if !finished {
            while i > 0 && i < self.pending.len() && self.pending[i] & 0xc0 == 0x80 {
                i -= 1;
                output.pop();
            }
        }
        self.pending.drain(..i);
        String::from_utf8_lossy(&output).replace('\r', "\n")
    }
}
pub(super) fn browse(folder: bool, context: &Context) -> io::Result<Option<std::path::PathBuf>> {
    context.stage(0., "Choose a path in the system dialog…")?;
    #[cfg(windows)]
    let mut command = {
        let mut command = Command::new("powershell.exe");
        command.args(["-NoProfile", "-STA", "-Command", r#"Add-Type -AssemblyName System.Windows.Forms; [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false); if ($env:SCENEMAX_PICK_FOLDER -eq '1') { $d = New-Object System.Windows.Forms.FolderBrowserDialog } else { $d = New-Object System.Windows.Forms.OpenFileDialog }; if ($d.ShowDialog() -eq 'OK') { if ($env:SCENEMAX_PICK_FOLDER -eq '1') { [Console]::Write($d.SelectedPath) } else { [Console]::Write($d.FileName) } }"#]);
        command.env("SCENEMAX_PICK_FOLDER", if folder { "1" } else { "0" });
        command
    };
    #[cfg(target_os = "macos")]
    let mut command = {
        let mut command = Command::new("osascript");
        command.args([
            "-e",
            if folder {
                "try\nPOSIX path of (choose folder)\non error number -128\nreturn \"\"\nend try"
            } else {
                "try\nPOSIX path of (choose file)\non error number -128\nreturn \"\"\nend try"
            },
        ]);
        command
    };
    #[cfg(all(not(windows), not(target_os = "macos")))]
    let mut command = {
        let mut command = Command::new("zenity");
        command.arg("--file-selection");
        if folder {
            command.arg("--directory");
        }
        command
    };
    let log = tempfile::NamedTempFile::new()?;
    run(&mut command, context, log.path())?;
    let text = std::fs::read_to_string(log.path())?;
    let path = text.trim();
    Ok((!path.is_empty()).then(|| std::path::PathBuf::from(path)))
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn credentials_and_unicode_survive_arbitrary_chunk_boundaries() {
        let input = "Output λ: secret-api-token / done\n";
        for size in 1..=input.len() {
            let mut redactor = Redactor {
                secret: b"secret-api-token".to_vec(),
                pending: Vec::new(),
            };
            let mut output = String::new();
            for chunk in input.as_bytes().chunks(size) {
                output.push_str(&redactor.push(chunk, false));
            }
            output.push_str(&redactor.push(&[], true));
            assert_eq!(output, "Output λ: [redacted] / done\n");
        }
    }
}
