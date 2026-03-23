# Path Replay

*Available since version 1.7.4*

Path replay allows you to record and play back movement paths using arrays. Every 6 numbers in the array represent one point in space (x, y, z, rx, ry, rz).

## Basic Replay

```scenemax
var points = [0,0,0,0,0,0,  1,1,10,0,90,0,  2,2,0,0,0,0]
s => sinbad
s.replay points in 10 seconds
```

## Start from a Specific Point

Start playback from point 5 in the array. Since each point uses 6 numbers, the system maps this to the correct array index:

```scenemax
s.replay points start at 5 in 10 seconds
```

## Loop Replay

Loop the replay with a continue condition. In this example, the loop runs forever (`1==1`):

```scenemax
s.replay points in 10 seconds loop while 1==1
```

## Replay Controls

Stop replay:

```scenemax
s.replay stop
```

Pause replay:

```scenemax
s.replay pause
```

Resume from the pause point:

```scenemax
s.replay resume
```

## Position Offsets

Add an offset to the replayed path. Here, Y gets +3 meters height and Z gets -5 meters:

```scenemax
s.replay points in 10 seconds : y offset 3 and z offset -5
```

## Rotation Offsets

Add a rotation offset. Here, 180 degrees around the Y axis:

```scenemax
s.replay points in 10 seconds : ry offset 180
```

## Change Speed

Change the replay speed mid-playback:

```scenemax
s.replay points in 10 seconds
wait 5 seconds
s.replay speed 20 seconds
```

## Switch Arrays

Switch to a different path array during playback:

```scenemax
s.replay points in 10 seconds
wait 5 seconds
s.replay switch to points2
```
