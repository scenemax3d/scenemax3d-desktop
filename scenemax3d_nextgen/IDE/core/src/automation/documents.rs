use super::{scene_template, ui_template};
use serde_json::{Value, json};
fn encode(v: &Value) -> Result<String, String> {
    serde_json::to_string_pretty(v).map_err(|e| e.to_string())
}
fn parse(source: &str) -> Result<Value, String> {
    if source.len() > 8 * 1024 * 1024 {
        return Err("Document exceeds 8 MiB".into());
    }
    serde_json::from_str(source).map_err(|e| e.to_string())
}
fn entries(
    v: &Value,
    path: &str,
    out: &mut Vec<(String, Value)>,
    depth: usize,
) -> Result<(), String> {
    if depth > 24 || out.len() > 2000 {
        return Err("Document hierarchy limit exceeded".into());
    }
    if let Some(a) = v.as_array() {
        for (i, e) in a.iter().enumerate() {
            let p = format!("{path}/{i}");
            out.push((p.clone(), e.clone()));
            if e["children"].is_array() {
                entries(&e["children"], &format!("{p}/children"), out, depth + 1)?;
            }
        }
    }
    Ok(())
}
fn flatten(v: &Value) -> Result<Vec<(String, Value)>, String> {
    let mut out = vec![];
    if v["entities"].is_array() {
        entries(&v["entities"], "/entities", &mut out, 0)?;
    } else if let Some(layers) = v["layers"].as_array() {
        for (i, l) in layers.iter().enumerate() {
            let p = format!("/layers/{i}");
            out.push((p.clone(), l.clone()));
            entries(&l["widgets"], &format!("{p}/widgets"), &mut out, 0)?;
        }
    } else {
        return Err("Expected entities or layers array".into());
    }
    Ok(out)
}
fn select(v: &Value, a: &Value, layer: bool) -> Result<String, String> {
    let path = a["widgetPath"].as_str().unwrap_or("");
    let id = a[if layer {
        "layerId"
    } else if a.get("widgetId").is_some() {
        "widgetId"
    } else {
        "entity_id"
    }]
    .as_str()
    .unwrap_or("");
    let name = a[if layer { "layerName" } else { "entity_name" }]
        .as_str()
        .unwrap_or("");
    let found: Vec<_> = flatten(v)?
        .into_iter()
        .filter(|(p, e)| {
            (!layer || p.split('/').count() == 3)
                && ((!path.is_empty() && (p == path || e["name"] == path))
                    || (!id.is_empty() && e["id"] == id)
                    || (!name.is_empty() && e["name"] == name))
        })
        .collect();
    if found.len() != 1 {
        return Err("Selector must match exactly one entity, widget or layer".into());
    }
    Ok(found[0].0.clone())
}
fn remove(v: &mut Value, p: &str) -> Result<Value, String> {
    let (parent, i) = p.rsplit_once('/').ok_or("Invalid pointer")?;
    let i = i.parse::<usize>().map_err(|_| "Invalid index")?;
    let a = v
        .pointer_mut(parent)
        .and_then(Value::as_array_mut)
        .ok_or("Missing container")?;
    if i >= a.len() {
        return Err("Missing entry".into());
    }
    Ok(a.remove(i))
}
/// Validate native structure, identity uniqueness and transforms before editing.
pub fn validate(source: &str) -> Result<Value, String> {
    let v = parse(source)?;
    let nodes = flatten(&v)?;
    let mut ids = std::collections::HashSet::new();
    let mut names = std::collections::HashSet::new();
    for (_, e) in &nodes {
        if !e.is_object() {
            return Err("Entries must be objects".into());
        }
        for (key, set) in [("id", &mut ids), ("name", &mut names)] {
            if let Some(s) = e[key].as_str()
                && !s.is_empty()
                && !set.insert(s.to_owned())
            {
                return Err(format!("Duplicate {key}: {s}"));
            }
        }
        for (key, n) in [("position", 3), ("scale", 3), ("rotation", 4)] {
            if let Some(value) = e.get(key) {
                let a = value
                    .as_array()
                    .filter(|a| a.len() == n)
                    .ok_or_else(|| format!("Invalid {key}"))?;
                if a.iter()
                    .any(|v| v.as_f64().is_none_or(|n| !n.is_finite() || n.abs() > 1e10))
                {
                    return Err(format!("Invalid {key}"));
                }
                if key == "rotation" && a.iter().all(|v| v.as_f64() == Some(0.)) {
                    return Err("Zero quaternion".into());
                }
            }
        }
    }
    if v["layers"].is_array() {
        crate::scene::parse(source)?;
    }
    Ok(json!({"valid":true,"entries":nodes.len()}))
}
/// Read-only JSON inspection.
pub fn inspect(name: &str, source: &str, args: &Value) -> Result<Value, String> {
    let v = parse(source)?;
    match name {
        "designer.get_document_json" => Ok(json!({"document":v})),
        "designer.validate_document" | "ui.validate_document" => validate(source),
        "designer.list_entities" => Ok(
            json!({"entities":flatten(&v)?.into_iter().map(|(p,e)|json!({"pointer":p,"entity":e})).collect::<Vec<_>>()}),
        ),
        "ui.list_layers" => Ok(json!({"layers":v["layers"]})),
        "ui.list_widgets" => Ok(
            json!({"widgets":flatten(&v)?.into_iter().filter(|(p,_)|p.contains("/widgets/")).map(|(p,e)|json!({"pointer":p,"widget":e})).collect::<Vec<_>>()}),
        ),
        "designer.get_entity" | "ui.get_widget" => {
            let p = select(&v, args, false)?;
            Ok(json!({"pointer":p,"entity":v.pointer(&p)}))
        }
        "ui.get_schema" => Ok(
            json!({"template":ui_template(),"widgetTypes":["PANEL","TEXT_VIEW","BUTTON","IMAGE","EDIT_TEXT","LIST_VIEW"],"widgetTemplate":serde_json::from_str::<Value>(&crate::scene::add(&encode(&ui_template())?,None,"BUTTON")?.0).map_err(|e|e.to_string())?["layers"][0]["widgets"][0],"sceneTemplate":scene_template(),"rotation":"quaternion XYZW","transforms":"local to parent","selectors":"JSON pointers (widgetPath) or unique names/IDs"}),
        ),
        _ => Err("Unknown inspection tool".into()),
    }
}
fn merge(target: &mut Value, patch: &Value) -> Result<(), String> {
    let obj = target.as_object_mut().ok_or("Expected object")?;
    for (k, v) in patch.as_object().ok_or("Expected properties object")? {
        obj.insert(k.clone(), v.clone());
    }
    Ok(())
}
fn vector_eq(value: &Value, expected: &[f64]) -> bool {
    value.as_array().is_some_and(|v| {
        v.len() == expected.len() && v.iter().zip(expected).all(|(a, b)| a.as_f64() == Some(*b))
    })
}
fn transform_defaults(entity: &mut Value) -> Result<(), String> {
    for (key, default) in [("position", json!([0, 0, 0])), ("scale", json!([1, 1, 1]))] {
        if entity[key].is_null() {
            entity[key] = default;
        }
        if entity[key].as_array().is_none_or(|v| v.len() != 3) {
            return Err(format!("Invalid {key}"));
        }
    }
    Ok(())
}
fn insert(v: &mut Value, entry: Value, name: Option<&str>) -> Result<(), String> {
    let (s, p) = crate::scene3d::structure::insert(&encode(v)?, None, entry)?;
    *v = parse(&s)?;
    if let Some(name) = name {
        v.pointer_mut(&p).ok_or("Missing new entity")?["name"] = json!(name);
    }
    Ok(())
}
/// Build one validated document transaction. Callers apply it through Document::replace_text.
pub fn edit(name: &str, source: &str, a: &Value) -> Result<String, String> {
    if name == "file.modify" {
        let output = if let Some(s) = a["newContent"].as_str() {
            s.to_owned()
        } else {
            let old = a["expectedText"]
                .as_str()
                .filter(|s| !s.is_empty())
                .ok_or("Provide newContent or nonempty expectedText")?;
            let new = a["replacementText"]
                .as_str()
                .ok_or("Missing replacementText")?;
            let n = source.matches(old).count();
            if n == 0 || (!a["replaceAll"].as_bool().unwrap_or(false) && n != 1) {
                return Err(
                    "Expected text must match exactly once unless replaceAll is true".into(),
                );
            }
            source.replace(old, new)
        };
        if output.len() > 8 * 1024 * 1024 {
            return Err("Document too large".into());
        }
        return Ok(output);
    }
    let mut v = parse(source)?;
    match name {
        "designer.set_document_json" => v = a["document"].clone(),
        "designer.update_entity" => {
            let p = select(&v, a, false)?;
            let changes = a["updates"]
                .as_object()
                .ok_or("Missing updates")?
                .iter()
                .map(|(k, v)| (k.clone(), v.clone()))
                .collect::<Vec<_>>();
            v = parse(&crate::scene3d::patch(source, &p, &changes)?)?;
        }
        "designer.apply_material" => {
            let p = select(&v, a, false)?;
            v.pointer_mut(&p).ok_or("Missing entity")?["material"] = a["material"].clone();
        }
        "designer.delete_entity" | "ui.delete_widget" | "ui.delete_layer" => {
            let p = select(&v, a, name == "ui.delete_layer")?;
            remove(&mut v, &p)?;
        }
        "designer.add_primitive"
        | "designer.add_primitive_advanced"
        | "designer.create_cinematic_rig" => {
            let kind = if name.ends_with("create_cinematic_rig") {
                "CINEMATIC_RIG"
            } else {
                a["primitive"].as_str().ok_or("Missing primitive")?
            };
            let mut e = crate::scene3d::structure::template(&kind.to_uppercase())?;
            if a["properties"].is_object() {
                merge(&mut e, &a["properties"])?;
            }
            for (k, dest) in [
                ("targetEntityId", "cinematicTargetEntityId"),
                ("targetEntityName", "cinematicTargetEntityName"),
            ] {
                if a[k].is_string() {
                    e[dest] = a[k].clone();
                }
            }
            insert(&mut v, e, a["name"].as_str())?;
        }
        "designer.add_entities_batch" => {
            for e in a["entities"].as_array().ok_or("Missing entities")? {
                insert(&mut v, e.clone(), e["name"].as_str())?;
            }
        }
        "designer.duplicate_entity" | "designer.array_entities" => {
            let p = select(&v, a, false)?;
            let original = v.pointer(&p).ok_or("Missing entity")?.clone();
            if p.matches('/').count() != 2 {
                return Err("Duplicate/array currently require a top-level entity".into());
            }
            let count = a["count"].as_u64().unwrap_or(1);
            if !(1..=100).contains(&count) {
                return Err("count must be 1..100".into());
            }
            for i in 1..=count {
                let mut e = original.clone();
                transform_defaults(&mut e)?;
                for axis in 0..3 {
                    e["position"][axis] = json!(
                        original["position"][axis].as_f64().unwrap_or(0.)
                            + a["step"][axis].as_f64().unwrap_or(0.) * i as f64
                    );
                }
                insert(&mut v, e, None)?;
            }
        }
        "designer.mirror_entities" => {
            let p = select(&v, a, false)?;
            if p.matches('/').count() != 2 {
                return Err("Mirror requires a top-level entity".into());
            }
            let mut e = v.pointer(&p).ok_or("Missing entity")?.clone();
            transform_defaults(&mut e)?;
            let axis = match a["axis"].as_str() {
                Some("x") => 0,
                Some("y") => 1,
                Some("z") => 2,
                _ => return Err("Invalid axis".into()),
            };
            e["position"][axis] = json!(
                2. * a["pivot"].as_f64().unwrap_or(0.) - e["position"][axis].as_f64().unwrap_or(0.)
            );
            e["scale"][axis] = json!(-e["scale"][axis].as_f64().unwrap_or(1.));
            if a["mode"] == "move" {
                *v.pointer_mut(&p).ok_or("Missing entity")? = e;
            } else {
                insert(&mut v, e, None)?;
            }
        }
        "designer.group_entities" => {
            if a["action"] == "ungroup" {
                let p = select(&v, a, false)?;
                let e = v.pointer(&p).ok_or("Missing group")?;
                if e["type"] != "SECTION"
                    || p.matches('/').count() != 2
                    || !vector_eq(&e["position"], &[0., 0., 0.])
                    || !vector_eq(&e["rotation"], &[0., 0., 0., 1.])
                    || !vector_eq(&e["scale"], &[1., 1., 1.])
                {
                    return Err(
                        "Ungroup requires a top-level section with identity transform".into(),
                    );
                }
                let children = e["children"].as_array().ok_or("Missing children")?.clone();
                remove(&mut v, &p)?;
                v["entities"]
                    .as_array_mut()
                    .ok_or("Missing entities")?
                    .extend(children);
            } else {
                let ids = a["entity_ids"].as_array().ok_or("Provide entity_ids")?;
                if ids.is_empty() {
                    return Err("Choose entities".into());
                }
                let original = v["entities"].as_array().ok_or("Missing entities")?.clone();
                let children: Vec<_> = original
                    .iter()
                    .filter(|e| ids.contains(&e["id"]))
                    .cloned()
                    .collect();
                if children.len() != ids.len() {
                    return Err("IDs must be unique top-level entities".into());
                }
                v["entities"] = json!(
                    original
                        .into_iter()
                        .filter(|e| !ids.contains(&e["id"]))
                        .collect::<Vec<_>>()
                );
                let mut n = 1;
                let text = encode(&v)?;
                while text.contains(&format!("mcp_group_{n}")) {
                    n += 1;
                }
                v["entities"].as_array_mut().ok_or("Missing entities")?.push(json!({"type":"SECTION","id":format!("mcp_group_{n}"),"name":a["group_name"].as_str().unwrap_or("group"),"position":[0,0,0],"rotation":[0,0,0,1],"scale":[1,1,1],"children":children}));
            }
        }
        "ui.add_layer" => {
            v["layers"].as_array_mut().ok_or("Expected UI document")?.push(json!({"name":a["name"],"visible":a["visible"].as_bool().unwrap_or(true),"zOrder":a["zOrder"].as_i64().unwrap_or(0),"widgets":[]}));
        }
        "ui.add_widget" => {
            let (s, p) = crate::scene::add(
                source,
                a["parent"].as_str(),
                a["type"].as_str().ok_or("Missing type")?,
            )?;
            v = parse(&s)?;
            let e = v.pointer_mut(&p).ok_or("Missing new widget")?;
            if let Some(name) = a["name"].as_str() {
                e["name"] = json!(name);
            }
            if a["properties"].is_object() {
                merge(e, &a["properties"])?;
            }
        }
        "ui.update_widget" | "ui.update_layer" => {
            let p = select(&v, a, name == "ui.update_layer")?;
            if a["updates"].get("children").is_some() || a["updates"].get("widgets").is_some() {
                return Err("Use add/delete tools to change hierarchy".into());
            }
            merge(v.pointer_mut(&p).ok_or("Missing target")?, &a["updates"])?;
        }
        _ => return Err("Unknown document edit".into()),
    }
    let output = encode(&v)?;
    validate(&output)?;
    Ok(output)
}
