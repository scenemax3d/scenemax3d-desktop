package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Compiler command for runtime UI text messaging:
 *   UI.layer1.panel1.text1.message("Hello", TextEffect.typewriter, 2)
 */
public class UIMessageCommand extends ActionStatementBase {

    public String uiName;
    public String layerName;
    public String widgetPath;
    public List<String> effectNames = new ArrayList<>();

    public SceneMaxParser.Logical_expressionContext messageExpr;
    public SceneMaxParser.Logical_expressionContext durationExpr;

    @Override
    public boolean validate(ProgramDef prg) {
        return layerName != null
                && widgetPath != null
                && !widgetPath.isEmpty()
                && !effectNames.isEmpty()
                && messageExpr != null
                && durationExpr != null;
    }
}
