//! Immutable per-session checkpoints; source files are never changed by recovery.
use crate::{Filesystem, ServiceError, io_result};
use scenemax_ide_core::{Document, Project};
use serde::{Deserialize, Serialize};
use std::{
    collections::{BTreeMap, HashMap},
    fs,
    io::{Read, Write},
    path::{Path, PathBuf},
    time::{SystemTime, UNIX_EPOCH},
};
const MAX_BYTES: u64 = 64 * 1024 * 1024;
#[derive(Serialize, Deserialize)]
struct Record {
    relative: PathBuf,
    baseline: Vec<u8>,
    text: String,
}
#[derive(Serialize, Deserialize)]
struct Checkpoint {
    version: u32,
    timestamp: u128,
    documents: Vec<Record>,
}
/// Recovered buffers and immutable checkpoint files they came from.
/// The application must ask before restoring or discarding them.
#[derive(Default)]
pub struct RecoveryBatch {
    /// Latest recoverable version of each script, retaining its original save baseline.
    pub documents: Vec<Document>,
    /// Checkpoints to retire only after explicit discard or durable restoration.
    pub sources: Vec<PathBuf>,
}
#[derive(Default)]
pub(crate) struct RecoveryJournal {
    previous: HashMap<PathBuf, PathBuf>,
}
fn folder(project: &Project, create: bool) -> Result<Option<PathBuf>, ServiceError> {
    let path = project.root().join(".scenemax-studio").join("recovery");
    if !path.exists() && !create {
        return Ok(None);
    }
    // Check each existing ancestor before creating directories (including junctions).
    let parent = project.root().join(".scenemax-studio");
    if parent.exists() {
        Filesystem::resolve(project, &parent)?;
    }
    if create {
        io_result(&path, "Create recovery folder", fs::create_dir_all(&path))?;
    }
    let resolved = Filesystem::resolve(project, &path)?;
    if create {
        let ignore = parent.join(".gitignore");
        match fs::OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(&ignore)
        {
            Ok(mut file) => io_result(&ignore, "Ignore local IDE state", file.write_all(b"*\n"))?,
            Err(error) if error.kind() == std::io::ErrorKind::AlreadyExists => {}
            Err(error) => {
                return Err(ServiceError::Io {
                    operation: "Ignore local IDE state",
                    path: ignore,
                    source: error,
                });
            }
        }
    }
    Ok(Some(resolved))
}
fn read(path: &Path) -> Result<Checkpoint, ServiceError> {
    let mut bytes = Vec::new();
    let file = io_result(path, "Read recovery", fs::File::open(path))?;
    io_result(
        path,
        "Read recovery",
        file.take(MAX_BYTES + 1).read_to_end(&mut bytes),
    )?;
    if bytes.len() as u64 > MAX_BYTES {
        return Err(ServiceError::Limit("Recovery checkpoint exceeds 64 MiB"));
    }
    let checkpoint: Checkpoint = serde_json::from_slice(&bytes)
        .map_err(|e| ServiceError::Recovery(format!("{}: {e}", path.display())))?;
    if checkpoint.version != 1 {
        return Err(ServiceError::Recovery(
            "Unsupported checkpoint version".into(),
        ));
    }
    Ok(checkpoint)
}
impl RecoveryJournal {
    pub(crate) fn load(root: &Path) -> Result<RecoveryBatch, ServiceError> {
        let project = Project::new(root.to_owned(), vec![]);
        let Some(folder) = folder(&project, false)? else {
            return Ok(RecoveryBatch::default());
        };
        let mut latest = BTreeMap::<PathBuf, (u128, Option<Document>)>::new();
        let mut sources = Vec::new();
        for entry in io_result(&folder, "List recovery", fs::read_dir(&folder))? {
            let entry = io_result(&folder, "Read recovery entry", entry)?;
            let path = entry.path();
            if path.extension().is_none_or(|ext| ext != "json") {
                continue;
            }
            if sources.len() >= 128 {
                return Err(ServiceError::Limit(
                    "Too many recovery checkpoints; inspect .scenemax-studio/recovery",
                ));
            }
            let path = Filesystem::resolve(&project, &path)?;
            let checkpoint = read(&path)?;
            for record in checkpoint.documents {
                if record.relative.is_absolute()
                    || record
                        .relative
                        .components()
                        .any(|c| !matches!(c, std::path::Component::Normal(_)))
                {
                    return Err(ServiceError::Recovery(
                        "Invalid script path in checkpoint".into(),
                    ));
                }
                let path = Filesystem::resolve(&project, &record.relative)?;
                if record.baseline.len() > 8 * 1024 * 1024 || record.text.len() > 8 * 1024 * 1024 {
                    return Err(ServiceError::Limit("Recovery buffer exceeds 8 MiB"));
                }
                let mut document = Document::from_bytes(path.clone(), record.baseline)?;
                document.replace_text(record.text);
                // Skip an older checkpoint once its exact text is already saved.
                let disk = Filesystem::open_document(&project, &path)?;
                let document = if document.encoded_bytes() == disk.saved_bytes() {
                    None
                } else {
                    Some(document)
                };
                if latest
                    .get(&path)
                    .is_none_or(|(stamp, _)| *stamp < checkpoint.timestamp)
                {
                    latest.insert(path, (checkpoint.timestamp, document));
                }
            }
            sources.push(path);
        }
        Ok(RecoveryBatch {
            documents: latest.into_values().filter_map(|(_, doc)| doc).collect(),
            sources,
        })
    }
    pub(crate) fn checkpoint(
        &mut self,
        root: &Path,
        documents: Vec<Document>,
        retire: Vec<PathBuf>,
    ) -> Result<(), ServiceError> {
        let project = Project::new(root.to_owned(), vec![]);
        let Some(folder) = folder(&project, !documents.is_empty())? else {
            return Ok(());
        };
        let mut obsolete = retire;
        if !documents.is_empty() {
            let timestamp = SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .map_err(|e| ServiceError::Recovery(e.to_string()))?
                .as_nanos();
            let records = documents
                .into_iter()
                .map(|doc| {
                    let relative = doc
                        .path()
                        .strip_prefix(root)
                        .map_err(|_| ServiceError::OutsideProject(doc.path().to_owned()))?
                        .to_owned();
                    Ok(Record {
                        relative,
                        baseline: doc.saved_bytes().to_vec(),
                        text: doc.text().to_owned(),
                    })
                })
                .collect::<Result<Vec<_>, ServiceError>>()?;
            let bytes = serde_json::to_vec(&Checkpoint {
                version: 1,
                timestamp,
                documents: records,
            })
            .map_err(|e| ServiceError::Recovery(e.to_string()))?;
            if bytes.len() as u64 > MAX_BYTES {
                return Err(ServiceError::Limit("Recovery checkpoint exceeds 64 MiB"));
            }
            let path = folder.join(format!("{}-{timestamp}.json", std::process::id()));
            let mut pending = io_result(
                &path,
                "Create recovery checkpoint",
                tempfile::NamedTempFile::new_in(&folder),
            )?;
            io_result(
                &path,
                "Write recovery checkpoint",
                pending.write_all(&bytes),
            )?;
            io_result(
                &path,
                "Flush recovery checkpoint",
                pending.as_file().sync_all(),
            )?;
            pending
                .persist_noclobber(&path)
                .map_err(|e| ServiceError::Io {
                    operation: "Publish recovery checkpoint",
                    path: path.clone(),
                    source: e.error,
                })?;
            if let Some(old) = self.previous.insert(root.to_owned(), path) {
                obsolete.push(old);
            }
        } else if let Some(old) = self.previous.remove(root) {
            obsolete.push(old);
        }
        for path in obsolete {
            if path.parent() != Some(folder.as_path())
                || path.extension().is_none_or(|e| e != "json")
            {
                return Err(ServiceError::OutsideProject(path));
            }
            match fs::remove_file(&path) {
                Ok(()) => {}
                Err(e) if e.kind() == std::io::ErrorKind::NotFound => {}
                Err(e) => {
                    return Err(ServiceError::Io {
                        operation: "Retire recovery checkpoint",
                        path,
                        source: e,
                    });
                }
            }
        }
        Ok(())
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn restart_restores_unsaved_unicode_without_overwriting_external_edits() {
        let dir = tempfile::tempdir().unwrap();
        fs::create_dir(dir.path().join("scripts")).unwrap();
        let path = dir.path().join("scripts/main");
        fs::write(&path, "original").unwrap();
        let project = Filesystem::open_project(dir.path()).unwrap();
        let mut doc = Filesystem::open_document(&project, &path).unwrap();
        doc.replace_text("שלום 🦀".into());
        let mut journal = RecoveryJournal::default();
        journal
            .checkpoint(project.root(), vec![doc], vec![])
            .unwrap();
        fs::write(&path, "external").unwrap();
        let mut restored = RecoveryJournal::load(project.root()).unwrap();
        assert_eq!(restored.documents[0].text(), "שלום 🦀");
        assert!(Filesystem::save_document(&mut restored.documents[0]).is_err());
        assert_eq!(fs::read_to_string(&path).unwrap(), "external");
        journal
            .checkpoint(project.root(), restored.documents, restored.sources)
            .unwrap();
        assert_eq!(
            RecoveryJournal::load(project.root())
                .unwrap()
                .documents
                .len(),
            1
        );
    }
    #[test]
    fn a_newer_saved_checkpoint_shadows_an_older_unsaved_copy() {
        let dir = tempfile::tempdir().unwrap();
        fs::create_dir(dir.path().join("scripts")).unwrap();
        let path = dir.path().join("scripts/main");
        fs::write(&path, "original").unwrap();
        let project = Filesystem::open_project(dir.path()).unwrap();
        let mut old = Filesystem::open_document(&project, &path).unwrap();
        old.replace_text("old unsaved".into());
        RecoveryJournal::default()
            .checkpoint(project.root(), vec![old], vec![])
            .unwrap();
        let mut current = Filesystem::open_document(&project, &path).unwrap();
        current.replace_text("latest".into());
        RecoveryJournal::default()
            .checkpoint(project.root(), vec![current], vec![])
            .unwrap();
        fs::write(&path, "latest").unwrap();
        assert!(
            RecoveryJournal::load(project.root())
                .unwrap()
                .documents
                .is_empty()
        );
    }
    #[test]
    fn clean_and_discarded_checkpoints_do_not_reappear() {
        let dir = tempfile::tempdir().unwrap();
        fs::create_dir(dir.path().join("scripts")).unwrap();
        let path = dir.path().join("scripts/main");
        fs::write(&path, "original").unwrap();
        let project = Filesystem::open_project(dir.path()).unwrap();
        let mut doc = Filesystem::open_document(&project, &path).unwrap();
        doc.replace_text("changed".into());
        let mut journal = RecoveryJournal::default();
        journal
            .checkpoint(project.root(), vec![doc], vec![])
            .unwrap();
        journal.checkpoint(project.root(), vec![], vec![]).unwrap();
        assert!(
            RecoveryJournal::load(project.root())
                .unwrap()
                .documents
                .is_empty()
        );
    }
}
