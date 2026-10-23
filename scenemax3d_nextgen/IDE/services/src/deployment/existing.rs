//! Recover completed Web exports when opening deployment in a fresh IDE session.
use super::{Context, files};
use scenemax_ide_core::deployment::Settings;
use std::{
    fs, io,
    path::{Path, PathBuf},
    time::SystemTime,
};

pub(super) fn load(root: &Path, context: &Context) -> io::Result<()> {
    let settings = files::load_settings(root)?;
    let artifact = latest_web(&settings, context)?;
    context.edit(|report| {
        if let Some(path) = artifact {
            report.status =
                "Existing Web build found. Run Web locally, or rebuild to include source changes."
                    .into();
            report.web_artifact = Some(path.clone());
            report.artifacts.push(path);
        } else {
            report.status = "Choose platforms, then check requirements or build.".into();
        }
        report.settings = Some(settings);
    });
    Ok(())
}

fn latest_web(settings: &Settings, context: &Context) -> io::Result<Option<PathBuf>> {
    // Only inspect the build system's known layout, never recursively search projects.
    let prefix = format!("{}-", settings.name);
    let filename = format!("{}-web.zip", settings.name);
    let Ok(entries) = fs::read_dir(&settings.output) else {
        return Ok(None);
    };
    let mut candidates = Vec::new();
    for entry in entries.flatten() {
        context.check()?;
        if !entry.file_type().is_ok_and(|kind| kind.is_dir())
            || !entry.file_name().to_string_lossy().starts_with(&prefix)
        {
            continue;
        }
        let path = entry.path().join("web").join(&filename);
        if let Ok(meta) = fs::symlink_metadata(&path)
            && meta.is_file()
        {
            candidates.push((meta.modified().unwrap_or(SystemTime::UNIX_EPOCH), path));
        }
    }
    candidates.sort_by(|a, b| b.cmp(a));
    for (_, path) in candidates {
        context.check()?;
        let Ok(file) = fs::File::open(&path) else {
            continue;
        };
        let Ok(zip) = zip::ZipArchive::new(file) else {
            continue;
        };
        // Ignore partial, damaged or unrelated archives, without extracting the game.
        if zip.file_names().any(|name| name == "index.html")
            && zip.file_names().any(|name| name.ends_with(".wasm"))
        {
            return Ok(Some(path));
        }
    }
    Ok(None)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    #[test]
    fn load_recovers_existing_web_without_a_build_or_sdk() {
        let project = tempfile::tempdir().unwrap();
        let output = tempfile::tempdir().unwrap();
        let settings = Settings {
            name: "sample".into(),
            output: output.path().to_string_lossy().into(),
            ..Default::default()
        };
        files::save_settings(project.path(), &settings).unwrap();
        let make_zip = |run: &str, stem: &str, valid: bool| {
            let directory = output.path().join(run).join("web");
            fs::create_dir_all(&directory).unwrap();
            let path = directory.join(format!("{stem}-web.zip"));
            let mut zip = zip::ZipWriter::new(fs::File::create(&path).unwrap());
            for name in if valid {
                vec!["index.html", "runtime.wasm"]
            } else {
                vec!["unrelated.txt"]
            } {
                zip.start_file(name, zip::write::SimpleFileOptions::default())
                    .unwrap();
                zip.write_all(b"fixture").unwrap();
            }
            zip.finish().unwrap();
            path
        };
        let older = make_zip("sample-first", "sample", true);
        fs::File::options()
            .write(true)
            .open(&older)
            .unwrap()
            .set_modified(SystemTime::UNIX_EPOCH + std::time::Duration::from_secs(100))
            .unwrap();
        let expected = make_zip("sample-second", "sample", true);
        make_zip("sample-invalid", "sample", false);
        make_zip("other-run", "other", true);
        let context = Context {
            report: Default::default(),
            cancel: Default::default(),
        };
        load(project.path(), &context).unwrap();
        assert_eq!(
            context.report.lock().unwrap().web_artifact.as_ref(),
            Some(&expected)
        );
        fs::remove_file(expected).unwrap();
        assert_eq!(
            latest_web(&settings, &context).unwrap(),
            Some(older.clone())
        );
        fs::remove_file(older).unwrap();
        assert_eq!(latest_web(&settings, &context).unwrap(), None);
    }
}
