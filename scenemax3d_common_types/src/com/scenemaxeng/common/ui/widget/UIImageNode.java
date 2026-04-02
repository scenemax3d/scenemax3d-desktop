package com.scenemaxeng.common.ui.widget;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;
import com.scenemaxeng.common.types.AssetsMapping;
import com.scenemaxeng.common.types.ResourceSetup2D;
import com.scenemaxeng.common.ui.layout.LayoutRect;
import com.scenemaxeng.common.ui.model.UIWidgetDef;

/**
 * An image widget - displays a texture on a quad.
 * Supports both direct image paths and sprite resources from AssetsMapping.
 */
public class UIImageNode extends UIWidgetNode {

    private Texture loadedTexture;
    private AssetsMapping assetsMapping;
    private ResourceSetup2D activeSpriteResource;

    public UIImageNode(String name, UIWidgetDef widgetDef, AssetManager assetManager,
                       float designCanvasWidth, float designCanvasHeight,
                       float runtimeCanvasWidth, float runtimeCanvasHeight,
                       AssetsMapping assetsMapping) {
        super(name, widgetDef, assetManager, designCanvasWidth, designCanvasHeight, runtimeCanvasWidth, runtimeCanvasHeight);
        this.assetsMapping = assetsMapping;
    }

    @Override
    public void createVisual() {
        Quad quad = new Quad(widgetDef.getWidth(), widgetDef.getHeight());
        backgroundGeom = new Geometry(getName() + "_img", quad);

        applyImageMaterial();

        backgroundGeom.setQueueBucket(RenderQueue.Bucket.Gui);
        attachChild(backgroundGeom);
    }

    private void applyImageMaterial() {
        String spriteName = widgetDef.getSpriteName();
        activeSpriteResource = null;

        // Sprite takes priority over imagePath
        if (spriteName != null && !spriteName.isEmpty() && assetsMapping != null) {
            ResourceSetup2D resource = assetsMapping.getSpriteSheetsIndex().get(spriteName.toLowerCase());
            if (resource != null) {
                try {
                    activeSpriteResource = resource;
                    loadedTexture = assetManager.loadTexture(resource.path);
                    Material mat = new Material(assetManager, "MatDefs/sprite_sheet.j3md");
                    mat.setTexture("ColorMap", loadedTexture);
                    mat.setFloat("SizeX", resource.cols);
                    mat.setFloat("SizeY", resource.rows);
                    mat.setFloat("Position", clampSpriteFrame(widgetDef.getSpriteFrame(), resource));
                    mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
                    backgroundGeom.setMaterial(mat);
                    return;
                } catch (Exception e) {
                    System.err.println("[UIImage] Failed to load sprite texture: " + spriteName + " path=" + resource.path);
                }
            } else {
                System.err.println("[UIImage] Sprite not found in AssetsMapping: " + spriteName);
            }
        }

        // Fall back to imagePath
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
    }

    @Override
    protected void onLayoutUpdated(LayoutRect rect) {
    }

    public void setImage(String imagePath) {
        widgetDef.setImagePath(imagePath);
        widgetDef.setSpriteName(null);
        widgetDef.setSpriteFrame(0);
        if (backgroundGeom != null) {
            applyImageMaterial();
        }
    }

    public void setSprite(String spriteName) {
        widgetDef.setSpriteName(spriteName);
        widgetDef.setImagePath(null);
        if (backgroundGeom != null) {
            applyImageMaterial();
        }
    }

    public void setSpriteFrame(int frame) {
        widgetDef.setSpriteFrame(frame);
        if (backgroundGeom == null) {
            return;
        }

        if (activeSpriteResource == null) {
            applyImageMaterial();
            return;
        }

        Material mat = backgroundGeom.getMaterial();
        if (mat == null) {
            applyImageMaterial();
            return;
        }

        mat.setFloat("Position", clampSpriteFrame(frame, activeSpriteResource));
    }

    private float clampSpriteFrame(int frame, ResourceSetup2D resource) {
        if (resource == null) {
            return Math.max(0, frame);
        }
        int maxFrame = Math.max(0, resource.cols * resource.rows - 1);
        return Math.max(0, Math.min(frame, maxFrame));
    }
}
