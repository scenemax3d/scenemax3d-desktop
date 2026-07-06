# Animation

## Playing 3D Animations

Play a named animation on a model:

```scenemax
s.Dance
```

## Chaining Animations

Chain multiple animations using `then`:

```scenemax
s.Dance then SliceVertical then JumpLoop
```

## Animation Speed

Play at half speed (slow motion):

```scenemax
s.Dance at speed of 0.5
```

Play at double speed:

```scenemax
s.Dance at speed of 2
```

## Protected Animations

A protected animation cannot be canceled when another animation starts while it is still running:

```scenemax
fighter.jump_high : protected
```

## Animations with Spaces in Names

Use quotes for animation names that contain spaces:

```scenemax
s."Take 001"
```

## Animation Frame Ranges

Play only part of an animation by adding a frame range immediately after the animation name:

```scenemax
horse."Take 001"[804-828] at speed of 2 loop
```

Frame ranges can also use percentages. If either endpoint has `%`, that endpoint is resolved as a percentage of the animation length at runtime:

```scenemax
horse.long_animation[0%-50%] loop
horse.long_animation[120-75%]
```

Out-of-range values are clamped to the animation's valid frame range. If the end resolves before the start, playback is clamped to the start frame instead of crashing.

Frame ranges can also be referenced by name when the model's JSON contains an `animationFrameRanges` table, such as ranges authored with the 3D Model Analyzer:

```scenemax
player.long_animation["walk"] loop
player.long_animation["attack"] at speed of 1.5
```

At parse time SceneMax looks up the selected model resource's `animationFrameRanges` entry with the matching `name` and stores its numeric `start` and `end` frame values in the compiled animation command. Runtime playback only receives numeric frame ranges.

## Animation Speed Control (mid-animation)

Slow down the current animation to 1/100th speed for 0.5 seconds:

```scenemax
player1.animation speed 0.01 for 0.5 seconds
```

Slow down only after the animation reaches 65% progress:

```scenemax
player2.animation speed 0.01 for 0.5 seconds when frames > 65
```

## Animation Percentage

Get the current animation's completion percentage:

```scenemax
player1.anim_percent
```

## Character Mode Animations

Switch to character mode and perform a jump:

```scenemax
n => ninja
n.switch to character mode
n.character.jump
```

Jump with custom speed:

```scenemax
n.switch to character mode
n.character.jump at speed of 20
```

Clear character mode:

```scenemax
n.clear character mode
```
