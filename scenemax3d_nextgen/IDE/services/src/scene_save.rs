//! Companion-code regeneration on the filesystem worker.
use crate::{Filesystem, ServiceError, io_result};
use scenemax_ide_core::Document;
use std::{
    fs,
    io::{Read, Write},
    path::Path,
};

fn read(path: &Path) -> Result<Option<Vec<u8>>, ServiceError> {
    let metadata = match fs::symlink_metadata(path) {
        Ok(m) => m,
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => return Ok(None),
        Err(e) => {
            return Err(ServiceError::Io {
                operation: "Read scene companion",
                path: path.into(),
                source: e,
            });
        }
    };
    if metadata.file_type().is_symlink() || !metadata.is_file() {
        return Err(ServiceError::Limit(
            "Scene companions must be regular files",
        ));
    }
    let mut bytes = Vec::new();
    io_result(path, "Read scene companion", fs::File::open(path))?
        .take(crate::MAX_DOCUMENT_BYTES + 1)
        .read_to_end(&mut bytes)
        .map_err(|source| ServiceError::Io {
            operation: "Read scene companion",
            path: path.into(),
            source,
        })?;
    if bytes.len() as u64 > crate::MAX_DOCUMENT_BYTES {
        return Err(ServiceError::Limit("Scene companion exceeds 8 MiB"));
    }
    Ok(Some(bytes))
}
fn stage(path: &Path, bytes: &[u8]) -> Result<tempfile::NamedTempFile, ServiceError> {
    let parent = path
        .parent()
        .ok_or_else(|| ServiceError::OutsideProject(path.into()))?;
    let mut pending = io_result(
        path,
        "Stage generated scene code",
        tempfile::NamedTempFile::new_in(parent),
    )?;
    if let Ok(meta) = fs::metadata(path) {
        if meta.permissions().readonly() {
            return Err(ServiceError::ReadOnly(path.into()));
        }
        io_result(
            path,
            "Preserve generated code permissions",
            pending.as_file().set_permissions(meta.permissions()),
        )?;
    }
    io_result(path, "Write generated scene code", pending.write_all(bytes))?;
    io_result(
        path,
        "Flush generated scene code",
        pending.as_file().sync_all(),
    )?;
    Ok(pending)
}
fn commit(file: tempfile::NamedTempFile, path: &Path) -> Result<(), ServiceError> {
    file.persist(path)
        .map(|_| ())
        .map_err(|e| ServiceError::Io {
            operation: "Replace generated scene code",
            path: path.into(),
            source: e.error,
        })
}

