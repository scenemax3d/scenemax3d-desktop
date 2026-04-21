package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;

public class UiListWidgetsTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "ui.list_widgets";
    }

    @Override
    public String getDescription() {
        return "Lists widgets in a .smui document as a flat array, with each entry's dot-path (e.g. 'layer1.panel1.score'), id, "
                + "name, type, parent path, and number of children. Optionally restrict to a single layer.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject().put("type", "string"))
                        .put("base", new JSONObject().put("type", "string"))
                        .put("layerName", new JSONObject().put("type", "string").put("description", "Restrict output to widgets in this layer.")))
                .put("required", new JSONArray().put("path"));
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        Path path = UiAuthoringSupport.resolveUiDocPath(context, arguments);
        DesignerAutomationSupport.ensureDiskFileExists(path);

        String layerFilter = optionalString(arguments, "layerName", "");
        JSONObject root = UiAuthoringSupport.readUiDoc(path);

        JSONArray widgetsOut = new JSONArray();
        UiAuthoringSupport.forEachWidget(root, (widget, layerName, widgetPath) -> {
            if (!layerFilter.isEmpty() && !layerFilter.equals(layerName)) {
                return;
            }
            String parentPath = widgetPath.substring(0, widgetPath.lastIndexOf('.'));
            JSONArray children = widget.optJSONArray("children");
            widgetsOut.put(new JSONObject()
                    .put("path", widgetPath)
                    .put("id", widget.optString("id"))
                    .put("name", widget.optString("name"))
                    .put("type", widget.optString("type"))
                    .put("layer", layerName)
                    .put("parent", parentPath)
                    .put("childCount", children == null ? 0 : children.length()));
        });

        JSONObject data = new JSONObject();
        data.put("path", path.toString());
        data.put("widgets", widgetsOut);
        data.put("count", widgetsOut.length());
        if (!layerFilter.isEmpty()) {
            data.put("layerName", layerFilter);
        }
        return SceneMaxToolResult.success("Listed " + widgetsOut.length() + " widgets.", data);
    }
}
