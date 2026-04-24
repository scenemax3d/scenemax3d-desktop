# Camera Systems

This document explains the runtime `camera.system` feature in SceneMax3D:

- what each camera system is for
- which game genres it fits best
- what the system is optimized for
- how to create and apply a system
- which parameters each system accepts
- what each parameter means

If you are looking for the older camera commands such as `Camera.chase`, `Camera.attach`, or cinematic rigs, see [camera.md](camera.md).

## Overview

A camera system is a reusable runtime camera behavior object. You create one, store it in a variable if you want, and then apply it to the main camera.

Basic pattern:

```scenemax
my_camera_system = camera.system.third_person(player1, distance 9, height 3)
camera.system = my_camera_system
```

Reset back to the default non-system camera behavior:

```scenemax
camera.system = default
```

Important runtime rule:

- only one camera system can be active at a time
- assigning a new camera system automatically turns the previous one off
- all systems are designed to move smoothly using damping/interpolation

## Camera Modifiers

In engine/runtime terms these are **camera modifiers** or **camera shake modifiers**.

In SceneMax syntax they are exposed as `camera.system.modifiers`.

They are designed to add short-lived procedural motion and noise on top of a running system camera so the camera feels:

- more reactive
- more physical
- less mechanically perfect
- more readable during strong gameplay events

### Core Behavior

- modifiers can be applied to the active system camera, and can also layer on top of cinematic playback
- modifiers automatically remove themselves when their duration ends
- multiple modifiers can be active at the same time
- modifiers are additive, so combining them is supported
- cinematic cameras still take over the base shot, but active modifiers can now layer on top of that cinematic playback

### Declaration

```scenemax
hit_fx = camera.system.modifiers.hit_modifier
quake_fx = camera.system.modifiers.earthquake_modifier
```

### Apply Syntax

```scenemax
fight_cam.apply hit_fx
```

With overrides:

```scenemax
fight_cam.apply hit_fx : duration 0.35, amplitude 1.4, rx 2, fov 0.8
```

Apply multiple modifiers over time:

```scenemax
fight_cam.apply hit_fx
fight_cam.apply bump_fx : duration 0.2, y 0.3
```

### Important Rule

The camera-system variable used in `.apply` must be the currently active system camera.

Example:

```scenemax
fight_cam = camera.system.fighting(player1, player2)
camera.system = fight_cam

hit_fx = camera.system.modifiers.hit_modifier
fight_cam.apply hit_fx
```

If `fight_cam` is not the active system camera, the modifier is rejected at runtime.

### Available Override Attributes

All modifiers support the same override keys:

#### `duration`

How long the modifier lives, in seconds.

This is the most common override.

#### `amplitude`

Overall strength multiplier for the effect.

- higher value: stronger shake
- lower value: softer shake

#### `frequency`

How fast the procedural oscillation/noise moves.

- higher value: tighter, buzzier shake
- lower value: heavier, slower shake

#### `x`

Position shake amount on the camera’s local X axis.

#### `y`

Position shake amount on the camera’s local Y axis.

#### `z`

Position shake amount on the camera’s local Z axis.

#### `rx`

Rotation shake amount around the camera’s local X axis, in degrees.

#### `ry`

Rotation shake amount around the camera’s local Y axis, in degrees.

#### `rz`

Rotation shake amount around the camera’s local Z axis, in degrees.

#### `fov`

Temporary FOV kick added by the modifier.

Useful for impact and explosive moments.

### Stacking Modifiers

Stacking does make sense in many cases, because gameplay events can overlap.

Good combinations:

- `hit_modifier` + `bump_modifier`
- `landing_modifier` + `decelerating_modifier`
- `explosion_modifier` + `earthquake_modifier`
- `shooting_modifier` repeatedly on top of a third-person or first-person system

Combinations that should be used carefully:

- too many high-amplitude modifiers at once can make the camera hard to read
- long `earthquake_modifier` plus strong `hit_modifier` values can become visually noisy

Practical advice:

