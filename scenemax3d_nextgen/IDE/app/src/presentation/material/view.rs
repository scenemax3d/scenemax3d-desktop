//! Retained material workbench: preset library, preview stage, surface inspector.
use super::*;
use scenemax_assets::material::{PRESETS, SCALARS, TEXTURES};
use scenemax_ide_ui::{button, label, number, property, theme::*};
use scenemax_ui_input::Button;
pub(super) struct Parts {
    pub viewport: Entity,
    pub status: Entity,
    pub slots: Entity,
}
fn column(commands: &mut Commands, parent: Entity, width: f32) -> Entity {
    commands
        .spawn((
            Node {
                width: px(width),
                min_width: px(width),
                height: percent(100.),
                flex_direction: FlexDirection::Column,
                padding: px(10.).all(),
                row_gap: px(5.),
                ..default()
            },
            BackgroundColor(PANEL),
            ChildOf(parent),
        ))
        .id()
}
fn title(commands: &mut Commands, parent: Entity, text: &str, size: f32) {
    commands.spawn((label(text, size), ChildOf(parent)));
}
fn input(commands: &mut Commands, parent: Entity, key: &str, value: &Value) {
    let text = display(value);
    property::input(commands, parent, Field(key.into(), text.clone()), &text);
}
pub(super) fn display(v: &Value) -> String {
    v.as_str()
        .map(str::to_owned)
        .unwrap_or_else(|| v.to_string())
}
fn color_row(commands: &mut Commands, parent: Entity, key: &str, value: &Value) {
    input(commands, parent, key, value);
    let row = commands
        .spawn((
            Node {
                flex_wrap: FlexWrap::Wrap,
                column_gap: px(3.),
                row_gap: px(3.),
                ..default()
            },
            ChildOf(parent),
        ))
        .id();
    for hex in [
        "#FFFFFF", "#B7C9E2", "#252830", "#A51E35", "#E8B454", "#3A9685", "#46DDEB", "#7858A6",
    ] {
        let rgba = scenemax_assets::material::color(hex).unwrap_or([1.; 4]);
        commands.spawn((
            Button,
            Action::Color(key.into(), hex.into()),
            Node {
                width: px(24.),
                height: px(20.),
                border: px(1.).all(),
                ..default()
            },
            BorderColor::all(EDGE),
            scenemax_ide_ui::ButtonSurface(Color::srgba(rgba[0], rgba[1], rgba[2], rgba[3])),
            BackgroundColor(Color::srgba(rgba[0], rgba[1], rgba[2], rgba[3])),
            ChildOf(row),
        ));
    }
}
pub(super) fn build(
    commands: &mut Commands,
    host: Entity,
    data: &Value,
    library: &scenemax_ide_services::material::Library,
    name: &str,
) -> Parts {
    commands.entity(host).insert(Node {
        width: percent(100.),
        height: percent(100.),
        min_height: px(0.),
        flex_direction: FlexDirection::Column,
        ..default()
    });
    let toolbar = commands
        .spawn((
            Node {
                height: px(44.),
                min_height: px(44.),
                align_items: AlignItems::Center,
                column_gap: px(8.),
                padding: UiRect::horizontal(px(12.)),
                ..default()
            },
            BackgroundColor(HEADER),
            ChildOf(host),
        ))
        .id();
    title(commands, toolbar, "MATERIAL STUDIO", 15.);
    button(commands, toolbar, "Save", Action::Save);
    button(commands, toolbar, "Refresh assets", Action::Refresh);
    button(commands, toolbar, "Undo", Action::Undo);
    button(commands, toolbar, "Redo", Action::Redo);
    commands.spawn((
        Node {
            flex_grow: 1.,
            ..default()
        },
        ChildOf(toolbar),
    ));
    title(commands, toolbar, name, 12.);
    let body = commands
        .spawn((
            Node {
                width: percent(100.),
                flex_grow: 1.,
                min_height: px(0.),
                ..default()
            },
            ChildOf(host),
        ))
        .id();
    let left = column(commands, body, 225.);
    title(commands, left, "SURFACE LIBRARY", 12.);
    title(commands, left, "Start with a surface, make it yours", 11.);
    let palette = commands
        .spawn((
            Node {
                flex_wrap: FlexWrap::Wrap,
                column_gap: px(4.),
                row_gap: px(4.),
                ..default()
            },
            ChildOf(left),
        ))
        .id();
    for preset in PRESETS {
        button(
            commands,
            palette,
            preset,
            Action::Preset(preset.to_string()),
        );
    }
    let textures = property::scroll_column(commands, left);
    title(commands, textures, "TEXTURE CHANNELS", 12.);
    title(
        commands,
        textures,
        "Project resources · correct color space",
        10.,
    );
    for (label, key, _) in TEXTURES {
        property::heading(commands, textures, label);
        let pointer = format!("/textures/{key}");
        let selected = data["textures"][key].as_str().unwrap_or("");
        let options = std::iter::once(String::new())
            .chain(library.textures.iter().cloned())
            .collect::<Vec<_>>();
        property::dropdown(
            commands,
            textures,
            Field(pointer, selected.into()),
            selected,
            &options,
        );
        commands.spawn((
            TextureThumb((*key).into()),
            ImageNode::default(),
            Node {
                width: px(64.),
                height: px(46.),
                margin: UiRect::vertical(px(3.)),
                ..default()
            },
            BackgroundColor(BG),
            ChildOf(textures),
        ));
    }
    let center = commands
        .spawn((
            Node {
                flex_grow: 1.,
                min_width: px(200.),
                height: percent(100.),
                flex_direction: FlexDirection::Column,
                ..default()
            },
            BackgroundColor(BG),
            ChildOf(body),
        ))
        .id();
    let controls = commands
        .spawn((
            Node {
                height: px(38.),
                min_height: px(38.),
                column_gap: px(4.),
                align_items: AlignItems::Center,
                padding: UiRect::horizontal(px(8.)),
                ..default()
            },
            ChildOf(center),
        ))
        .id();
    for (caption, shape) in [("Sphere", 0), ("Cube", 1), ("Plane", 2)] {
        button(commands, controls, caption, Action::Shape(shape));
    }
    button(commands, controls, "Frame", Action::Frame);
    button(commands, controls, "Studio / warm", Action::Light);
    button(commands, controls, "Rotate", Action::Rotate);
    let models = commands
        .spawn((
            Node {
                height: px(30.),
                min_height: px(30.),
                padding: UiRect::horizontal(px(8.)),
                ..default()
            },
            ChildOf(center),
        ))
        .id();
    let choices = std::iter::once(String::new())
        .chain(library.models.iter().cloned())
        .collect::<Vec<_>>();
    let choices: Vec<_> = choices
        .into_iter()
        .map(|p| {
            let label = if p.is_empty() {
                "Preview primitive".into()
            } else {
                p.clone()
            };
            (p, label)
        })
        .collect();
    property::dropdown_labeled(commands, models, PreviewModel(String::new()), "", &choices);
    let viewport = commands
        .spawn((
            Node {
                width: percent(100.),
                flex_grow: 1.,
                min_height: px(100.),
                overflow: Overflow::clip(),
                ..default()
            },
            BackgroundColor(BG),
            ChildOf(center),
        ))
        .id();
    title(
        commands,
        center,
        "Right drag to orbit · wheel to zoom · Frame to fit",
        11.,
    );
    let slots = commands
        .spawn((
            Node {
                min_height: px(30.),
                flex_wrap: FlexWrap::Wrap,
                column_gap: px(4.),
                padding: px(6.).all(),
                ..default()
            },
            ChildOf(center),
        ))
        .id();
    let right = column(commands, body, 280.);
    title(commands, right, "SURFACE INSPECTOR", 12.);
    let inspector = property::scroll_column(commands, right);
    property::heading(commands, inspector, "Identity");
    input(commands, inspector, "/name", &data["name"]);
    property::heading(commands, inspector, "Base color · #RRGGBB or #RRGGBBAA");
    color_row(commands, inspector, "/baseColor", &data["baseColor"]);
    let mut section = "";
    for field in SCALARS {
        if section != field.section {
            section = field.section;
            property::heading(commands, inspector, section);
        }
        title(commands, inspector, field.label, 11.);
        let value = display(&data[field.key]);
        number::input(
            commands,
            inspector,
            Field(format!("/{}", field.key), value.clone()),
            &value,
            field.range[0] as f64,
            field.range[1] as f64,
            0.01,
        );
        if field.key == "emissionStrength" {
            color_row(
                commands,
                inspector,
                "/emissionColor",
                &data["emissionColor"],
            );
        }
    }
    property::heading(commands, inspector, "Rendering");
    let alpha = data["alphaMode"].as_str().unwrap_or("Opaque");
    property::dropdown(
        commands,
        inspector,
        Field("/alphaMode".into(), alpha.into()),
        alpha,
        &["Opaque".into(), "Mask".into(), "Blend".into(), "Add".into()],
    );
    for (key, label) in [
        ("doubleSided", "Double sided"),
        ("unlit", "Unlit"),
        ("flipNormalY", "Flip normal Y (DirectX)"),
    ] {
        let checked = data[key].as_bool().unwrap_or(false);
        property::checkbox(
            commands,
            inspector,
            Field(format!("/{key}"), checked.to_string()),
            label,
            checked,
        );
    }
    property::heading(commands, inspector, "Model assignment");
    title(commands, inspector, "glTF material slot (blank = all)", 11.);
    input(commands, inspector, "/targetSlot", &data["targetSlot"]);
    title(
        commands,
        inspector,
        &format!("SceneMax: object.material = \"{name}\""),
        11.,
    );
    title(
        commands,
        inspector,
        "Save the material before running your game.",
        11.,
    );
    let assignment = format!(
        "object.material = {}",
        serde_json::to_string(name).unwrap_or_default()
    );
    let copy = button(
        commands,
        inspector,
        "Copy script assignment",
        Name::new("Copy material assignment"),
    );
    commands.entity(copy).observe(
        move |_: On<PointerClick>,
              clipboard: Option<ResMut<bevy::clipboard::Clipboard>>,
              mut session: ResMut<Session>| {
            if let Some(mut clipboard) = clipboard {
                session.status = match clipboard.set_text(assignment.clone()) {
                    Ok(()) => {
                        "Material assignment copied; replace object with your entity name".into()
                    }
                    Err(e) => e.to_string(),
                };
            }
        },
    );
    let status = commands
        .spawn((
            label("Live PBR preview · Ready", 12.),
            Node {
                min_height: px(28.),
                padding: UiRect::axes(px(12.), px(6.)),
                ..default()
            },
            BackgroundColor(HEADER),
            ChildOf(host),
        ))
        .id();
    Parts {
        viewport,
        status,
        slots,
    }
}
