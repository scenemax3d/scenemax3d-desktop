//! Deployment dependency closure. Never mutates the author's catalogs or assets.
use super::{Context, Request, files};
use serde_json::Value;
use std::{
    collections::{BTreeMap, BTreeSet},
    fs,
    io::{self, Read},
    path::{Component, Path, PathBuf},
};

// A small lexical scan deliberately retains references in every branch and function.
// Comments do not make dormant assets reachable; quoted names remain whole tokens.
fn tokens(text: &str) -> Vec<String> {
    let mut result = Vec::new();
    let mut chars = text.chars().peekable();
    while let Some(c) = chars.next() {
        if c == '/' && chars.peek() == Some(&'/') {
            for c in chars.by_ref() {
                if c == '\n' {
                    break;
                }
            }
        } else if c == '/' && chars.peek() == Some(&'*') {
            chars.next();
            while let Some(c) = chars.next() {
                if c == '*' && chars.peek() == Some(&'/') {
                    chars.next();
                    break;
                }
            }
        } else if c == '"' || c == '\'' {
            let mut s = String::new();
            while let Some(next) = chars.next() {
                if next == c {
                    break;
                }
                if next == '\\' && chars.peek() == Some(&c) {
                    s.push(c);
                    chars.next();
                } else {
                    s.push(next);
                }
            }
            result.push(s);
        } else if c.is_alphanumeric() || c == '_' {
            let mut s = String::from(c);
            while chars
                .peek()
                .is_some_and(|c| c.is_alphanumeric() || matches!(c, '_' | '-' | '/' | '\\'))
            {
                if let Some(c) = chars.next() {
                    s.push(c);
                }
            }
            result.push(s);
        } else if !c.is_whitespace() {
            result.push(c.to_string());
        }
    }
    result
}
fn normalized(path: &Path) -> PathBuf {
    let mut result = PathBuf::new();
    for part in path.components() {
        match part {
            Component::CurDir => {}
            Component::ParentDir => {
                result.pop();
            }
            p => result.push(p.as_os_str()),
        }
    }
    result
}
fn read_text(path: &Path) -> io::Result<String> {
    let meta = files::regular(path)?;
    if meta.len() > 32 * 1024 * 1024 {
        return Err(io::Error::other("Deployment document exceeds 32 MiB."));
    }
    let text = fs::read_to_string(path)?;
    Ok(text.trim_start_matches('\u{feff}').to_owned())
}
pub(super) fn root_entry(project: &Path, context: &Context) -> io::Result<PathBuf> {
    files::list(&project.join("scripts"), context)?
        .into_iter()
        .filter(|p| p.file_name().is_some_and(|n| n == "main"))
        .min_by_key(|p| (p.components().count(), p.clone()))
        .ok_or_else(|| io::Error::other("This project has no root main script."))
}

fn catalog_json(text: &str) -> Result<Value, serde_json::Error> {
    // Match the runtime's legacy models/animations reader: tolerate trailing commas.
    let mut clean = String::with_capacity(text.len());
    let mut chars = text.chars().peekable();
    let (mut quoted, mut escaped) = (false, false);
    while let Some(c) = chars.next() {
        if quoted {
            if escaped {
                escaped = false;
            } else if c == '\\' {
                escaped = true;
            } else if c == '"' {
                quoted = false;
            }
        } else if c == '"' {
            quoted = true;
        } else if c == ','
            && chars
                .clone()
                .find(|c| !c.is_whitespace())
                .is_some_and(|c| c == '}' || c == ']')
        {
            continue;
        }
        clean.push(c);
    }
    serde_json::from_str(&clean)
}
type ModelReferences = BTreeMap<String, BTreeSet<String>>;

