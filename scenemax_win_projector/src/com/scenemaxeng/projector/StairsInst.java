package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.StairsVariableDef;

public class StairsInst extends ModelInst {

    public ActionLogicalExpressionVm widthExpr;
    public ActionLogicalExpressionVm stepHeightExpr;
    public ActionLogicalExpressionVm stepDepthExpr;
    public ActionLogicalExpressionVm stepCountExpr;
    public ActionLogicalExpressionVm materialExpr;

    public StairsInst(StairsVariableDef varDef, SceneMaxScope scope) {
        super(null, varDef, scope);

        if (varDef.widthExpr != null) {
            this.widthExpr = new ActionLogicalExpressionVm(varDef.widthExpr, scope);
        }
        if (varDef.stepHeightExpr != null) {
            this.stepHeightExpr = new ActionLogicalExpressionVm(varDef.stepHeightExpr, scope);
        }
        if (varDef.stepDepthExpr != null) {
            this.stepDepthExpr = new ActionLogicalExpressionVm(varDef.stepDepthExpr, scope);
        }
        if (varDef.stepCountExpr != null) {
            this.stepCountExpr = new ActionLogicalExpressionVm(varDef.stepCountExpr, scope);
        }
        if (varDef.materialExpr != null) {
            this.materialExpr = new ActionLogicalExpressionVm(varDef.materialExpr, scope);
        }
    }
}
