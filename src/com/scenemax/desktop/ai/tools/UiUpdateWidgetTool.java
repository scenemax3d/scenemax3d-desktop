package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import com.scenemaxeng.common.ui.model.UIChainStyle;
import com.scenemaxeng.common.ui.model.UIConstraintSide;
import com.scenemaxeng.common.ui.model.UISizeMode;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

public class UiUpdateWidgetTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "ui.update_widget";
    }

    @Override
    public String getDescription() {
        return "Updates one widget's properties in-place. 'updates' is a flat object merged onto the widget "
                + "using the keys from ui.get_schema. If 'updates.name' is given, every constraint targetName "
                + "that referenced the old name is rewritten. If 'constraints' is provided, the widget's "
                + "constraints array is REPLACED wholesale (merging constraint arrays is ambiguous).";
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
                        .put("updates", new JSONObject().put("type", "object"))
                        .put("constraints", new JSONObject().put("type", "array"))
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

        JSONObject widget = hit.widget;
        String widgetType = widget.optString("type");
        int renamedConstraints = 0;
        String oldName = widget.optString("name");
        String newName = oldName;

        JSONObject updates = arguments.optJSONObject("updates");
        if (updates != null) {
            if (updates.has("name")) {
                String requested = updates.optString("name", "").trim();
                if (requested.isEmpty()) {
                    throw new IllegalArgumentException("name cannot be blank.");
                }
                if (requested.contains(".")) {
                    throw new IllegalArgumentException("name must not contain '.'.");
                }
                if (!requested.equals(oldName)) {
                    Set<String> taken = UiAuthoringSupport.collectAllNames(root);
                    taken.remove(oldName);
                    if (taken.contains(requested)) {
                        throw new IllegalArgumentException("name already in use: " + requested);
                    }
                    widget.put("name", requested);
                    newName = requested;
                }
            }
            applyUpdates(widget, widgetType, updates);
        }

        if (arguments.has("constraints")) {
            JSONArray constraintsIn = arguments.optJSONArray("constraints");
            JSONArray constraintsOut = new JSONArray();
            if (constraintsIn != null) {
                for (int i = 0; i < constraintsIn.length(); i++) {
                    JSONObject c = constraintsIn.optJSONObject(i);
                    if (c == null) {
                        continue;
                    }
                    constraintsOut.put(validateConstraint(c, root, newName));
                }
            }
            widget.put("constraints", constraintsOut);
        }

        if (!oldName.equals(newName)) {
            renamedConstraints = UiAuthoringSupport.rewriteConstraintTargets(root, oldName, newName);
        }

        JSONArray warnings = collectResourceWarnings(context, widget);

        UiAuthoringSupport.writeUiDoc(path, root);
        boolean reloaded = UiAuthoringSupport.reloadInIdeIfOpen(context, path, arguments);

        JSONObject data = new JSONObject();
        data.put("path", path.toString());
        data.put("widget", widget);
        data.put("renamedConstraintReferences", renamedConstraints);
        data.put("warnings", warnings);
        data.put("reloaded", reloaded);
        return SceneMaxToolResult.success("Updated widget " + newName + ".", data);
    }

    private void applyUpdates(JSONObject widget, String type, JSONObject updates) {
        for (String key : updates.keySet()) {
            if ("id".equals(key) || "type".equals(key) || "name".equals(key)
                    || "constraints".equals(key) || "children".equals(key)) {
                continue;
            }
            Object value = updates.get(key);
            switch (key) {
                case "widthMode":
                case "heightMode":
                    String sm = String.valueOf(value).toUpperCase(Locale.ROOT);
                    UISizeMode.valueOf(sm);
                    widget.put(key, sm);
                    break;
                case "horizontalChainStyle":
                case "verticalChainStyle":
                    if (value == null || JSONObject.NULL.equals(value)) {
                        widget.remove(key);
                    } else {
                        String cs = String.valueOf(value).toUpperCase(Locale.ROOT);
                        UIChainStyle.valueOf(cs);
                        widget.put(key, cs);
                    }
                    break;
                default:
                    if (value == null || JSONObject.NULL.equals(value)) {
                        widget.remove(key);
                    } else {
                        widget.put(key, value);
                    }
                    break;
            }
        }
        if ("TEXT_VIEW".equals(type) && updates.has("textAlignment")) {
            String ta = updates.optString("textAlignment", "left");
            if (!"left".equals(ta) && !"center".equals(ta) && !"right".equals(ta)) {
                throw new IllegalArgumentException("textAlignment must be left, center, or right.");
            }
        }
        if ("IMAGE".equals(type) && updates.has("imageScaleMode")) {
            String im = updates.optString("imageScaleMode", "fit");
            if (!"fit".equals(im) && !"fill".equals(im) && !"stretch".equals(im)) {
                throw new IllegalArgumentException("imageScaleMode must be fit, fill, or stretch.");
            }
        }
    }

    private JSONObject validateConstraint(JSONObject c, JSONObject root, String selfName) {
        String sideRaw = c.optString("side", "").toUpperCase(Locale.ROOT);
        String targetSideRaw = c.optString("targetSide", "").toUpperCase(Locale.ROOT);
        String targetName = c.optString("targetName", "");
        if (sideRaw.isEmpty() || targetSideRaw.isEmpty() || targetName.isEmpty()) {
            throw new IllegalArgumentException("Constraint requires side, targetSide, and targetName.");
        }
        UIConstraintSide.valueOf(sideRaw);
        UIConstraintSide.valueOf(targetSideRaw);
        if (!"parent".equals(targetName)) {
            if (targetName.equals(selfName)) {
                throw new IllegalArgumentException("Constraint targetName cannot reference itself: " + targetName);
            }
            Set<String> allNames = UiAuthoringSupport.collectAllNames(root);
            if (!allNames.contains(targetName)) {
                throw new IllegalArgumentException("Constraint targetName '" + targetName
                        + "' does not resolve to any layer or widget in this document.");
            }
        }
        JSONObject out = new JSONObject();
        out.put("side", sideRaw);
        out.put("targetName", targetName);
        out.put("targetSide", targetSideRaw);
        out.put("margin", c.optDouble("margin", 0));
        return out;
    }

    private JSONArray collectResourceWarnings(SceneMaxToolContext context, JSONObject widget) {
        JSONArray warnings = new JSONArray();
        String sprite = widget.optString("spriteName", null);
        if (sprite != null && !sprite.isEmpty()) {
            Set<String> names = UiAuthoringSupport.spriteNames(context);
            if (!names.contains(sprite)) {
                warnings.put("Unknown spriteName '" + sprite + "' — not found in ui.list_sprites.");
            }
        }
        String font = widget.optString("fontName", null);
        if (font != null && !font.isEmpty()) {
            Set<String> names = UiAuthoringSupport.fontNames(context);
            if (!names.contains(font)) {
                warnings.put("Unknown fontName '" + font + "' — not found in ui.list_fonts.");
            }
        }
        return warnings;
    }
}
