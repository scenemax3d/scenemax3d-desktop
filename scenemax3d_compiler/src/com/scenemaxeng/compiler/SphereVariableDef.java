package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser.Logical_expressionContext;

public class SphereVariableDef extends VariableDef {

    public Logical_expressionContext radiusExpr;
    public Logical_expressionContext materialExpr;
    public boolean isCollider;

    public SphereVariableDef() {
        this.varType = VAR_TYPE_SPHERE;
    }

    @Override
    public boolean validate(ProgramDef prg) {
        return true;// Sphere is a built-in resource. not need to check its existence
    }
}
