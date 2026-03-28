package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;
import org.antlr.v4.runtime.ParserRuleContext;

public class QuadVariableDef extends VariableDef {

    public ParserRuleContext widthExpr;
    public ParserRuleContext heightExpr;
    public SceneMaxParser.Logical_expressionContext materialExpr;
    public boolean isCollider;

    public QuadVariableDef() {
        this.varType = VariableDef.VAR_TYPE_QUAD;
    }

    @Override
    public boolean validate(ProgramDef prg) {
        return true; // Quad is a built-in resource, no need to check its existence
    }
}
