package com.scenemax.designer.shader;

public enum ShaderPreviewTarget {
    BOX("Box"),
    SPHERE("Sphere"),
    SPRITE("Sprite"),
    MODEL("3D Model");

    private final String displayName;

    ShaderPreviewTarget(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
