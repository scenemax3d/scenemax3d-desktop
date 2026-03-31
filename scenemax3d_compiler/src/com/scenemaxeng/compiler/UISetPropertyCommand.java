package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

/**
 * Compiler command for property assignment on UI widgets:
 *   UI.hud.layer1.panel1.button1.text = "Hello world"
 *   UI.hud.layer1.text1.text = "Score: " + score
 *   UI.hud.layer1.text1.color = "#FF0000FF"
 *   UI.hud.layer1.image1.image = "textures/icon.png"
 *
 * The property name is the last segment before '='.
 * The value is a logical expression (can be a string literal, variable, or expression).
 */
public class UISetPropertyCommand extends ActionStatementBase {

    public String uiName;          // loaded UI system name
    public String layerName;       // layer name
    public String widgetPath;      // dot-separated path to the widget
    public String propertyName;    // property to set (text, color, image, fontSize, etc.)

    // The value expression — stored as a parser context for runtime evaluation
    public SceneMaxParser.Logical_expressionContext valueExpr;

    // For simple string literal values, pre-resolved at compile time
    public String stringValue;

    @Override
    public boolean validate(ProgramDef prg) {
        return uiName != null && layerName != null && propertyName != null;
    }
}
