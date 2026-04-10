# Camera

## Rotate Camera

Set the camera to a fixed rotation (X, Y, Z angles in degrees):

```scenemax
camera.rotate(0,180,45)
```

## Turn Camera

Rotate the camera left over time:

```scenemax
Camera.turn left 360 in 10 seconds
```

## Chase Mode

Make the camera follow an object:

```scenemax
Camera.chase s
```

Follow from behind (trailing mode):

```scenemax
Camera.chase s : trailing = true
```

Set initial vertical rotation angle:

```scenemax
Camera.chase s : vertical rotation = 15
```

Set initial horizontal rotation angle:

```scenemax
Camera.chase s : horizontal rotation = 15
```

Set maximum follow distance:

```scenemax
Camera.chase s : max distance = 40
```

Set minimum follow distance:

```scenemax
Camera.chase s : min distance = 10
```

## Attach Mode

Attach the camera directly to an object:

```scenemax
Camera.attach to s
```

Attach with a position offset:

```scenemax
Camera.attach to s : pos (1,2,-3)
```

Attach with X-axis rotation:

```scenemax
Camera.attach to s : rotation (-15,0,0)
```

Attach to object `s` and rotate to face object `m`:

```scenemax
Camera.attach to s : rotation (m)
```

## Cinematic Camera

The cinematic camera lets you author a reusable camera rig in the UI Designer and trigger it at run-time from SceneMax code.

The workflow is:

1. Create a `Cinematic Rig` in the designer.
2. Add one or more rails to the rig.
3. Pick ranges on those rails and add them to the cinematic sequence.
4. Give the rig a `Runtime ID`.
5. Trigger that rig from code with `cinematic.camera.<runtime_id>`.

Unlike a regular chase or attach camera, a cinematic camera replays an authored multi-segment motion path.

### Designer Concepts

A cinematic rig contains:

- A transformable parent rig node.
- One or more rails.
- A sequence of segments built from rail ranges.
- Optional look-at target settings used for preview and as authored reference data.

Each sequence segment stores:

- The source rail.
- Start anchor.
- End anchor.
- Segment weight.

The rig also stores sequence-level settings such as:

- Preview duration.
- Ease-in.
- Ease-out.
- Look-at target and target offset.
- Runtime ID.

### Runtime Declaration

Declare a cinematic camera instance from a designer-authored rig:

```scenemax
intro_cam=>cinematic.camera.stadium_intro
```

`stadium_intro` is the rig `Runtime ID` from the designer.

### Runtime Playback

Play the cinematic camera by providing:

- `target`
- `duration`

Example:

```scenemax
intro_cam.play : target (player1 forward 1 up 3), duration 10
```

This means:

- use the authored cinematic rig `stadium_intro`
- anchor the rig relative to `player1`
- keep the authored rig-to-target relationship during playback
- play the entire cinematic sequence in `10` seconds

### Important Runtime Rule

The cinematic `play` command no longer uses `pos(...)`.

The rig position is derived from:

- the designer-authored relationship between the cinematic rig and its target
- the run-time `target` statement you pass into `play`

This makes run-time playback match design-time framing more accurately, especially when reusing the same cinematic around different objects or in different scenes.

### Target Forms

The `target` attribute supports:

- a game object name
- a full position statement

Simple object target:

```scenemax
intro_cam.play : target player1, duration 8
```

Target with a relative position statement:

```scenemax
intro_cam.play : target (player1 forward 1 up 2), duration 8
```

This is useful when you want the cinematic to be framed relative to the moving character instead of targeting the character origin directly.

### Moving Targets

During playback, the engine keeps the rig-to-target relationship alive while the target is moving.

That means the system recalculates the rig placement continuously, so the authored cinematic remains attached to the moving target instead of only snapping once at the beginning.

This is especially useful for:

- boss introductions
- sports replay cameras
- cutscenes around walking characters
- dramatic orbit shots around moving vehicles

### Duration And Segment Weights

At run-time, `duration` is the total time in seconds for the entire cinematic sequence.

Example:

```scenemax
intro_cam.play : target player1, duration 6
```

If the rig contains multiple segments, their authored weights decide how that total time is distributed.

For example, if the sequence contains:

- segment A with weight `45`
- segment B with weight `35`

Then SceneMax splits the total duration proportionally:

- A gets `45 / 80`
- B gets `35 / 80`

So with `duration 8`, segment A gets `4.5` seconds and segment B gets `3.5` seconds.

### Ease-In And Ease-Out

The cinematic rig supports sequence-level easing:

- `Ease In` affects only the beginning of the first segment
- `Ease Out` affects only the ending of the last segment

Middle segments keep their authored path without extra sequence entrance/exit easing.

### Authoring Tips

- Use multiple rails to build more expressive composite motion.
- Keep the rig target set in the designer while authoring, so the preview camera shows the intended framing.
- Use segment weights to shape pacing between wide establishing motion and tighter closing motion.
- Use track-level preview to fine-tune a single segment range before testing the whole sequence.
- Use rig-level preview duration to match the intended run-time duration.

### Common Use Cases

#### Character Intro

Orbit around the hero and settle into a final framed shot:

