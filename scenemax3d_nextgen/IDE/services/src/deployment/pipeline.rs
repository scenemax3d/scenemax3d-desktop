use super::{Context, Request, assets, files, process};
use scenemax_ide_core::deployment::{Platform, Target, normalize_itch_target};
use serde::Deserialize;
use std::{
    fs::{self, File},
    io,
    path::{Component, Path, PathBuf},
    process::Command,
};

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct Recipe {
    program: PathBuf,
    args: Vec<String>,
    artifact: PathBuf,
}
fn recipe(platform: &Platform) -> io::Result<Recipe> {
    let mut value: Recipe =
        serde_json::from_slice(&files::read_small(Path::new(&platform.recipe))?)?;
    if value.program.as_os_str().is_empty()
        || value.artifact.as_os_str().is_empty()
        || value
            .artifact
            .components()
            .any(|p| !matches!(p, Component::Normal(_)))
    {
        return Err(io::Error::other(
            "SDK recipe needs a program, args and a relative artifact path inside {output}.",
        ));
    }
    if !value.program.is_absolute() && value.program.components().count() > 1 {
        value.program = Path::new(&platform.recipe)
            .parent()
            .unwrap_or(Path::new("."))
            .join(value.program);
    }
    Ok(value)
}
fn available(program: &Path) -> bool {
    if program.components().count() > 1 {
        return program.is_file();
    }
    std::env::var_os("PATH").is_some_and(|paths| {
        std::env::split_paths(&paths).any(|p| {
            p.join(program).is_file()
                || (cfg!(windows) && p.join(program).with_extension("exe").is_file())
        })
    })
}
fn native_host() -> &'static str {
    match (std::env::consts::OS, std::env::consts::ARCH) {
        ("windows", "x86_64") => "x86_64-pc-windows-msvc",
        ("windows", "aarch64") => "aarch64-pc-windows-msvc",
        ("linux", "x86_64") => "x86_64-unknown-linux-gnu",
        ("linux", "aarch64") => "aarch64-unknown-linux-gnu",
        ("macos", "aarch64") => "aarch64-apple-darwin",
        ("macos", "x86_64") => "x86_64-apple-darwin",
        _ => "unknown",
    }
}
pub(super) fn check(request: &Request, context: &Context) -> io::Result<()> {
    context.stage(2., "Checking project and platform requirements…")?;
    request.settings.validate().map_err(io::Error::other)?;
    let root = request.project.canonicalize()?;
    let entry = request.entry.canonicalize()?;
    if !entry.starts_with(root.join("scripts")) || !entry.is_file() {
        return Err(io::Error::other(
            "The entry script must be inside this project's scripts directory.",
        ));
    }
    if request
        .settings
        .platforms
        .iter()
        .any(|p| p.selected && p.target.desktop())
        && !request
            .workspace
            .join("Projector/launcher/Cargo.toml")
            .is_file()
    {
        return Err(io::Error::other(
            "Build workspace is missing Projector/launcher. Set SCENEMAX_BUILD_WORKSPACE to your NextGen workspace.",
        ));
    }
    let mut errors = Vec::new();
    if !request.settings.builtin_resources.is_empty()
        && !Path::new(&request.settings.builtin_resources).is_dir()
    {
        errors.push("The shared resources directory does not exist.".into());
    }
    for platform in request.settings.platforms.iter().filter(|p| p.selected) {
        context.check()?;
        let result = if platform.target.desktop() {
            if !available(Path::new("cargo")) {
                Err(io::Error::other("Install Rust/Cargo or add it to PATH."))
            } else if !platform.runtime.is_empty() {
                files::native_binary(Path::new(&platform.runtime), platform.target)
            } else if request.settings.native_effects && platform.target != Target::Windows {
                Err(io::Error::other(
                    "The automatic native Effekseer build currently stages a Windows DLL. For this target, supply a matching prebuilt projector and libraries, or disable native effects.",
                ))
            } else {
                Ok(())
            }
        } else {
            recipe(platform).and_then(|r| {
                if available(&r.program) {
                    Ok(())
                } else {
                    Err(io::Error::other(format!(
                        "SDK builder not found: {}",
                        r.program.display()
                    )))
                }
            })
        };
        match result {
            Ok(()) => context.log(format!(
                "[{}] Configuration ready{}\n",
                platform.target.id(),
                if platform.target.desktop() && platform.triple != native_host() {
                    "; cross compilation requires the selected Rust target, linker and SDK"
                } else {
                    ""
                }
            )),
            Err(error) => errors.push(format!("{}: {error}", platform.target.id())),
        }
    }
    if request.settings.upload && !available(Path::new(&request.settings.butler)) {
        errors.push("Butler was not found. Set its executable path, then use Sign in.".into());
    }
    if errors.is_empty() {
        Ok(())
    } else {
        Err(io::Error::other(errors.join("\n")))
    }
}
fn cargo(
    request: &Request,
    platform: &Platform,
    package: &str,
    effects: bool,
    context: &Context,
    log: &Path,
) -> io::Result<PathBuf> {
    let suffix = if platform.target == Target::Windows {
        ".exe"
    } else {
        ""
    };
    let mut out = request.workspace.join("target");
    if platform.triple != native_host() {
        out.push(&platform.triple);
    }
    let binary = out.join("release").join(format!("{package}{suffix}"));
    if package != "scenemax_game_launcher"
        && !request.settings.rebuild_runtime
        && binary.is_file()
        && (!effects || release_has_effects(&out.join("release"), package))
    {
        files::native_binary(&binary, platform.target)?;
        // Release binaries are installed engine tools, independent of game scripts.
        // Source changes are picked up only by the explicit rebuild option.
        context.log(format!(
            "Reusing release {package} for {}. Engine rebuild skipped.\n",
            platform.triple
        ));
        return Ok(binary);
    }
    let mut command = Command::new("cargo");
    command
        .current_dir(&request.workspace)
        .args(["build", "--locked", "--release", "-p", package]);
    command.env_remove("CARGO_BUILD_TARGET");
    // Explicit target directory avoids ambient CARGO_TARGET_DIR changing artifact discovery.
    command
        .arg("--target-dir")
        .arg(request.workspace.join("target"));
    if platform.triple != native_host() {
        command.args(["--target", &platform.triple]);
    }
    if effects {
        command.args(["--features", "effekseer_native"]);
    }
    context.log(format!(
        "Building {package} for {} (release)\n",
        platform.triple
    ));
    process::run(&mut command, context, log)?;
    files::native_binary(&binary, platform.target)?;
    Ok(binary)
}
fn release_has_effects(release: &Path, package: &str) -> bool {
    // Cargo's most recently completed package fingerprint describes the installed
    // release binary. Unknown/custom runtimes must be selected explicitly in the form.
    let Ok(entries) = fs::read_dir(release.join(".fingerprint")) else {
        return false;
    };
    let latest = entries
        .flatten()
        .filter(|entry| {
            entry
                .file_name()
                .to_string_lossy()
                .starts_with(&format!("{package}-"))
        })
        .filter_map(|entry| {
            let path = entry.path().join(format!("bin-{package}.json"));
            Some((fs::metadata(&path).ok()?.modified().ok()?, path))
        })
        .max_by_key(|(stamp, _)| *stamp);
    latest
        .and_then(|(_, path)| files::read_small(&path).ok())
        .and_then(|bytes| serde_json::from_slice::<serde_json::Value>(&bytes).ok())
        .is_some_and(|value| {
            value["features"]
                .as_str()
                .is_some_and(|s| s.contains("\"effekseer_native\""))
        })
}
pub(super) fn build(request: &Request, context: &Context) -> io::Result<()> {
    let started = std::time::Instant::now();
    let mut rooted_request = request.clone();
    rooted_request.entry = assets::root_entry(&request.project.canonicalize()?, context)?;
    let request = &rooted_request;
    check(request, context)?;
    files::save_settings(&request.project, &request.settings)?;
    let output = PathBuf::from(&request.settings.output);
    fs::create_dir_all(&output)?;
    let output = output.canonicalize()?;
    let project = request.project.canonicalize()?;
    if output.starts_with(project.join("scripts")) || output.starts_with(project.join("resources"))
    {
        return Err(io::Error::other(
            "Choose an output folder outside scripts and resources.",
        ));
    }
    // Keep the run directory even on failure, so logs and finished artifacts survive.
    let run = tempfile::Builder::new()
        .prefix(&format!("{}-", request.settings.name))
        .tempdir_in(&output)?
        .keep();
    let log = run.join("build.log");
    File::create(&log)?;
    context.edit(|r| r.log_path = Some(log.clone()));
    let staging = tempfile::Builder::new()
        .prefix(".staging-")
        .tempdir_in(&run)?;
    let snapshot = staging.path().join("project");
    fs::create_dir(&snapshot)?;
    context.stage(8., "Collecting game dependencies…")?;
    if !request.settings.builtin_resources.is_empty() {
        let builtin = Path::new(&request.settings.builtin_resources).canonicalize()?;
        if output.starts_with(&builtin) {
            return Err(io::Error::other(
                "The output folder must be outside shared resources.",
            ));
        }
    }
    assets::snapshot(request, &snapshot, &run.join("package-size.json"), context)?;
    let canonical_entry = request.entry.canonicalize()?;
    let entry = canonical_entry
        .strip_prefix(&project)
        .map_err(io::Error::other)?;
    let entry = entry
        .to_str()
        .ok_or_else(|| io::Error::other("Entry point must be Unicode."))?
        .replace('\\', "/");
    fs::write(
        snapshot.join("launch.json"),
        serde_json::to_vec(&serde_json::json!({"script": entry}))?,
    )?;
    let targets: Vec<_> = request
        .settings
        .platforms
        .iter()
        .filter(|p| p.selected)
        .collect();
    let mut uploads = Vec::new();
    for (index, platform) in targets.iter().enumerate() {
        let base = 12. + index as f32 * 68. / targets.len() as f32;
        context.stage(
            base,
            format!(
                "Building {} · {} of {}",
                platform.target.id(),
                index + 1,
                targets.len()
            ),
        )?;
        let platform_dir = run.join(platform.target.id());
        fs::create_dir(&platform_dir)?;
        let artifact = if platform.target.desktop() {
            desktop(
                request,
                platform,
                &snapshot,
                staging.path(),
                &platform_dir,
                context,
                &log,
            )?
        } else {
            sdk(
                request,
                platform,
                &snapshot,
                staging.path(),
                &platform_dir,
                context,
                &log,
            )?
        };
        context.edit(|r| r.artifacts.push(artifact.clone()));
        let summary = format!(
            "Artifact complete: {} ({:.1} MiB), elapsed {:.1}s\n",
            artifact.display(),
            fs::metadata(&artifact)?.len() as f64 / 1048576.,
            started.elapsed().as_secs_f64()
        );
        context.log(&summary);
        use std::io::Write;
        writeln!(fs::OpenOptions::new().append(true).open(&log)?, "{summary}")?;
        context.stage(
            12. + (index + 1) as f32 * 68. / targets.len() as f32,
            format!("{} package complete", platform.target.id()),
        )?;
        uploads.push(artifact);
    }
    if request.settings.upload {
        upload(request, &uploads, context, &log)?;
    }
    context.stage(
        100.,
        if request.settings.upload {
            "Build and upload complete."
        } else {
            "Build complete. Your release artifacts are ready."
        },
    )
}
pub(super) fn upload(
    request: &Request,
    artifacts: &[PathBuf],
    context: &Context,
    log: &Path,
) -> io::Result<()> {
    let target = normalize_itch_target(&request.settings.itch_page).map_err(io::Error::other)?;
    let platforms: Vec<_> = request
        .settings
        .platforms
        .iter()
        .filter(|p| p.selected)
        .collect();
    if platforms.is_empty() || platforms.len() != artifacts.len() {
        return Err(io::Error::other(
            "All selected artifacts must be complete before upload. Restore the built platform selection or build again.",
        ));
    }
    // Validate the entire set before publishing the first channel.
    for (platform, artifact) in platforms.iter().zip(artifacts) {
        if !artifact.is_file()
            || artifact
                .parent()
                .and_then(Path::file_name)
                .is_none_or(|n| n != platform.target.id())
        {
            return Err(io::Error::other(
                "An artifact is missing or does not match the selected platforms. Build again.",
            ));
        }
    }
    for (index, (platform, artifact)) in platforms.iter().zip(artifacts).enumerate() {
        context.stage(
            82. + index as f32 * 17. / artifacts.len() as f32,
            format!("Uploading to {target}:{}…", platform.channel),
        )?;
        process::run(
            &mut push_command(
                &request.settings,
                artifact,
                &format!("{target}:{}", platform.channel),
            )?,
            context,
            log,
        )?;
        context.log(format!("Uploaded {target}:{}\n", platform.channel));
    }
    context.stage(
        100.,
        "Upload complete. All selected channels were published.",
    )
}
pub(super) fn push_command(
    settings: &scenemax_ide_core::deployment::Settings,
    artifact: &Path,
    destination: &str,
) -> io::Result<Command> {
    // Butler consumes directories or ZIP archives. Passing the Web ZIP directly
    // makes index.html the upload root, rather than uploading a downloadable ZIP.
    let source = if artifact.extension().is_some_and(|e| e == "zip") {
        artifact
    } else {
        artifact
            .parent()
            .ok_or_else(|| io::Error::other("Artifact has no parent directory."))?
    };
    let mut command = Command::new(&settings.butler);
    command
        .arg("push")
        .arg(source)
        .arg(destination)
        .arg("--userversion")
        .arg(&settings.version);
    Ok(command)
}
pub(super) fn desktop(
    request: &Request,
    platform: &Platform,
    snapshot: &Path,
    staging: &Path,
    output: &Path,
    context: &Context,
    log: &Path,
) -> io::Result<PathBuf> {
    let share = 68.
        / request
            .settings
            .platforms
            .iter()
            .filter(|p| p.selected)
            .count() as f32;
    let binary = if platform.runtime.is_empty() {
        cargo(
            request,
            platform,
            "scenemax_projector_nextgen",
            request.settings.native_effects,
            context,
            log,
        )?
    } else {
        PathBuf::from(&platform.runtime)
    };
    files::native_binary(&binary, platform.target)?;
    context.advance(
        share * 0.45,
        format!(
            "Building the {} single-file launcher…",
            platform.target.id()
        ),
    )?;
    let launcher = cargo(
        request,
        platform,
        "scenemax_game_launcher",
        false,
        context,
        log,
    )?;
    context.advance(
        share * 0.10,
        format!("Staging {} runtime and libraries…", platform.target.id()),
    )?;
    let payload = staging.join(platform.target.id());
    fs::create_dir_all(&payload)?;
    files::tree(snapshot, &payload, context)?;
    let exe = platform.target == Target::Windows;
    files::copy(
        &binary,
        &payload.join(if exe { "projector.exe" } else { "projector" }),
        context,
    )?;
    let parent = binary
        .parent()
        .ok_or_else(|| io::Error::other("Projector has no parent directory."))?;
    let mut has_effects = false;
    for item in fs::read_dir(parent)? {
        let path = item?.path();
        let name = path.file_name().unwrap_or_default().to_string_lossy();
        let library = if exe {
            name.ends_with(".dll")
        } else {
            name.ends_with(".dylib") || name.contains(".so")
        };
        if library && path.is_file() {
            has_effects |= name.contains("scenemax_effekseer");
            files::copy(
                &path,
                &payload.join(path.file_name().unwrap_or_default()),
                context,
            )?;
        }
    }
    if request.settings.native_effects && !has_effects {
        return Err(io::Error::other(
            "Native effects are enabled but the matching Effekseer library is missing beside the projector.",
        ));
    }
    context.log("Embedding the runtime, native libraries, scripts and resources…\n");
    context.advance(
        share * 0.10,
        format!("Compressing the {} release…", platform.target.id()),
    )?;
    let stem = &request.settings.name;
    if exe {
        let partial = staging.join("windows.partial");
        files::zip(&payload, &partial, Some(&launcher), context)?;
        let artifact = output.join(format!("{stem}.exe"));
        fs::rename(partial, &artifact)?;
        Ok(artifact)
    } else {
        let bundle = staging.join(format!("{}-bundle", platform.target.id()));
        fs::create_dir(&bundle)?;
        files::zip(&payload, &bundle.join(stem), Some(&launcher), context)?;
        let partial = staging.join(format!("{}.partial", platform.target.id()));
        files::zip(&bundle, &partial, None, context)?;
        let artifact = output.join(format!("{stem}-{}.zip", platform.target.id()));
        fs::rename(partial, &artifact)?;
        Ok(artifact)
    }
}
fn sdk(
    request: &Request,
    platform: &Platform,
    snapshot: &Path,
    staging: &Path,
    output: &Path,
    context: &Context,
    log: &Path,
) -> io::Result<PathBuf> {
    let manifest: serde_json::Value =
        serde_json::from_slice(&files::read_small(&snapshot.join("launch.json"))?)?;
    let entry = manifest["script"]
        .as_str()
        .ok_or_else(|| io::Error::other("Missing staged entry point."))?;
    let recipe = recipe(platform)?;
    let work = staging.join(format!("{}-sdk", platform.target.id()));
    fs::create_dir(&work)?;
    let replacements = [
        ("{project}", snapshot.to_string_lossy().into_owned()),
        ("{output}", work.to_string_lossy().into_owned()),
        (
            "{workspace}",
            request.workspace.to_string_lossy().into_owned(),
        ),
        ("{entry}", entry.into()),
        ("{version}", request.settings.version.clone()),
        ("{name}", request.settings.name.clone()),
    ];
    let mut command = Command::new(recipe.program);
    command.current_dir(
        Path::new(&platform.recipe)
            .parent()
            .unwrap_or(Path::new(".")),
    );
    for arg in recipe.args {
        command.arg(
            replacements
                .iter()
                .fold(arg, |text, (key, value)| text.replace(key, value)),
        );
    }
    context.log(format!(
        "Running the configured {} SDK builder.\n",
        platform.target.id()
    ));
    process::run(&mut command, context, log)?;
    let artifact = work.join(recipe.artifact).canonicalize()?;
    if !artifact.starts_with(work.canonicalize()?) {
        return Err(io::Error::other(
            "SDK artifact escaped its isolated output directory.",
        ));
    }
    files::regular(&artifact)?;
    let (partial, final_name) = if platform.target == Target::Web {
        if !artifact.is_dir()
            || !artifact.join("index.html").is_file()
            || !files::list(&artifact, context)?
                .iter()
                .any(|p| p.extension().is_some_and(|e| e == "wasm"))
        {
            return Err(io::Error::other(
                "Web builder must produce a directory with index.html and a .wasm module.",
            ));
        }
        let partial = work.join("web.partial");
        files::zip(&artifact, &partial, None, context)?;
        (partial, format!("{}-web.zip", request.settings.name))
    } else {
        let extension = if platform.target == Target::Android {
            "apk"
        } else {
            "ipa"
        };
        if artifact.extension().is_none_or(|e| e != extension) {
            return Err(io::Error::other(format!(
                "Builder must produce a .{extension} file."
            )));
        }
        let archive = zip::ZipArchive::new(File::open(&artifact)?)?;
        let valid = if platform.target == Target::Android {
            archive.file_names().any(|n| n == "AndroidManifest.xml")
                && archive
                    .file_names()
                    .any(|n| n.starts_with("lib/") && n.ends_with(".so"))
        } else {
            archive
                .file_names()
                .any(|n| n.starts_with("Payload/") && n.ends_with(".app/Info.plist"))
        };
        if !valid {
            return Err(io::Error::other(
                "The SDK builder did not produce a valid native application archive.",
            ));
        }
        (artifact, format!("{}.{extension}", request.settings.name))
    };
    let final_path = output.join(final_name);
    fs::rename(partial, &final_path)?;
    Ok(final_path)
}
