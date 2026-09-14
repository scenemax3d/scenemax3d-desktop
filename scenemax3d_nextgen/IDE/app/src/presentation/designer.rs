//! Retained UI scene documents. No window or projector process is created.
use super::{components::*, input::Action};
use crate::application::{Session, ViewChange};
use bevy::{prelude::*, text::EditableText};
use scenemax_ide_core::DocumentRevision;
use scenemax_ide_services::scene::{PreviewWidget, ScenePreview};
use scenemax_ide_ui::{
    button,
    canvas::{Canvas, CanvasText},
    label,
    theme::*,
};

#[derive(Component, Default)]
pub(crate) struct Designer {
    revision: Option<DocumentRevision>,
    selected: Option<String>,
    redraw: bool,
    parts: Option<view::Parts>,
    pending: Option<bevy::tasks::Task<Result<ScenePreview, String>>>,
    scene: Option<ScenePreview>,
    collapsed: std::collections::HashSet<String>,
    properties_collapsed: bool,
    live_revision: Option<DocumentRevision>,
    keep_controls: bool,
    gesture: live::Gesture,
}
#[derive(Component)]
pub(crate) struct Pick {
    host: Entity,
    pointer: String,
}
#[derive(Component)]
pub(crate) struct Structure {
    host: Entity,
    kind: Option<&'static str>,
}
#[derive(Component)]
pub(crate) struct Property {
    pub(crate) host: Entity,
    pub(crate) key: String,
    pub(crate) kind: view::FieldKind,
    observed: String,
}
pub(crate) fn interactions(
    picks: Query<(&Interaction, &Pick), Changed<Interaction>>,
    structures: Query<(&Interaction, &Structure), Changed<Interaction>>,
    mut designers: Query<(&EditorHost, &mut Designer)>,
    mut session: ResMut<Session>,
    mut changes: MessageWriter<ViewChange>,
) {
    for (interaction, pick) in &picks {
        if *interaction == Interaction::Pressed
            && pick.pointer.contains("/widgets/")
            && let Ok((_, mut d)) = designers.get_mut(pick.host)
            && d.selected.as_deref() != Some(pick.pointer.as_str())
        {
            view::hierarchy::reveal(&mut d.collapsed, &pick.pointer);
            d.selected = Some(pick.pointer.clone());
            d.redraw = true;
        }
    }
    for (interaction, action) in &structures {
        if *interaction != Interaction::Pressed {
            continue;
        }
        let Ok((host, mut d)) = designers.get_mut(action.host) else {
            continue;
        };
        let result = (|| -> Result<(), String> {
            let doc = session
                .workspace
                .document_mut(host.0)
                .map_err(|e| e.to_string())?;
            if Some(doc.revision()) != d.revision {
                return Err("Document changed; retry the action".into());
            }
            let source = if let Some(kind) = action.kind {
                let (source, pointer) =
                    scenemax_ide_core::scene::add(doc.text(), d.selected.as_deref(), kind)?;
                scenemax_ide_services::scene::preview(&source)?;
                view::hierarchy::reveal(&mut d.collapsed, &pointer);
                d.selected = Some(pointer);
                source
            } else {
                scenemax_ide_core::scene::remove(
                    doc.text(),
                    d.selected.as_deref().ok_or("Select a widget")?,
                )?
            };
            doc.replace_text(source);
            changes.write(ViewChange::BufferChanged(host.0));
            Ok(())
        })();
        session.status = result.map_or_else(
            |e| e,
            |()| "UI structure updated — save to write the document".into(),
        );
    }
}

type PickAppearance<'w, 's> = Query<
    'w,
    's,
    (
        &'static Pick,
        Option<&'static mut Outline>,
        &'static mut BackgroundColor,
        Option<&'static mut scenemax_ide_ui::ButtonSurface>,
    ),
>;

