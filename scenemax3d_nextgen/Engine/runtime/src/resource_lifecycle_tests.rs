use super::*;
use crate::value_calls::tests::{resume_next, run_in};
use bevy::ecs::system::RunSystemOnce;

fn run(world: &mut World, source: &str) {
    run_in(world, source);
    for _ in 0..64 {
        if world.resource::<DelayedActionQueue>().actions.is_empty() {
            return;
        }
        resume_next(world);
    }
    panic!("fixture did not finish");
}

fn named(world: &mut World, name: &str) -> Entity {
    let target = world
        .resource::<SceneMaxVars>()
        .2
        .get(name)
        .and_then(|v| v.entity_identity())
        .map(|id| id.runtime_name)
        .unwrap_or_else(|| name.into());
    world
        .query::<(Entity, &SceneMaxEntity)>()
        .iter(world)
        .find(|(_, s)| s.name == target)
        .unwrap()
        .0
}

#[test]
fn dynamic_resources_freeze_before_effectful_coordinates_and_scoped_returns() {
    let mut world = World::new();
    run(
        &mut world,
        r#"
var selected = "effects.effekseer.first"
var calls = 0
change() = {
 selected = "effects.effekseer.second"
 calls = calls + 1
 return 4
}
make(resource) = {
 subject => (resource): pos (change(),0,0)
 return subject
}
var result = make(selected)
var final_calls = calls
"#,
    );
    let result = named(&mut world, "result");
    assert_eq!(
        world
            .get::<SceneMaxEffekseerEffect>(result)
            .unwrap()
            .asset_id,
        "first"
    );
    assert_eq!(world.get::<Transform>(result).unwrap().translation.x, 4.);
    assert_eq!(world.resource::<SceneMaxVars>().0["final_calls"], 1.);
}

#[test]
fn dynamic_source_pool_growth_reevaluates_resource_and_retains_reused_identity() {
    let mut world = World::new();
    run(
        &mut world,
        r#"
var selected = "effects.effekseer.first"
source => (selected)
items => Object.Pool(source, size 1)
selected = "effects.effekseer.second"
var first = items.acquire
var grown = items.acquire
items.release first
var reused = items.acquire
"#,
    );
    let first = named(&mut world, "first");
    let grown = named(&mut world, "grown");
    assert_eq!(
        world
            .get::<SceneMaxEffekseerEffect>(first)
            .unwrap()
            .asset_id,
        "first"
    );
    assert_eq!(
        world
            .get::<SceneMaxEffekseerEffect>(grown)
            .unwrap()
            .asset_id,
        "second"
    );
    assert_eq!(
        world.resource::<SceneMaxVars>().2["first"],
        world.resource::<SceneMaxVars>().2["reused"]
    );
    assert_ne!(first, grown);
}

#[test]
fn shared_dynamic_declarations_skip_resource_side_effects_and_invalid_replacement_is_atomic() {
    let mut world = World::new();
    run(
        &mut world,
        r#"
var calls = 0
choose() = {
 calls = calls + 1
 return "effects.effekseer.first"
}
shared subject => (choose())
var original = subject
shared subject => (choose())
var result = calls
"#,
    );
    assert_eq!(world.resource::<SceneMaxVars>().0["result"], 1.);
    let original = named(&mut world, "original");
    assert_eq!(named(&mut world, "subject"), original);
    run(
        &mut world,
        "ordinary => box\nvar old = ordinary\nordinary => (null)\nordinary => (missing)",
    );
    assert_eq!(named(&mut world, "ordinary"), named(&mut world, "old"));
}

#[test]
fn resource_source_serialization_escapes_names_and_preserves_options() {
    let raw = r#"dynamic (choose("x:y")) sprite: multiplayer, pos (2,3,4), scale 2"#;
    let encoded =
        scenemax_parser::evaluated_entity_resource_source(raw, "sheet with \"quotes\":blue")
            .unwrap();
    let parsed = scenemax_parser::parse_program(&format!("subject => {encoded}")).unwrap();
    let Statement::ModelDecl { options, .. } = &parsed.statements[0] else {
        panic!()
    };
    assert!(options.sprite && options.multiplayer);
    let value = scenemax_runtime_vm_core::references::evaluate(
        options.resource_value.as_ref().unwrap(),
        &SceneMaxVars::default(),
        None,
        &HashMap::new(),
        &RuntimeVmSpatial {
            transforms_by_name: None,
            collider_bounds: None,
        },
    )
    .unwrap();
    assert_eq!(
        value.snapshot(),
        Some(serde_json::json!("sheet with \"quotes\":blue"))
    );
    assert_eq!(options.position.unwrap().x, 2.);
    assert_eq!(options.scale.unwrap().x, 2.);
    assert!(!encoded.contains("choose"));
    let parsed = scenemax_parser::parse_program("subject => (\"sprite static collider\")").unwrap();
    let Statement::ModelDecl { options, .. } = &parsed.statements[0] else {
        panic!()
    };
    assert!(!options.sprite && !options.collider && options.body_kind.is_none());
}

