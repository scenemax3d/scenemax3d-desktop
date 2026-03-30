package com.scenemax.designer.ui.widget;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.scenemax.designer.ui.layout.LayoutRect;
import com.scenemax.designer.ui.model.UIWidgetDef;

/**
 * An image widget — displays a texture on a quad.
 *
 * Properties:
 *   imagePath      - path to the texture asset
 *   imageScaleMode - "fit" (maintain ratio, fit inside bounds),
 *                    "fill" (maintain ratio, fill bounds, may crop),
 *                    "stretch" (ignore ratio, fill bounds exactly)
 */
public class UIImageNode extends UIWidgetNode {

    private Texture loadedTexture;

    public UIImageNode(String name, UIWidgetDef widgetDef, AssetManager assetManager, float canvasHeight) {
        super(name, widgetDef, assetManager, canvasHeight);
    }

    @Override
    public void createVisual() {
        // Create a quad with initial dimensions
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
                // Fallback to a magenta placeholder on load failure
                backgroundGeom.setMaterial(createColorMaterial(
                        new com.jme3.math.ColorRGBA(1, 0, 1, 1)));
                System.err.println("[UIImage] Failed to load texture: " + path);
            }
        } else {
            // No image set — show a placeholder
            backgroundGeom.setMaterial(createColorMaterial(
                    new com.jme3.math.ColorRGBA(0.5f, 0.5f, 0.5f, 0.3f)));
        }

        backgroundGeom.setQueueBucket(RenderQueue.Bucket.Gui);
        attachChild(backgroundGeom);
    }

    @Override
    protected void onLayoutUpdated(LayoutRect rect) {
        // For "fit" and "fill" modes, we could adjust the quad size or UV coordinates.
        // For now, "stretch" is the default behavior (quad fills the layout rect).
        // TODO: implement fit/fill scale modes with UV or geometry adjustment
    }

    /**
     * Changes the image at runtime.
     */
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
