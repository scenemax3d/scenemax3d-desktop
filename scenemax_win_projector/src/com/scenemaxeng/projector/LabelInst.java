package com.scenemaxeng.projector;

import com.jme3.font.BitmapText;
import com.jme3.scene.Node;
import com.scenemaxeng.compiler.LabelVariableDef;

public class LabelInst extends EntityInstBase {

    public final LabelVariableDef labelDef;
    public final Node node = new Node();
    public BitmapText text;
    public ActionLogicalExpressionVm xExpr;
    public ActionLogicalExpressionVm yExpr;
    public ActionLogicalExpressionVm zExpr;
    public ActionLogicalExpressionVm widthExpr;
    public ActionLogicalExpressionVm heightExpr;
    public ActionLogicalExpressionVm scaleExpr;
    public ActionLogicalExpressionVm scaleXExpr;
    public ActionLogicalExpressionVm scaleYExpr;
    public ActionLogicalExpressionVm scaleZExpr;
    public ActionLogicalExpressionVm transparencyExpr;
    public RunTimeVarDef entityForPos;

    public LabelInst(LabelVariableDef varDef, SceneMaxScope scope) {
        this.labelDef = varDef;
        this.varDef = varDef;
        this.scope = scope;

        if (varDef.xExpr != null) {
            this.xExpr = new ActionLogicalExpressionVm(varDef.xExpr, scope);
            this.yExpr = new ActionLogicalExpressionVm(varDef.yExpr, scope);
            this.zExpr = new ActionLogicalExpressionVm(varDef.zExpr, scope);
        }
        if (varDef.scaleExpr != null) {
            this.scaleExpr = new ActionLogicalExpressionVm(varDef.scaleExpr, scope);
        }
        if (varDef.widthExpr != null) {
            this.widthExpr = new ActionLogicalExpressionVm(varDef.widthExpr, scope);
            this.heightExpr = new ActionLogicalExpressionVm(varDef.heightExpr, scope);
        }
        if (varDef.scaleXExpr != null) {
            this.scaleXExpr = new ActionLogicalExpressionVm(varDef.scaleXExpr, scope);
            this.scaleYExpr = new ActionLogicalExpressionVm(varDef.scaleYExpr, scope);
            this.scaleZExpr = new ActionLogicalExpressionVm(varDef.scaleZExpr, scope);
        }
        if (varDef.transparencyExpr != null) {
            this.transparencyExpr = new ActionLogicalExpressionVm(varDef.transparencyExpr, scope);
        }
    }
}
