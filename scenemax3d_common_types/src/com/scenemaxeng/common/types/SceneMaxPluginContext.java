package com.scenemaxeng.common.types;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

public interface SceneMaxPluginContext {
    String getProjectPath();
    String getResourcesFolder();
    String getScriptsFolder();

    String getSetting(String key, String defaultValue);
    void setSetting(String key, String value);

    void registerToolbarAction(SceneMaxPluginAction action);
    void registerMenuAction(String menuPath, SceneMaxPluginAction action);
    void registerView(SceneMaxPluginView view);
    void registerAssetProvider(SceneMaxAssetProvider provider);

    void openView(String viewId);
    SceneMaxPluginImportResult importModelAsset(File modelFile, String requestedName, JSONObject metadata) throws IOException;
    void previewModelAsset(File modelFile, String requestedName, JSONObject metadata) throws IOException;
    void refreshProject();
    void showStatus(String message);
}
