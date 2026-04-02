package com.scenemaxeng.common.ui.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pure data definition for a UI widget. No JME dependency.
 *
 * Each widget has:
 * - A unique name (used for constraint references and scripting access)
 * - A type (PANEL, BUTTON, TEXT_VIEW, IMAGE, GUIDELINE)
 * - Size mode and dimensions
 * - Constraints that define its position within the parent
 * - Bias for centering when constrained on both sides
 * - Type-specific properties (text, color, imagePath, etc.)
 * - Optional children (PANEL can contain child widgets)
 */
public class UIWidgetDef {

    private String id;
    private String name;
    private UIWidgetType type;

    // --- Size ---
    private UISizeMode widthMode = UISizeMode.WRAP_CONTENT;
    private UISizeMode heightMode = UISizeMode.WRAP_CONTENT;
    private float width = 100;    // used when widthMode == FIXED
    private float height = 50;    // used when heightMode == FIXED

    // Aspect ratio (width:height). 0 means no ratio enforced.
    // When set, one dimension must be MATCH_CONSTRAINT and will be computed from the other.
    private float aspectRatio = 0;

    // --- Constraints ---
    private List<UIConstraint> constraints = new ArrayList<>();

    // Bias: 0.0 = left/top, 0.5 = center, 1.0 = right/bottom
    // Only effective when constrained on both sides of an axis
    private float horizontalBias = 0.5f;
    private float verticalBias = 0.5f;

    // --- Padding (inner spacing for containers) ---
    private float paddingLeft = 0;
    private float paddingRight = 0;
    private float paddingTop = 0;
    private float paddingBottom = 0;

    // --- Margins (outer spacing, always applied regardless of constraints) ---
    private float marginLeft = 0;
    private float marginRight = 0;
    private float marginTop = 0;
    private float marginBottom = 0;

    // --- Visibility ---
    private boolean visible = true;

    // --- Chain support ---
    // If this widget is the head of a chain, these define the chain style.
    // null means this widget is not a chain head.
    private UIChainStyle horizontalChainStyle = null;
    private UIChainStyle verticalChainStyle = null;
    // Weight for weighted chains (only used with MATCH_CONSTRAINT in a chain)
    private float horizontalWeight = 1.0f;
    private float verticalWeight = 1.0f;

    // --- Children (for PANEL containers) ---
    private List<UIWidgetDef> children = new ArrayList<>();

    // --- Type-specific properties ---

    // PANEL
    private String backgroundColor = "#33333300";   // RGBA hex
    private String backgroundImage = null;

    // BUTTON
    private String buttonText = "Button";
    private String buttonTextColor = "#FFFFFFFF";
    private String buttonColor = "#4488FFFF";
    private String buttonPressedColor = "#2266CCFF";

    // TEXT_VIEW
    private String text = "Text";
    private String textColor = "#FFFFFFFF";
    private float fontSize = 16;
    private String textAlignment = "left";   // left, center, right
    private String fontName = null;          // font from AssetsMapping (null = default)

    // IMAGE
    private String imagePath = null;
    private String imageScaleMode = "fit";   // fit, fill, stretch
    private String spriteName = null;        // sprite from AssetsMapping (overrides imagePath when set)
    private int spriteFrame = 0;

    // GUIDELINE
    private boolean guidelineIsHorizontal = true;   // true = horizontal, false = vertical
    private boolean guidelineIsPercent = false;      // true = position is a 0-1 percentage
    private float guidelinePosition = 0;             // pixels from edge, or 0-1 percentage

    // --- Center constraints (convenience: centers widget in parent on given axis) ---
    private boolean centerHorizontal = false;
    private boolean centerVertical = false;

    // --- Z-order within parent (higher = drawn on top) ---
    private int zOrder = 0;

