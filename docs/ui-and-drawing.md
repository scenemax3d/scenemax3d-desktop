# UI & Drawing

## Header Text

Print text on the screen:

```scenemax
header.print "hello world"
```

Print text with a variable value:

```scenemax
header.print "score: "+score
```

## Drawing Sprites on Screen

Draw a 2D sprite on the screen at a screen position (for UI elements):

```scenemax
sys.draw runningman : pos (10,10)
```

## Materials as Textures

Apply a sprite as a texture on a box or sphere:

```scenemax
b is a box : material="shira1"
s is a sphere : material="runningman"
```

Change a material at runtime:

```scenemax
s is a sphere : material = "pond"
wait 5 seconds
s.material="shira1"
```
