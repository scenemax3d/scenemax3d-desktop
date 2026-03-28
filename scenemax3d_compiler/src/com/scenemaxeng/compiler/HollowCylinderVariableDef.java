package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;
import org.antlr.v4.runtime.ParserRuleContext;

public class HollowCylinderVariableDef extends VariableDef {

    public ParserRuleContext radiusTopExpr;
    public ParserRuleContext radiusBottomExpr;
    public ParserRuleContext innerRadiusTopExpr;
    public ParserRuleContext innerRadiusBottomExpr;
    public ParserRuleContext heightExpr;
    public SceneMaxParser.Logical_expressionContext materialExpr;
    public boolean isCollider;

    public HollowCylinderVariableDef() {
        this.varType = VariableDef.VAR_TYPE_HOLLOW_CYLINDER;
    }

    @Override
    public boolean validate(ProgramDef prg) {
        return true;
    }
}
