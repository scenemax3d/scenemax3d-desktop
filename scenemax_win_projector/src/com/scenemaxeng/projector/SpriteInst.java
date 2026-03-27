package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.SpriteDef;
import com.scenemaxeng.compiler.VariableDef;

public class SpriteInst extends EntityInstBase{

    public SpriteDef spriteDef;

    public ActionLogicalExpressionVm xExpr;
    public ActionLogicalExpressionVm yExpr;
    public ActionLogicalExpressionVm zExpr;
    public RunTimeVarDef entityForPos;
    public RunTimeVarDef entityForRot;

    public SpriteInst(SpriteDef sd, VariableDef varDef, SceneMaxScope scope) {
        this.spriteDef=sd;
        this.scope=scope;
        this.varDef=varDef;
        this.thresholdX=80;

        if(varDef.xExpr!=null) {
            this.xExpr=new ActionLogicalExpressionVm(varDef.xExpr,scope);
            this.yExpr=new ActionLogicalExpressionVm(varDef.yExpr,scope);
            this.zExpr=new ActionLogicalExpressionVm(varDef.zExpr,scope);

        }


    }

}
