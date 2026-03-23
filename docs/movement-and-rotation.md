# Movement & Rotation

## Move

Move an object left by 3 meters over 2 seconds:

```scenemax
d.move left 3 in 2 seconds
```

## Turn (Y-axis rotation)

Turn an object left by 360 degrees over 10 seconds:

```scenemax
d.turn left 360 in 10 seconds
```

## Roll (Z-axis rotation)

Roll an object left by 360 degrees over 10 seconds:

```scenemax
d.roll left 360 in 10 seconds
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
n look at (n2)
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

Move while looking at a moving target. Here, `player2` moves toward `player1` while continuously looking at a point 2 meters above `player3`:

```scenemax
player2.move to (player1 forward 2 right 3 up 1) in 3 seconds looking at (player3 up 2) async
```
