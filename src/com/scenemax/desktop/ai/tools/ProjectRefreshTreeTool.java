package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONObject;

public class ProjectRefreshTreeTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "project.refresh_tree";
    }

    @Override
    public String getDescription() {
        return "Refreshes the IDE project files tree from disk and optionally restores or reopens the current selection.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("restore_selection", new JSONObject().put("type", "boolean"))
                        .put("reopen_selection", new JSONObject().put("type", "boolean")));
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) {
        MainApp host = context.getHost();
        if (host == null) {
            throw new IllegalStateException("The IDE host is not available.");
        }

        boolean restoreSelection = optionalBoolean(arguments, "restore_selection", true);
        boolean reopenSelection = optionalBoolean(arguments, "reopen_selection", false);
        JSONObject data = host.refreshProjectFilesTreeForAutomation(restoreSelection, reopenSelection);
        return SceneMaxToolResult.success("Refreshed the IDE project files tree.", data);
    }
}
