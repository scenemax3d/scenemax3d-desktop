package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.WedgeVariableDef;

public class WedgeInst extends ModelInst {

    public ActionLogicalExpressionVm sizeXExpr;
    public ActionLogicalExpressionVm sizeYExpr;
    public ActionLogicalExpressionVm sizeZExpr;
    public ActionLogicalExpressionVm materialExpr;

    public WedgeInst(WedgeVariableDef varDef, SceneMaxScope scope) {
        super(null, varDef, scope);

        if (varDef.sizeXExpr != null) {
            this.sizeXExpr = new ActionLogicalExpressionVm(varDef.sizeXExpr, scope);
        }
        if (varDef.sizeYExpr != null) {
            this.sizeYExpr = new ActionLogicalExpressionVm(varDef.sizeYExpr, scope);
        }
        if (varDef.sizeZExpr != null) {
            this.sizeZExpr = new ActionLogicalExpressionVm(varDef.sizeZExpr, scope);
        }
        if (varDef.materialExpr != null) {
            this.materialExpr = new ActionLogicalExpressionVm(varDef.materialExpr, scope);
        }
    }
}
