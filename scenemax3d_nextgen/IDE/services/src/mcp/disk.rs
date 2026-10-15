//! Filesystem adapters for automation, isolated from live editor state.
use crate::{Filesystem, TreeOperation};
use scenemax_ide_core::{Document, Project};
use serde_json::{Value, json};
use std::{
    path::{Component, Path, PathBuf},
    sync::{Mutex, mpsc},
    thread,
};
/// One immutable automation snapshot for a background operation.
pub struct Request {
    /// Canonical project root captured when accepted.
    pub root: PathBuf,
    /// Tool name or internal load action.
    pub name: String,
    /// Validated tool arguments.
    pub args: Value,
    /// Open-buffer snapshots used by search and validation.
    pub buffers: Vec<Document>,
}
/// Worker result; documents are adopted on the IDE thread only.
pub enum Output {
    /// Loaded or newly-created document.
    Document(Document),
    /// Structured read result.
    Json(Value),
    /// Decoded, bounded image for a preview-only reference overlay.
    Image(ImageData),
}
/// Decoded pixels transferred to the presentation adapter.
pub struct ImageData {
    /// Pixel width.
    pub width: u32,
    /// Pixel height.
    pub height: u32,
    /// RGBA8 pixels.
    pub pixels: Vec<u8>,
}
/// Owned, single-operation worker.
pub struct Worker {
    tx: Option<mpsc::SyncSender<Request>>,
    rx: Mutex<Option<mpsc::Receiver<Result<Output, String>>>>,
    handle: Option<thread::JoinHandle<()>>,
}
impl Worker {
    /// Start a bounded worker.
    pub fn new() -> std::io::Result<Self> {
        let (tx, rx) = mpsc::sync_channel(1);
        let (out, result) = mpsc::sync_channel(1);
        let handle = thread::Builder::new()
            .name("scenemax-mcp-files".into())
            .spawn(move || {
                while let Ok(request) = rx.recv() {
                    if out.send(perform(request)).is_err() {
                        break;
                    }
                }
            })?;
        Ok(Self {
            tx: Some(tx),
            rx: Mutex::new(Some(result)),
            handle: Some(handle),
        })
    }
    /// Submit one snapshot without blocking.
    pub fn submit(&self, r: Request) -> Result<(), String> {
        self.tx
            .as_ref()
            .ok_or("Worker stopped")?
            .try_send(r)
            .map_err(|e| e.to_string())
    }
    /// Poll completion.
    pub fn poll(&self) -> Option<Result<Output, String>> {
        self.rx.lock().ok()?.as_ref()?.try_recv().ok()
    }
}
impl Drop for Worker {
    fn drop(&mut self) {
        self.tx.take();
        if let Ok(receiver) = self.rx.get_mut() {
            receiver.take();
        }
        if let Some(handle) = self.handle.take() {
            let _ = handle.join();
        }
    }
}
/// Resolve a lexical project-relative path. Existing files are canonicalized by Filesystem.
pub fn path(root: &Path, args: &Value, key: &str) -> Result<PathBuf, String> {
    let raw = args[key].as_str().ok_or_else(|| format!("Missing {key}"))?;
    let base = match args["base"].as_str().unwrap_or("workspace") {
        "workspace" | "project" => root.to_owned(),
        "scripts" => root.join("scripts"),
        "resources" => root.join("resources"),
        _ => return Err("Unsupported base".into()),
    };
    let p = Path::new(raw);
    let p = if p.is_absolute() {
        p.to_owned()
    } else {
        base.join(p)
    };
    // Canonical Windows roots use the verbatim prefix; clients often send ordinary paths.
    let root_text = root.to_string_lossy();
    let path_text = p.to_string_lossy();
    let root_text = root_text.strip_prefix(r"\\?\").unwrap_or(&root_text);
    let path_text = path_text.strip_prefix(r"\\?\").unwrap_or(&path_text);
    let relative = Path::new(path_text)
        .strip_prefix(root_text)
        .map_err(|_| "Path is outside active project")?;
    for c in relative.components() {
        match c {
            Component::Normal(n) => {
                let s = n.to_string_lossy();
                if s.starts_with('.') || s.contains(':') {
                    return Err("Hidden metadata and alternate streams are not exposed".into());
                }
            }
            _ => return Err("Traversal is not allowed".into()),
        }
    }
    Ok(root.join(relative))
}
fn checked(project: &Project, p: &Path) -> Result<PathBuf, String> {
    let resolved = Filesystem::resolve(project, p).map_err(|e| e.to_string())?;
    path(project.root(), &json!({"path":resolved}), "path")
}
fn load_image(root: &Path, args: &Value, key: &str) -> Result<image::DynamicImage, String> {
    use std::io::Read;
    let project = Project::new(root.to_owned(), vec![]);
    let file = checked(&project, &path(root, args, key)?)?;
    let mut bytes = Vec::new();
    std::fs::File::open(file)
        .map_err(|e| e.to_string())?
        .take(16 * 1024 * 1024 + 1)
        .read_to_end(&mut bytes)
        .map_err(|e| e.to_string())?;
    if bytes.len() > 16 * 1024 * 1024 {
        return Err("Reference image exceeds 16 MiB".into());
    }
    let mut reader = image::ImageReader::new(std::io::Cursor::new(bytes))
        .with_guessed_format()
        .map_err(|e| e.to_string())?;
    let mut limits = image::Limits::default();
    limits.max_image_width = Some(4096);
    limits.max_image_height = Some(4096);
    limits.max_alloc = Some(64 * 1024 * 1024);
    reader.limits(limits);
    reader.decode().map_err(|e| e.to_string())
}
fn perform(r: Request) -> Result<Output, String> {
    let root = r.root.canonicalize().map_err(|e| e.to_string())?;
    let project = Project::new(root.clone(), vec![]);
    match r.name.as_str() {
        "editor.check" => {
            let doc = r.buffers.first().ok_or("No document to validate")?;
            let result = match doc.path().extension().and_then(|e| e.to_str()) {
                Some("smdesign") => scenemax_ide_core::automation::validate(doc.text()).map(|_| ()),
                Some("smui") => crate::scene::preview(doc.text()).map(|_| ()),
                Some("smmat") => serde_json::from_str::<Value>(doc.text())
                    .map_err(|e| e.to_string())
                    .and_then(|v| scenemax_assets::material::validate(&v)),
                _ => scenemax_parser::parse_program(doc.text())
                    .map(|_| ())
                    .map_err(|e| e.to_string()),
            };
            Ok(Output::Json(
                json!({"valid":result.is_ok(),"message":result.err().unwrap_or("Validation passed".into()),"path":doc.path(),"revision":r.args["checked_revision"]}),
            ))
        }
        "designer.overlay_reference_image" => {
            let image = load_image(&root, &r.args, "image_path")?.to_rgba8();
            Ok(Output::Image(ImageData {
                width: image.width(),
                height: image.height(),
                pixels: image.into_raw(),
            }))
        }
        "designer.compare_to_reference" => {
            let reference = load_image(&root, &r.args, "reference_image_path")?;
            let snapshot = load_image(&root, &r.args, "snapshot_path")?;
            let a = reference
                .resize_exact(256, 256, image::imageops::FilterType::Triangle)
                .to_luma8();
            let b = snapshot
                .resize_exact(256, 256, image::imageops::FilterType::Triangle)
                .to_luma8();
            let mse = a
                .as_raw()
                .iter()
                .zip(b.as_raw())
                .map(|(a, b)| ((f64::from(*a) - f64::from(*b)) / 255.).powi(2))
                .sum::<f64>()
                / (256. * 256.);
            Ok(Output::Json(
                json!({"grayscaleMse":mse,"similarity":1.-mse,"referenceSize":[reference.width(),reference.height()],"snapshotSize":[snapshot.width(),snapshot.height()],"note":"Images resized independently to 256x256; this metric does not assess artistic quality."}),
            ))
        }
        "load" => {
            let p = checked(&project, &path(&root, &r.args, "path")?)?;
            Ok(Output::Document(
                Filesystem::open_document(&project, &p).map_err(|e| e.to_string())?,
            ))
        }
        "file.create" | "designer.create_document" | "scene.create" => {
            let op = if r.name == "scene.create" {
                TreeOperation::AddScene {
                    parent: path(&root, &r.args, "parentPath")?,
                    name: r.args["sceneName"]
                        .as_str()
                        .ok_or("Missing sceneName")?
                        .into(),
                }
            } else if r.name == "designer.create_document" {
                let ui = r.args["kind"] == "ui";
                let mut name = r.args["fileName"]
                    .as_str()
                    .ok_or("Missing fileName")?
                    .to_owned();
                let ext = if ui { ".smui" } else { ".smdesign" };
                if !name.ends_with(ext) {
                    name.push_str(ext);
                }
                TreeOperation::Create {
                    parent: path(&root, &r.args, "directoryPath")?,
                    name,
                    source: Some(
                        if ui {
                            scenemax_ide_core::automation::ui_template()
                        } else {
                            scenemax_ide_core::automation::scene_template()
                        }
                        .to_string(),
                    ),
                }
            } else {
                let p = path(&root, &r.args, "path")?;
                if r.args["createParents"] == true {
                    let parent = p.parent().ok_or("Missing parent")?;
                    let mut current = root.clone();
                    for component in parent
                        .strip_prefix(&root)
                        .map_err(|e| e.to_string())?
                        .components()
                    {
                        current.push(component);
                        match std::fs::create_dir(&current) {
                            Ok(()) => {}
                            Err(e) if e.kind() == std::io::ErrorKind::AlreadyExists => {}
                            Err(e) => return Err(e.to_string()),
                        }
                        let canonical = checked(&project, &current)?;
                        if !canonical.is_dir() {
                            return Err("Parent is not a directory".into());
                        }
                        current = canonical;
                    }
                }
                TreeOperation::Create {
                    parent: p.parent().ok_or("Missing parent")?.to_owned(),
                    name: p
                        .file_name()
                        .ok_or("Missing filename")?
                        .to_string_lossy()
                        .into(),
                    source: Some(r.args["content"].as_str().ok_or("Missing content")?.into()),
                }
            };
            if let TreeOperation::Create { parent, .. } | TreeOperation::AddScene { parent, .. } =
                &op
            {
                checked(&project, parent)?;
            }
            let result = crate::tree_operations::perform(&root, op).map_err(|e| e.to_string())?;
            Ok(Output::Document(
                result.document.ok_or("No created document")?,
            ))
        }
        "project.search_text" => {
            let project = Filesystem::open_project(&root).map_err(|e| e.to_string())?;
            let paths = project
                .entries()
                .iter()
                .filter(|e| !e.is_directory)
                .map(|e| e.path.clone())
                .collect();
            let report = crate::search::search(
                root,
                paths,
                r.args["query"].as_str().ok_or("Missing query")?,
                r.buffers,
            )
            .map_err(|e| e.to_string())?;
            let limit = r.args["maxResults"].as_u64().unwrap_or(100).min(100) as usize;
            let truncated = report.truncated || report.hits.len() > limit;
            Ok(Output::Json(
                json!({"hits":report.hits.into_iter().take(limit).map(|h|json!({"path":h.path,"line":h.line,"preview":h.preview})).collect::<Vec<_>>(),"scanned":report.scanned,"skipped":report.skipped,"truncated":truncated}),
            ))
        }
        "ui.list_fonts" | "ui.list_sprites" => {
            let scene = crate::scene::assets::load(
                &root,
                &scenemax_ide_core::automation::ui_template().to_string(),
            )?;
            Ok(Output::Json(
                json!({"fonts":scene.fonts,"sprites":scene.sprites,"warnings":scene.warnings}),
            ))
        }
        "designer.validate_resources"
        | "designer.list_available_materials"
        | "asset.list_models" => {
            let source = r
                .buffers
                .first()
                .map(|d| d.text().to_owned())
                .unwrap_or_else(|| scenemax_ide_core::automation::scene_template().to_string());
            let scene = crate::scene3d::load(&root, &source)?;
            Ok(Output::Json(
                json!({"catalog":scene.catalog,"diagnostics":scene.entities.iter().filter(|e|!e.note.is_empty()).map(|e|json!({"id":e.id,"name":e.name,"message":e.note})).collect::<Vec<_>>()}),
            ))
        }
        _ => Err("Unsupported disk action".into()),
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn paths_creation_and_search_are_bounded() {
        let tmp = tempfile::tempdir().unwrap();
        let root = tmp.path().canonicalize().unwrap();
        std::fs::create_dir(root.join("scripts")).unwrap();
        for name in [
            "ui.list_fonts",
            "ui.list_sprites",
            "asset.list_models",
            "designer.list_available_materials",
        ] {
            assert!(
                perform(Request {
                    root: root.clone(),
                    name: name.into(),
                    args: json!({}),
                    buffers: vec![]
                })
                .is_ok(),
                "{name}"
            );
        }
        crate::scene::preview(&scenemax_ide_core::automation::ui_template().to_string()).unwrap();
        for path_value in [
            "../escape",
            "scripts/../../outside",
            ".git/config",
            "scripts/file:stream",
        ] {
            assert!(path(&root, &json!({"path":path_value}), "path").is_err());
        }
        let make = || Request {
            root: root.clone(),
            name: "file.create".into(),
            args: json!({"path":"scripts/example.code","content":"sys.print \"original\""}),
            buffers: vec![],
        };
        let Output::Document(mut doc) = perform(make()).unwrap() else {
            panic!("Expected created document")
        };
        assert!(perform(make()).is_err());
        doc.replace_text("sys.print \"unsaved marker\"".into());
        let result = perform(Request {
            root: root.clone(),
            name: "project.search_text".into(),
            args: json!({"query":"unsaved marker"}),
            buffers: vec![doc],
        })
        .unwrap();
        let Output::Json(report) = result else {
            panic!("Expected report")
        };
        assert_eq!(report["hits"].as_array().unwrap().len(), 1);
        let r = perform(Request {
            root,
            name: "load".into(),
            args: json!({"path":tmp.path().parent().unwrap().join("outside")}),
            buffers: vec![],
        });
        assert!(r.is_err());
    }
}
