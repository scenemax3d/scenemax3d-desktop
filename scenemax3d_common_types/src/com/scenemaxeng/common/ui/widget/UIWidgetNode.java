package com.scenemaxeng.common.ui.widget;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.scenemaxeng.common.types.AssetsMapping;
import com.scenemaxeng.common.ui.layout.LayoutRect;
import com.scenemaxeng.common.ui.model.UIWidgetDef;
import com.scenemaxeng.common.ui.model.UIWidgetType;

import java.util.logging.Level;
import java.util.logging.Logger;

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

    private static final Logger LOGGER = Logger.getLogger(UIWidgetNode.class.getName());

    protected UIWidgetDef widgetDef;
    protected AssetManager assetManager;
    protected LayoutRect layoutRect;
    protected float designCanvasWidth;
    protected float designCanvasHeight;
    protected float runtimeCanvasWidth;
    protected float runtimeCanvasHeight;

    protected Geometry backgroundGeom;

    public UIWidgetNode(String name, UIWidgetDef widgetDef, AssetManager assetManager,
                        float designCanvasWidth, float designCanvasHeight,
                        float runtimeCanvasWidth, float runtimeCanvasHeight) {
        super(name);
        this.widgetDef = widgetDef;
        this.assetManager = assetManager;
        this.designCanvasWidth = designCanvasWidth;
        this.designCanvasHeight = designCanvasHeight;
        this.runtimeCanvasWidth = runtimeCanvasWidth;
        this.runtimeCanvasHeight = runtimeCanvasHeight;
    }

    public abstract void createVisual();

    public void updateLayout(LayoutRect rect) {
        this.layoutRect = rect;

        float scaleX = designCanvasWidth != 0 ? runtimeCanvasWidth / designCanvasWidth : 1f;
        float scaleY = designCanvasHeight != 0 ? runtimeCanvasHeight / designCanvasHeight : 1f;

        float scaledX = rect.x * scaleX;
        float scaledY = rect.y * scaleY;
        float scaledWidth = rect.width * scaleX;
        float scaledHeight = rect.height * scaleY;

        float jmeX;
        float jmeY;
        if (getParent() instanceof UIWidgetNode) {
            UIWidgetNode parentWidget = (UIWidgetNode) getParent();
            LayoutRect parentRect = parentWidget.getLayoutRect();
            if (parentRect != null) {
                float scaledParentX = parentRect.x * scaleX;
                float scaledParentY = parentRect.y * scaleY;
                float scaledParentHeight = parentRect.height * scaleY;
                float localX = scaledX - scaledParentX;
                float localY = scaledY - scaledParentY;
                jmeX = localX;
                jmeY = scaledParentHeight - localY - scaledHeight;
            } else {
                jmeX = scaledX;
                jmeY = runtimeCanvasHeight - scaledY - scaledHeight;
            }
        } else {
            jmeX = scaledX;
            jmeY = runtimeCanvasHeight - scaledY - scaledHeight;
        }

        LOGGER.log(Level.INFO, "Applying UI widget ''{0}'' type={1} rect={2} jmeTranslation=({3}, {4}, 0)",
                new Object[]{getName(), widgetDef.getType(), rect, jmeX, jmeY});

        setLocalTranslation(jmeX, jmeY, 0);

        if (backgroundGeom != null) {
            backgroundGeom.setMesh(new Quad(scaledWidth, scaledHeight));
        }

        onLayoutUpdated(rect);
    }

    protected void onLayoutUpdated(LayoutRect rect) {
    }

    public void setWidgetVisible(boolean visible) {
        setCullHint(visible ? CullHint.Inherit : CullHint.Always);
    }

    public UIWidgetDef getWidgetDef() {
        return widgetDef;
    }

    public LayoutRect getLayoutRect() {
        return layoutRect;
    }

    protected Material createColorMaterial(ColorRGBA color) {
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        return mat;
    }

    protected Material createTextureMaterial(String texturePath) {
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setTexture("ColorMap", assetManager.loadTexture(texturePath));
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        return mat;
    }

    protected static ColorRGBA parseColor(String hex) {
        if (hex == null || hex.isEmpty()) {
            return ColorRGBA.White.clone();
        }
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }

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

    public static UIWidgetNode create(UIWidgetDef def, AssetManager assetManager,
                                      float designCanvasWidth, float designCanvasHeight,
                                      float runtimeCanvasWidth, float runtimeCanvasHeight,
                                      AssetsMapping assetsMapping) {
        switch (def.getType()) {
            case PANEL:
                return new UIPanelNode(def.getName(), def, assetManager,
                        designCanvasWidth, designCanvasHeight, runtimeCanvasWidth, runtimeCanvasHeight);
            case BUTTON:
                return new UIButtonNode(def.getName(), def, assetManager,
                        designCanvasWidth, designCanvasHeight, runtimeCanvasWidth, runtimeCanvasHeight);
            case TEXT_VIEW:
                return new UITextViewNode(def.getName(), def, assetManager,
                        designCanvasWidth, designCanvasHeight, runtimeCanvasWidth, runtimeCanvasHeight);
            case IMAGE:
                return new UIImageNode(def.getName(), def, assetManager,
                        designCanvasWidth, designCanvasHeight, runtimeCanvasWidth, runtimeCanvasHeight, assetsMapping);
            default:
                return null;
        }
    }
}
