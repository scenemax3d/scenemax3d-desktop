package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.EditorTabPanel;
import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONObject;

import java.nio.file.Path;

public class EditorReloadFromDiskTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "editor.reload_from_disk";
    }

    @Override
    public String getDescription() {
        return "Reloads an open document from disk, or opens it fresh if it is not already open.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject().put("type", "string"))
                        .put("base", new JSONObject().put("type", "string"))
                        .put("force", new JSONObject().put("type", "boolean")));
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        MainApp host = context.getHost();
        if (host == null) {
            throw new IllegalStateException("The IDE host is not available.");
        }

        Path path = DesignerAutomationSupport.resolvePathOrActive(context, arguments, "workspace");
        DesignerAutomationSupport.ensureDiskFileExists(path);
        boolean force = optionalBoolean(arguments, "force", false);
        EditorTabPanel tabs = host.getEditorTabPanel();
        if (!force && tabs != null && tabs.isTabDirty(path.toString())) {
            throw new IllegalStateException("The open IDE tab has unsaved changes. Save it first or pass force=true.");
        }

        boolean reloaded = host.reloadFileFromDiskForAutomation(path.toFile());
        JSONObject data = new JSONObject();
        data.put("path", path.toString());
        data.put("reloaded", reloaded);
        return SceneMaxToolResult.success(reloaded
                        ? "Reloaded " + path.getFileName() + " from disk."
                        : "Could not reload " + path.getFileName() + ".",
                data);
    }
}
