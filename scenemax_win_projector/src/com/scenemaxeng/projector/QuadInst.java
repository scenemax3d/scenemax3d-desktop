package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.QuadVariableDef;

public class QuadInst extends ModelInst {

    public ActionLogicalExpressionVm widthExpr;
    public ActionLogicalExpressionVm heightExpr;
    public ActionLogicalExpressionVm materialExpr;

    public QuadInst(QuadVariableDef varDef, SceneMaxScope scope) {
        super(null, varDef, scope);

        if (varDef.widthExpr != null) {
            this.widthExpr = new ActionLogicalExpressionVm(varDef.widthExpr, scope);
        }
        if (varDef.heightExpr != null) {
            this.heightExpr = new ActionLogicalExpressionVm(varDef.heightExpr, scope);
        }
        if (varDef.materialExpr != null) {
            this.materialExpr = new ActionLogicalExpressionVm(varDef.materialExpr, scope);
        }
    }
}
