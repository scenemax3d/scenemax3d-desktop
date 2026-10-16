//! Input acceptance uses the real dispatcher and continuation scheduler.
use super::*;
use bevy::ecs::system::RunSystemOnce;

#[test]
fn recurring_entity_parameters_drive_spatial_queries_and_animation() {
    let source = r#"
var measured = -1
act(a,b) = {
 measured = distance(a,b)
 b.look at (a)
 b.strike
 finishes = finishes + 1
}
run act(first,second) every 0.5 seconds
"#;
    let mut world = world(source);
    crate::value_calls::tests::run_in(&mut world, "var measured = -1");
    world.init_resource::<RecurringRunTimers>();
    for (name, x) in [("first", 0.), ("second", 12.)] {
        let reference = scenemax_runtime_vm_core::references::Reference::entity(name);
        scenemax_runtime_vm_core::references::assign(name, reference.clone(),
            &mut world.resource_mut::<SceneMaxVars>(), None, false).unwrap();
        world.resource_mut::<SceneMaxObjectPools>().entity_values.2.insert(name.into(), reference);
        world.spawn((SceneMaxEntity { name: name.into(), runtime_name: format!("{name}@1") },
            Transform::from_xyz(x, 0., 0.), SceneMaxGltf { gltf: Handle::default(), animation_names: vec!["strike".into()] }));
    }
    delta(&mut world, 0.5);
    world.run_system_once(update_recurring_runs).unwrap();
    assert_eq!(world.resource::<SceneMaxVars>().0["measured"], 12.);
    assert_eq!(world.query::<&AnimationToPlay>().iter(&world).count(), 1);
    let queue = world.resource::<DelayedActionQueue>();
    assert_eq!(queue.actions[0].animation_wait.as_ref().unwrap().target, "second");
    let entity = world.query::<(Entity, &SceneMaxEntity)>().iter(&world)
        .find(|(_, entity)| entity.name == "second").unwrap().0;
    assert!(horizontal_forward(world.get::<Transform>(entity).unwrap()).dot(Vec3::NEG_X) > 0.999);
    world.entity_mut(entity).insert(CurrentAnimation {
        rewind: None, identity: None, controller_owner: None, network_lifetime: None,
        playback_graphs: Vec::new(), clip: "strike".into(), looped: false,
        speed: 1., elapsed_seconds: 0.1, duration_seconds: 0.2,
    });
    resume(&mut world, 0.1);
    assert_eq!(world.resource::<SceneMaxVars>().0["finishes"], 0.);
    world.get_mut::<CurrentAnimation>(entity).unwrap().elapsed_seconds = 0.2;
    resume(&mut world, 0.1);
    assert_eq!(world.resource::<SceneMaxVars>().0["finishes"], 1.);
}

pub(super) fn world(source: &str) -> World {
    let mut world = World::new();
    crate::value_calls::tests::run_in(&mut world, "var starts = 0\nvar finishes = 0\nvar other = 0");
    world.insert_resource(SceneMaxStartupProgram(Some(scenemax_parser::parse_program(source).unwrap()), None));
    world.init_resource::<ButtonInput<KeyCode>>();
    world.init_resource::<SceneMaxAnimationDurations>();
    world.init_resource::<SceneMaxCameraSystem>();
    world.init_resource::<SceneMaxUiActionQueue>();
    world.init_resource::<ActiveActionControllers>();
    delta(&mut world, 0.1);
    world
}

fn delta(world: &mut World, seconds: f32) {
    let mut time = Time::<()>::default();
    time.advance_by(std::time::Duration::from_secs_f32(seconds));
    world.insert_resource(time);
}

pub(super) fn input(world: &mut World) {
    world.run_system_once(apply_key_events).unwrap();
    world.resource_mut::<ButtonInput<KeyCode>>().clear();
}

fn resume(world: &mut World, seconds: f32) {
    delta(world, seconds);
    world.run_system_once(update_delayed_actions).unwrap();
}

