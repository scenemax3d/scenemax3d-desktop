use std::{
    env, fs,
    path::{Path, PathBuf},
    process::Command,
};

fn main() {
    println!("cargo:rerun-if-changed=native/effekseer_bevy/CMakeLists.txt");
    println!("cargo:rerun-if-changed=native/effekseer_bevy/scenemax_effekseer_bevy.cpp");
    println!("cargo:rerun-if-changed=native/effekseer_bevy/scenemax_effekseer_bevy.h");
    println!("cargo:rerun-if-env-changed=SCENEMAX_EFFEKSEER_NATIVE_BUILD");
    println!("cargo:rerun-if-env-changed=SCENEMAX_EFFEKSEER_NATIVE_BUILD_DIR");
    println!("cargo:rerun-if-env-changed=SCENEMAX_EFFEKSEER_NATIVE_PREBUILT_DIR");
    println!("cargo:rerun-if-env-changed=SCENEMAX_EFFEKSEER_NATIVE_SKIP_BUILD");

    if env::var_os("CARGO_FEATURE_EFFEKSEER_NATIVE").is_none() {
        return;
    }

    if let Some(prebuilt) = env::var_os("SCENEMAX_EFFEKSEER_NATIVE_PREBUILT_DIR") {
        let dir = PathBuf::from(prebuilt);
        println!("cargo:rustc-link-search=native={}", dir.display());
        println!("cargo:rustc-link-lib=dylib=scenemax_effekseer_bevy");
        copy_runtime_dll(&dir);

        return;
    }

    if env::var_os("SCENEMAX_EFFEKSEER_NATIVE_SKIP_BUILD").is_some() {
        println!(
            "cargo:warning=effekseer_native enabled, but native bridge build was skipped by SCENEMAX_EFFEKSEER_NATIVE_SKIP_BUILD"
        );
        return;
    }

    let manifest_dir = PathBuf::from(env::var("CARGO_MANIFEST_DIR").unwrap());
    let repo_root = manifest_dir
        .parent()
        .and_then(Path::parent)
        .and_then(Path::parent)
        .expect("runtime crate should live under scenemax3d_nextgen/Engine/runtime");
    let source_dir = manifest_dir.join("native/effekseer_bevy");
    let profile = env::var("PROFILE").unwrap_or_else(|_| "debug".to_owned());
    let native_build_root = env::var_os("SCENEMAX_EFFEKSEER_NATIVE_BUILD_DIR")
        .map(PathBuf::from)
        .unwrap_or_else(|| repo_root.join("tmp").join("eb"));
    let mut out_dir = native_build_root.join(&profile).join("b");
    if let Ok(cache) = fs::read_to_string(out_dir.join("CMakeCache.txt"))
        && let Some(home) = cache
            .lines()
            .find_map(|line| line.strip_prefix("CMAKE_HOME_DIRECTORY:INTERNAL="))
        && Path::new(home).canonicalize().ok() != source_dir.canonicalize().ok()
    {
        out_dir = native_build_root.join(&profile).join("b-effects");
    }
    let install_dir = native_build_root.join(&profile).join("i");
    let effekseer_root = repo_root.join("third_party/Effekseer");

    let dll = install_dir.join("bin/scenemax_effekseer_bevy.dll");
    let fresh = fs::metadata(&dll)
        .and_then(|m| m.modified())
        .ok()
        .is_some_and(|built| {
            [
                "CMakeLists.txt",
                "scenemax_effekseer_bevy.cpp",
                "scenemax_effekseer_bevy.h",
            ]
            .iter()
            .all(|p| {
                fs::metadata(source_dir.join(p))
                    .and_then(|m| m.modified())
                    .is_ok_and(|modified| modified <= built)
            })
        });
    if fresh && env::var_os("SCENEMAX_EFFEKSEER_NATIVE_BUILD").is_none() {
        println!(
            "cargo:rustc-link-search=native={}",
            install_dir.join("lib").display()
        );
        println!("cargo:rustc-link-lib=dylib=scenemax_effekseer_bevy");
        copy_runtime_dll(&install_dir.join("bin"));
        return;
    }
    let cmake = find_cmake();

    run(Command::new(&cmake)
        .arg("-S")
        .arg(&source_dir)
        .arg("-B")
        .arg(&out_dir)
        .arg(format!(
            "-DEFFEKSEER_ROOT={}",
            effekseer_root.to_string_lossy()
        ))
        .arg(format!(
            "-DCMAKE_INSTALL_PREFIX={}",
            install_dir.to_string_lossy()
        )));

    run(Command::new(&cmake)
        .arg("--build")
        .arg(&out_dir)
        .arg("--config")
        .arg("Release")
        .arg("--target")
        .arg("install"));

    let lib_dir = install_dir.join("lib");
    let bin_dir = install_dir.join("bin");
    println!("cargo:rustc-link-search=native={}", lib_dir.display());
    println!("cargo:rustc-link-search=native={}", bin_dir.display());
    println!("cargo:rustc-link-lib=dylib=scenemax_effekseer_bevy");
    copy_runtime_dll(&bin_dir);
}

fn run(command: &mut Command) {
    let status = command
        .status()
        .unwrap_or_else(|error| panic!("failed to run {:?}: {error}", command));
    if !status.success() {
        panic!("command {:?} failed with {status}", command);
    }
}

fn find_cmake() -> PathBuf {
    if let Some(path) = find_on_path("cmake.exe").or_else(|| find_on_path("cmake")) {
        return path;
    }

    let candidates = [
        r"C:\Program Files\Microsoft Visual Studio\2022\Community\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe",
        r"C:\Program Files\Microsoft Visual Studio\2022\Professional\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe",
        r"C:\Program Files\Microsoft Visual Studio\2022\Enterprise\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe",
        r"C:\Program Files (x86)\Microsoft Visual Studio\2019\Community\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe",
        r"C:\Program Files (x86)\Microsoft Visual Studio\2019\Professional\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe",
        r"C:\Program Files (x86)\Microsoft Visual Studio\2019\Enterprise\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe",
    ];

    candidates
        .iter()
        .map(PathBuf::from)
        .find(|path| path.is_file())
        .unwrap_or_else(|| PathBuf::from("cmake"))
}

fn find_on_path(name: &str) -> Option<PathBuf> {
    let path = env::var_os("PATH")?;
    env::split_paths(&path)
        .map(|dir| dir.join(name))
        .find(|candidate| candidate.is_file())
}

fn copy_runtime_dll(bin_dir: &Path) {
    let dll = bin_dir.join("scenemax_effekseer_bevy.dll");
    if !dll.is_file() {
        return;
    }

    let Some(profile_dir) = cargo_profile_dir() else {
        return;
    };

    copy_file(&dll, &profile_dir.join("scenemax_effekseer_bevy.dll"));
    copy_file(
        &dll,
        &profile_dir.join("deps").join("scenemax_effekseer_bevy.dll"),
    );
}

fn cargo_profile_dir() -> Option<PathBuf> {
    let out_dir = PathBuf::from(env::var_os("OUT_DIR")?);
    out_dir.ancestors().nth(3).map(Path::to_path_buf)
}

fn copy_file(source: &Path, destination: &Path) {
    if fs::read(source).ok() == fs::read(destination).ok() {
        return;
    }
    if let Some(parent) = destination.parent() {
        fs::create_dir_all(parent)
            .unwrap_or_else(|error| panic!("failed to create {}: {error}", parent.display()));
    }
    fs::copy(source, destination).unwrap_or_else(|error| {
        panic!(
            "failed to copy {} to {}: {error}",
            source.display(),
            destination.display()
        )
    });
}
