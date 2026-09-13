//! Read the owned projector's runtime log without blocking the UI thread.
use std::{
    fs::File,
    io::{Read, Seek, SeekFrom},
    path::PathBuf,
    sync::{
        Arc,
        atomic::{AtomicBool, Ordering},
        mpsc::SyncSender,
    },
    thread,
    time::{Duration, SystemTime},
};
pub(crate) struct RuntimeLog {
    stop: Arc<AtomicBool>,
}
impl RuntimeLog {
    pub(crate) fn start(
        path: PathBuf,
        since: SystemTime,
        output: SyncSender<String>,
    ) -> std::io::Result<Self> {
        let stop = Arc::new(AtomicBool::new(false));
        let cancelled = stop.clone();
        thread::Builder::new()
            .name("ide-runtime-log".into())
            .spawn(move || {
                let mut position = 0;
                let mut pending = Vec::new();
                while !cancelled.load(Ordering::Relaxed) {
                    if let Ok(mut file) = File::open(&path)
                        && let Ok(metadata) = file.metadata()
                        && metadata.modified().is_ok_and(|modified| modified >= since)
                    {
                        if metadata.len() < position {
                            position = 0;
                            pending.clear();
                        }
                        if file.seek(SeekFrom::Start(position)).is_ok() {
                            let mut bytes = Vec::new();
                            if file.take(64 * 1024).read_to_end(&mut bytes).is_ok() {
                                position += bytes.len() as u64;
                                for byte in bytes {
                                    pending.push(byte);
                                    if byte == b'\n' || pending.len() >= 4096 {
                                        let _ = output.try_send(
                                            String::from_utf8_lossy(&pending).trim_end().to_owned(),
                                        );
                                        pending.clear();
                                    }
                                }
                            }
                        }
                    }
                    thread::sleep(Duration::from_millis(100));
                }
            })?;
        Ok(Self { stop })
    }
}
impl Drop for RuntimeLog {
    fn drop(&mut self) {
        self.stop.store(true, Ordering::Relaxed);
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn forwards_complete_unicode_lines_from_a_new_runtime_session() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("runtime.log");
        let (send, receive) = std::sync::mpsc::sync_channel(8);
        let tail = RuntimeLog::start(path.clone(), SystemTime::now(), send).unwrap();
        // Separate the timestamps on filesystems with coarse modification times.
        thread::sleep(Duration::from_millis(20));
        std::fs::write(path, "[RUNTIME] שלום 🦀\n").unwrap();
        assert_eq!(
            receive.recv_timeout(Duration::from_secs(3)).unwrap(),
            "[RUNTIME] שלום 🦀"
        );
        drop(tail);
    }
}
