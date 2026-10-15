use super::*;
use crate::inventory::{confined, json};
use std::{
    fs,
    io::Write,
    path::{Path, PathBuf},
};

struct Lock(PathBuf);
impl Drop for Lock {
    fn drop(&mut self) {
        let _ = fs::remove_file(&self.0);
    }
}
fn snapshot(path: &Path) -> io::Result<Option<Vec<u8>>> {
    match fs::read(path) {
        Ok(v) => Ok(Some(v)),
        Err(e) if e.kind() == io::ErrorKind::NotFound => Ok(None),
        Err(e) => Err(e),
    }
}
/// Export `.fnt` and `.png` beside the project font index, then register atomically.
/// Existing names/files are rejected; failed registration removes only this export's files.
pub fn export(project: &Path, catalog: &Catalog, options: &Options) -> io::Result<String> {
    options.validate().map_err(error)?;
    options.validate_name().map_err(error)?;
    let project = project.canonicalize()?;
    if !project.join("scripts").is_dir() {
        return Err(error("Open a SceneMax project first."));
    }
    let lock = confined(&project, Path::new(".scenemax-studio/inventory.lock"))?;
    fs::create_dir_all(
        lock.parent()
            .ok_or_else(|| error("Missing project state folder"))?,
    )?;
    fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(&lock)
        .map_err(|_| error("Another asset operation is active for this project."))?;
    let _lock = Lock(lock);
    let folder = confined(&project, Path::new("resources/fonts"))?;
    let index = confined(&project, Path::new("resources/fonts/fonts-ext.json"))?;
    let mut indexes = vec![index.clone(), folder.join("fonts.json")];
    if let Some(shared) = project
        .ancestors()
        .skip(1)
        .map(|p| p.join("resources"))
        .find(|p| p.is_dir())
    {
        indexes.push(shared.join("fonts/fonts.json"));
        indexes.push(shared.join("fonts/fonts-ext.json"));
    }
    let mut guards = Vec::new();
    for path in indexes {
        let bytes = snapshot(&path)?;
        if let Some(bytes) = &bytes
            && !bytes.iter().all(u8::is_ascii_whitespace)
        {
            let value = json(&path)?;
            if let Some(rows) = value["fonts"].as_array()
                && rows.iter().any(|v| {
                    v["name"]
                        .as_str()
                        .is_some_and(|n| n.eq_ignore_ascii_case(&options.name))
                })
            {
                return Err(error(format!(
                    "A font named '{}' already exists.",
                    options.name
                )));
            }
        }
        guards.push((path, bytes));
    }
    let before = snapshot(&index)?;
    let mut value = if before
        .as_ref()
        .is_some_and(|b| !b.iter().all(u8::is_ascii_whitespace))
    {
        json(&index)?
    } else {
        serde_json::json!({"fonts":[]})
    };
    if value.get("fonts").is_none() {
        value["fonts"] = serde_json::json!([]);
    }
    let rows = value["fonts"]
        .as_array_mut()
        .ok_or_else(|| error("fonts-ext.json must contain a fonts array."))?;
    rows.push(serde_json::json!({"name":options.name,"path":format!("fonts/{}.fnt",options.name)}));
    let fnt = confined(
        &project,
        &Path::new("resources/fonts").join(format!("{}.fnt", options.name)),
    )?;
    let png = confined(
        &project,
        &Path::new("resources/fonts").join(format!("{}.png", options.name)),
    )?;
    if fnt.exists() || png.exists() {
        return Err(error(
            "An output file with this name already exists. Choose a different asset name.",
        ));
    }
    let atlas = build_atlas(catalog, options)?;
    fs::create_dir_all(&folder)?;
    let staged = tempfile::tempdir_in(&folder)?;
    atlas
        .image
        .save_with_format(staged.path().join("atlas.png"), image::ImageFormat::Png)
        .map_err(error)?;
    let mut staged_index = tempfile::NamedTempFile::new_in(&folder)?;
    staged_index.write_all(&serde_json::to_vec_pretty(&value)?)?;
    staged_index.as_file().sync_all()?;
    for (path, bytes) in guards {
        if snapshot(&path)? != bytes {
            return Err(error(
                "Font registration changed during generation. Export again.",
            ));
        }
    }
    let mut created = Vec::new();
    let result = (|| {
        let mut file = fs::OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(&png)?;
        created.push(png.clone());
        io::copy(
            &mut fs::File::open(staged.path().join("atlas.png"))?,
            &mut file,
        )?;
        file.sync_all()?;
        let mut file = fs::OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(&fnt)?;
        created.push(fnt.clone());
        file.write_all(atlas.descriptor.as_bytes())?;
        file.sync_all()?;
        if snapshot(&index)? != before {
            return Err(error(
                "Font registration changed during export. Export again.",
            ));
        }
        staged_index.persist(&index).map_err(|e| e.error)?;
        Ok::<_, io::Error>(())
    })();
    if let Err(e) = result {
        let mut failed = false;
        for path in created {
            failed |= fs::remove_file(path).is_err();
        }
        return Err(error(format!(
            "{e}{}",
            if failed {
                ". Could not clean all new output files; inspect resources/fonts."
            } else {
                ""
            }
        )));
    }
    Ok(format!(
        "Font exported and registered successfully.\n{} characters · {} × {} atlas\n{}\n{}\n{}",
        atlas.characters,
        atlas.image.width(),
        atlas.image.height(),
        fnt.display(),
        png.display(),
        index.display()
    ))
}
