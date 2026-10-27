//! Built-in browser projector compilation and static-site packaging.
use super::{Context, Request, files, process};
#[path = "web/effects.rs"]
mod effects;
#[path = "web/loading.rs"]
mod loading;
#[path = "web/package.rs"]
mod package;
use crate::build_configuration::{self, WASM_BINDGEN_VERSION};
use std::{
    fs, io,
    path::{Path, PathBuf},
    process::Command,
};

fn command(program: impl AsRef<std::ffi::OsStr>, request: &Request) -> Command {
    let mut c = Command::new(program);
    c.current_dir(&request.workspace);
    build_configuration::prepare(&mut c);
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        c.creation_flags(0x08000000);
    }
    c
}
pub(super) fn check(request: &Request) -> io::Result<()> {
    if !request.settings.rebuild_runtime {
        super::prebuilt::web(&request.workspace)?;
        return Ok(());
    }
    check_workspace(&request.workspace)?;
    let generator = command(build_configuration::wasm_bindgen_path(), request)
        .arg("--version")
        .output()
        .map_err(|_| {
            io::Error::other("Open File > Settings > Build Configuration and install Web tools.")
        })?;
    if !generator.status.success()
        || String::from_utf8_lossy(&generator.stdout).trim()
            != format!("wasm-bindgen {WASM_BINDGEN_VERSION}")
    {
        return Err(io::Error::other(format!(
            "Web export requires wasm-bindgen {WASM_BINDGEN_VERSION}. Install Web tools in File > Settings."
        )));
    }
    let cargo = build_configuration::cargo_path();
    let rustc = cargo
        .parent()
        .filter(|p| !p.as_os_str().is_empty())
        .map(|p| p.join(if cfg!(windows) { "rustc.exe" } else { "rustc" }))
        .unwrap_or_else(|| "rustc".into());
    let target = command(rustc, request)
        .args([
            "--print",
            "target-libdir",
            "--target",
            "wasm32-unknown-unknown",
        ])
        .output().map_err(|e| io::Error::other(format!(
            "Cannot run Rust for Web export: {e}. Open File > Settings > Build Configuration, install / repair Rust, then install Web tools and refresh detection."
        )))?;
    if !target.status.success()
        || !Path::new(String::from_utf8_lossy(&target.stdout).trim()).is_dir()
    {
        return Err(io::Error::other(
            "The active Rust toolchain is missing wasm32-unknown-unknown. Install Web tools in File > Settings.",
        ));
    }
    Ok(())
}

fn check_workspace(workspace: &Path) -> io::Result<()> {
    for file in [
        "Cargo.toml",
        "Cargo.lock",
        "rust-toolchain.toml",
        "Projector/app/src/web.rs",
        "third_party/wgpu/Cargo.toml",
    ] {
        if !workspace.join(file).is_file() {
            return Err(io::Error::other(format!(
                "Web build sources are incomplete at {} (missing {file}). Obtain the full SceneMax source distribution matching this IDE, including third_party, in a writable folder. Set SCENEMAX_BUILD_WORKSPACE to its scenemax3d_nextgen folder before launching the IDE. Installing Web tools alone does not install SceneMax sources. See IDE/WEB_EXPORT.md.",
                workspace.display()
            )));
        }
    }
    Ok(())
}

