//! Scene toolbar actions use document transactions, never runtime commands.
use super::*;
use crate::application::ViewChange;
use scenemax_ide_core::scene3d::structure;
use serde_json::{Value, json};
#[derive(Component, Clone)]
pub(crate) enum Action {
    Add(&'static str),
    Model(String),
    Delete,
    Copy,
    Paste,
    Orbit,
    Pan,
    Lights,
    Ambient,
}
#[derive(Resource, Default)]
pub(crate) struct Tools {
    clipboard: Option<Value>,
    pub(super) lights_off: bool,
}
#[derive(Component)]
pub(crate) struct Picker;
pub(super) fn icon(key: &str) -> Vec<Vec<Vec2>> {
    let paths: Vec<Vec<(f32, f32)>> = match key {
        "move" => vec![
            vec![(10., 2.), (10., 18.)],
            vec![(2., 10.), (18., 10.)],
            vec![(7., 5.), (10., 2.), (13., 5.)],
            vec![(7., 15.), (10., 18.), (13., 15.)],
            vec![(5., 7.), (2., 10.), (5., 13.)],
            vec![(15., 7.), (18., 10.), (15., 13.)],
        ],
        "scale" => vec![
            vec![(3., 17.), (17., 3.)],
            vec![(10., 3.), (17., 3.), (17., 10.)],
            vec![(3., 10.), (3., 17.), (10., 17.)],
        ],
        "rotate" | "reset" => vec![
            (0..=28)
                .map(|i| {
                    let a = (-35. + i as f32 * 300. / 28.).to_radians();
                    (10. + a.cos() * 7., 10. - a.sin() * 7.)
                })
                .collect(),
            vec![(13., 3.), (17., 6.), (18., 1.)],
        ],
        "copy" => vec![
            vec![(7., 6.), (17., 6.), (17., 18.), (7., 18.), (7., 6.)],
            vec![(4., 14.), (2., 14.), (2., 2.), (12., 2.), (12., 4.)],
        ],
        "MODEL" => vec![vec![
            (10., 1.),
            (18., 6.),
            (15., 16.),
            (10., 19.),
            (5., 16.),
            (2., 6.),
            (10., 1.),
            (15., 16.),
            (18., 6.),
            (2., 6.),
            (5., 16.),
            (10., 1.),
        ]],
        "BOX" => vec![
            vec![
                (3., 6.),
                (10., 2.),
                (17., 6.),
                (17., 14.),
                (10., 18.),
                (3., 14.),
                (3., 6.),
                (10., 10.),
                (17., 6.),
            ],
            vec![(10., 10.), (10., 18.)],
        ],
        "WEDGE" => vec![
            vec![(2., 16.), (15., 3.), (18., 16.), (2., 16.)],
            vec![(15., 3.), (12., 16.)],
        ],
        "CONE" => vec![vec![
            (2., 16.),
            (10., 2.),
            (18., 16.),
            (10., 18.),
            (2., 16.),
        ]],
        "QUAD" => vec![vec![(3., 3.), (15., 5.), (17., 17.), (5., 15.), (3., 3.)]],
        "STAIRS" => vec![vec![
            (2., 18.),
            (2., 13.),
            (7., 13.),
            (7., 8.),
            (12., 8.),
            (12., 3.),
            (17., 3.),
            (17., 18.),
            (2., 18.),
        ]],
        "ARCH" => vec![vec![
            (3., 18.),
            (3., 8.),
            (5., 3.),
            (10., 1.),
            (15., 3.),
            (17., 8.),
            (17., 18.),
            (13., 18.),
            (13., 8.),
            (10., 5.),
            (7., 8.),
            (7., 18.),
            (3., 18.),
        ]],
        "CYLINDER" | "HOLLOW_CYLINDER" => vec![vec![
            (3., 5.),
            (6., 2.),
            (14., 2.),
            (17., 5.),
            (14., 8.),
            (6., 8.),
            (3., 5.),
            (3., 15.),
            (6., 18.),
            (14., 18.),
            (17., 15.),
            (17., 5.),
        ]],
        "delete" => vec![
            vec![(3., 5.), (17., 5.)],
            vec![(5., 5.), (6., 18.), (14., 18.), (15., 5.)],
            vec![(8., 5.), (8., 2.), (12., 2.), (12., 5.)],
        ],
        "paste" => vec![
            vec![
                (7., 4.),
                (3., 4.),
                (3., 18.),
                (17., 18.),
                (17., 4.),
                (13., 4.),
            ],
            vec![(7., 2.), (13., 2.), (13., 6.), (7., 6.), (7., 2.)],
        ],
        "PATH" => vec![vec![
            (2., 17.),
            (5., 16.),
            (8., 12.),
            (10., 6.),
            (13., 3.),
            (18., 2.),
        ]],
        "pan" => vec![vec![
            (3., 10.),
            (7., 15.),
            (7., 4.),
            (9., 4.),
            (9., 11.),
            (10., 2.),
            (12., 2.),
            (12., 11.),
            (14., 4.),
            (16., 4.),
            (15., 14.),
            (12., 18.),
            (7., 18.),
            (3., 10.),
        ]],
        "LIGHT" | "lights" => vec![
            vec![
                (7., 15.),
                (5., 10.),
                (5., 6.),
                (8., 3.),
                (12., 3.),
                (15., 6.),
                (15., 10.),
                (13., 15.),
                (7., 15.),
                (7., 18.),
                (13., 18.),
            ],
            vec![(1., 6.), (3., 7.)],
            vec![(17., 7.), (19., 6.)],
            vec![(10., 0.), (10., 2.)],
        ],
        _ => {
            let sy = if key == "CINEMATIC_RIG" || key == "orbit" {
                4.
            } else {
                8.
            };
            vec![
                (0..=24)
                    .map(|i| {
                        let a = i as f32 * std::f32::consts::TAU / 24.;
                        (10. + a.cos() * 8., 10. + a.sin() * sy)
                    })
                    .collect(),
            ]
        }
    };
    paths
        .into_iter()
        .map(|p| p.into_iter().map(|(x, y)| Vec2::new(x, y)).collect())
        .collect()
}
pub(super) fn control<T: Component>(
    commands: &mut Commands,
    parent: Entity,
    key: &str,
    title: &str,
    action: T,
) -> Entity {
    if let Some(icon) = super::super::java_icons::toolbar(key) {
        scenemax_ide_ui::icons::image_button(
            commands,
            parent,
            title,
            super::super::java_icons::JavaIcon(icon),
            action,
        )
    } else {
        scenemax_ide_ui::icons::button(commands, parent, title, &icon(key), action)
    }
}
pub(super) fn toolbar(commands: &mut Commands, parent: Entity) {
    let bar = commands
        .spawn((
            Node {
                width: percent(100.),
                min_height: px(26.),
                flex_shrink: 0.,
                flex_wrap: FlexWrap::Wrap,
                align_items: AlignItems::Center,
                column_gap: px(1.),
                ..default()
            },
            ChildOf(parent),
        ))
        .id();
    commands.spawn((label("Add:", 11.), ChildOf(bar)));
    for kind in structure::ADD_TYPES {
        control(
            commands,
            bar,
            kind,
            &format!("Add {}", kind.to_lowercase().replace('_', " ")),
            Action::Add(kind),
        );
    }
    for (key, title, action) in [
        ("delete", "Delete selected", Action::Delete),
        ("copy", "Copy selected", Action::Copy),
        ("paste", "Paste object", Action::Paste),
    ] {
        control(commands, bar, key, title, action);
    }
    super::gizmo::toolbar(commands, bar);
    commands.spawn((label("Camera:", 11.), ChildOf(bar)));
    for (key, title, action) in [
        ("orbit", "Orbit camera", Action::Orbit),
        ("pan", "Pan camera", Action::Pan),
        ("lights", "Toggle design lighting", Action::Lights),
        ("ambient", "Ambient light", Action::Ambient),
    ] {
        control(commands, bar, key, title, action);
    }
}
#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct Controls<'w, 's> {
    drawing: ResMut<'w, super::path::Drawing>,
    nav: ResMut<'w, super::navigation::Navigation>,
    cameras: Query<'w, 's, &'static Orbit>,
    pickers: Query<'w, 's, Entity, With<Picker>>,
    lights: Query<'w, 's, &'static mut Visibility, With<super::render::DesignLight>>,
}
pub(crate) fn update(
    mut commands: Commands,
    actions: Query<(Entity, &Interaction, &Action), Changed<Interaction>>,
    mut tools: ResMut<Tools>,
    mut session: ResMut<Session>,
    mut state: ResMut<SceneState>,
    mut changes: MessageWriter<ViewChange>,
    controls: Controls,
) {
    let Controls {
        mut drawing,
        mut nav,
        cameras,
        pickers,
        mut lights,
    } = controls;
    for (control, interaction, action) in &actions {
        if *interaction != Interaction::Pressed {
            continue;
        }
        match action {
            Action::Orbit => {
                nav.pan = false;
                continue;
            }
            Action::Pan => {
                nav.pan = true;
                continue;
            }
            Action::Lights => {
                tools.lights_off = !tools.lights_off;
                for mut v in &mut lights {
                    *v = if tools.lights_off {
                        Visibility::Hidden
                    } else {
                        Visibility::Inherited
                    };
                }
                continue;
            }
            _ => {}
        }
        let Some((id, revision)) = state.current else {
            continue;
        };
        let Some(scene) = state.scene.as_ref() else {
            continue;
        };
        let selected = scene.entities.get(state.selected);
        if matches!(action, Action::Add("PATH")) {
            drawing.begin((id, revision));
            session.status="Draw path: click to place points on the work plane; double-click to finish; Esc to cancel".into();
            continue;
        }
        if matches!(action, Action::Ambient) {
            for picker in &pickers {
                commands.entity(picker).despawn();
            }
            super::ambient::spawn(&mut commands, control, id, revision, &scene.ambient);
            continue;
        }
        if matches!(action, Action::Add("MODEL")) {
            for picker in &pickers {
                commands.entity(picker).despawn();
            }
            let popup = commands
                .spawn((
                    Picker,
                    Node {
                        position_type: PositionType::Absolute,
                        top: px(32.),
                        left: px(0.),
                        width: px(240.),
                        max_height: px(360.),
                        overflow: Overflow::scroll_y(),
                        flex_direction: FlexDirection::Column,
                        ..default()
                    },
                    BackgroundColor(PANEL),
                    GlobalZIndex(90),
                    ChildOf(control),
                ))
                .id();
            commands.entity(popup).observe(
                |mut event: On<Pointer<Scroll>>,
                 mut q: Query<(&mut ScrollPosition, &ComputedNode), With<Picker>>| {
                    event.propagate(false);
                    if let Ok((mut scroll, node)) = q.get_mut(event.entity) {
                        let max = (node.content_size().y - node.size().y).max(0.)
                            * node.inverse_scale_factor();
                        scroll.y = (scroll.y - event.y * 30.).clamp(0., max);
                    }
                },
            );
            let cancel = button(
                &mut commands,
                popup,
                "Cancel",
                Name::new("Cancel model selection"),
            );
            commands.entity(cancel).observe(
                move |_: On<Pointer<Click>>, mut commands: Commands| {
                    commands.entity(popup).try_despawn();
                },
            );
            for name in scene
                .catalog
                .get("resourcePath")
                .into_iter()
                .flatten()
                .filter(|n| !n.is_empty())
            {
                button(&mut commands, popup, name, Action::Model(name.clone()));
            }
            if scene
                .catalog
                .get("resourcePath")
                .is_none_or(|names| names.iter().all(String::is_empty))
            {
                commands.spawn((label("No project models available", 12.), ChildOf(popup)));
            }
            continue;
        }
        let result = (|| -> Result<Option<(String, Option<String>)>, String> {
            let doc = session.workspace.document(id).map_err(|e| e.to_string())?;
            if doc.revision() != revision {
                return Err("Scene is reloading; retry the action".into());
            }
            if selected.is_some_and(|e| e.kind == "CAMERA")
                && matches!(action, Action::Copy | Action::Delete)
            {
                return Err("The game camera is a permanent scene control".into());
            }
            if matches!(action, Action::Copy) {
                tools.clipboard = selected.map(|e| e.properties.clone());
                return Ok(None);
            }
            if matches!(action, Action::Delete) {
                return Ok(Some((
                    structure::remove(doc.text(), &selected.ok_or("Select an object")?.pointer)?,
                    None,
                )));
            }
            let mut entry = match action {
                Action::Add("CINEMATIC_RIG")
                    if selected.is_some_and(|e| e.kind == "CINEMATIC_RIG") =>
                {
                    structure::template("CINEMATIC_TRACK")?
                }
                Action::Add(kind) => structure::template(kind)?,
                Action::Model(name) => {
                    let mut e = structure::template("MODEL")?;
                    e["resourcePath"] = json!(name);
                    e
                }
                Action::Paste => tools.clipboard.clone().ok_or("Copy a scene object first")?,
                _ => return Ok(None),
            };
            if !matches!(action, Action::Paste) {
                let target = state
                    .camera
                    .and_then(|c| cameras.get(c).ok())
                    .map(|o| o.target)
                    .unwrap_or(Vec3::ZERO);
                entry["position"] = json!(target.to_array());
            }
            let parent = selected
                .filter(|e| {
                    e.kind == "SECTION"
                        || (entry["type"] == "CINEMATIC_TRACK" && e.kind == "CINEMATIC_RIG")
                })
                .map(|e| e.pointer.as_str());
            if entry["type"] == "CINEMATIC_TRACK" {
                entry["position"] = json!([0, 0, 0]);
            }
            let (source, pointer) = structure::insert(doc.text(), parent, entry)?;
            Ok(Some((source, Some(pointer))))
        })();
        match result {
            Ok(Some((source, pointer))) => {
                if let Ok(doc) = session.workspace.document_mut(id) {
                    doc.replace_text(source);
                    changes.write(ViewChange::BufferChanged(id));
                    state.pending_selection = pointer;
                    session.status = "Scene updated — Ctrl+S to save; Ctrl+Z to undo".into();
                }
                for p in &pickers {
                    commands.entity(p).despawn();
                }
            }
            Ok(None) => session.status = "Object copied".into(),
            Err(e) => session.status = e,
        }
    }
}

pub(crate) fn lighting(
    tools: Res<Tools>,
    mut lights: Query<&mut Visibility, With<super::render::DesignLight>>,
) {
    let visibility = if tools.lights_off {
        Visibility::Hidden
    } else {
        Visibility::Inherited
    };
    for mut light in &mut lights {
        if *light != visibility {
            *light = visibility;
        }
    }
}
