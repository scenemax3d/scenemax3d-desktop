package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.HollowCylinderVariableDef;

public class HollowCylinderInst extends ModelInst {

    public ActionLogicalExpressionVm radiusTopExpr;
    public ActionLogicalExpressionVm radiusBottomExpr;
    public ActionLogicalExpressionVm innerRadiusTopExpr;
    public ActionLogicalExpressionVm innerRadiusBottomExpr;
    public ActionLogicalExpressionVm heightExpr;
    public ActionLogicalExpressionVm materialExpr;

    public HollowCylinderInst(HollowCylinderVariableDef varDef, SceneMaxScope scope) {
        super(null, varDef, scope);

        if (varDef.radiusTopExpr != null) {
            this.radiusTopExpr = new ActionLogicalExpressionVm(varDef.radiusTopExpr, scope);
        }
        if (varDef.radiusBottomExpr != null) {
            this.radiusBottomExpr = new ActionLogicalExpressionVm(varDef.radiusBottomExpr, scope);
        }
        if (varDef.innerRadiusTopExpr != null) {
            this.innerRadiusTopExpr = new ActionLogicalExpressionVm(varDef.innerRadiusTopExpr, scope);
        }
        if (varDef.innerRadiusBottomExpr != null) {
            this.innerRadiusBottomExpr = new ActionLogicalExpressionVm(varDef.innerRadiusBottomExpr, scope);
        }
        if (varDef.heightExpr != null) {
            this.heightExpr = new ActionLogicalExpressionVm(varDef.heightExpr, scope);
        }
        if (varDef.materialExpr != null) {
            this.materialExpr = new ActionLogicalExpressionVm(varDef.materialExpr, scope);
        }
    }
}
