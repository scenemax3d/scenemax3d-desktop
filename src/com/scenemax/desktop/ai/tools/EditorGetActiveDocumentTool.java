package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.EditorTabPanel;
import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONObject;

public class EditorGetActiveDocumentTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "editor.get_active_document";
    }

    @Override
    public String getDescription() {
        return "Returns information about the active editor tab, including unsaved text for text tabs.";
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
        if (host == null || host.getEditorTabPanel() == null) {
            throw new IllegalStateException("The IDE host is not available");
        }

        EditorTabPanel editorTabPanel = host.getEditorTabPanel();
        String filePath = editorTabPanel.getActiveFilePath();
        if (filePath == null) {
            return SceneMaxToolResult.success("No active editor tab.", new JSONObject().put("hasActiveTab", false));
        }

        String kind = editorTabPanel.getActiveTabKind();
        JSONObject data = new JSONObject();
        data.put("hasActiveTab", true);
        data.put("path", filePath);
        data.put("kind", kind);
        data.put("dirty", editorTabPanel.isActiveTabDirty());
        if ("text".equals(kind)) {
            data.put("content", editorTabPanel.getCurrentEditorText());
        }
        return SceneMaxToolResult.success("Collected active editor state.", data);
    }
}
