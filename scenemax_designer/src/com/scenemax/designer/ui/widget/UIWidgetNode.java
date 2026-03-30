package com.scenemax.designer.ui.widget;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.scenemax.designer.ui.layout.LayoutRect;
import com.scenemax.designer.ui.model.UIWidgetDef;
import com.scenemax.designer.ui.model.UIWidgetType;

/**
 * Base class for all JME-rendered UI widgets.
 * Each widget is a JME Node that wraps a Geometry (typically a Quad).
 *
 * The coordinate system for screen-space UI:
 *   - Origin is bottom-left (JME guiNode convention)
 *   - X grows right, Y grows up
 *
 * LayoutRect uses top-left origin (Y grows down), so updateLayout()
 * converts between the two coordinate systems.
 */
public abstract class UIWidgetNode extends Node {

    protected UIWidgetDef widgetDef;
    protected AssetManager assetManager;
    protected LayoutRect layoutRect;
    protected float canvasHeight; // needed for Y-axis flip

    // The background quad geometry (most widgets have one)
    protected Geometry backgroundGeom;

    public UIWidgetNode(String name, UIWidgetDef widgetDef, AssetManager assetManager, float canvasHeight) {
        super(name);
        this.widgetDef = widgetDef;
        this.assetManager = assetManager;
        this.canvasHeight = canvasHeight;
    }

    /**
     * Creates the visual representation. Called once after construction.
     * Subclasses must implement this to build their specific geometry.
     */
    public abstract void createVisual();

    /**
     * Updates this node's position and size based on computed layout.
     * Converts from layout coordinates (top-left origin, Y down) to
     * JME guiNode coordinates (bottom-left origin, Y up).
     */
    public void updateLayout(LayoutRect rect) {
        this.layoutRect = rect;

        // Convert from top-left Y-down to bottom-left Y-up
        float jmeX = rect.x;
        float jmeY = canvasHeight - rect.y - rect.height;

        setLocalTranslation(jmeX, jmeY, 0);

        // Rebuild background quad with new dimensions
        if (backgroundGeom != null) {
            backgroundGeom.setMesh(new Quad(rect.width, rect.height));
        }

        // Let subclasses update their specific visuals
        onLayoutUpdated(rect);
    }

    /**
     * Called after updateLayout() to let subclasses adjust their content
     * (e.g., reposition text within a button).
     */
    protected void onLayoutUpdated(LayoutRect rect) {
        // Override in subclasses
    }

    /**
     * Shows or hides this widget.
     */
    public void setWidgetVisible(boolean visible) {
        setCullHint(visible ? CullHint.Inherit : CullHint.Always);
    }

    public UIWidgetDef getWidgetDef() { return widgetDef; }
    public LayoutRect getLayoutRect() { return layoutRect; }

    // ========================================================================
    // Utility methods for subclasses
    // ========================================================================

    /**
     * Creates a flat-colored unshaded material with alpha blending.
     */
    protected Material createColorMaterial(ColorRGBA color) {
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        return mat;
    }

    /**
     * Creates a textured unshaded material with alpha blending.
     */
    protected Material createTextureMaterial(String texturePath) {
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setTexture("ColorMap", assetManager.loadTexture(texturePath));
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        return mat;
    }

    /**
     * Parses a hex color string (#RRGGBBAA or #RRGGBB) into a JME ColorRGBA.
     */
    protected static ColorRGBA parseColor(String hex) {
        if (hex == null || hex.isEmpty()) return ColorRGBA.White.clone();
        if (hex.startsWith("#")) hex = hex.substring(1);

        try {
            float r = Integer.parseInt(hex.substring(0, 2), 16) / 255f;
            float g = Integer.parseInt(hex.substring(2, 4), 16) / 255f;
            float b = Integer.parseInt(hex.substring(4, 6), 16) / 255f;
            float a = hex.length() >= 8 ? Integer.parseInt(hex.substring(6, 8), 16) / 255f : 1.0f;
            return new ColorRGBA(r, g, b, a);
        } catch (Exception e) {
            return ColorRGBA.White.clone();
        }
    }

    /**
     * Factory method: creates the appropriate widget node subclass based on widget type.
     */
    public static UIWidgetNode create(UIWidgetDef def, AssetManager assetManager, float canvasHeight) {
        switch (def.getType()) {
            case PANEL:
                return new UIPanelNode(def.getName(), def, assetManager, canvasHeight);
            case BUTTON:
                return new UIButtonNode(def.getName(), def, assetManager, canvasHeight);
            case TEXT_VIEW:
                return new UITextViewNode(def.getName(), def, assetManager, canvasHeight);
            case IMAGE:
                return new UIImageNode(def.getName(), def, assetManager, canvasHeight);
            default:
                return null;
        }
    }
}
