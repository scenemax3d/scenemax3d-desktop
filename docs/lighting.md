# Lighting

SceneMax3D supports first-class light objects for building readable, cinematic, and performant scenes. Lights can be authored in the UI Designer or written directly in SceneMax code using the `Lights.*` syntax.

The lighting system is meant to cover the common real-time game lighting workflow:

- global sunlight and moonlight with `Lights.directional`
- local bulbs, lamps, fire, signs, and explosions with `Lights.point`
- flashlights, stage beams, vehicle headlights, and security cones with `Lights.spot`
- broad environmental mood with `Lights.sky` and `Lights.ambient`
- reflection and image-based lighting support with `Lights.probe`
- optional shadow quality levels with `shadow low`, `shadow medium`, `shadow high`, or `shadow on`
- Designer support for adding, selecting, moving, rotating, previewing, and exporting lights

When a scene declares custom lights, the built-in fallback lighting is disabled for that scene. When the scene is cleared or switched, custom lights and their shadow filters are cleaned up with the rest of the scene objects.

## Quick Example

```scenemax
sun => Lights.directional : color "#fff3d2", intensity 3.0, direction (-0.3,-0.8,-0.4), shadow high
lamp => Lights.point : pos (2,4,1), color warm, intensity 900 lumens, range 12, shadow medium
stageSpot => Lights.spot : pos (0,6,-4), look at player1, angle 35, intensity 2500, shadow on
environment => Lights.sky : preset "Night Neon", exposure 0.2, ambient "#223344"
```

This creates:

- a strong warm sun with high-quality directional shadows
- a warm local lamp with medium point-light shadows
- a spotlight aimed at `player1`
- a dark blue ambient environment

## Declaration Syntax

```scenemax
name => Lights.type : attribute value, attribute value
```

`type` can be:

- `directional`
- `point`
- `spot`
- `sky`
- `ambient`
- `probe`

Attributes can be separated with commas or `and`.

```scenemax
lamp => Lights.point : pos (2,4,1), color warm, intensity 900 lumens, range 12

lamp2 => Lights.point : pos (2,4,1) and color warm and intensity 900 lumens and range 12
```

Most attributes allow either `attribute value` or `attribute = value`.

```scenemax
sun => Lights.directional : intensity 2.0, shadow high
sun2 => Lights.directional : intensity = 2.0, shadow high
```

## Which Light To Use

Use `Lights.directional` when the light source is effectively infinitely far away and all rays travel in the same direction. Sunlight and moonlight are the main examples. A directional light has no position; only its direction matters.

Use `Lights.point` when light spreads outward from one place in every direction. Lamps, torches, candles, magic glows, muzzle flashes, explosions, neon signs, and glowing pickups are typical point lights. Point lights have position and range.

Use `Lights.spot` when light comes from one place and points in one direction with a cone shape. Flashlights, stage lights, searchlights, vehicle headlights, ceiling downlights, and security cameras are typical spot lights. Spot lights have position, direction, range, angle, and optional `look at` targeting.

Use `Lights.sky` when you want a broad environmental color preset that shapes the whole scene mood. It is useful for day, night, overcast, or stylized atmosphere. Sky lighting is not a physical bulb and does not have a visible position.

Use `Lights.ambient` when you want simple non-directional fill light. Ambient light brightens shadows and dark surfaces evenly. Use it carefully; too much ambient light makes objects look flat.

Use `Lights.probe` when the scene needs image-based lighting or reflection-probe style contribution. Probes are useful near reflective materials, indoor rooms, polished floors, metallic props, or areas where local environment lighting should feel richer.

## Decision Guide

| Goal | Best light | Why |
| --- | --- | --- |
| Outdoor daylight | `Lights.directional` + `Lights.sky` | Sun gives direction and shadows; sky fills the world color. |
| Night level with readable characters | `Lights.sky` + a few `Lights.point` or `Lights.spot` lights | Sky sets mood; local lights guide attention. |
| Lamp, torch, fire, magic glow | `Lights.point` | Emits outward from a position. |
| Flashlight or cone beam | `Lights.spot` | Emits from a position toward a direction or target. |
| Stage, arena, boss reveal | `Lights.spot` | Gives dramatic direction and controllable cone width. |
| Make shadows less black | `Lights.ambient` or `Lights.sky` | Adds global fill. |
| Reflective room or metallic scene | `Lights.probe` | Adds environment-based lighting contribution. |
| Cheap mobile-friendly fill | `Lights.ambient` without shadows | Low visual cost and predictable. |

