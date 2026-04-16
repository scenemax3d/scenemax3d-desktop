package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;
import org.antlr.v4.runtime.ParserRuleContext;

public class StairsVariableDef extends VariableDef {

    public ParserRuleContext widthExpr;
    public ParserRuleContext stepHeightExpr;
    public ParserRuleContext stepDepthExpr;
    public ParserRuleContext stepCountExpr;
    public SceneMaxParser.Logical_expressionContext materialExpr;
    public boolean isCollider;

    public StairsVariableDef() {
        this.varType = VariableDef.VAR_TYPE_STAIRS;
    }

    @Override
    public boolean validate(ProgramDef prg) {
        return true;
    }
}
