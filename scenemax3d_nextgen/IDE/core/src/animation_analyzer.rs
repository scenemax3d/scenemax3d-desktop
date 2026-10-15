//! Lossless model catalog edits for the animation analyzer.
use serde_json::{Value, json};
use std::path::Path;
/// Whether a file is a model resource catalog.
pub fn is_file(path: &Path) -> bool {
    path.file_name().is_some_and(|n| {
        n.eq_ignore_ascii_case("models-ext.json") || n.eq_ignore_ascii_case("models.json")
    }) && path
        .parent()
        .and_then(Path::file_name)
        .is_some_and(|n| n.eq_ignore_ascii_case("models"))
}
/// Apply a single cell edit, insertion or deletion while retaining unknown metadata.
pub fn edit(source: &str, pointer: &str, value: Value) -> Result<String, String> {
    let mut data: Value = serde_json::from_str(source).map_err(|e| e.to_string())?;
    let (prefix, key) = pointer.rsplit_once('/').ok_or("Invalid edit")?;
    if key == "add" {
        let model = data
            .pointer_mut(prefix)
            .and_then(Value::as_object_mut)
            .ok_or("Missing model")?;
        let rows = model
            .entry("animationFrameRanges")
            .or_insert(json!([]))
            .as_array_mut()
            .ok_or("Invalid records")?;
        rows.push(value);
    } else if key == "delete" {
        let rows = data
            .pointer_mut(prefix)
            .and_then(Value::as_array_mut)
            .ok_or("Missing records")?;
        let i = value.as_u64().ok_or("Missing row")? as usize;
        if i >= rows.len() {
            return Err("Record no longer exists".into());
        }
        rows.remove(i);
    } else {
        if !matches!(key, "name" | "sourceAnimation" | "start" | "end") {
            return Err("Unsupported record field".into());
        }
        if matches!(key, "start" | "end") && value.as_u64().is_none() {
            return Err("Frame must be a nonnegative integer".into());
        }
        data.pointer_mut(prefix)
            .and_then(Value::as_object_mut)
            .ok_or("Missing record")?
            .insert(key.into(), value);
    }
    // Preserve old per-record fields; make the legacy shared source unambiguous.
    let model_index = pointer
        .split('/')
        .nth(2)
        .and_then(|v| v.parse::<usize>().ok())
        .ok_or("Missing model index")?;
    if let Some(model) = data["models"]
        .as_array_mut()
        .ok_or("Missing models array")?
        .get_mut(model_index)
    {
        let fallback = model["animationFrameRangesSourceAnimation"]
            .as_str()
            .or_else(|| model["sourceAnimation"].as_str())
            .unwrap_or("")
            .to_owned();
        if let Some(rows) = model["animationFrameRanges"].as_array_mut() {
            for row in rows.iter_mut() {
                if row["sourceAnimation"].as_str().is_none_or(str::is_empty) {
                    row["sourceAnimation"] = json!(fallback);
                }
            }
            let common = rows
                .first()
                .and_then(|r| r["sourceAnimation"].as_str())
                .filter(|v| {
                    !v.is_empty()
                        && rows
                            .iter()
                            .all(|r| r["sourceAnimation"].as_str() == Some(*v))
                })
                .map(str::to_owned);
            if let Some(m) = model.as_object_mut() {
                m.remove("animationFrameRangesSourceAnimation");
                if let Some(common) = common {
                    m.insert("animationFrameRangesSourceAnimation".into(), json!(common));
                }
            }
        }
    }
    serde_json::to_string_pretty(&data).map_err(|e| e.to_string())
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn record_edit_preserves_unknown_fields_and_legacy_sources() {
        let source = json!({"extra":42,"models":[{"name":"fixture","animationFrameRangesSourceAnimation":"Take","physics":{"custom":1},"animationFrameRanges":[{"name":"part","start":0,"end":5,"custom":true}]}]}).to_string();
        let edited = edit(&source, "/models/0/animationFrameRanges/0/end", json!(8)).unwrap();
        let data: Value = serde_json::from_str(&edited).unwrap();
        assert_eq!(
            data["models"][0]["animationFrameRanges"][0]["sourceAnimation"],
            "Take"
        );
        assert_eq!(data["models"][0]["animationFrameRanges"][0]["custom"], true);
        assert_eq!(data["extra"], 42);
        assert!(edit(&source, "/models/0/animationFrameRanges/0/start", json!(-1)).is_err());
    }
}