- stack freely for short, event-driven effects
- keep `amplitude` moderate
- use `duration` to keep readability under control

## Built-In Modifiers

The following presets are currently implemented.

### `hit_modifier`

Best for:

- melee hits
- kicks
- punches
- weapon impacts

Optimized for:

- short sharp reaction
- a brief lateral/rotational jolt

Example:

```scenemax
hit_fx = camera.system.modifiers.hit_modifier
fight_cam.apply hit_fx : duration 0.25, amplitude 1.2
```

### `fall_modifier`

Best for:

- falling states
- losing footing
- sustained downward movement

Optimized for:

- softer downward wobble
- a less impact-heavy sensation than a hit

Example:

```scenemax
fall_fx = camera.system.modifiers.fall_modifier
platform_cam.apply fall_fx : duration 0.6
```

### `shooting_modifier`

Best for:

- recoil
- sustained fire
- forceful ranged attacks

Optimized for:

- short high-frequency recoil-style vibration

Example:

```scenemax
shoot_fx = camera.system.modifiers.shooting_modifier
fps_cam.apply shoot_fx : duration 0.15, amplitude 0.8
```

### `accelerating_modifier`

Best for:

- boosting
- rapid acceleration
- dash starts

Optimized for:

- a pull-back sensation
- speed buildup feel

Example:

```scenemax
accel_fx = camera.system.modifiers.accelerating_modifier
race_cam.apply accel_fx : duration 0.4, fov 1.5
```

### `decelerating_modifier`

Best for:

- braking
- sudden slowdown
- heavy stop events

Optimized for:

- a forward settling impulse
- weight transfer feeling

Example:

```scenemax
brake_fx = camera.system.modifiers.decelerating_modifier
race_cam.apply brake_fx : duration 0.3
```

### `bump_modifier`

Best for:

- wheel bumps
- body collisions
- small terrain impact

Optimized for:

- quick vertical impact
- short roughness spikes

Example:

```scenemax
bump_fx = camera.system.modifiers.bump_modifier
race_cam.apply bump_fx : y 0.3, duration 0.2
```

### `landing_modifier`

Best for:

- hitting the ground after a long jump
- hard landings
- stomp-like impacts

Optimized for:

- heavier downward impact than `bump_modifier`
- stronger landing emphasis

Example:

```scenemax
landing_fx = camera.system.modifiers.landing_modifier
platform_cam.apply landing_fx : duration 0.35, amplitude 1.3
```

### `earthquake_modifier`

Best for:

- earthquakes
- collapsing environments
- boss roar / arena tremor moments

Optimized for:

- long multi-axis rumble
- sustained instability

Example:

```scenemax
quake_fx = camera.system.modifiers.earthquake_modifier
strategy_cam.apply quake_fx : duration 2.5, amplitude 1.1
```

### `explosion_modifier`

Best for:

- explosions
- shockwaves
- large impact set pieces

Optimized for:

- shock plus rumble
- stronger FOV kick than small hit effects

Example:

```scenemax
explosion_fx = camera.system.modifiers.explosion_modifier
follow_cam.apply explosion_fx : duration 0.5, amplitude 1.4, fov 1.5
```

### `near_miss_modifier`

Best for:

- bullets or objects barely missing the player
- swipe attacks
- fast danger passing by the camera

Optimized for:

- sideways whip and tension
- short evasive visual reaction

Example:

```scenemax
miss_fx = camera.system.modifiers.near_miss_modifier
follow_cam.apply miss_fx : duration 0.2, ry 2
```

## Modifier Usage Guide

This section explains how to think about camera modifiers in production, how to choose the right one, and how to tune it safely.

### The Main Idea

A camera system defines the camera's long-running behavior.

Examples:

- `fighting` defines stable versus framing
- `third_person` defines trailing character follow behavior
- `racing` defines chase-camera speed framing

A camera modifier does something different.

It adds a temporary procedural reaction on top of the active system camera.

Examples:

