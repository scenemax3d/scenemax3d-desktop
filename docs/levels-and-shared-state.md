# Levels & Shared State

## Switching Levels

Switch to a new level. The current level is completely cleared and a new level from the specified folder starts:

```scenemax
switch to "level2"
```

Switch back to the main game file:

```scenemax
switch to main
```

## Shared Variables

Variables declared with `shared` persist across level switches:

```scenemax
shared var score = 0
```

## Shared Models

Models declared with `shared` persist across level switches:

```scenemax
shared player => sinbad
shared my_box => box
```
