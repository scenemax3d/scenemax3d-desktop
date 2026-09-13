use std::path::PathBuf;
use std::process::Command;

fn main() {
    println!("cargo:rerun-if-env-changed=SCENEMAX_REGENERATE_PARSER");
    // Normal builds use the checked-in Rust parser and need no JVM. Grammar
    // maintainers can explicitly regenerate it until the generator is ported.
    if std::env::var_os("SCENEMAX_REGENERATE_PARSER").as_deref() != Some(std::ffi::OsStr::new("1"))
    {
        println!("cargo:rerun-if-changed=build.rs");
        println!("cargo:rerun-if-changed=grammar/SceneMaxNextGen.g4");
        println!("cargo:rerun-if-changed=src/generated/SceneMaxNextGen.g4.snapshot");
        let grammar = std::fs::read("grammar/SceneMaxNextGen.g4").expect("missing grammar");
        let generated_from = std::fs::read("src/generated/SceneMaxNextGen.g4.snapshot")
            .expect("missing generated grammar snapshot");
        // Normalize checkout line endings (Git autocrlf must not cause false failures).
        assert_eq!(
            String::from_utf8_lossy(&grammar).replace("\r\n", "\n"),
            String::from_utf8_lossy(&generated_from).replace("\r\n", "\n"),
            "Grammar changed: regenerate the Rust parser with SCENEMAX_REGENERATE_PARSER=1"
        );
        return;
    }
    println!("cargo:rerun-if-changed=grammar/SceneMaxNextGen.g4");
    println!("cargo:rerun-if-changed=build.rs");

    let manifest_dir =
        PathBuf::from(std::env::var("CARGO_MANIFEST_DIR").expect("CARGO_MANIFEST_DIR"));
    let workspace_root = manifest_dir
        .parent()
        .and_then(|path| path.parent())
        .expect("parser crate should live in Language/parser");
    let antlr_jar = workspace_root
        .join("Tools")
        .join("antlr")
        .join("antlr4-4.8-2-SNAPSHOT-complete.jar");
    let grammar_dir = manifest_dir.join("grammar");
    let generated_dir = manifest_dir.join("src").join("generated");

    std::fs::create_dir_all(&generated_dir).expect("failed to create generated parser directory");

    if !antlr_jar.is_file() {
        panic!(
            "missing ANTLR Rust generator jar at {}",
            antlr_jar.display()
        );
    }

    let output = Command::new("java")
        .current_dir(&grammar_dir)
        .arg("-cp")
        .arg(&antlr_jar)
        .arg("org.antlr.v4.Tool")
        .arg("-Dlanguage=Rust")
        .arg("-visitor")
        .arg("-o")
        .arg(&generated_dir)
        .arg("SceneMaxNextGen.g4")
        .output()
        .expect("failed to start ANTLR Rust generator");

    if !output.status.success() {
        panic!(
            "ANTLR Rust generation failed\nstdout:\n{}\nstderr:\n{}",
            String::from_utf8_lossy(&output.stdout),
            String::from_utf8_lossy(&output.stderr)
        );
    }
    std::fs::copy(
        grammar_dir.join("SceneMaxNextGen.g4"),
        generated_dir.join("SceneMaxNextGen.g4.snapshot"),
    )
    .expect("failed to save generated grammar snapshot");
}