fn script_closure(
    project: &Path,
    entry: &Path,
    context: &Context,
) -> io::Result<(BTreeSet<PathBuf>, BTreeSet<String>, ModelReferences)> {
    let root = project.join("scripts");
    let inventory: BTreeSet<_> = files::list(&root, context)?.into_iter().collect();
    let scene_root = entry
        .parent()
        .ok_or_else(|| io::Error::other("Missing entry folder."))?;
    let mut pending = vec![entry.to_owned()];
    let mut selected = BTreeSet::new();
    let mut names = BTreeSet::new();
    let mut references = ModelReferences::new();
    let mut material_requests = BTreeSet::new();
    loop {
        // Resolve native surfaces only through material assignments, never shader names.
        // A symbolic/dynamic name may be supplied by another reachable script/function.
        if pending.is_empty() && !material_requests.is_empty() {
            let materials: Vec<_> = inventory
                .iter()
                .filter(|p| p.extension().is_some_and(|e| e == "smmat"))
                .collect();
            let dynamic = material_requests.iter().any(|name| {
                !materials.iter().any(|p| {
                    p.file_stem()
                        .is_some_and(|s| s.to_string_lossy().to_lowercase() == *name)
                })
            });
            for material in materials {
                if material.file_stem().is_some_and(|s| {
                    let name = s.to_string_lossy().to_lowercase();
                    material_requests.contains(&name) || (dynamic && names.contains(&name))
                }) {
                    pending.push(material.clone());
                }
            }
            material_requests.clear();
        }
        let Some(path) = pending.pop() else {
            break;
        };
        context.check()?;
        let path = normalized(&path);
        if !inventory.contains(&path) {
            return Err(io::Error::other(format!(
                "Missing script dependency: {}",
                path.display()
            )));
        }
        if !selected.insert(path.clone()) {
            continue;
        }
        let text = read_text(&path)?;
        // Generated .code is the runtime authority for scene resources. Designer
        // documents retain editor-only legacy resource names and imported sources.
        if path.extension().is_some_and(|e| e == "smdesign") {
            continue;
        }
        if path.extension().is_some_and(|e| e == "smmat") {
            let value: Value = serde_json::from_str(&text).map_err(|e| {
                io::Error::other(format!("Invalid material {}: {e}", path.display()))
            })?;
            scenemax_assets::material::validate(&value).map_err(|e| {
                io::Error::other(format!("Invalid material {}: {e}", path.display()))
            })?;
            for (_, key, _) in scenemax_assets::material::TEXTURES {
                if let Some(texture) = value["textures"][key].as_str().filter(|p| !p.is_empty()) {
                    if !project.join("resources").join(texture).is_file() {
                        return Err(io::Error::other(format!(
                            "Missing texture {texture} referenced by {}",
                            path.display()
                        )));
                    }
                    names.insert(texture.to_lowercase());
                }
            }
            continue;
        }
        let words = tokens(&text);
        names.extend(words.iter().map(|s| s.to_lowercase()));
        let parent = path.parent().unwrap_or(&root);
        // Camera tracks and authored UI live beside code, and can be loaded by name.
        for document in &inventory {
            if document.parent() == Some(parent)
                && document.extension().is_some_and(|e| e == "smdesign")
            {
                pending.push(document.clone());
            }
        }
        if path
            .extension()
            .is_some_and(|e| e == "smui" || e == "smdesign" || e == "smmat")
        {
            continue;
        }
        let program = scenemax_parser::parse_program(&text).map_err(|error| {
            io::Error::other(format!("Cannot compile {}: {error}", path.display()))
        })?;
        let mut statements: Vec<_> = program.statements.iter().collect();
        while let Some(statement) = statements.pop() {
            use scenemax_parser::Statement;
            match statement {
                Statement::SetMaterial(material) => {
                    if let scenemax_parser::AssignmentValue::Symbol(name) = &material.material {
                        if !name.trim().is_empty() {
                            material_requests.insert(name.trim().to_lowercase());
                        }
                    } else {
                        material_requests.insert(String::from("*dynamic*"));
                    }
                }
                Statement::AddCode { path: include } => {
                    let candidate = normalized(
                        &parent.join(include.trim_start_matches('/').replace('\\', "/")),
                    );
                    pending.push(if inventory.contains(&candidate) {
                        candidate
                    } else {
                        candidate.with_extension("code")
                    });
                }
                Statement::SwitchTo { scene } => {
                    let relative = scene.replace('\\', "/");
                    let candidates = [
                        scene_root.join(&relative).join("main"),
                        parent.join(&relative).join("main"),
                        root.join(&relative).join("main"),
                    ];
                    let target = candidates
                        .iter()
                        .map(|p| normalized(p))
                        .find(|p| inventory.contains(p));
                    pending.push(target.ok_or_else(|| {
                        io::Error::other(format!(
                            "Missing scene dependency: {scene} (from {})",
                            path.display()
                        ))
                    })?);
                }
                Statement::UiLoad { name } => {
                    let file = if name.ends_with(".smui") {
                        name.clone()
                    } else {
                        format!("{name}.smui")
                    };
                    let candidates = [parent.join(&file), scene_root.join(&file), root.join(&file)];
                    let found = candidates
                        .iter()
                        .map(|p| normalized(p))
                        .find(|p| inventory.contains(p));
                    pending.push(found.ok_or_else(|| {
                        io::Error::other(format!(
                            "Missing UI dependency: {name} (from {})",
                            path.display()
                        ))
                    })?);
                }
                Statement::ModelDecl { resource, name, .. } => {
                    names.insert(resource.to_lowercase());
                    references
                        .entry(resource.to_lowercase())
                        .or_default()
                        .insert(format!(
                            "{} (entity {name})",
                            path.strip_prefix(project)
                                .map_err(io::Error::other)?
                                .to_string_lossy()
                                .replace('\\', "/")
                        ));
                }
                Statement::If(branch) => {
                    statements.extend(&branch.actions);
                    statements.extend(&branch.else_actions);
                }
                Statement::KeyEvent(event) => statements.extend(&event.actions),
                Statement::WhenEvent(event) => statements.extend(&event.actions),
                Statement::FunctionDef(function) => statements.extend(&function.actions),
                Statement::AnimationControllerEvent(event) => statements.extend(&event.actions),
                Statement::ThrowMotionEvent(event) => statements.extend(&event.actions),
                Statement::Guarded { actions, .. }
                | Statement::Repeat { actions, .. }
                | Statement::DoWhile { actions, .. }
                | Statement::LoopContinue { actions, .. }
                | Statement::Async { actions } => statements.extend(actions),
                _ => {}
            }
        }
    }
    Ok((selected, names, references))
}

