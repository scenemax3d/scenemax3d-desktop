package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ArchVariableDef;

public class ArchInst extends ModelInst {

    public ActionLogicalExpressionVm widthExpr;
    public ActionLogicalExpressionVm heightExpr;
    public ActionLogicalExpressionVm depthExpr;
    public ActionLogicalExpressionVm thicknessExpr;
    public ActionLogicalExpressionVm segmentsExpr;
    public ActionLogicalExpressionVm materialExpr;

    public ArchInst(ArchVariableDef varDef, SceneMaxScope scope) {
        super(null, varDef, scope);

        if (varDef.widthExpr != null) {
            this.widthExpr = new ActionLogicalExpressionVm(varDef.widthExpr, scope);
        }
        if (varDef.heightExpr != null) {
            this.heightExpr = new ActionLogicalExpressionVm(varDef.heightExpr, scope);
        }
        if (varDef.depthExpr != null) {
            this.depthExpr = new ActionLogicalExpressionVm(varDef.depthExpr, scope);
        }
        if (varDef.thicknessExpr != null) {
            this.thicknessExpr = new ActionLogicalExpressionVm(varDef.thicknessExpr, scope);
        }
        if (varDef.segmentsExpr != null) {
            this.segmentsExpr = new ActionLogicalExpressionVm(varDef.segmentsExpr, scope);
        }
        if (varDef.materialExpr != null) {
            this.materialExpr = new ActionLogicalExpressionVm(varDef.materialExpr, scope);
        }
    }
}
