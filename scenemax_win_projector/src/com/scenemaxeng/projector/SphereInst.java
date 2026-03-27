package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.SphereVariableDef;

public class SphereInst extends ModelInst {

    public ActionLogicalExpressionVm radiusExpr;
    public ActionLogicalExpressionVm materialExpr;

    public SphereInst(SphereVariableDef varDef, SceneMaxScope scope) {
        super(null,varDef,scope);

        if(varDef.radiusExpr!=null) {
            this.radiusExpr=new ActionLogicalExpressionVm(varDef.radiusExpr,scope);
        }

        if(varDef.materialExpr!=null) {
            this.materialExpr=new ActionLogicalExpressionVm(varDef.materialExpr,scope);
        }

    }
}
