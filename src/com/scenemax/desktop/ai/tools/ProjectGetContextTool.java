package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONObject;

import java.nio.file.Path;

public class ProjectGetContextTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "project.get_context";
    }

    @Override
    public String getDescription() {
        return "Returns workspace, active project, and active editor context for SceneMax.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject());
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) {
        JSONObject data = new JSONObject();
        putPath(data, "workspaceRoot", context.getWorkspaceRoot());
        putPath(data, "scriptsRoot", context.getScriptsRoot());
        putPath(data, "resourcesRoot", context.getResourcesRoot());
        putPath(data, "activeProjectRoot", context.getActiveProjectRoot());

        MainApp host = context.getHost();
        if (host != null) {
            data.put("activeDocument", host.getAutomationActiveDocumentSnapshot());
        }

        return SceneMaxToolResult.success("Collected SceneMax workspace context.", data);
    }

    private void putPath(JSONObject target, String key, Path value) {
        target.put(key, value == null ? JSONObject.NULL : value.toString());
    }
}
