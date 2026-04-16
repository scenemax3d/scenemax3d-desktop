package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.AppConfig;
import com.scenemax.desktop.AppDB;
import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;

public class AssetImportSketchfabTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "asset.import_sketchfab";
    }

    @Override
    public String getDescription() {
        return "Downloads a Sketchfab model into the active project's resources/Models folder and registers it as a SceneMax model asset.";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        JSONObject properties = new JSONObject();
        properties.put("uid", new JSONObject()
                .put("type", "string")
                .put("description", "Sketchfab model UID or viewer URL."));
        properties.put("modelName", new JSONObject()
                .put("type", "string")
                .put("description", "Optional SceneMax resource name to register. Defaults to a sanitized Sketchfab title."));
        properties.put("apiToken", new JSONObject()
                .put("type", "string")
                .put("description", "Optional Sketchfab API token. If omitted, uses the token saved in the IDE settings."));
        properties.put("replaceExisting", new JSONObject()
                .put("type", "boolean")
                .put("description", "Replace an existing imported model with the same name."));
        schema.put("properties", properties);
        schema.put("required", new JSONArray().put("uid"));
        return schema;
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        Path resourcesRoot = context.getResourcesRoot();
        if (resourcesRoot == null) {
            throw new IllegalStateException("Sketchfab import requires an active project with a resources folder.");
        }

        String apiToken = resolveApiToken(optionalString(arguments, "apiToken", ""));
        if (apiToken.isEmpty()) {
            throw new IllegalArgumentException("No Sketchfab API token is configured. Pass apiToken or set sketchfab_api_token in the IDE.");
        }

        SketchfabImportSupport.ImportResult result = SketchfabImportSupport.importModel(
                resourcesRoot,
                requireString(arguments, "uid"),
                optionalString(arguments, "modelName", ""),
                apiToken,
                optionalBoolean(arguments, "replaceExisting", false)
        );

        MainApp host = context.getHost();
        if (host != null) {
            host.refreshWorkspaceViews();
            host.registerImportedModelInActiveDesignerForAutomation(result.modelName, result.modelPath);
        }

        JSONObject data = new JSONObject();
        data.put("uid", result.uid);
        data.put("modelName", result.modelName);
        data.put("modelPath", result.modelPath);
        data.put("modelDirectory", result.modelDirectory);
        data.put("format", result.format);
        data.put("viewerUrl", result.viewerUrl);
        data.put("title", result.title);
        data.put("author", result.author);
        data.put("license", result.license);
        data.put("thumbnailUrl", result.thumbnailUrl);
        return SceneMaxToolResult.success("Imported Sketchfab model as '" + result.modelName + "'.", data);
    }

    private String resolveApiToken(String explicitToken) {
        if (explicitToken != null && !explicitToken.trim().isEmpty()) {
            return explicitToken.trim();
        }

        String stored = AppDB.getInstance().getParam("sketchfab_api_token");
        if (stored != null && !stored.trim().isEmpty()) {
            return stored.trim();
        }

        String configured = AppConfig.get("sketchfab_api_token", "");
        return configured == null ? "" : configured.trim();
    }
}
