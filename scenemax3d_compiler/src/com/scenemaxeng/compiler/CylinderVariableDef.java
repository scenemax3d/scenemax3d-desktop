package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;
import org.antlr.v4.runtime.ParserRuleContext;

public class CylinderVariableDef extends VariableDef {

    public ParserRuleContext radiusTopExpr;
    public ParserRuleContext radiusBottomExpr;
    public ParserRuleContext heightExpr;
    public SceneMaxParser.Logical_expressionContext materialExpr;
    public boolean isCollider;

    public CylinderVariableDef() {
        this.varType = VariableDef.VAR_TYPE_CYLINDER;
    }

    @Override
    public boolean validate(ProgramDef prg) {
        return true; // Cylinder is a built-in resource, no need to check its existence
    }
}
