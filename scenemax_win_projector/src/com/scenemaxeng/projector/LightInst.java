package com.scenemaxeng.projector;

import com.jme3.light.Light;
import com.jme3.post.Filter;
import com.jme3.scene.Node;
import com.scenemaxeng.compiler.LightVariableDef;

import java.util.ArrayList;
import java.util.List;

public class LightInst extends EntityInstBase {

    public final Node node = new Node("SceneMaxLight");
    public final List<Filter> shadowFilters = new ArrayList<>();
    public Light light;

    public LightInst(LightVariableDef varDef, SceneMaxScope scope) {
        this.scope = scope;
        this.varDef = varDef;
        this.node.setName(varDef.varName + "_light");
    }

    public LightVariableDef getLightDef() {
        return (LightVariableDef) varDef;
    }
}
