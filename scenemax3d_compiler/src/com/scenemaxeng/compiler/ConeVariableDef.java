package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;
import org.antlr.v4.runtime.ParserRuleContext;

public class ConeVariableDef extends VariableDef {

    public ParserRuleContext radiusTopExpr;
    public ParserRuleContext radiusBottomExpr;
    public ParserRuleContext heightExpr;
    public SceneMaxParser.Logical_expressionContext materialExpr;
    public boolean isCollider;

    public ConeVariableDef() {
        this.varType = VariableDef.VAR_TYPE_CONE;
    }

    @Override
    public boolean validate(ProgramDef prg) {
        return true;
    }
}
