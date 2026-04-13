package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import com.scenemax.desktop.ai.ToolPaths;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;

public class EditorOpenFileTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "editor.open_file";
    }

    @Override
    public String getDescription() {
        return "Opens a file in the SceneMax editor or the matching designer tab.";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        JSONObject properties = new JSONObject();
        properties.put("path", new JSONObject().put("type", "string"));
        properties.put("base", new JSONObject().put("type", "string"));
        schema.put("properties", properties);
        schema.put("required", new JSONArray().put("path"));
        return schema;
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        MainApp host = context.getHost();
        if (host == null) {
            throw new IllegalStateException("The IDE host is not available");
        }

        Path path = ToolPaths.resolvePath(context, requireString(arguments, "path"), optionalString(arguments, "base", "workspace"));
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("File does not exist: " + path);
        }

        host.openFileFromAutomation(path.toFile());

        JSONObject data = new JSONObject();
        data.put("path", path.toString());
        return SceneMaxToolResult.success("Opened " + path.getFileName() + " in the IDE.", data);
    }
}
