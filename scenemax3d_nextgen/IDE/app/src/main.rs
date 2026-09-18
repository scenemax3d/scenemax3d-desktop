//! Command-line entry point; composition and behavior live in the library.
#![forbid(unsafe_code)]
use clap::Parser;
use scenemax_ide::LaunchOptions;
use std::path::PathBuf;

#[derive(Parser)]
#[command(about = "SceneMax native Bevy IDE — no JVM required")]
struct Args {
    #[arg(long)]
    project_root: Option<PathBuf>,
    #[arg(long)]
    project_catalog: Option<PathBuf>,
    #[arg(long)]
    script: Option<PathBuf>,
    #[arg(long)]
    projector: Option<PathBuf>,
    #[arg(long, hide = true)]
    smoke_frames: Option<u32>,
    #[arg(long, hide = true)]
    smoke_screenshot: Option<PathBuf>,
    #[arg(long, hide = true, requires = "smoke_frames")]
    smoke_menu: bool,
    #[arg(long, hide = true, requires = "smoke_frames")]
    smoke_projects: bool,
    #[arg(long, hide = true, requires = "smoke_frames")]
    smoke_run_project: bool,
    #[arg(long, hide = true, requires = "smoke_frames")]
    smoke_completion: bool,
    #[arg(long, hide = true, requires = "smoke_frames")]
    smoke_scene_entry: Option<usize>,
    #[arg(long, hide = true, requires = "smoke_frames")]
    smoke_tree_menu: Option<PathBuf>,
}
fn main() -> anyhow::Result<()> {
    let args = Args::parse();
    scenemax_ide::run(LaunchOptions {
        select_catalog_project: args.project_root.is_none(),
        project_root: args.project_root.unwrap_or(std::env::current_dir()?),
        project_catalog: args.project_catalog,
        script: args.script,
        projector: args.projector,
        smoke_frames: args.smoke_frames,
        smoke_screenshot: args.smoke_screenshot,
        smoke_menu: args.smoke_menu,
        smoke_projects: args.smoke_projects,
        smoke_run_project: args.smoke_run_project,
        smoke_completion: args.smoke_completion,
        smoke_scene_entry: args.smoke_scene_entry,
        smoke_tree_menu: args.smoke_tree_menu,
    })
}
