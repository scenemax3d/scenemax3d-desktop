//! Exercise the actual export pipeline and preview server without the IDE UI.
use scenemax_ide_core::deployment::{Settings, Target};
use scenemax_ide_services::deployment::{Builder, Operation, Request, preview::Preview};
use std::{io, path::PathBuf, time::Duration};

fn main() -> io::Result<()> {
    let workspace = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("../..")
        .canonicalize()?;
    let args: Vec<_> = std::env::args().skip(1).collect();
    let artifact = if args.first().is_some_and(|s| s == "--preview") {
        PathBuf::from(
            args.get(1)
                .ok_or_else(|| io::Error::other("--preview requires a ZIP"))?,
        )
    } else {
        let project = args
            .first()
            .map(PathBuf::from)
            .unwrap_or_else(|| workspace.join("Tests/fixtures/web_effects/project"))
            .canonicalize()?;
        let mut settings = Settings {
            name: "effects-fixture".into(),
            output: workspace
                .join("target/web-effects-exports")
                .to_string_lossy()
                .into_owned(),
            native_effects: false,
            builtin_resources: args.get(2).cloned().unwrap_or_default(),
            ..Default::default()
        };
        if let Some(path) = args.get(3) {
            settings.web_loading = serde_json::from_slice(&std::fs::read(path)?)?;
        }
        for platform in &mut settings.platforms {
            platform.selected = platform.target == Target::Web;
        }
        let mut builder = Builder::default();
        builder.start(Operation::BuildTemporary(Request {
            entry: args
                .get(1)
                .map(PathBuf::from)
                .unwrap_or_else(|| project.join("scripts/main")),
            project,
            workspace,
            settings,
        }))?;
        let mut revision = 0;
        loop {
            if let Some(report) = builder.report() {
                if report.revision != revision {
                    println!("{}", report.status);
                    revision = report.revision;
                }
                if report.finished {
                    if !report.success {
                        return Err(io::Error::other(format!(
                            "{}; log: {:?}",
                            report.status, report.log_path
                        )));
                    }
                    break report
                        .web_artifact
                        .ok_or_else(|| io::Error::other("Missing Web artifact"))?;
                }
            }
            std::thread::sleep(Duration::from_millis(250));
        }
    };
    println!("Artifact: {}", artifact.display());
    let mut preview = Preview::default();
    preview.serve(artifact)?;
    loop {
        let status = preview.status();
        if let Some(url) = status.url {
            println!("Preview: {url}");
            break;
        }
        if !status.active {
            return Err(io::Error::other(status.message));
        }
        std::thread::sleep(Duration::from_millis(50));
    }
    println!("Press Enter to stop the preview.");
    let mut line = String::new();
    io::stdin().read_line(&mut line)?;
    preview.stop();
    Ok(())
}
