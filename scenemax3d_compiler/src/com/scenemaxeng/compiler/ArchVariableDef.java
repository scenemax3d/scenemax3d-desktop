package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;
import org.antlr.v4.runtime.ParserRuleContext;

public class ArchVariableDef extends VariableDef {

    public ParserRuleContext widthExpr;
    public ParserRuleContext heightExpr;
    public ParserRuleContext depthExpr;
    public ParserRuleContext thicknessExpr;
    public ParserRuleContext segmentsExpr;
    public SceneMaxParser.Logical_expressionContext materialExpr;
    public boolean isCollider;

    public ArchVariableDef() {
        this.varType = VariableDef.VAR_TYPE_ARCH;
    }

    @Override
    public boolean validate(ProgramDef prg) {
        return true;
    }
}
