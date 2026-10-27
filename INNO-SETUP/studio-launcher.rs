//! Portable installation paths, applied only to the launched IDE process.
#![windows_subsystem = "windows"]
use std::{env, process::Command};
fn main() -> std::io::Result<()> {
    let exe = env::current_exe()?;
    let root = exe.parent().expect("installed executable has a parent");
    let workspace = root.join("scenemax3d_nextgen");
    Command::new(root.join("scenemax_ide.exe"))
        .current_dir(root)
        .env("SCENEMAX_BUILD_WORKSPACE", &workspace)
        .env("SCENEMAX_BUILTIN_RESOURCES", root.join("resources"))
        .env("SCENEMAX_FBX2GLTF", workspace.join("Tools/vendor/fbx2gltf/FBX2glTF-windows-x86_64.exe"))
        .env("SCENEMAX_GLTFPACK", workspace.join("Tools/vendor/gltfpack/gltfpack.exe"))
        .args(env::args_os().skip(1))
        .spawn()?;
    Ok(())
}
