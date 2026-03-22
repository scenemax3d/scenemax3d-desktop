package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;
import org.antlr.v4.runtime.ParserRuleContext;

public class BoxVariableDef extends VariableDef{

    public SceneMaxParser.Logical_expressionContext materialExpr;
    public ParserRuleContext sizeX;
    public ParserRuleContext sizeY;
    public ParserRuleContext sizeZ;
    public boolean isCollider;

    public BoxVariableDef() {
        this.varType = VariableDef.VAR_TYPE_BOX;
    }

    @Override
    public boolean validate(ProgramDef prg) {
        return true;// Sphere is a built-in resource. not need to check its existence
    }
}