```scenemax
hero_intro=>cinematic.camera.hero_intro
hero_intro.play : target (player1 forward 1 up 2), duration 5
```

Use this when gameplay pauses briefly and you want a polished hero reveal.

#### Sports Broadcast Fly-In

Use a multi-rail stadium camera that starts high and ends close to the field:

```scenemax
match_cam=>cinematic.camera.stadium_flyover
match_cam.play : target (ball forward 0 up 1), duration 7
```

This works well for:

- match introductions
- replay transitions
- halftime presentation shots

#### Boss Entrance

Keep the cinematic attached to a moving enemy while it enters the arena:

```scenemax
boss_cam=>cinematic.camera.boss_arrival
boss_cam.play : target (boss forward 2 up 4), duration 6
```

Because the rig follows the moving target relationship during playback, the shot remains usable even if the boss is animated or moving forward.

#### Triggered In An Event

You can create and play the cinematic inside a code block:

```scenemax
when key space Is pressed once do
    intro_cam=>cinematic.camera.cinematic1
    intro_cam.play : target (player1 forward 1 up 3), duration 8
end do
```

This is useful for:

- debug testing
- interaction-triggered cutscenes
- scripted encounter starts

#### Reuse The Same Rig For Different Actors

Because the cinematic is anchored from the target statement at run-time, the same rig can often be reused for different actors:

```scenemax
cam_a=>cinematic.camera.close_orbit
cam_a.play : target (player1 forward 1 up 2), duration 4

cam_b=>cinematic.camera.close_orbit
cam_b.play : target (player2 forward 1 up 2), duration 4
```

This is one of the main advantages of the target-driven cinematic model.

### Troubleshooting

If a cinematic does not play as expected, check the following:

- The designer rig has a valid `Runtime ID`.
- The requested runtime ID exists in one of the project designer scenes.
- The rig has at least one sequence segment.
- The run-time target object exists when playback begins.
- The target offset in the designer is reasonable for the character or object height.

### Full Example

```scenemax
// Create the player
player1=>sinbad

// Trigger a cinematic when the player presses space
when key space Is pressed once do
    intro_cam=>cinematic.camera.cinematic1
    intro_cam.play : target (player1 forward 1 up 3), duration 8
end do
```

This gives you a reusable designer-authored cinematic path that can be replayed around the player with consistent framing.

## Camera Systems

You can create a reusable camera system value and then apply it to the scene camera:

```scenemax
fight_cam = camera.system.fighting(player1, player2, depth 18, height 5, side 1.5, min distance 12, max distance 28, damping 8)
camera.system = fight_cam
```

Only one camera system can be active on a camera at a time. Applying a new one automatically turns the previous system off.

Reset the camera back to its default manual behavior with:

```scenemax
camera.system = default
```

### Fighting Camera

The fighting camera keeps two targets framed together and smoothly adjusts position and FOV while preserving a stable side view.

Required arguments:

- `player`
- `opponent`

Supported runtime options:

- `depth`
- `height`
- `side`
- `min distance`
- `max distance`
- `zoom_factor`
- `damping`
- `look ahead`
- `fov`
- `max fov`
- `arena min x`
- `arena max x`
- `arena min z`
- `arena max z`

### Third-Person Camera

```scenemax
follow_cam = camera.system.third_person(player1, distance 9, height 3, side 1, look ahead 2, damping 7, fov 55, max fov 62)
camera.system = follow_cam
```

Useful options:

- `distance`
- `height`
- `side`
- `look ahead`
- `damping`
- `fov`
- `max fov`
- `min x` / `max x` / `min y` / `max y` / `min z` / `max z`

### First-Person Camera

```scenemax
fps_cam = camera.system.first_person(player1, height 1.7, depth 0.15, side 0.05, fov 72)
camera.system = fps_cam
```

Useful options:

- `height`
- `depth`
- `side`
- `look ahead`
- `fov`
- bounds options

### Racing Camera

```scenemax
race_cam = camera.system.racing(car1, distance 14, height 4, side 0, look ahead 8, zoom_factor 1.2, fov 58, max fov 68)
camera.system = race_cam
```

Useful options:

- `distance`
- `height`
- `side`
- `look ahead`
- `zoom_factor`
- `min distance`
- `max distance`
- `damping`
- `fov`
- `max fov`

### Platformer Camera

```scenemax
platform_cam = camera.system.platformer(player1, distance 10, height 3, look ahead 3, dead zone 2, vertical bias 2.5, damping 6)
camera.system = platform_cam
```

Useful options:

- `distance`
- `height`
- `side`
- `look ahead`
- `dead zone`
- `vertical bias`
- `damping`
- `fov`
- `max fov`

### RTS Camera

```scenemax
strategy_cam = camera.system.rts(distance 30, angle 60, height 4, depth 0, damping 5, fov 50, min x -50, max x 50, min z -50, max z 50)
camera.system = strategy_cam
```

You can also anchor it to a target at runtime:

```scenemax
focus_cam = camera.system.rts(commander, distance 28, angle 58)
camera.system = focus_cam
```

Useful options:

- `distance`
- `angle`
- `height`
- `depth`
- `damping`
- `fov`
- bounds options
