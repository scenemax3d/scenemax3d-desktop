package com.scenemaxeng.compiler;

/**
 * Compiler command for:
 *   UI.layer1.show
 *   UI.layer1.hide
 *   UI.layer1.panel1.show
 *   UI.layer1.panel1.hide
 *   UI.layer1.panel1.button1.show
 *   UI.layer1.panel1.button1.hide
 *
 * The dotPath is the path after "UI." and before ".show"/".hide".
 * Examples:
 *   "UI.hud.show"                    → uiName="hud", dotPath="", show=true   (layer-level)
 *   "UI.hud.panel1.show"             → uiName="hud", dotPath="panel1", show=true
 *   "UI.hud.panel1.button1.hide"     → uiName="hud", dotPath="panel1.button1", show=false
 *
 * Note: The first segment after "UI." is always the loaded UI name,
 * the second segment is the layer name, and the remaining segments
 * navigate the widget tree.
 */
public class UIShowHideCommand extends ActionStatementBase {

    public String uiName;      // the loaded UI system name
    public String layerName;   // the layer name
    public String widgetPath;  // dot-separated path within the layer (empty = target is the layer itself)
    public boolean show;       // true = show, false = hide

    @Override
    public boolean validate(ProgramDef prg) {
        return uiName != null && layerName != null;
    }
}