pub(crate) fn refresh(
    mut commands: Commands,
    session: Res<Session>,
    mut hosts: Query<(Entity, &EditorHost, &mut Designer)>,
    mut picks: PickAppearance,
    assets: Option<Res<crate::project_assets::ProjectAssets>>,
    server: Option<Res<AssetServer>>,
    mut rows: view::hierarchy::Rows,
) {
    for (entity, host, mut designer) in &mut hosts {
        let Ok(doc) = session.workspace.document(host.0) else {
            continue;
        };
        view::hierarchy::synchronize(entity, &designer, &mut rows);
        let mut ready = None;
        if designer.revision != Some(doc.revision()) {
            designer.keep_controls =
                designer.live_revision == Some(doc.revision()) && designer.parts.is_some();
            if let Some(previous) = designer.scene.as_ref() {
                match scenemax_ide_services::scene::assets::refresh_cached(doc.text(), previous) {
                    Ok(Some(scene)) => ready = Some(Ok(scene)),
                    Ok(None) => {}
                    Err(error) => ready = Some(Err(error)),
                }
            }
            designer.pending = None;
            if ready.is_none() {
                let source = doc.text().to_owned();
                let root = session.workspace.project().root().to_owned();
                designer.pending = Some(
                    bevy::tasks::IoTaskPool::get_or_init(Default::default).spawn(async move {
                        scenemax_ide_services::scene::assets::load(&root, &source)
                    }),
                );
            }
            designer.revision = Some(doc.revision());
        }
        let loaded = ready.or_else(|| {
            designer
                .pending
                .as_mut()
                .and_then(|task| bevy::tasks::block_on(bevy::tasks::poll_once(task)))
        });
        if loaded.is_none() && (!designer.redraw || designer.scene.is_none()) {
            continue;
        }
        let preview_changed = loaded.is_some();
        let inspect = designer.redraw || !preview_changed;
        let result = if let Some(result) = loaded {
            designer.pending = None;
            if !designer.keep_controls {
                commands.entity(entity).despawn_children();
                designer.parts = None;
            }
            result
        } else {
            let Some(scene) = designer.scene.take() else {
                continue;
            };
            Ok(scene)
        };
        designer.redraw = false;
        match result {
            Ok(scene) => {
                if !scene
                    .widgets
                    .iter()
                    .any(|w| Some(&w.pointer) == designer.selected.as_ref())
                {
                    designer.selected = scene.widgets.first().map(|w| w.pointer.clone());
                }
                if let Some(mut parts) = designer.parts.take() {
                    if preview_changed && let Some(previous) = designer.scene.as_ref() {
                        view::update_preview(
                            &mut commands,
                            entity,
                            &mut parts,
                            &scene,
                            previous,
                            assets.as_deref(),
                            server.as_deref(),
                        );
                    }
                    if inspect {
                        // A selection can change while a new font/sprite is still loading.
                        // Build controls from the current buffer, never the older rendered snapshot.
                        if let Ok(mut current) = scenemax_ide_services::scene::preview(doc.text()) {
                            current.fonts = scene.fonts.clone();
                            current.sprites = scene.sprites.clone();
                            view::inspect(
                                &mut commands,
                                entity,
                                &parts,
                                &current,
                                designer.selected.as_deref(),
                            );
                        }
                    }
                    for (pick, outline, mut background, surface) in &mut picks {
                        if pick.host != entity {
                            continue;
                        }
                        let selected = designer.selected.as_deref() == Some(pick.pointer.as_str());
                        if let Some(mut surface) = surface {
                            let color = if selected { SELECTED } else { PANEL };
                            if surface.0 != color {
                                surface.0 = color;
                            }
                            if background.0 != color {
                                background.0 = color;
                            }
                        } else if let Some(mut outline) = outline {
                            outline.color = if selected { SELECTED } else { Color::NONE };
                        }
                    }
                    designer.parts = Some(parts);
                } else {
                    designer.parts = Some(build(
                        &mut commands,
                        entity,
                        &scene,
                        designer.selected.as_deref(),
                        assets.as_deref(),
                        server.as_deref(),
                        &designer.collapsed,
                        designer.properties_collapsed,
                    ));
                }
                designer.scene = Some(scene);
            }
            Err(error) => {
                commands.spawn((label(format!("Cannot preview UI document\n{error}\nThe source file has not been changed."),14.),ChildOf(entity)));
            }
        }
    }
}
mod view;
use view::build;

pub(crate) mod live;