struct Library {
    root: PathBuf,
    destination: PathBuf,
    inventory: BTreeMap<String, PathBuf>,
    selected: BTreeSet<String>,
    catalogs: BTreeMap<String, Value>,
    rows: BTreeSet<(String, String, usize)>,
    dependencies: BTreeMap<String, BTreeSet<String>>,
}
impl Library {
    fn new(root: PathBuf, destination: PathBuf, context: &Context) -> io::Result<Self> {
        let mut inventory = BTreeMap::new();
        if root.is_dir() {
            for path in files::list(&root, context)? {
                let key = path
                    .strip_prefix(&root)
                    .map_err(io::Error::other)?
                    .to_string_lossy()
                    .replace('\\', "/")
                    .to_lowercase();
                if inventory.insert(key, path).is_some() {
                    return Err(io::Error::other(
                        "Resource names differ only by case; rename them before cross-platform export.",
                    ));
                }
            }
        }
        let mut catalogs = BTreeMap::new();
        for (key, path) in &inventory {
            // Resource registry files are one level below resources; model documents
            // and import metadata deeper in the tree are dependencies, not registries.
            if key.matches('/').count() == 1 && key.ends_with(".json") {
                let Ok(value) = catalog_json(&read_text(path)?) else {
                    context.log(format!(
                        "Skipping legacy non-JSON catalog (not loadable by Bevy): {}\n",
                        path.display()
                    ));
                    continue;
                };
                if value.as_object().is_some_and(|o| {
                    o.values().any(|v| {
                        v.as_array()
                            .is_some_and(|a| a.iter().any(|r| r["name"].is_string()))
                    })
                }) {
                    catalogs.insert(key.clone(), value);
                }
            }
        }
        Ok(Self {
            root,
            destination,
            inventory,
            selected: BTreeSet::new(),
            catalogs,
            rows: BTreeSet::new(),
            dependencies: BTreeMap::new(),
        })
    }
    fn select(&mut self, key: &str, names: &mut BTreeSet<String>) -> io::Result<()> {
        let key = key.replace('\\', "/").to_lowercase();
        if key.ends_with(".j3o")
            || self.catalogs.contains_key(&key)
            || !self.inventory.contains_key(&key)
            || !self.selected.insert(key.clone())
        {
            return Ok(());
        }
        let path = self.inventory[&key].clone();
        let extension = path
            .extension()
            .and_then(|s| s.to_str())
            .unwrap_or("")
            .to_lowercase();
        if extension == "gltf" || extension == "glb" {
            let value: Value = if extension == "gltf" {
                serde_json::from_str(&read_text(&path)?)?
            } else {
                let mut file = fs::File::open(&path)?;
                let mut header = [0u8; 20];
                file.read_exact(&mut header)?;
                let size =
                    u32::from_le_bytes([header[12], header[13], header[14], header[15]]) as usize;
                if &header[..4] != b"glTF" || &header[16..] != b"JSON" || size > 32 * 1024 * 1024 {
                    return Err(io::Error::other(format!(
                        "Invalid GLB header: {}",
                        path.display()
                    )));
                }
                let mut json = vec![0; size];
                file.read_exact(&mut json)?;
                serde_json::from_slice(&json)?
            };
            for group in ["buffers", "images"] {
                if let Some(items) = value[group].as_array() {
                    for item in items {
                        if let Some(uri) = item["uri"].as_str() {
                            if uri.starts_with("data:") {
                                continue;
                            }
                            let decoded = decode_uri(uri)?;
                            let dep =
                                normalized(&path.parent().unwrap_or(&self.root).join(decoded));
                            let relative = dep.strip_prefix(&self.root).map_err(|_| {
                                io::Error::other("glTF dependency escapes resource root.")
                            })?;
                            let key = relative.to_string_lossy().replace('\\', "/").to_lowercase();
                            if !self.inventory.contains_key(&key) {
                                return Err(io::Error::other(format!(
                                    "Missing glTF dependency: {} (from {})",
                                    dep.display(),
                                    path.display()
                                )));
                            }
                            self.dependencies
                                .entry(
                                    path.strip_prefix(&self.root)
                                        .map_err(io::Error::other)?
                                        .to_string_lossy()
                                        .replace('\\', "/")
                                        .to_lowercase(),
                                )
                                .or_default()
                                .insert(key.clone());
                            self.select(&key, names)?;
                        }
                    }
                }
            }
            return Ok(());
        }
        // Binary effect files reference sidecars internally. Preserve that effect's
        // directory, not the entire shared effect collection.
        if matches!(extension.as_str(), "efkefc" | "efk" | "j3md") {
            let prefix = key
                .rsplit_once('/')
                .map(|(p, _)| format!("{p}/"))
                .unwrap_or_default();
            let dependencies: Vec<_> = self
                .inventory
                .keys()
                .filter(|k| k.starts_with(&prefix))
                .cloned()
                .collect();
            for dep in dependencies {
                self.select(&dep, names)?;
            }
        }
        if matches!(
            extension.as_str(),
            "json"
                | "gltf"
                | "fnt"
                | "smweapon"
                | "smmotion"
                | "smskybox"
                | "smik"
                | "smshader"
                | "smeffect"
                | "smprobe"
        ) {
            let text = read_text(&path)?;
            // Preview models are editor state, not dependencies of a runtime constraint.
            let text = if scenemax_ide_core::ik::is_file(&path) {
                let mut value: Value = serde_json::from_str(&text)?;
                if let Some(v) = value.as_object_mut() {
                    v.remove("designerMetadata");
                    v.remove("targetModelId");
                }
                value.to_string()
            } else {
                text
            };
            names.extend(tokens(&text).into_iter().map(|s| s.to_lowercase()));
            let values = if let Ok(value) = serde_json::from_str::<Value>(&text) {
                strings(&value)
            } else {
                tokens(&text)
            };
            let parent = path.parent().unwrap_or(&self.root).to_owned();
            for value in values {
                self.reference(&value, &parent, names)?;
            }
        }
        Ok(())
    }
    fn reference(
        &mut self,
        value: &str,
        parent: &Path,
        names: &mut BTreeSet<String>,
    ) -> io::Result<()> {
        let value = value.split('#').next().unwrap_or(value).replace('\\', "/");
        if value.starts_with("data:") {
            return Ok(());
        }
        for candidate in [self.root.join(&value), parent.join(&value)] {
            let candidate = normalized(&candidate);
            if let Ok(relative) = candidate.strip_prefix(&self.root) {
                let key = relative.to_string_lossy().replace('\\', "/").to_lowercase();
                self.select(&key, names)?;
            }
        }
        Ok(())
    }
    fn expand(&mut self, names: &mut BTreeSet<String>) -> io::Result<()> {
        let catalogs = self.catalogs.clone();
        for (key, value) in catalogs {
            if let Some(object) = value.as_object() {
                for (group, array) in object {
                    if let Some(array) = array.as_array() {
                        for (i, row) in array.iter().enumerate() {
                            if row["name"]
                                .as_str()
                                .is_some_and(|n| names.contains(&n.to_lowercase()))
                            {
                                self.rows.insert((key.clone(), group.clone(), i));
                                let row = runtime_row(row, names);
                                for value in strings(&row) {
                                    if value.to_lowercase().ends_with(".j3o")
                                        && row["sourceModel"].is_string()
                                    {
                                        continue;
                                    }
                                    names.insert(value.to_lowercase());
                                    self.reference(&value, &self.root.clone(), names)?;
                                }
                            }
                        }
                    }
                }
            }
        }
        // Standalone authored definitions (effects, skyboxes, materials, motions,
        // shaders, probes) can be addressed without a catalog.
        let candidates: Vec<_> = self
            .inventory
            .iter()
            .filter(|(key, path)| {
                let stem = path
                    .file_stem()
                    .unwrap_or_default()
                    .to_string_lossy()
                    .to_lowercase();
                let ext = path.extension().and_then(|e| e.to_str()).unwrap_or("");
                let definition = matches!(
                    ext,
                    "json"
                        | "smweapon"
                        | "smmotion"
                        | "smskybox"
                        | "smik"
                        | "smshader"
                        | "smeffect"
                        | "smprobe"
                        | "efkefc"
                );
                names.contains(*key)
                    || (scenemax_ide_core::ik::is_file(path)
                        && read_text(path)
                            .ok()
                            .and_then(|s| serde_json::from_str::<Value>(&s).ok())
                            .and_then(|v| v["id"].as_str().map(str::to_lowercase))
                            .is_some_and(|id| names.contains(&id)))
                    || (definition
                        && (names.contains(&stem)
                            || (stem.ends_with(".ik")
                                && names.contains(stem.trim_end_matches(".ik")))))
            })
            .map(|(key, _)| key.clone())
            .collect();
        for key in candidates {
            self.select(&key, names)?;
        }
        Ok(())
    }
    fn model_inventory(
        &self,
        references: &ModelReferences,
        names: &BTreeSet<String>,
        snapshot: &Path,
        all_assets: bool,
    ) -> Vec<Value> {
        let prefix = self
            .destination
            .strip_prefix(snapshot)
            .unwrap_or(&self.destination)
            .to_string_lossy()
            .replace('\\', "/");
        let mut result = Vec::new();
        for (catalog, value) in &self.catalogs {
            let Some(models) = value["models"].as_array() else {
                continue;
            };
            for (i, row) in models.iter().enumerate() {
                let name = row["name"].as_str().unwrap_or_default();
                let path = row["path"].as_str().unwrap_or_default();
                let selected = self.rows.contains(&(catalog.clone(), "models".into(), i));
                if !selected {
                    continue;
                }
                let resolved = if supported_model(path) {
                    Some(path)
                } else {
                    row["sourceModel"]
                        .as_str()
                        .and_then(|source| {
                            models.iter().find(|r| {
                                r["name"]
                                    .as_str()
                                    .is_some_and(|n| n.eq_ignore_ascii_case(source))
                            })
                        })
                        .and_then(|r| r["path"].as_str())
                        .filter(|p| supported_model(p))
                };
                let mut paths = BTreeSet::new();
                if selected && let Some(resolved) = resolved {
                    let mut pending = vec![resolved.replace('\\', "/").to_lowercase()];
                    while let Some(key) = pending.pop() {
                        if self.selected.contains(&key)
                            && paths.insert(key.clone())
                            && let Some(deps) = self.dependencies.get(&key)
                        {
                            pending.extend(deps.iter().cloned());
                        }
                    }
                }
                let status = if !selected {
                    "not referenced"
                } else if resolved.is_none() {
                    "unsupported"
                } else if paths.is_empty() {
                    "missing"
                } else {
                    "included"
                };
                let files: Vec<_> = paths.iter().filter_map(|key| self.inventory.get(key)).map(|p| serde_json::json!({"path":format!("{prefix}/{}", p.strip_prefix(&self.root).unwrap_or(p).to_string_lossy().replace('\\', "/")), "bytes":fs::metadata(p).map(|m| m.len()).unwrap_or(0)})).collect();
                let bytes: u64 = files.iter().filter_map(|f| f["bytes"].as_u64()).sum();
                let sources = references
                    .get(&name.to_lowercase())
                    .cloned()
                    .unwrap_or_default();
                let reason = if status == "unsupported" {
                    "Excluded: Bevy cannot load this model format. Convert to glTF/GLB or remove the scene reference."
                } else if !selected {
                    "Not referenced by the reachable game; animation retarget options alone do not require a model."
                } else if !sources.is_empty() {
                    "Declared by the parsed game scripts."
                } else if all_assets {
                    "Included by explicit all-assets mode."
                } else if names.contains(&name.to_lowercase()) {
                    "Dependency of selected scripts, UI or asset metadata (including source-model aliases)."
                } else {
                    "Included by explicit all-assets mode."
                };
                result.push(serde_json::json!({"name":name,"catalog":format!("{prefix}/{catalog}"),"path":format!("{prefix}/{path}"),"resolvedPath":resolved.map(|p| format!("{prefix}/{p}")),"status":status,"reason":reason,"referencedBy":sources,"bytes":bytes,"files":files}));
            }
        }
        result.sort_by(|a, b| {
            a["name"]
                .as_str()
                .cmp(&b["name"].as_str())
                .then_with(|| a["path"].as_str().cmp(&b["path"].as_str()))
        });
        result
    }
    fn write(
        &self,
        context: &Context,
        report: &mut Vec<Value>,
        names: &BTreeSet<String>,
    ) -> io::Result<()> {
        for key in &self.selected {
            let source = &self.inventory[key];
            let relative = source.strip_prefix(&self.root).map_err(io::Error::other)?;
            let destination = self.destination.join(relative);
            files::copy(source, &destination, context)?;
            report.push(serde_json::json!({"file": destination.to_string_lossy(), "bytes": files::regular(source)?.len()}));
        }
        for (key, original) in &self.catalogs {
            let mut value = original.clone();
            if let Some(object) = value.as_object_mut() {
                for (group, value) in object {
                    if let Some(array) = value.as_array_mut() {
                        let mut i = 0;
                        array.retain(|row| {
                            let keep = !row["name"].is_string()
                                || self.rows.contains(&(key.clone(), group.clone(), i));
                            i += 1;
                            keep && !(row["path"]
                                .as_str()
                                .is_some_and(|p| p.to_lowercase().ends_with(".j3o"))
                                && !row["sourceModel"].is_string())
                        });
                        for row in array {
                            *row = runtime_row(row, names);
                        }
                    }
                }
            }
            let relative = self.inventory[key]
                .strip_prefix(&self.root)
                .map_err(io::Error::other)?;
            let target = self.destination.join(relative);
            fs::create_dir_all(
                target
                    .parent()
                    .ok_or_else(|| io::Error::other("Missing catalog parent."))?,
            )?;
            fs::write(target, serde_json::to_vec(&value)?)?;
        }
        Ok(())
    }
}
fn runtime_row(row: &Value, names: &BTreeSet<String>) -> Value {
    let mut row = row.clone();
    if let Some(retargets) = row
        .get_mut("bevyBakedRetargets")
        .and_then(Value::as_array_mut)
    {
        retargets.retain(|r| {
            r["model"]
                .as_str()
                .is_some_and(|m| names.contains(&m.to_lowercase()))
        });
    }
    row
}
fn supported_model(path: &str) -> bool {
    let path = path.to_ascii_lowercase();
    path.ends_with(".gltf") || path.ends_with(".glb")
}

