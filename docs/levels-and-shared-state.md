# Scenes & Shared State
You can switch between scenes (game levels / starting screen / menues etc.) by using the switch command.
Each scene needs to have its **own folder** with a **main** file in the project structure:

<img width="485" height="270" alt="image" src="https://github.com/user-attachments/assets/924d629c-0235-4e7c-9ce9-580f5b7ca797" />

## Switching Scenes

Switch to a new scene. The current scene is completely cleared and a new scene from the specified folder starts:

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

Models declared with `shared` persist across scenes switches:

```scenemax
shared player => sinbad
shared my_box => box
```
