use super::*;
use serde_json::json;
fn fixture() -> (tempfile::TempDir, PathBuf, PathBuf) {
    let dir = tempfile::tempdir().unwrap();
    let a = dir.path().join("source");
    let b = dir.path().join("target");
    for p in [&a, &b] {
        fs::create_dir_all(p.join("scripts")).unwrap();
        fs::create_dir_all(p.join("resources/Models/sample")).unwrap();
    }
    fs::write(a.join("resources/Models/sample/mesh.gltf"),json!({"asset":{"version":"2.0"},"buffers":[{"uri":"../shared.bin","byteLength":4}],"images":[{"uri":"texture%20map.png"}]}).to_string()).unwrap();
    fs::write(a.join("resources/Models/shared.bin"), [1, 2, 3, 4]).unwrap();
    fs::write(a.join("resources/Models/sample/texture map.png"), [5, 6]).unwrap();
    fs::write(a.join("resources/Models/models-ext.json"),json!({"preserve":"yes","models":[{"name":"sample","path":"Models/sample/mesh.gltf","scaleX":2,"custom":{"keep":true}}]}).to_string()).unwrap();
    (dir, a, b)
}
fn chosen(inv: &Inventory) -> Vec<String> {
    vec![
        inv.assets
            .iter()
            .find(|a| a.category == "Models")
            .unwrap()
            .id
            .clone(),
    ]
}
#[test]
fn same_named_files_in_different_folders_have_distinct_identity() {
    let (_d, a, _) = fixture();
    for folder in ["first", "second"] {
        let path = a.join("resources/textures").join(folder);
        fs::create_dir_all(&path).unwrap();
        fs::write(path.join("albedo.png"), [1, 2]).unwrap();
    }
    let inv = scan(&a).unwrap();
    let assets: Vec<_> = inv.assets.iter().filter(|a| a.name == "albedo").collect();
    assert_eq!(assets.len(), 2);
    assert_ne!(assets[0].id, assets[1].id);
}
#[test]
fn copies_registration_and_external_gltf_dependencies_without_touching_source() {
    let (_d, a, b) = fixture();
    let inv = scan(&a).unwrap();
    let before = fs::read(a.join("resources/Models/models-ext.json")).unwrap();
    let p = review(&inv, &chosen(&inv), Some(&b), Operation::Copy).unwrap();
    assert!(p.conflicts.is_empty(), "{:?}", p.conflicts);
    assert!(p.lines.iter().any(|s| s.contains("shared.bin")));
    apply(p, |_, _| {}).unwrap();
    assert_eq!(
        fs::read(a.join("resources/Models/models-ext.json")).unwrap(),
        before
    );
    assert_eq!(
        fs::read(b.join("resources/Models/shared.bin")).unwrap(),
        [1, 2, 3, 4]
    );
    let dest = json(&b.join("resources/Models/models-ext.json")).unwrap();
    assert_eq!(dest["models"][0]["custom"]["keep"], true);
    assert!(
        review(&inv, &chosen(&inv), Some(&b), Operation::Copy)
            .unwrap()
            .conflicts
            .iter()
            .any(|s| s.contains("already registers"))
    );
}
#[test]
fn conflicting_files_and_changed_indices_never_overwrite() {
    let (_d, a, b) = fixture();
    fs::write(b.join("resources/Models/sample/mesh.gltf"), "different").unwrap();
    let inv = scan(&a).unwrap();
    let p = review(&inv, &chosen(&inv), Some(&b), Operation::Copy).unwrap();
    assert!(!p.conflicts.is_empty());
    assert!(apply(p, |_, _| {}).is_err());
    assert_eq!(
        fs::read_to_string(b.join("resources/Models/sample/mesh.gltf")).unwrap(),
        "different"
    );
    fs::remove_file(b.join("resources/Models/sample/mesh.gltf")).unwrap();
    let p = review(&inv, &chosen(&inv), Some(&b), Operation::Copy).unwrap();
    fs::write(a.join("resources/Models/models-ext.json"), "{}").unwrap();
    assert!(apply(p, |_, _| {}).is_err());
    assert!(!b.join("resources/Models/shared.bin").exists());
}
#[test]
fn move_keeps_payload_shared_by_other_registration_and_archives_index() {
    let (_d, a, b) = fixture();
    let path = a.join("resources/Models/models-ext.json");
    let mut data = json(&path).unwrap();
    let mut other = data["models"][0].clone();
    other["name"] = json!("other");
    data["models"].as_array_mut().unwrap().push(other);
    fs::write(&path, data.to_string()).unwrap();
    let inv = scan(&a).unwrap();
    let ids = vec![
        inv.assets
            .iter()
            .find(|a| a.name == "sample")
            .unwrap()
            .id
            .clone(),
    ];
    let plan = review(&inv, &ids, Some(&b), Operation::Move).unwrap();
    apply(plan, |_, _| {}).unwrap();
    assert!(a.join("resources/Models/sample/mesh.gltf").exists());
    let source = json(&path).unwrap();
    assert_eq!(source["models"].as_array().unwrap().len(), 1);
    assert_eq!(source["preserve"], "yes");
    assert!(a.join(".scenemax-studio/trash").is_dir());
}
#[test]
fn material_transfer_includes_texture_and_rejects_missing_dependency() {
    let (_d, a, b) = fixture();
    fs::create_dir_all(a.join("resources/Textures")).unwrap();
    fs::write(a.join("resources/Textures/a.png"), "pixels").unwrap();
    fs::write(
        a.join("scripts/surface.smmat"),
        json!({"version":1,"textures":{"baseColor":"Textures/a.png"}}).to_string(),
    )
    .unwrap();
    let inv = scan(&a).unwrap();
    let ids = vec![
        inv.assets
            .iter()
            .find(|a| a.name == "surface")
            .unwrap()
            .id
            .clone(),
    ];
    let plan = review(&inv, &ids, Some(&b), Operation::Copy).unwrap();
    assert!(plan.conflicts.is_empty(), "{:?}", plan.conflicts);
    apply(plan, |_, _| {}).unwrap();
    assert!(b.join("scripts/surface.smmat").is_file());
    assert!(b.join("resources/Textures/a.png").is_file());
    fs::remove_file(a.join("resources/Textures/a.png")).unwrap();
    assert!(
        !review(&inv, &ids, Some(&b), Operation::Copy)
            .unwrap()
            .conflicts
            .is_empty()
    );
}
#[test]
fn archive_and_rename_preserve_metadata_and_reject_collisions() {
    let (_d, a, _b) = fixture();
    let inv = scan(&a).unwrap();
    let ids = chosen(&inv);
    rename(&inv, &ids[0], "renamed").unwrap();
    let inv = scan(&a).unwrap();
    assert!(inv.assets.iter().any(|a| a.name == "renamed"));
    let ids = chosen(&inv);
    let plan = review(&inv, &ids, None, Operation::Archive).unwrap();
    apply(plan, |_, _| {}).unwrap();
    assert_eq!(
        json(&a.join("resources/Models/models-ext.json")).unwrap()["models"],
        json!([])
    );
    assert!(!a.join("resources/Models/sample/mesh.gltf").exists());
}
#[test]
fn escaping_dependency_is_a_conflict_and_empty_selection_rejected() {
    let (_d, a, b) = fixture();
    fs::write(
        a.join("resources/Models/sample/mesh.gltf"),
        json!({"asset":{"version":"2.0"},"buffers":[{"uri":"../../../../outside.bin"}]})
            .to_string(),
    )
    .unwrap();
    let inv = scan(&a).unwrap();
    assert!(
        !review(&inv, &chosen(&inv), Some(&b), Operation::Copy)
            .unwrap()
            .conflicts
            .is_empty()
    );
    assert!(review(&inv, &[], Some(&b), Operation::Copy).is_err());
}

