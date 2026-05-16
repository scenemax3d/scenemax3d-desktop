# Throw Motion Designer

Throw motion assets describe reusable movement paths for thrown or launched objects. They are stored as `.smmotion` JSON files, authored in the Throw Motion Designer, loaded through the project asset index, and used at runtime with `system.motion(...)`.

Typical use cases:

- thrown weapons, such as axes, knives, spears, or boomerangs
- temporary projectile visuals
- scripted launch movement for any game object
- moving an equipped weapon without knowing its generated runtime id

The throw motion system moves the target object along the authored path. It does not rotate the object and it does not perform collision handling. Use the object's own collider, weapon colliders, animation events, or regular collision commands for gameplay hits.

## File Placement

Throw motion definitions use the `.smmotion` extension.

There are two copies involved while authoring:

- the designer document, which can live anywhere in the project's files tree
- the runtime resource, which is exported on Save under `resources/throw_motions`

Runtime only loads the resource copy under the project's resources folder. Script-folder `.smmotion` files are source documents, not runtime resources.

Examples:

```text
scripts/Fighting Game/game_level_train/axe_throw.smmotion
resources/throw_motions/motion_axe_throw.smmotion
```

When you click Save in the designer, SceneMax saves the source document in place and creates or updates:

```text
resources/throw_motions/<motion_id>.smmotion
```

SceneMax also removes duplicate throw motion resources with the same id from the runtime throw motion folders, so only one resource with that id remains available to the game.

Runtime throw motions are indexed by both:

- the `id` field inside the `.smmotion` file
- the file name without `.smmotion`

If the file is named `axe_throw.smmotion` and the id is `motion_axe_throw`, scripts can normally use the id:

```scenemax
motion = system.motion("motion_axe_throw")
```

## Creating a Throw Motion Asset

### From the Main Menu

Use:

```text
Assets > Create Motion
```

SceneMax asks for the motion name and starter template. The new `.smmotion` file is created under the project root.

### From a Folder Context Menu

Right-click a folder in the project tree and choose:

```text
Create Throw Motion
```

The new `.smmotion` file is created inside that folder.

### Motion ID

The designer generates a stable id from the display name. For example:

```text
Axe Throw -> motion_axe_throw
```

Prefer lowercase ids with underscores. The id is what scripts should pass to `system.motion`.

## Designer Layout

The Throw Motion Designer has two main areas:

- the authoring panel, with Overview and Motion fields
- the preview panel, which shows the path and moves a simple sphere along it

The preview sphere is only a visual marker. Runtime applies the path to your actual object.

### Overview Fields

`Motion ID`
: Stable runtime id, such as `motion_axe_throw`.

`Name`
: Human-readable display name.

`Motion Type`
: The movement family. Each type exposes different parameters.

### Preview Controls

`Play`
: Plays the current path preview.

`Step`
: Advances the preview by one frame.

`Reset`
: Resets playback to the start.

`Reset View`
: Refits the camera around the preview path.

`Trajectory`
: Shows or hides the path line.

`Samples`
: Shows or hides sample points along the path.

`Target Distance`
: Preview-only distance used to show where the motion is aimed.

`Target Height`
: Preview-only height offset used to show an elevated or lowered target.

These preview target controls do not save runtime target data. Runtime target direction is supplied by `system.motion(...)`.

## Runtime Mental Model

Throw motion has two parts:

1. Create a motion instance.
2. Apply that instance to an object or equipped weapon.

```scenemax
motion = system.motion("motion_axe_throw", target enemy)
motion.apply axe async
```

The asset controls the shape and timing of the path. The runtime `target` controls the direction for this particular throw.

If no `target` is supplied, the motion uses the applied object's current forward direction.

`target` is not an exact landing command. It calculates the forward direction of the motion instance. Distance and timing come from the `.smmotion` asset, such as `duration`, `maxDistance`, `outboundDistance`, or `maxLifetime`.

## Runtime Syntax

### Create a Motion Instance

Create a motion from an asset id:

```scenemax
motion = system.motion("motion_axe_throw")
```

