//! Document-backed hierarchy menus and drag/drop; no runtime mutations.
use super::*;
use bevy::{
    input_focus::{FocusCause, InputFocus},
    text::EditableText,
};
use scenemax_ide_core::scene3d::structure::{self, Placement};
use serde_json::{Value, json};

#[derive(Clone)]
struct Target {
    stamp: (DocumentId, DocumentRevision),
    pointer: Option<String>,
    position: Vec2,
}
#[derive(Clone)]
enum Action {
    Folder,
    Code,
    Add(&'static str),
    Model(String),
    Adds,
    Models,
    Rename,
    ConfirmRename,
    Copy,
    Paste,
    Duplicate,
    Delete,
    Move(Placement),
    Up,
    Down,
    Out,
    Expand,
    Collapse,
    Close,
}
#[derive(Resource, Default)]
pub(crate) struct State {
    target: Option<Target>,
    open: bool,
    pending: Option<Action>,
    drag: Option<Target>,
    drop: Option<Placement>,
    clipboard: Option<Value>,
    model_position: Option<Vec3>,
}
impl State {
    pub(super) fn pick_toolbar_model(
        &mut self,
        scene: &SceneState,
        position: Vec3,
        inventory: &mut crate::presentation::inventory::State,
    ) {
        let Some(data) = scene.scene.as_ref() else {
            return;
        };
        let selected = data
            .entities
            .get(scene.selected)
            .filter(|e| e.kind == "SECTION")
            .map(|_| scene.selected);
        self.target = snapshot(scene, selected, Vec2::ZERO);
        self.model_position = Some(position);
        self.open = false;
        self.pending = None;
        inventory.pick_model(
            data.catalog
                .get("resourcePath")
                .into_iter()
                .flatten()
                .filter(|n| !n.is_empty())
                .cloned()
                .collect(),
        );
    }
}
#[derive(Component)]
struct Menu;
#[derive(Component)]
struct RenameInput;
#[derive(Component)]
pub(crate) struct Tree;
#[derive(Component)]
pub(crate) struct Row(pub usize);

fn snapshot(state: &SceneState, index: Option<usize>, position: Vec2) -> Option<Target> {
    Some(Target {
        stamp: state.current?,
        pointer: index.and_then(|i| {
            state
                .scene
                .as_ref()?
                .entities
                .get(i)
                .map(|e| e.pointer.clone())
        }),
        position,
    })
}
pub(super) fn tree(commands: &mut Commands, tree: Entity) {
    commands.entity(tree).insert(Tree).observe(
        move |mut e: On<PointerClick>, scene: Res<SceneState>, mut state: ResMut<State>| {
            if e.button == PointerButton::Secondary {
                e.propagate(false);
                state.target = snapshot(&scene, None, e.pointer.position);
                state.open = true;
            }
        },
    );
}
pub(super) fn row(commands: &mut Commands, row: Entity, index: usize) {
    commands
        .entity(row)
        .insert((Row(index), BorderColor::all(SELECTED)));
    // Native buttons consume clicks/drag-end before they reach their parent row.
    // Bind the actual selection/disclosure buttons as well as blank row space.
    commands.queue(move |world: &mut World| {
        let controls: Vec<Entity> = world
            .get::<Children>(row)
            .into_iter()
            .flatten()
            .copied()
            .filter(|child| world.get::<Choose>(*child).is_some())
            .collect();
        let mut commands = world.commands();
        for control in controls {
            observe_row(&mut commands, control, index);
        }
    });
    observe_row(commands, row, index);
}
fn observe_row(commands: &mut Commands, row: Entity, index: usize) {
    commands
        .entity(row)
        .observe(
            move |mut e: On<PointerClick>,
                  mut commands: Commands,
                  mut scene: ResMut<SceneState>,
                  mut state: ResMut<State>| {
                if e.button == PointerButton::Secondary {
                    e.propagate(false);
                    scene.selected = index;
                    if let (Some(parts), Some(data)) = (scene.parts, scene.scene.as_ref()) {
                        view::inspect(&mut commands, parts, data, index);
                    }
                    state.target = snapshot(&scene, Some(index), e.pointer.position);
                    state.open = true;
                }
            },
        )
        .observe(
            move |mut e: On<PointerDragStart>, scene: Res<SceneState>, mut state: ResMut<State>| {
                if e.button == PointerButton::Primary
                    && scene
                        .scene
                        .as_ref()
                        .and_then(|s| s.entities.get(index))
                        .is_some_and(|e| e.kind != "CAMERA")
                {
                    e.propagate(false);
                    state.drag = snapshot(&scene, Some(index), e.pointer.position);
                    state.drop = None;
                }
            },
        )
        .observe(
            |mut e: On<PointerDrag>,
             scene: Res<SceneState>,
             mut state: ResMut<State>,
             nodes: Query<
                (&ComputedNode, &UiGlobalTransform, Option<&Row>),
                Or<(With<Tree>, With<Row>)>,
            >,
             mut session: ResMut<Session>| {
                if e.button != PointerButton::Primary || state.drag.is_none() {
                    return;
                }
                e.propagate(false);
                state.drop = destination(e.pointer.position, &scene, &nodes);
                session.status = match &state.drop {
                    Some(Placement::Inside(_)) => "Drop to move into folder or rig",
                    Some(Placement::Before(_)) => "Drop to place before this node",
                    Some(Placement::After(_)) => "Drop to place after this node",
                    Some(Placement::Root) => "Drop to move to the end of the scene root",
                    None => "Drag onto a hierarchy row or the empty tree area",
                }
                .into();
            },
        )
        .observe(
            |mut e: On<PointerDragEnd>,
             scene: Res<SceneState>,
             mut state: ResMut<State>,
             nodes: Query<
                (&ComputedNode, &UiGlobalTransform, Option<&Row>),
                Or<(With<Tree>, With<Row>)>,
            >| {
                if e.button != PointerButton::Primary {
                    return;
                }
                if let Some(target) = state.drag.take() {
                    e.propagate(false);
                    if let Some(place) = destination(e.pointer.position, &scene, &nodes) {
                        state.target = Some(target);
                        state.pending = Some(Action::Move(place));
                    }
                    state.drop = None;
                }
            },
        );
}
fn destination(
    position: Vec2,
    scene: &SceneState,
    nodes: &Query<(&ComputedNode, &UiGlobalTransform, Option<&Row>), Or<(With<Tree>, With<Row>)>>,
) -> Option<Placement> {
    let scene = scene.scene.as_ref()?;
    let in_tree = nodes.iter().any(|(node, transform, row)| {
        row.is_none()
            && transform.try_inverse().is_some_and(|inverse| {
                node.size().min_element() > 0.
                    && !inverse
                        .transform_point2(position / node.inverse_scale_factor())
                        .abs()
                        .cmpgt(node.size() * 0.5)
                        .any()
            })
    });
    if !in_tree {
        return None;
    }
    for (node, transform, row) in nodes {
        let Some(inverse) = transform.try_inverse() else {
            continue;
        };
        let point = inverse.transform_point2(position / node.inverse_scale_factor());
        let size = node.size();
        if size.min_element() <= 0. || point.abs().cmpgt(size * 0.5).any() {
            continue;
        }
        let Some(row) = row else {
            continue;
        };
        let entry = scene.entities.get(row.0)?;
        if entry.kind == "CAMERA" {
            return None;
        }
        let fraction = point.y / size.y + 0.5;
        return Some(
            if matches!(entry.kind.as_str(), "SECTION" | "CINEMATIC_RIG")
                && (0.25..0.75).contains(&fraction)
            {
                Placement::Inside(entry.pointer.clone())
            } else if fraction < 0.5 {
                Placement::Before(entry.pointer.clone())
            } else {
                Placement::After(entry.pointer.clone())
            },
        );
    }
    Some(Placement::Root)
}
fn item(commands: &mut Commands, parent: Entity, caption: &str, action: Action) {
    let row = button(commands, parent, caption, Name::new(caption.to_owned()));
    commands
        .entity(row)
        .observe(move |mut e: On<PointerClick>, mut state: ResMut<State>| {
            if e.button == PointerButton::Primary {
                e.propagate(false);
                state.pending = Some(action.clone());
            }
        });
}
fn menu(commands: &mut Commands, target: &Target, size: Vec2) -> Entity {
    let overlay = commands
        .spawn((
            Menu,
            GlobalZIndex(350),
            Node {
                position_type: PositionType::Absolute,
                width: percent(100.),
                height: percent(100.),
                ..default()
            },
        ))
        .id();
    commands
        .entity(overlay)
        .observe(|mut e: On<PointerClick>, mut state: ResMut<State>| {
            e.propagate(false);
            state.pending = Some(Action::Close);
        });
    let panel = commands
        .spawn((
            Node {
                position_type: PositionType::Absolute,
                left: px(target.position.x.clamp(0., (size.x - 250.).max(0.))),
                top: px(target.position.y.clamp(0., (size.y - 520.).max(0.))),
                width: px(245.),
                max_height: px((size.y - 20.).max(100.)),
                overflow: Overflow::scroll_y(),
                flex_direction: FlexDirection::Column,
                padding: px(5.).all(),
                ..default()
            },
            BackgroundColor(PANEL),
            ChildOf(overlay),
        ))
        .id();
    commands.entity(panel).observe(|mut e: On<PointerClick>| {
        e.propagate(false);
    });
    commands.entity(panel).observe(
        |mut e: On<PointerScroll>, mut panels: Query<&mut ScrollPosition>| {
            e.propagate(false);
            if let Ok(mut scroll) = panels.get_mut(e.entity) {
                scroll.y = (scroll.y - e.y * 25.).max(0.);
            }
        },
    );
    commands.entity(panel).insert(ScrollPosition::default());
    panel
}
#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct Ui<'w, 's> {
    menus: Query<'w, 's, Entity, With<Menu>>,
    keys: Res<'w, ButtonInput<KeyCode>>,
    windows: Query<'w, 's, &'static Window, With<bevy::window::PrimaryWindow>>,
    inputs: Query<'w, 's, &'static EditableText, With<RenameInput>>,
    focus: Option<ResMut<'w, InputFocus>>,
}
pub(crate) fn update(
    mut commands: Commands,
    mut state: ResMut<State>,
    mut scene: ResMut<SceneState>,
    mut session: ResMut<Session>,
    mut changes: MessageWriter<crate::application::ViewChange>,
    mut ui: Ui,
    mut inventory: ResMut<crate::presentation::inventory::State>,
) {
    if state.target.is_some()
        && let Some(name) = inventory.picked_model.take()
    {
        state.pending = Some(Action::Model(name));
    }
    if ui.keys.just_pressed(KeyCode::Escape) {
        state.pending = Some(Action::Close);
        state.drag = None;
        state.drop = None;
    }
    if !ui.inputs.is_empty() && ui.keys.just_pressed(KeyCode::Enter) {
        state.pending = Some(Action::ConfirmRename);
    }
    if state
        .target
        .as_ref()
        .is_some_and(|t| scene.current != Some(t.stamp))
    {
        state.target = None;
        state.open = false;
        state.pending = Some(Action::Close);
    }
    let action = state.pending.take();
    if state.open || action.is_some() {
        for entity in &ui.menus {
            commands.entity(entity).try_despawn();
        }
    }
    if matches!(action, Some(Action::Close)) {
        state.open = false;
        state.target = None;
        return;
    }
    let Some(target) = state.target.clone() else {
        return;
    };
    let Some(data) = scene.scene.as_ref() else {
        return;
    };
    let selected = target
        .pointer
        .as_ref()
        .and_then(|p| data.entities.iter().find(|e| &e.pointer == p));
    let size = ui
        .windows
        .iter()
        .next()
        .map(|w| Vec2::new(w.width(), w.height()))
        .unwrap_or(Vec2::new(1280., 800.));
    if state.open || matches!(action, Some(Action::Adds | Action::Models | Action::Rename)) {
        state.open = false;
        let panel = menu(&mut commands, &target, size);
        match action {
            Some(Action::Adds) => {
                for kind in structure::ADD_TYPES.iter().filter(|k| **k != "PATH") {
                    item(
                        &mut commands,
                        panel,
                        &format!("Add {}", kind.to_lowercase().replace('_', " ")),
                        if *kind == "MODEL" {
                            Action::Models
                        } else {
                            Action::Add(kind)
                        },
                    );
                }
            }
            Some(Action::Models) => {
                state.model_position = None;
                commands.entity(panel).try_despawn();
                inventory.pick_model(
                    data.catalog
                        .get("resourcePath")
                        .into_iter()
                        .flatten()
                        .filter(|n| !n.is_empty())
                        .cloned()
                        .collect(),
                );
                return;
            }
            Some(Action::Rename) => {
                let input = commands
                    .spawn((
                        RenameInput,
                        bevy::ui_widgets::TextInput,
                        EditableText::new(selected.map_or("", |e| &e.name)),
                        Node {
                            width: percent(100.),
                            padding: px(6.).all(),
                            ..default()
                        },
                        TextFont {
                            font_size: FontSize::Px(14.),
                            ..default()
                        },
                        TextColor(INK),
                        scenemax_ide_ui::theme::TEXT_CURSOR_STYLE,
                        BackgroundColor(BG),
                        ChildOf(panel),
                    ))
                    .id();
                if let Some(focus) = ui.focus.as_mut() {
                    focus.set(input, FocusCause::Navigated);
                }
                item(&mut commands, panel, "Rename", Action::ConfirmRename);
            }
            _ => {
                item(&mut commands, panel, "Create folder", Action::Folder);
                item(&mut commands, panel, "Add code node", Action::Code);
                item(&mut commands, panel, "Add object…", Action::Adds);
                if selected.is_some_and(|e| e.kind == "CINEMATIC_RIG") {
                    item(
                        &mut commands,
                        panel,
                        "Add cinematic track",
                        Action::Add("CINEMATIC_TRACK"),
                    );
                }
                if state.clipboard.is_some() {
                    item(&mut commands, panel, "Paste", Action::Paste);
                }
                if selected.is_some_and(|e| e.kind != "CAMERA") {
                    for (caption, action) in [
                        ("Rename…", Action::Rename),
                        ("Copy", Action::Copy),
                        ("Duplicate", Action::Duplicate),
                        ("Move up", Action::Up),
                        ("Move down", Action::Down),
                        ("Move out of folder", Action::Out),
                        ("Move to scene root", Action::Move(Placement::Root)),
                        ("Delete object / folder", Action::Delete),
                    ] {
                        item(&mut commands, panel, caption, action);
                    }
                }
                item(&mut commands, panel, "Expand all", Action::Expand);
                item(&mut commands, panel, "Collapse all", Action::Collapse);
            }
        }
        item(&mut commands, panel, "Cancel", Action::Close);
        return;
    }
    let Some(action) = action else {
        return;
    };
    if matches!(action, Action::Expand | Action::Collapse) {
        scene.collapsed = if matches!(action, Action::Collapse) {
            data.entities
                .iter()
                .enumerate()
                .filter_map(|(i, e)| {
                    matches!(e.kind.as_str(), "SECTION" | "CINEMATIC_RIG").then_some(i)
                })
                .collect()
        } else {
            default()
        };
        return;
    }
    if matches!(action, Action::Copy) {
        state.clipboard = selected.map(|e| e.properties.clone());
        return;
    }
    let result = (|| -> Result<(String, Option<String>), String> {
        let doc = session
            .workspace
            .document(target.stamp.0)
            .map_err(|e| e.to_string())?;
        if doc.revision() != target.stamp.1 {
            return Err("Scene changed; reopen the menu".into());
        }
        let pointer = target.pointer.as_deref();
        let parent = selected.and_then(|e| {
            if matches!(action, Action::Duplicate) {
                e.parent
                    .and_then(|i| data.entities.get(i))
                    .map(|p| p.pointer.as_str())
            } else if e.kind == "SECTION"
                || (e.kind == "CINEMATIC_RIG" && matches!(action, Action::Add("CINEMATIC_TRACK")))
            {
                Some(e.pointer.as_str())
            } else {
                e.parent
                    .and_then(|i| data.entities.get(i))
                    .filter(|p| p.kind == "SECTION")
                    .map(|p| p.pointer.as_str())
            }
        });
        match action {
            Action::Delete => Ok((
                structure::remove(doc.text(), pointer.ok_or("Select an object")?)?,
                None,
            )),
            Action::ConfirmRename => {
                let name = ui
                    .inputs
                    .iter()
                    .next()
                    .map(|t| t.value().to_string())
                    .unwrap_or_default();
                if name.trim().is_empty() {
                    return Err("Enter a name".into());
                }
                Ok((
                    scenemax_ide_core::scene3d::patch(
                        doc.text(),
                        pointer.ok_or("Select an object")?,
                        &[("name".into(), json!(name.trim()))],
                    )?,
                    pointer.map(str::to_owned),
                ))
            }
            Action::Move(place) => {
                structure::relocate(doc.text(), pointer.ok_or("Select an object")?, &place)
                    .map(|(s, p)| (s, Some(p)))
            }
            Action::Up | Action::Down | Action::Out => {
                let p = pointer.ok_or("Select an object")?;
                let (list, index) = p.rsplit_once('/').ok_or("Invalid object")?;
                let index: usize = index.parse().map_err(|_| "Invalid object index")?;
                let place = match action {
                    Action::Up if index > 0 => Placement::Before(format!("{list}/{}", index - 1)),
                    Action::Down => Placement::After(format!("{list}/{}", index + 1)),
                    Action::Out => Placement::After(
                        list.strip_suffix("/children")
                            .ok_or("Object is already at scene root")?
                            .into(),
                    ),
                    _ => return Err("Object is already first".into()),
                };
                structure::relocate(doc.text(), p, &place).map(|(s, p)| (s, Some(p)))
            }
            _ => {
                let entry = match action {
                    Action::Folder => {
                        let mut e = structure::template("SECTION")?;
                        e["name"] = json!("folder");
                        e
                    }
                    Action::Code => structure::template("CODE")?,
                    Action::Add(kind) => structure::template(kind)?,
                    Action::Model(name) => {
                        let mut e = structure::template("MODEL")?;
                        e["resourcePath"] = json!(name);
                        if let Some(position) = state.model_position {
                            e["position"] = json!(position.to_array());
                        }
                        e
                    }
                    Action::Paste => state.clipboard.clone().ok_or("Copy an object first")?,
                    Action::Duplicate => selected.ok_or("Select an object")?.properties.clone(),
                    _ => return Err("Unsupported action".into()),
                };
                structure::insert(doc.text(), parent, entry).map(|(s, p)| (s, Some(p)))
            }
        }
    })();
    match result {
        Ok((source, pointer)) => {
            if let Ok(doc) = session.workspace.document_mut(target.stamp.0) {
                if source != doc.text() {
                    doc.replace_text(source);
                    changes.write(crate::application::ViewChange::BufferChanged(
                        target.stamp.0,
                    ));
                    scene.pending_selection = pointer;
                    scene.collapsed.clear();
                    session.status = "Hierarchy updated — Ctrl+S to save; Ctrl+Z to undo".into();
                }
            }
        }
        Err(error) => session.status = error,
    }
    state.target = None;
}
pub(crate) fn feedback(
    state: Res<State>,
    scene: Res<SceneState>,
    mut rows: Query<(&Row, &mut Node)>,
) {
    for (row, mut node) in &mut rows {
        let p = scene
            .scene
            .as_ref()
            .and_then(|s| s.entities.get(row.0))
            .map(|e| &e.pointer);
        let border = match (&state.drop, p) {
            (Some(Placement::Before(t)), Some(p)) if p == t => UiRect::top(px(2.)),
            (Some(Placement::After(t)), Some(p)) if p == t => UiRect::bottom(px(2.)),
            (Some(Placement::Inside(t)), Some(p)) if p == t => px(2.).all(),
            _ => UiRect::default(),
        };
        if node.border != border {
            node.border = border;
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use scenemax_ide_core::{Document, Project};
    #[test]
    fn native_row_buttons_open_context_menu_and_start_drag() {
        use bevy::picking::{
            backend::HitData,
            pointer::{Location, PointerId},
        };
        let dir = tempfile::tempdir().unwrap();
        let mut session = Session::new(Project::new(dir.path().into(), vec![]));
        let source = r#"{"entities":[{"type":"BOX","name":"subject"}]}"#;
        let id = session
            .workspace
            .open_document(
                Document::from_bytes(dir.path().join("scene.smdesign"), source.as_bytes().into())
                    .unwrap(),
            )
            .unwrap();
        let revision = session.workspace.document(id).unwrap().revision();
        let mut app = App::new();
        app.add_plugins(bevy::ui_widgets::ButtonPlugin)
            .init_resource::<State>()
            .insert_resource(SceneState {
                current: Some((id, revision)),
                scene: Some(scenemax_ide_services::scene3d::load(dir.path(), source).unwrap()),
                ..default()
            });
        let parent = app.world_mut().spawn(Node::default()).id();
        let button = app
            .world_mut()
            .spawn((
                scenemax_ui_input::Button,
                Choose::Select(0),
                ChildOf(parent),
            ))
            .id();
        row(&mut app.world_mut().commands(), parent, 0);
        app.world_mut().flush();
        let pointer = Pointer::new(
            PointerId::Mouse,
            Location {
                target: bevy::camera::NormalizedRenderTarget::None {
                    width: 100,
                    height: 100,
                },
                position: Vec2::splat(10.),
            },
        );
        let hit = HitData::new(Entity::PLACEHOLDER, 0., None, None);
        app.world_mut().trigger(PointerClick {
            entity: button,
            pointer: pointer.clone(),
            button: PointerButton::Secondary,
            hit: hit.clone(),
            duration: std::time::Duration::from_millis(30),
            count: 1,
        });
        assert!(app.world().resource::<State>().open);
        assert_eq!(
            app.world()
                .resource::<State>()
                .target
                .as_ref()
                .unwrap()
                .pointer
                .as_deref(),
            Some("/entities/0")
        );
        app.world_mut().trigger(PointerDragStart {
            entity: button,
            pointer,
            button: PointerButton::Primary,
            hit,
        });
        assert!(app.world().resource::<State>().drag.is_some());
    }
    #[test]
    fn menu_mutations_target_clicked_node_and_undo_restores_hierarchy() {
        let dir = tempfile::tempdir().unwrap();
        let source = r#"{"entities":[{"type":"BOX","name":"first"},{"type":"SECTION","name":"folder","children":[]}]}"#;
        let mut session = Session::new(Project::new(dir.path().into(), vec![]));
        let id = session
            .workspace
            .open_document(
                Document::from_bytes(dir.path().join("scene.smdesign"), source.as_bytes().into())
                    .unwrap(),
            )
            .unwrap();
        let revision = session.workspace.document(id).unwrap().revision();
        let mut app = App::new();
        app.insert_resource(session)
            .insert_resource(SceneState {
                current: Some((id, revision)),
                scene: Some(scenemax_ide_services::scene3d::load(dir.path(), source).unwrap()),
                selected: 1,
                ..default()
            })
            .insert_resource(State {
                target: Some(Target {
                    stamp: (id, revision),
                    pointer: Some("/entities/0".into()),
                    position: Vec2::ZERO,
                }),
                pending: Some(Action::Move(Placement::Inside("/entities/1".into()))),
                ..default()
            })
            .init_resource::<crate::presentation::inventory::State>()
            .init_resource::<ButtonInput<KeyCode>>()
            .add_message::<crate::application::ViewChange>()
            .add_systems(Update, update);
        app.update();
        let doc = app
            .world()
            .resource::<Session>()
            .workspace
            .document(id)
            .unwrap();
        let moved = doc.text().to_owned();
        assert_eq!(
            serde_json::from_str::<Value>(&moved).unwrap()["entities"][0]["children"][0]["name"],
            "first"
        );
        assert_eq!(
            app.world()
                .resource::<SceneState>()
                .pending_selection
                .as_deref(),
            Some("/entities/0/children/0")
        );
        {
            let mut session = app.world_mut().resource_mut::<Session>();
            let doc = session.workspace.document_mut(id).unwrap();
            doc.undo();
            assert_eq!(doc.text(), source);
            doc.redo();
            assert_eq!(doc.text(), moved);
        }
        // A stale menu must never apply an index to a changed document.
        app.world_mut().resource_mut::<State>().target = Some(Target {
            stamp: (id, revision),
            pointer: Some("/entities/0".into()),
            position: Vec2::ZERO,
        });
        app.world_mut().resource_mut::<State>().pending = Some(Action::Delete);
        app.update();
        assert_eq!(
            app.world()
                .resource::<Session>()
                .workspace
                .document(id)
                .unwrap()
                .text(),
            moved
        );
    }
    #[test]
    fn picked_model_inserts_in_original_folder_and_undo_restores_scene() {
        let dir = tempfile::tempdir().unwrap();
        let source = r#"{"entities":[{"type":"SECTION","name":"folder","children":[]}]}"#;
        let mut session = Session::new(Project::new(dir.path().into(), vec![]));
        let id = session
            .workspace
            .open_document(
                Document::from_bytes(dir.path().join("scene.smdesign"), source.as_bytes().into())
                    .unwrap(),
            )
            .unwrap();
        let revision = session.workspace.document(id).unwrap().revision();
        let mut app = App::new();
        app.insert_resource(session)
            .insert_resource(SceneState {
                current: Some((id, revision)),
                scene: Some(scenemax_ide_services::scene3d::load(dir.path(), source).unwrap()),
                ..default()
            })
            .insert_resource(State {
                target: Some(Target {
                    stamp: (id, revision),
                    pointer: Some("/entities/0".into()),
                    position: Vec2::ZERO,
                }),
                ..default()
            })
            .init_resource::<crate::presentation::inventory::State>()
            .init_resource::<ButtonInput<KeyCode>>()
            .add_message::<crate::application::ViewChange>()
            .add_systems(Update, update);
        app.world_mut()
            .resource_mut::<crate::presentation::inventory::State>()
            .picked_model = Some("fixture_model".into());
        app.update();
        let mut session = app.world_mut().resource_mut::<Session>();
        let doc = session.workspace.document_mut(id).unwrap();
        let value: Value = serde_json::from_str(doc.text()).unwrap();
        assert_eq!(
            value["entities"][0]["children"][0]["resourcePath"],
            "fixture_model"
        );
        assert_eq!(value["entities"][0]["children"][0]["type"], "MODEL");
        doc.undo();
        assert_eq!(doc.text(), source);
        // A selection returned after the document changed must not insert anywhere.
        app.world_mut()
            .resource_mut::<crate::presentation::inventory::State>()
            .picked_model = Some("stale_model".into());
        app.update();
        assert_eq!(
            app.world()
                .resource::<Session>()
                .workspace
                .document(id)
                .unwrap()
                .text(),
            source
        );
    }
}
