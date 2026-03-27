package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.BoxVariableDef;

public class BoxInst extends ModelInst{

    public ActionLogicalExpressionVm materialExpr;

    public BoxInst(BoxVariableDef varDef, SceneMaxScope scope) {
        super(null,varDef,scope);

    }
}