Create from a variable:

```scenemax
motion_id = "motion_axe_throw"
motion = system.motion(motion_id)
```

Declare the variable explicitly:

```scenemax
var motion = system.motion("motion_axe_throw")
```

Assign later:

```scenemax
motion = system.motion("motion_axe_throw")
```

### Target Option

`target` is optional. It is used only to calculate the direction of the motion when `apply` starts.

Target a game object:

```scenemax
enemy => fighter2 : pos (8, 0, 12)
motion = system.motion("motion_axe_throw", target enemy)
```

Target a 3D position:

```scenemax
motion = system.motion("motion_axe_throw", target (10, 2, 30))
```

The equals sign is optional:

```scenemax
motion = system.motion("motion_axe_throw", target = enemy)
motion = system.motion("motion_axe_throw", target = (10, 2, 30))
```

Without a target:

```scenemax
motion = system.motion("motion_axe_throw")
```

In this case the applied object's forward direction is used.

### Apply to a Regular Object

Apply to any runtime object:

```scenemax
axe => meshy_axe : pos (0, 1, 0)
motion = system.motion("motion_axe_throw", target enemy)
motion.apply axe
```

This is blocking by default. The next command runs only after the motion finishes:

```scenemax
motion.apply axe
Logger.info "axe motion finished"
```

### Apply Asynchronously

Use `async` to continue immediately:

```scenemax
motion.apply axe async
Logger.info "axe is already moving"
```

This matches the behavior of other timed movement commands.

### Apply to the Equipped Weapon

When a character has a weapon equipped, prefer:

```scenemax
motion.apply player1.weapon
```

This resolves the weapon through the character equipment system instead of requiring the generated runtime weapon id.

Full example:

```scenemax
player1 => fighter1 : pos (4, -3, 0)
enemy => fighter2 : pos (8, -3, 12)

player1.weapon = "weapon_player_weapon"
player1.weapon.posture = "fight"

when key Q is pressed once do
  player1.weapon.detach
  motion = system.motion("motion_axe_throw", target enemy)
  motion.apply player1.weapon async
end do
```

This is useful when several players use the same weapon asset:

```scenemax
player1.weapon = "weapon_player_weapon"
player2.weapon = "weapon_player_weapon"

throw1 = system.motion("motion_axe_throw", target enemy1)
throw2 = system.motion("motion_axe_throw", target enemy2)

throw1.apply player1.weapon async
throw2.apply player2.weapon async
```

## Motion Types

The designer exposes the supported authoring templates below. Each type stores all parameters in JSON, but only the relevant authoring fields are shown in the designer.

### Target Arc Throw

Best for authored, predictable throws that should reach a target direction in a fixed time.

Visible designer fields:

`Duration`
: Total motion duration in seconds.

`Arc Height`
: Extra height added at the middle of the path.

`Easing`
: Path progress curve. Supported values are `linear`, `ease_in`, `ease_out`, and `ease_in_out`.

`Align To Path`
: Saved in the asset for compatibility. The current runtime does not rotate the object.

Example:

```json
{
  "type": "SceneMaxThrowMotionDefinition",
  "schemaVersion": "1.0",
  "id": "motion_axe_throw",
  "displayName": "Axe Throw",
  "motionType": "target_arc",
  "parameters": {
    "duration": 0.75,
    "arcHeight": 2.0,
    "easingFunction": "ease_out",
    "alignToPath": false
  },
  "designerMetadata": {}
}
```

Runtime sample:

```scenemax
motion = system.motion("motion_axe_throw", target enemy)
motion.apply player1.weapon async
```

### Ballistic Throw

Best for gravity-shaped throws. The path starts with an initial velocity and falls under gravity.

Visible designer fields:

`Initial Speed`
: Launch speed.

`Launch Angle`
: Upward launch angle in degrees.

`Gravity Scale`
: Multiplier for gravity. `1.0` is normal gravity, lower values float more, higher values fall faster.

`Max Distance`
: Saved in the asset. Current ballistic sampling is time-based and mainly uses `Max Lifetime`.

