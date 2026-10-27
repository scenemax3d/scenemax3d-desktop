use super::*;
use std::collections::BTreeSet;
const INDEXES: &[(&str, &str, &str)] = &[
    ("Models/models", "models", "Models"),
    ("textures/textures", "textures", "Textures"),
    ("sprites/sprites", "sprites", "Sprites"),
    ("audio/audio", "sounds", "Audio"),
    ("videos/videos", "videos", "Videos"),
    ("fonts/fonts", "fonts", "Fonts"),
    ("animations/animations", "animations", "Animations"),
    ("shaders/shaders", "shaders", "Shaders"),
    (
        "environment_shaders/environment-shaders",
        "environmentShaders",
        "Shaders",
    ),
    ("material/materials", "materials", "Materials"),
    ("skyboxes/skyboxes", "skyboxes", "Skyboxes"),
    ("terrain/terrains", "terrains", "Terrain"),
    ("effects/effects", "effects", "Effekseer"),
];
fn category(ext: &str) -> Option<&'static str> {
    Some(match ext {
        "smmat" | "mat" | "j3m" => "Materials",
        "smdesign" => "Scenes",
        "smui" => "UI",
        "smshader" | "bvshader" | "smbevyshader" | "smenvshader" | "wgsl" | "j3md" => "Shaders",
        "smweapon" => "Weapons",
        "smmotion" => "Throw Motions",
        "smeffectdesign" | "efkefc" | "efkproj" | "efk" => "Effekseer",
        "smskybox" => "Skyboxes",
        "smterrain" => "Terrain",
        "smik" => "IK",
        "smcamera" | "smcinematic" => "Cinematics",
        _ => return None,
    })
}
fn find_case(root: &Path, rel: &str) -> Option<PathBuf> {
    let mut p = root.to_owned();
    for segment in rel.split('/') {
        p = fs::read_dir(p)
            .ok()?
            .filter_map(Result::ok)
            .find(|e| {
                e.file_name()
                    .to_string_lossy()
                    .eq_ignore_ascii_case(segment)
            })?
            .path();
    }
    Some(p)
}
fn file_path(data: &Value) -> Option<&str> {
    ["bevyPath", "path", "back", "front", "HeightMap", "Alpha"]
        .iter()
        .find_map(|k| data[*k].as_str().filter(|s| !s.is_empty()))
}
fn asset(
    context: (&Path, &Path, &str),
    cat: &str,
    name: String,
    file: Option<PathBuf>,
    data: Value,
    index: Option<PathBuf>,
    key: &str,
) -> Asset {
    let (project, root, origin) = context;
    let bytes = file
        .as_ref()
        .and_then(|p| fs::metadata(p).ok())
        .map(|m| m.len())
        .unwrap_or(0);
    let status = if file.as_ref().is_some_and(|p| !p.is_file()) {
        "Missing file"
    } else if file.as_ref().and_then(|p| p.extension()).is_some_and(|e| {
        e.eq_ignore_ascii_case("j3o")
            || e.eq_ignore_ascii_case("j3m")
            || e.eq_ignore_ascii_case("j3md")
    }) {
        "Legacy format — unavailable in Bevy"
    } else if file.is_none() {
        "Metadata registration"
    } else {
        "Ready"
    }
    .into();
    Asset {
        id: format!(
            "{}|{}|{}|{}",
            root.display(),
            cat,
            index
                .as_ref()
                .or(file.as_ref())
                .map(|p| p.display().to_string())
                .unwrap_or_default(),
            name
        ),
        name,
        category: cat.into(),
        origin: origin.into(),
        project: project.into(),
        root: root.into(),
        file,
        index,
        key: key.into(),
        data,
        bytes,
        status,
    }
}
fn resources(project: &Path, root: &Path, origin: &str, out: &mut Inventory) -> io::Result<()> {
    if !root.is_dir() {
        return Ok(());
    }
    let root = root.canonicalize()?;
    let files = walk(&root)?;
    let mut registered = BTreeSet::new();
    for (base, key, cat) in INDEXES {
        for suffix in ["-ext.json", ".json"] {
            let Some(index) = find_case(&root, &format!("{base}{suffix}")) else {
                continue;
            };
            if confined(
                &root,
                index
                    .strip_prefix(&root)
                    .map_err(|e| error(e.to_string()))?,
            )
            .is_err()
            {
                continue;
            }
            let data = match json(&index) {
                Ok(v) => v,
                Err(e) => {
                    out.warnings.push(format!("{}: {e}", index.display()));
                    continue;
                }
            };
            let Some(rows) = data[*key].as_array() else {
                out.warnings
                    .push(format!("{}: missing {key} array", index.display()));
                continue;
            };
            for row in rows {
                let name = row["name"].as_str().unwrap_or("unnamed").to_owned();
                let mut file = file_path(row)
                    .and_then(|p| confined(&root, Path::new(&p.replace('\\', "/"))).ok());
                if *cat == "Models"
                    && let Ok(res) = scenemax_assets::resolve_model_resource_with_builtin_fallback(
                        &root,
                        Some(&root),
                        &name,
                    )
                {
                    file = confined(
                        &root,
                        Path::new(
                            res.asset_path
                                .strip_prefix("builtin://")
                                .unwrap_or(&res.asset_path),
                        ),
                    )
                    .ok();
                }
                if *cat == "Animations"
                    && let Ok(res) = scenemax_assets::resolve_animation_resource(&root, &name)
                {
                    file = confined(&root, Path::new(&res.asset_path)).ok();
                }
                if let Some(p) = &file {
                    registered.insert(p.clone());
                }
                let mut item = asset(
                    (project, &root, origin),
                    cat,
                    name,
                    file,
                    row.clone(),
                    Some(
                        index
                            .strip_prefix(&root)
                            .map_err(|e| error(e.to_string()))?
                            .into(),
                    ),
                    key,
                );
                if out
                    .assets
                    .iter()
                    .any(|a| a.category == *cat && a.name.eq_ignore_ascii_case(&item.name))
                {
                    item.status = format!("Shadowed / duplicate • {}", item.status);
                }
                out.assets.push(item);
            }
        }
    }
    for file in files {
        if registered.contains(&file) {
            continue;
        }
        let ext = file
            .extension()
            .unwrap_or_default()
            .to_string_lossy()
            .to_lowercase();
        let relative = file
            .strip_prefix(&root)
            .map_err(|e| error(e.to_string()))?
            .to_string_lossy()
            .replace('\\', "/");
        let cat = category(&ext).or_else(|| {
            if relative.to_lowercase().starts_with("scenes/")
                && ["code", "png"].contains(&ext.as_str())
            {
                return Some("Scenes");
            }
            if relative.to_lowercase().starts_with("videos/")
                && ["mp4", "mov", "webm", "mkv", "avi", "mpeg", "mpg", "m4v"]
                    .contains(&ext.as_str())
            {
                return Some("Videos");
            }
            if ["png", "jpg", "jpeg", "bmp", "tga", "dds"].contains(&ext.as_str())
                && [
                    "textures/",
                    "models/",
                    "material/",
                    "materials/",
                    "effects/",
                ]
                .iter()
                .any(|p| relative.to_lowercase().starts_with(p))
            {
                Some("Textures")
            } else if relative.to_lowercase().starts_with("animations/")
                && ["glb", "gltf", "anim", "j3o"].contains(&ext.as_str())
            {
                Some("Animations")
            } else {
                None
            }
        });
        if let Some(cat) = cat {
            out.assets.push(asset(
                (project, &root, origin),
                cat,
                file.file_stem()
                    .unwrap_or_default()
                    .to_string_lossy()
                    .into_owned(),
                Some(file.clone()),
                if [
                    "Materials",
                    "Shaders",
                    "Weapons",
                    "Throw Motions",
                    "Scenes",
                    "UI",
                    "IK",
                    "Cinematics",
                    "Skyboxes",
                    "Terrain",
                ]
                .contains(&cat)
                {
                    json(&file).unwrap_or_else(|_| serde_json::json!({"path":relative}))
                } else {
                    serde_json::json!({"path":relative})
                },
                None,
                "",
            ));
        }
    }
    Ok(())
}
/// Scan active project and inherited installation assets without following links.
pub fn scan(project: &Path) -> io::Result<Inventory> {
    let project = project.canonicalize()?;
    let mut result = Inventory::default();
    resources(&project, &project.join("resources"), "Project", &mut result)?;
    let scripts = project.join("scripts");
    if scripts.is_dir() {
        let scripts = scripts.canonicalize()?;
        for file in walk(&scripts)? {
            let ext = file
                .extension()
                .unwrap_or_default()
                .to_string_lossy()
                .to_lowercase();
            if let Some(cat) = category(&ext) {
                let metadata = json(&file)
                    .unwrap_or_else(|_| serde_json::json!({"path":file.to_string_lossy()}));
                result.assets.push(asset(
                    (&project, &scripts, "Designer"),
                    cat,
                    file.file_stem()
                        .unwrap_or_default()
                        .to_string_lossy()
                        .into_owned(),
                    Some(file),
                    metadata,
                    None,
                    "",
                ));
            }
        }
    }
    if let Some(shared) = crate::resource_roots::shared(&project) {
        resources(&project, &shared, "Shared", &mut result)?;
    }
    result.assets.sort_by_key(|a| {
        (
            CATEGORIES
                .iter()
                .position(|c| *c == a.category)
                .unwrap_or(usize::MAX),
            a.name.to_lowercase(),
            a.origin.clone(),
        )
    });
    Ok(result)
}
