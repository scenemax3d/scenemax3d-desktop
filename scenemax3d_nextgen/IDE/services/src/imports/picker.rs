use super::*;
/// Show the operating-system file chooser. Call from a worker, never the UI thread.
pub fn pick_file(kind: Kind) -> io::Result<Option<PathBuf>> {
    #[cfg(windows)]
    {
        let mut cmd = std::process::Command::new("powershell.exe");
        cmd.args(["-NoProfile", "-STA", "-Command", r#"Add-Type -AssemblyName System.Windows.Forms; [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false); $d = New-Object System.Windows.Forms.OpenFileDialog; $d.Title = $env:SCENEMAX_IMPORT_TITLE; $d.Filter = $env:SCENEMAX_IMPORT_FILTER; if ($d.ShowDialog() -eq 'OK') { [Console]::Write($d.FileName) }"#]);
        cmd.env("SCENEMAX_IMPORT_TITLE", kind.title());
        cmd.env(
            "SCENEMAX_IMPORT_FILTER",
            format!(
                "{}|{}",
                kind.title(),
                kind.extensions()
                    .iter()
                    .map(|e| format!("*.{e}"))
                    .collect::<Vec<_>>()
                    .join(";")
            ),
        );
        files::hidden(&mut cmd);
        let out = cmd.output()?;
        if !out.status.success() {
            return Err(error(String::from_utf8_lossy(&out.stderr).into_owned()));
        }
        let value = String::from_utf8(out.stdout).map_err(|e| error(e.to_string()))?;
        Ok((!value.is_empty()).then(|| PathBuf::from(value)))
    }
    #[cfg(not(windows))]
    {
        let _ = kind;
        Err(error("Enter the source path in the import dialog"))
    }
}