## Common Attributes

### Color

Color accepts hex strings, named colors, and the built-in `warm` and `cool` tones.

```scenemax
warmLamp => Lights.point : color warm, intensity 700 lumens, pos (2,3,1), range 8
moon => Lights.directional : color cool, intensity 0.8, direction (0.2,-1,0.1)
alert => Lights.point : color red, intensity 2.0, pos (0,2,0), range 5
custom => Lights.spot : color "#66ccff", pos (0,5,0), direction (0,-1,0), angle 25
```

Supported named colors include `warm`, `cool`, `red`, `green`, `blue`, `white`, `black`, `yellow`, `orange`, `pink`, `cyan`, `magenta`, `gray`, and `grey`.

Prefer hex colors when you need precise art direction. Prefer `warm` and `cool` when you want quick readable lighting while sketching a scene.

### Intensity

Intensity can be a plain multiplier or a lumen value.

```scenemax
sun => Lights.directional : intensity 2.5, direction (-0.2,-1,-0.5)
lamp => Lights.point : intensity 900 lumens, pos (1,3,0), range 10
```

Use plain numeric intensity for global/direct artistic control, especially directional and ambient lights. Use `lumens` for local lights when thinking in real-world-ish terms: a small lamp might be around `400 lumens`, a bright room bulb around `800-1200 lumens`, and a strong stage or vehicle light can be much higher.

### Position

Use `pos (x,y,z)` for lights that exist at a location.

```scenemax
lamp => Lights.point : pos (2,4,1), range 12, intensity 900 lumens
```

Point, spot, and probe lights use position. Directional, sky, and ambient lights do not depend on position for their visual effect.

You can move light objects at runtime:

```scenemax
lamp.move right 3 in 1 second
lamp.move to (player1 up 2 forward 1) in 0.5 seconds
```

For point lights, movement changes where the light comes from. For spot lights, movement changes the cone origin.

### Direction

Use `direction (x,y,z)` for directional and spot lights.

```scenemax
sun => Lights.directional : direction (-0.3,-0.8,-0.4), intensity 3.0
flashlight => Lights.spot : pos (0,2,0), direction (0,-0.2,1), angle 28, range 20
```

The vector is normalized by the runtime, so it does not need to have exact length `1`. Direction is still important: `(0,-1,0)` points down, `(0,0,1)` points forward on the Z axis, and `(-0.3,-0.8,-0.4)` points diagonally downward.

You can rotate light objects at runtime:

```scenemax
stageSpot.rotate(y 45) in 2 seconds
stageSpot.rotate to (y 20) in 1 second
```

Rotating a spot light changes its beam direction. Rotating a directional light changes the sun or moon direction. Rotating point, sky, or ambient lights has no visible lighting effect because those light types have no directional cone.

### Look At

Spot lights can point at another scene object with `look at`.

```scenemax
stageSpot => Lights.spot : pos (0,6,-4), look at player1, angle 35, intensity 2500, shadow on
```

Use `look at` when a light should be aimed at a character, prop, boss, doorway, or stage area at creation time. Use rotation commands when you want to animate the beam yourself.

### Range

Range controls how far a point or spot light reaches.

```scenemax
candle => Lights.point : pos (0,1,0), color warm, intensity 100 lumens, range 3
streetLamp => Lights.point : pos (0,6,0), color warm, intensity 1400 lumens, range 18
```

Keep ranges as small as the scene allows. Smaller ranges usually look better because they keep the light focused, and they reduce the number of objects affected by the light.

### Angle

Angle controls the outer cone width of a spot light, in degrees.

```scenemax
flashlight => Lights.spot : pos (0,2,0), direction (0,-0.1,1), angle 22, range 25
wideStage => Lights.spot : pos (0,8,-6), look at player1, angle 45, range 30
```

Use narrow angles such as `15` to `25` for flashlights and searchlights. Use wider angles such as `35` to `60` for stage lights, ceiling lights, and area emphasis.

### Shadows

Lights can request shadows with:

```scenemax
shadow on
shadow low
shadow medium
shadow high
shadow off
```

Examples:

```scenemax
sun => Lights.directional : direction (-0.3,-0.8,-0.4), intensity 2.5, shadow high
lamp => Lights.point : pos (2,4,1), intensity 900 lumens, range 12, shadow medium
flashlight => Lights.spot : pos (0,2,-2), direction (0,-0.2,1), angle 25, range 20, shadow low
```