pub(crate) fn save(doc: &mut Document) -> Result<Option<Document>, ServiceError> {
    if scenemax_ide_core::animation_analyzer::is_file(doc.path()) && doc.is_dirty() {
        crate::animation_analyzer::validate_changed(
            doc.path(),
            std::str::from_utf8(doc.saved_bytes()).unwrap_or(""),
            doc.text(),
        )
        .map_err(|message| ServiceError::Io {
            operation: "Validate animation records",
            path: doc.path().to_owned(),
            source: std::io::Error::other(message),
        })?;
    }
    if scenemax_ide_core::ik::is_file(doc.path()) {
        crate::ik::save(doc)?;
        return Ok(None);
    }
    if doc
        .path()
        .extension()
        .is_some_and(|e| e.eq_ignore_ascii_case("smmotion"))
    {
        crate::motion::save(doc)?;
        return Ok(None);
    }
    if doc
        .path()
        .extension()
        .is_some_and(|e| e.eq_ignore_ascii_case("smweapon"))
    {
        crate::weapon::save(doc)?;
        return Ok(None);
    }
    if !doc
        .path()
        .extension()
        .is_some_and(|e| e.eq_ignore_ascii_case("smdesign"))
    {
        Filesystem::save_plain(doc)?;
        return Ok(None);
    }
    let path = doc.path().to_owned();
    if read(&path)?.as_deref() != Some(doc.saved_bytes()) {
        return Err(ServiceError::ExternalChange(path));
    }
    let stem = path
        .file_stem()
        .and_then(|s| s.to_str())
        .ok_or(ServiceError::Limit("Invalid designer filename"))?;
    let companion = |suffix: &str| -> Result<String, ServiceError> {
        // New scenes use hyphens. Existing underscore companions remain readable;
        // an explicitly present new companion (even empty) takes precedence.
        let bytes = match read(&path.with_file_name(format!("{stem}-{suffix}.code")))? {
            Some(bytes) => bytes,
            None => {
                read(&path.with_file_name(format!("{stem}_{suffix}.code")))?.unwrap_or_default()
            }
        };
        String::from_utf8(bytes)
            .map(|s| s.trim_start_matches('\u{feff}').to_owned())
            .map_err(|_| ServiceError::Limit("Scene companion must contain UTF-8 source"))
    };
    let root = path
        .ancestors()
        .find(|p| p.file_name().is_some_and(|n| n == "scripts"))
        .and_then(Path::parent)
        .or_else(|| path.parent())
        .ok_or_else(|| ServiceError::OutsideProject(path.clone()))?;
    let materials = crate::material::documents(root)
        .map_err(ServiceError::SceneGeneration)?
        .into_keys()
        .collect::<Vec<_>>();
    let source = scenemax_ide_core::scene_codegen::generate(
        doc.text(),
        &path.file_name().unwrap_or_default().to_string_lossy(),
        &companion("init")?,
        &companion("end")?,
        &materials,
    )
    .map_err(ServiceError::SceneGeneration)?;
    let output = path.with_extension("code");
    let previous = read(&output)?;
    let generated = Document::from_bytes(output.clone(), source.as_bytes().to_vec())?;
    let pending = stage(&output, source.as_bytes())?;
    // Stage a rollback copy before changing either durable file.
    let rollback = previous
        .as_ref()
        .map(|bytes| stage(&output, bytes))
        .transpose()?;
    if read(&output)? != previous {
        return Err(ServiceError::ExternalChange(output));
    }
    if previous.is_none() {
        pending
            .persist_noclobber(&output)
            .map_err(|e| ServiceError::Io {
                operation: "Create generated scene code",
                path: output.clone(),
                source: e.error,
            })?;
    } else {
        commit(pending, &output)?;
    }
    if let Err(error) = Filesystem::save_plain(doc) {
        let restored = if let Some(rollback) = rollback {
            commit(rollback, &output)
        } else {
            io_result(
                &output,
                "Roll back generated scene code",
                fs::remove_file(&output),
            )
        };
        if let Err(rollback) = restored {
            return Err(ServiceError::SceneGeneration(format!(
                "{error}; companion rollback failed: {rollback}"
            )));
        }
        return Err(error);
    }
    Ok(Some(generated))
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;
    fn fixture() -> (tempfile::TempDir, Document) {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("scene.smdesign");
        let source = json!({"entities":[{"type":"SECTION","children":[{"type":"CODE","name":"Setup","codeText":"Logger.info \"old\"\n"},{"type":"BOX","name":"surface","sizeX":2,"sizeY":1,"sizeZ":3},{"type":"CODE","name":"Finish","codeText":"Logger.info \"tail\"\n"}]}]});
        fs::write(&path, source.to_string()).unwrap();
        fs::write(path.with_extension("code"), "// previous output\n").unwrap();
        let mut doc = Document::from_bytes(path, source.to_string().into_bytes()).unwrap();
        doc.replace_text(source.to_string().replace("old", "new"));
        (dir, doc)
    }
    #[test]
    fn explicit_save_of_clean_designer_repairs_stale_or_missing_generated_code() {
        let (dir, mut doc) = fixture();
        Filesystem::save_plain(&mut doc).unwrap();
        assert!(!doc.is_dirty());
        let output = dir.path().join("scene.code");
        for missing in [false, true] {
            if missing {
                fs::remove_file(&output).unwrap();
            }
            let generated = save(&mut doc)
                .unwrap()
                .expect("Save must regenerate even a clean designer");
            assert!(generated.text().contains("Logger.info \"new\""));
            assert_eq!(fs::read_to_string(&output).unwrap(), generated.text());
            assert!(!doc.is_dirty());
        }
        fs::write(doc.path(), "external edits").unwrap();
        let previous = fs::read(&output).unwrap();
        assert!(matches!(
            save(&mut doc),
            Err(ServiceError::ExternalChange(_))
        ));
        assert_eq!(fs::read(&output).unwrap(), previous);
    }
    #[test]
    fn saving_code_node_regenerates_in_order_with_companions_and_parser_accepts_output() {
        let (dir, mut doc) = fixture();
        fs::write(dir.path().join("scene_init.code"), "Logger.info \"init\"\n").unwrap();
        fs::write(dir.path().join("scene_end.code"), "Logger.info \"end\"\n").unwrap();
        let generated = save(&mut doc).unwrap().unwrap();
        assert!(!doc.is_dirty());
        let code = fs::read_to_string(dir.path().join("scene.code")).unwrap();
        assert_eq!(generated.text(), code);
        let ordered = [
            "\"init\"",
            "camera.pos",
            "\"new\"",
            "surface => box",
            "\"tail\"",
            "\"end\"",
        ];
        let positions = ordered.map(|s| code.find(s).unwrap());
        assert!(positions.windows(2).all(|p| p[0] < p[1]));
        assert!(!code.contains("\"old\""));
        scenemax_parser::parse_program(&code).unwrap();
        assert!(fs::read_to_string(doc.path()).unwrap().contains("new"));
        let mut next: serde_json::Value = serde_json::from_str(doc.text()).unwrap();
        next["entities"][0]["children"][0]["codeText"] = json!("");
        doc.replace_text(next.to_string());
        save(&mut doc).unwrap();
        assert!(
            !fs::read_to_string(dir.path().join("scene.code"))
                .unwrap()
                .contains("\"new\"")
        );
    }
    #[test]
    fn hyphenated_scene_companions_drive_generation_and_override_older_names() {
        let (dir, mut doc) = fixture();
        fs::write(
            dir.path().join("scene-init.code"),
            "Logger.info \"before scene\"\n",
        )
        .unwrap();
        fs::write(
            dir.path().join("scene-end.code"),
            "Logger.info \"after scene\"\n",
        )
        .unwrap();
        fs::write(
            dir.path().join("scene_init.code"),
            "Logger.info \"old init\"\n",
        )
        .unwrap();
        fs::write(
            dir.path().join("scene_end.code"),
            "Logger.info \"old end\"\n",
        )
        .unwrap();
        let generated = save(&mut doc).unwrap().unwrap();
        let code = generated.text();
        assert!(code.find("before scene").unwrap() < code.find("camera.pos").unwrap());
        assert!(code.find("after scene").unwrap() > code.find("surface => box").unwrap());
        assert!(!code.contains("old init") && !code.contains("old end"));
        scenemax_parser::parse_program(code).unwrap();
        fs::write(dir.path().join("scene-init.code"), "").unwrap();
        let generated = save(&mut doc).unwrap().unwrap();
        assert!(
            !generated.text().contains("before scene") && !generated.text().contains("old init")
        );
    }
    #[test]
    fn source_conflict_rolls_back_companion_and_keeps_buffer_dirty() {
        let (dir, mut doc) = fixture();
        fs::write(doc.path(), "external edits").unwrap();
        assert!(matches!(
            save(&mut doc),
            Err(ServiceError::ExternalChange(_))
        ));
        assert!(doc.is_dirty());
        assert_eq!(
            fs::read_to_string(dir.path().join("scene.code")).unwrap(),
            "// previous output\n"
        );
    }
    #[test]
    fn invalid_scene_does_not_replace_either_file() {
        let (dir, mut doc) = fixture();
        let original = fs::read(doc.path()).unwrap();
        doc.replace_text("{bad json".into());
        assert!(save(&mut doc).is_err());
        assert_eq!(fs::read(doc.path()).unwrap(), original);
        assert_eq!(
            fs::read_to_string(dir.path().join("scene.code")).unwrap(),
            "// previous output\n"
        );
        assert!(doc.is_dirty());
    }
    #[test]
    fn material_edit_keeps_generated_camera_and_model_transforms() {
        use scenemax_parser::{SceneMaxVec3, Statement};
        let mut scene = json!({"gameCamera":{"position":[8,-13,49],"rotation":[0,0,0,1]}, "entities":[
            {"type":"BOX","name":"surface","position":[0,-15,0]},
            {"type":"MODEL","name":"structure","resourcePath":"sample_structure","position":[22,-66,148],"scale":[30,30,30]},
            {"type":"MODEL","name":"level","resourcePath":"sample_level","position":[220,-90,210],"scale":[0.02,0.02,0.02]},
            {"type":"MODEL","name":"prop","resourcePath":"sample_prop","position":[1,2,3],"scale":[2,3,4]}
        ]});
        let generate = |scene: &serde_json::Value| {
            scenemax_ide_core::scene_codegen::generate(
                &scene.to_string(),
                "scene.smdesign",
                "",
                "",
                &["surface_material".into()],
            )
            .unwrap()
        };
        let before = scenemax_parser::parse_program(&generate(&scene)).unwrap();
        scene["entities"][0]["material"] = json!("surface_material");
        let code = generate(&scene);
        assert!(code.contains("scale 30 async"));
        assert!(code.contains("scale (2,3,4)"));
        let mut after = scenemax_parser::parse_program(&code).unwrap();
        let assignment = after
            .statements
            .iter()
            .position(|s| matches!(s, Statement::SetMaterial { .. }))
            .unwrap();
        after.statements.remove(assignment);
        assert_eq!(before.statements, after.statements);
        let models: Vec<_> = after
            .statements
            .iter()
            .filter_map(|s| {
                if let Statement::ModelDecl { name, options, .. } = s
                    && name != "surface"
                {
                    Some(options)
                } else {
                    None
                }
            })
            .collect();
        assert_eq!(
            models[0].scale,
            Some(SceneMaxVec3 {
                x: 30.,
                y: 30.,
                z: 30.
            })
        );
        assert_eq!(
            models[0].position,
            Some(SceneMaxVec3 {
                x: 22.,
                y: -66.,
                z: 148.
            })
        );
        assert_eq!(
            models[1].scale,
            Some(SceneMaxVec3 {
                x: 0.02,
                y: 0.02,
                z: 0.02
            })
        );
        assert_eq!(
            models[2].scale,
            Some(SceneMaxVec3 {
                x: 2.,
                y: 3.,
                z: 4.
            })
        );
    }

    #[test]
    fn generator_emits_materials_models_primitives_lights_and_paths() {
        let entities = json!([
            {"type":"MODEL","name":"mesh","resourcePath":"registered_model","material":"surface","shader":"effect","staticModel":true,"attachTo":"anchor","jointMapping":"A,B","modelCollisionShape":"box","ikAsset":"rig","ikLayers":[{"layerId":"reach","enabled":true,"target":"anchor"}]},
            {"type":"SPHERE","name":"anchor","radius":1},
            {"type":"QUAD","name":"panel"},{"type":"WEDGE","name":"ramp"},
            {"type":"CYLINDER","name":"column"},{"type":"CONE","name":"cone"},
            {"type":"HOLLOW_CYLINDER","name":"tube"},{"type":"STAIRS","name":"steps"},
            {"type":"ARCH","name":"archway"},
            {"type":"LIGHT","name":"sun","lightType":"directional","rotation":[0,std::f64::consts::FRAC_1_SQRT_2,0,std::f64::consts::FRAC_1_SQRT_2]},
            {"type":"PATH","name":"route","pathData":{"subdivisions":2,"controlPoints":[{"position":[0,0,0],"tangentOut":[0,0,1]},{"position":[0,0,3],"tangentIn":[0,0,-1]}]}}
        ]);
        let code = scenemax_ide_core::scene_codegen::generate(
            &json!({"entities":entities}).to_string(),
            "scene.smdesign",
            "",
            "",
            &[],
        )
        .unwrap();
        assert!(code.contains("mesh.material = \"surface\""));
        assert!(code.contains("mesh.shader = \"effect\""));
        scenemax_parser::parse_program(&code).unwrap_or_else(|e| panic!("{e:?}\n{code}"));
    }
}
