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