`Max Lifetime`
: Maximum simulation time in seconds.

`Align To Velocity`
: Saved in the asset for compatibility. The current runtime does not rotate the object.

Example:

```json
{
  "type": "SceneMaxThrowMotionDefinition",
  "schemaVersion": "1.0",
  "id": "motion_grenade_lob",
  "displayName": "Grenade Lob",
  "motionType": "ballistic",
  "parameters": {
    "initialSpeed": 18.0,
    "launchAngle": 42.0,
    "gravityScale": 1.0,
    "maxDistance": 30.0,
    "maxLifetime": 2.5,
    "alignToVelocity": false
  },
  "designerMetadata": {}
}
```

Runtime sample:

```scenemax
grenade => grenade_model : pos (0, 1, 0)
motion = system.motion("motion_grenade_lob", target (12, 0, 18))
motion.apply grenade async
```

### Straight Projectile

Best for darts, bullets, energy bolts, or any motion that travels forward with optional acceleration.

Visible designer fields:

`Speed`
: Starting speed.

`Acceleration`
: Speed change per second. Positive values accelerate, negative values slow down.

`Max Distance`
: Maximum travel distance.

`Max Lifetime`
: Maximum lifetime in seconds.

`Align To Velocity`
: Saved in the asset for compatibility. The current runtime does not rotate the object.

Example:

```json
{
  "type": "SceneMaxThrowMotionDefinition",
  "schemaVersion": "1.0",
  "id": "motion_arrow_shot",
  "displayName": "Arrow Shot",
  "motionType": "straight",
  "parameters": {
    "speed": 32.0,
    "acceleration": 0.0,
    "maxDistance": 45.0,
    "maxLifetime": 3.0,
    "alignToVelocity": false
  },
  "designerMetadata": {}
}
```

Runtime sample:

```scenemax
arrow => arrow_model : pos (0, 1, 0)
shot = system.motion("motion_arrow_shot", target enemy)
shot.apply arrow async
```

### Homing Throw

Best for curved projectiles that steer toward the supplied runtime target.

Visible designer fields:

`Speed`
: Travel speed.

`Acceleration`
: Saved in the asset. Current homing sampling uses constant speed.

`Turn Rate`
: Maximum steering rate in degrees per second.

`Homing Delay`
: Seconds before steering begins.

`Homing Strength`
: Steering blend multiplier.

`Lose Target`
: Saved in the asset for compatibility.

`Max Lifetime`
: Maximum lifetime in seconds.

Example:

```json
{
  "type": "SceneMaxThrowMotionDefinition",
  "schemaVersion": "1.0",
  "id": "motion_magic_dagger",
  "displayName": "Magic Dagger",
  "motionType": "homing",
  "parameters": {
    "speed": 16.0,
    "acceleration": 0.0,
    "turnRate": 240.0,
    "homingDelay": 0.15,
    "homingStrength": 1.0,
    "loseTargetBehavior": "continue",
    "maxLifetime": 3.0
  },
  "designerMetadata": {}
}
```

Runtime sample:

```scenemax
dagger => dagger_model : pos (0, 1, 0)
motion = system.motion("motion_magic_dagger", target enemy)
motion.apply dagger async
```

### Returning Throw

Best for boomerangs or thrown weapons that go out and return to their start point.

Visible designer fields:

`Outbound Duration`
: Time for the outgoing segment.

`Outbound Distance`
: Forward distance for the outgoing segment.

`Outbound Arc Height`
: Extra height added at the middle of the outgoing segment.

`Return Speed`
: Speed of the return segment.

`Return Delay`
: Pause before returning.

`Can Hit On Return`
: Saved in the asset for compatibility. Collision is handled separately by colliders.

`Catch Radius`
: Saved in the asset for compatibility.

`Easing`
: Path progress curve for outgoing and return segments.

Example:

```json
{
  "type": "SceneMaxThrowMotionDefinition",
  "schemaVersion": "1.0",
  "id": "motion_boomerang",
  "displayName": "Boomerang",
  "motionType": "returning",
  "parameters": {
    "outboundDuration": 0.7,
    "outboundDistance": 14.0,
    "outboundArcHeight": 1.2,
    "returnSpeed": 20.0,
    "returnDelay": 0.1,
    "canHitOnReturn": true,
    "catchRadius": 0.75,
    "easingFunction": "ease_in_out"
  },
  "designerMetadata": {}
}
```

Runtime sample:

```scenemax
boomerang => boomerang_model : pos (0, 1, 0)
motion = system.motion("motion_boomerang", target enemy)
motion.apply boomerang
Logger.info "boomerang returned"
```

### Physics Throw

Best as a physics-flavored template. In the current runtime, physics throw sampling follows the ballistic sampler, while the physics-specific values are saved for compatibility and future physics integration.

Visible designer fields:

`Initial Speed`
: Launch speed.

`Upward Angle`
: Upward launch angle in degrees.

`Force Mode`
: Saved in the asset. Common value: `impulse`.

`Mass Override`
: Saved in the asset for compatibility.

`Drag`
: Saved in the asset for compatibility.

`Gravity Scale`
: Gravity multiplier.

`Max Lifetime`
: Maximum simulation time in seconds.

Example:

```json
{
  "type": "SceneMaxThrowMotionDefinition",
  "schemaVersion": "1.0",
  "id": "motion_physics_rock",
  "displayName": "Physics Rock",
  "motionType": "physics",
  "parameters": {
    "initialSpeed": 14.0,
    "launchAngle": 35.0,
    "forceMode": "impulse",
    "massOverride": 0.0,
    "drag": 0.0,
    "gravityScale": 1.0,
    "maxLifetime": 3.0
  },
  "designerMetadata": {}
}
```

Runtime sample:

```scenemax
rock => rock_model : pos (0, 1, 0)
motion = system.motion("motion_physics_rock", target enemy)
motion.apply rock async
```

### Custom Curve

`custom_curve` is reserved for compatibility with old files and future curve editing. It is not exposed in the current designer, and new assets should use one of the visible templates above.

## Complete JSON Shape

A full `.smmotion` file may contain these fields:

```json
{
  "type": "SceneMaxThrowMotionDefinition",
  "schemaVersion": "1.0",
  "id": "motion_example",
  "displayName": "Example Throw Motion",
  "motionType": "target_arc",
  "parameters": {
    "initialSpeed": 16.0,
    "launchAngle": 35.0,
    "gravityScale": 1.0,
    "duration": 1.2,
    "arcHeight": 3.0,
    "arcMode": "relative_height",
    "easingFunction": "ease_in_out",
    "speed": 18.0,
    "acceleration": 0.0,
    "maxDistance": 30.0,
    "maxLifetime": 4.0,
    "alignToVelocity": true,
    "alignToPath": true,
    "spinSpeed": 720.0,
    "collisionMode": "spherecast",
    "collisionRadius": 0.25,
    "stopOnImpact": true,
    "targetMode": "point",
    "turnRate": 180.0,
    "maxTurnAngle": 90.0,
    "homingDelay": 0.15,
    "homingStrength": 1.0,
    "loseTargetBehavior": "continue",
    "outboundDuration": 0.75,
    "outboundDistance": 12.0,
    "outboundArcHeight": 1.0,
    "returnSpeed": 18.0,
    "returnDelay": 0.15,
    "canHitOnReturn": true,
    "ownerTarget": "thrower",
    "catchRadius": 0.75,
    "forceMode": "impulse",
    "massOverride": 0.0,
    "drag": 0.0,
    "impactBehavior": "stop",
    "switchToPhysicsOnImpact": false
  },
  "designerMetadata": {}
}
```

Some fields remain in the schema for forward compatibility even though the current designer does not expose them and the current runtime does not use them for gameplay. In particular, collision, impact, spin, and automatic rotation are intentionally not handled by throw motion runtime.

## Common Patterns

### Throw Equipped Weapon From an Animation Event