Use `shadow high` for the main sunlight, hero spotlights, and important cinematic shots. Use `shadow medium` for important local lights. Use `shadow low` for secondary local lights. Use `shadow off` or omit `shadow` for fill lights, decorative glows, and most small lights.

Shadows are visually powerful but have real cost. A good default is:

- one high-quality directional shadow for the sun or moon
- one or two medium/low local shadow-casting lights near the player
- many non-shadowed point lights for visual accents

## Light Types In Detail

### Directional Light

A directional light represents sunlight, moonlight, or another light source so far away that its rays are parallel. It affects the whole scene evenly from one direction.

```scenemax
sun => Lights.directional : color "#fff3d2", intensity 3.0, direction (-0.3,-0.8,-0.4), shadow high
```

Use it for:

- sun and moon
- strong outdoor key light
- stylized global rim light
- large directional shafts in arenas or side-scrollers

Avoid using several strong directional lights unless you are deliberately making a stylized scene. Multiple directional lights can flatten the composition because everything is lit from everywhere.

Good outdoor pattern:

```scenemax
sun => Lights.directional : color "#fff3d2", intensity 2.8, direction (-0.4,-0.9,-0.2), shadow high
sky => Lights.sky : preset "Sunny Day", exposure 0.8, ambient "#b9d8ff"
```

Good moonlight pattern:

```scenemax
moon => Lights.directional : color cool, intensity 0.7, direction (0.2,-0.9,0.4), shadow medium
night => Lights.sky : preset "Night Neon", exposure 0.2, ambient "#172033"
```

### Point Light

A point light emits in every direction from a position. It is the most useful local light.

```scenemax
lamp => Lights.point : pos (2,4,1), color warm, intensity 900 lumens, range 12, shadow medium
```

Use it for:

- bulbs and lamps
- torches, candles, campfires
- pickup glow
- magic or sci-fi energy
- muzzle flashes and explosions
- neon accents when a sign should cast colored light nearby

Keep point lights close to the object that visually explains them. If a table lamp has a visible bulb, place the light at the bulb, not at the table center.

Small warm prop light:

```scenemax
candleLight => Lights.point : pos (0,1.2,0), color "#ffb36a", intensity 160 lumens, range 4, shadow off
```

Bright gameplay guide light:

```scenemax
exitGlow => Lights.point : pos (10,3,-2), color cyan, intensity 1300 lumens, range 16, shadow off
```

Explosion flash:

```scenemax
blastLight => Lights.point : pos (enemy1), color orange, intensity 3500 lumens, range 18, shadow off
blastLight.move up 0.1 in 0.05 seconds
```

### Spot Light

A spot light emits a cone from a position. It gives the most controllable dramatic lighting.

```scenemax
stageSpot => Lights.spot : pos (0,6,-4), look at player1, angle 35, intensity 2500, shadow on
```

Use it for:

- flashlights
- headlights
- stage lights
- ceiling downlights
- searchlights
- security camera cones
- boss entrances and reveal shots

For a flashlight, use a narrow angle and longer range:

```scenemax
flashlight => Lights.spot : pos (0,2,-1), direction (0,-0.1,1), color white, intensity 1800 lumens, range 28, angle 22, shadow medium
```

For stage lighting, use colored spots aimed at the performer:

```scenemax
stageKey => Lights.spot : pos (-4,7,-5), look at player1, color "#ffdca0", intensity 3000, range 30, angle 32, shadow high
stageFill => Lights.spot : pos (4,6,-4), look at player1, color "#88aaff", intensity 1200, range 25, angle 45, shadow off
```

For sweeping searchlights, rotate the light:

```scenemax
search => Lights.spot : pos (0,8,-10), direction (0,-0.3,1), color cool, intensity 2600, range 45, angle 18, shadow medium

when key SPACE is pressed do
  search.rotate(y 120) in 4 seconds
end do
```

### Sky Light

A sky light sets broad environmental mood. It is best used as a base layer, then combined with directional or local lights.

```scenemax
environment => Lights.sky : preset "Night Neon", exposure 0.2, ambient "#223344"
```

Use it for:

- day/night mood
- colored global fill
- making unlit sides readable
- matching the scene's skybox or visual palette

Supported presets include:

