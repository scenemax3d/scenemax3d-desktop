package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import com.scenemaxeng.common.ui.model.UIChainStyle;
import com.scenemaxeng.common.ui.model.UIConstraintSide;
import com.scenemaxeng.common.ui.model.UISizeMode;
import com.scenemaxeng.common.ui.model.UIWidgetType;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class UiAddWidgetTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "ui.add_widget";
    }

    @Override
    public String getDescription() {
        return "Adds a widget (PANEL/BUTTON/TEXT_VIEW/EDIT_TEXT/LIST_VIEW/IMAGE/GUIDELINE) to a .smui document. "
                + "Set 'parent' to a layer name (top-level) or a dot-path to an existing PANEL "
                + "(e.g. 'hud.statusPanel') to nest. Name must be unique across the document; "
                + "if omitted, a unique one is generated from the type. Pass initial properties "
                + "as a flat object using the keys from ui.get_schema. Constraints is an array of "
                + "{side,targetName,targetSide,margin}. Sprite/font references are checked against "
                + "ui.list_sprites / ui.list_fonts and reported as warnings if unknown.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject().put("type", "string"))
                        .put("base", new JSONObject().put("type", "string"))
                        .put("parent", new JSONObject().put("type", "string")
                                .put("description", "Layer name, or dot-path to a PANEL widget."))
                        .put("type", new JSONObject().put("type", "string")
                                .put("description", "PANEL / BUTTON / TEXT_VIEW / EDIT_TEXT / LIST_VIEW / IMAGE / GUIDELINE"))
                        .put("name", new JSONObject().put("type", "string")
                                .put("description", "Unique across the document. Auto-generated if omitted."))
                        .put("properties", new JSONObject().put("type", "object")
                                .put("description", "Flat property map. Keys match ui.get_schema."))
                        .put("constraints", new JSONObject().put("type", "array"))
                        .put("reload", new JSONObject().put("type", "boolean"))
                        .put("force", new JSONObject().put("type", "boolean")))
                .put("required", new JSONArray().put("path").put("parent").put("type"));
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        Path path = UiAuthoringSupport.resolveUiDocPath(context, arguments);
        DesignerAutomationSupport.ensureDiskFileExists(path);
        DesignerAutomationSupport.ensureNotDirtyInIde(context, path, optionalBoolean(arguments, "force", false));

        String parentRef = requireString(arguments, "parent");
        String typeRaw = requireString(arguments, "type").toUpperCase(Locale.ROOT);
        UIWidgetType widgetType;
        try {
            widgetType = UIWidgetType.valueOf(typeRaw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid widget type: " + typeRaw
                    + " (valid: PANEL, BUTTON, TEXT_VIEW, EDIT_TEXT, LIST_VIEW, IMAGE, GUIDELINE).");
        }

        JSONObject root = UiAuthoringSupport.readUiDoc(path);

        JSONArray container;
        String parentKind;
        String parentPath;
        if (parentRef.contains(".")) {
            UiAuthoringSupport.WidgetHit hit = UiAuthoringSupport.findWidgetByPath(root, parentRef);
            if (hit == null) {
                throw new IllegalArgumentException("No widget found at path: " + parentRef);
            }
            if (!"PANEL".equals(hit.widget.optString("type"))) {
                throw new IllegalArgumentException("Parent widget '" + parentRef
                        + "' is not a PANEL — only PANEL widgets can have children.");
            }
            if (!hit.widget.has("children") || hit.widget.optJSONArray("children") == null) {
                hit.widget.put("children", new JSONArray());
            }
            container = hit.widget.getJSONArray("children");
            parentKind = "widget";
            parentPath = hit.path;
        } else {
            JSONObject layer = UiAuthoringSupport.findLayer(root, parentRef);
            if (layer == null) {
                throw new IllegalArgumentException("No layer found named: " + parentRef);
            }
            if (!layer.has("widgets") || layer.optJSONArray("widgets") == null) {
                layer.put("widgets", new JSONArray());
            }
            container = layer.getJSONArray("widgets");
            parentKind = "layer";
            parentPath = layer.optString("name");
        }

        Set<String> taken = UiAuthoringSupport.collectAllNames(root);
        String requestedName = optionalString(arguments, "name", "").trim();
        String name;
        if (requestedName.isEmpty()) {
            name = UiAuthoringSupport.nextUniqueName(root, widgetType.name().toLowerCase(Locale.ROOT));
        } else {
            if (requestedName.contains(".")) {
                throw new IllegalArgumentException("Widget name must not contain '.'.");
            }
            if (taken.contains(requestedName)) {
                throw new IllegalArgumentException("Name is already in use in this document: " + requestedName);
            }
            name = requestedName;
        }

        JSONObject widget = buildWidget(name, widgetType, arguments.optJSONObject("properties"));

        JSONArray constraintsIn = arguments.optJSONArray("constraints");
        JSONArray constraintsOut = new JSONArray();
        if (constraintsIn != null) {
            for (int i = 0; i < constraintsIn.length(); i++) {
                JSONObject c = constraintsIn.optJSONObject(i);
                if (c == null) {
                    continue;
                }
                constraintsOut.put(validateConstraint(c, root, name, parentKind, parentPath));
            }
        }
        widget.put("constraints", constraintsOut);

        JSONArray warnings = collectResourceWarnings(context, widget);

        container.put(widget);

        UiAuthoringSupport.writeUiDoc(path, root);
        boolean reloaded = UiAuthoringSupport.reloadInIdeIfOpen(context, path, arguments);

        String widgetPath = parentPath + "." + name;

        JSONObject data = new JSONObject();
        data.put("path", path.toString());
        data.put("widget", widget);
        data.put("widgetPath", widgetPath);
        data.put("parent", parentRef);
        data.put("warnings", warnings);
        data.put("reloaded", reloaded);
        return SceneMaxToolResult.success("Added " + widgetType.name() + " " + name + " under " + parentRef + ".", data);
    }

    private JSONObject buildWidget(String name, UIWidgetType type, JSONObject props) {
        JSONObject widget = new JSONObject();
        widget.put("id", UUID.randomUUID().toString());
        widget.put("name", name);
        widget.put("type", type.name());
        widget.put("widthMode", "WRAP_CONTENT");
        widget.put("heightMode", "WRAP_CONTENT");
        widget.put("width", 100);
        widget.put("height", 50);
        widget.put("horizontalBias", 0.5);
        widget.put("verticalBias", 0.5);
        widget.put("visible", true);
        switch (type) {
            case PANEL:
                widget.put("backgroundColor", "#33333300");
                break;
            case BUTTON:
                widget.put("buttonText", "Button");
                widget.put("buttonTextColor", "#FFFFFFFF");
                widget.put("buttonColor", "#4488FFFF");
                widget.put("buttonPressedColor", "#2266CCFF");
                break;
            case TEXT_VIEW:
                widget.put("text", "Text");
                widget.put("textColor", "#FFFFFFFF");
                widget.put("fontSize", 16);
                widget.put("textAlignment", "left");
                break;
            case EDIT_TEXT:
                widget.put("width", 220);
                widget.put("height", 42);
                widget.put("text", "");
                widget.put("textColor", "#FFFFFFFF");
                widget.put("fontSize", 16);
                widget.put("textAlignment", "left");
                widget.put("editTextMultiline", false);
                widget.put("editTextPlaceholder", "Enter text");
                widget.put("editTextBackgroundColor", "#20242AFF");
                widget.put("editTextFocusedColor", "#2A303AFF");
                widget.put("editTextCursorColor", "#FFFFFFFF");
                widget.put("editTextSelectionColor", "#4A90E280");
                break;
            case LIST_VIEW:
                widget.put("width", 420);
                widget.put("height", 220);
                widget.put("listColumnCount", 3);
                widget.put("listHeaders", new JSONArray().put("Name").put("Value").put("Status"));
                widget.put("listColumnWidths", new JSONArray().put(140).put(140).put(140));
                widget.put("listRows", new JSONArray()
                        .put(new JSONArray().put("Player 1").put("100").put("Ready"))
                        .put(new JSONArray().put("Player 2").put("80").put("Waiting")));
                widget.put("listHeaderFontSize", 16);
                widget.put("listRowFontSize", 14);
                widget.put("listViewStyle", "classic");
                widget.put("listSelectedRowIndex", -1);
                break;
            case IMAGE:
                widget.put("imageScaleMode", "fit");
                widget.put("spriteFrame", 0);
                break;
            case GUIDELINE:
                widget.put("guidelineIsHorizontal", true);
                widget.put("guidelineIsPercent", false);
                widget.put("guidelinePosition", 0);
                break;
        }

        if (props != null) {
            applyProperties(widget, type, props);
        }
        return widget;
    }

    private void applyProperties(JSONObject widget, UIWidgetType type, JSONObject props) {
        for (String key : props.keySet()) {
            if ("id".equals(key) || "name".equals(key) || "type".equals(key)
                    || "constraints".equals(key) || "children".equals(key)) {
                continue;
            }
            Object value = props.get(key);
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
                    widget.put(key, value);
                    break;
            }
        }
        if ((type == UIWidgetType.TEXT_VIEW || type == UIWidgetType.EDIT_TEXT) && props.has("textAlignment")) {
            String ta = props.optString("textAlignment", "left");
            if (!"left".equals(ta) && !"center".equals(ta) && !"right".equals(ta)) {
                throw new IllegalArgumentException("textAlignment must be left, center, or right.");
            }
        }
        if (props.has("multiplayer") && type != UIWidgetType.TEXT_VIEW) {
            throw new IllegalArgumentException("multiplayer is supported only on TEXT_VIEW widgets.");
        }
        if (type == UIWidgetType.IMAGE && props.has("imageScaleMode")) {
            String im = props.optString("imageScaleMode", "fit");
            if (!"fit".equals(im) && !"fill".equals(im) && !"stretch".equals(im)) {
                throw new IllegalArgumentException("imageScaleMode must be fit, fill, or stretch.");
            }
        }
        if (type == UIWidgetType.LIST_VIEW) {
            validateListViewProperties(widget);
        }
    }

    private void validateListViewProperties(JSONObject widget) {
        int columns = widget.optInt("listColumnCount", 1);
        if (columns < 1) {
            throw new IllegalArgumentException("listColumnCount must be at least 1.");
        }
        String style = widget.optString("listViewStyle", "classic");
        if (!"classic".equals(style) && !"dark".equals(style) && !"blue".equals(style)) {
            throw new IllegalArgumentException("listViewStyle must be classic, dark, or blue.");
        }
        JSONArray widths = widget.optJSONArray("listColumnWidths");
        if (widths != null) {
            if (widths.length() != columns) {
                throw new IllegalArgumentException("listColumnWidths must contain one width per column.");
            }
            for (int i = 0; i < widths.length(); i++) {
                if (widths.optDouble(i, 0) <= 0) {
                    throw new IllegalArgumentException("listColumnWidths values must be greater than zero.");
                }
            }
        }
    }

    private JSONObject validateConstraint(JSONObject c, JSONObject root, String selfName,
                                          String parentKind, String parentPath) {
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
        String headerFont = widget.optString("listHeaderFontName", null);
        if (headerFont != null && !headerFont.isEmpty()) {
            Set<String> names = UiAuthoringSupport.fontNames(context);
            if (!names.contains(headerFont)) {
                warnings.put("Unknown listHeaderFontName '" + headerFont + "' not found in ui.list_fonts.");
            }
        }
        String rowFont = widget.optString("listRowFontName", null);
        if (rowFont != null && !rowFont.isEmpty()) {
            Set<String> names = UiAuthoringSupport.fontNames(context);
            if (!names.contains(rowFont)) {
                warnings.put("Unknown listRowFontName '" + rowFont + "' not found in ui.list_fonts.");
            }
        }
        String bgImg = widget.optString("backgroundImage", null);
        if (bgImg != null && !bgImg.isEmpty() && "".equals(bgImg.trim())) {
            warnings.put("Empty backgroundImage string.");
        }
        return warnings;
    }
}
