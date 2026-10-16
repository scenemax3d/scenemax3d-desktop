//! Java-compatible sizing analysis of the immutable deploy snapshot.
use super::{Context, files};
use serde::Serialize;
use std::{
    collections::BTreeMap,
    fmt::Write as _,
    fs, io,
    path::{Path, PathBuf},
    time::{SystemTime, UNIX_EPOCH},
};

#[derive(Serialize)]
struct Row {
    label: String,
    path: String,
    bytes: u64,
    #[serde(rename = "miB")]
    mib: f64,
    count: usize,
}
impl Row {
    fn new(label: String, path: String, bytes: u64, count: usize) -> Self {
        Self {
            label,
            path,
            bytes,
            mib: mib(bytes),
            count,
        }
    }
}
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct Analysis {
    generated: String,
    deploy_total_bytes: u64,
    deploy_total_mi_b: f64,
    count_unit: &'static str,
    categories: Vec<Row>,
    top_contributors: Vec<Row>,
    models: Vec<serde_json::Value>,
}
fn mib(bytes: u64) -> f64 {
    (bytes as f64 / 1_048_576. * 100.).round() / 100.
}
fn category(path: &str) -> (String, String) {
    let (prefix, rest, shared) = if let Some(rest) = path.strip_prefix("builtin/resources/") {
        ("builtin/resources", rest, true)
    } else if let Some(rest) = path.strip_prefix("resources/") {
        ("resources", rest, false)
    } else if path.starts_with("scripts/") {
        return ("runtime scripts".into(), "scripts".into());
    } else {
        return ("package metadata / other".into(), ".".into());
    };
    let (folder, _) = rest.split_once('/').unwrap_or(("", rest));
    let label = match folder.to_ascii_lowercase().as_str() {
        "models" => "Models",
        "audio" => "audio",
        "videos" => "videos",
        "textures" | "sprites" => "Textures / sprites",
        "effects" => "Effekseer effects",
        "material" | "materials" => "materials",
        "fonts" => "fonts",
        "animations" => "animations",
        "probes" => "light probes",
        "shaders" => "shaders",
        "environment_shaders" => "environment shaders",
        "skyboxes" => "skyboxes",
        "ik" => "IK",
        "throw_motions" => "throw motions",
        "weapons" => "weapons",
        _ => "resources / other",
    };
    (
        format!("{label}{}", if shared { " (shared)" } else { "" }),
        if folder.is_empty() {
            prefix.into()
        } else {
            format!("{prefix}/{folder}")
        },
    )
}
fn timestamp(seconds: u64) -> String {
    let mut days = seconds / 86400;
    let mut year = 1970;
    let leap = |y: u64| y.is_multiple_of(4) && (!y.is_multiple_of(100) || y.is_multiple_of(400));
    while year < 9999 {
        let length = if leap(year) { 366 } else { 365 };
        if days < length {
            break;
        }
        days -= length;
        year += 1;
    }
    let months = [
        31,
        if leap(year) { 29 } else { 28 },
        31,
        30,
        31,
        30,
        31,
        31,
        30,
        31,
        30,
        31,
    ];
    let mut month = 1;
    for length in months {
        if days < length {
            break;
        }
        days -= length;
        month += 1;
    }
    format!(
        "{year:04}-{month:02}-{:02}T{:02}:{:02}:{:02}Z",
        days + 1,
        seconds / 3600 % 24,
        seconds / 60 % 60,
        seconds % 60
    )
}
pub(super) fn write(snapshot: &Path, output: &Path, context: &Context) -> io::Result<PathBuf> {
    let mut groups = BTreeMap::<(String, String), (u64, usize)>::new();
    let mut contributors = Vec::new();
    let mut total = 0;
    for file in files::list(snapshot, context)? {
        context.check()?;
        let bytes = files::regular(&file)?.len();
        let path = file
            .strip_prefix(snapshot)
            .map_err(io::Error::other)?
            .to_str()
            .ok_or_else(|| io::Error::other("Report paths must be Unicode."))?
            .replace('\\', "/");
        total += bytes;
        let group = groups.entry(category(&path)).or_default();
        group.0 += bytes;
        group.1 += 1;
        contributors.push(Row::new(
            file.file_name()
                .unwrap_or_default()
                .to_string_lossy()
                .into_owned(),
            path,
            bytes,
            0,
        ));
    }
    let mut categories: Vec<_> = groups
        .into_iter()
        .map(|((label, path), (bytes, count))| Row::new(label, path, bytes, count))
        .collect();
    let order = |a: &Row, b: &Row| b.bytes.cmp(&a.bytes).then_with(|| a.path.cmp(&b.path));
    categories.sort_by(order);
    contributors.sort_by(order);
    contributors.truncate(40);
    let dependency_report = output.join("package-size.json");
    let models = if dependency_report.is_file() {
        let value: serde_json::Value = serde_json::from_slice(&fs::read(dependency_report)?)?;
        value["models"]
            .as_array()
            .into_iter()
            .flatten()
            .filter(|model| model["status"] == "included")
            .cloned()
            .collect()
    } else {
        Vec::new()
    };
    let analysis = Analysis {
        generated: timestamp(
            SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .map_err(io::Error::other)?
                .as_secs(),
        ),
        deploy_total_bytes: total,
        deploy_total_mi_b: mib(total),
        count_unit: "files",
        categories,
        top_contributors: contributors,
        models,
    };
    let mut text = format!(
        "SceneMax Packaging Size Report\nGenerated: {}\nDeploy content total: {} bytes ({:.2} MiB)\n\nAsset Categories\n",
        analysis.generated,
        total,
        mib(total)
    );
    for row in &analysis.categories {
        writeln!(
            text,
            "{:>10}  {:<24}  count={:<4}  {}",
            format!("{:.2} MiB", row.mib),
            row.label,
            row.count,
            row.path
        )
        .map_err(io::Error::other)?;
    }
    text.push_str("\nTop Contributors\n");
    for row in &analysis.top_contributors {
        writeln!(text, "{:>10}  {}", format!("{:.2} MiB", row.mib), row.path)
            .map_err(io::Error::other)?;
    }
    text.push_str("\nPackaged Models\n");
    for model in &analysis.models {
        let field = |key: &str| model[key].as_str().unwrap_or("-");
        writeln!(
            text,
            "\n  {}  [{}]  {:.2} MiB\n    Registered: {}\n    Runtime: {}\n    {}",
            field("name"),
            field("status"),
            mib(model["bytes"].as_u64().unwrap_or(0)),
            field("path"),
            field("resolvedPath"),
            field("reason")
        )
        .map_err(io::Error::other)?;
        if let Some(sources) = model["referencedBy"].as_array() {
            for source in sources {
                writeln!(text, "    Used by: {}", source.as_str().unwrap_or_default())
                    .map_err(io::Error::other)?;
            }
        }
        if let Some(files) = model["files"].as_array() {
            for file in files {
                writeln!(
                    text,
                    "    {:>10.2} MiB  {}",
                    mib(file["bytes"].as_u64().unwrap_or(0)),
                    file["path"].as_str().unwrap_or_default()
                )
                .map_err(io::Error::other)?;
            }
        }
    }
    text.push_str("\nNotes\n- This report measures the staged deploy folder before it is embedded into target packages.\n- Category counts are files (including catalogs and sidecars), not logical resource registrations.\n- Shared resources are listed separately; every staged file is counted exactly once in category totals.\n- Packaged models include glTF/GLB descriptors, external buffers and textures; aliases can share files, so model totals must not be summed.\n- Top Contributors lists only the largest 40 files; Packaged Models lists all included models.\n- Large files here are candidates for asset compression, LOD reduction, or removal.\n- The final artifact also includes the projector/runtime and platform libraries. Compression and deduplication reduce archive sizes.\n- File size alone does not measure GPU memory, loading time or frame rate.\n");
    context.check()?;
    let report = output.join("size-report-analysis.txt");
    fs::write(
        output.join("size-report-analysis.json"),
        serde_json::to_vec_pretty(&analysis)?,
    )?;
    fs::write(&report, text)?;
    context.log(format!(
        "Wrote size report analysis: {}\n",
        report.display()
    ));
    Ok(report)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn dates_are_utc_and_handle_leap_days() {
        assert_eq!(timestamp(0), "1970-01-01T00:00:00Z");
        assert_eq!(timestamp(951782400), "2000-02-29T00:00:00Z");
    }
}
