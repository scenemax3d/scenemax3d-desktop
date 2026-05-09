# Movement & Rotation

## Move

Move an object left by 3 meters over 2 seconds:

```scenemax
d.move left 3 in 2 seconds
```

Add optional easing to timed movement:

```scenemax
d.move left 3 in 2 seconds ease in out
```

## Turn (Y-axis rotation)

Turn an object left by 360 degrees over 10 seconds:

```scenemax
d.turn left 360 in 10 seconds
```

Turn with easing:

```scenemax
d.turn left 360 in 10 seconds ease out
```

## Roll (Z-axis rotation)

Roll an object left by 360 degrees over 10 seconds:

```scenemax
d.roll left 360 in 10 seconds
```

Roll with easing:

```scenemax
d.roll left 360 in 10 seconds ease in
```

## Rotate

Rotate one or more axes over time:

```scenemax
d.rotate(y 180) in 2 seconds ease in out
```

## Async Movement

Perform a rotation asynchronously (does not block the next command):

```scenemax
d.turn left 30 in 5 seconds async
```

## Hide / Show

Hide an object:

```scenemax
d.hide
```

## Look At

Make one object face another:

```scenemax
n.look at (n2)
```

## Angle Between Objects

Calculate the angle between two objects:

```scenemax
var ang = angle(n1,n2)
```

## Distance Between Objects

Measure the distance between two objects:

```scenemax
n => ninja : pos (-2,0,0)
n2 => ninja : pos (2,0,0)
var dist = distance(n,n2)
```

## Detach from Parent

Detach an object from its parent object:

```scenemax
n.detach from parent
```

## Position Statements

Move to a position relative to another object. For example, move towards `player1` plus 2 meters forward, 3 meters right, and 1 meter up:

```scenemax
player2.move to (player1 forward 2 right 3 up 1) in 3 seconds
```

You can add the same easing clause to `move to`:

```scenemax
player2.move to (player1 forward 2 right 3 up 1) in 3 seconds ease in out
```

Move while looking at a moving target. Here, `player2` moves toward `player1` while continuously looking at a point 2 meters above `player3`:

```scenemax
player2.move to (player1 forward 2 right 3 up 1) in 3 seconds looking at (player3 up 2) async
```

## Motion Easing

Timed movement and rotation commands support an optional easing clause right after the duration. Easing changes how progress is distributed over time, but the command still completes the exact requested movement or rotation. The runtime controller applies easing to progress deltas each frame, so the sum of all deltas still reaches the final target.

Supported commands:

- `move (...) in ... seconds`
- `move to ... in ... seconds`
- `move ... in ... seconds`
- `move ... for ... seconds`
- `turn ... in ... seconds`
- `roll ... in ... seconds`
- `rotate(...) in ... seconds`
- `rotate to (...) in ... seconds`

### Basic Syntax

The classic syntax is still supported:

```scenemax
player.move forward 4 in 1.5 seconds ease in
player.move forward 4 for 1.5 seconds ease out
player.rotate(y 90) in 0.6 seconds ease in out
player.rotate to (y 180) in 0.8 seconds ease out
```

These classic forms are aliases:

- `ease in` = `ease in "Quad"`
- `ease out` = `ease out "Quad"`
- `ease in out` = `ease in out "Quad"`

### Named Function Syntax

Add a quoted function name after the direction to choose the easing curve:

```scenemax
player.move to (1,2,3) in 3 seconds ease in out "Cubic"
player.turn left 90 in 1 second ease out "Back"
player.rotate to (y 180) in 2 seconds ease in "Sine"
```

The direction keywords keep their current meaning:

- `ease in` starts slowly and accelerates.
- `ease out` starts quickly and decelerates.
- `ease in out` eases at both ends.

The function name is case-insensitive. Spaces, hyphens, and underscores are ignored, so `"CubicBezier"`, `"cubic_bezier"`, and `"cubic-bezier"` are treated the same.

### Parameters

Add optional parentheses after the function name for tuning:

```scenemax
player.move to (1,2,3) in 3 seconds ease in out "Cubic" (0.25)
player.move to (1,2,3) in 3 seconds ease out "CubicBezier" (0.25, 0.1, 0.25, 1)
player.move to (1,2,3) in 3 seconds ease out "Elastic" (1.2, 0.4)
```

If parameters are omitted, each function uses its default values.

### Ease Time

