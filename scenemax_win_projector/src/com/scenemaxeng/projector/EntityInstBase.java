package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.VariableDef;

public class EntityInstBase {

    public VariableDef varDef;
    public SceneMaxScope scope;
    public String entityKey;
    public float thresholdX=100;

    // Cached runtime name to avoid repeated string concatenation (varName + "@" + scopeId).
    // Computed lazily on first access. Safe because varDef and scope are set once at construction.
    private String cachedVarRunTimeName;

    public String getVarRunTimeName() {
        String name = cachedVarRunTimeName;
        if (name == null) {
            name = this.varDef.varName + "@" + this.scope.scopeId;
            cachedVarRunTimeName = name;
        }
        return name;
    }
}
