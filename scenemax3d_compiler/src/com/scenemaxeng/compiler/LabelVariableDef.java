package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class LabelVariableDef extends VariableDef {

    public SceneMaxParser.Logical_expressionContext textExpr;
    public SceneMaxParser.Logical_expressionContext widthExpr;
    public SceneMaxParser.Logical_expressionContext heightExpr;
    public String font;
    public String style;
    public SceneMaxParser.Logical_expressionContext transparencyExpr;

    public LabelVariableDef() {
        this.varType = VariableDef.VAR_TYPE_LABEL;
        this.resName = "label";
    }

    @Override
    public boolean validate(ProgramDef prg) {
        return true;
    }
}
