//! Headless access to the same packaging worker, useful for isolated acceptance runs.
use scenemax_ide_core::deployment::Settings;
use scenemax_ide_services::{
    Filesystem,
    deployment::{Builder, Operation, Request, workspace},
};
use std::{io, path::PathBuf, time::Duration};
fn main() -> Result<(), Box<dyn std::error::Error>> {
    let args: Vec<_> = std::env::args_os().skip(1).collect();
    let root = args
        .first()
        .ok_or("Usage: package PROJECT OUTPUT [PREBUILT_PROJECTOR | --web]")?;
    let project = Filesystem::open_project(&PathBuf::from(root))?;
    let mut settings = Settings {
        name: "package-smoke".into(),
        output: args
            .get(1)
            .ok_or("Missing output folder")?
            .to_string_lossy()
            .into_owned(),
        native_effects: false,
        ..Default::default()
    };
    if args.get(2).is_some_and(|arg| arg == "--web") {
        for platform in &mut settings.platforms {
            platform.selected = platform.target == scenemax_ide_core::deployment::Target::Web;
        }
    } else if let Some(runtime) = args.get(2)
        && let Some(platform) = settings.platforms.iter_mut().find(|p| p.selected)
    {
        platform.runtime = runtime.to_string_lossy().into_owned();
    }
    let request = Request {
        multiplayer_override: None,
        project: project.root().to_owned(),
        entry: project
            .entry_point()
            .ok_or("Missing main script")?
            .to_owned(),
        workspace: workspace(),
        settings,
    };
    let mut builder = Builder::default();
    builder.start(Operation::Build(request))?;
    let mut revision = 0;
    loop {
        if let Some(report) = builder.report() {
            if report.revision != revision {
                println!("{}% {}", report.progress, report.status);
                revision = report.revision;
            }
            if report.finished {
                for artifact in report.artifacts {
                    println!("Artifact: {}", artifact.display());
                }
                if !report.success {
                    for text in report.logs {
                        eprint!("{text}");
                    }
                    return Err(io::Error::other(report.status).into());
                }
                return Ok(());
            }
        }
        std::thread::sleep(Duration::from_millis(100));
    }
}
