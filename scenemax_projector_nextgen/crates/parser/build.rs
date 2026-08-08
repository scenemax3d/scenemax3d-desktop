use std::path::PathBuf;
use std::process::Command;

fn main() {
    println!("cargo:rerun-if-changed=grammar/SceneMaxNextGen.g4");
    println!("cargo:rerun-if-changed=build.rs");

    let manifest_dir =
        PathBuf::from(std::env::var("CARGO_MANIFEST_DIR").expect("CARGO_MANIFEST_DIR"));
    let workspace_root = manifest_dir
        .parent()
        .and_then(|path| path.parent())
        .expect("parser crate should live in crates/parser");
    let antlr_jar = workspace_root
        .join("tools")
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
}
