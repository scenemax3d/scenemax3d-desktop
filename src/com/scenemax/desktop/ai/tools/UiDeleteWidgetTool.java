package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;
import java.util.List;

public class UiDeleteWidgetTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "ui.delete_widget";
    }

    @Override
    public String getDescription() {
        return "Removes a widget (and all its children) from a .smui document. "
                + "Warns if other widgets reference the deleted widget via constraints. "
                + "Target by widgetPath (dot path) or widgetId.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject().put("type", "string"))
                        .put("base", new JSONObject().put("type", "string"))
                        .put("widgetPath", new JSONObject().put("type", "string"))
                        .put("widgetId", new JSONObject().put("type", "string"))
                        .put("reload", new JSONObject().put("type", "boolean"))
                        .put("force", new JSONObject().put("type", "boolean")))
                .put("required", new JSONArray().put("path"));
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        Path path = UiAuthoringSupport.resolveUiDocPath(context, arguments);
        DesignerAutomationSupport.ensureDiskFileExists(path);
        DesignerAutomationSupport.ensureNotDirtyInIde(context, path, optionalBoolean(arguments, "force", false));

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

        String name = hit.widget.optString("name");
        JSONArray warnings = new JSONArray();
        List<String> referrers = UiAuthoringSupport.findConstraintReferrers(root, name);
        referrers.remove(hit.path);
        if (!referrers.isEmpty()) {
            warnings.put("Widget '" + name + "' is referenced by constraints on: " + String.join(", ", referrers)
                    + ". Those constraints will fail to resolve at runtime.");
        }

        hit.containerArray.remove(hit.index);

        UiAuthoringSupport.writeUiDoc(path, root);
        boolean reloaded = UiAuthoringSupport.reloadInIdeIfOpen(context, path, arguments);

        JSONObject data = new JSONObject();
        data.put("path", path.toString());
        data.put("deletedWidgetName", name);
        data.put("deletedWidgetPath", hit.path);
        data.put("warnings", warnings);
        data.put("reloaded", reloaded);
        return SceneMaxToolResult.success("Deleted widget " + hit.path + ".", data);
    }
}
