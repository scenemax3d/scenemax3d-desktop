package com.scenemax.designer.shader;

public enum ShaderPreviewTarget {
    BOX("Box"),
    SPHERE("Sphere"),
    SPRITE("Sprite");

    private final String displayName;

    ShaderPreviewTarget(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
