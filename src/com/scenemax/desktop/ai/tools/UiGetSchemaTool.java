package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

public class UiGetSchemaTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "ui.get_schema";
    }

    @Override
    public String getDescription() {
        return "Returns the authoritative schema for .smui UI documents: widget types, common and type-specific "
                + "widget properties with defaults, and every enum value (sides, size modes, render modes, chain styles). "
                + "Call this first when you're about to author a UI so you never have to guess property names.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject());
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) {
        JSONObject data = new JSONObject();

        data.put("widgetTypes", new JSONArray()
                .put("PANEL").put("BUTTON").put("TEXT_VIEW").put("IMAGE").put("GUIDELINE"));

        data.put("enums", new JSONObject()
                .put("sizeMode", new JSONArray().put("FIXED").put("WRAP_CONTENT").put("MATCH_CONSTRAINT"))
                .put("constraintSide", new JSONArray().put("LEFT").put("RIGHT").put("TOP").put("BOTTOM"))
                .put("renderMode", new JSONArray().put("SCREEN_SPACE").put("WORLD_SPACE"))
                .put("chainStyle", new JSONArray().put("SPREAD").put("SPREAD_INSIDE").put("PACKED"))
                .put("imageScaleMode", new JSONArray().put("fit").put("fill").put("stretch"))
                .put("textAlignment", new JSONArray().put("left").put("center").put("right")));

        data.put("commonProperties", commonPropertiesSchema());
        data.put("typeProperties", new JSONObject()
                .put("PANEL", panelPropertiesSchema())
                .put("BUTTON", buttonPropertiesSchema())
                .put("TEXT_VIEW", textViewPropertiesSchema())
                .put("IMAGE", imagePropertiesSchema())
                .put("GUIDELINE", guidelinePropertiesSchema()));

        data.put("constraint", new JSONObject()
                .put("description", "Connects one side of this widget to a side of a sibling (or 'parent'). "
                        + "Target 'parent' means the enclosing layer or parent PANEL.")
                .put("fields", new JSONObject()
                        .put("side", "One of LEFT/RIGHT/TOP/BOTTOM — which side of THIS widget")
                        .put("targetName", "Name of the sibling widget, or 'parent'")
                        .put("targetSide", "One of LEFT/RIGHT/TOP/BOTTOM — which side of the TARGET")
                        .put("margin", "Pixel offset from the anchor point (float)"))
                .put("example", new JSONObject()
                        .put("side", "TOP")
                        .put("targetName", "headerPanel")
                        .put("targetSide", "BOTTOM")
                        .put("margin", 8)));

        data.put("layer", new JSONObject()
                .put("fields", new JSONObject()
                        .put("id", "string (uuid, auto-generated if omitted)")
                        .put("name", "string (must be unique across all layers and widgets)")
                        .put("visible", "boolean (default true)")
                        .put("zOrder", "integer (higher = on top)")
                        .put("renderMode", "SCREEN_SPACE (HUD overlay) or WORLD_SPACE (3D billboard)")
                        .put("widgets", "array of widget objects")));

        data.put("document", new JSONObject()
                .put("fields", new JSONObject()
                        .put("version", "integer (current format version is 1)")
                        .put("name", "string — the UI name used in scripting as UI.load \"name\"")
                        .put("canvasWidth", "number (default 1920)")
                        .put("canvasHeight", "number (default 1080)")
                        .put("layers", "array of layer objects")));

        data.put("notes", new JSONArray()
                .put("Widget names must be unique across the entire document (including layer names). The parser splits paths on '.', so widget names must not contain dots.")
                .put("Colors are hex RGBA strings like #RRGGBBAA (e.g. '#FFFFFFFF' for opaque white).")
                .put("A constraint that references a missing target widget will fail to resolve at runtime.")
                .put("For IMAGE widgets, spriteName takes priority over imagePath when both are set.")
                .put("When widthMode is MATCH_CONSTRAINT, the widget must have both LEFT and RIGHT constraints (same for TOP/BOTTOM with heightMode).")
                .put("aspectRatio requires exactly one of widthMode/heightMode to be MATCH_CONSTRAINT."));

        return SceneMaxToolResult.success("UI schema (widgets, enums, constraint, layer, document).", data);
    }

    private JSONObject commonPropertiesSchema() {
        return new JSONObject()
                .put("id", "string (uuid, auto-generated if omitted)")
                .put("name", "string (unique within document)")
                .put("type", "one of widgetTypes")
                .put("widthMode", "one of enums.sizeMode (default WRAP_CONTENT)")
                .put("heightMode", "one of enums.sizeMode (default WRAP_CONTENT)")
                .put("width", "number, used when widthMode is FIXED (default 100)")
                .put("height", "number, used when heightMode is FIXED (default 50)")
                .put("aspectRatio", "number (0 = none). When > 0, one dimension must be MATCH_CONSTRAINT.")
                .put("constraints", "array of constraint objects")
                .put("horizontalBias", "number 0..1 (default 0.5)")
                .put("verticalBias", "number 0..1 (default 0.5)")
                .put("paddingLeft", "number (default 0)")
                .put("paddingRight", "number (default 0)")
                .put("paddingTop", "number (default 0)")
                .put("paddingBottom", "number (default 0)")
                .put("marginLeft", "number (default 0)")
                .put("marginRight", "number (default 0)")
                .put("marginTop", "number (default 0)")
                .put("marginBottom", "number (default 0)")
                .put("visible", "boolean (default true)")
                .put("horizontalChainStyle", "one of enums.chainStyle, or null")
                .put("verticalChainStyle", "one of enums.chainStyle, or null")
                .put("horizontalWeight", "number (default 1.0)")
                .put("verticalWeight", "number (default 1.0)")
                .put("centerHorizontal", "boolean (convenience: centers widget in parent on X)")
                .put("centerVertical", "boolean (convenience: centers widget in parent on Y)")
                .put("zOrder", "integer within parent (higher = on top)")
                .put("children", "array of child widget objects (PANEL only is typically nested)");
    }

    private JSONObject panelPropertiesSchema() {
        return new JSONObject()
                .put("backgroundColor", "RGBA hex (default #33333300)")
                .put("backgroundImage", "string path (optional)");
    }

    private JSONObject buttonPropertiesSchema() {
        return new JSONObject()
                .put("buttonText", "string (default 'Button')")
                .put("buttonTextColor", "RGBA hex (default #FFFFFFFF)")
                .put("buttonColor", "RGBA hex (default #4488FFFF)")
                .put("buttonPressedColor", "RGBA hex (default #2266CCFF)");
    }

    private JSONObject textViewPropertiesSchema() {
        return new JSONObject()
                .put("text", "string (default 'Text')")
                .put("textColor", "RGBA hex (default #FFFFFFFF)")
                .put("fontSize", "number (default 16)")
                .put("textAlignment", "one of enums.textAlignment (default 'left')")
                .put("fontName", "string — name from ui.list_fonts, or null for default");
    }

    private JSONObject imagePropertiesSchema() {
        return new JSONObject()
                .put("imagePath", "string — direct image path (optional)")
                .put("imageScaleMode", "one of enums.imageScaleMode (default 'fit')")
                .put("spriteName", "string — name from ui.list_sprites (takes priority over imagePath)")
                .put("spriteFrame", "integer (default 0)");
    }

    private JSONObject guidelinePropertiesSchema() {
        return new JSONObject()
                .put("guidelineIsHorizontal", "boolean (true = horizontal, false = vertical)")
                .put("guidelineIsPercent", "boolean (true = guidelinePosition is a 0..1 ratio)")
                .put("guidelinePosition", "number (pixels or 0..1 depending on guidelineIsPercent)");
    }
}
