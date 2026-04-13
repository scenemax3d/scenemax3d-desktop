package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONObject;

public class EditorSaveActiveTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "editor.save_active";
    }

    @Override
    public String getDescription() {
        return "Saves the active editor or designer tab.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject());
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) {
        MainApp host = context.getHost();
        if (host == null) {
            throw new IllegalStateException("The IDE host is not available");
        }
        host.saveActiveEditorTab();

        JSONObject data = new JSONObject();
        data.put("path", host.getEditorTabPanel() != null ? host.getEditorTabPanel().getActiveFilePath() : JSONObject.NULL);
        return SceneMaxToolResult.success("Saved the active tab.", data);
    }
}
