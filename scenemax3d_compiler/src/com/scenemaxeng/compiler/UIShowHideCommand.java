package com.scenemaxeng.compiler;

/**
 * Compiler command for:
 *   UI.layer1.show
 *   UI.layer1.hide
 *   UI.layer1.panel1.show
 *   UI.layer1.panel1.hide
 *   UI.hud.layer1.panel1.button1.show
 *   UI.hud.layer1.panel1.button1.hide
 *
 * Scripts may omit the UI document name and target the active loaded UI,
 * or include it explicitly as UI.<uiName>.<layer>...
 */
public class UIShowHideCommand extends ActionStatementBase {

    public String uiName;      // the loaded UI system name, or null for the active UI
    public String layerName;   // the layer name
    public String widgetPath;  // dot-separated path within the layer (empty = target is the layer itself)
    public boolean show;       // true = show, false = hide

    @Override
    public boolean validate(ProgramDef prg) {
        return layerName != null;
    }
}
