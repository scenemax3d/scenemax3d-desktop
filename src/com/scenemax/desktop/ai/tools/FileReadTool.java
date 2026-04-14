package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import com.scenemax.desktop.ai.ToolPaths;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileReadTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "file.read";
    }

    @Override
    public String getDescription() {
        return "Reads a text file from the workspace or active project.";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        JSONObject properties = new JSONObject();
        properties.put("path", new JSONObject().put("type", "string").put("description", "Path of the file to read."));
        properties.put("base", new JSONObject().put("type", "string").put("description", "workspace, project, scripts, or resources."));
        schema.put("properties", properties);
        schema.put("required", new JSONArray().put("path"));
        return schema;
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        Path path = ToolPaths.resolvePath(context, requireString(arguments, "path"), optionalString(arguments, "base", "workspace"));
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("File does not exist: " + path);
        }
        String content = Files.readString(path, StandardCharsets.UTF_8);

        JSONObject data = new JSONObject();
        data.put("path", path.toString());
        data.put("sizeBytes", Files.size(path));
        data.put("content", content);
        return SceneMaxToolResult.success("Read file " + path.getFileName(), data);
    }
}
