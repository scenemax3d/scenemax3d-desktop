package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

/**
 * Compiler command for runtime UI easing:
 *   UI.layer1.panel1.ease("EaseInQuad", Up, 0.5)
 *   UI.hud.layer1.ease("EaseOutBounce", Left, 1)
 */
public class UIEaseCommand extends ActionStatementBase {

    public String uiName;
    public String layerName;
    public String widgetPath;
    public String directionName;

    public SceneMaxParser.Logical_expressionContext easingExpr;
    public SceneMaxParser.Logical_expressionContext durationExpr;

    @Override
    public boolean validate(ProgramDef prg) {
        return layerName != null
                && directionName != null
                && easingExpr != null
                && durationExpr != null;
    }
}
