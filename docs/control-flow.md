# Control Flow

## If / Else

Basic condition check:

```scenemax
if score == 5 {

}
```

If / else if / else:

```scenemax
if score == 5 {

} else if score == 10 {

} else {

}
```

## Loops

Loop 10 times:

```scenemax
do 10 times
  d.turn 30 in 1 second
  d.turn 20 in 0.5 seconds
end do
```

Loop with a counter and a while condition:

```scenemax
var counter = 10
do 10 times
  d.turn left 30 in 1 second
  d.turn right 20 in 0.5 seconds
  counter = counter - 1
while counter>0
```

Async loop (does not block the next command):

```scenemax
do 10 times async
  d.turn 30 in 1 second
  d.turn 20 in 0.5 seconds
end do
```

## For Each

Iterate over all objects in the game:

```scenemax
for each (m) {
  m.turn left 360 in 1 second
}
```

Iterate over all objects of a specific type (`model`, `sprite`, `box`, `sphere`):

```scenemax
for each model (m) {

}
```

Iterate over all models whose name contains "sp":

```scenemax
for each model (m) : name "sp" {

}
```

## Go Conditions

*Available since version 1.6.5*

Go conditions determine whether a procedure, event, or animation is allowed to run. In this example, `game_over` will only run when `game_status` equals zero AND `ko` equals one:

```scenemax
[game_status==0 && ko==1]
game_over = {}
```

Go condition on a keyboard event:

```scenemax
[game_status==0]
When key A is pressed do
End do
```

Go condition on an animation:

```scenemax
[game_status==0]
ninja.Backflip
```

### Continuous Go Conditions

A go condition prefixed with `#` is checked continuously while the block executes. If the condition becomes false, the block stops:

```scenemax
#[player_is_jumping==0]
do
  player1.move left 2 in 1 second
  player1.round_kick
end do
```

### When Conditions

Execute an action when a condition becomes true:

```scenemax
when x==1 && y==2 do

end do
```

Execute an action after a sequence of conditions are met one after another:

```scenemax
when x==1 after x==2 after y==3 do

end do
```
