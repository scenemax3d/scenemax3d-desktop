package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public class UiListSpritesTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "ui.list_sprites";
    }

    @Override
    public String getDescription() {
        return "Lists all sprite names available to UI IMAGE widgets in the active project, merged from the "
                + "global resources/sprites/sprites.json and the project-level resources/sprites/sprites-ext.json. "
                + "Use this to pick valid spriteName values.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject());
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) {
        List<UiAuthoringSupport.CatalogEntry> sprites = UiAuthoringSupport.listSprites(context);
        JSONArray arr = new JSONArray();
        for (UiAuthoringSupport.CatalogEntry entry : sprites) {
            arr.put(entry.toSpriteJson());
        }
        JSONObject data = new JSONObject();
        data.put("sprites", arr);
        data.put("count", sprites.size());
        data.put("resourcesRoot", context.getResourcesRoot() != null ? context.getResourcesRoot().toString() : JSONObject.NULL);
        data.put("projectRoot", context.getActiveProjectRoot() != null ? context.getActiveProjectRoot().toString() : JSONObject.NULL);
        return SceneMaxToolResult.success("Listed " + sprites.size() + " sprites.", data);
    }
}
