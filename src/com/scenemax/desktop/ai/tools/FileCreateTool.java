package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import com.scenemax.desktop.ai.ToolPaths;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileCreateTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "file.create";
    }

    @Override
    public String getDescription() {
        return "Creates a new text file in the workspace or active project.";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        JSONObject properties = new JSONObject();
        properties.put("path", new JSONObject().put("type", "string").put("description", "Path of the file to create."));
        properties.put("content", new JSONObject().put("type", "string").put("description", "Initial file content."));
        properties.put("base", new JSONObject().put("type", "string").put("description", "workspace, project, scripts, or resources."));
        properties.put("createParents", new JSONObject().put("type", "boolean").put("description", "Create parent folders if needed."));
        properties.put("overwrite", new JSONObject().put("type", "boolean").put("description", "Allow replacing an existing file."));
        properties.put("openInEditor", new JSONObject().put("type", "boolean").put("description", "Open the file after creating it."));
        schema.put("properties", properties);
        schema.put("required", new JSONArray().put("path"));
        return schema;
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        Path path = ToolPaths.resolvePath(context, requireString(arguments, "path"), optionalString(arguments, "base", "workspace"));
        boolean createParents = optionalBoolean(arguments, "createParents", true);
        boolean overwrite = optionalBoolean(arguments, "overwrite", false);
        boolean openInEditor = optionalBoolean(arguments, "openInEditor", true);
        String content = arguments.optString("content", "");

        if (Files.exists(path) && !overwrite) {
            throw new IllegalArgumentException("File already exists: " + path);
        }
        if (createParents && path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        Files.writeString(path, content, StandardCharsets.UTF_8);

        MainApp host = context.getHost();
        if (host != null) {
            host.refreshWorkspaceViews();
            if (openInEditor) {
                host.openFileFromAutomation(path.toFile());
            }
        }

        JSONObject data = new JSONObject();
        data.put("path", path.toString());
        data.put("sizeBytes", Files.size(path));
        return SceneMaxToolResult.success("Created file " + path.getFileName(), data);
    }
}