fn sprite_world() -> (App, tempfile::TempDir) {
    let root = tempfile::tempdir().unwrap();
    fs::create_dir(root.path().join("sprites")).unwrap();
    fs::write(
        root.path().join("sprites/sprites.json"),
        r#"{"sprites":[{"name":"sheet","path":"sprites/sheet.png","rows":1,"cols":4}]}"#,
    )
    .unwrap();
    // Header-only metadata fixture; rendering/decode acceptance uses the native PNG fixture.
    let mut header = vec![0; 24];
    header[..8].copy_from_slice(b"\x89PNG\r\n\x1a\n");
    header[16..20].copy_from_slice(&64_u32.to_be_bytes());
    header[20..24].copy_from_slice(&16_u32.to_be_bytes());
    fs::write(root.path().join("sprites/sheet.png"), header).unwrap();
    let mut app = App::new();
    app.add_plugins((
        MinimalPlugins,
        AssetPlugin {
            file_path: root.path().display().to_string(),
            ..default()
        },
    ))
    .init_asset::<Mesh>()
    .init_asset::<StandardMaterial>()
    .init_asset::<Image>();
    app.world_mut().init_resource::<SceneMaxRuntimeAssets>();
    app.world_mut()
        .resource_mut::<SceneMaxRuntimeAssets>()
        .asset_root = Some(root.path().into());
    (app, root)
}

#[test]
fn runtime_returned_sprites_and_source_pools_build_independent_visuals() {
    let (mut app, _root) = sprite_world();
    let world = app.world_mut();
    run(
        world,
        r#"
make(resource) = {
 subject => (resource) sprite: pos (2,3,0), size (2,3)
 return subject
}
var result = make("sheet")
source => sheet sprite: hidden
items => Object.Pool(source, size 2)
var first = items.acquire
var second = items.acquire
"#,
    );
    let result = named(world, "result");
    assert_eq!(world.get::<SceneMaxSprite>(result).unwrap().frame_count, 4);
    assert_eq!(
        world.get::<Transform>(result).unwrap().translation,
        Vec3::new(2., 3., 0.)
    );
    let first = named(world, "first");
    let second = named(world, "second");
    assert_ne!(
        world.get::<SceneMaxSprite>(first).unwrap().mesh,
        world.get::<SceneMaxSprite>(second).unwrap().mesh
    );
    run(
        world,
        "first.play frame 0 to 3 in 1 seconds loop\nsecond.play frame 1 to 2 in 2 seconds loop",
    );
    assert_eq!(
        world
            .get::<SceneMaxSpriteAnimation>(first)
            .unwrap()
            .to_frame,
        3
    );
    assert_eq!(
        world
            .get::<SceneMaxSpriteAnimation>(second)
            .unwrap()
            .from_frame,
        1
    );
    run(world, "items.release first\nvar reused = items.acquire");
    assert_eq!(named(world, "reused"), first);
    assert_eq!(
        *world.get::<Visibility>(first).unwrap(),
        Visibility::Inherited
    );
}

#[test]
fn pooled_sensors_disable_reserves_and_restore_collision_after_reuse() {
    let mut world = World::new();
    run(
        &mut world,
        "source => sphere collider\nitems => Object.Pool(source, size 1)",
    );
    let member = world.resource::<SceneMaxObjectPools>().pools["items"].available[0].clone();
    let entity = named(&mut world, &member);
    assert!(world.get::<Sensor>(entity).is_some());
    assert!(world.get::<AvianCollider>(entity).is_none());
    run(&mut world, "var first = items.acquire");
    assert!(world.get::<AvianCollider>(entity).is_some());
    assert_ne!(
        *world.get::<CollisionLayers>(entity).unwrap(),
        CollisionLayers::NONE
    );
    run(&mut world, "items.release first");
    assert!(world.get::<AvianCollider>(entity).is_none());
    run(&mut world, "var reused = items.acquire");
    assert_eq!(named(&mut world, "reused"), entity);
    assert!(world.get::<AvianCollider>(entity).is_some());
    assert!(world.get::<Sensor>(entity).is_some());
}

