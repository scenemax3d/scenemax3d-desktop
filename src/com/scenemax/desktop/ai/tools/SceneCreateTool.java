package com.scenemax.desktop.ai.tools;

import com.scenemax.designer.DesignerDocument;
import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import com.scenemax.desktop.ai.ToolPaths;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class SceneCreateTool extends AbstractSceneMaxTool {

    private static final String INVALID_NAME_CHARS = ".*[\\\\/:*?\"<>|].*";

    @Override
    public String getName() {
        return "scene.create";
    }

    @Override
    public String getDescription() {
        return "Creates a new scene the same way the UI does: a scene folder containing "
                + "<sceneName>.smdesign and a 'main' file referencing the generated <sceneName>.code.";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        JSONObject properties = new JSONObject();
        properties.put("parentPath", new JSONObject().put("type", "string")
                .put("description", "Directory under scripts (or absolute inside the project) where the scene folder will be created."));
        properties.put("base", new JSONObject().put("type", "string")
                .put("description", "Base to resolve parentPath against (scripts, workspace, project). Default: scripts."));
        properties.put("sceneName", new JSONObject().put("type", "string")
                .put("description", "Name of the new scene. Must be a valid folder name (no \\ / : * ? \" < > | )."));
        properties.put("openInEditor", new JSONObject().put("type", "boolean")
                .put("description", "Open the new .smdesign in the editor after creation. Default: true."));
        schema.put("properties", properties);
        schema.put("required", new JSONArray().put("parentPath").put("sceneName"));
        return schema;
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        String sceneName = requireString(arguments, "sceneName");
        if (sceneName.matches(INVALID_NAME_CHARS)) {
            throw new IllegalArgumentException("Invalid scene name. Avoid these characters: \\ / : * ? \" < > |");
        }

        Path parent = ToolPaths.resolvePath(context, requireString(arguments, "parentPath"),
                optionalString(arguments, "base", "scripts"));
        if (!Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        if (!Files.isDirectory(parent)) {
            throw new IllegalArgumentException("parentPath is not a directory: " + parent);
        }

        Path sceneFolder = parent.resolve(sceneName);
        if (Files.exists(sceneFolder)) {
            throw new IllegalStateException("A folder named '" + sceneName + "' already exists at " + parent);
        }
        Files.createDirectory(sceneFolder);

        String docName = sceneName + ".smdesign";
        File designerFile = sceneFolder.resolve(docName).toFile();
        DesignerDocument.writeEmptyFile(designerFile);

        Path mainFile = sceneFolder.resolve("main");
        String mainContent = "add \"" + sceneName + ".code\" code\n";
        Files.write(mainFile, mainContent.getBytes(StandardCharsets.UTF_8));

        MainApp host = context.getHost();
        if (host != null) {
            host.refreshWorkspaceViews();
            if (optionalBoolean(arguments, "openInEditor", true)) {
                host.openFileFromAutomation(designerFile);
            }
        }

        JSONObject data = new JSONObject();
        data.put("sceneFolder", sceneFolder.toString());
        data.put("designerPath", designerFile.getAbsolutePath());
        data.put("mainPath", mainFile.toString());
        data.put("sceneName", sceneName);
        return SceneMaxToolResult.success("Created scene '" + sceneName + "' at " + sceneFolder, data);
    }
}
