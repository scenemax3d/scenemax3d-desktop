use super::*;
use std::{fs, sync::atomic::AtomicBool};
#[test]
fn size_analysis_reconciles_categories_and_limits_largest_files() {
    let root = tempfile::tempdir().unwrap();
    let snapshot = root.path().join("snapshot");
    for directory in [
        "resources/Models/demo",
        "builtin/resources/audio",
        "scripts",
        "resources/custom",
    ] {
        fs::create_dir_all(snapshot.join(directory)).unwrap();
    }
    fs::write(
        snapshot.join("resources/Models/demo/model.glb"),
        vec![0; 200],
    )
    .unwrap();
    fs::write(
        snapshot.join("builtin/resources/audio/sound.ogg"),
        vec![0; 300],
    )
    .unwrap();
    fs::write(snapshot.join("scripts/main"), vec![0; 10]).unwrap();
    for i in 0..42 {
        fs::write(snapshot.join(format!("resources/custom/{i:02}.bin")), [0]).unwrap();
    }
    let path = size_report::write(&snapshot, root.path(), &context()).unwrap();
    let value: serde_json::Value =
        serde_json::from_slice(&fs::read(root.path().join("size-report-analysis.json")).unwrap())
            .unwrap();
    assert_eq!(value["deployTotalBytes"], 552);
    assert!(value["deployTotalMiB"].is_number());
    assert_eq!(
        value["categories"]
            .as_array()
            .unwrap()
            .iter()
            .map(|r| r["bytes"].as_u64().unwrap())
            .sum::<u64>(),
        552
    );
    assert_eq!(
        value["categories"]
            .as_array()
            .unwrap()
            .iter()
            .map(|r| r["count"].as_u64().unwrap())
            .sum::<u64>(),
        45
    );
    assert_eq!(value["topContributors"].as_array().unwrap().len(), 40);
    assert_eq!(
        value["topContributors"][0]["path"],
        "builtin/resources/audio/sound.ogg"
    );
    let text = fs::read_to_string(path).unwrap();
    for section in [
        "SceneMax Packaging Size Report",
        "Asset Categories",
        "Top Contributors",
        "Notes",
        "audio (shared)",
        "resources / other",
    ] {
        assert!(text.contains(section));
    }
    assert!(!snapshot.join("size-report-analysis.txt").exists());
    let empty = root.path().join("empty");
    fs::create_dir(&empty).unwrap();
    size_report::write(&empty, root.path(), &context()).unwrap();
    let value: serde_json::Value =
        serde_json::from_slice(&fs::read(root.path().join("size-report-analysis.json")).unwrap())
            .unwrap();
    assert_eq!(value["deployTotalBytes"], 0);
    assert!(value["categories"].as_array().unwrap().is_empty());
}
#[test]
fn single_file_archive_deduplicates_verified_identical_resources() {
    let root = tempfile::tempdir().unwrap();
    let source = root.path().join("payload");
    fs::create_dir_all(source.join("resources")).unwrap();
    fs::create_dir_all(source.join("builtin/resources")).unwrap();
    fs::write(source.join("resources/shared.bin"), vec![3; 100_000]).unwrap();
    fs::write(
        source.join("builtin/resources/shared.bin"),
        vec![3; 100_000],
    )
    .unwrap();
    fs::write(source.join("resources/different.bin"), vec![4; 100_000]).unwrap();
    let stub = root.path().join("stub");
    fs::write(&stub, "MZ stub").unwrap();
    let package = root.path().join("game.exe");
    files::zip(&source, &package, Some(&stub), &context()).unwrap();
    let mut archive = zip::ZipArchive::new(fs::File::open(package).unwrap()).unwrap();
    assert_eq!(archive.len(), 3); // Two unique resources and the alias manifest.
    let aliases: serde_json::Value =
        serde_json::from_reader(archive.by_name(".scenemax-package-aliases.json").unwrap())
            .unwrap();
    assert_eq!(
        aliases["resources/shared.bin"],
        "builtin/resources/shared.bin"
    );
    assert!(archive.by_name("resources/different.bin").is_ok());
}
#[test]
fn dependency_package_follows_scenes_and_gltf_without_shipping_import_sources() {
    let root = tempfile::tempdir().unwrap();
    for dir in [
        "scripts/start",
        "scripts/start/next",
        "resources/Models/used",
        "resources/Models/unused",
        "resources/audio",
    ] {
        fs::create_dir_all(root.path().join(dir)).unwrap();
    }
    fs::write(
        root.path().join("scripts/start/main"),
        "switch to \"next\" // unused\n",
    )
    .unwrap();
    fs::write(
        root.path().join("scripts/start/next/main"),
        "add \"objects.code\" code\n",
    )
    .unwrap();
    fs::write(
        root.path().join("scripts/start/next/objects.code"),
        "item is a used\nitem.switch to character mode\n",
    )
    .unwrap();
    fs::write(
        root.path().join("scripts/start/unused.code"),
        "item is an unused\n",
    )
    .unwrap();
    fs::write(root.path().join("resources/Models/models-ext.json"), r#"{"models":[{"name":"used","path":"Models/used/scene.gltf"},{"name":"unused","path":"Models/unused/scene.glb"}],"custom":7}"#).unwrap();
    fs::write(
        root.path().join("resources/Models/used/scene.gltf"),
        r#"{"buffers":[{"uri":"mesh.bin"}],"images":[{"uri":"map.png"}]}"#,
    )
    .unwrap();
    for name in ["mesh.bin", "map.png", "scene.j3o", "source.fbx"] {
        fs::write(root.path().join("resources/Models/used").join(name), name).unwrap();
    }
    fs::write(
        root.path().join("resources/Models/unused/scene.glb"),
        b"unused",
    )
    .unwrap();
    // A caller's currently active scene must not replace the project's root main.
    let request = Request {
        project: root.path().to_owned(),
        entry: root.path().join("scripts/start/next/objects.code"),
        workspace: workspace(),
        settings: Default::default(),
    };
    let output = root.path().join("snapshot");
    assets::snapshot(
        &request,
        &output,
        &root.path().join("size.json"),
        &context(),
    )
    .unwrap();
    assert!(output.join("resources/Models/used/mesh.bin").is_file());
    assert!(output.join("resources/Models/used/map.png").is_file());
    assert!(!output.join("resources/Models/used/scene.j3o").exists());
    assert!(!output.join("resources/Models/unused/scene.glb").exists());
    assert!(!output.join("scripts/start/unused.code").exists());
    assert!(output.join("scripts/start/main").is_file());
    let index: serde_json::Value =
        serde_json::from_slice(&fs::read(output.join("resources/Models/models-ext.json")).unwrap())
            .unwrap();
    assert_eq!(index["models"].as_array().unwrap().len(), 1);
    assert_eq!(index["custom"], 7);
    assert!(
        fs::read_to_string(root.path().join("resources/Models/models-ext.json"))
            .unwrap()
            .contains("unused")
    );
}

#[test]
#[ignore = "Uses SCENEMAX_PACKAGE_BENCH_PROJECT; never uploads or saves project settings"]
fn benchmark_dependency_snapshot() {
    let project = PathBuf::from(std::env::var_os("SCENEMAX_PACKAGE_BENCH_PROJECT").unwrap())
        .canonicalize()
        .unwrap();
    let opened = crate::Filesystem::open_project(&project).unwrap();
    let request = Request {
        entry: opened.entry_point().unwrap().to_owned(),
        settings: files::load_settings(&project).unwrap(),
        project,
        workspace: workspace(),
    };
    let output = tempfile::Builder::new()
        .prefix("dependency-bench-")
        .tempdir_in(workspace().join("target"))
        .unwrap()
        .keep();
    let started = std::time::Instant::now();
    assets::snapshot(
        &request,
        &output.join("snapshot"),
        &output.join("package-size.json"),
        &context(),
    )
    .unwrap();
    println!(
        "Snapshot: {} in {:.2}s",
        output.display(),
        started.elapsed().as_secs_f64()
    );
    let entry = assets::root_entry(&request.project, &context()).unwrap();
    fs::write(output.join("snapshot/launch.json"), serde_json::to_vec(&serde_json::json!({"script":entry.strip_prefix(&request.project).unwrap().to_string_lossy().replace('\\', "/")})).unwrap()).unwrap();
    size_report::write(&output.join("snapshot"), &output, &context()).unwrap();
    if std::env::var_os("SCENEMAX_PACKAGE_REPORT_ONLY").is_some() {
        println!(
            "Size report: {}",
            output.join("size-report-analysis.txt").display()
        );
        return;
    }
    let platform = request
        .settings
        .platforms
        .iter()
        .find(|p| p.selected)
        .unwrap();
    let destination = output.join(platform.target.id());
    fs::create_dir(&destination).unwrap();
    fs::write(output.join("build.log"), "").unwrap();
    let artifact = pipeline::desktop(
        &request,
        platform,
        &output.join("snapshot"),
        &output.join("staging"),
        &destination,
        &context(),
        &output.join("build.log"),
    )
    .unwrap();
    println!(
        "Artifact: {} ({:.1} MiB) in {:.2}s total",
        artifact.display(),
        fs::metadata(&artifact).unwrap().len() as f64 / 1048576.,
        started.elapsed().as_secs_f64()
    );
}
#[test]
fn process_streams_both_channels_and_reports_failure() {
    let root = tempfile::tempdir().unwrap();
    let log = root.path().join("build.log");
    let c = context();
    #[cfg(windows)]
    let mut command = {
        let mut c = std::process::Command::new("powershell.exe");
        c.args(["-NoProfile", "-Command", "[Console]::Out.WriteLine('stdout-marker'); [Console]::Error.WriteLine('stderr-marker'); exit 7"]);
        c
    };
    #[cfg(not(windows))]
    let mut command = {
        let mut c = std::process::Command::new("sh");
        c.args(["-c", "echo stdout-marker; echo stderr-marker >&2; exit 7"]);
        c
    };
    assert!(
        process::run(&mut command, &c, &log)
            .unwrap_err()
            .to_string()
            .contains("exited")
    );
    let text = fs::read_to_string(log).unwrap();
    assert!(text.contains("stdout-marker"));
    assert!(text.contains("stderr-marker"));
}
#[test]
fn owned_process_cancels_promptly() {
    let root = tempfile::tempdir().unwrap();
    let c = context();
    let cancel = c.cancel.clone();
    #[cfg(windows)]
    let mut command = {
        let mut c = std::process::Command::new("powershell.exe");
        c.args(["-NoProfile", "-Command", "Start-Sleep -Seconds 60"]);
        c
    };
    #[cfg(not(windows))]
    let mut command = {
        let mut c = std::process::Command::new("sleep");
        c.arg("60");
        c
    };
    let signal = std::thread::spawn(move || {
        std::thread::sleep(std::time::Duration::from_millis(300));
        cancel.store(true, Ordering::Relaxed);
    });
    let started = std::time::Instant::now();
    assert_eq!(
        process::run(&mut command, &c, &root.path().join("log"))
            .unwrap_err()
            .kind(),
        io::ErrorKind::Interrupted
    );
    signal.join().unwrap();
    assert!(started.elapsed().as_secs() < 10);
}
#[test]
#[ignore = "Requires a built release launcher and the Rust compiler"]
fn actual_single_file_launches_from_an_unrelated_directory_and_cleans_up() {
    let root = tempfile::tempdir().unwrap();
    let payload = root.path().join("payload");
    fs::create_dir_all(payload.join("scripts/nested")).unwrap();
    fs::create_dir_all(payload.join("resources")).unwrap();
    fs::write(payload.join("scripts/nested/main"), b"source fixture").unwrap();
    fs::write(payload.join("resources/probe"), b"resource fixture").unwrap();
    fs::write(
        payload.join("launch.json"),
        br#"{"script":"scripts/nested/main"}"#,
    )
    .unwrap();
    let mock = root.path().join("mock.rs");
    fs::write(&mock, r#"fn main() {
        let args: Vec<_> = std::env::args().collect();
        assert_eq!(args[1], "run"); assert_eq!(args[2], "--project-root"); assert_eq!(args[4], "--script");
        assert_eq!(std::fs::read(&args[5]).unwrap(), b"source fixture");
        assert_eq!(std::fs::read("resources/probe").unwrap(), b"resource fixture");
        let dir = std::env::current_dir().unwrap();
        std::fs::write(std::env::var_os("SCENEMAX_PACKAGE_TEST_RESULT").unwrap(), dir.to_str().unwrap()).unwrap();
    }"#).unwrap();
    let executable = if cfg!(windows) {
        "projector.exe"
    } else {
        "projector"
    };
    let mut compiler = std::process::Command::new("rustc");
    compiler.arg(mock).arg("-o").arg(payload.join(executable));
    #[cfg(windows)]
    compiler.args(["-C", "linker=rust-lld.exe"]);
    assert!(compiler.status().unwrap().success());
    let launcher = workspace().join("target/release").join(if cfg!(windows) {
        "scenemax_game_launcher.exe"
    } else {
        "scenemax_game_launcher"
    });
    let artifact = root
        .path()
        .join(if cfg!(windows) { "game.exe" } else { "game" });
    files::zip(&payload, &artifact, Some(&launcher), &context()).unwrap();
    let marker = root.path().join("result.txt");
    let unrelated = root.path().join("unrelated");
    fs::create_dir(&unrelated).unwrap();
    let mut run = std::process::Command::new(artifact);
    run.current_dir(unrelated)
        .env("SCENEMAX_PACKAGE_TEST_RESULT", &marker);
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        run.creation_flags(0x08000000);
    }
    assert!(run.status().unwrap().success());
    let extracted = PathBuf::from(fs::read_to_string(marker).unwrap());
    assert!(
        !extracted.exists(),
        "Private extraction directory must be cleaned after game exit"
    );
    assert_eq!(
        fs::read(payload.join("resources/probe")).unwrap(),
        b"resource fixture"
    );
}
fn context() -> Context {
    Context {
        report: Arc::new(Mutex::new(Report::default())),
        cancel: Arc::new(AtomicBool::new(false)),
    }
}
#[test]
fn butler_receives_archive_contents_for_web_and_a_directory_for_executables() {
    use std::path::Path;
    let settings = Settings {
        version: "2.1 preview".into(),
        ..Default::default()
    };
    let command = pipeline::push_command(
        &settings,
        Path::new("release/web/game.zip"),
        "user/game:web",
    )
    .unwrap();
    let args: Vec<_> = command
        .get_args()
        .map(|a| a.to_string_lossy().into_owned())
        .collect();
    assert_eq!(
        args,
        [
            "push",
            "release/web/game.zip",
            "user/game:web",
            "--userversion",
            "2.1 preview"
        ]
    );
    let command = pipeline::push_command(
        &settings,
        Path::new("release/windows/game.exe"),
        "user/game:windows",
    )
    .unwrap();
    assert_eq!(command.get_args().nth(1).unwrap(), "release/windows");
}
#[test]
fn snapshot_isolated_and_settings_preserve_unknown_keys() {
    let root = tempfile::tempdir().unwrap();
    let source = root.path().join("source");
    fs::create_dir_all(source.join("scripts/nested")).unwrap();
    fs::write(source.join("scripts/nested/main"), b"original").unwrap();
    let dest = root.path().join("copy");
    files::tree(&source, &dest, &context()).unwrap();
    fs::write(source.join("scripts/nested/main"), b"changed").unwrap();
    assert_eq!(
        fs::read(dest.join("scripts/nested/main")).unwrap(),
        b"original"
    );
    fs::create_dir(source.join(".scenemax-studio")).unwrap();
    fs::write(
        source.join(".scenemax-studio/deployment.json"),
        br#"{"future":true}"#,
    )
    .unwrap();
    files::save_settings(&source, &Settings::default()).unwrap();
    let value: serde_json::Value =
        serde_json::from_slice(&fs::read(source.join(".scenemax-studio/deployment.json")).unwrap())
            .unwrap();
    assert_eq!(value["future"], true);
}
#[test]
fn cancellation_and_wrong_platform_are_rejected() {
    let c = context();
    c.cancel.store(true, Ordering::Relaxed);
    assert!(c.check().is_err());
    let root = tempfile::tempdir().unwrap();
    let path = root.path().join("fake");
    fs::write(&path, b"MZxx").unwrap();
    assert!(files::native_binary(&path, scenemax_ide_core::deployment::Target::Linux).is_err());
}
#[test]
fn package_contains_runtime_and_resources_and_no_source_mutation() {
    let root = tempfile::tempdir().unwrap();
    let source = root.path().join("payload");
    fs::create_dir_all(source.join("resources")).unwrap();
    fs::write(source.join("resources/data"), b"asset").unwrap();
    fs::write(source.join("projector"), b"runtime").unwrap();
    let stub = root.path().join("stub");
    fs::write(&stub, b"MZ test launcher").unwrap();
    let artifact = root.path().join("game.exe");
    files::zip(&source, &artifact, Some(&stub), &context()).unwrap();
    let mut archive = zip::ZipArchive::new(fs::File::open(artifact).unwrap()).unwrap();
    assert_eq!(archive.len(), 2);
    assert_eq!(
        archive.by_name("projector").unwrap().unix_mode().unwrap() & 0o777,
        0o755
    );
    assert_eq!(fs::read(source.join("resources/data")).unwrap(), b"asset");
}

