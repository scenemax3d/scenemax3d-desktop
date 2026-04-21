package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;

public class UiGetWidgetTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "ui.get_widget";
    }

    @Override
    public String getDescription() {
        return "Returns the full JSON of one widget in a .smui document, including its constraints and children. "
                + "Target it by widgetPath (dot path like 'layer1.panel1.score') or by widgetId.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject().put("type", "string"))
                        .put("base", new JSONObject().put("type", "string"))
                        .put("widgetPath", new JSONObject().put("type", "string").put("description", "Dot path: layerName.widgetName[.childName...]"))
                        .put("widgetId", new JSONObject().put("type", "string")))
                .put("required", new JSONArray().put("path"));
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        Path path = UiAuthoringSupport.resolveUiDocPath(context, arguments);
        DesignerAutomationSupport.ensureDiskFileExists(path);

        String widgetPath = optionalString(arguments, "widgetPath", "");
        String widgetId = optionalString(arguments, "widgetId", "");
        if (widgetPath.isEmpty() && widgetId.isEmpty()) {
            throw new IllegalArgumentException("Provide widgetPath or widgetId.");
        }

        JSONObject root = UiAuthoringSupport.readUiDoc(path);
        UiAuthoringSupport.WidgetHit hit = !widgetPath.isEmpty()
                ? UiAuthoringSupport.findWidgetByPath(root, widgetPath)
                : UiAuthoringSupport.findWidgetById(root, widgetId);
        if (hit == null) {
            throw new IllegalArgumentException("No widget found for the given widgetPath/widgetId.");
        }

        JSONObject data = new JSONObject();
        data.put("path", path.toString());
        data.put("widget", new JSONObject(hit.widget.toString()));
        data.put("widgetPath", hit.path);
        data.put("layer", hit.layer.optString("name"));
        return SceneMaxToolResult.success("Fetched widget " + hit.path + ".", data);
    }
}
