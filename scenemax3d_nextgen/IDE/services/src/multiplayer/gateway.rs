//! Optional gateway artifact creation in the same cancellable server-build job.
use super::{build, read};
use scenemax_ide_core::multiplayer::Settings;
use std::{
    fs,
    path::{Path, PathBuf},
    process::Command,
    sync::atomic::AtomicBool,
    time::Duration,
};

pub(super) fn prepare(platform: &str, cancel: &AtomicBool) -> Result<Vec<u8>, String> {
    build::check(cancel)?;
    let workspace = crate::deployment::workspace();
    let name = executable_name(platform);
    // Distributions may ship target-specific adapters to avoid needing Rust on users' machines.
    let bundled = workspace
        .join("../tools/browser-gateway/bin")
        .join(platform)
        .join(name);
    if bundled.is_file() {
        return read(&bundled, 64 * 1024 * 1024);
    }
    let target = match platform {
        "windows-x64" => "x86_64-pc-windows-msvc",
        "linux-x64" => "x86_64-unknown-linux-musl",
        "macos-x64" => "x86_64-apple-darwin",
        _ => return Err("Unsupported gateway platform".into()),
    };
    if !workspace.join("Engine/multiplayer/Cargo.toml").is_file() {
        return Err("Browser gateway build sources are missing. Install the matching SceneMax source distribution or a prebuilt browser gateway for the selected platform.".into());
    }
    if platform == "linux-x64" {
        // Rust's self-contained musl target and bundled LLD cross-build from Windows too.
        // rustup selects the workspace's pinned toolchain, never changing the user's default.
        let rustup = crate::build_configuration::cargo_path()
            .parent()
            .map(|p| {
                p.join(if cfg!(windows) {
                    "rustup.exe"
                } else {
                    "rustup"
                })
            })
            .filter(|p| p.is_file())
            .unwrap_or_else(|| "rustup".into());
        let mut setup = Command::new(rustup);
        crate::build_configuration::prepare(&mut setup);
        setup
            .current_dir(&workspace)
            .args(["target", "add", target]);
        if !build::compile_named(
            &mut setup,
            cancel,
            Duration::from_secs(300),
            "Linux Rust target setup",
        )? {
            return Err(
                "Install Rust with rustup in Build Configuration before building a Linux gateway."
                    .into(),
            );
        }
    }
    let mut command = Command::new(crate::build_configuration::cargo_path());
    crate::build_configuration::prepare(&mut command);
    if platform == "linux-x64" {
        command
            .env("CARGO_TARGET_X86_64_UNKNOWN_LINUX_MUSL_LINKER", "rust-lld")
            .env(
                "CARGO_TARGET_X86_64_UNKNOWN_LINUX_MUSL_RUSTFLAGS",
                "-C link-self-contained=yes -C target-feature=+crt-static",
            );
    }
    let output = workspace.join("target/browser-gateway");
    command
        .current_dir(&workspace)
        .args([
            "build",
            "--locked",
            "--release",
            "-p",
            "scenemax_multiplayer",
            "--features",
            "web-gateway",
            "--bin",
            "scenemax_web_gateway",
            "--target",
            target,
            "--target-dir",
        ])
        .arg(&output)
        .env_remove("SCENEMAX_MULTIPLAYER_PASSWORD");
    let built = build::compile_named(&mut command, cancel, Duration::from_secs(300), "Rust gateway")
        .map_err(|e| format!("Browser gateway build failed for {platform}. Install the Rust target and linker for this platform in Build Configuration, or use a prebuilt gateway. {}", e))?;
    if !built {
        return Err("Rust compiler unavailable. Open File > Settings > Build Configuration and install / repair Rust.".into());
    }
    read(
        &output.join(target).join("release").join(name),
        64 * 1024 * 1024,
    )
}
pub(super) fn executable_name(platform: &str) -> &'static str {
    if platform == "windows-x64" {
        "scenemax_web_gateway.exe"
    } else {
        "scenemax_web_gateway"
    }
}
pub(super) fn install(
    directory: &Path,
    settings: &Settings,
    bytes: &[u8],
) -> Result<PathBuf, String> {
    let path = directory.join(executable_name(&settings.platform));
    fs::write(&path, bytes).map_err(|e| e.to_string())?;
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(&path, fs::Permissions::from_mode(0o700)).map_err(|e| e.to_string())?;
    }
    let config = serde_json::json!({
        "listen": settings.browser_gateway.listen,
        "upstream": format!("127.0.0.1:{}", settings.port),
        "allowed_origin": settings.browser_gateway.allowed_origin,
    });
    fs::write(
        directory.join("gateway.json"),
        serde_json::to_vec_pretty(&config).map_err(|e| e.to_string())?,
    )
    .map_err(|e| e.to_string())?;
    fs::write(directory.join("README.md"), format!(
        "# SceneMax multiplayer server\n\nStart the UDP server and scenemax_web_gateway on the same host. The gateway reads gateway.json beside its executable when opened without arguments.\n\nBrowser URL: {}\nAllowed game origin: {}\n\nNative games connect directly to UDP port {}. For public HTTPS games, terminate TLS at a reverse proxy that forwards WebSocket upgrades to {} and expose the configured wss:// URL. The gateway itself speaks plain WebSocket. Open only the necessary firewall ports.\n\nFor local play, use Run Server Locally in Project Settings; the IDE manages both processes.\n",
        settings.browser_gateway.websocket_url, settings.browser_gateway.allowed_origin, settings.port, settings.browser_gateway.listen
    )).map_err(|e| e.to_string())?;
    Ok(path)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn bundle_config_targets_colocated_udp_and_keeps_url_out_of_bind_address() {
        let root = tempfile::tempdir().unwrap();
        let mut settings = Settings::default();
        settings.port = 19001;
        settings.server = "public.example.com".into();
        settings.browser_gateway.websocket_url = "wss://network.example.com/play".into();
        settings.browser_gateway.allowed_origin = "https://games.example.com".into();
        let path = install(root.path(), &settings, b"fixture").unwrap();
        assert!(path.is_file());
        let value: serde_json::Value =
            serde_json::from_slice(&fs::read(root.path().join("gateway.json")).unwrap()).unwrap();
        assert_eq!(value["upstream"], "127.0.0.1:19001");
        assert_eq!(value["listen"], "127.0.0.1:9002");
        assert_eq!(value["allowed_origin"], "https://games.example.com");
        assert!(!value.to_string().contains("password"));
    }
}
