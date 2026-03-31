package com.scenemaxeng.common.ui.widget;

import com.jme3.asset.AssetManager;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import com.scenemaxeng.common.ui.model.UIWidgetDef;

/**
 * A panel widget - a colored or textured rectangular area.
 * Panels can contain child widgets.
 */
public class UIPanelNode extends UIWidgetNode {

    public UIPanelNode(String name, UIWidgetDef widgetDef, AssetManager assetManager,
                       float designCanvasWidth, float designCanvasHeight,
                       float runtimeCanvasWidth, float runtimeCanvasHeight) {
        super(name, widgetDef, assetManager, designCanvasWidth, designCanvasHeight, runtimeCanvasWidth, runtimeCanvasHeight);
    }

    @Override
    public void createVisual() {
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

    public void setBackgroundColor(String hexColor) {
        widgetDef.setBackgroundColor(hexColor);
        if (backgroundGeom != null) {
            backgroundGeom.setMaterial(createColorMaterial(parseColor(hexColor)));
        }
    }

    public void setBackgroundImage(String imagePath) {
        widgetDef.setBackgroundImage(imagePath);
        if (backgroundGeom != null && imagePath != null && !imagePath.isEmpty()) {
            backgroundGeom.setMaterial(createTextureMaterial(imagePath));
        }
    }
}
