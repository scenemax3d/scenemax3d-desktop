//! Persist form edits independently of build validation and publishing.
use super::files;
use scenemax_ide_core::deployment::Settings;
use std::{io, path::PathBuf, sync::mpsc, thread::JoinHandle};

/// Ordered background preference writes, drained when the IDE shuts down.
#[derive(Default)]
pub struct SettingsWriter {
    sender: Option<mpsc::Sender<(PathBuf, Settings)>>,
    errors: Option<std::sync::Mutex<mpsc::Receiver<String>>>,
    worker: Option<JoinHandle<()>>,
}
impl SettingsWriter {
    /// Queue edited form values without validating a build or waiting for disk.
    pub fn save(&mut self, root: PathBuf, settings: Settings) -> io::Result<()> {
        if self.sender.is_none() {
            let (send, receive) = mpsc::channel::<(PathBuf, Settings)>();
            let (errors, reports) = mpsc::channel();
            self.worker = Some(
                std::thread::Builder::new()
                    .name("deployment-settings".into())
                    .spawn(move || {
                        for (root, settings) in receive {
                            if let Err(error) = files::save_settings(&root, &settings) {
                                let _ = errors
                                    .send(format!("Could not save deployment settings: {error}"));
                            }
                        }
                    })?,
            );
            self.sender = Some(send);
            self.errors = Some(std::sync::Mutex::new(reports));
        }
        self.sender
            .as_ref()
            .unwrap()
            .send((root, settings))
            .map_err(io::Error::other)
    }
    /// Consume one pending write error for presentation to the user.
    pub fn error(&self) -> Option<String> {
        self.errors.as_ref()?.lock().ok()?.try_recv().ok()
    }
}
impl Drop for SettingsWriter {
    fn drop(&mut self) {
        self.sender.take();
        if let Some(worker) = self.worker.take() {
            let _ = worker.join();
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn edits_survive_shutdown_without_build_validation() {
        let root = tempfile::tempdir().unwrap();
        let mut writer = SettingsWriter::default();
        let mut settings = Settings::default();
        settings.itch_page = "https://example.itch.io/sample".into();
        settings.butler = "C:/tools with spaces/butler.exe".into();
        settings.upload = true;
        writer.save(root.path().into(), settings.clone()).unwrap();
        settings.web_loading.footer = "Custom credit".into();
        writer.save(root.path().into(), settings).unwrap();
        drop(writer);
        let saved = files::load_settings(root.path()).unwrap();
        assert_eq!(saved.itch_page, "https://example.itch.io/sample");
        assert_eq!(saved.butler, "C:/tools with spaces/butler.exe");
        assert_eq!(saved.web_loading.footer, "Custom credit");
        assert!(!saved.upload);
        let path = root.path().join(".scenemax-studio/deployment.json");
        let mut json: serde_json::Value =
            serde_json::from_slice(&std::fs::read(&path).unwrap()).unwrap();
        assert!(json.get("upload").is_none());
        // Older versions persisted this checkbox; ignore that previous opt-in.
        json["upload"] = true.into();
        std::fs::write(&path, serde_json::to_vec(&json).unwrap()).unwrap();
        assert!(!files::load_settings(root.path()).unwrap().upload);
    }
}
