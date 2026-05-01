package com.scenemaxeng.common.types;

import javax.swing.JComponent;

public interface SceneMaxAssetProvider {
    String getId();
    String getDisplayName();
    JComponent createBrowserComponent(SceneMaxPluginContext context);
}
