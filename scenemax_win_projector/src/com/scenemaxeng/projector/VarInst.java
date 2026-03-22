package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.VariableDef;

import java.util.List;

public class VarInst {

    public VariableDef varDef;
    public SceneMaxScope scope;
    public Object value;
    public List<Object> values;
    public int varType;
    public VariableDef varReference;


    public VarInst(VariableDef vd, SceneMaxScope scope) {
        this.varDef=vd;
        this.scope=scope;
    }

}