fn sprite_tick(world: &mut World, seconds: f32) {
    let mut time = Time::<()>::default();
    time.advance_by(std::time::Duration::from_secs_f32(seconds));
    world.insert_resource(time);
    world.run_system_once(update_sprite_animations).unwrap();
}

#[test]
fn finite_sprite_repetitions_and_total_playback_time_suspend_for_the_whole_run() {
    for (suffix, seconds, expected_u) in [("loop 3 times", 3., 0.75), ("for 1.5 seconds", 1.5, 0.5)]
    {
        let (mut app, _root) = sprite_world();
        let world = app.world_mut();
        run(world, "subject => sheet sprite");
        run_in(
            world,
            &format!("subject.play (frame 0 to 3 in 1 seconds) {suffix}\nvar finished = 1"),
        );
        let entity = named(world, "subject");
        assert_eq!(
            world
                .get::<SceneMaxSpriteAnimation>(entity)
                .unwrap()
                .limit_seconds,
            Some(seconds)
        );
        assert_eq!(
            world.resource::<DelayedActionQueue>().actions[0].remaining_seconds,
            seconds
        );
        assert!(!world.resource::<SceneMaxVars>().0.contains_key("finished"));
        sprite_tick(world, seconds - 0.1);
        assert!(world.get::<SceneMaxSpriteAnimation>(entity).is_some());
        sprite_tick(world, 0.2);
        assert!(world.get::<SceneMaxSpriteAnimation>(entity).is_none());
        let sheet = world.get::<SceneMaxSprite>(entity).unwrap();
        let mesh = world.resource::<Assets<Mesh>>().get(&sheet.mesh).unwrap();
        let VertexAttributeValues::Float32x2(uv) = mesh.attribute(Mesh::ATTRIBUTE_UV_0).unwrap()
        else {
            panic!()
        };
        assert_eq!(uv[0][0], expected_u);
        resume_next(world);
        assert_eq!(world.resource::<SceneMaxVars>().0["finished"], 1.);
    }
}

#[test]
fn parallel_sprite_playback_uses_private_uv_meshes_and_replacement_clears_old_playback() {
    let (mut app, _root) = sprite_world();
    let world = app.world_mut();
    run(
        world,
        "first => sheet sprite\nsecond => sheet sprite\nfirst.play frame 0 to 3 in 1 seconds loop\nsecond.play frame 0 to 3 in 2 seconds loop",
    );
    let first = named(world, "first");
    let second = named(world, "second");
    sprite_tick(world, 0.6);
    let frame = |entity| {
        let mesh = world
            .resource::<Assets<Mesh>>()
            .get(&world.get::<SceneMaxSprite>(entity).unwrap().mesh)
            .unwrap();
        let VertexAttributeValues::Float32x2(uv) = mesh.attribute(Mesh::ATTRIBUTE_UV_0).unwrap()
        else {
            panic!()
        };
        uv[0][0]
    };
    assert_eq!(frame(first), 0.5);
    assert_eq!(frame(second), 0.25);
    run(world, "first => sheet sprite");
    let replacement = named(world, "first");
    assert_ne!(first, replacement);
    assert!(world.get_entity(first).is_err());
    assert!(world.get::<SceneMaxSpriteAnimation>(replacement).is_none());
    assert!(world.get::<SceneMaxSpriteAnimation>(second).is_some());
}

#[test]
fn sprite_source_pools_preserve_authored_physics_through_inactive_and_active_cycles() {
    let (mut app, _root) = sprite_world();
    let world = app.world_mut();
    run(
        world,
        "source => static (\"sheet\") sprite: collision shape box, hidden\nitems => Object.Pool(source, size 1)\nvar first = items.acquire",
    );
    let first = named(world, "first");
    assert!(world.get::<SceneMaxSprite>(first).is_some());
    assert_eq!(
        *world.get::<AvianRigidBody>(first).unwrap(),
        AvianRigidBody::Static
    );
    world
        .run_system_once(sync_collision_layers_for_visibility)
        .unwrap();
    assert!(world.get::<AvianCollider>(first).is_some());
    run(world, "items.release first");
    assert!(world.get::<AvianCollider>(first).is_none());
    run(world, "var reused = items.acquire");
    assert_eq!(named(world, "reused"), first);
    world
        .run_system_once(sync_collision_layers_for_visibility)
        .unwrap();
    assert!(world.get::<AvianCollider>(first).is_some());
}
