package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;
import org.antlr.v4.runtime.ParserRuleContext;

public class WedgeVariableDef extends VariableDef {

    public ParserRuleContext sizeXExpr;
    public ParserRuleContext sizeYExpr;
    public ParserRuleContext sizeZExpr;
    public SceneMaxParser.Logical_expressionContext materialExpr;
    public boolean isCollider;

    public WedgeVariableDef() {
        this.varType = VariableDef.VAR_TYPE_WEDGE;
    }

    @Override
    public boolean validate(ProgramDef prg) {
        return true;
    }
}
