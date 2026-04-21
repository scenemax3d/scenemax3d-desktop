package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import com.scenemaxeng.common.ui.model.UIConstraintSide;
import com.scenemaxeng.common.ui.model.UISizeMode;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

public class UiValidateDocumentTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "ui.validate_document";
    }

    @Override
    public String getDescription() {
        return "Runs structural validation over a .smui document: unique names, valid enums, "
                + "constraint targets resolve, MATCH_CONSTRAINT requires both opposing constraints, "
                + "aspectRatio requires exactly one MATCH_CONSTRAINT dimension, sprite/font references "
                + "exist in the resource catalogs. Returns {valid, errors, warnings}. Call this before "
                + "handing a UI over to a human — it catches the mistakes an agent is most likely to make.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject().put("type", "string"))
                        .put("base", new JSONObject().put("type", "string")))
                .put("required", new JSONArray().put("path"));
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        Path path = UiAuthoringSupport.resolveUiDocPath(context, arguments);
        DesignerAutomationSupport.ensureDiskFileExists(path);

        JSONObject root = UiAuthoringSupport.readUiDoc(path);
        JSONArray errors = new JSONArray();
        JSONArray warnings = new JSONArray();

        Set<String> seenNames = new LinkedHashSet<>();
        Set<String> duplicateNames = new LinkedHashSet<>();
        JSONArray layers = root.optJSONArray("layers");
        if (layers != null) {
            for (int i = 0; i < layers.length(); i++) {
                JSONObject layer = layers.optJSONObject(i);
                if (layer == null) {
                    continue;
                }
                String layerName = layer.optString("name", "");
                if (layerName.isEmpty()) {
                    errors.put("Layer at index " + i + " has no name.");
                } else if (!seenNames.add(layerName)) {
                    duplicateNames.add(layerName);
                }
                if (layerName.contains(".")) {
                    errors.put("Layer name contains '.': " + layerName);
                }
                String rm = layer.optString("renderMode", "SCREEN_SPACE");
                if (!"SCREEN_SPACE".equals(rm) && !"WORLD_SPACE".equals(rm)) {
                    errors.put("Layer '" + layerName + "' has invalid renderMode: " + rm);
                }
            }
        }

        Set<String> allNames = UiAuthoringSupport.collectAllNames(root);
        Set<String> spriteCatalog = UiAuthoringSupport.spriteNames(context);
        Set<String> fontCatalog = UiAuthoringSupport.fontNames(context);

        Set<String> widgetNameDupCheck = new LinkedHashSet<>();
        UiAuthoringSupport.forEachWidget(root, (widget, layerName, widgetPath) -> {
            String name = widget.optString("name", "");
            if (name.isEmpty()) {
                errors.put("Widget at " + widgetPath + " has no name.");
            } else {
                if (!widgetNameDupCheck.add(name)) {
                    duplicateNames.add(name);
                }
                if (name.contains(".")) {
                    errors.put("Widget name contains '.': " + widgetPath);
                }
            }

            String type = widget.optString("type", "");
            switch (type) {
                case "PANEL":
                case "BUTTON":
                case "TEXT_VIEW":
                case "IMAGE":
                case "GUIDELINE":
                    break;
                default:
                    errors.put(widgetPath + ": invalid widget type '" + type + "'.");
            }

            String widthMode = widget.optString("widthMode", "WRAP_CONTENT");
            String heightMode = widget.optString("heightMode", "WRAP_CONTENT");
            try {
                UISizeMode.valueOf(widthMode);
            } catch (IllegalArgumentException ex) {
                errors.put(widgetPath + ": invalid widthMode '" + widthMode + "'.");
            }
            try {
                UISizeMode.valueOf(heightMode);
            } catch (IllegalArgumentException ex) {
                errors.put(widgetPath + ": invalid heightMode '" + heightMode + "'.");
            }

            JSONArray constraints = widget.optJSONArray("constraints");
            boolean hasLeft = false, hasRight = false, hasTop = false, hasBottom = false;
            if (constraints != null) {
                for (int i = 0; i < constraints.length(); i++) {
                    JSONObject c = constraints.optJSONObject(i);
                    if (c == null) {
                        continue;
                    }
                    String side = c.optString("side", "");
                    String targetSide = c.optString("targetSide", "");
                    String targetName = c.optString("targetName", "");
                    try {
                        UIConstraintSide.valueOf(side);
                    } catch (IllegalArgumentException ex) {
                        errors.put(widgetPath + ": invalid constraint side '" + side + "'.");
                    }
                    try {
                        UIConstraintSide.valueOf(targetSide);
                    } catch (IllegalArgumentException ex) {
                        errors.put(widgetPath + ": invalid constraint targetSide '" + targetSide + "'.");
                    }
                    if (targetName.isEmpty()) {
                        errors.put(widgetPath + ": constraint missing targetName.");
                    } else if (!"parent".equals(targetName) && !allNames.contains(targetName)) {
                        errors.put(widgetPath + ": constraint targetName '" + targetName
                                + "' does not resolve to any layer or widget.");
                    } else if (targetName.equals(name)) {
                        errors.put(widgetPath + ": constraint targets itself.");
                    }
                    switch (side) {
                        case "LEFT": hasLeft = true; break;
                        case "RIGHT": hasRight = true; break;
                        case "TOP": hasTop = true; break;
                        case "BOTTOM": hasBottom = true; break;
                        default:
                    }
                }
            }
            boolean centerH = widget.optBoolean("centerHorizontal", false);
            boolean centerV = widget.optBoolean("centerVertical", false);

            if ("MATCH_CONSTRAINT".equals(widthMode) && !(centerH || (hasLeft && hasRight))) {
                errors.put(widgetPath + ": widthMode=MATCH_CONSTRAINT requires both LEFT and RIGHT constraints "
                        + "(or centerHorizontal=true).");
            }
            if ("MATCH_CONSTRAINT".equals(heightMode) && !(centerV || (hasTop && hasBottom))) {
                errors.put(widgetPath + ": heightMode=MATCH_CONSTRAINT requires both TOP and BOTTOM constraints "
                        + "(or centerVertical=true).");
            }

            double aspectRatio = widget.optDouble("aspectRatio", 0);
            if (aspectRatio > 0) {
                boolean wMc = "MATCH_CONSTRAINT".equals(widthMode);
                boolean hMc = "MATCH_CONSTRAINT".equals(heightMode);
                if (wMc == hMc) {
                    errors.put(widgetPath + ": aspectRatio > 0 requires exactly one of widthMode/heightMode to be MATCH_CONSTRAINT.");
                }
            }

            double hBias = widget.optDouble("horizontalBias", 0.5);
            double vBias = widget.optDouble("verticalBias", 0.5);
            if (hBias < 0 || hBias > 1) {
                warnings.put(widgetPath + ": horizontalBias outside 0..1 range.");
            }
            if (vBias < 0 || vBias > 1) {
                warnings.put(widgetPath + ": verticalBias outside 0..1 range.");
            }

            String alignment = widget.optString("textAlignment", "");
            if (!alignment.isEmpty() && !"left".equals(alignment) && !"center".equals(alignment) && !"right".equals(alignment)) {
                errors.put(widgetPath + ": invalid textAlignment '" + alignment + "' (use left/center/right).");
            }
            String scale = widget.optString("imageScaleMode", "");
            if (!scale.isEmpty() && !"fit".equals(scale) && !"fill".equals(scale) && !"stretch".equals(scale)) {
                errors.put(widgetPath + ": invalid imageScaleMode '" + scale + "' (use fit/fill/stretch).");
            }

            String spriteName = widget.optString("spriteName", "");
            if (!spriteName.isEmpty() && !spriteCatalog.contains(spriteName)) {
                warnings.put(widgetPath + ": spriteName '" + spriteName + "' not found in sprite catalog.");
            }
            String fontName = widget.optString("fontName", "");
            if (!fontName.isEmpty() && !fontCatalog.contains(fontName)) {
                warnings.put(widgetPath + ": fontName '" + fontName + "' not found in font catalog.");
            }
        });

        for (String dup : duplicateNames) {
            errors.put("Name is used more than once in the document: '" + dup + "'.");
        }

        JSONObject data = new JSONObject();
        data.put("path", path.toString());
        data.put("valid", errors.isEmpty());
        data.put("errors", errors);
        data.put("warnings", warnings);
        data.put("errorCount", errors.length());
        data.put("warningCount", warnings.length());

        String summary = errors.isEmpty()
                ? "Valid — " + warnings.length() + " warning(s)."
                : errors.length() + " error(s), " + warnings.length() + " warning(s).";
        return SceneMaxToolResult.success(summary, data);
    }
}
