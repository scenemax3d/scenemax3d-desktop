package com.scenemax.designer.video;

public enum VideoPreviewShape {
    PANE("Pane"),
    BOX("Box"),
    SPHERE("Sphere");

    private final String label;

    VideoPreviewShape(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
