use std::path::PathBuf;

use anyhow::Result;
use clap::{Parser, Subcommand};
use scenemax_runtime::{
    BevyRetargetDesignerLaunch, BevyShaderDesignerLaunch, ProjectorLaunch, WindowSettings,
};
use tracing_subscriber::{EnvFilter, fmt};

#[derive(Debug, Parser)]
#[command(name = "scenemax-projector-nextgen")]
#[command(about = "Bevy-based SceneMax3D NextGen runtime projector")]
struct Cli {
    #[command(subcommand)]
    command: Option<Command>,
}

#[derive(Debug, Subcommand)]
enum Command {
    /// Run the projector from a SceneMax script path.
    Run {
        #[arg(long)]
        script: Option<PathBuf>,

        #[arg(long)]
        project_root: Option<PathBuf>,

        #[arg(long, default_value_t = 1600)]
        width: u32,

        #[arg(long, default_value_t = 900)]
        height: u32,
    },

    /// Reserved for a future exported runtime IR flow.
    RunIr {
        #[arg(long)]
        ir: PathBuf,
    },

    /// Audit a project for NextGen-compatible assets.
    AuditAssets {
        #[arg(long)]
        project: PathBuf,
    },

    /// Resolve a SceneMax model name without opening a Bevy window.
    ResolveModel {
        #[arg(long)]
        project: PathBuf,

        #[arg(long)]
        model: String,
    },

    /// Open the Bevy-native shader designer for a SceneMax shader document.
    ShaderDesigner {
        #[arg(long)]
        shader: PathBuf,

        #[arg(long)]
        project_root: Option<PathBuf>,
    },

    /// Open the Bevy-native animation retarget designer for an imported animation.
    RetargetDesigner {
        #[arg(long)]
        project_root: PathBuf,

        #[arg(long)]
        animation: String,

        #[arg(long)]
        model: Option<String>,
    },
}

fn main() -> Result<()> {
    fmt()
        .with_env_filter(EnvFilter::from_default_env())
        .without_time()
        .init();

    let cli = Cli::parse();
    let packaged_project_root = packaged_project_root();
    match cli.command.unwrap_or(Command::Run {
        script: None,
        project_root: packaged_project_root,
        width: 1600,
        height: 900,
    }) {
        Command::Run {
            script,
            project_root,
            width,
            height,
        } => {
            let launch = ProjectorLaunch {
                script,
                project_root,
                window: WindowSettings { width, height },
            };
            scenemax_runtime::run_bevy_projector(launch);
        }
        Command::RunIr { ir } => {
            tracing::info!(path = %ir.display(), "IR loading is the next milestone");
        }
        Command::AuditAssets { project } => {
            scenemax_runtime::audit_assets(&project)?;
        }
        Command::ResolveModel { project, model } => {
            let path = scenemax_runtime::resolve_model_asset_for_project(&project, &model)?;
            println!("{model} -> {path}");
        }
        Command::ShaderDesigner {
            shader,
            project_root,
        } => {
            scenemax_runtime::run_bevy_shader_designer(BevyShaderDesignerLaunch {
                shader,
                project_root,
            });
        }
        Command::RetargetDesigner {
            project_root,
            animation,
            model,
        } => {
            scenemax_runtime::run_bevy_retarget_designer(BevyRetargetDesignerLaunch {
                project_root,
                animation,
                model,
            });
        }
    }

    Ok(())
}

fn packaged_project_root() -> Option<PathBuf> {
    let current_dir = std::env::current_dir().ok()?;
    let has_staged_main = current_dir.join("running").join("main").is_file()
        || current_dir.join("running").join("main.code").is_file();
    let has_resources = current_dir.join("resources").is_dir();
    if has_staged_main && has_resources {
        Some(current_dir)
    } else {
        None
    }
}