#[test]
fn scoped_key_handlers_keep_parameters_guards_and_independent_continuations() {
    for trigger in ["pressed once", "pressed", "released"] {
        let source = format!(r#"
var distance = 99
var enabled = 0
first => box
second => box
bind(target,distance) = {{
 var enabled = 1
 #[enabled == 1]
 when key e is {trigger} do
  target.move(x+distance) in 1 seconds
  finishes = finishes + 1
 end do
}}
run bind(first,2)
run bind(second,3)
"#);
        let mut world = world(&source);
        crate::value_calls::tests::run_in(&mut world,&source);
        while !world.resource::<DelayedActionQueue>().actions.is_empty() { crate::value_calls::tests::resume_next(&mut world); }
        assert_eq!(world.resource::<DelayedActionQueue>().registered_key_events.events.len(),2);
        world.resource_mut::<ButtonInput<KeyCode>>().press(KeyCode::KeyE);
        if trigger == "released" { world.resource_mut::<ButtonInput<KeyCode>>().release(KeyCode::KeyE); }
        input(&mut world);
        if trigger != "pressed" {
            delta(&mut world,1.);
            world.run_system_once(update_timed_moves).unwrap();
            resume(&mut world,1.);
        }
        let positions = world.query::<(&SceneMaxEntity,&Transform)>().iter(&world).map(|(s,t)| (s.name.clone(),t.translation.x)).collect::<HashMap<_,_>>();
        let scale = if trigger == "pressed" { 0.1 } else { 1. };
        assert!((positions["first"]-2.*scale).abs()<0.001);
        assert!((positions["second"]-3.*scale).abs()<0.001);
        assert_eq!(world.resource::<SceneMaxVars>().0["finishes"],2.);
        assert_eq!(world.resource::<SceneMaxVars>().0["enabled"],0.);
    }
}

#[test]
fn scoped_recurring_calls_keep_distinct_intervals_and_entity_arguments() {
    let source = r#"
first => box
second => box
tick(target,amount) = {
 target.pos = (target.x+amount,0,0)
}
bind(target,amount,period) = {
 run tick(target,amount) every period seconds
}
run bind(first,2,1)
run bind(second,3,2)
"#;
    let mut world=world(source);
    world.init_resource::<RecurringRunTimers>();
    crate::value_calls::tests::run_in(&mut world,source);
    while !world.resource::<DelayedActionQueue>().actions.is_empty() { crate::value_calls::tests::resume_next(&mut world); }
    assert_eq!(world.resource::<DelayedActionQueue>().registered_run_every.events.len(),2);
    for expected in [[2.,0.],[4.,3.]] {
        delta(&mut world,1.);
        world.run_system_once(update_recurring_runs).unwrap();
        let positions = world.query::<(&SceneMaxEntity,&Transform)>().iter(&world).map(|(s,t)| (s.name.clone(),t.translation.x)).collect::<HashMap<_,_>>();
        assert_eq!([positions["first"],positions["second"]],expected);
    }
}

#[test]
fn entity_stop_releases_key_continuations_and_allows_new_motion() {
    let source="when key a is pressed once do\nsubject.move(x+20) in 10 seconds\nfinishes = finishes + 1\nend do\nwhen key e is pressed once do\nsubject.rotate(y+180) in 10 seconds\nother = other + 1\nend do";
    let mut world=world(source);
    crate::value_calls::tests::run_in(&mut world,"subject => box");
    for key in [KeyCode::KeyA,KeyCode::KeyE] { world.resource_mut::<ButtonInput<KeyCode>>().press(key); input(&mut world); }
    delta(&mut world,1.);
    world.run_system_once(update_timed_moves).unwrap();
    world.run_system_once(update_timed_turns).unwrap();
    crate::value_calls::tests::run_in(&mut world,"var alias = subject\nhalt(target) = {\n target.stop\n}\nrun halt(alias)");
    resume(&mut world,0.1);
    assert_eq!(world.resource::<SceneMaxVars>().0["finishes"],1.);
    assert_eq!(world.resource::<SceneMaxVars>().0["other"],1.);
    let entity=world.query::<(Entity,&SceneMaxEntity)>().iter(&world).find(|(_,s)|s.name=="subject").unwrap().0;
    assert!(world.get::<TimedMoves>(entity).is_none());
    assert!(world.get::<TimedTurns>(entity).is_none());
    assert!((world.get::<Transform>(entity).unwrap().translation.x-2.).abs()<0.001);
    crate::value_calls::tests::run_in(&mut world,"subject.move(x+1) in 1 seconds async");
    delta(&mut world,1.);
    world.run_system_once(update_timed_moves).unwrap();
    assert!((world.get::<Transform>(entity).unwrap().translation.x-3.).abs()<0.001);
}

#[test]
fn entity_stop_covers_relative_absolute_and_character_motion_without_stopping_destinations() {
    for action in ["subject.move(x+8) in 4 seconds async", "subject.move forward 8 in 4 seconds async",
        "subject.rotate(x+90) in 4 seconds async", "subject.rotate to (y=90) in 4 seconds async"] {
        let mut world=world("");
        crate::value_calls::tests::run_in(&mut world,&format!("subject => box\n{action}\nsubject.stop"));
        while !world.resource::<DelayedActionQueue>().actions.is_empty() { crate::value_calls::tests::resume_next(&mut world); }
        let entity=world.query::<(Entity,&SceneMaxEntity)>().iter(&world).find(|(_,s)|s.name=="subject").unwrap().0;
        assert!(world.get::<TimedMoves>(entity).is_none(),"{action}");
        assert!(world.get::<TimedTurns>(entity).is_none(),"{action}");
    }
    let mut world=world("");
    crate::value_calls::tests::run_in(&mut world,"subject => box\nsubject.move to (4,0,0) in 4 seconds async\nsubject.move(y+8) in 4 seconds async\nsubject.stop");
    while !world.resource::<DelayedActionQueue>().actions.is_empty() { crate::value_calls::tests::resume_next(&mut world); }
    let entity=world.query::<(Entity,&SceneMaxEntity)>().iter(&world).find(|(_,s)|s.name=="subject").unwrap().0;
    assert_eq!(world.get::<TimedMoves>(entity).unwrap().moves.len(),1);
    delta(&mut world,4.);
    world.run_system_once(update_timed_moves).unwrap();
    assert_eq!(world.get::<Transform>(entity).unwrap().translation,Vec3::new(4.,0.,0.));
}

#[test]
fn stopping_retired_alias_does_not_stop_a_replacement_entity() {
    let mut world=world("");
    crate::value_calls::tests::run_in(&mut world,"subject => box\nvar retired = subject\nsubject => sphere\nsubject.move(x+4) in 4 seconds async\nretired.stop");
    while !world.resource::<DelayedActionQueue>().actions.is_empty() { crate::value_calls::tests::resume_next(&mut world); }
    let moves=world.query::<&TimedMoves>().iter(&world).count();
    assert_eq!(moves,1);
}

#[test]
fn returned_factory_key_handler_can_suspend_and_stop_does_not_skip_explicit_wait() {
    let source=r#"
make() = {
 item => box
 when key e is pressed once do
  item.move(x+1) in 0.2 seconds
  finishes = finishes + 1
 end do
 return item
}

var actor = make()
when key q is pressed once do
 actor.move(y+5) in 5 seconds async
 wait 2 seconds
 other = other + 1
end do
"#;
    let mut world=world(source);
    crate::value_calls::tests::run_in(&mut world,source);
    while !world.resource::<DelayedActionQueue>().actions.is_empty() { crate::value_calls::tests::resume_next(&mut world); }
    world.resource_mut::<DelayedActionQueue>().registered_key_events.events.retain(|e| e.scope.is_some());
    for key in [KeyCode::KeyE,KeyCode::KeyQ] { world.resource_mut::<ButtonInput<KeyCode>>().press(key); input(&mut world); }
    crate::value_calls::tests::run_in(&mut world,"actor.stop");
    resume(&mut world,0.1);
    assert_eq!(world.resource::<SceneMaxVars>().0["finishes"],1.);
    assert_eq!(world.resource::<SceneMaxVars>().0["other"],0.);
    resume(&mut world,2.);
    assert_eq!(world.resource::<SceneMaxVars>().0["other"],1.);
}

#[test]
fn independent_keys_complete_overlapping_timed_actions_and_tails() {
    let mut world = world("when key a is pressed once do\nsubject.move(x+2) in 1 seconds\nfinishes = finishes + 1\nend do\nwhen key e is pressed once do\nsubject.rotate(y+90) in 2 seconds\nother = other + 1\nend do");
    crate::value_calls::tests::run_in(&mut world, "subject => box");
    let entity = world.query::<EntityRef>().iter(&world).find(|e| e.contains::<SceneMaxEntity>()).unwrap().id();
    world.resource_mut::<ButtonInput<KeyCode>>().press(KeyCode::KeyA);
    input(&mut world);
    world.resource_mut::<ButtonInput<KeyCode>>().press(KeyCode::KeyE);
    input(&mut world);
    assert_eq!(world.resource::<ActiveActionControllers>().running.len(), 2);
    delta(&mut world, 1.);
    world.run_system_once(update_timed_moves).unwrap();
    world.run_system_once(update_timed_turns).unwrap();
    resume(&mut world, 1.);
    assert_eq!(world.resource::<SceneMaxVars>().0["finishes"], 1.);
    assert_eq!(world.resource::<SceneMaxVars>().0["other"], 0.);
    world.run_system_once(update_timed_turns).unwrap();
    resume(&mut world, 1.);
    assert_eq!(world.resource::<SceneMaxVars>().0["other"], 1.);
    let transform = world.get::<Transform>(entity).unwrap();
    assert!(transform.translation.abs_diff_eq(Vec3::X * 2., 0.001));
    assert!(transform.rotation.abs_diff_eq(Quat::from_rotation_y(90_f32.to_radians()), 0.001));
    assert!(world.resource::<ActiveActionControllers>().running.is_empty());
}

#[test]
fn held_and_released_handlers_do_not_reenter_until_their_tail_finishes() {
    for trigger in ["pressed", "released"] {
        for registered in [false, true] {
            let source = format!("when key r is {trigger} do\nstarts = starts + 1\nwait 1 seconds\nfinishes = finishes + 1\nend do");
            let mut world = world(&source);
            if registered {
                let program = world.resource_mut::<SceneMaxStartupProgram>().0.take().unwrap();
                let Statement::KeyEvent(event) = program.statements[0].clone() else { panic!() };
                register_key_event(&mut world.resource_mut::<DelayedActionQueue>().registered_key_events, event, None);
                world.insert_resource(SceneMaxStartupProgram(Some(Program::new(vec![])), None));
            }
            for _ in 0..5 {
                world.resource_mut::<ButtonInput<KeyCode>>().press(KeyCode::KeyR);
                if trigger == "released" { world.resource_mut::<ButtonInput<KeyCode>>().release(KeyCode::KeyR); }
                input(&mut world);
            }
            assert_eq!(world.resource::<SceneMaxVars>().0["starts"], 1.);
            assert_eq!(world.resource::<DelayedActionQueue>().actions.len(), 1);
            world.resource_mut::<ButtonInput<KeyCode>>().reset_all();
            resume(&mut world, 1.1);
            assert_eq!(world.resource::<SceneMaxVars>().0["finishes"], 1.);
            assert!(world.resource::<ActiveActionControllers>().running.is_empty());
            world.resource_mut::<ButtonInput<KeyCode>>().press(KeyCode::KeyR);
            if trigger == "released" { world.resource_mut::<ButtonInput<KeyCode>>().release(KeyCode::KeyR); }
            input(&mut world);
            assert_eq!(world.resource::<SceneMaxVars>().0["starts"], 2.);
        }
    }
}

#[test]
fn held_motion_remains_per_frame_and_focus_suppresses_new_gameplay() {
    let mut world = world("when key w is pressed do\ncamera.move(x+4) in 2 seconds\nstarts = starts + 1\nend do");
    let camera = world.spawn((Camera3d::default(), Transform::default())).id();
    world.resource_mut::<ButtonInput<KeyCode>>().press(KeyCode::KeyW);
    for _ in 0..5 { input(&mut world); }
    assert_eq!(world.resource::<SceneMaxVars>().0["starts"], 5.);
    assert!(world.get::<Transform>(camera).unwrap().translation.abs_diff_eq(Vec3::X, 0.001));
    assert!(world.get::<TimedMoves>(camera).is_none());
    world.resource_mut::<SceneMaxUiActionQueue>().input_focused = true;
    input(&mut world);
    assert_eq!(world.resource::<SceneMaxVars>().0["starts"], 5.);
    assert!(world.resource::<DelayedActionQueue>().actions.is_empty());
}

#[test]
fn all_reference_gameplay_keys_dispatch_and_wait_for_key_resumes() {
    let names = ('a'..='z').chain('0'..='9').map(|c| c.to_string())
        .chain(["up", "down", "left", "right", "space", "enter", "del"].map(str::to_owned));
    for name in names {
        let mut world = world(&format!("when key {name} is pressed once do\nstarts = starts + 1\nwait for key {name} to be pressed\nfinishes = finishes + 1\nend do"));
        let code = key_code_from_scenemax(&name).unwrap();
        assert_eq!(key_code_from_scenemax(&name.to_uppercase()), Some(code));
        world.resource_mut::<ButtonInput<KeyCode>>().press(code);
        input(&mut world);
        assert_eq!(world.resource::<SceneMaxVars>().0["finishes"], 0., "{name}");
        world.resource_mut::<ButtonInput<KeyCode>>().release(code);
        resume(&mut world, 0.1);
        assert_eq!(world.resource::<SceneMaxVars>().0["finishes"], 0., "{name}");
        world.resource_mut::<ButtonInput<KeyCode>>().press(code);
        resume(&mut world, 0.1);
        assert_eq!(world.resource::<SceneMaxVars>().0["starts"], 1., "{name}");
        assert_eq!(world.resource::<SceneMaxVars>().0["finishes"], 1., "{name}");
    }
    assert_eq!(key_code_from_scenemax("unknown"), None);
}

#[test]
fn function_registered_collision_callbacks_keep_independent_values_entities_and_delayed_tails() {
    let mut world=world("var dummy = 0");
    world.init_resource::<ActiveCollisionEvents>();world.init_resource::<SceneMaxPhysicsContacts>();
    crate::value_calls::tests::run_in(&mut world,r#"
var total = 0
anchor => box
setup(amount) = {
 probe => box
 when probe collides with anchor do
  wait 0.1 seconds
  total = total + amount
 end do
}
run setup(3)
run setup(7)
"#);
    for _ in 0..4 {if world.resource::<DelayedActionQueue>().actions.is_empty(){break;} crate::value_calls::tests::resume_next(&mut world);}
    assert_eq!(world.resource::<DelayedActionQueue>().registered_when_events.events.len(),2);
    world.run_system_once(apply_when_events).unwrap();resume(&mut world,0.2);
    world.run_system_once(apply_when_events).unwrap();resume(&mut world,0.2);
    assert_eq!(world.resource::<SceneMaxVars>().0["total"],10.);
}

#[test]
fn returned_factories_retain_collision_registrations_and_delayed_callback_scopes() {
    let mut world=world("");
    world.init_resource::<ActiveCollisionEvents>();
    world.init_resource::<SceneMaxPhysicsContacts>();
    crate::value_calls::tests::run_in(&mut world,r#"
var total = 0
anchor => box
make(amount) = {
 probe => box
 when probe collides with anchor do
  wait 0.1 seconds
  total = total + amount
 end do
 return probe
}
var first = make(3)
var second = make(7)
"#);
    while !world.resource::<DelayedActionQueue>().actions.is_empty() { crate::value_calls::tests::resume_next(&mut world); }
    assert_eq!(world.resource::<DelayedActionQueue>().registered_when_events.events.len(),2);
    world.run_system_once(apply_when_events).unwrap();
    resume(&mut world,0.2);
    world.run_system_once(apply_when_events).unwrap();
    resume(&mut world,0.2);
    assert_eq!(world.resource::<SceneMaxVars>().0["total"],10.);
}


#[test]
fn pooled_recurring_position_commands_retain_resolved_coordinates() {
    let source = r#"
make() = {
 item => box: multiplayer
 item.data.ticks = 0
 return item
}
tick(target,amount) = {
 if (target.data.ticks < 3) {
  target.pos = (target.x,target.y,target.z+amount)
  target.data.ticks = target.data.ticks + 1
 }
}
schedule(target,amount,period) = {
 run tick(target,amount) every period seconds
}
items => Object.Pool(make, size 1)
var first = items.acquire
run schedule(first,0.2,1)
"#;
    let mut world=world(source);
    world.init_resource::<RecurringRunTimers>();
    crate::value_calls::tests::run_in(&mut world,source);
    while !world.resource::<DelayedActionQueue>().actions.is_empty() { crate::value_calls::tests::resume_next(&mut world); }
    for expected in [0.2,0.4,0.6] {
        delta(&mut world,1.);
        world.run_system_once(update_recurring_runs).unwrap();
        let position=world.query::<(&SceneMaxEntity,&Transform)>().iter(&world)
            .find(|(s,_)|s.name.starts_with("__scenemax_instance")).unwrap().1.translation;
        assert!((position.z-expected).abs()<0.001,"{position:?} expected {expected}");
    }
}

#[test]
fn lobby_arrow_handlers_update_selected_row() {
    let source = r#"
var row = 0 [0..1];
when key down is pressed once do
 row = row + 1
 UI.layer1.rooms.selected = row
end do
when key up is pressed once do
 row = row - 1
 UI.layer1.rooms.selected = row
end do
"#;
    let mut world = world(source);
    crate::value_calls::tests::run_in(&mut world, r#"
var sessions = ["First", "Second"]
show = {
 var rows = ""
 for (var j=0;j<sessions.Length;j=j+1) {
  rows = rows + sessions[j] + "|0;"
 }
 UI.layer1.rooms.rows = rows
}
run show
var row = 0 [0..1];
"#);
    while !world.resource::<DelayedActionQueue>().actions.is_empty() { crate::value_calls::tests::resume_next(&mut world); }
    world.resource_mut::<ButtonInput<KeyCode>>().press(KeyCode::ArrowDown);
    input(&mut world);
    assert_eq!(world.resource::<SceneMaxVars>().0["row"], 1.);
    let queue = world.resource::<SceneMaxUiActionQueue>();
    assert!(matches!(queue.actions.last(), Some(SceneMaxUiAction::SetProperty { value, .. }) if value == "1"), "{:?}", queue.actions);
}
