use super::*;
fn request(kind: Kind, source: PathBuf) -> Request {
    Request {
        kind,
        source,
        name: "test_asset".into(),
        rows: 2,
        cols: 3,
        frame_width: 16.,
        frame_height: 24.,
        clip: String::new(),
        scale: 2.,
        model: None,
            effect: None,
    }
}
#[test]
fn each_import_registers_the_runtime_format_and_preserves_other_entries() {
    for kind in [
        Kind::Model,
        Kind::Animation,
        Kind::Sprite,
        Kind::Audio,
        Kind::Video,
        Kind::Effect,
    ] {
        let project = tempfile::tempdir().unwrap();
        let source = tempfile::tempdir().unwrap();
        let extension = kind.extensions()[if matches!(kind, Kind::Model | Kind::Animation) {
            1
        } else {
            0
        }];
        let path = source.path().join(format!("source.{extension}"));
        fs::write(&path,if extension=="gltf" {br#"{"asset":{"version":"2.0"},"animations":[{"name":"walk"}],"buffers":[{"uri":"data.bin"}]}"#.as_slice()} else {b"sample"}).unwrap();
        if kind == Kind::Effect { fs::write(&path, empty_effect()).unwrap(); }
        fs::write(source.path().join("data.bin"), [0; 8]).unwrap();
        let (folder, stem, key) = kind.location();
        let category = project.path().join("resources").join(folder);
        fs::create_dir_all(&category).unwrap();
        fs::write(
            category.join(format!("{stem}-ext.json")),
            serde_json::to_vec(&json!({"custom":42,key:[{"name":"existing","path":"old"}]}))
                .unwrap(),
        )
        .unwrap();
        let result = import(project.path(), &request(kind, path.clone())).unwrap();
        assert!(result.asset.is_file());
        assert!(result.document.is_file());
        assert!(path.is_file());
        if kind == Kind::Effect {
            let doc: Value = serde_json::from_slice(&fs::read(result.document).unwrap()).unwrap();
            assert_eq!(
                doc["source"]["importedEffectFile"],
                "effects/test_asset/source.efkefc"
            );
        } else {
            let doc: Value = serde_json::from_slice(&fs::read(result.document).unwrap()).unwrap();
            assert_eq!(doc["custom"], 42);
            assert_eq!(doc[key][0]["name"], "existing");
            assert_eq!(doc[key][1]["name"], "test_asset");
            if kind == Kind::Sprite {
                assert_eq!(doc[key][1]["cols"], 3);
            }
            if kind == Kind::Animation {
                assert_eq!(doc[key][1]["clipName"], "walk");
            }
            if kind == Kind::Model {
                assert_eq!(doc[key][1]["scaleX"], 2.);
            }
        }
        assert!(import(project.path(), &request(kind, path)).is_err());
    }
}
#[test]
fn missing_or_escaping_dependencies_leave_no_registration_or_asset_folder() {
    for uri in ["missing.bin", "../outside.bin", "%2e%2e/outside.bin"] {
        let project = tempfile::tempdir().unwrap();
        let source = tempfile::tempdir().unwrap();
        let path = source.path().join("model.gltf");
        fs::write(
            &path,
            serde_json::to_vec(&json!({"asset":{"version":"2.0"},"buffers":[{"uri":uri}]}))
                .unwrap(),
        )
        .unwrap();
        assert!(import(project.path(), &request(Kind::Model, path)).is_err());
        assert!(
            !project
                .path()
                .join("resources/Models/models-ext.json")
                .exists()
        );
        assert!(!project.path().join("resources/Models/test_asset").exists());
        assert!(
            !project
                .path()
                .join("resources/Models/.asset-import.lock")
                .exists()
        );
    }
}
#[test]
fn effect_companions_and_gltf_encoded_paths_are_preserved() {
    let project = tempfile::tempdir().unwrap();
    let source = tempfile::tempdir().unwrap();
    fs::create_dir(source.path().join("Texture")).unwrap();
    fs::write(source.path().join("Texture/spark.png"), b"image").unwrap();
    let effect = source.path().join("spark.efkefc");
    fs::write(&effect, empty_effect()).unwrap();
    let out = import(project.path(), &request(Kind::Effect, effect)).unwrap();
    assert!(
        out.asset
            .parent()
            .unwrap()
            .join("Texture/spark.png")
            .is_file()
    );
    fs::write(source.path().join("mesh data.bin"), [0; 8]).unwrap();
    let model = source.path().join("model.gltf");
    fs::write(
        &model,
        br#"{"asset":{"version":"2.0"},"buffers":[{"uri":"mesh%20data.bin"}]}"#,
    )
    .unwrap();
    let out = import(project.path(), &request(Kind::Model, model)).unwrap();
    assert!(out.asset.parent().unwrap().join("mesh data.bin").is_file());
}
#[test]
fn invalid_clip_and_name_never_publish() {
    let project = tempfile::tempdir().unwrap();
    let source = tempfile::tempdir().unwrap();
    let file = source.path().join("anim.gltf");
    fs::write(
        &file,
        br#"{"asset":{"version":"2.0"},"animations":[{"name":"walk"}]}"#,
    )
    .unwrap();
    let mut req = request(Kind::Animation, file);
    req.clip = "absent".into();
    assert!(
        import(project.path(), &req)
            .unwrap_err()
            .to_string()
            .contains("Available clips: walk")
    );
    req.clip.clear();
    req.name = "../escape".into();
    assert!(import(project.path(), &req).is_err());
    assert!(
        !project
            .path()
            .join("resources/animations/animations-ext.json")
            .exists()
    );
}

fn empty_effect() -> Vec<u8> { [b"SKFE".as_slice(),&[0;8]].concat() }