- a punch lands
- the player fires a weapon
- the car hits a bump
- the player lands from a high jump
- the level starts shaking

So the simplest rule is:

- choose the camera system for the genre
- choose the modifier for the gameplay event

### When To Use Modifiers

Use a modifier when you want the player to feel:

- impact
- force
- instability
- momentum
- danger passing close by

Use modifiers when the base camera is already correct but the event needs stronger feedback.

Good examples by genre:

- fighting games: `hit_modifier`, `near_miss_modifier`, `earthquake_modifier`
- platformers: `fall_modifier`, `landing_modifier`, `bump_modifier`
- shooters: `shooting_modifier`, `explosion_modifier`, `near_miss_modifier`
- racers: `accelerating_modifier`, `decelerating_modifier`, `bump_modifier`
- RTS / tactics: `earthquake_modifier`, `explosion_modifier`

### When Not To Use Modifiers

Do not use a modifier for:

- permanent camera offsets
- normal follow behavior
- replacing the actual camera system
- authored cinematic movement

If you want a persistent viewpoint style, use a camera system.
If you want a directed shot, use a cinematic camera.

### Recommended Workflow

1. Build the correct system camera first.
2. Play the game without modifiers.
3. Add modifiers only to the events that feel flat or underpowered.
4. Start with only `duration` and `amplitude`.
5. Fine-tune `frequency` and axis values after the basic feel is correct.
6. Use `fov` only when the event should feel bigger, faster, or more dramatic.

### Attribute Meanings In Practice

All modifiers share the same override attributes, but each one changes a different part of the feel.

#### `duration`

Controls how long the modifier lives.

Use shorter `duration` for:

- recoil
- hits
- near misses
- small bumps

Use longer `duration` for:

- falling
- earthquakes
- heavy environmental events

If a modifier feels annoying rather than impactful, the first thing to reduce is often `duration`.

#### `amplitude`

Controls overall strength.

This is usually the first attribute to adjust when the modifier feels:

- too weak
- too strong

If the preset feels fundamentally correct but the size is wrong, adjust `amplitude` before changing many other values.

#### `frequency`

Controls how fast and tight the motion feels.

Use higher `frequency` for:

- recoil
- rapid gunfire
- sharp impacts

Use lower `frequency` for:

- earthquakes
- heavy falls
- big environmental rumble

Practical interpretation:

- high frequency feels nervous, snappy, and buzzy
- low frequency feels heavy, slow, and weighty

#### `x`

Controls positional shake on the camera's local X axis.

This mostly reads as sideways camera translation.

Good for:

- glancing hits
- side slap force
- near misses
- lateral instability

#### `y`

Controls positional shake on the camera's local Y axis.

This mostly reads as vertical camera translation.

Good for:

- bumps
- landings
- falling wobble
- ground contact force

#### `z`

Controls positional shake on the camera's local Z axis.

This mostly reads as forward/back translation.

Good for:

- recoil push/pull
- acceleration
- braking
- explosion pressure

#### `rx`

Controls rotational shake around the camera's local X axis.

This mostly reads as pitch.

Good for:

- recoil
- landing impact
- hit emphasis with up/down force

#### `ry`

Controls rotational shake around the camera's local Y axis.

This mostly reads as yaw.

Good for:

- near misses
- side hits
- danger whipping across the player

#### `rz`

Controls rotational shake around the camera's local Z axis.

This mostly reads as roll.

Good for:

- rough collisions
- chaotic instability
- body-shake style camera energy

Use `rz` carefully.
Too much roll can reduce gameplay clarity.

#### `fov`

Adds a temporary field-of-view kick.

Good for:

- acceleration
- explosions
- dramatic heavy hits
- spectacle moments

Use `fov` more carefully in:

- precision platforming
- aim-sensitive first-person gameplay
- any section where visual clarity matters more than drama

### How To Tune A Modifier

Recommended tuning order:

