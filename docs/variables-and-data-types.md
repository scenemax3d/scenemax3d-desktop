# Variables & Data Types

## Numeric Variables

Declare a numeric variable with an initial value:

```scenemax
var score=0
```

## String Variables

Declare a string variable:

```scenemax
var name="adi barda"
```

## Updating Variables

Update a numeric variable:

```scenemax
score=score+5
```

Update multiple variables at once:

```scenemax
score=10, life=3, time=60
```

## Variable Range Constraints

Define an allowed numeric range. The system will always ensure the variable stays within this range:

```scenemax
var life1=1 [0..20]
```

Minimum only (no upper bound):

```scenemax
var life1=1 [0..]
```

Maximum only (no lower bound):

```scenemax
var life1=1 [..20]
```

## Shared Variables

Declare a variable shared across all levels (not deleted on level switch):

```scenemax
shared var score = 0
```

## Shared Models

Declare a model shared across all levels:

```scenemax
shared player => sinbad
```

Declare a shared box:

```scenemax
shared my_box => box
```

## Predefined Shortcut Expressions

Define a reusable condition expression:

```scenemax
var @allow_collision_check = coll==0 &&
  is_jumping==0 &&
  is_old_fighter_jumping==0 &&
  ko==0
```

Use the predefined expression in conditions:

```scenemax
if (@allow_collision_check && @game_is_running)

when @player_is_dead && game_state == 1 do
end do

[@player_is_alive]
move_player = {}
```
