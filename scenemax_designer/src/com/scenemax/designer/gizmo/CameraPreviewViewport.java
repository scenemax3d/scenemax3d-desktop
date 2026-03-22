package com.scenemax.designer.gizmo;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.ui.Picture;
import com.scenemax.designer.DesignerEntity;

/**
 * Manages an offscreen viewport that renders a live camera preview.
 * Displayed as a small picture-in-picture overlay in the bottom-right corner,
 * similar to Unity's camera preview when a camera object is selected.
 */
public class CameraPreviewViewport {

    private static final int PREVIEW_WIDTH = 320;
    private static final int PREVIEW_HEIGHT = 180;
    private static final int MARGIN = 10;
    private static final int BORDER = 2;

    private Camera previewCam;
    private ViewPort previewViewPort;
    private FrameBuffer frameBuffer;
    private Texture2D previewTexture;
    private Node previewOverlay; // contains picture + border
    private Picture previewPicture;
    private boolean visible = false;
    private float posX, posY;

    /**
     * Initializes the offscreen preview camera, framebuffer, and GUI overlay.
     */
    public void init(AssetManager assetManager, RenderManager renderManager,
                     Node rootNode, int screenWidth, int screenHeight) {
        // 1. Create preview camera
        previewCam = new Camera(PREVIEW_WIDTH, PREVIEW_HEIGHT);
        previewCam.setFrustumPerspective(45f, (float) PREVIEW_WIDTH / PREVIEW_HEIGHT, 0.1f, 1000f);

        // 2. Create FrameBuffer for offscreen rendering
        frameBuffer = new FrameBuffer(PREVIEW_WIDTH, PREVIEW_HEIGHT, 1);
        frameBuffer.setDepthBuffer(Image.Format.Depth);
        previewTexture = new Texture2D(PREVIEW_WIDTH, PREVIEW_HEIGHT, Image.Format.RGBA8);
        previewTexture.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        previewTexture.setMagFilter(Texture.MagFilter.Bilinear);
        frameBuffer.setColorTexture(previewTexture);

        // 3. Create pre-view viewport that renders rootNode into the framebuffer
        previewViewPort = renderManager.createPreView("CameraPreviewVP", previewCam);
        previewViewPort.setClearFlags(true, true, true);
        previewViewPort.setBackgroundColor(new ColorRGBA(0.15f, 0.15f, 0.15f, 1f));
        previewViewPort.setOutputFrameBuffer(frameBuffer);
        previewViewPort.attachScene(rootNode);

        // 4. Create the GUI overlay node (picture + border)
        previewOverlay = new Node("CameraPreviewOverlay");

        // Border quad (slightly larger than the preview, behind it)
        Geometry border = createBorderGeometry(assetManager);
        border.setLocalTranslation(-BORDER, -BORDER, 0);
        previewOverlay.attachChild(border);

        // Preview picture
        previewPicture = new Picture("CameraPreviewPic");
        previewPicture.setTexture(assetManager, previewTexture, false);
        previewPicture.setWidth(PREVIEW_WIDTH);
        previewPicture.setHeight(PREVIEW_HEIGHT);
        previewPicture.setLocalTranslation(0, 0, 1); // in front of border
        previewOverlay.attachChild(previewPicture);

        // "Camera Preview" label could be added here if desired

        updatePosition(screenWidth, screenHeight);
    }

    /**
     * Creates a solid dark border quad behind the preview picture.
     */
    private Geometry createBorderGeometry(AssetManager assetManager) {
        Quad quad = new Quad(PREVIEW_WIDTH + BORDER * 2, PREVIEW_HEIGHT + BORDER * 2);
        Geometry geo = new Geometry("CamPreviewBorder", quad);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", new ColorRGBA(0.8f, 0.8f, 0.8f, 1f));
        geo.setMaterial(mat);
        return geo;
    }

    /**
     * Shows the preview overlay in the GUI.
     */
    public void show(Node guiNode) {
        if (!visible) {
            guiNode.attachChild(previewOverlay);
            visible = true;
        }
    }

    /**
     * Hides the preview overlay.
     */
    public void hide() {
        if (visible) {
            previewOverlay.removeFromParent();
            visible = false;
        }
    }

    public boolean isVisible() {
        return visible;
    }

    /**
     * Syncs the preview camera with the camera entity's world transform.
     */
    public void syncWithEntity(DesignerEntity cameraEntity) {
        if (cameraEntity == null || previewCam == null) return;
        Node node = cameraEntity.getSceneNode();
        if (node == null) return;

        Vector3f pos = node.getWorldTranslation();
        Quaternion rot = node.getWorldRotation();

        previewCam.setLocation(pos);
        previewCam.setRotation(rot);
    }

    /**
     * Updates the overlay position for the bottom-right corner after a resize.
     */
    public void updatePosition(int screenWidth, int screenHeight) {
        posX = screenWidth - PREVIEW_WIDTH - MARGIN;
        posY = MARGIN;
        if (previewOverlay != null) {
            previewOverlay.setLocalTranslation(posX, posY, 0);
        }
    }

    /**
     * Checks if a screen point falls within the preview overlay area.
     */
    public boolean containsPoint(Vector2f screenPos) {
        return screenPos.x >= posX - BORDER && screenPos.x <= posX + PREVIEW_WIDTH + BORDER &&
               screenPos.y >= posY - BORDER && screenPos.y <= posY + PREVIEW_HEIGHT + BORDER;
    }

    /**
     * Cleans up the viewport and framebuffer.
     */
    public void cleanup(RenderManager renderManager) {
        hide();
        if (previewViewPort != null) {
            renderManager.removePreView(previewViewPort);
        }
    }
}