- `"Night Neon"`
- `"Sunny Day"`
- `"Overcast"`

You can override the ambient color directly:

```scenemax
overcast => Lights.sky : preset "Overcast", exposure 0.5, ambient "#8f949c"
```

`ambient` is the most important runtime value for the sky light's fill color. `exposure` is kept with the sky preset and exported by the Designer so scenes can preserve their authored environment intent.

Use darker ambient colors for contrast and mood. Use brighter ambient colors for casual, readable, low-contrast games.

### Ambient Light

Ambient light adds an even color everywhere.

```scenemax
fill => Lights.ambient : color "#263244", intensity 0.35
```

Use it for:

- simple fill when shadows are too black
- stylized low-cost scenes
- prototypes where lighting should be readable immediately
- mobile scenes where many shadows are too expensive

Avoid high ambient intensity in scenes that need drama. If everything is bright from every direction, materials lose depth and shadows stop guiding the player.

Good subtle fill:

```scenemax
fill => Lights.ambient : color "#202838", intensity 0.25
```

Flat but readable prototype fill:

```scenemax
prototypeLight => Lights.ambient : color white, intensity 0.8
```

### Light Probe

A light probe provides environment-based lighting contribution from a probe asset.

```scenemax
roomProbe => Lights.probe : preset "1", pos (0,2,0)
```

Use it for:

- reflective or metallic objects
- indoor rooms with a distinct local mood
- areas where materials should pick up nearby environmental color
- polished floors, sci-fi corridors, or showroom scenes

Probe presets map to available probe assets. Use `"1"` through `"5"` when using the built-in probe set.

```scenemax
hallProbe => Lights.probe : preset "2", pos (0,2,5)
garageProbe => Lights.probe : preset "4", pos (-6,2,0)
```

## Designer Workflow

The UI Designer supports light entities directly.

Typical workflow:

1. Add a light from the Designer toolbar.
2. Select it in the scene or hierarchy.
3. Set type, color, intensity, range, angle, shadow mode, direction, look-at target, preset, exposure, and ambient color in the inspector.
4. Move and rotate the light with the same transform tools used for objects.
5. Preview the result immediately in the Designer viewport.
6. Export or generate SceneMax code.

Designer-authored lights export to the same language syntax:

```scenemax
keyLight => Lights.spot : pos (-3,5,-4), direction (0.4,-0.8,0.3), color warm, intensity 1800, range 25, angle 30, shadow medium
```

For spot and directional lights, the Designer applies the entity rotation to the exported direction. This means you can aim lights visually and still get correct generated code.

## Moving And Rotating Lights

Lights are scene objects with a transform node, so the usual movement and rotation commands can target them.

```scenemax
lamp => Lights.point : pos (0,3,0), color warm, intensity 900 lumens, range 10
lamp.move right 2 in 1 second
```

```scenemax
scanner => Lights.spot : pos (0,5,-6), direction (0,-0.2,1), angle 20, range 35, shadow low
scanner.rotate(y 90) in 2 seconds
```

How transforms affect each type:

| Light type | Moving affects light? | Rotating affects light? |
| --- | --- | --- |
| `directional` | No visible effect | Yes, changes light direction |
| `point` | Yes, changes origin | No visible effect |
| `spot` | Yes, changes origin | Yes, changes beam direction |
| `sky` | No visible effect | No visible effect |
| `ambient` | No visible effect | No visible effect |
| `probe` | Yes, changes probe position | No visible effect |

## Scene Switching And Cleanup

Custom lights are cleaned up when a scene is cleared or switched. This includes:

- removing the runtime light from the scene root
- removing point, spot, and directional shadow filters
- removing the backing light transform node
- restoring fallback lighting only when no custom scene lights remain

This behavior lets every scene own its own lighting setup without leaking old lights into the next scene.

## Practical Recipes

### Bright Outdoor Arena

```scenemax
sky => Lights.sky : preset "Sunny Day", exposure 0.9, ambient "#b9d8ff"
sun => Lights.directional : color "#fff1c7", intensity 2.7, direction (-0.35,-0.85,-0.25), shadow high
```

Use this for training arenas, outdoor platformers, racing tracks, and readable action scenes.

### Night Street With Neon

