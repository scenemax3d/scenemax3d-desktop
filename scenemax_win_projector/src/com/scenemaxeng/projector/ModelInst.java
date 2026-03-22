package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ModelDef;
import com.scenemaxeng.compiler.VariableDef;

public class ModelInst extends EntityInstBase{

    public ModelDef modelDef;
    public ActionLogicalExpression scaleExpr;
    public ActionLogicalExpression massExpr;
    public ActionLogicalExpression xExpr;
    public ActionLogicalExpression yExpr;
    public ActionLogicalExpression zExpr;
    public ActionLogicalExpression rxExpr;
    public ActionLogicalExpression ryExpr;
    public ActionLogicalExpression rzExpr;
    public RunTimeVarDef entityForPos;
    public RunTimeVarDef entityForRot;

    public ModelInst(ModelDef md, VariableDef varDef, SceneMaxScope scope) {
        this.modelDef=md;
        this.scope=scope;
        this.varDef=varDef;

        if(varDef.scaleExpr!=null) {
            this.scaleExpr = new ActionLogicalExpression(varDef.scaleExpr,scope);
        }

        if(varDef.massExpr!=null) {
            this.massExpr = new ActionLogicalExpression(varDef.massExpr,scope);
        }

        if(varDef.xExpr!=null) {
            this.xExpr=new ActionLogicalExpression(varDef.xExpr,scope);
            this.yExpr=new ActionLogicalExpression(varDef.yExpr,scope);
            this.zExpr=new ActionLogicalExpression(varDef.zExpr,scope);

        }

        if(varDef.useVerbalTurn) {
            if(varDef.rxExpr!=null) {
                this.rxExpr=new ActionLogicalExpression(varDef.rxExpr,scope);
            } else if(varDef.ryExpr!=null) {
                this.ryExpr=new ActionLogicalExpression(varDef.ryExpr,scope);
            } else if(varDef.rzExpr!=null) {
                this.rzExpr=new ActionLogicalExpression(varDef.rzExpr,scope);
            }
        } else if(varDef.rxExpr!=null) {
            this.rxExpr=new ActionLogicalExpression(varDef.rxExpr,scope);
            this.ryExpr=new ActionLogicalExpression(varDef.ryExpr,scope);
            this.rzExpr=new ActionLogicalExpression(varDef.rzExpr,scope);
        }

    }

}