#[cfg(test)]
mod setup_tests {
    use super::*;
    #[test]
    fn incomplete_distribution_reports_missing_source_and_recovery_steps() {
        let root = tempfile::tempdir().unwrap();
        let error = check_workspace(root.path()).unwrap_err().to_string();
        assert!(error.contains("Cargo.toml") && error.contains("SCENEMAX_BUILD_WORKSPACE"));
        for file in [
            "Cargo.toml",
            "Cargo.lock",
            "rust-toolchain.toml",
            "Projector/app/src/web.rs",
            "third_party/wgpu/Cargo.toml",
        ] {
            let path = root.path().join(file);
            fs::create_dir_all(path.parent().unwrap()).unwrap();
            fs::write(path, "").unwrap();
        }
        check_workspace(root.path()).unwrap();
        fs::remove_file(root.path().join("third_party/wgpu/Cargo.toml")).unwrap();
        assert!(
            check_workspace(root.path())
                .unwrap_err()
                .to_string()
                .contains("third_party/wgpu")
        );
    }
}
pub(super) fn build(
    request: &Request,
    snapshot: &Path,
    staging: &Path,
    output: &Path,
    context: &Context,
    log: &Path,
) -> io::Result<PathBuf> {
    check(request)?;
    let loading_html = loading::render(&request.settings.web_loading, &request.project)?;
    let site = staging.join("web-site");
    fs::create_dir(&site)?;
    let game = site.join("game");
    files::tree(snapshot, &game, context)?;
    // The browser's immutable file store infers directories from mounted files.
    // Keep the resources root present even for games using only primitives.
    fs::create_dir_all(game.join("resources"))?;
    let root_marker = game.join("resources/.scenemax-web-root");
    if !root_marker.exists() {
        fs::write(root_marker, [])?;
    }
    let launch: serde_json::Value =
        serde_json::from_slice(&files::read_small(&game.join("launch.json"))?)?;
    let script = launch["script"]
        .as_str()
        .ok_or_else(|| io::Error::other("Missing browser entry script"))?;
    let listing = files::list(&game, context)?;
    let mut has_effects = false;
    let mut distortion = false;
    for path in &listing {
        if path
            .extension()
            .is_some_and(|e| e.eq_ignore_ascii_case("efkefc") || e.eq_ignore_ascii_case("efk"))
        {
            distortion |= effects::validate(&files::read_small(path)?)
                .map_err(|e| io::Error::other(format!("{}: {e}", path.display())))?;
            has_effects = true;
        }
    }
    if has_effects {
        effects::bundle(&site)?;
    }
    let paths: Vec<_> = listing
        .iter()
        .map(|p| {
            p.strip_prefix(&site)
                .map(|p| p.to_string_lossy().replace('\\', "/"))
                .map_err(io::Error::other)
        })
        .collect::<io::Result<_>>()?;
    let source = fs::read_to_string(game.join(script))?;
    let multiplayer = source.lines().any(|line| {
        line.trim().starts_with("//$[multiplayer_server]=")
            && !line.trim().trim_end_matches(';').ends_with('=')
    });
    let gateway_url = if let Some(settings) = &request.multiplayer_override {
        settings.browser_gateway.websocket_url.clone()
    } else {
        crate::multiplayer::browser_gateway_url(&request.project, multiplayer)
            .map_err(io::Error::other)?
    };
    let aliases = package::deduplicate(&site, &listing, context)?;
    fs::write(
        site.join("game-manifest.json"),
        serde_json::to_vec(
            &serde_json::json!({"entry":format!("game/{script}"),"files":paths,"aliases":aliases,"multiplayer":multiplayer,"effects":has_effects,"effect_distortion":distortion}),
        )?,
    )?;
    let allowed_origin = if let Some(settings) = &request.multiplayer_override {
        settings.browser_gateway.allowed_origin.clone()
    } else {
        crate::multiplayer::browser_allowed_origin(&request.project, multiplayer)
            .map_err(io::Error::other)?
    };
    write_network_config(&site, &gateway_url, &allowed_origin)?;
    if request.settings.rebuild_runtime {
        context.log(
        "Building the browser projector (WebGPU); Cargo reuses unchanged compilation outputs.\n",
    );
        let target = request.workspace.join("target");
        let mut cargo = command(build_configuration::cargo_path(), request);
        cargo
            .args([
                "build",
                "--locked",
                "--release",
                "--target",
                "wasm32-unknown-unknown",
                "-p",
                "scenemax_projector_nextgen",
                "--no-default-features",
                "--target-dir",
            ])
            .arg(&target);
        cargo.env_remove("CARGO_BUILD_TARGET");
        process::run(&mut cargo, context, log)?;
        let wasm = target.join("wasm32-unknown-unknown/release/scenemax_projector_nextgen.wasm");
        let mut bindings = command(build_configuration::wasm_bindgen_path(), request);
        bindings
            .arg(&wasm)
            .args(["--target", "web", "--out-name", "runtime", "--out-dir"])
            .arg(&site);
        process::run(&mut bindings, context, log)?;
    } else {
        let runtime = super::prebuilt::web(&request.workspace)?;
        context.log("Using bundled browser projector; no compilation required.\n");
        files::tree(&runtime, &site, context)?;
    }
    fs::write(site.join("index.html"), loading_html)?;
    fs::write(site.join("game.js"), include_str!("web/game.js"))?;
    fs::write(site.join("audio.mjs"), include_str!("web/audio.mjs"))?;
    fs::write(
        site.join("bootstrap.mjs"),
        include_str!("web/bootstrap.mjs"),
    )?;
    fs::write(site.join("files.mjs"), include_str!("web/files.mjs"))?;
    fs::write(site.join("README.txt"), include_str!("web/README.md"))?;
    let partial = staging.join("web.partial");
    files::zip(&site, &partial, None, context)?;
    let artifact = output.join(format!("{}-web.zip", request.settings.name));
    fs::rename(partial, &artifact)?;
    Ok(artifact)
}

fn write_network_config(site: &Path, gateway_url: &str, allowed_origin: &str) -> io::Result<()> {
    fs::write(
        site.join("network.json"),
        serde_json::to_vec(
            &serde_json::json!({"websocket_url": gateway_url, "allowed_origin": allowed_origin}),
        )?,
    )
}
#[cfg(test)]
mod network_config_tests {
    use super::*;
    #[test]
    fn writes_only_public_endpoint_to_browser_sidecar() {
        let dir = tempfile::tempdir().unwrap();
        write_network_config(
            dir.path(),
            "wss://gateway.example.com/network?version=1",
            "http://localhost:8080",
        )
        .unwrap();
        let actual: serde_json::Value =
            serde_json::from_slice(&fs::read(dir.path().join("network.json")).unwrap()).unwrap();
        assert_eq!(
            actual,
            serde_json::json!({"websocket_url":"wss://gateway.example.com/network?version=1", "allowed_origin":"http://localhost:8080"})
        );
        write_network_config(dir.path(), "", "").unwrap();
        assert_eq!(
            serde_json::from_slice::<serde_json::Value>(
                &fs::read(dir.path().join("network.json")).unwrap()
            )
            .unwrap()["websocket_url"],
            ""
        );
    }
}
