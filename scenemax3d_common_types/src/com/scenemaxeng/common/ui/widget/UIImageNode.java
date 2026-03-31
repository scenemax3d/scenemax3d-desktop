package com.scenemaxeng.common.ui.widget;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;
import com.scenemaxeng.common.ui.layout.LayoutRect;
import com.scenemaxeng.common.ui.model.UIWidgetDef;

/**
 * An image widget - displays a texture on a quad.
 */
public class UIImageNode extends UIWidgetNode {

    private Texture loadedTexture;

    public UIImageNode(String name, UIWidgetDef widgetDef, AssetManager assetManager,
                       float designCanvasWidth, float designCanvasHeight,
                       float runtimeCanvasWidth, float runtimeCanvasHeight) {
        super(name, widgetDef, assetManager, designCanvasWidth, designCanvasHeight, runtimeCanvasWidth, runtimeCanvasHeight);
    }

    @Override
    public void createVisual() {
        Quad quad = new Quad(widgetDef.getWidth(), widgetDef.getHeight());
        backgroundGeom = new Geometry(getName() + "_img", quad);

        String path = widgetDef.getImagePath();
        if (path != null && !path.isEmpty()) {
            try {
                loadedTexture = assetManager.loadTexture(path);
                Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
                mat.setTexture("ColorMap", loadedTexture);
                mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
                backgroundGeom.setMaterial(mat);
            } catch (Exception e) {
                backgroundGeom.setMaterial(createColorMaterial(new com.jme3.math.ColorRGBA(1, 0, 1, 1)));
                System.err.println("[UIImage] Failed to load texture: " + path);
            }
        } else {
            backgroundGeom.setMaterial(createColorMaterial(new com.jme3.math.ColorRGBA(0.5f, 0.5f, 0.5f, 0.3f)));
        }

        backgroundGeom.setQueueBucket(RenderQueue.Bucket.Gui);
        attachChild(backgroundGeom);
    }

    @Override
    protected void onLayoutUpdated(LayoutRect rect) {
    }

    public void setImage(String imagePath) {
        widgetDef.setImagePath(imagePath);
        if (backgroundGeom != null && imagePath != null && !imagePath.isEmpty()) {
            try {
                loadedTexture = assetManager.loadTexture(imagePath);
                Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
                mat.setTexture("ColorMap", loadedTexture);
                mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
                backgroundGeom.setMaterial(mat);
            } catch (Exception e) {
                System.err.println("[UIImage] Failed to load texture: " + imagePath);
            }
        }
    }
}
