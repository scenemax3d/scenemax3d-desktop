use super::*;
use std::{
    collections::{BTreeMap, BTreeSet},
    io::{Read, Write},
    time::SystemTime,
};
/// Reviewed asset operation.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Operation {
    /// Keep source registrations and files.
    Copy,
    /// Copy dependencies, then archive selected source assets.
    Move,
    /// Archive selected registrations and unshared files.
    Archive,
}
#[derive(Clone, Debug)]
struct File {
    source: PathBuf,
    target: PathBuf,
    len: u64,
    modified: SystemTime,
    existing: bool,
}
#[derive(Clone, Debug)]
struct Edit {
    path: PathBuf,
    before: Option<Vec<u8>>,
    after: Vec<u8>,
}
/// Immutable, conflict-checked plan. Apply consumes this exact reviewed snapshot.
#[derive(Clone, Debug)]
pub struct Plan {
    /// Human-readable summary and dependency files.
    pub lines: Vec<String>,
    /// Total transferred bytes (existing identical files excluded).
    pub bytes: u64,
    /// Count of registrations/documents including dependencies.
    pub assets: usize,
    /// Conflicts that prevent applying the plan.
    pub conflicts: Vec<String>,
    guards: Vec<(PathBuf, Vec<u8>)>,
    operation: Operation,
    project: PathBuf,
    target: Option<PathBuf>,
    files: Vec<File>,
    edits: Vec<Edit>,
    archive: Vec<(PathBuf, u64, SystemTime)>,
}
fn strings(v: &Value, out: &mut Vec<String>) {
    match v {
        Value::String(s) => out.push(s.clone()),
        Value::Array(v) => {
            for v in v {
                strings(v, out)
            }
        }
        Value::Object(v) => {
            for (k, v) in v {
                if !["sourcePath", "importedAt"].contains(&k.as_str()) {
                    strings(v, out)
                }
            }
        }
        _ => {}
    }
}
fn path_reference(s: &str) -> bool {
    !s.contains(['\n', '\r', '"', '{', '}'])
        && !s.contains(':')
        && !s.starts_with('/')
        && !s.starts_with('#')
        && s.contains('/')
}
fn gltf_json(path: &Path) -> io::Result<Value> {
    if path
        .extension()
        .is_some_and(|e| e.eq_ignore_ascii_case("gltf"))
    {
        return json(path);
    }
    let mut file = fs::File::open(path)?;
    let mut header = [0u8; 20];
    file.read_exact(&mut header)?;
    if &header[..4] != b"glTF" || &header[16..20] != b"JSON" {
        return Err(error("Invalid GLB header"));
    }
    let len = u32::from_le_bytes(
        header[12..16]
            .try_into()
            .map_err(|_| error("Invalid GLB header"))?,
    ) as usize;
    if len > 16 * 1024 * 1024 {
        return Err(error("GLB metadata exceeds 16 MiB"));
    }
    let mut bytes = vec![0; len];
    file.read_exact(&mut bytes)?;
    serde_json::from_slice(&bytes).map_err(Into::into)
}
fn mentions(s: &str, name: &str) -> bool {
    if s == name {
        return true;
    }
    s.match_indices(name).any(|(i, _)| {
        let word = |c: char| c.is_alphanumeric() || c == '_';
        !s[..i].chars().next_back().is_some_and(word)
            && !s[i + name.len()..].chars().next().is_some_and(word)
    })
}
fn relative(root: &Path, path: &Path) -> io::Result<PathBuf> {
    path.strip_prefix(root)
        .map(Path::to_owned)
        .map_err(|_| error("Asset dependency escapes root"))
}
fn add_path(root: &Path, path: &Path, out: &mut BTreeSet<PathBuf>) -> io::Result<()> {
    let path = confined(root, &relative(root, path)?)?;
    if !path.exists() {
        return Err(error(format!("Missing dependency: {}", path.display())));
    }
    if path.is_dir() {
        for p in walk(&path)? {
            confined(root, &relative(root, &p)?)?;
            out.insert(p);
        }
    } else {
        out.insert(path);
    }
    Ok(())
}
fn uripath(base: &Path, uri: &str, root: &Path) -> io::Result<PathBuf> {
    let mut bytes = Vec::new();
    let b = uri.as_bytes();
    let mut i = 0;
    while i < b.len() {
        if b[i] == b'%' {
            let s = uri
                .get(i + 1..i + 3)
                .ok_or_else(|| error("Invalid dependency URI"))?;
            bytes.push(u8::from_str_radix(s, 16).map_err(|_| error("Invalid dependency URI"))?);
            i += 3;
        } else {
            bytes.push(b[i]);
            i += 1;
        }
    }
    let decoded = String::from_utf8(bytes).map_err(|_| error("Invalid dependency URI"))?;
    if decoded.contains(':') || decoded.starts_with('/') || decoded.starts_with('\\') {
        return Err(error("Remote or absolute dependencies are unsupported"));
    }
    let mut path = base.to_owned();
    for c in Path::new(&decoded.replace('\\', "/")).components() {
        match c {
            std::path::Component::ParentDir => {
                path.pop();
            }
            std::path::Component::CurDir => {}
            std::path::Component::Normal(v) => path.push(v),
            _ => return Err(error("Invalid dependency")),
        }
    }
    confined(root, &relative(root, &path)?)
}
fn files(asset: &Asset) -> io::Result<BTreeSet<PathBuf>> {
    let mut out = BTreeSet::new();
    if let Some(file) = &asset.file {
        add_path(&asset.root, file, &mut out)?;
        let rel = relative(&asset.root, file)?;
        // Preserve the complete imported bundle (textures, binaries, animation sidecars).
        if ["Models", "Animations", "Effekseer", "Fonts"].contains(&asset.category.as_str())
            && rel.components().count() >= 3
            && let Some(parent) = file.parent()
        {
            add_path(&asset.root, parent, &mut out)?;
        }
        if asset.origin == "Designer" && ["Scenes", "UI"].contains(&asset.category.as_str()) {
            let stem = file.file_stem().unwrap_or_default().to_string_lossy();
            for suffix in [".code", "_init.code", "_end.code", "_ui.code"] {
                let p = file.with_file_name(format!("{stem}{suffix}"));
                if p.is_file() {
                    add_path(&asset.root, &p, &mut out)?;
                }
            }
        }
    }
    let mut refs = Vec::new();
    strings(&asset.data, &mut refs);
    for s in refs {
        let s = s.replace('\\', "/");
        if !path_reference(&s) {
            continue;
        }
        let path = confined(&asset.root, Path::new(&s))?;
        if path.exists() {
            add_path(&asset.root, &path, &mut out)?;
        } else if asset.origin == "Designer" {
            // Material texture paths are resource-relative, handled as separate dependencies below.
            continue;
        } else if Path::new(&s).extension().is_some() {
            return Err(error(format!("Missing dependency: {s}")));
        }
    }
    let mut processed = BTreeSet::new();
    loop {
        let next = out.iter().find(|p| !processed.contains(*p)).cloned();
        let Some(file) = next else {
            break;
        };
        processed.insert(file.clone());
        let ext = file
            .extension()
            .unwrap_or_default()
            .to_string_lossy()
            .to_lowercase();
        if ext == "gltf" || ext == "glb" {
            let data = gltf_json(&file)?;
            for key in ["buffers", "images"] {
                for v in data[key].as_array().into_iter().flatten() {
                    if let Some(uri) = v["uri"].as_str().filter(|s| !s.starts_with("data:")) {
                        let p = uripath(
                            file.parent().ok_or_else(|| error("Missing folder"))?,
                            uri,
                            &asset.root,
                        )?;
                        add_path(&asset.root, &p, &mut out)?;
                    }
                }
            }
        }
        if ext == "fnt" {
            let text = fs::read_to_string(&file)?;
            for line in text.lines().filter(|l| l.trim_start().starts_with("page ")) {
                if let Some((_, tail)) = line.split_once("file=\"")
                    && let Some((name, _)) = tail.split_once('"')
                {
                    let p = uripath(
                        file.parent().ok_or_else(|| error("Missing font folder"))?,
                        name,
                        &asset.root,
                    )?;
                    add_path(&asset.root, &p, &mut out)?;
                }
            }
        }
        if out.len() > 100_000 {
            return Err(error("Asset dependency set exceeds limit"));
        }
    }
    Ok(out)
}
fn same(a: &Path, b: &Path) -> io::Result<bool> {
    if fs::metadata(a)?.len() != fs::metadata(b)?.len() {
        return Ok(false);
    }
    let mut a = fs::File::open(a)?;
    let mut b = fs::File::open(b)?;
    let mut x = [0u8; 65536];
    let mut y = [0u8; 65536];
    loop {
        let n = a.read(&mut x)?;
        b.read_exact(&mut y[..n])?;
        if x[..n] != y[..n] {
            return Ok(false);
        }
        if n == 0 {
            return Ok(true);
        }
    }
}
fn index_edit<'a>(
    path: &Path,
    edits: &'a mut BTreeMap<PathBuf, (Option<Vec<u8>>, Value)>,
) -> io::Result<&'a mut Value> {
    if !edits.contains_key(path) {
        let before = if path.exists() {
            Some(fs::read(path)?)
        } else {
            None
        };
        let value = if path.exists() {
            json(path)?
        } else {
            serde_json::json!({})
        };
        edits.insert(path.into(), (before, value));
    }
    Ok(&mut edits
        .get_mut(path)
        .ok_or_else(|| error("Missing index edit"))?
        .1)
}
/// Resolve dependencies and conflicts before any mutation. The caller displays this review first.
pub fn review(
    inventory: &Inventory,
    ids: &[String],
    target: Option<&Path>,
    operation: Operation,
) -> io::Result<Plan> {
    let selected: Vec<_> = inventory
        .assets
        .iter()
        .filter(|a| ids.contains(&a.id))
        .collect();
    let first = selected
        .first()
        .ok_or_else(|| error("Select at least one asset"))?;
    let project = first.project.canonicalize()?;
    let target = target.map(Path::canonicalize).transpose()?;
    if operation != Operation::Archive && target.is_none() {
        return Err(error("Choose a destination project"));
    }
    if target.as_ref() == Some(&project) {
        return Err(error("Choose a different project"));
    }
    if let Some(t) = &target
        && !t.join("scripts").is_dir()
    {
        return Err(error(
            "Destination must be an existing SceneMax project with a scripts folder",
        ));
    }
    if operation != Operation::Copy && selected.iter().any(|a| !a.owned()) {
        return Err(error("Shared installation assets are read-only; use Copy"));
    }
    let mut plan = Plan {
        guards: vec![],
        lines: vec![],
        bytes: 0,
        assets: 0,
        conflicts: vec![],
        operation,
        project: project.clone(),
        target: target.clone(),
        files: vec![],
        edits: vec![],
        archive: vec![],
    };
    let mut edits = BTreeMap::new();
    let mut added = BTreeSet::new();
    let mut sources = BTreeSet::new();
    let mut pending = selected.clone();
    let mut visited = BTreeSet::new();
    let destination = target.as_ref().map(|p| scan(p)).transpose()?;
    while let Some(a) = pending.pop() {
        if !visited.insert(a.id.clone()) {
            continue;
        }
        if let Some(index) = &a.index {
            let path = confined(&a.root, index)?;
            let value = json(&path)?;
            if !value[&a.key]
                .as_array()
                .is_some_and(|rows| rows.contains(&a.data))
            {
                return Err(error("Source registration changed; refresh inventory"));
            }
            plan.guards.push((path.clone(), fs::read(path)?));
        }
        plan.assets += 1;
        plan.lines
            .push(format!("{} • {} ({})", a.category, a.name, a.origin));
        let deps = match files(a) {
            Ok(v) => v,
            Err(e) => {
                plan.conflicts.push(format!("{}: {e}", a.name));
                continue;
            }
        };
        let mut references = Vec::new();
        strings(&a.data, &mut references);
        if let Some(file) = &a.file
            && a.origin == "Designer"
            && let Ok(data) = json(file)
        {
            strings(&data, &mut references);
        }
        for p in &deps {
            if p.extension().is_some_and(|e| e == "code")
                && fs::metadata(p)?.len() < 8 * 1024 * 1024
            {
                references.push(fs::read_to_string(p)?);
            }
        }
        if operation != Operation::Archive {
            for b in &inventory.assets {
                if b.id != a.id
                    && !b.status.starts_with("Shadowed")
                    && references.iter().any(|s| mentions(s, &b.name))
                {
                    pending.push(b);
                }
            }
            let dest = target.as_ref().ok_or_else(|| error("Missing target"))?;
            let base = if a.origin == "Designer" {
                "scripts"
            } else {
                "resources"
            };
            for source in deps {
                let to = confined(dest, &Path::new(base).join(relative(&a.root, &source)?))?;
                add_file(&mut plan, &mut added, source, to)?;
            }
            // Native materials/shaders can reference project-relative texture files.
            if a.origin == "Designer" {
                for s in &references {
                    let rel = PathBuf::from(s.replace('\\', "/"));
                    if !rel.is_absolute() && path_reference(s) {
                        let root = project.join("resources");
                        let source = confined(&root, &rel)?;
                        if source.is_file() {
                            add_file(
                                &mut plan,
                                &mut added,
                                source,
                                confined(dest, &Path::new("resources").join(rel))?,
                            )?;
                        } else if a.category == "Materials" && Path::new(s).extension().is_some() {
                            plan.conflicts
                                .push(format!("Missing material dependency: {s}"));
                        }
                    }
                }
            }
            if let Some(index) = &a.index {
                if destination.as_ref().is_some_and(|d| {
                    d.assets.iter().any(|b| {
                        b.category == a.category
                            && b.name.eq_ignore_ascii_case(&a.name)
                            && b.origin != "Shared"
                    })
                }) {
                    plan.conflicts.push(format!(
                        "Destination already registers {}: {}",
                        a.category, a.name
                    ));
                    continue;
                }
                let index = index.to_string_lossy().replace('\\', "/");
                let ext = if index.ends_with("-ext.json") {
                    index
                } else {
                    format!("{}-ext.json", index.trim_end_matches(".json"))
                };
                let path = confined(dest, &Path::new("resources").join(ext))?;
                let value = index_edit(&path, &mut edits)?;
                if value.get(&a.key).is_none() {
                    value[&a.key] = serde_json::json!([]);
                }
                let rows = value[&a.key]
                    .as_array_mut()
                    .ok_or_else(|| error("Destination index array is invalid"))?;
                if rows.iter().any(|r| {
                    r["name"]
                        .as_str()
                        .is_some_and(|n| n.eq_ignore_ascii_case(&a.name))
                }) {
                    plan.conflicts
                        .push(format!("Duplicate registration in transfer: {}", a.name));
                } else {
                    rows.push(a.data.clone());
                }
            }
        }
    }
    if operation != Operation::Copy {
        // Never remove a payload still used by another registration/authoring document.
        let mut shared = BTreeSet::new();
        for a in &inventory.assets {
            if a.owned() && !ids.contains(&a.id) {
                if let Some(index) = &a.index {
                    let path = confined(&a.root, index)?;
                    plan.guards.push((path.clone(), fs::read(path)?));
                }
                match files(a) {
                    Ok(f) => shared.extend(f),
                    Err(_) => {
                        if let Some(file) = &a.file {
                            shared.insert(file.clone());
                            if let Some(parent) = file.parent()
                                && parent != a.root
                                && parent.is_dir()
                            {
                                shared.extend(walk(parent)?);
                            }
                        }
                    }
                }
            }
        }
        for a in &selected {
            if let Some(index) = &a.index {
                let path = confined(&a.root, index)?;
                let value = index_edit(&path, &mut edits)?;
                let rows = value[&a.key]
                    .as_array_mut()
                    .ok_or_else(|| error("Source index array is invalid"))?;
                let pos = rows
                    .iter()
                    .position(|r| r == &a.data)
                    .ok_or_else(|| error("Registration changed; refresh inventory"))?;
                rows.remove(pos);
            }
            for file in files(a)? {
                if !shared.contains(&file) && sources.insert(file.clone()) {
                    let m = fs::metadata(&file)?;
                    plan.archive.push((file, m.len(), m.modified()?));
                }
            }
        }
        plan.lines.push(format!("Archive {} unshared source files; keep payloads used by other registrations. Script references are not rewritten.",plan.archive.len()));
    }
    for (path, (before, value)) in edits {
        plan.edits.push(Edit {
            path,
            before,
            after: serde_json::to_vec_pretty(&value)?,
        });
    }
    for f in &plan.files {
        plan.lines.push(format!(
            "{}  •  {} bytes{}",
            f.target.display(),
            f.len,
            if f.existing {
                " (already identical)"
            } else {
                ""
            }
        ));
    }
    Ok(plan)
}
fn add_file(
    plan: &mut Plan,
    added: &mut BTreeSet<PathBuf>,
    source: PathBuf,
    target: PathBuf,
) -> io::Result<()> {
    if !added.insert(target.clone()) {
        if let Some(previous) = plan.files.iter().find(|f| f.target == target)
            && !same(&source, &previous.source)?
        {
            plan.conflicts
                .push(format!("Two dependencies use {}", target.display()));
        }
        return Ok(());
    }
    let meta = fs::metadata(&source)?;
    let existing = target.exists();
    if existing && !same(&source, &target)? {
        plan.conflicts.push(format!(
            "Different file already exists: {}",
            target.display()
        ));
    }
    if !existing {
        plan.bytes += meta.len();
    }
    plan.files.push(File {
        source,
        target,
        len: meta.len(),
        modified: meta.modified()?,
        existing,
    });
    Ok(())
}
fn atomic(path: &Path, bytes: &[u8]) -> io::Result<()> {
    let parent = path.parent().ok_or_else(|| error("Missing parent"))?;
    fs::create_dir_all(parent)?;
    let mut tmp = tempfile::NamedTempFile::new_in(parent)?;
    tmp.write_all(bytes)?;
    tmp.as_file().sync_all()?;
    tmp.persist(path).map_err(|e| e.error)?;
    Ok(())
}
struct Locks(Vec<PathBuf>);
impl Drop for Locks {
    fn drop(&mut self) {
        for p in &self.0 {
            let _ = fs::remove_file(p);
        }
    }
}
fn locks(roots: &[PathBuf]) -> io::Result<Locks> {
    let mut out = Locks(vec![]);
    let mut roots = roots.to_vec();
    roots.sort();
    roots.dedup();
    for root in roots {
        let p = confined(&root, Path::new(".scenemax-studio/inventory.lock"))?;
        fs::create_dir_all(p.parent().ok_or_else(|| error("Missing parent"))?)?;
        fs::OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(&p)
            .map_err(|_| error("Another inventory operation is active for this project"))?;
        out.0.push(p);
    }
    Ok(out)
}
/// Apply a previously reviewed plan, rolling back on errors. Progress is reported per file/index.
pub fn apply(plan: Plan, mut progress: impl FnMut(usize, usize)) -> io::Result<String> {
    if !plan.conflicts.is_empty() {
        return Err(error("Resolve conflicts before applying this transfer"));
    }
    let mut roots = vec![plan.project.clone()];
    if let Some(t) = &plan.target {
        roots.push(t.clone());
    }
    let _locks = locks(&roots)?;
    for (path, bytes) in &plan.guards {
        if fs::read(path)? != *bytes {
            return Err(error("Source registration changed since review"));
        }
    }
    for f in &plan.files {
        if let Some(target) = &plan.target {
            confined(target, &relative(target, &f.target)?)?;
        }
        let m = fs::symlink_metadata(&f.source)?;
        if linked(&m) {
            return Err(error("Source became a link since review"));
        }
        if m.len() != f.len || m.modified()? != f.modified {
            return Err(error("Source changed since review; review again"));
        }
        if f.existing {
            if !same(&f.source, &f.target)? {
                return Err(error("Destination changed since review"));
            }
        } else if f.target.exists() {
            return Err(error("Destination appeared since review"));
        }
    }
    for e in &plan.edits {
        let root = roots
            .iter()
            .find(|r| e.path.starts_with(r))
            .ok_or_else(|| error("Index escapes project"))?;
        confined(root, &relative(root, &e.path)?)?;
        let now = if e.path.exists() {
            Some(fs::read(&e.path)?)
        } else {
            None
        };
        if now != e.before {
            return Err(error(
                "Registration changed since review; refresh and review again",
            ));
        }
    }
    for (p, len, time) in &plan.archive {
        confined(&plan.project, &relative(&plan.project, p)?)?;
        let m = fs::metadata(p)?;
        if m.len() != *len || m.modified()? != *time {
            return Err(error("Source changed since review"));
        }
    }
    let folder = confined(&plan.project, Path::new(".scenemax-studio/trash"))?;
    fs::create_dir_all(&folder)?;
    let backup = tempfile::Builder::new()
        .prefix("inventory-")
        .tempdir_in(&folder)?;
    let total = plan.files.len() + plan.edits.len() + plan.archive.len();
    let mut count = 0;
    let mut created = Vec::new();
    let mut written = Vec::new();
    let mut archived = Vec::new();
    let manifest = plan
        .edits
        .iter()
        .enumerate()
        .map(|(i, e)| serde_json::json!({"path":e.path,"backup":format!("index-{i}.json")}))
        .collect::<Vec<_>>();
    fs::write(
        backup.path().join("manifest.json"),
        serde_json::to_vec_pretty(&manifest)?,
    )?;
    let result = (|| {
        for f in &plan.files {
            if !f.existing {
                fs::create_dir_all(f.target.parent().ok_or_else(|| error("Missing parent"))?)?;
                let mut from = fs::File::open(&f.source)?;
                let mut to = fs::OpenOptions::new()
                    .write(true)
                    .create_new(true)
                    .open(&f.target)?;
                created.push(f.target.clone());
                io::copy(&mut from, &mut to)?;
                to.sync_all()?;
            }
            count += 1;
            progress(count, total);
        }
        for (i, e) in plan.edits.iter().enumerate() {
            if let Some(before) = &e.before {
                fs::write(backup.path().join(format!("index-{i}.json")), before)?;
            }
            atomic(&e.path, &e.after)?;
            written.push(e);
            count += 1;
            progress(count, total);
        }
        for (p, _, _) in &plan.archive {
            let rel = relative(&plan.project, p)?;
            let to = backup.path().join("files").join(rel);
            fs::create_dir_all(to.parent().ok_or_else(|| error("Missing archive parent"))?)?;
            fs::rename(p, &to)?;
            archived.push((p.clone(), to));
            count += 1;
            progress(count, total);
        }
        Ok::<_, io::Error>(())
    })();
    if let Err(e) = result {
        let mut failed = false;
        for (p, to) in archived.iter().rev() {
            failed |= fs::rename(to, p).is_err();
        }
        for e in written.iter().rev() {
            failed |= if let Some(bytes) = &e.before {
                atomic(&e.path, bytes).is_err()
            } else {
                fs::remove_file(&e.path).is_err()
            };
        }
        for p in created.iter().rev() {
            failed |= fs::remove_file(p).is_err();
        }
        if failed {
            let recovery = backup.keep();
            return Err(error(format!(
                "{e}. Rollback incomplete; recovery files: {}",
                recovery.display()
            )));
        }
        return Err(e);
    }

    let saved = backup.keep();
    Ok(format!(
        "{:?} complete: {} assets, {} files, {:.1} MiB. Recovery: {}",
        plan.operation,
        plan.assets,
        plan.files.len(),
        plan.bytes as f64 / 1048576.,
        saved.display()
    ))
}
/// Rename a registration without rewriting scripts; reject duplicates and stale metadata.
pub fn rename(inventory: &Inventory, id: &str, name: &str) -> io::Result<String> {
    let a = inventory
        .assets
        .iter()
        .find(|a| a.id == id)
        .ok_or_else(|| error("Select an asset"))?;
    if !a.owned() {
        return Err(error("Shared assets are read-only"));
    }
    if name.is_empty()
        || !name
            .chars()
            .all(|c| c.is_alphanumeric() || c == '_' || c == '-')
    {
        return Err(error("Use letters, digits, underscores or hyphens"));
    }
    if inventory
        .assets
        .iter()
        .any(|b| b.id != a.id && b.category == a.category && b.name.eq_ignore_ascii_case(name))
    {
        return Err(error("This resource name already exists"));
    }
    let index = a
        .index
        .as_ref()
        .ok_or_else(|| error("Rename file-based assets in the project tree"))?;
    let _lock = locks(std::slice::from_ref(&a.project))?;
    let path = confined(&a.root, index)?;
    let mut data = json(&path)?;
    let rows = data[&a.key]
        .as_array_mut()
        .ok_or_else(|| error("Invalid index"))?;
    let row = rows
        .iter_mut()
        .find(|r| **r == a.data)
        .ok_or_else(|| error("Registration changed; refresh inventory"))?;
    row["name"] = Value::String(name.into());
    atomic(&path, &serde_json::to_vec_pretty(&data)?)?;
    Ok(format!(
        "Renamed {} to {name}. Update script references to the old name.",
        a.name
    ))
}
