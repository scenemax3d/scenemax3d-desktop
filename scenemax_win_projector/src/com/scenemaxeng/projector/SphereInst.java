package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.SphereVariableDef;

public class SphereInst extends ModelInst {

    public ActionLogicalExpression radiusExpr;
    public ActionLogicalExpression materialExpr;

    public SphereInst(SphereVariableDef varDef, SceneMaxScope scope) {
        super(null,varDef,scope);

        if(varDef.radiusExpr!=null) {
            this.radiusExpr=new ActionLogicalExpression(varDef.radiusExpr,scope);
        }

        if(varDef.materialExpr!=null) {
            this.materialExpr=new ActionLogicalExpression(varDef.materialExpr,scope);
        }

    }
}
