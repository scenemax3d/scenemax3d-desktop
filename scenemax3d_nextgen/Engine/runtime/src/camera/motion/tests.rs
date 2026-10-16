use super::*;
use bevy::ecs::system::RunSystemOnce;
use crate::value_calls::tests::{run_in, resume_next, ContinuousDelta};

fn setup() -> (World, Entity) {
    let mut world = World::new();
    let camera = world.spawn((Camera3d::default(), Transform::default())).id();
    world.init_resource::<SceneMaxStartupProgram>();
    (world, camera)
}
fn run(world: &mut World, source: &str) {
    run_in(world, source);
    for _ in 0..32 {
        if world.resource::<DelayedActionQueue>().actions.is_empty() { return; }
        resume_next(world);
    }
    panic!("script did not complete");
}
fn tick(world: &mut World, seconds: f32) {
    let mut time = Time::<()>::default();
    time.advance_by(std::time::Duration::from_secs_f32(seconds));
    world.insert_resource(time);
    world.run_system_once(update_timed_moves).unwrap();
    world.run_system_once(update_timed_turns).unwrap();
}
fn position(world: &World, entity: Entity, expected: Vec3) {
    let actual = world.get::<Transform>(entity).unwrap().translation;
    assert!(actual.abs_diff_eq(expected, 0.0002), "{actual:?} != {expected:?}");
}
fn rotation(world: &World, entity: Entity, expected: Vec3) {
    let actual = world.get::<Transform>(entity).unwrap().rotation;
    let expected = camera_rotation_from_degrees(SceneMaxVec3 { x:expected.x, y:expected.y, z:expected.z });
    assert!(actual.dot(expected).abs() > 0.99999, "{actual:?} != {expected:?}");
}
#[test]
fn camera_evaluated_poses_and_value_functions_run_once() {
    let (mut world, camera) = setup();
    run(&mut world, "var calls = 0\nvalue(n) = {\ncalls = calls + 1\nreturn n\n}\ncamera.pos(value(2),value(3),value(4))\ncamera.rotate(value(10),value(20),value(30))");
    position(&world, camera, Vec3::new(2.,3.,4.));
    rotation(&world, camera, Vec3::new(10.,20.,30.));
    assert_eq!(world.resource::<SceneMaxVars>().0["calls"], 6.);
}
#[test]
fn camera_relative_and_absolute_rotation_use_camera_angles() {
    for axis in ["x", "y", "z"] {
        let (mut world, camera) = setup();
        run(&mut world, &format!("camera.rotate(10,20,30)\ncamera.rotate({axis}+20) in 2 seconds async"));
        tick(&mut world, 1.);
        let mut expected = Vec3::new(10.,20.,30.);
        expected[match axis { "x" => 0, "y" => 1, _ => 2 }] += 10.;
        rotation(&world, camera, expected);
        tick(&mut world, 1.);
        run(&mut world, &format!("camera.rotate to ({axis} 5) in 1 seconds async"));
        tick(&mut world, 1.);
        expected[match axis { "x" => 0, "y" => 1, _ => 2 }] = 5.;
        rotation(&world, camera, expected);
        assert!(world.get::<TimedTurns>(camera).is_none());
    }
}
#[test]
fn camera_directional_motion_uses_view_and_projected_axes() {
    for (word, direction) in [("forward", MoveDirection::Forward),("backward", MoveDirection::Backward),("left", MoveDirection::Left),("right", MoveDirection::Right),("up", MoveDirection::Up),("down", MoveDirection::Down)] {
        let (mut world, camera) = setup();
        run(&mut world, &format!("camera.rotate(30,45,10)\ncamera.move {word} 4 in 2 seconds async"));
        let original = *world.get::<Transform>(camera).unwrap();
        tick(&mut world, 2.);
        position(&world, camera, camera_direction(direction, &original) * 4.);
    }
}
#[test]
fn camera_coordinate_relative_and_entity_destinations() {
    let (mut world, camera) = setup();
    run(&mut world, "camera.pos(1,2,3)\ncamera.move to (5,6,7) in 2 seconds async");
    tick(&mut world, 1.); position(&world, camera, Vec3::new(3.,4.,5.));
    tick(&mut world, 1.); position(&world, camera, Vec3::new(5.,6.,7.));
    run(&mut world, "camera.move by (2,-1,3) in 1 seconds async");
    tick(&mut world, 1.); position(&world, camera, Vec3::new(7.,5.,10.));
    run(&mut world, "destination => box: pos (9,8,7)\ncamera.move to (destination) in 1 seconds async");
    tick(&mut world, 1.); position(&world, camera, Vec3::new(9.,8.,7.));
}
#[test]
fn camera_easing_is_integrated_for_move_destination_and_rotation() {
    for command in ["camera.move(x+8)", "camera.move to (8,0,0)"] {
        let (mut world, camera) = setup();
        run(&mut world, &format!("{command} in 2 seconds ease in async"));
        tick(&mut world, 1.); position(&world, camera, Vec3::new(2.,0.,0.));
        tick(&mut world, 1.); position(&world, camera, Vec3::new(8.,0.,0.));
    }
    let (mut world, camera) = setup();
    run(&mut world, "camera.rotate(0,0,0)\ncamera.rotate(y+80) in 2 seconds ease in async");
    tick(&mut world, 1.); rotation(&world, camera, Vec3::new(0.,20.,0.));
    tick(&mut world, 1.); rotation(&world, camera, Vec3::new(0.,80.,0.));
}
#[test]
fn camera_loop_move_and_rotation_observe_guard_changes() {
    let (mut world, camera) = setup();
    run(&mut world, "var active = 1\ncamera.rotate(0,0,0)\ncamera.move(x+2) in 1 seconds loop while active == 1\ncamera.rotate(y+10) in 1 seconds loop while active == 1");
    tick(&mut world, 1.); tick(&mut world, 1.);
    position(&world, camera, Vec3::new(4.,0.,0.)); rotation(&world, camera, Vec3::new(0.,20.,0.));
    world.resource_mut::<SceneMaxVars>().0.insert("active".into(), 0.);
    tick(&mut world, 1.);
    assert!(world.get::<TimedMoves>(camera).is_none()); assert!(world.get::<TimedTurns>(camera).is_none());
}
#[test]
fn camera_held_axis_direction_and_turn_do_not_accumulate_controllers() {
    for (command, expected) in [("camera.move(x+4) in 2 seconds", Vec3::X), ("camera.move forward 4 in 2 seconds", -Vec3::Z)] {
        let (mut world, camera) = setup();
        world.insert_resource(ContinuousDelta(0.5));
        run(&mut world, command);
        position(&world, camera, expected);
        assert!(world.get::<TimedMoves>(camera).is_none());
    }
    let (mut world, camera) = setup();
    run(&mut world, "camera.rotate(0,0,0)");
    world.insert_resource(ContinuousDelta(0.5));
    run(&mut world, "camera.rotate(y+40) in 2 seconds");
    rotation(&world, camera, Vec3::new(0.,10.,0.));
    assert!(world.get::<TimedTurns>(camera).is_none());
}
#[test]
fn camera_concurrent_move_and_turn_lifetimes_are_independent() {
    let (mut world, camera) = setup();
    run(&mut world, "camera.rotate(0,0,0)\ncamera.move(x+2) in 1 seconds async\ncamera.move(y+8) in 2 seconds async\ncamera.rotate(y+40) in 2 seconds async");
    tick(&mut world, 1.); position(&world, camera, Vec3::new(2.,4.,0.));
    assert_eq!(world.get::<TimedMoves>(camera).unwrap().moves.len(), 1);
    rotation(&world, camera, Vec3::new(0.,20.,0.));
    tick(&mut world, 1.); position(&world, camera, Vec3::new(2.,8.,0.));
    assert!(world.get::<TimedMoves>(camera).is_none());
}
#[test]
fn camera_blocking_and_async_preserve_script_continuation() {
    for async_run in [false,true] {
        let (mut world, camera) = setup();
        run_in(&mut world, &format!("camera.move to (4,0,0) in 2 seconds {}\nvar finished = 1", if async_run { "async" } else { "" }));
        assert_eq!(world.resource::<SceneMaxVars>().0.contains_key("finished"), async_run);
        assert!(world.get::<TimedMoves>(camera).is_some());
        if !async_run {
            assert_eq!(world.resource::<DelayedActionQueue>().actions[0].remaining_seconds, 2.);
            tick(&mut world, 2.); resume_next(&mut world);
            assert_eq!(world.resource::<SceneMaxVars>().0["finished"], 1.);
        }
    }
}

#[test]
fn camera_look_at_and_scene_cleanup_preserve_scene_object_controllers() {
    let (mut world, camera) = setup();
    run(&mut world, "destination => box: pos (2,0,0)\ncamera.pos(2,0,10)\ncamera.look at destination");
    assert!(world.get::<Transform>(camera).unwrap().forward().abs_diff_eq(-Vec3::Z, 0.0001));
    run(&mut world, "camera.move(x+4) in 10 seconds async\ncamera.rotate(y+60) in 10 seconds async\ndestination.move(y+4) in 10 seconds async");
    world.run_system_once(|mut commands: Commands| clear(&mut commands)).unwrap();
    assert!(world.get::<TimedMoves>(camera).is_none());
    assert!(world.get::<TimedTurns>(camera).is_none());
    assert_eq!(world.query::<&TimedMoves>().iter(&world).count(), 1);
}
