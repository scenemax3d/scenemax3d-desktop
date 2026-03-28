package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.CylinderVariableDef;

public class CylinderInst extends ModelInst {

    public ActionLogicalExpressionVm radiusTopExpr;
    public ActionLogicalExpressionVm radiusBottomExpr;
    public ActionLogicalExpressionVm heightExpr;
    public ActionLogicalExpressionVm materialExpr;

    public CylinderInst(CylinderVariableDef varDef, SceneMaxScope scope) {
        super(null, varDef, scope);

        if (varDef.radiusTopExpr != null) {
            this.radiusTopExpr = new ActionLogicalExpressionVm(varDef.radiusTopExpr, scope);
        }
        if (varDef.radiusBottomExpr != null) {
            this.radiusBottomExpr = new ActionLogicalExpressionVm(varDef.radiusBottomExpr, scope);
        }
        if (varDef.heightExpr != null) {
            this.heightExpr = new ActionLogicalExpressionVm(varDef.heightExpr, scope);
        }
        if (varDef.materialExpr != null) {
            this.materialExpr = new ActionLogicalExpressionVm(varDef.materialExpr, scope);
        }
    }
}
