# Effects

SceneMax supports two effect families:

- built-in effects such as `explosion`, `flame`, and `TimeOrbit`
- imported Effekseer effects that are created in the Effekseer designer and played at runtime

This page focuses on the new Effekseer runtime flow and includes complete examples.

## Quick Start

1. Import or create an Effekseer effect in the SceneMax Effekseer designer.
2. Make sure the effect is stored under `resources/effects/<assetId>/`.
3. Declare an effect object in code with `effects.effekseer.<assetId>`.
4. Position it, optionally aim or attach it, then call `.play`.

Minimal example:

```scenemax
fire_burst => effects.effekseer.fire_burst
fire_burst.play pos (0,0,0)
```

## Declaring An Effekseer Effect

Declare an effect object the same way you declare a model, sprite, box, or sphere:

```scenemax
my_effect => effects.effekseer.my_effect_name
```

You can also give it initial placement data:

```scenemax
portal_fx => effects.effekseer.portal_ring : pos (0,1,0) and rotate (0,180,0)
```

What this gives you:

- a real SceneMax runtime object with its own node
- support for `show`, `hide`, `delete`, `pos`, `look at`, `attach to`, and `detach from parent`
- automatic cleanup when switching scenes
- automatic packaging when the effect is used in the program

## Playing An Effect

### Play At A Fixed Position

```scenemax
impact_fx => effects.effekseer.impact_small
impact_fx.play pos (3,1,7)
```

### Play At Another Entity

The `pos (...)` target can be explicit axes or another entity name:

```scenemax
player_hit_fx => effects.effekseer.hit_flash
player_hit_fx.play pos (player)
```

### Start From A Predefined Placement

You can also define the effect's base transform when you create it, then play it later:

```scenemax
fire_fx => effects.effekseer.fire_column : pos (0,1,0) and rotate (0,45,0)
fire_fx.play pos (enemy)
```

## Runtime Attributes

Use the `attr = [ ... ]` list on `.play` to pass supported Effekseer runtime inputs.

Example:

```scenemax
missile_fx => effects.effekseer.missile_trail
missile_fx.play pos (missile), attr = ["play_back_speed" 1.2, "homing force" 0.9]
```

Supported attribute names:

| Attribute | Meaning |
| --- | --- |
| `"play_back_speed"` | Playback speed multiplier |
| `"playback_speed"` | Same as `play_back_speed` |
| `"speed"` | Same as `play_back_speed` |
| `"input0"` | Effekseer dynamic input channel 0 |
| `"input1"` | Effekseer dynamic input channel 1 |
| `"input2"` | Effekseer dynamic input channel 2 |
| `"input3"` | Effekseer dynamic input channel 3 |
| `"homing force"` | Alias for `input0` |
| `"orbit bias"` | Alias for `input1` |
| `"velocity damping"` | Alias for `input2` |

Important notes:

- Effekseer exposes four generic runtime dynamic inputs: `input0` to `input3`.
- The friendly names `"homing force"`, `"orbit bias"`, and `"velocity damping"` are SceneMax aliases that map onto those input channels.
- An effect only reacts to a given input if the Effekseer asset was authored to use that dynamic input.
- Unknown attribute names are ignored safely, but they will not affect the effect.

### Example: Multiple Runtime Inputs

```scenemax
orb_fx => effects.effekseer.magic_orb
orb_fx.play pos (orb_anchor), attr = [
  "play_back_speed" 0.85,
  "input0" 1.0,
  "input1" 0.25,
  "velocity damping" 0.4
]
```

## Aiming The Effect Before Play

Effekseer effect objects support the same verbal turn / look-at flow used by other SceneMax entities.

### Face Another Object

```scenemax
beam_fx => effects.effekseer.energy_beam
beam_fx.look at (enemy)
beam_fx.play pos (caster)
```

### Load With Initial Rotation

```scenemax
cone_fx => effects.effekseer.cone_blast : rotate (0,90,0)
cone_fx.play pos (0,0,0)
```

Use this when the effect has directional visuals and should face a target before playback starts.

## Attaching Effects To Other Game Objects

Effect nodes can be attached to any other game object, similar to camera attachment.

### Attach To A Model

```scenemax
player => sinbad
aura_fx => effects.effekseer.aura_loop

aura_fx.attach to player
aura_fx.play pos (player)
```

### Attach To A Joint

```scenemax
player => ninja
sword_fx => effects.effekseer.sword_trail

sword_fx.attach to player."mixamorig:RightHand"
sword_fx.play pos (player)
```

### Attach With Offset

```scenemax
drone => drone_bot
smoke_fx => effects.effekseer.engine_smoke

smoke_fx.attach to drone having pos (0,-0.3,-1.2)
smoke_fx.play pos (drone)
```

### Detach From Parent

```scenemax
smoke_fx.detach from parent
```

After detaching, the effect keeps its world position and rotation and becomes independent again.

## Show, Hide, And Delete

Effekseer effects support the normal object lifecycle commands:

### Hide

```scenemax
portal_fx.hide
```

### Show

```scenemax
portal_fx.show
```

### Delete

```scenemax
portal_fx.delete
```

`delete` stops the active effect instance, releases native runtime resources, and removes the object from the scene.

## Full Example

This example shows declaration, aiming, runtime attributes, attachment, visibility, and cleanup together:

```scenemax
player => sinbad
enemy => ninja : pos (0,0,10)

missile_fx => effects.effekseer.magic_missile
shield_fx => effects.effekseer.shield_aura

shield_fx.attach to player
shield_fx.play pos (player), attr = ["play_back_speed" 0.9, "input0" 0.6]

missile_fx.look at (enemy)
missile_fx.play pos (player), attr = [
  "play_back_speed" 1.15,
  "homing force" 1.0,
  "orbit bias" 0.2,
  "velocity damping" 0.1
]

wait 2 seconds
shield_fx.hide

wait 1 second
shield_fx.show

wait 1 second
shield_fx.detach from parent

wait 1 second
missile_fx.delete
shield_fx.delete
```

## Packaging And Scene Changes

If an Effekseer asset is declared with:

```scenemax
my_effect => effects.effekseer.my_effect_name
```

SceneMax will:

- include the used asset under `resources/effects/my_effect_name/` during packaging/export
- clean up loaded runtime instances when switching scenes

That means you do not need a separate `using` declaration for normal effect usage.

## Built-In Effects

SceneMax also still supports built-in effects:

### Explosion

```scenemax
effects.explosion.show : pos (b)
```

### Flame

```scenemax
effects.flame.show : pos (13,-15,43) and radius = 5 and duration 10
```

### Time Orbit

```scenemax
effects.TimeOrbit.show : pos (0,0,0) and end size = 7 and duration = 10
```