#[test]
fn model_inventory_excludes_legacy_and_unused_retargets_and_explains_gltf() {
    let root = tempfile::tempdir().unwrap();
    for dir in ["scripts", "resources/Models", "resources/animations"] {
        fs::create_dir_all(root.path().join(dir)).unwrap();
    }
    fs::write(
        root.path().join("scripts/main"),
        "item is a used\nold is a legacy\ncopy is an alias\nsys.print \"motion\"\n",
    )
    .unwrap();
    let catalog = r#"{"models":[{"name":"used","path":"Models/used.gltf"},{"name":"unused","path":"Models/unused.gltf"},{"name":"legacy","path":"Models/legacy.j3o"},{"name":"alias","path":"Models/alias.j3o","sourceModel":"used"}]}"#;
    fs::write(
        root.path().join("resources/Models/models-ext.json"),
        catalog,
    )
    .unwrap();
    let gltf = r#"{"buffers":[{"uri":"mesh.bin"}],"images":[{"uri":"map.png"}]}"#;
    fs::write(root.path().join("resources/Models/used.gltf"), gltf).unwrap();
    fs::write(root.path().join("resources/Models/unused.gltf"), "{}").unwrap();
    for (file, bytes) in [
        ("mesh.bin", 71),
        ("map.png", 23),
        ("legacy.j3o", 100),
        ("alias.j3o", 100),
    ] {
        fs::write(
            root.path().join("resources/Models").join(file),
            vec![0; bytes],
        )
        .unwrap();
    }
    fs::write(root.path().join("resources/animations/animations-ext.json"), r#"{"animations":[{"name":"motion","bevyBakedRetargets":[{"model":"used","path":"animations/used.json"},{"model":"unused","path":"animations/unused.json"}]}]}"#).unwrap();
    for model in ["used", "unused"] {
        fs::write(
            root.path()
                .join(format!("resources/animations/{model}.json")),
            "{}",
        )
        .unwrap();
    }
    for all in [false, true] {
        let output = root.path().join(if all { "all" } else { "filtered" });
        let request = Request {
            project: root.path().to_owned(),
            entry: root.path().join("scripts/main"),
            workspace: workspace(),
            settings: Settings {
                include_all_resources: all,
                ..Default::default()
            },
        };
        fs::create_dir_all(&output).unwrap();
        let snapshot = output.join("snapshot");
        assets::snapshot(
            &request,
            &snapshot,
            &output.join("package-size.json"),
            &context(),
        )
        .unwrap();
        assert!(!snapshot.join("resources/Models/legacy.j3o").exists());
        assert!(!snapshot.join("resources/Models/alias.j3o").exists());
        assert_eq!(snapshot.join("resources/Models/unused.gltf").exists(), all);
        assert_eq!(
            snapshot.join("resources/animations/unused.json").exists(),
            all
        );
        assert!(snapshot.join("resources/animations/used.json").exists());
        let staged: serde_json::Value = serde_json::from_slice(
            &fs::read(snapshot.join("resources/Models/models-ext.json")).unwrap(),
        )
        .unwrap();
        assert!(
            staged["models"]
                .as_array()
                .unwrap()
                .iter()
                .any(|m| m["name"] == "alias")
        );
        assert!(
            !staged["models"]
                .as_array()
                .unwrap()
                .iter()
                .any(|m| m["name"] == "legacy")
        );
        let animations: serde_json::Value = serde_json::from_slice(
            &fs::read(snapshot.join("resources/animations/animations-ext.json")).unwrap(),
        )
        .unwrap();
        assert_eq!(
            animations["animations"][0]["bevyBakedRetargets"]
                .as_array()
                .unwrap()
                .len(),
            if all { 2 } else { 1 }
        );
        let report_path = size_report::write(&snapshot, &output, &context()).unwrap();
        let report: serde_json::Value =
            serde_json::from_slice(&fs::read(output.join("size-report-analysis.json")).unwrap())
                .unwrap();
        let models = report["models"].as_array().unwrap();
        let used = models.iter().find(|m| m["name"] == "used").unwrap();
        assert_eq!(used["status"], "included");
        assert_eq!(used["bytes"], gltf.len() + 94);
        assert_eq!(used["files"].as_array().unwrap().len(), 3);
        assert_eq!(used["referencedBy"][0], "scripts/main (entity item)");
        assert!(!models.iter().any(|m| m["name"] == "legacy"));
        assert_eq!(models.iter().any(|m| m["name"] == "unused"), all);
        assert!(models.iter().all(|m| m["status"] == "included"));
        assert_eq!(
            models.iter().find(|m| m["name"] == "alias").unwrap()["resolvedPath"],
            "resources/Models/used.gltf"
        );
        let text = fs::read_to_string(report_path).unwrap();
        assert!(text.contains("Packaged Models"));
        assert!(!text.contains("Model Inventory"));
        assert!(!text.contains("legacy.j3o"));
        if !all {
            assert!(!text.contains("unused"));
        }
        assert!(text.contains("resources/Models/used.gltf"));
        assert!(text.contains("scripts/main (entity item)"));
    }
    assert_eq!(
        fs::read_to_string(root.path().join("resources/Models/models-ext.json")).unwrap(),
        catalog
    );
    assert!(root.path().join("resources/Models/legacy.j3o").exists());
}

#[test]
fn native_materials_package_only_when_reachable_with_their_textures() {
    let root = tempfile::tempdir().unwrap();
    fs::create_dir_all(root.path().join("scripts")).unwrap();
    fs::create_dir_all(root.path().join("resources/textures")).unwrap();
    fs::write(
        root.path().join("scripts/main"),
        "shape => box\nshape.material = \"finish\"\nshape.shader = \"dormant\"\n",
    )
    .unwrap();
    let mut value = scenemax_assets::material::preset("Gold");
    value["textures"] = serde_json::json!({"baseColor":"textures/gold.png"});
    fs::write(root.path().join("scripts/finish.smmat"), value.to_string()).unwrap();
    value["textures"] = serde_json::json!({"baseColor":"textures/unused.png"});
    fs::write(root.path().join("scripts/dormant.smmat"), value.to_string()).unwrap();
    for file in ["gold.png", "unused.png"] {
        fs::write(root.path().join("resources/textures").join(file), "fixture").unwrap();
    }
    let request = Request {
        project: root.path().into(),
        entry: root.path().join("scripts/main"),
        workspace: workspace(),
        settings: Default::default(),
    };
    let output = root.path().join("snapshot");
    assets::snapshot(
        &request,
        &output,
        &root.path().join("package-size.json"),
        &context(),
    )
    .unwrap();
    assert!(output.join("scripts/finish.smmat").is_file());
    assert!(output.join("resources/textures/gold.png").is_file());
    assert!(!output.join("scripts/dormant.smmat").exists());
    assert!(!output.join("resources/textures/unused.png").exists());
}

#[test]
fn ik_packages_by_asset_id_without_editor_preview_models() {
    let root = tempfile::tempdir().unwrap();
    fs::create_dir_all(root.path().join("scripts")).unwrap();
    fs::create_dir_all(root.path().join("resources/ik")).unwrap();
    fs::create_dir_all(root.path().join("resources/Models")).unwrap();
    fs::write(
        root.path().join("scripts/main"),
        "actor => box\nactor.ik = \"ik_contact\"\n",
    )
    .unwrap();
    let mut value = scenemax_ide_core::ik::template("Contact", "TwoBoneIK").unwrap();
    value["targetModelId"] = serde_json::json!("authoring_rig");
    value["designerMetadata"]["previewTargetModel"] = serde_json::json!("preview_only");
    fs::write(
        root.path().join("resources/ik/custom_filename.smik"),
        value.to_string(),
    )
    .unwrap();
    fs::write(root.path().join("resources/Models/models-ext.json"),r#"{"models":[{"name":"authoring_rig","path":"Models/authoring.glb"},{"name":"preview_only","path":"Models/preview.glb"}]}"#).unwrap();
    for file in ["authoring.glb", "preview.glb"] {
        fs::write(root.path().join("resources/Models").join(file), "fixture").unwrap();
    }
    let request = Request {
        project: root.path().into(),
        entry: root.path().join("scripts/main"),
        workspace: workspace(),
        settings: Default::default(),
    };
    let output = root.path().join("snapshot");
    assets::snapshot(
        &request,
        &output,
        &root.path().join("size.json"),
        &context(),
    )
    .unwrap();
    assert!(output.join("resources/ik/custom_filename.smik").is_file());
    assert!(!output.join("resources/Models/authoring.glb").exists());
    assert!(!output.join("resources/Models/preview.glb").exists());
}
