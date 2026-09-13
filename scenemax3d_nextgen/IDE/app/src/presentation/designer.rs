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
}
#[derive(Component)]
pub(crate) struct Pick {
    host: Entity,
    pointer: String,
}
#[derive(Component)]
pub(crate) struct Apply(Entity);
#[derive(Component)]
pub(crate) struct Structure {
    host: Entity,
    kind: Option<&'static str>,
}
#[derive(Component)]
pub(crate) struct Property {
    pub(crate) host: Entity,
    pub(crate) key: &'static str,
}
pub(crate) fn interactions(
    picks: Query<(&Interaction, &Pick), Changed<Interaction>>,
    structures: Query<(&Interaction, &Structure), Changed<Interaction>>,
    applies: Query<(&Interaction, &Apply), Changed<Interaction>>,
    fields: Query<(&Property, &EditableText)>,
    mut designers: Query<(&EditorHost, &mut Designer)>,
    mut session: ResMut<Session>,
    mut changes: MessageWriter<ViewChange>,
) {
    for (interaction, pick) in &picks {
        if *interaction == Interaction::Pressed
            && let Ok((_, mut d)) = designers.get_mut(pick.host)
            && d.selected.as_deref() != Some(pick.pointer.as_str())
        {
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
    for (interaction, apply) in &applies {
        if *interaction != Interaction::Pressed {
            continue;
        }
        let Ok((host, d)) = designers.get(apply.0) else {
            continue;
        };
        let Some(pointer) = &d.selected else {
            continue;
        };
        let field = |key| {
            fields
                .iter()
                .find(|(p, _)| p.host == apply.0 && p.key == key)
                .map(|(_, v)| v.value().to_string())
                .unwrap_or_default()
        };
        let result = (|| -> Result<(), String> {
            let doc = session
                .workspace
                .document_mut(host.0)
                .map_err(|e| e.to_string())?;
            if Some(doc.revision()) != d.revision {
                return Err("Document changed; reselect the widget before applying".into());
            }
            let number = |key| {
                field(key)
                    .parse::<f64>()
                    .map_err(|_| format!("Invalid {key}"))
            };
            let source = scenemax_ide_core::scene::apply(
                doc.text(),
                pointer,
                scenemax_ide_core::scene::Properties {
                    text: field("Text"),
                    width: number("Width")?,
                    height: number("Height")?,
                    font_size: number("Font size")?,
                    color: field("Color"),
                },
            )?;
            // Reject changes that cannot be previewed before recording history.
            scenemax_ide_services::scene::preview(&source)?;
            doc.replace_text(source);
            changes.write(ViewChange::BufferChanged(host.0));
            Ok(())
        })();
        session.status = result.map_or_else(
            |e| e,
            |()| "UI properties applied — save to write the document".into(),
        );
    }
}

type PickAppearance<'w, 's> = Query<
    'w,
    's,
    (
        &'static Pick,
        &'static mut Node,
        &'static mut BackgroundColor,
        Option<&'static mut scenemax_ide_ui::ButtonSurface>,
    ),
>;

pub(crate) fn refresh(
    mut commands: Commands,
    session: Res<Session>,
    mut hosts: Query<(Entity, &EditorHost, &mut Designer)>,
    mut picks: PickAppearance,
) {
    for (entity, host, mut designer) in &mut hosts {
        let Ok(doc) = session.workspace.document(host.0) else {
            continue;
        };
        if designer.revision == Some(doc.revision()) && !designer.redraw {
            continue;
        }
        let selection_only = designer.revision == Some(doc.revision()) && designer.parts.is_some();
        designer.revision = Some(doc.revision());
        designer.redraw = false;
        if !selection_only {
            commands.entity(entity).despawn_children();
            designer.parts = None;
        }
        match scenemax_ide_services::scene::preview(doc.text()) {
            Ok(scene) => {
                if !scene
                    .widgets
                    .iter()
                    .any(|w| Some(&w.pointer) == designer.selected.as_ref())
                {
                    designer.selected = scene.widgets.first().map(|w| w.pointer.clone());
                }
                if let Some(parts) = designer.parts {
                    view::inspect(
                        &mut commands,
                        entity,
                        parts,
                        &scene,
                        designer.selected.as_deref(),
                    );
                    for (pick, mut node, mut background, surface) in &mut picks {
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
                        } else {
                            let border = px(if selected { 2. } else { 0. }).all();
                            if node.border != border {
                                node.border = border;
                            }
                        }
                    }
                } else {
                    designer.parts = Some(build(
                        &mut commands,
                        entity,
                        &scene,
                        designer.selected.as_deref(),
                    ));
                }
            }
            Err(error) => {
                commands.spawn((label(format!("Cannot preview UI document\n{error}\nThe source file has not been changed."),14.),ChildOf(entity)));
            }
        }
    }
}
mod view;
use view::build;