`easeTime` limits easing to part of the command duration while keeping the rest linear.

For `ease in`, `easeTime` controls the beginning of the motion. For `ease out`, it controls the ending. For `ease in out`, it controls both ends. For example:

```scenemax
player.move to (1,2,3) in 3 seconds ease in out "Cubic" (0.25)
```

This eases the first 25% of the motion, moves linearly through the middle, and eases the last 25%. For `ease in out`, values above `0.5` are clamped to `0.5` because the in and out windows cannot overlap.

### Functions

`Linear`

- No easing; progress is constant.
- Parameters: none.
- Example: `ease out "Linear"`

`Sine`

- Smooth, gentle easing based on a sine curve.
- Parameters: `(easeTime)`
- Default: full-duration easing.
- Example: `ease in out "Sine"`
- Tuned example: `ease in out "Sine" (0.3)`

`Quad`

- Quadratic easing. This is the default used by the classic syntax.
- Parameters: `(easeTime)`
- Default: full-duration easing.
- Example: `ease in "Quad"`

`Cubic`

- Stronger than `Quad`; useful for snappier movement.
- Parameters: `(easeTime)`
- Default: full-duration easing.
- Example: `ease in out "Cubic" (0.25)`

`Quart`

- Fourth-power easing; more dramatic acceleration/deceleration.
- Parameters: `(easeTime)`
- Default: full-duration easing.
- Example: `ease out "Quart"`

`Quint`

- Fifth-power easing; very strong at the eased side.
- Parameters: `(easeTime)`
- Default: full-duration easing.
- Example: `ease in "Quint"`

`Expo`

- Exponential easing; very slow at the start for `ease in`, very soft at the end for `ease out`.
- Parameters: `(easeTime)`
- Default: full-duration easing.
- Example: `ease out "Expo"`

`Circ`

- Circular easing; a rounded curve with a natural-feeling ramp.
- Parameters: `(easeTime)`
- Default: full-duration easing.
- Example: `ease in out "Circ"`

`Back`

- Overshoots slightly before settling, useful for punchy turns or UI-like movement.
- Parameters: `(overshoot, easeTime)`
- Defaults: `overshoot = 1.70158`, full-duration easing.
- Example: `ease out "Back"`
- Tuned example: `ease out "Back" (2.0)`
- Windowed example: `ease out "Back" (2.0, 0.2)`

`Elastic`

- Springs past the target and settles back.
- Parameters: `(amplitude, period, easeTime)`
- Defaults: `amplitude = 1.0`, `period = 0.3`, full-duration easing.
- Example: `ease out "Elastic"`
- Tuned example: `ease out "Elastic" (1.2, 0.4)`
- Windowed example: `ease out "Elastic" (1.2, 0.4, 0.3)`

`Bounce`

- Bounces near the eased side.
- Parameters: `(easeTime)`
- Default: full-duration easing.
- Example: `ease out "Bounce"`

`Power`

- Custom power curve.
- Parameters: `(power, easeTime)`
- Defaults: `power = 2.5`, full-duration easing.
- Example: `ease in "Power" (3.0)`
- Windowed example: `ease in "Power" (3.0, 0.25)`

`CubicBezier`

- Custom cubic Bezier easing using CSS-style control points.
- Parameters: `(x1, y1, x2, y2, easeTime)`
- Defaults for `ease in`: `(0.42, 0, 1, 1)`
- Defaults for `ease out`: `(0, 0, 0.58, 1)`
- Example: `ease out "CubicBezier" (0.25, 0.1, 0.25, 1)`
- Windowed example: `ease in out "CubicBezier" (0.42, 0, 0.58, 1, 0.25)`

### Complete Examples

Move to a coordinate with a cubic ease at both ends:

```scenemax
player.move to (1,2,3) in 3 seconds ease in out "Cubic" (0.25)
```

Move to a coordinate using a custom Bezier curve:

```scenemax
player.move to (1,2,3) in 3 seconds ease out "CubicBezier" (0.25, 0.1, 0.25, 1)
```

Move to a coordinate with an elastic finish:

```scenemax
player.move to (1,2,3) in 3 seconds ease out "Elastic" (1.2, 0.4)
```

Turn with a back overshoot:

```scenemax
player.turn left 90 in 1 second ease out "Back" (2.0)
```

Rotate to a target angle with a sine ease:

```scenemax
player.rotate to (y 180) in 2 seconds ease in "Sine"
```
