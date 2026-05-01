package com.scenemaxeng.common.types;

public interface SceneMaxPluginAction {
    String getId();
    String getLabel();
    String getTooltip();

    default String getIconResourcePath() {
        return "";
    }

    void perform(SceneMaxPluginContext context);
}
