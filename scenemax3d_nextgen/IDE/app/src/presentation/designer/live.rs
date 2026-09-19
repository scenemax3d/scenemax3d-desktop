//! Immediate validated property edits; controls remain alive while the preview updates.
use super::*;
use bevy::input_focus::InputFocus;
use scenemax_ide_ui::property::{Checked, Choice};
#[derive(Default)]
pub(super) struct Gesture {
    last: Option<(DocumentRevision, Entity, Option<Entity>, u64)>,
}
type Fields<'w, 's> = Query<
    'w,
    's,
    (
        Entity,
        &'static mut Property,
        Option<&'static EditableText>,
        Option<&'static Checked>,
        Option<&'static Choice>,
    ),
>;
pub(crate) fn update(
    mut fields: Fields,
    mut designers: Query<(Entity, &EditorHost, &mut Designer)>,
    mut session: ResMut<Session>,
    mut changes: MessageWriter<ViewChange>,
    focus: Option<Res<InputFocus>>,
    mouse: Option<Res<ButtonInput<MouseButton>>>,
) {
    let focused = focus.as_ref().and_then(|f| f.get());
    for (entity, host, mut state) in &mut designers {
        let Ok(doc) = session.workspace.document(host.0) else {
            continue;
        };
        if state.parts.is_none()
            || state.redraw
            || state.revision != Some(doc.revision())
            || (state.pending.is_some() && !state.keep_controls)
        {
            state.gesture.last = None;
            continue;
        }
        let Some(pointer) = state.selected.clone() else {
            continue;
        };
        if state
            .gesture
            .last
            .is_some_and(|(_, _, last, _)| last != focused)
        {
            state.gesture.last = None;
        }
        let mut pending = Vec::new();
        for (field, mut property, text, checked, choice) in
            fields.iter_mut().filter(|(_, p, _, _, _)| p.host == entity)
        {
            let current = if let Some(text) = text {
                text.value().to_string()
            } else if let Some(c) = checked {
                c.0.to_string()
            } else {
                choice.map(|c| c.0.clone()).unwrap_or_default()
            };
            if current == property.observed {
                continue;
            }
            property.observed = current.clone();
            let value = if let Some(c) = checked {
                Ok(serde_json::json!(c.0))
            } else if let Some(c) = choice {
                Ok(serde_json::json!(c.0))
            } else {
                view::parse_field(&property.kind, &current)
            };
            pending.push((field, property.key.clone(), value));
        }
        if pending.is_empty() {
            continue;
        }
        let mut source = doc.text().to_owned();
        let mut edited = Vec::new();
        let mut error = None;
        for (field, key, value) in pending {
            let values = if key.starts_with("constraints/") {
                let prefix = key
                    .rsplit_once('/')
                    .map(|(p, _)| format!("{p}/"))
                    .unwrap_or_default();
                fields
                    .iter()
                    .filter(|(_, p, _, _, _)| p.host == entity && p.key.starts_with(&prefix))
                    .map(|(_, p, _, _, _)| {
                        view::parse_field(&p.kind, &p.observed).map(|v| (p.key.clone(), v))
                    })
                    .collect::<Result<Vec<_>, _>>()
            } else {
                value.map(|v| vec![(key.clone(), v)])
            };
            let result = values
                .and_then(|values| scenemax_ide_core::scene::patch(&source, &pointer, values))
                .and_then(|next| {
                    scenemax_ide_services::scene::preview(&next)?;
                    Ok(next)
                });
            match result {
                Ok(next) => {
                    if next != source {
                        source = next;
                        edited.push(field);
                    }
                }
                Err(message) => {
                    error = Some(format!(
                        "{}: {message} (preview keeps the last valid value)",
                        key
                    ))
                }
            }
        }
        if let Some(error) = error.as_ref() {
            session.status = error.clone();
        }
        if edited.is_empty() {
            continue;
        }
        let Ok(doc) = session.workspace.document_mut(host.0) else {
            continue;
        };
        let saved = doc.saved_version();
        let previous = doc.revision();
        let held = mouse.as_ref().is_some_and(|m| m.pressed(MouseButton::Left));
        let pressed = mouse
            .as_ref()
            .is_some_and(|m| m.just_pressed(MouseButton::Left));
        let continuing = edited.len() == 1
            && (focused == Some(edited[0]) || held)
            && !pressed
            && state.gesture.last == Some((previous, edited[0], focused, saved));
        if continuing {
            doc.replace_text_continuing(source);
        } else {
            doc.replace_text(source);
        }
        let revision = doc.revision();
        state.live_revision = Some(revision);
        state.gesture.last = (edited.len() == 1).then_some((revision, edited[0], focused, saved));
        changes.write(ViewChange::BufferChanged(host.0));
        if error.is_none() {
            session.status = "UI updated · Ctrl+S to save · Ctrl+Z to undo".into();
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use scenemax_ide_core::{Document, DocumentId, Project};
    const SOURCE: &str = r#"{"name":"hud","canvasWidth":800,"canvasHeight":600,"layers":[{"name":"overlay","widgets":[{"name":"caption","type":"TEXT_VIEW","widthMode":"FIXED","heightMode":"FIXED","width":100,"height":40,"fontSize":20,"text":"Hello"}]}]}"#;
    fn setup() -> (App, Entity, DocumentId) {
        let mut session = Session::new(Project::new(std::path::PathBuf::from("."), vec![]));
        let id = session
            .workspace
            .open_document(
                Document::from_bytes("hud.smui".into(), SOURCE.as_bytes().to_vec()).unwrap(),
            )
            .unwrap();
        let revision = session.workspace.document(id).unwrap().revision();
        let mut app = App::new();
        app.insert_resource(session)
            .init_resource::<InputFocus>()
            .add_message::<ViewChange>()
            .add_systems(Update, (update, super::super::refresh).chain());
        let host = app
            .world_mut()
            .spawn((Node::default(), EditorHost(id), Designer::default()))
            .id();
        let scene = scenemax_ide_services::scene::preview(SOURCE).unwrap();
        let parts = view::build(
            &mut app.world_mut().commands(),
            host,
            &scene,
            Some("/layers/0/widgets/0"),
            None,
            None,
            &Default::default(),
            false,
        );
        app.world_mut().flush();
        let mut d = app.world_mut().get_mut::<Designer>(host).unwrap();
        d.revision = Some(revision);
        d.parts = Some(parts);
        d.scene = Some(scene);
        d.selected = Some("/layers/0/widgets/0".into());
        (app, host, id)
    }
    fn field(app: &mut App, key: &str) -> Entity {
        let world = app.world_mut();
        world
            .query::<(Entity, &Property)>()
            .iter(world)
            .find(|(_, p)| p.key == key)
            .unwrap()
            .0
    }
    fn edit(app: &mut App, field: Entity, value: &str) {
        app.world_mut()
            .get_mut::<EditableText>(field)
            .unwrap()
            .editor_mut()
            .set_text(value);
        app.update();
    }
    #[test]
    fn text_layout_choices_and_visibility_update_without_replacing_controls_or_canvas() {
        let (mut app, host, id) = setup();
        let width = field(&mut app, "width");
        let text = field(&mut app, "text");
        app.world_mut()
            .resource_mut::<InputFocus>()
            .set(width, bevy::input_focus::FocusCause::Navigated);
        let canvas = {
            let world = app.world_mut();
            world
                .query_filtered::<Entity, With<Canvas>>()
                .single(world)
                .unwrap()
        };
        let widget = {
            let world = app.world_mut();
            world
                .query_filtered::<(Entity, &Pick), Without<Button>>()
                .iter(world)
                .find(|(_, p)| p.host == host)
                .unwrap()
                .0
        };
        edit(&mut app, width, "200");
        assert_eq!(app.world().get::<Node>(widget).unwrap().width, percent(25.));
        assert_eq!(app.world().resource::<InputFocus>().get(), Some(width));
        assert!(app.world().get::<Canvas>(canvas).is_some());
        assert!(app.world().get::<Property>(text).is_some());
        edit(&mut app, text, "Changed");
        assert_eq!(app.world().get::<Text>(widget).unwrap().0, "Changed");
        let hidden_frame = app
            .world()
            .get::<Children>(widget)
            .unwrap()
            .iter()
            .find(|e| {
                app.world()
                    .get::<Name>(*e)
                    .is_some_and(|n| n.as_str() == "Hidden at runtime")
            })
            .unwrap();
        let visible = field(&mut app, "visible");
        app.world_mut().get_mut::<Checked>(visible).unwrap().0 = false;
        app.update();
        assert_eq!(
            app.world().get::<Node>(widget).unwrap().display,
            Display::Flex
        );
        assert_eq!(
            app.world().get::<Node>(hidden_frame).unwrap().display,
            Display::Flex
        );
        let source: serde_json::Value = serde_json::from_str(
            app.world()
                .resource::<Session>()
                .workspace
                .document(id)
                .unwrap()
                .text(),
        )
        .unwrap();
        assert_eq!(source["layers"][0]["widgets"][0]["visible"], false);
        app.world_mut().get_mut::<Checked>(visible).unwrap().0 = true;
        app.update();
        assert_eq!(
            app.world().get::<Node>(widget).unwrap().display,
            Display::Flex
        );
        assert_eq!(
            app.world().get::<Node>(hidden_frame).unwrap().display,
            Display::None
        );
        let alignment = field(&mut app, "textAlignment");
        app.world_mut().get_mut::<Choice>(alignment).unwrap().0 = "right".into();
        app.update();
        assert_eq!(
            app.world().get::<TextLayout>(widget).unwrap().justify,
            Justify::Right
        );
        let doc = app
            .world()
            .resource::<Session>()
            .workspace
            .document(id)
            .unwrap();
        assert!(doc.is_dirty());
        assert!(doc.text().contains("Changed"));
        assert!(app.world().get::<Designer>(host).unwrap().pending.is_none());
    }
    #[test]
    fn incomplete_numbers_keep_last_valid_preview_and_typing_is_one_undo_gesture() {
        let (mut app, _, id) = setup();
        let width = field(&mut app, "width");
        app.world_mut()
            .resource_mut::<InputFocus>()
            .set(width, bevy::input_focus::FocusCause::Navigated);
        edit(&mut app, width, "200");
        edit(&mut app, width, "220");
        let valid = app
            .world()
            .resource::<Session>()
            .workspace
            .document(id)
            .unwrap()
            .text()
            .to_owned();
        edit(&mut app, width, "-");
        assert_eq!(
            app.world()
                .resource::<Session>()
                .workspace
                .document(id)
                .unwrap()
                .text(),
            valid
        );
        assert_eq!(app.world().resource::<InputFocus>().get(), Some(width));
        edit(&mut app, width, "240");
        let mut session = app.world_mut().resource_mut::<Session>();
        let doc = session.workspace.document_mut(id).unwrap();
        assert!(doc.undo());
        assert_eq!(doc.text(), SOURCE);
    }
    #[test]
    fn constraint_target_commits_the_current_edge_and_save_splits_undo() {
        let (mut app, _, id) = setup();
        let edge = field(&mut app, "constraints/LEFT/targetSide");
        app.world_mut().get_mut::<Choice>(edge).unwrap().0 = "RIGHT".into();
        app.update();
        let target = field(&mut app, "constraints/LEFT/targetName");
        app.world_mut().get_mut::<Choice>(target).unwrap().0 = "parent".into();
        app.update();
        let source = app
            .world()
            .resource::<Session>()
            .workspace
            .document(id)
            .unwrap()
            .text();
        let value: serde_json::Value = serde_json::from_str(source).unwrap();
        assert_eq!(
            value["layers"][0]["widgets"][0]["constraints"][0]["targetSide"],
            "RIGHT"
        );
        let width = field(&mut app, "width");
        app.world_mut()
            .resource_mut::<InputFocus>()
            .set(width, bevy::input_focus::FocusCause::Navigated);
        edit(&mut app, width, "200");
        app.world_mut()
            .resource_mut::<Session>()
            .workspace
            .document_mut(id)
            .unwrap()
            .mark_saved();
        let saved = app
            .world()
            .resource::<Session>()
            .workspace
            .document(id)
            .unwrap()
            .text()
            .to_owned();
        edit(&mut app, width, "220");
        let mut session = app.world_mut().resource_mut::<Session>();
        let doc = session.workspace.document_mut(id).unwrap();
        assert!(doc.undo());
        assert_eq!(doc.text(), saved);
    }
}
