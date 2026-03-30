package com.scenemax.designer.ui.widget;

import com.jme3.asset.AssetManager;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import com.scenemax.designer.ui.layout.LayoutRect;
import com.scenemax.designer.ui.model.UIWidgetDef;

/**
 * A panel widget — a colored or textured rectangular area.
 * Panels can contain child widgets.
 *
 * Properties:
 *   backgroundColor - hex RGBA color string
 *   backgroundImage - optional texture path
 */
public class UIPanelNode extends UIWidgetNode {

    public UIPanelNode(String name, UIWidgetDef widgetDef, AssetManager assetManager, float canvasHeight) {
        super(name, widgetDef, assetManager, canvasHeight);
    }

    @Override
    public void createVisual() {
        // Create a quad for the panel background
        Quad quad = new Quad(widgetDef.getWidth(), widgetDef.getHeight());
        backgroundGeom = new Geometry(getName() + "_bg", quad);

        if (widgetDef.getBackgroundImage() != null && !widgetDef.getBackgroundImage().isEmpty()) {
            backgroundGeom.setMaterial(createTextureMaterial(widgetDef.getBackgroundImage()));
        } else {
            ColorRGBA color = parseColor(widgetDef.getBackgroundColor());
            backgroundGeom.setMaterial(createColorMaterial(color));
        }

        backgroundGeom.setQueueBucket(RenderQueue.Bucket.Gui);
        attachChild(backgroundGeom);
    }

    /**
     * Updates the background color at runtime.
     */
    public void setBackgroundColor(String hexColor) {
        widgetDef.setBackgroundColor(hexColor);
        if (backgroundGeom != null) {
            backgroundGeom.setMaterial(createColorMaterial(parseColor(hexColor)));
        }
    }

    /**
     * Updates the background image at runtime.
     */
    public void setBackgroundImage(String imagePath) {
        widgetDef.setBackgroundImage(imagePath);
        if (backgroundGeom != null && imagePath != null && !imagePath.isEmpty()) {
            backgroundGeom.setMaterial(createTextureMaterial(imagePath));
        }
    }
}
