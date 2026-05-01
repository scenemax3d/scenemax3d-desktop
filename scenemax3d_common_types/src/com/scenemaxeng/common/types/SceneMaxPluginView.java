package com.scenemaxeng.common.types;

import javax.swing.JComponent;

public interface SceneMaxPluginView {
    String getId();
    String getTitle();
    JComponent createComponent(SceneMaxPluginContext context);
}