1. Pick the correct built-in modifier.
2. Set `duration`.
3. Set `amplitude`.
4. Adjust `frequency`.
5. Adjust axis values: `x`, `y`, `z`, `rx`, `ry`, `rz`.
6. Add `fov` only if the event should feel larger or faster.

Simple rule of thumb:

- `duration` changes how long the player feels the event
- `amplitude` changes how large the event feels
- `frequency` changes whether the event feels sharp or heavy
- axis values define directional personality

### Modifier Selection Guide

Use `hit_modifier` when:

- the event is a direct impact
- the response should be sharp and immediate

Use `fall_modifier` when:

- the player is in descent
- the feeling should be unstable rather than explosive

Use `shooting_modifier` when:

- the event is recoil-like
- repeated force pulses should feel tight and quick

Use `accelerating_modifier` when:

- speed is building
- the camera should feel pulled backward or widened

Use `decelerating_modifier` when:

- momentum is being killed
- the player should feel weight transfer and settling

Use `bump_modifier` when:

- the event is a quick rough impact
- it should feel smaller than a true landing

Use `landing_modifier` when:

- the player hits the ground hard
- the event should feel heavier than a bump

Use `earthquake_modifier` when:

- the world itself is unstable
- the event should last longer

Use `explosion_modifier` when:

- a blast wave or major shock should be felt
- you want both impact and residual rumble

Use `near_miss_modifier` when:

- danger passes close to the player or camera
- the feeling should be directional and tense

### Common Stacking Patterns

Stacking is supported and can make sense.

Good combinations:

- `hit_modifier` + `bump_modifier`
- `landing_modifier` + `decelerating_modifier`
- `explosion_modifier` + `earthquake_modifier`
- repeated `shooting_modifier` applications on top of `first_person` or `third_person`

Use restraint with:

- long `earthquake_modifier` plus strong `hit_modifier`
- very large roll-heavy combinations
- many simultaneous high-amplitude modifiers

If the image becomes hard to read, reduce:

- `duration`
- `amplitude`
- `rz`
- the number of overlapping modifiers

### Example Recipes

Light melee hit:

```scenemax
hit_fx = camera.system.modifiers.hit_modifier
fight_cam.apply hit_fx : duration 0.18, amplitude 0.9
```

Heavy fighting-game blow:

```scenemax
hit_fx = camera.system.modifiers.hit_modifier
fight_cam.apply hit_fx : duration 0.32, amplitude 1.5, rx 2.2, ry 2.6, fov 0.8
```

Racing bump:

```scenemax
bump_fx = camera.system.modifiers.bump_modifier
race_cam.apply bump_fx : duration 0.16, y 0.35, rz 0.9
```

Hard platform landing:

```scenemax
landing_fx = camera.system.modifiers.landing_modifier
platform_cam.apply landing_fx : duration 0.3, amplitude 1.25, y 0.4, rx 1.9
```

Explosion near the player:

```scenemax
explosion_fx = camera.system.modifiers.explosion_modifier
follow_cam.apply explosion_fx : duration 0.5, amplitude 1.35, z 0.28, fov 1.4
```

Long environmental rumble:

```scenemax
quake_fx = camera.system.modifiers.earthquake_modifier
strategy_cam.apply quake_fx : duration 2.8, amplitude 1.0, frequency 6
```

## General Usage

There are three shapes of camera-system declarations:

Dual-target system:

```scenemax
fight_cam = camera.system.fighting(player1, player2, ...)
```

Single-target system:

```scenemax
follow_cam = camera.system.third_person(player1, ...)
```

Zero-target or optional-target system:

```scenemax
strategy_cam = camera.system.rts(...)
focus_cam = camera.system.rts(commander, ...)
```

Then apply the system:

```scenemax
camera.system = follow_cam
```

## Shared Concepts

Many systems use the same parameter names. These names keep the same meaning across systems whenever they are available.

### `distance`

How far the camera stays from the target in the main follow direction.

- larger value: wider shot
- smaller value: tighter shot

### `depth`

An extra forward/back style offset used by systems that need it.