```scenemax
night => Lights.sky : preset "Night Neon", exposure 0.18, ambient "#141c30"
moon => Lights.directional : color cool, intensity 0.45, direction (0.2,-0.9,0.25), shadow medium
signA => Lights.point : pos (-4,3,2), color magenta, intensity 1200 lumens, range 12, shadow off
signB => Lights.point : pos (3,2.5,-1), color cyan, intensity 1000 lumens, range 10, shadow off
doorLamp => Lights.point : pos (0,3,5), color warm, intensity 700 lumens, range 7, shadow low
```

Use local colored point lights to create mood and guide the player. Keep most neon lights shadowless.

### Interior Room

```scenemax
roomFill => Lights.ambient : color "#242832", intensity 0.25
ceiling => Lights.point : pos (0,3.2,0), color warm, intensity 1000 lumens, range 9, shadow medium
desk => Lights.spot : pos (-2,2.2,1), direction (0.3,-0.8,0.1), color "#ffd3a0", intensity 650 lumens, range 7, angle 45, shadow low
roomProbe => Lights.probe : preset "2", pos (0,1.5,0)
```

Use a small amount of ambient fill, one meaningful ceiling or lamp light, and a probe if reflective materials are important.

### Flashlight Horror Setup

```scenemax
dark => Lights.sky : preset "Night Neon", exposure 0.08, ambient "#05070c"
moon => Lights.directional : color cool, intensity 0.18, direction (-0.2,-0.7,0.4), shadow medium
flashlight => Lights.spot : pos (0,1.8,-0.4), direction (0,-0.05,1), color "#f8f1d8", intensity 1800 lumens, range 32, angle 20, shadow medium
```

Keep the environment very dark, then let the spot light define what the player can read.

### Fighting Game Stage

```scenemax
stageEnv => Lights.sky : preset "Night Neon", exposure 0.25, ambient "#1d2238"
backRim => Lights.directional : color "#99bbff", intensity 0.8, direction (0.3,-0.5,0.8), shadow off
leftSpot => Lights.spot : pos (-5,7,-5), look at player1, color "#ffd2a0", intensity 2500, range 28, angle 35, shadow medium
rightSpot => Lights.spot : pos (5,7,-5), look at player2, color "#a0c8ff", intensity 2200, range 28, angle 35, shadow medium
crowdGlow => Lights.point : pos (0,3,8), color magenta, intensity 1500 lumens, range 20, shadow off
```

Use opposing spotlights to separate fighters from the background. Add non-shadowed color glows for showmanship without making the scene expensive.

### Mobile-Friendly Lighting

```scenemax
sky => Lights.sky : preset "Sunny Day", exposure 0.7, ambient "#a7bfd8"
sun => Lights.directional : color "#fff3d2", intensity 1.8, direction (-0.3,-0.8,-0.4), shadow medium
pickupGlow => Lights.point : pos (2,1.2,0), color cyan, intensity 400 lumens, range 4, shadow off
```

Use one shadow-casting directional light, simple sky or ambient fill, and small shadowless accent lights.

## Performance Guidelines

Lighting quality is a balance between readability, mood, and frame rate.

Prefer:

- one main shadow-casting directional light
- local shadows only where the player notices them
- short point and spot ranges
- shadowless colored accent lights
- ambient or sky fill for readability

Avoid:

- many overlapping shadow-casting point lights
- very large point light ranges
- high ambient intensity in dramatic scenes
- several strong directional lights pointing in different directions
- using shadows on lights that only add color mood

If the scene feels flat, reduce ambient intensity and add one stronger directional or spot light. If the scene feels noisy or expensive, remove shadows from secondary lights first.

## Full Syntax Reference

```scenemax
name => Lights.directional : color VALUE, intensity NUMBER, direction (x,y,z), shadow low|medium|high|on|off
name => Lights.point : pos (x,y,z), color VALUE, intensity NUMBER [lumens], range NUMBER, shadow low|medium|high|on|off
name => Lights.spot : pos (x,y,z), direction (x,y,z), look at target, angle NUMBER, intensity NUMBER [lumens], range NUMBER, shadow low|medium|high|on|off
name => Lights.sky : preset "Preset Name", exposure NUMBER, ambient VALUE
name => Lights.ambient : color VALUE, intensity NUMBER
name => Lights.probe : preset "1", pos (x,y,z)
```

`VALUE` can be a hex color such as `"#fff3d2"` or a named color such as `warm`, `cool`, `red`, `blue`, or `white`.

For spot lights, use either `direction (...)` or `look at target`. If both are provided, the target direction is preferred when the target exists.
