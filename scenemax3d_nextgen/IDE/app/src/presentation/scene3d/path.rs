//! Interactive path drawing on the editor's horizontal work plane.
use super::*;
use serde_json::json;
#[derive(Resource, Default)]
pub(crate) struct Drawing {
    pub(super) document: Option<(DocumentId, DocumentRevision)>,
    points: Vec<Vec3>,
    finish: bool,
}
impl Drawing {
    pub(super) fn begin(&mut self, stamp: (DocumentId, DocumentRevision)) {
        *self = Self {
            document: Some(stamp),
            ..default()
        };
    }
}
#[derive(Component)]
pub(crate) struct Draft;
pub(super) fn observe(commands: &mut Commands, viewport: Entity, camera: Entity) {
    commands.entity(viewport).observe(
        move |event: On<Pointer<Click>>,
              mut drawing: ResMut<Drawing>,
              ports: Query<(&ComputedNode, &UiGlobalTransform)>,
              cameras: Query<(&Transform, &Projection, &Orbit)>| {
            if drawing.document.is_none() || event.button != PointerButton::Primary {
                return;
            }
            let Ok((node, ui)) = ports.get(viewport) else {
                return;
            };
            let Ok((transform, projection, orbit)) = cameras.get(camera) else {
                return;
            };
            let Some(inverse) = ui.try_inverse() else {
                return;
            };
            let uv = inverse
                .transform_point2(event.pointer_location.position / node.inverse_scale_factor())
                / node.size()
                + Vec2::splat(0.5);
            let Projection::Perspective(p) = projection else {
                return;
            };
            let half = (p.fov * 0.5).tan();
            let dir = transform.rotation
                * Vec3::new(
                    (uv.x * 2. - 1.) * half * p.aspect_ratio,
                    (1. - uv.y * 2.) * half,
                    -1.,
                );
            if dir.y.abs() < 0.0001 {
                return;
            }
            let t = (orbit.target.y - transform.translation.y) / dir.y;
            if t <= 0. {
                return;
            }
            let point = transform.translation + dir * t;
            if drawing
                .points
                .last()
                .is_none_or(|last| last.distance(point) > 0.01)
            {
                drawing.points.push(point);
            }
            if event.count == 2 && drawing.points.len() >= 2 {
                drawing.finish = true;
            }
        },
    );
}
#[derive(bevy::ecs::system::SystemParam)]
pub(crate) struct Preview<'w, 's> {
    drafts: Query<'w, 's, Entity, With<Draft>>,
    meshes: Option<ResMut<'w, Assets<Mesh>>>,
    materials: Option<ResMut<'w, Assets<StandardMaterial>>>,
    count: Local<'s, usize>,
}
pub(crate) fn update(
    mut commands: Commands,
    mut drawing: ResMut<Drawing>,
    keys: Res<ButtonInput<KeyCode>>,
    mut session: ResMut<Session>,
    mut state: ResMut<SceneState>,
    mut changes: MessageWriter<crate::application::ViewChange>,
    preview: Preview,
) {
    let Preview {
        drafts,
        mut meshes,
        mut materials,
        mut count,
    } = preview;
    let Some(stamp) = drawing.document else {
        return;
    };
    if keys.just_pressed(KeyCode::Escape) || state.current != Some(stamp) {
        *drawing = Drawing::default();
    } else if drawing.finish {
        let result = (|| -> Result<(), String> {
            let doc = session
                .workspace
                .document_mut(stamp.0)
                .map_err(|e| e.to_string())?;
            if doc.revision() != stamp.1 {
                return Err("Document changed while drawing".into());
            }
            let mut entry = scenemax_ide_core::scene3d::structure::template("PATH")?;
            entry["position"] = json!([0, 0, 0]);
            entry["pathData"] = json!({"closed":false,"subdivisions":20,"controlPoints":drawing.points.iter().map(|p|json!({"position":p.to_array(),"tangentIn":[0,0,0],"tangentOut":[0,0,0],"tangentBroken":false})).collect::<Vec<_>>()});
            let (source, pointer) =
                scenemax_ide_core::scene3d::structure::insert(doc.text(), None, entry)?;
            doc.replace_text(source);
            state.pending_selection = Some(pointer);
            changes.write(crate::application::ViewChange::BufferChanged(stamp.0));
            Ok(())
        })();
        session.status = result.map_or_else(|e| e, |()| "Path created — Ctrl+S to save".into());
        *drawing = Drawing::default();
    }
    if drawing.document.is_none() || *count != drawing.points.len() {
        for draft in &drafts {
            commands.entity(draft).despawn();
        }
        *count = drawing.points.len();
        if drawing.document.is_some()
            && let (Some(root), Some(meshes), Some(materials)) =
                (state.world, meshes.as_mut(), materials.as_mut())
        {
            let draft = commands
                .spawn((
                    Draft,
                    Transform::default(),
                    Visibility::default(),
                    ChildOf(root),
                ))
                .id();
            super::cinematic::line(
                &mut commands,
                draft,
                meshes,
                materials,
                &drawing.points,
                Color::srgb(1., 0.75, 0.2),
            );
        }
    }
}
pub(super) fn points(data: &serde_json::Value) -> Vec<Vec3> {
    let vec = |v: &serde_json::Value| {
        Vec3::new(
            v[0].as_f64().unwrap_or(0.) as f32,
            v[1].as_f64().unwrap_or(0.) as f32,
            v[2].as_f64().unwrap_or(0.) as f32,
        )
    };
    let Some(points) = data["controlPoints"].as_array() else {
        return vec![];
    };
    let mut result = Vec::new();
    let segments = if data["closed"].as_bool().unwrap_or(false) {
        points.len()
    } else {
        points.len().saturating_sub(1)
    };
    for i in 0..segments {
        let a = &points[i];
        let b = &points[(i + 1) % points.len()];
        let p = vec(&a["position"]);
        let q = vec(&b["position"]);
        let h = p + vec(&a["tangentOut"]);
        let k = q + vec(&b["tangentIn"]);
        for step in 0..=20 {
            let t = step as f32 / 20.;
            let u = 1. - t;
            result.push(p * u * u * u + h * 3. * u * u * t + k * 3. * u * t * t + q * t * t * t);
        }
    }
    result
}