- in `fighting`, it behaves like the camera pull-back distance logic
- in `first_person`, it pushes the camera slightly forward from the target origin
- in `rts`, it contributes to the final world-space offset

### `height`

How high above the target or focus point the camera should be.

- larger value: camera is higher
- smaller value: camera is lower

### `side`

A sideways offset relative to the target.

- positive value: move camera to one side
- negative value: move camera to the opposite side

### `look ahead`

How much the camera aims ahead of the target or movement direction instead of looking exactly at the target center.

- useful for motion readability
- good for racers, platformers, and third-person movement

### `damping`

How quickly the camera catches up to the desired position/look-at.

- higher value: snappier response
- lower value: softer, floatier response

### `fov`

Base field of view in degrees.

- lower value: more zoomed in
- higher value: wider lens feel

### `max fov`

Upper limit for dynamic FOV expansion.

- mainly used by systems that widen the lens while the target moves faster or spreads farther apart

### `min distance` / `max distance`

Distance clamps used by systems that dynamically adapt zoom or chase distance.

- `min distance`: never go closer than this
- `max distance`: never go farther than this

### `zoom_factor`

How strongly dynamic spacing or speed affects distance/FOV logic.

- higher value: more aggressive zoom response
- lower value: steadier framing

### Bounds

Some systems accept world-space clamp values:

- `min x`
- `max x`
- `min y`
- `max y`
- `min z`
- `max z`

These limit where the camera itself can move in world space.

The fighting camera also supports arena-specific clamps:

- `arena min x`
- `arena max x`
- `arena min z`
- `arena max z`

These are specialized clamps for arena-style side-view combat spaces.

## Fighting Camera

### Best For

- fighting games
- arena duels
- versus combat
- boss fights where both attacker and defender must stay visible

### Optimized For

- keeping two characters visible at the same time
- preserving a stable side view
- adapting zoom based on fighter spacing
- gameplay readability over cinematic motion

### Declaration

```scenemax
fight_cam = camera.system.fighting(player1, player2, depth 18, height 5, side 1.5, min distance 12, max distance 28, damping 8, fov 48, max fov 62)
camera.system = fight_cam
```

### Required Arguments

- `player`
- `opponent`

### Available Parameters

#### `depth`

Controls how far the camera is pulled back from the midpoint of the fighters.

In practice, the system also adapts this using fighter separation and `zoom_factor`.

#### `height`

Raises the camera above the fighters.

Also slightly raises the look-at point so characters are framed more naturally.

#### `side`

Shifts the camera along the fighter axis.

Use this to bias the shot left/right without changing the stable side-view behavior.

#### `min distance`

Minimum camera pull-back distance.

Useful when fighters are very close and you still want enough framing room.

#### `max distance`

Maximum camera pull-back distance.

Useful to stop the camera from getting too wide when fighters move apart.

#### `zoom_factor`

Controls how strongly fighter spacing affects camera distance.

#### `damping`

Controls how quickly the camera follows the desired framing.

#### `look ahead`

Biases the look-at target along the line between fighters.

Useful when you want the framing to lean slightly toward the active side.

#### `fov`

Base fighting-camera field of view.

#### `max fov`

Maximum field of view used as the fighters spread apart.

#### `arena min x`, `arena max x`, `arena min z`, `arena max z`

Clamp the camera inside a combat arena.

Use these if your fight takes place in a known stage area and you do not want the camera drifting outside it.

### When To Use It

Use this when camera readability matters more than dramatic angle changes and both fighters must remain on screen almost all the time.

## Third-Person Camera

### Best For

- action-adventure games
- third-person character games
- exploration games
- over-the-shoulder gameplay

### Optimized For

- stable follow framing behind a character
- smooth movement with a natural trailing feel
- readable forward movement
- subtle FOV expansion when the target moves faster

### Declaration

```scenemax
follow_cam = camera.system.third_person(player1, distance 9, height 3, side 1, look ahead 2, damping 7, fov 55, max fov 62)
camera.system = follow_cam
```

### Required Arguments

- one target object

