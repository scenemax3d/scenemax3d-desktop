package com.scenemax.desktop.ai.tools;

import com.scenemax.designer.SketchfabService;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

public class AssetSearchSketchfabTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "asset.search_sketchfab";
    }

    @Override
    public String getDescription() {
        return "Searches Sketchfab for downloadable 3D models using the same filters as the SceneMax importer.";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        JSONObject properties = new JSONObject();
        properties.put("query", new JSONObject().put("type", "string"));
        properties.put("category", new JSONObject().put("type", "string"));
        properties.put("license", new JSONObject().put("type", "string"));
        properties.put("sortBy", new JSONObject().put("type", "string"));
        properties.put("maxFaceCount", new JSONObject().put("type", "integer"));
        properties.put("animatedOnly", new JSONObject().put("type", "boolean"));
        properties.put("staffPicked", new JSONObject().put("type", "boolean"));
        properties.put("cursor", new JSONObject().put("type", "string"));
        schema.put("properties", properties);
        schema.put("required", new JSONArray().put("query"));
        return schema;
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        JSONObject response = SketchfabService.search(
                requireString(arguments, "query"),
                optionalString(arguments, "category", ""),
                optionalString(arguments, "license", ""),
                optionalString(arguments, "sortBy", ""),
                optionalInt(arguments, "maxFaceCount", SketchfabService.DEFAULT_LOW_POLY_FACE_COUNT),
                optionalBoolean(arguments, "animatedOnly", false),
                optionalBoolean(arguments, "staffPicked", false),
                optionalString(arguments, "cursor", "")
        );

        JSONArray results = new JSONArray();
        JSONArray sourceResults = response.optJSONArray("results");
        if (sourceResults != null) {
            for (int i = 0; i < sourceResults.length(); i++) {
                JSONObject model = sourceResults.getJSONObject(i);
                JSONObject item = new JSONObject();
                item.put("uid", model.optString("uid"));
                item.put("name", model.optString("name"));
                item.put("viewerUrl", model.optString("viewerUrl"));
                item.put("thumbnailUrl", SketchfabService.getThumbnailUrl(model, 256));
                item.put("faceCount", model.optInt("faceCount", 0));
                item.put("vertexCount", model.optInt("vertexCount", 0));
                item.put("isAnimated", model.optBoolean("isAnimated", false));
                item.put("license", model.optJSONObject("license") != null
                        ? model.optJSONObject("license").optString("label")
                        : "");
                item.put("author", model.optJSONObject("user") != null
                        ? model.optJSONObject("user").optString("displayName")
                        : "");
                results.put(item);
            }
        }

        JSONObject data = new JSONObject();
        data.put("query", arguments.optString("query", ""));
        data.put("results", results);
        data.put("nextCursor", SketchfabService.extractCursor(response.optString("next", null)));
        data.put("previousCursor", SketchfabService.extractCursor(response.optString("previous", null)));
        return SceneMaxToolResult.success("Found " + results.length() + " Sketchfab model result(s).", data);
    }
}