#[test]
fn partial_copy_failure_rolls_back_only_our_files_and_preserves_concurrent_file() {
    let (_d, a, b) = fixture();
    let inv = scan(&a).unwrap();
    let plan = review(&inv, &chosen(&inv), Some(&b), Operation::Copy).unwrap();
    let conflict = b.join("resources/Models/sample/texture map.png");
    let result = apply(plan, |n, _| {
        if n == 1 {
            fs::write(&conflict, "concurrent write").unwrap();
        }
    });
    assert!(result.is_err());
    assert!(!b.join("resources/Models/sample/mesh.gltf").exists());
    assert!(!b.join("resources/Models/models-ext.json").exists());
    assert_eq!(fs::read_to_string(&conflict).unwrap(), "concurrent write");
    assert!(a.join("resources/Models/sample/mesh.gltf").exists());
}

#[test]
fn legacy_index_syntax_preserves_quoted_content() {
    let dir = tempfile::tempdir().unwrap();
    let file = dir.path().join("index.json");
    fs::write(
        &file,
        r#"{"fonts":[{name:"sample",path:"fonts/sample.fnt",note:"path: untouched, }",},],}"#,
    )
    .unwrap();
    let value = json(&file).unwrap();
    assert_eq!(value["fonts"][0]["name"], "sample");
    assert_eq!(value["fonts"][0]["note"], "path: untouched, }");
}
