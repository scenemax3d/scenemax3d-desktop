package com.scenemax.designer.material;

public enum MaterialPreviewShape {
    BOX("Box"),
    SPHERE("Sphere");

    private final String displayName;

    MaterialPreviewShape(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
