# Events & Input

## Keyboard Events

Execute code when a key is pressed:

```scenemax
when key left is pressed do

end do
```

Execute only once per key press (requires `once` keyword):

```scenemax
when key up is pressed once do
  move_forward=1
  player1."run_sword" loop
  run walk_forward async
end do
```

## Mouse Events

Execute code when the left mouse button is pressed:

```scenemax
when mouse left is pressed do

end do
```

Execute code when clicking on a specific object (model, sphere, box, or canvas):

```scenemax
player => sinbad
when mouse left is pressed on player do
end do
```

## Groups & Ray Checks

Assign an object to a group:

```scenemax
D belongs to the enemies group
```

Check if any object in a group is hit by an imaginary ray cast from the mouse position:

```scenemax
if enemies.ray check do

end do
```

Cast a ray from a specific object's position instead of the mouse:

```scenemax
if enemies.ray check from (s) do

end do
```
