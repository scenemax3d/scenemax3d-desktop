# Sprites

## Loading Sprites

Load a bird sprite:

```scenemax
br is a bird sprite
```

Load a sprite hidden at a specific position:

```scenemax
b is a bird sprite: hidden, pos (-4,2,2)
```

Load a display-only sprite with no collision detection (better performance):

```scenemax
b is a bird sprite: collision shape none
```

## Billboard Sprites

Create a sprite that always faces the camera:

```scenemax
hit1 is a good_jap1 sprite : scale 3 and billboard true
```

Position a billboard sprite on a character's joint:

```scenemax
hit1 is a good_jap1 sprite : scale 3 and billboard true and pos(player2."mixamorig:Head")
```

## Sprite Animation

Animate frames 0 to 14, looping 5 times:

```scenemax
br.play (0 to 14) loop 5 times
```

Infinite loop animation:

```scenemax
br.play (0 to 14) loop
```

Animate for a specific duration:

```scenemax
br.play (0 to 14) for 5 seconds
```

Control animation speed (3 seconds per cycle instead of default 1 second):

```scenemax
br.play (0 to 14 in 3 seconds) loop
```
