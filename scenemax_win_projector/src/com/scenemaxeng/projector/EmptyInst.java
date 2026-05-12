package com.scenemaxeng.projector;

import com.jme3.scene.Node;
import com.scenemaxeng.compiler.VariableDef;

public class EmptyInst extends EntityInstBase {

    public final Node node = new Node();

    public EmptyInst(VariableDef varDef, SceneMaxScope scope) {
        this.varDef = varDef;
        this.scope = scope;
    }
}
