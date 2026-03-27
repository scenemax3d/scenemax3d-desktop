package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ModelDef;
import com.scenemaxeng.compiler.VariableDef;

public class ModelInst extends EntityInstBase{

    public ModelDef modelDef;
    public ActionLogicalExpressionVm scaleExpr;
    public ActionLogicalExpressionVm massExpr;
    public ActionLogicalExpressionVm xExpr;
    public ActionLogicalExpressionVm yExpr;
    public ActionLogicalExpressionVm zExpr;
    public ActionLogicalExpressionVm rxExpr;
    public ActionLogicalExpressionVm ryExpr;
    public ActionLogicalExpressionVm rzExpr;
    public RunTimeVarDef entityForPos;
    public RunTimeVarDef entityForRot;

    public ModelInst(ModelDef md, VariableDef varDef, SceneMaxScope scope) {
        this.modelDef=md;
        this.scope=scope;
        this.varDef=varDef;

        if(varDef.scaleExpr!=null) {
            this.scaleExpr = new ActionLogicalExpressionVm(varDef.scaleExpr,scope);
        }

        if(varDef.massExpr!=null) {
            this.massExpr = new ActionLogicalExpressionVm(varDef.massExpr,scope);
        }

        if(varDef.xExpr!=null) {
            this.xExpr=new ActionLogicalExpressionVm(varDef.xExpr,scope);
            this.yExpr=new ActionLogicalExpressionVm(varDef.yExpr,scope);
            this.zExpr=new ActionLogicalExpressionVm(varDef.zExpr,scope);

        }

        if(varDef.useVerbalTurn) {
            if(varDef.rxExpr!=null) {
                this.rxExpr=new ActionLogicalExpressionVm(varDef.rxExpr,scope);
            } else if(varDef.ryExpr!=null) {
                this.ryExpr=new ActionLogicalExpressionVm(varDef.ryExpr,scope);
            } else if(varDef.rzExpr!=null) {
                this.rzExpr=new ActionLogicalExpressionVm(varDef.rzExpr,scope);
            }
        } else if(varDef.rxExpr!=null) {
            this.rxExpr=new ActionLogicalExpressionVm(varDef.rxExpr,scope);
            this.ryExpr=new ActionLogicalExpressionVm(varDef.ryExpr,scope);
            this.rzExpr=new ActionLogicalExpressionVm(varDef.rzExpr,scope);
        }

    }

}
