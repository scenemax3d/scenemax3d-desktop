package com.scenemaxeng.projector;

import com.jme3.scene.Node;
import com.scenemaxeng.compiler.VariableDef;

public class EffekseerInst extends EntityInstBase {

    public final Node node;
    public RunTimeVarDef entityForPos;
    public RunTimeVarDef entityForRot;
    public final String effectResourceName;
    public final String assetId;
    public final String effectPath;
    public long nativeContextHandle;
    public boolean visible = true;
    public boolean loaded = false;
    public boolean pendingPlay = false;
    public boolean playing = false;
    public float playbackSpeed = 1.0f;
    public final float[] dynamicInputs = new float[] {0f, 0f, 0f, 0f};

    public EffekseerInst(VariableDef varDef, SceneMaxScope scope, String effectResourceName, String assetId, String effectPath) {
        this.varDef = varDef;
        this.scope = scope;
        this.effectResourceName = effectResourceName;
        this.assetId = assetId;
        this.effectPath = effectPath;
        this.node = new Node(varDef.varName + "@" + scope.scopeId);
        this.node.setUserData("key", this.node.getName());
        this.visible = varDef.visible;
    }
}
