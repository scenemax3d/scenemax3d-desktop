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
