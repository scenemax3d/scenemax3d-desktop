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

## Selectable List Views

List-view widgets authored in `.smui` files render at runtime with their design-time headers, rows, fonts, selected row, and style preset.

You can update list data with normal UI property assignment:

```scenemax
UI.layer1.players.headers = "Name | Score | Status"
UI.layer1.players.rows = "Alice | 10 | Ready; Bob | 5 | Waiting"
UI.layer1.players.addrow = "Cara | 7 | Ready"
UI.layer1.players.selected = 1
UI.layer1.players.style = "dark"
UI.layer1.players.columnwidths = "180 | 90 | 150"
UI.layer1.players.headerfontsize = 18
UI.layer1.players.rowfontsize = 14
```

Rows are separated by semicolons or new lines. Cells and column widths are separated with `|`. Cell text wraps by word when it does not fit the column width. In the designer, selected list views can also resize columns by dragging the vertical dividers.

## UI Ease Animations

Slide a UI layer or widget in and out of view with built-in easing:

```scenemax
UI.layer1.my_panel.ease("EaseInQuad", Up, 0.5)
UI.layer1.my_panel.ease("EaseOutBounce", Left, 1)
UI.hud.layer1.ease("EaseOutElastic", Down, 1)
```

`EaseIn...` functions end with the target shown on screen.

`EaseOut...` functions end with the target hidden.

Supported ease names:

- `EaseInQuad`
- `EaseInCubic`
- `EaseInQuart`
- `EaseInQuint`
- `EaseInSine`
- `EaseInCirc`
- `EaseInExpo`
- `EaseInBack`
- `EaseInElastic`
- `EaseInBounce`
- `EaseInPower`
- `EaseInBezier`
- `EaseInCustom`
- `EaseOutQuad`
- `EaseOutCubic`
- `EaseOutQuart`
- `EaseOutQuint`
- `EaseOutSine`
- `EaseOutCirc`
- `EaseOutExpo`
- `EaseOutBack`
- `EaseOutElastic`
- `EaseOutBounce`
- `EaseOutPower`
- `EaseOutBezier`
- `EaseOutCustom`

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
