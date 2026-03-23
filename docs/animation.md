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
n is a ninja
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
