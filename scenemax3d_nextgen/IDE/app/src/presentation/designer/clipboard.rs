//! HUD hierarchy context menus and an editor-local subtree clipboard.
use super::*;
use scenemax_ide_core::{DocumentId, scene::clipboard};
use serde_json::Value;
#[derive(Clone)]
struct Target {
    host: Entity,
    document: DocumentId,
    revision: DocumentRevision,
    pointer: String,
    position: Vec2,
}
#[derive(Clone, Copy)]
enum Action {
    Copy,
    Paste,
    Close,
}
#[derive(Resource, Default)]
pub(crate) struct State {
    target: Option<Target>,
    clipboard: Option<Value>,
    open: bool,
    pending: Option<Action>,
}
#[derive(Component)]
pub(crate) struct Menu;

pub(super) fn observe(commands: &mut Commands, entity: Entity, host: Entity, pointer: String) {
    commands.entity(entity).observe(
        move |mut event: On<PointerClick>,
              session: Res<Session>,
              mut designers: Query<(&EditorHost, &mut Designer)>,
              mut state: ResMut<State>| {
            if event.button != PointerButton::Secondary {
                return;
            }
            event.propagate(false);
            let Ok((editor, mut designer)) = designers.get_mut(host) else {
                return;
            };
            let Ok(doc) = session.workspace.document(editor.0) else {
                return;
            };
            if designer.revision != Some(doc.revision()) {
                return;
            }
            designer.selected = Some(pointer.clone());
            designer.redraw = true;
            state.target = Some(Target {
                host,
                document: editor.0,
                revision: doc.revision(),
                pointer: pointer.clone(),
                position: event.pointer.position,
            });
            state.open = true;
            state.pending = None;
        },
    );
}
fn item(commands: &mut Commands, panel: Entity, caption: &str, enabled: bool, action: Action) {
    if !enabled {
        commands
            .spawn((
                label(caption, 14.),
                Node {
                    padding: px(7.).all(),
                    ..default()
                },
                ChildOf(panel),
            ))
            .insert(TextColor(MUTED));
        return;
    }
    let entity = button(commands, panel, caption, Name::new(caption.to_owned()));
    commands.entity(entity).observe(
        move |mut event: On<PointerClick>, mut state: ResMut<State>| {
            if event.button == PointerButton::Primary {
                event.propagate(false);
                state.pending = Some(action);
            }
        },
    );
}
fn menu(commands: &mut Commands, target: &Target, size: Vec2, copy: bool, paste: bool) {
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
        .observe(|mut event: On<PointerClick>, mut state: ResMut<State>| {
            event.propagate(false);
            state.pending = Some(Action::Close);
        });
    let panel = commands
        .spawn((
            Node {
                position_type: PositionType::Absolute,
                left: px(target.position.x.clamp(0., (size.x - 200.).max(0.))),
                top: px(target.position.y.clamp(0., (size.y - 90.).max(0.))),
                width: px(195.),
                padding: px(5.).all(),
                flex_direction: FlexDirection::Column,
                ..default()
            },
            BackgroundColor(PANEL),
            ChildOf(overlay),
        ))
        .id();
    commands
        .entity(panel)
        .observe(|mut event: On<PointerClick>| event.propagate(false));
    item(commands, panel, "Copy", copy, Action::Copy);
    item(commands, panel, "Paste", paste, Action::Paste);
}
fn apply(
    action: Action,
    target: &Target,
    state: &mut State,
    session: &mut Session,
    designer: &mut Designer,
) -> Result<bool, String> {
    let doc = session
        .workspace
        .document_mut(target.document)
        .map_err(|e| e.to_string())?;
    if doc.revision() != target.revision {
        return Err("Document changed; open the menu again".into());
    }
    match action {
        Action::Copy => {
            state.clipboard = Some(clipboard::copy(doc.text(), &target.pointer)?);
            Ok(false)
        }
        Action::Paste => {
            let (source, pointer) = clipboard::paste(
                doc.text(),
                &target.pointer,
                state.clipboard.as_ref().ok_or("Copy a UI element first")?,
            )?;
            scenemax_ide_services::scene::preview(&source)?;
            doc.replace_text(source);
            view::hierarchy::reveal(&mut designer.collapsed, &pointer);
            designer.selected = Some(pointer);
            designer.redraw = true;
            Ok(true)
        }
        Action::Close => Ok(false),
    }
}
pub(crate) fn update(
    mut commands: Commands,
    mut state: ResMut<State>,
    mut session: ResMut<Session>,
    mut designers: Query<(&EditorHost, &mut Designer)>,
    menus: Query<Entity, With<Menu>>,
    keys: Res<ButtonInput<KeyCode>>,
    windows: Query<&Window, With<bevy::window::PrimaryWindow>>,
    mut changes: MessageWriter<ViewChange>,
) {
    if keys.just_pressed(KeyCode::Escape) {
        state.pending = Some(Action::Close);
    }
    if state.target.as_ref().is_some_and(|t| {
        session.workspace.active_id() != Some(t.document)
            || session
                .workspace
                .document(t.document)
                .map_or(true, |d| d.revision() != t.revision)
            || designers.get(t.host).is_err()
    }) {
        state.pending = Some(Action::Close);
    }
    let action = state.pending.take();
    if state.open || action.is_some() {
        for entity in &menus {
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
    if state.open {
        state.open = false;
        let Ok(doc) = session.workspace.document(target.document) else {
            return;
        };
        let copy = clipboard::copy(doc.text(), &target.pointer).is_ok();
        let paste = state.clipboard.is_some()
            && clipboard::can_paste(&clipboard_root(doc.text()), &target.pointer);
        let size = windows
            .iter()
            .next()
            .map(|w| Vec2::new(w.width(), w.height()))
            .unwrap_or(Vec2::new(1280., 800.));
        menu(&mut commands, &target, size, copy, paste);
    }
    if let Some(action) = action {
        if let Ok((_, mut designer)) = designers.get_mut(target.host) {
            match apply(action, &target, &mut state, &mut session, &mut designer) {
                Ok(changed) => {
                    if changed {
                        changes.write(ViewChange::BufferChanged(target.document));
                    }
                    session.status = if changed {
                        "UI element pasted — Ctrl+Z to undo"
                    } else {
                        "UI element copied"
                    }
                    .into();
                }
                Err(error) => session.status = error,
            }
        }
        state.target = None;
    }
}
fn clipboard_root(source: &str) -> Value {
    scenemax_ide_core::scene::parse(source).unwrap_or(Value::Null)
}

#[cfg(test)]
mod tests {
    use super::*;
    use scenemax_ide_core::{Document, Project};

    fn click(app: &mut App, entity: Entity, button: PointerButton) {
        use bevy::picking::{
            backend::HitData,
            pointer::{Location, PointerId},
        };
        app.world_mut().trigger(PointerClick {
            entity,
            pointer: Pointer::new(
                PointerId::Mouse,
                Location {
                    target: bevy::camera::NormalizedRenderTarget::None {
                        width: 800,
                        height: 600,
                    },
                    position: Vec2::splat(20.),
                },
            ),
            button,
            hit: HitData::new(Entity::PLACEHOLDER, 0., None, None),
            duration: std::time::Duration::from_millis(30),
            count: 1,
        });
    }

    #[test]
    fn native_hierarchy_context_copy_paste_into_empty_root_is_one_undo_step() {
        let dir = tempfile::tempdir().unwrap();
        let source = r#"{"name":"hud","layers":[{"name":"main","widgets":[]},{"name":"source","widgets":[{"name":"panel","type":"PANEL","children":[{"name":"go","type":"BUTTON","onClickProcedure":"launch"}]}]}]}"#;
        let mut session = Session::new(Project::new(dir.path().into(), vec![]));
        let id = session
            .workspace
            .open_document(
                Document::from_bytes(dir.path().join("hud.smui"), source.as_bytes().into())
                    .unwrap(),
            )
            .unwrap();
        let revision = session.workspace.document(id).unwrap().revision();
        let mut app = App::new();
        crate::tests::ui_resources(&mut app);
        app.add_plugins(bevy::ui_widgets::ButtonPlugin)
            .insert_resource(session)
            .init_resource::<State>()
            .init_resource::<ButtonInput<KeyCode>>()
            .add_message::<ViewChange>()
            .add_systems(Update, update);
        let host = app
            .world_mut()
            .spawn((
                Node::default(),
                EditorHost(id),
                Designer {
                    revision: Some(revision),
                    ..default()
                },
            ))
            .id();
        let scene = scenemax_ide_services::scene::preview(source).unwrap();
        assert!(
            scene
                .layers
                .iter()
                .any(|(p, name)| p == "/layers/0" && name == "main")
        );
        view::build(
            &mut app.world_mut().commands(),
            host,
            &scene,
            None,
            None,
            None,
            &default(),
            false,
        );
        app.world_mut().flush();
        let pick = |app: &mut App, path: &str| {
            app.world_mut()
                .query::<(Entity, &Pick)>()
                .iter(app.world())
                .find(|(_, p)| p.pointer == path)
                .unwrap()
                .0
        };
        let panel = pick(&mut app, "/layers/1/widgets/0");
        click(&mut app, panel, PointerButton::Secondary);
        assert!(app.world().resource::<State>().open);
        app.update();
        let menu_button = |app: &mut App, caption: &str| {
            app.world_mut()
                .query::<(Entity, &Name)>()
                .iter(app.world())
                .find(|(_, n)| n.as_str() == caption)
                .unwrap()
                .0
        };
        let copy = menu_button(&mut app, "Copy");
        click(&mut app, copy, PointerButton::Primary);
        app.update();
        assert!(app.world().resource::<State>().clipboard.is_some());
        let root = pick(&mut app, "/layers/0");
        click(&mut app, root, PointerButton::Secondary);
        app.update();
        let paste = menu_button(&mut app, "Paste");
        click(&mut app, paste, PointerButton::Primary);
        app.update();
        assert_eq!(
            app.world()
                .get::<Designer>(host)
                .unwrap()
                .selected
                .as_deref(),
            Some("/layers/0/widgets/0")
        );
        let mut session = app.world_mut().resource_mut::<Session>();
        let doc = session.workspace.document_mut(id).unwrap();
        let pasted = doc.text().to_owned();
        assert_eq!(
            clipboard_root(&pasted)["layers"][0]["widgets"][0]["children"][0]["onClickProcedure"],
            "launch"
        );
        assert!(doc.undo());
        assert_eq!(doc.text(), source);
        assert!(doc.redo());
        assert_eq!(doc.text(), pasted);
        let target = Target {
            host,
            document: id,
            revision,
            pointer: "/layers/0".into(),
            position: Vec2::ZERO,
        };
        assert!(
            apply(
                Action::Paste,
                &target,
                &mut State::default(),
                &mut session,
                &mut Designer::default()
            )
            .is_err()
        );
    }
}
