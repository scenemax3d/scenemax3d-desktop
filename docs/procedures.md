# Procedures & Functions

## Defining a Procedure

```scenemax
my_proc = {
  d.turn 360 in 10 seconds
}
```

## Running a Procedure

```scenemax
run my_proc
```

## Running at Intervals

Run a procedure every 1.5 seconds:

```scenemax
run my_proc every 1.5 seconds
```

## Running Asynchronously

```scenemax
run my_proc async
```

Asynchronous procedures are useful for fire-and-forget behavior. A procedure used as a value-returning function should run synchronously.

## Procedures with Arguments

Define a procedure that accepts an argument:

```scenemax
my_proc (m) = {
  m.turn 360 in 10 seconds
}
```

Call it with an argument:

```scenemax
run my_proc (s)
```

## Returning Values

Procedures can return a value with `return <expression>`.

```scenemax
get_bonus = {
  return 25
}

var score_bonus = get_bonus()
```

Return values can be numbers, strings, arrays, or object references.

```scenemax
create_rock = {
  rock1 => meshy_rock : pos (22.532026,-51.0,148.68306), scale 2, rotate (0,0,0), shadow mode on, collision shape box, mass 3.0
  return rock1
}

var rock = create_rock()
rock.move forward 10 in 1 seconds
```

`return` without a value still works as an early exit:

```scenemax
check_level = {
  if level_done == true then {
    return
  }

  logger.info "level still running"
}
```

Value-returning functions are evaluated synchronously. Do not use `async`, interval execution, or repeating `do` loops for functions that must return a value.