    public UIWidgetDef(String name, UIWidgetType type) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.type = type;
    }

    public UIWidgetDef(String id, String name, UIWidgetType type) {
        this.id = id;
        this.name = name;
        this.type = type;
    }

    // ========================================================================
    // Getters / Setters
    // ========================================================================

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UIWidgetType getType() { return type; }

    // Size
    public UISizeMode getWidthMode() { return widthMode; }
    public void setWidthMode(UISizeMode widthMode) { this.widthMode = widthMode; }
    public UISizeMode getHeightMode() { return heightMode; }
    public void setHeightMode(UISizeMode heightMode) { this.heightMode = heightMode; }
    public float getWidth() { return width; }
    public void setWidth(float width) { this.width = width; }
    public float getHeight() { return height; }
    public void setHeight(float height) { this.height = height; }
    public float getAspectRatio() { return aspectRatio; }
    public void setAspectRatio(float aspectRatio) { this.aspectRatio = aspectRatio; }

    // Constraints
    public List<UIConstraint> getConstraints() { return constraints; }
    public void addConstraint(UIConstraint constraint) { constraints.add(constraint); }
    public void removeConstraint(UIConstraint constraint) { constraints.remove(constraint); }
    public void clearConstraints() { constraints.clear(); }

    public float getHorizontalBias() { return horizontalBias; }
    public void setHorizontalBias(float horizontalBias) { this.horizontalBias = horizontalBias; }
    public float getVerticalBias() { return verticalBias; }
    public void setVerticalBias(float verticalBias) { this.verticalBias = verticalBias; }

    // Padding
    public float getPaddingLeft() { return paddingLeft; }
    public void setPaddingLeft(float paddingLeft) { this.paddingLeft = paddingLeft; }
    public float getPaddingRight() { return paddingRight; }
    public void setPaddingRight(float paddingRight) { this.paddingRight = paddingRight; }
    public float getPaddingTop() { return paddingTop; }
    public void setPaddingTop(float paddingTop) { this.paddingTop = paddingTop; }
    public float getPaddingBottom() { return paddingBottom; }
    public void setPaddingBottom(float paddingBottom) { this.paddingBottom = paddingBottom; }

    // Margins
    public float getMarginLeft() { return marginLeft; }
    public void setMarginLeft(float marginLeft) { this.marginLeft = marginLeft; }
    public float getMarginRight() { return marginRight; }
    public void setMarginRight(float marginRight) { this.marginRight = marginRight; }
    public float getMarginTop() { return marginTop; }
    public void setMarginTop(float marginTop) { this.marginTop = marginTop; }
    public float getMarginBottom() { return marginBottom; }
    public void setMarginBottom(float marginBottom) { this.marginBottom = marginBottom; }

    // Visibility
    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }

    // Chain
    public UIChainStyle getHorizontalChainStyle() { return horizontalChainStyle; }
    public void setHorizontalChainStyle(UIChainStyle style) { this.horizontalChainStyle = style; }
    public UIChainStyle getVerticalChainStyle() { return verticalChainStyle; }
    public void setVerticalChainStyle(UIChainStyle style) { this.verticalChainStyle = style; }
    public float getHorizontalWeight() { return horizontalWeight; }
    public void setHorizontalWeight(float weight) { this.horizontalWeight = weight; }
    public float getVerticalWeight() { return verticalWeight; }
    public void setVerticalWeight(float weight) { this.verticalWeight = weight; }

    // Children
    public List<UIWidgetDef> getChildren() { return children; }
    public void addChild(UIWidgetDef child) { children.add(child); }
    public void removeChild(UIWidgetDef child) { children.remove(child); }

    // Panel properties
    public String getBackgroundColor() { return backgroundColor; }
    public void setBackgroundColor(String color) { this.backgroundColor = color; }
    public String getBackgroundImage() { return backgroundImage; }
    public void setBackgroundImage(String path) { this.backgroundImage = path; }

    // Button properties
    public String getButtonText() { return buttonText; }
    public void setButtonText(String text) { this.buttonText = text; }
    public String getButtonTextColor() { return buttonTextColor; }
    public void setButtonTextColor(String color) { this.buttonTextColor = color; }
    public String getButtonColor() { return buttonColor; }
    public void setButtonColor(String color) { this.buttonColor = color; }
    public String getButtonPressedColor() { return buttonPressedColor; }
    public void setButtonPressedColor(String color) { this.buttonPressedColor = color; }

    // Text properties
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getTextColor() { return textColor; }
    public void setTextColor(String color) { this.textColor = color; }
    public float getFontSize() { return fontSize; }
    public void setFontSize(float fontSize) { this.fontSize = fontSize; }
    public String getTextAlignment() { return textAlignment; }
    public void setTextAlignment(String alignment) { this.textAlignment = alignment; }
    public String getFontName() { return fontName; }
    public void setFontName(String fontName) { this.fontName = fontName; }

    // Image properties
    public String getImagePath() { return imagePath; }
    public void setImagePath(String path) { this.imagePath = path; }
    public String getImageScaleMode() { return imageScaleMode; }
    public void setImageScaleMode(String mode) { this.imageScaleMode = mode; }
    public String getSpriteName() { return spriteName; }
    public void setSpriteName(String spriteName) { this.spriteName = spriteName; }
    public int getSpriteFrame() { return spriteFrame; }
    public void setSpriteFrame(int spriteFrame) { this.spriteFrame = spriteFrame; }

    // Guideline properties
    public boolean isGuidelineHorizontal() { return guidelineIsHorizontal; }
    public void setGuidelineIsHorizontal(boolean horizontal) { this.guidelineIsHorizontal = horizontal; }
    public boolean isGuidelinePercent() { return guidelineIsPercent; }
    public void setGuidelineIsPercent(boolean percent) { this.guidelineIsPercent = percent; }
    public float getGuidelinePosition() { return guidelinePosition; }
    public void setGuidelinePosition(float position) { this.guidelinePosition = position; }

    // Center constraints
    public boolean isCenterHorizontal() { return centerHorizontal; }
    public void setCenterHorizontal(boolean centerHorizontal) { this.centerHorizontal = centerHorizontal; }
    public boolean isCenterVertical() { return centerVertical; }
    public void setCenterVertical(boolean centerVertical) { this.centerVertical = centerVertical; }

    // Z-order
    public int getZOrder() { return zOrder; }
    public void setZOrder(int zOrder) { this.zOrder = zOrder; }

    // ========================================================================
    // Constraint helpers
    // ========================================================================

    /**
     * Find the constraint for the given side, or null if none.
     */
    public UIConstraint getConstraintForSide(UIConstraintSide side) {
        for (UIConstraint c : constraints) {
            if (c.getSide() == side) return c;
        }
        return null;
    }

    /**
     * Returns true if this widget is constrained on both left and right, or centered horizontally.
     */
    public boolean isHorizontallyConstrained() {
        return centerHorizontal
            || (getConstraintForSide(UIConstraintSide.LEFT) != null
                && getConstraintForSide(UIConstraintSide.RIGHT) != null);
    }

    /**
     * Returns true if this widget is constrained on both top and bottom, or centered vertically.
     */
    public boolean isVerticallyConstrained() {
        return centerVertical
            || (getConstraintForSide(UIConstraintSide.TOP) != null
                && getConstraintForSide(UIConstraintSide.BOTTOM) != null);
    }

    // ========================================================================
    // Serialization
    // ========================================================================

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("name", name);
        json.put("type", type.name());

        // Size
        json.put("widthMode", widthMode.name());
        json.put("heightMode", heightMode.name());
        json.put("width", width);
        json.put("height", height);
        if (aspectRatio > 0) json.put("aspectRatio", aspectRatio);

        // Constraints
        JSONArray constraintsArr = new JSONArray();
        for (UIConstraint c : constraints) {
            constraintsArr.put(c.toJSON());
        }
        json.put("constraints", constraintsArr);

        json.put("horizontalBias", horizontalBias);
        json.put("verticalBias", verticalBias);

        // Padding
        if (paddingLeft != 0) json.put("paddingLeft", paddingLeft);
        if (paddingRight != 0) json.put("paddingRight", paddingRight);
        if (paddingTop != 0) json.put("paddingTop", paddingTop);
        if (paddingBottom != 0) json.put("paddingBottom", paddingBottom);

        // Margins
        if (marginLeft != 0) json.put("marginLeft", marginLeft);
        if (marginRight != 0) json.put("marginRight", marginRight);
        if (marginTop != 0) json.put("marginTop", marginTop);
        if (marginBottom != 0) json.put("marginBottom", marginBottom);

        // Visibility
        json.put("visible", visible);

        // Chain
        if (horizontalChainStyle != null) json.put("horizontalChainStyle", horizontalChainStyle.name());
        if (verticalChainStyle != null) json.put("verticalChainStyle", verticalChainStyle.name());
        if (horizontalWeight != 1.0f) json.put("horizontalWeight", horizontalWeight);
        if (verticalWeight != 1.0f) json.put("verticalWeight", verticalWeight);

        // Center constraints
        if (centerHorizontal) json.put("centerHorizontal", true);
        if (centerVertical) json.put("centerVertical", true);

        // Z-order
        if (zOrder != 0) json.put("zOrder", zOrder);

        // Type-specific
        switch (type) {
            case PANEL:
                json.put("backgroundColor", backgroundColor);
                if (backgroundImage != null) json.put("backgroundImage", backgroundImage);
                break;
            case BUTTON:
                json.put("buttonText", buttonText);
                json.put("buttonTextColor", buttonTextColor);
                json.put("buttonColor", buttonColor);
                json.put("buttonPressedColor", buttonPressedColor);
                break;
            case TEXT_VIEW:
                json.put("text", text);
                json.put("textColor", textColor);
                json.put("fontSize", fontSize);
                json.put("textAlignment", textAlignment);
                if (fontName != null) json.put("fontName", fontName);
                break;
            case IMAGE:
                if (imagePath != null) json.put("imagePath", imagePath);
                json.put("imageScaleMode", imageScaleMode);
                if (spriteName != null) json.put("spriteName", spriteName);
                json.put("spriteFrame", spriteFrame);
                break;
            case GUIDELINE:
                json.put("guidelineIsHorizontal", guidelineIsHorizontal);
                json.put("guidelineIsPercent", guidelineIsPercent);
                json.put("guidelinePosition", guidelinePosition);
                break;
        }

        // Children
        if (!children.isEmpty()) {
            JSONArray childrenArr = new JSONArray();
            for (UIWidgetDef child : children) {
                childrenArr.put(child.toJSON());
            }
            json.put("children", childrenArr);
        }

        return json;
    }

    public static UIWidgetDef fromJSON(JSONObject json) {
        String id = json.getString("id");
        String name = json.getString("name");
        UIWidgetType type = UIWidgetType.valueOf(json.getString("type"));

        UIWidgetDef def = new UIWidgetDef(id, name, type);

        // Size
        def.widthMode = UISizeMode.valueOf(json.optString("widthMode", "WRAP_CONTENT"));
        def.heightMode = UISizeMode.valueOf(json.optString("heightMode", "WRAP_CONTENT"));
        def.width = (float) json.optDouble("width", 100);
        def.height = (float) json.optDouble("height", 50);
        def.aspectRatio = (float) json.optDouble("aspectRatio", 0);

        // Constraints
        if (json.has("constraints")) {
            JSONArray constraintsArr = json.getJSONArray("constraints");
            for (int i = 0; i < constraintsArr.length(); i++) {
                def.constraints.add(UIConstraint.fromJSON(constraintsArr.getJSONObject(i)));
            }
        }

        def.horizontalBias = (float) json.optDouble("horizontalBias", 0.5);
        def.verticalBias = (float) json.optDouble("verticalBias", 0.5);

        // Padding
        def.paddingLeft = (float) json.optDouble("paddingLeft", 0);
        def.paddingRight = (float) json.optDouble("paddingRight", 0);
        def.paddingTop = (float) json.optDouble("paddingTop", 0);
        def.paddingBottom = (float) json.optDouble("paddingBottom", 0);

        // Margins
        def.marginLeft = (float) json.optDouble("marginLeft", 0);
        def.marginRight = (float) json.optDouble("marginRight", 0);
        def.marginTop = (float) json.optDouble("marginTop", 0);
        def.marginBottom = (float) json.optDouble("marginBottom", 0);

        // Visibility
        def.visible = json.optBoolean("visible", true);

        // Chain
        if (json.has("horizontalChainStyle"))
            def.horizontalChainStyle = UIChainStyle.valueOf(json.getString("horizontalChainStyle"));
        if (json.has("verticalChainStyle"))
            def.verticalChainStyle = UIChainStyle.valueOf(json.getString("verticalChainStyle"));
        def.horizontalWeight = (float) json.optDouble("horizontalWeight", 1.0);
        def.verticalWeight = (float) json.optDouble("verticalWeight", 1.0);

        // Center constraints
        def.centerHorizontal = json.optBoolean("centerHorizontal", false);
        def.centerVertical = json.optBoolean("centerVertical", false);

        // Z-order
        def.zOrder = json.optInt("zOrder", 0);

        // Type-specific
        switch (type) {
            case PANEL:
                def.backgroundColor = json.optString("backgroundColor", "#33333300");
                def.backgroundImage = json.optString("backgroundImage", null);
                break;
            case BUTTON:
                def.buttonText = json.optString("buttonText", "Button");
                def.buttonTextColor = json.optString("buttonTextColor", "#FFFFFFFF");
                def.buttonColor = json.optString("buttonColor", "#4488FFFF");
                def.buttonPressedColor = json.optString("buttonPressedColor", "#2266CCFF");
                break;
            case TEXT_VIEW:
                def.text = json.optString("text", "Text");
                def.textColor = json.optString("textColor", "#FFFFFFFF");
                def.fontSize = (float) json.optDouble("fontSize", 16);
                def.textAlignment = json.optString("textAlignment", "left");
                def.fontName = json.optString("fontName", null);
                break;
            case IMAGE:
                def.imagePath = json.optString("imagePath", null);
                def.imageScaleMode = json.optString("imageScaleMode", "fit");
                def.spriteName = json.optString("spriteName", null);
                def.spriteFrame = json.optInt("spriteFrame", 0);
                break;
            case GUIDELINE:
                def.guidelineIsHorizontal = json.optBoolean("guidelineIsHorizontal", true);
                def.guidelineIsPercent = json.optBoolean("guidelineIsPercent", false);
                def.guidelinePosition = (float) json.optDouble("guidelinePosition", 0);
                break;
        }

        // Children
        if (json.has("children")) {
            JSONArray childrenArr = json.getJSONArray("children");
            for (int i = 0; i < childrenArr.length(); i++) {
                def.children.add(UIWidgetDef.fromJSON(childrenArr.getJSONObject(i)));
            }
        }

        return def;
    }
}
