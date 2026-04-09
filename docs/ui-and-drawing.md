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

## UI Runtime Messages

Animate a text widget at runtime:

```scenemax
UI.layer1.panel1.text1.message("Hello World", TextEffect.typewriter, 2)
```

You can also target a named UI system:

```scenemax
UI.hud.layer1.dialogText.message("Mission Start", TextEffect.zoom_in, 1.2)
```

Effects can be combined when they make sense:

```scenemax
UI.layer1.dialogText.message("Mission Start", TextEffect.fade_in | TextEffect.zoom_in, 1.2)
```

Supported `TextEffect` values in the current UI/font system:

- `typewriter`
- `typewriter_zoom_in`
- `word_reveal`
- `chunk_reveal`
- `zoom_in`
- `zoom_out`
- `fade_in`
- `fade_out`

Notes:

- The last parameter is the total animation duration in seconds.
- This version animates whole-text reveal/scale/fade effects that work with the current `BitmapText` pipeline.
- Per-glyph wave, wobble, bounce, and inline rich-text effects are not implemented yet.

## Drawing Sprites on Screen

Draw a 2D sprite on the screen at a screen position (for UI elements):

```scenemax
sys.draw runningman : pos (10,10)
```

## Materials as Textures

Apply a sprite as a texture on a box or sphere:

```scenemax
b => box : material="shira1"
s => sphere : material="runningman"
```

Change a material at runtime:

```scenemax
s => sphere : material = "pond"
wait 5 seconds
s.material="shira1"
```