fn decode_uri(uri: &str) -> io::Result<String> {
    let mut bytes = Vec::new();
    let mut input = uri.as_bytes().iter().copied();
    while let Some(b) = input.next() {
        if b == b'%' {
            let high = input.next().and_then(|c| (c as char).to_digit(16));
            let low = input.next().and_then(|c| (c as char).to_digit(16));
            match (high, low) {
                (Some(h), Some(l)) => bytes.push((h * 16 + l) as u8),
                _ => return Err(io::Error::other("Invalid escaped glTF URI.")),
            }
        } else {
            bytes.push(b);
        }
    }
    String::from_utf8(bytes).map_err(io::Error::other)
}
fn strings(value: &Value) -> Vec<String> {
    let mut out = Vec::new();
    match value {
        Value::String(s) => out.push(s.clone()),
        Value::Array(a) => {
            for v in a {
                out.extend(strings(v));
            }
        }
        Value::Object(o) => {
            for (key, v) in o {
                if key != "originalImportPath" {
                    out.extend(strings(v));
                }
            }
        }
        _ => {}
    }
    out
}

pub(super) fn snapshot(
    request: &Request,
    destination: &Path,
    report_path: &Path,
    context: &Context,
) -> io::Result<()> {
    let mut roots = vec![
        request.project.join("scripts"),
        request.project.join("resources"),
    ];
    if !request.settings.builtin_resources.is_empty() {
        roots.push(PathBuf::from(&request.settings.builtin_resources));
    }
    let stamps = inventory_stamps(&roots, context)?;
    snapshot_inner(request, destination, report_path, context)?;
    if stamps != inventory_stamps(&roots, context)? {
        return Err(io::Error::other(
            "Scripts or resources changed during dependency collection. Retry with a stable project.",
        ));
    }
    Ok(())
}
fn inventory_stamps(
    roots: &[PathBuf],
    context: &Context,
) -> io::Result<BTreeMap<PathBuf, (u64, std::time::SystemTime)>> {
    let mut stamps = BTreeMap::new();
    for root in roots.iter().filter(|p| p.is_dir()) {
        for path in files::list(root, context)? {
            let meta = files::regular(&path)?;
            stamps.insert(path, (meta.len(), meta.modified()?));
        }
    }
    Ok(stamps)
}
fn snapshot_inner(
    request: &Request,
    destination: &Path,
    report_path: &Path,
    context: &Context,
) -> io::Result<()> {
    let project = request.project.canonicalize()?;
    let entry = root_entry(&project, context)?;
    context.log(format!(
        "Compiling deployment graph from root main: {}\n",
        entry.display()
    ));
    let (mut scripts, mut names, references) = script_closure(&project, &entry, context)?;
    if request.settings.include_all_resources {
        scripts = files::list(&project.join("scripts"), context)?
            .into_iter()
            .collect();
        context.log("Compatibility mode: all resources except unsupported J3O payloads.\n");
    }
    for script in &scripts {
        files::copy(
            script,
            &destination.join(script.strip_prefix(&project).map_err(io::Error::other)?),
            context,
        )?;
    }
    let mut libraries = vec![Library::new(
        project.join("resources"),
        destination.join("resources"),
        context,
    )?];
    if !request.settings.builtin_resources.is_empty() {
        libraries.push(Library::new(
            Path::new(&request.settings.builtin_resources).canonicalize()?,
            destination.join("builtin/resources"),
            context,
        )?);
    }
    if request.settings.include_all_resources {
        for library in &libraries {
            for catalog in library.catalogs.values() {
                if let Some(object) = catalog.as_object() {
                    for value in object.values() {
                        if let Some(rows) = value.as_array() {
                            names.extend(
                                rows.iter()
                                    .filter_map(|r| r["name"].as_str())
                                    .map(str::to_lowercase),
                            );
                        }
                    }
                }
            }
        }
        for library in &mut libraries {
            for key in library.inventory.keys().cloned().collect::<Vec<_>>() {
                library.select(&key, &mut names)?;
            }
        }
    }
    loop {
        context.check()?;
        let before = (
            names.len(),
            libraries
                .iter()
                .map(|l| l.selected.len() + l.rows.len())
                .sum::<usize>(),
        );
        for library in &mut libraries {
            library.expand(&mut names)?;
        }
        let after = (
            names.len(),
            libraries
                .iter()
                .map(|l| l.selected.len() + l.rows.len())
                .sum::<usize>(),
        );
        if before == after {
            break;
        }
    }
    let mut report = Vec::new();
    for library in &libraries {
        library.write(context, &mut report, &names)?;
    }
    report.sort_by_key(|r| std::cmp::Reverse(r["bytes"].as_u64().unwrap_or(0)));
    let included: u64 = report.iter().filter_map(|r| r["bytes"].as_u64()).sum();
    let available: u64 = libraries
        .iter()
        .flat_map(|l| l.inventory.values())
        .map(|p| fs::metadata(p).map(|m| m.len()))
        .collect::<io::Result<Vec<_>>>()?
        .into_iter()
        .sum();
    for row in &mut report {
        if let Some(path) = row["file"].as_str() {
            row["file"] = Path::new(path)
                .strip_prefix(destination)
                .map_err(io::Error::other)?
                .to_string_lossy()
                .replace('\\', "/")
                .into();
        }
    }
    let models: Vec<_> = libraries
        .iter()
        .flat_map(|library| {
            library.model_inventory(
                &references,
                &names,
                destination,
                request.settings.include_all_resources,
            )
        })
        .collect();
    for model in &models {
        if model["status"] == "unsupported" {
            context.log(format!("WARNING: excluded unsupported model {} ({}). Convert to glTF/GLB or remove its scene reference.\n", model["name"], model["path"]));
        }
    }
    fs::write(
        report_path,
        serde_json::to_vec_pretty(
            &serde_json::json!({"resource_bytes": included, "excluded_bytes": available.saturating_sub(included), "script_files": scripts.len(), "files": report, "models": models}),
        )?,
    )?;
    context.log(format!("Dependencies: {} scripts/documents, {} resource files; {:.1} MiB included, {:.1} MiB excluded. See package-size.json.\n", scripts.len(), report.len(), included as f64 / 1048576., available.saturating_sub(included) as f64 / 1048576.));
    fs::create_dir_all(destination.join("resources"))?;
    Ok(())
}
