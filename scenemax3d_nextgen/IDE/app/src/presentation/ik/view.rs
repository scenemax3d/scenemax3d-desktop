use super::*;
use scenemax_ide_ui::{button, label, number, property, theme::*};
pub(super) struct Parts {
    pub inspector: Entity,
    pub viewport: Entity,
    pub status: Entity,
    pub animation: Entity,
    pub target_bones: Entity,
}
fn row(c: &mut Commands, p: Entity) -> Entity {
    c.spawn((
        Node {
            flex_wrap: FlexWrap::Wrap,
            column_gap: px(5.),
            row_gap: px(4.),
            flex_shrink: 0.,
            ..default()
        },
        ChildOf(p),
    ))
    .id()
}
fn title(c: &mut Commands, p: Entity, text: &str) {
    c.spawn((label(text, 12.), ChildOf(p)));
}
fn field(
    c: &mut Commands,
    p: Entity,
    data: &Value,
    key: &str,
    caption: &str,
    options: Option<&[String]>,
) {
    title(c, p, caption);
    let value = display(data.pointer(key).unwrap_or(&Value::Null));
    let marker = Field(key.into(), value.clone());
    if let Some(options) = options {
        property::dropdown(c, p, marker, &value, options);
    } else {
        property::input(c, p, marker, &value);
    }
}
fn numeric(
    (c, p): (&mut Commands, Entity),
    data: &Value,
    key: &str,
    caption: &str,
    min: f64,
    max: f64,
    step: f64,
) {
    title(c, p, caption);
    let value = display(data.pointer(key).unwrap_or(&Value::Null));
    number::input(
        c,
        p,
        Field(key.into(), value.clone()),
        &value,
        min,
        max,
        step,
    );
}
// Keep each vector together, including its per-axis sliders, at any inspector width.
fn vector(c: &mut Commands, p: Entity, data: &Value, key: &str, caption: &str, range: (f64, f64)) {
    title(c, p, caption);
    let axes = c
        .spawn((
            Node {
                display: Display::Grid,
                width: percent(100.),
                min_width: px(0.),
                grid_template_columns: vec![GridTrack::flex(1.); 3],
                column_gap: px(6.),
                flex_shrink: 0.,
                ..default()
            },
            ChildOf(p),
        ))
        .id();
    for (i, axis) in ["X", "Y", "Z"].iter().enumerate() {
        let cell = c
            .spawn((
                Node {
                    min_width: px(0.),
                    flex_direction: FlexDirection::Column,
                    row_gap: px(3.),
                    ..default()
                },
                ChildOf(axes),
            ))
            .id();
        numeric(
            (c, cell),
            data,
            &format!("{key}/{i}"),
            axis,
            range.0,
            range.1,
            0.05,
        );
    }
}
pub(super) fn build(c: &mut Commands, host: Entity) -> Parts {
    c.entity(host).insert(Node {
        width: percent(100.),
        height: percent(100.),
        min_height: px(0.),
        flex_direction: FlexDirection::Column,
        ..default()
    });
    let toolbar = row(c, host);
    c.entity(toolbar).insert(BackgroundColor(HEADER));
    title(c, toolbar, "IK ASSET DESIGNER");
    for (caption, action) in [
        ("Save", Action::Save),
        ("Undo", Action::Undo),
        ("Redo", Action::Redo),
        ("Refresh models", Action::Refresh),
    ] {
        button(c, toolbar, caption, action);
    }
    let body = c
        .spawn((
            Node {
                flex_grow: 1.,
                min_height: px(0.),
                ..default()
            },
            ChildOf(host),
        ))
        .id();
    let left = c
        .spawn((
            Node {
                width: px(365.),
                min_width: px(290.),
                flex_direction: FlexDirection::Column,
                ..default()
            },
            BackgroundColor(PANEL),
            ChildOf(body),
        ))
        .id();
    let inspector = property::scroll_column(c, left);
    let right = c
        .spawn((
            Node {
                flex_grow: 1.,
                min_width: px(0.),
                flex_direction: FlexDirection::Column,
                row_gap: px(5.),
                padding: px(8.).all(),
                ..default()
            },
            ChildOf(body),
        ))
        .id();
    let tools = row(c, right);
    for (caption, action) in [
        ("Run once", Action::Run),
        ("Play layer", Action::Play),
        ("Play all", Action::PlayAll),
        ("Stop IK", Action::Stop),
        ("Reset pose", Action::Reset),
        ("Reset view", Action::Frame),
    ] {
        button(c, tools, caption, action);
    }
    let gizmos = row(c, right);
    for (caption, action) in [
        ("Move", Action::Mode("Move")),
        ("Rotate", Action::Mode("Rotate")),
        ("Scale", Action::Mode("Scale")),
        ("Edit model", Action::EditTarget(false)),
        ("Edit target", Action::EditTarget(true)),
    ] {
        button(c, gizmos, caption, action);
    }
    let animation = row(c, right);
    let playback = row(c, right);
    button(c, playback, "Play animation", Action::PlayAnim);
    button(c, playback, "Stop animation", Action::StopAnim);
    let target_bones = row(c, right);
    title(
        c,
        right,
        "Right-drag: orbit · Wheel: zoom · Left-drag an axis: transform model / target",
    );
    let viewport = c
        .spawn((
            Node {
                flex_grow: 1.,
                min_height: px(140.),
                ..default()
            },
            BackgroundColor(BG),
            ChildOf(right),
        ))
        .id();
    let status = c
        .spawn((
            label("Select a rigged model to begin.", 12.),
            Node {
                max_height: px(95.),
                overflow: Overflow::scroll_y(),
                ..default()
            },
            ChildOf(right),
        ))
        .id();
    Parts {
        inspector,
        viewport,
        status,
        animation,
        target_bones,
    }
}
pub(super) fn inspector(c: &mut Commands, p: Entity, s: &State) {
    let data = &s.data;
    title(c, p, "ASSET");
    field(c, p, data, "/id", "IK ID", None);
    let models = std::iter::once(String::new())
        .chain(s.models.iter().map(|a| a.name.clone()))
        .collect::<Vec<_>>();
    field(
        c,
        p,
        data,
        "/targetModelId",
        "Character / rig model",
        Some(&models),
    );
    title(c, p, "LAYERS");
    let layers = data["layers"].as_array().cloned().unwrap_or_default();
    let selector = c
        .spawn((
            Node {
                width: percent(100.),
                flex_direction: FlexDirection::Column,
                flex_shrink: 0.,
                ..default()
            },
            LayerSelector,
            ChildOf(p),
        ))
        .id();
    layer_selector(c, selector, s);
    let tools = row(c, p);
    for (caption, action) in [
        ("Add", Action::Add),
        ("Duplicate", Action::Duplicate),
        ("Delete", Action::Delete),
        ("Humanoid", Action::Humanoid),
    ] {
        button(c, tools, caption, action);
    }
    if let Some(layer) = layers.get(s.layer) {
        let prefix = format!("/layers/{}", s.layer);
        field(c, p, data, &format!("{prefix}/id"), "Layer ID", None);
        field(
            c,
            p,
            data,
            &format!("{prefix}/solverType"),
            "Solver",
            Some(
                &ik::SOLVERS
                    .iter()
                    .map(|s| s.to_string())
                    .collect::<Vec<_>>(),
            ),
        );
        title(c, p, "Use case");
        property::dropdown(
            c,
            p,
            Control("preset"),
            ik::PRESETS[s.preset],
            &ik::PRESETS
                .iter()
                .map(|s| s.to_string())
                .collect::<Vec<_>>(),
        );
        button(c, p, "Apply use case to selected layer", Action::Preset);
        field(
            c,
            p,
            data,
            &format!("{prefix}/enabled"),
            "Enabled",
            Some(&["true".into(), "false".into()]),
        );
        numeric(
            (c, p),
            data,
            &format!("{prefix}/weight"),
            "Weight",
            0.,
            1.,
            0.05,
        );
        numeric(
            (c, p),
            data,
            &format!("{prefix}/priority"),
            "Priority",
            -100.,
            100.,
            1.,
        );
        numeric(
            (c, p),
            data,
            &format!("{prefix}/blendTime"),
            "Blend time (seconds)",
            0.,
            10.,
            0.05,
        );
        let solver = layer["solverType"].as_str().unwrap_or("");
        let keys: &[(&str, &str)] = match solver {
            "FABRIK" => &[("start", "Start joint"), ("end", "End joint")],
            "LookAtIK" | "AimIK" => &[("end", "End joint")],
            "ThreeBoneIK" => &[
                ("root", "Root joint"),
                ("middle", "Middle joint"),
                ("secondMiddle", "Second middle joint"),
                ("end", "End joint"),
            ],
            _ => &[
                ("root", "Root joint"),
                ("middle", "Middle joint"),
                ("end", "End joint"),
            ],
        };
        let bones = std::iter::once(String::new())
            .chain(s.bones.iter().cloned())
            .collect::<Vec<_>>();
        for (key, caption) in keys {
            let key = format!("{prefix}/chain/{key}");
            field(c, p, data, &key, caption, Some(&bones));
        }
        if matches!(solver, "LookAtIK" | "AimIK") {
            field(
                c,
                p,
                data,
                &format!("{prefix}/affectedJoints"),
                "Affected joints (comma separated)",
                None,
            );
        }
        field(
            c,
            p,
            data,
            &format!("{prefix}/target"),
            "Runtime target: object or object.\"joint\"",
            None,
        );
        field(
            c,
            p,
            data,
            &format!("{prefix}/poleTarget"),
            "Pole target (optional object / joint)",
            None,
        );
        field(
            c,
            p,
            data,
            &format!("{prefix}/matchTargetRotation"),
            "Match target orientation",
            Some(&["true".into(), "false".into()]),
        );
        for (key, caption) in [
            ("positionOffset", "Contact offset in target space"),
            ("rotationOffset", "Contact rotation offset (degrees)"),
        ] {
            vector(
                c,
                p,
                data,
                &format!("{prefix}/{key}"),
                caption,
                (-360., 360.),
            );
        }
        field(
            c,
            p,
            data,
            &format!("{prefix}/allowStretch"),
            "Allow stretch",
            Some(&["true".into(), "false".into()]),
        );
        for (key, caption, min, max, step) in [
            ("maxStretch", "Maximum stretch", 1., 5., 0.01),
            ("maxAngle", "Maximum aim angle", 0., 180., 1.),
            ("iterations", "Solver iterations", 1., 64., 1.),
        ] {
            numeric(
                (c, p),
                data,
                &format!("{prefix}/{key}"),
                caption,
                min,
                max,
                step,
            );
        }
    }
    title(c, p, "PREVIEW TARGET");
    let targets = ["Sphere".to_string(), "Box".to_string()]
        .into_iter()
        .chain(s.models.iter().map(|a| a.name.clone()))
        .collect::<Vec<_>>();
    field(
        c,
        p,
        data,
        "/designerMetadata/previewTargetModel",
        "Target visual / model",
        Some(&targets),
    );
    for (key, caption) in [
        (
            "previewPolePosition",
            "Pole position (used when a pole target is set)",
        ),
        ("previewTargetPosition", "Target position"),
        ("previewTargetRotation", "Target rotation (degrees)"),
        ("previewTargetScale", "Target scale"),
        ("previewModelPosition", "Model position"),
        ("previewModelRotation", "Model rotation (degrees)"),
        ("previewModelScale", "Model scale"),
    ] {
        vector(
            c,
            p,
            data,
            &format!("/designerMetadata/{key}"),
            caption,
            (if key.contains("Scale") { 0.01 } else { -100. }, 100.),
        );
    }
}

#[derive(Component)]
pub(super) struct LayerSelector;
pub(super) fn layer_selector(c: &mut Commands, p: Entity, s: &State) {
    let layers = s.data["layers"].as_array().cloned().unwrap_or_default();
    let options = layers
        .iter()
        .enumerate()
        .map(|(i, l)| {
            (
                i.to_string(),
                format!(
                    "{} · {}",
                    l["id"].as_str().unwrap_or(""),
                    l["solverType"].as_str().unwrap_or("")
                ),
            )
        })
        .collect::<Vec<_>>();
    property::dropdown_labeled(c, p, Control("layer"), &s.layer.to_string(), &options);
}
