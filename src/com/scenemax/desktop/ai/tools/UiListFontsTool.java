package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public class UiListFontsTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "ui.list_fonts";
    }

    @Override
    public String getDescription() {
        return "Lists all font names available to UI TEXT_VIEW widgets in the active project, merged from the "
                + "global resources/fonts/fonts.json and the project-level resources/fonts/fonts-ext.json. "
                + "Use this to pick valid fontName values.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject());
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) {
        List<UiAuthoringSupport.CatalogEntry> fonts = UiAuthoringSupport.listFonts(context);
        JSONArray arr = new JSONArray();
        for (UiAuthoringSupport.CatalogEntry entry : fonts) {
            arr.put(entry.toFontJson());
        }
        JSONObject data = new JSONObject();
        data.put("fonts", arr);
        data.put("count", fonts.size());
        data.put("resourcesRoot", context.getResourcesRoot() != null ? context.getResourcesRoot().toString() : JSONObject.NULL);
        data.put("projectRoot", context.getActiveProjectRoot() != null ? context.getActiveProjectRoot().toString() : JSONObject.NULL);
        return SceneMaxToolResult.success("Listed " + fonts.size() + " fonts.", data);
    }
}