### Available Parameters

#### `distance`

Main follow distance behind the target.

#### `height`

Raises the camera above the target.

#### `side`

Offsets the camera sideways for centered or over-the-shoulder framing.

#### `look ahead`

Pushes the look-at point ahead of the target movement/forward direction.

Useful when you want the player to see more of where they are going.

#### `damping`

Controls how quickly the camera catches up.

#### `zoom_factor`

Controls how much movement speed affects the dynamic FOV widening.

#### `fov`

Base third-person field of view.

#### `max fov`

Maximum third-person field of view during movement.

#### `min x`, `max x`, `min y`, `max y`, `min z`, `max z`

World-space camera clamps.

### When To Use It

Use this for a general-purpose character-follow camera where the player should always feel attached to the character but still see the environment ahead.

## First-Person Camera

### Best For

- FPS games
- immersive first-person exploration
- cockpit-like character view
- first-person interaction games

### Optimized For

- attaching the camera close to the controlled character
- immediate viewpoint response
- direct aiming/looking
- a head-level perspective

### Declaration

```scenemax
fps_cam = camera.system.first_person(player1, height 1.7, depth 0.15, side 0.05, fov 72)
camera.system = fps_cam
```

### Required Arguments

- one target object

### Available Parameters

#### `height`

Vertical offset from the target origin.

This is usually the most important first-person parameter because it approximates eye level.

#### `depth`

Moves the camera slightly forward relative to the target.

Useful when the model origin is behind the desired eye position.

#### `side`

Small lateral offset.

Useful for stylized shoulder/helmet positioning.

#### `look ahead`

Extends the look direction forward.

Normally this can remain small because first-person already points directly ahead.

#### `fov`

Base first-person field of view.

#### `min x`, `max x`, `min y`, `max y`, `min z`, `max z`

Optional world-space camera clamps.

### When To Use It

Use this when the camera should feel like the player’s eyes rather than a trailing observer.

## Racing Camera

### Best For

- racing games
- driving games
- chase-camera vehicle play
- arcade driving

### Optimized For

- following a fast-moving vehicle from behind
- showing the road ahead
- widening the lens as speed increases
- preserving forward motion readability

### Declaration

```scenemax
race_cam = camera.system.racing(car1, distance 14, height 4, side 0, look ahead 8, zoom_factor 1.2, min distance 10, max distance 24, damping 7, fov 58, max fov 68)
camera.system = race_cam
```

### Required Arguments

- one target object

### Available Parameters

#### `distance`

Base chase distance behind the vehicle.

#### `height`

How high the camera sits above the vehicle.

#### `side`

Lateral offset from the vehicle centerline.

#### `look ahead`

Pushes the look-at point further down the road.

This is especially useful in high-speed gameplay.

#### `zoom_factor`

Controls how much motion increases chase distance and dynamic FOV.

#### `min distance`

Minimum chase distance.

#### `max distance`

Maximum chase distance.

#### `damping`

Controls how quickly the camera settles into the chase position.

#### `fov`

Base racing field of view.

#### `max fov`

Maximum racing field of view during high-speed motion.

### When To Use It

Use this when speed sensation and road visibility are more important than character framing.

## Platformer Camera

### Best For

- side-scrollers
- 3D platformers
- character-jumping games
- precision movement games

### Optimized For

- keeping the player readable during jumps and lateral movement
- reducing nervous camera jitter
- allowing the player some freedom inside a dead zone before the camera reacts
- biasing the frame upward for jump arcs and hazards

### Declaration

```scenemax
platform_cam = camera.system.platformer(player1, distance 10, height 3, side 0, look ahead 3, dead zone 2, vertical bias 2.5, damping 6, fov 52, max fov 58)
camera.system = platform_cam
```

### Required Arguments

- one target object

### Available Parameters

#### `distance`

How far the camera stays from the player.

#### `height`

How high the camera sits relative to the player.

#### `side`

Side offset for framing bias.

#### `look ahead`

