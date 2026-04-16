package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import com.scenemax.desktop.ai.ToolPaths;
import com.scenemax.designer.material.MaterialDocument;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;

public class MaterialSaveExportTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "material.save_export";
    }

    @Override
    public String getDescription() {
        return "Exports a .mat document into runtime-ready project resources and updates the material index.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject().put("type", "string"))
                        .put("base", new JSONObject().put("type", "string"))
                        .put("force", new JSONObject().put("type", "boolean")))
                .put("required", new JSONArray().put("path"));
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        Path path = ToolPaths.resolvePath(context, requireString(arguments, "path"), optionalString(arguments, "base", "workspace"));
        if (!path.getFileName().toString().toLowerCase().endsWith(".mat")) {
            throw new IllegalArgumentException("material.save_export only supports .mat files.");
        }
        DesignerAutomationSupport.ensureDiskFileExists(path);
        DesignerAutomationSupport.ensureNotDirtyInIde(context, path, optionalBoolean(arguments, "force", false));

        Path resourcesRoot = context.getResourcesRoot();
        if (resourcesRoot == null) {
            throw new IllegalStateException("No active project resources folder is available.");
        }

        MaterialDocument document = MaterialDocument.load(path.toFile());
        document.exportRuntimeAssets(path.toFile(), resourcesRoot.toString());

        MainApp host = context.getHost();
        if (host != null) {
            host.refreshWorkspaceViews();
        }

        JSONObject data = new JSONObject();
        data.put("path", path.toString());
        data.put("runtimeFolder", MaterialDocument.getRuntimeFolder(path.toFile(), resourcesRoot.toString()).getAbsolutePath());
        data.put("runtimeAssetBase", MaterialDocument.getRuntimeAssetBase(path.toFile()));
        data.put("materialName", MaterialDocument.getRuntimeName(path.toFile()));
        return SceneMaxToolResult.success("Exported runtime assets for " + path.getFileName() + ".", data);
    }
}