```scenemax
player1 => fighter1 : pos (4, -3, 0)
enemy => fighter2 : pos (9, -3, 12)

player1.weapon = "weapon_player_weapon"
player1.weapon.posture = "fight"

when key Q is pressed once do
  anim = animation player1.zombie_punch1
  anim.event("zombie_punch1", 30) = {
    player1.weapon.detach
    throw_motion = system.motion("motion_axe_throw", target enemy)
    throw_motion.apply player1.weapon async
  }
  anim.run
end do
```

### Throw Toward the Cursor or a Calculated Point

Use a position expression when you already know the target position:

```scenemax
target_x = 10
target_y = 1
target_z = 22

motion = system.motion("motion_axe_throw", target (target_x, target_y, target_z))
motion.apply player1.weapon async
```

### Sequential Throws

Without `async`, each throw waits for the previous one:

```scenemax
throw1 = system.motion("motion_axe_throw", target enemy1)
throw1.apply axe1

throw2 = system.motion("motion_axe_throw", target enemy2)
throw2.apply axe2
```

### Parallel Throws

With `async`, the motions run together:

```scenemax
throw1 = system.motion("motion_axe_throw", target enemy1)
throw2 = system.motion("motion_axe_throw", target enemy2)

throw1.apply axe1 async
throw2.apply axe2 async
```

### Collision Handling With the Weapon Collider

Throw motion only moves the object. Let the weapon collider handle hits:

```scenemax
player1.weapon = "weapon_player_weapon"

when player1.weapon.colliders["axe_upper_collider"] collides with enemy do
  enemy.data.hit = 1
end do

when key Q is pressed once do
  player1.weapon.detach
  motion = system.motion("motion_axe_throw", target enemy)
  motion.apply player1.weapon async
end do
```

Use the collider name defined by the weapon asset. The owner-based form resolves the correct runtime collider for that player's equipped weapon, so the same weapon asset can be reused across multiple players without hard-coding generated collider ids.

## Runtime Notes

- `system.motion` returns a motion instance value. It does not move anything by itself.
- `motion.apply object` starts movement on the selected object.
- `motion.apply player.weapon` starts movement on the currently equipped weapon for that character.
- `target` is stored on the motion instance and resolved when `apply` starts.
- For object targets, the runtime uses the target object's world position at apply time.
- For position targets, the runtime evaluates the supplied `(x, y, z)` expressions at apply time.
- If no target is supplied, the applied object's forward direction is used.
- The start point is always the applied object's world position at the moment `apply` starts.
- Throw motion moves the object by translation only.
- Throw motion does not rotate, spin, collide, damage, destroy, stick, or bounce the object.
- `async` makes `apply` non-blocking.

## Troubleshooting

### The Motion Asset Is Not Found

Check:

- the `.smmotion` file exists in one of the indexed folders
- the id in the file matches the string passed to `system.motion`
- the project was refreshed after creating the asset

Example:

```scenemax
motion = system.motion("motion_axe_throw")
```

The file should contain:

```json
{
  "id": "motion_axe_throw"
}
```

### The Equipped Weapon Is Not Found

`motion.apply player1.weapon` requires that the player currently has a weapon equipped:

```scenemax
player1.weapon = "weapon_player_weapon"
motion = system.motion("motion_axe_throw")
motion.apply player1.weapon async
```

If the weapon was unequipped or never equipped, runtime reports that the owner has no equipped weapon.

### The Weapon Is Still Attached to the Hand

Detach it before applying the throw motion:

```scenemax
player1.weapon.detach
motion = system.motion("motion_axe_throw", target enemy)
motion.apply player1.weapon async
```

### The Object Moves but Does Not Rotate

This is expected. Throw motion only translates the object. Add your own animation, model setup, shader effect, or rotation command if visual spin is needed.

```scenemax
motion.apply axe async
axe.turn 720 in 0.75 seconds async
```

### The Hit Does Not Register

Throw motion does not perform collision checks. Add a collider and a collision command:

```scenemax
when player1.weapon.colliders["axe_upper_collider"] collides with enemy do
  enemy.data.health = enemy.data.health - 10
end do
```