Moves the camera’s focus forward in the movement direction.

#### `dead zone`

Amount of target movement allowed before the camera shifts its internal focus point.

- larger dead zone: calmer camera
- smaller dead zone: more reactive camera

#### `vertical bias`

Raises the look-at point to better frame jumps, platforms, and overhead hazards.

#### `damping`

Controls how softly the camera follows once the target exits the dead zone.

#### `zoom_factor`

Controls the amount of movement-based FOV change.

#### `fov`

Base platformer field of view.

#### `max fov`

Maximum platformer field of view during movement.

### When To Use It

Use this when movement precision matters and you want the camera to help instead of constantly reacting to every tiny step.

## RTS Camera

### Best For

- real-time strategy games
- management games
- tactics games
- top-down builder or commander views

### Optimized For

- top-down or angled overview framing
- strategic readability over immersion
- large-area visibility
- stable bounded map coverage

### Declarations

Free overview camera:

```scenemax
strategy_cam = camera.system.rts(distance 30, angle 60, height 4, depth 0, damping 5, fov 50, min x -50, max x 50, min z -50, max z 50)
camera.system = strategy_cam
```

Target-focused overview camera:

```scenemax
focus_cam = camera.system.rts(commander, distance 28, angle 58)
camera.system = focus_cam
```

### Required Arguments

- no target is required
- optionally, one target object can be provided as the focus anchor

### Available Parameters

#### `distance`

Main horizontal stand-off distance from the focus point.

#### `angle`

Pitch-style angle in degrees used to place the camera above the focus point.

- lower angle: flatter view
- higher angle: steeper top-down view

#### `height`

Additional vertical lift on top of the `angle` placement.

#### `depth`

Additional forward/back world-space component in the final camera offset.

#### `vertical bias`

Raises the focus point the camera looks at.

#### `damping`

Controls how quickly the camera settles to its strategic position.

#### `fov`

Base RTS field of view.

#### `min x`, `max x`, `min y`, `max y`, `min z`, `max z`

World-space camera bounds.

These are especially important for RTS cameras because they let you keep the camera inside the playable map.

### When To Use It

Use this when the player needs tactical awareness of an area rather than an immersive close-up character view.

## Naming Note

The preferred term is now **modifier**.

Preferred syntax:

```scenemax
hit_fx = camera.system.modifiers.hit_modifier
```

## Choosing The Right Camera System

Use `fighting` when:

- two opponents must remain visible
- left/right combat readability matters
- arena framing is more important than exploration

Use `third_person` when:

- one character is the focus
- movement through the environment matters
- you want a general-purpose follow camera

Use `first_person` when:

- the camera should feel attached to the player’s eyes
- aiming and direct viewpoint matter most

Use `racing` when:

- speed and forward visibility matter most
- you want dynamic chase distance and dynamic FOV

Use `platformer` when:

- jump readability matters
- you want a calmer camera with a dead zone

Use `rts` when:

- the player needs map awareness
- the camera should stay high, angled, and bounded

## Typical Workflow

1. Create the game objects first.
2. Create the system value that matches your genre.
3. Tune `distance`, `height`, `fov`, and `damping` first.
4. Add genre-specific parameters like `dead zone`, `angle`, `look ahead`, or arena bounds.
5. Apply the system with `camera.system = your_system`.
6. Reset with `camera.system = default` when needed.

Example:

```scenemax
player1=>sinbad

adventure_cam = camera.system.third_person(
    player1,
    distance 9,
    height 3,
    side 1,
    look ahead 2,
    damping 7,
    fov 55,
    max fov 62
)

camera.system = adventure_cam
```

## Notes

- Camera systems are runtime behaviors, not designer-authored cinematic rigs.
- For authored cutscenes and rails, use `cinematic.camera.<runtime_id>` instead.
- If a system feels too stiff, lower `damping`.
- If a system feels too lazy, raise `damping`.
- If framing is too tight, raise `distance`, `depth`, or `fov` depending on the system.
- If framing is too wide, lower those values.
