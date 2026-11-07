//! Recover the installed starter project without touching existing projects.
use super::*;
use std::fs;

const SCRIPT: &str =
    include_str!("../../../../../INNO-SETUP/starter-projects/Hello World/scripts/main");
const MODELS: &str = include_str!(
    "../../../../../INNO-SETUP/starter-projects/Hello World/resources/Models/models-ext.json"
);

pub(super) fn ensure(installation: &Path) -> Result<(), ServiceError> {
    let projects = installation.join("projects");
    io_result(
        &projects,
        "Create projects folder",
        fs::create_dir_all(&projects),
    )?;
    let catalog_path = projects.join("projects.json");
    let mut value: serde_json::Value = if catalog_path.exists() {
        let bytes = io_result(
            &catalog_path,
            "Read project catalog",
            fs::read(&catalog_path),
        )?;
        serde_json::from_slice(bytes.strip_prefix(&[0xef, 0xbb, 0xbf]).unwrap_or(&bytes))
            .map_err(|e| ServiceError::Catalog(e.to_string()))?
    } else {
        serde_json::json!({"projects":[]})
    };
    let rows = value
        .get_mut("projects")
        .and_then(serde_json::Value::as_array_mut)
        .ok_or_else(|| ServiceError::Catalog("Missing projects list".into()))?;
    // An external catalog project counts too. Never replace a user's catalog.
    if rows.iter().any(|row| {
        row["path"]
            .as_str()
            .is_some_and(|p| installation.join(p).is_dir())
    }) {
        return Ok(());
    }
    // Recover projects still on disk even if the catalog was removed or emptied.
    let mut discovered = Vec::new();
    for entry in io_result(&projects, "Find projects", fs::read_dir(&projects))? {
        let entry = io_result(&projects, "Read project folder", entry)?;
        if entry.path().join("scripts").is_dir()
            && !entry.file_name().to_string_lossy().starts_with('.')
        {
            let name = entry.file_name().to_string_lossy().into_owned();
            discovered.push(serde_json::json!({"name":name, "path":format!("projects/{name}")}));
        }
    }
    discovered.sort_by_key(|row| row["name"].as_str().unwrap_or("").to_owned());
    if discovered.is_empty() {
        let root = projects.join("Hello World");
        if root.exists() {
            return Err(ServiceError::Catalog("Cannot create the starter project: projects/Hello World already exists. Your files were preserved.".into()));
        }
        let pending = io_result(
            &projects,
            "Prepare starter project",
            tempfile::Builder::new()
                .prefix(".starter-")
                .tempdir_in(&projects),
        )?;
        let staged = pending.path().join("Hello World");
        let models = staged.join("resources/Models");
        io_result(
            &staged,
            "Create starter folders",
            fs::create_dir_all(&models),
        )?;
        io_result(
            &staged,
            "Create starter scripts",
            fs::create_dir_all(staged.join("scripts")),
        )?;
        io_result(
            &staged,
            "Write starter script",
            fs::write(staged.join("scripts/main"), SCRIPT),
        )?;
        io_result(
            &models,
            "Write starter models",
            fs::write(models.join("models-ext.json"), MODELS),
        )?;
        let source = installation.join("resources/Models/dragon1");
        let target = models.join("dragon1");
        let model = source.join("scene.gltf");
        let bytes = io_result(&model, "Read starter model", fs::read(&model))?;
        let gltf: serde_json::Value =
            serde_json::from_slice(&bytes).map_err(|e| ServiceError::Catalog(e.to_string()))?;
        io_result(
            &target,
            "Create starter model folder",
            fs::create_dir_all(&target),
        )?;
        io_result(
            &target,
            "Copy starter model",
            fs::write(target.join("scene.gltf"), bytes),
        )?;
        for resource in gltf["buffers"]
            .as_array()
            .into_iter()
            .flatten()
            .chain(gltf["images"].as_array().into_iter().flatten())
        {
            let Some(uri) = resource["uri"].as_str().filter(|s| !s.starts_with("data:")) else {
                continue;
            };
            let relative = Path::new(uri);
            if relative.is_absolute()
                || relative.components().any(|p| {
                    !matches!(
                        p,
                        std::path::Component::Normal(_) | std::path::Component::CurDir
                    )
                })
            {
                return Err(ServiceError::Catalog(
                    "Invalid starter model resource path".into(),
                ));
            }
            let destination = target.join(relative);
            io_result(
                &destination,
                "Create model resource folder",
                fs::create_dir_all(destination.parent().unwrap()),
            )?;
            io_result(
                &destination,
                "Copy starter model resource",
                fs::copy(source.join(relative), &destination),
            )?;
        }
        io_result(&root, "Publish starter project", fs::rename(&staged, &root))?;
        discovered.push(serde_json::json!({"name":"Hello World","path":"projects/Hello World"}));
    }
    let selected = discovered[0]["name"].clone();
    *rows = discovered;
    value["selectedProject"] = selected;
    let bytes =
        serde_json::to_vec_pretty(&value).map_err(|e| ServiceError::Catalog(e.to_string()))?;
    let mut pending = io_result(
        &projects,
        "Prepare project catalog",
        tempfile::NamedTempFile::new_in(&projects),
    )?;
    use std::io::Write;
    io_result(
        &catalog_path,
        "Write project catalog",
        pending.write_all(&bytes),
    )?;
    io_result(
        &catalog_path,
        "Publish project catalog",
        pending.persist(&catalog_path).map_err(|e| e.error),
    )?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    fn installation() -> tempfile::TempDir {
        let dir = tempfile::tempdir().unwrap();
        fs::write(dir.path().join(".scenemax-studio-installation"), "1").unwrap();
        let model = dir.path().join("resources/Models/dragon1");
        fs::create_dir_all(model.join("textures")).unwrap();
        fs::write(
            model.join("scene.gltf"),
            r#"{"buffers":[{"uri":"scene.bin"}],"images":[{"uri":"textures/skin.png"}]}"#,
        )
        .unwrap();
        fs::write(model.join("scene.bin"), b"model buffer").unwrap();
        fs::write(model.join("textures/skin.png"), b"texture").unwrap();
        dir
    }
    #[test]
    fn missing_empty_and_stale_collections_create_complete_selected_starter() {
        for state in 0..4 {
            let dir = installation();
            let projects = dir.path().join("projects");
            if state > 0 {
                fs::create_dir(&projects).unwrap();
            }
            if state == 2 {
                fs::write(
                    projects.join("projects.json"),
                    r#"{"projects":[],"custom":true}"#,
                )
                .unwrap();
            }
            if state == 3 {
                fs::write(projects.join("projects.json"), r#"{"projects":[{"name":"Deleted","path":"projects/deleted"}],"selectedProject":"Deleted"}"#).unwrap();
            }
            let catalog = super::super::load_startup_catalog(dir.path(), None).unwrap();
            let starter = projects.join("Hello World");
            assert_eq!(catalog.selected, Some(starter.canonicalize().unwrap()));
            assert_eq!(catalog.projects.len(), 1);
            assert_eq!(
                fs::read_to_string(starter.join("scripts/main")).unwrap(),
                SCRIPT
            );
            assert_eq!(
                fs::read_to_string(starter.join("resources/Models/models-ext.json")).unwrap(),
                MODELS
            );
            assert_eq!(
                fs::read(starter.join("resources/Models/dragon1/scene.bin")).unwrap(),
                b"model buffer"
            );
            assert!(
                starter
                    .join("resources/Models/dragon1/textures/skin.png")
                    .is_file()
            );
            fs::write(starter.join("scripts/main"), "user edits").unwrap();
            super::super::load_startup_catalog(dir.path(), None).unwrap();
            assert_eq!(
                fs::read_to_string(starter.join("scripts/main")).unwrap(),
                "user edits"
            );
        }
    }
    #[test]
    fn missing_catalog_recovers_existing_projects_without_overwriting_files() {
        let dir = installation();
        let project = dir.path().join("projects/My Game/scripts");
        fs::create_dir_all(&project).unwrap();
        fs::write(project.join("main"), "user code").unwrap();
        let catalog = super::super::load_startup_catalog(dir.path(), None).unwrap();
        assert_eq!(catalog.projects[0].name, "My Game");
        assert!(!dir.path().join("projects/Hello World").exists());
        assert_eq!(
            fs::read_to_string(project.join("main")).unwrap(),
            "user code"
        );
        let bytes = fs::read(dir.path().join("projects/projects.json")).unwrap();
        super::super::load_startup_catalog(dir.path(), None).unwrap();
        assert_eq!(
            fs::read(dir.path().join("projects/projects.json")).unwrap(),
            bytes
        );
    }
    #[test]
    fn missing_assets_or_conflicting_folder_never_publish_partial_project() {
        let dir = installation();
        fs::remove_file(dir.path().join("resources/Models/dragon1/scene.bin")).unwrap();
        assert!(super::super::load_startup_catalog(dir.path(), None).is_err());
        assert!(!dir.path().join("projects/Hello World").exists());
        assert!(!dir.path().join("projects/projects.json").exists());
        fs::create_dir(dir.path().join("projects/Hello World")).unwrap();
        fs::write(dir.path().join("projects/Hello World/notes.txt"), "keep").unwrap();
        assert!(super::super::load_startup_catalog(dir.path(), None).is_err());
        assert_eq!(
            fs::read_to_string(dir.path().join("projects/Hello World/notes.txt")).unwrap(),
            "keep"
        );
    }
}
