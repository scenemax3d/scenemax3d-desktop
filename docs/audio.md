# Audio

## Playing Audio

Play a sound:

```scenemax
audio.play "walking1"
```

Play audio in a loop:

```scenemax
audio.play "walking1" loop
```

## Volume Control

*Available since version 1.7.2*

Set the volume (0-100):

```scenemax
audio.play "walking1" : volume 10 loop
```

## Stopping Audio

Stop a looping audio track:

```scenemax
audio.play "walking1" loop
wait 10 seconds
audio.stop "walking1"
```
