package com.scenemax.designer.gizmo;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * A 3D progress-bar gizmo displayed at the position of a model
 * that is being loaded asynchronously.  Renders as a horizontal bar
 * with a "Loading model: xxx" label above it.  Always faces the camera
 * (billboard behaviour applied manually each frame).
 */
public class LoadingProgressGizmo extends Node {

    private static final float BAR_WIDTH = 3.0f;
    private static final float BAR_HEIGHT = 0.35f;
    private static final ColorRGBA BG_COLOR = new ColorRGBA(0.12f, 0.12f, 0.12f, 0.9f);
    private static final ColorRGBA FILL_COLOR = new ColorRGBA(0.0f, 0.85f, 1.0f, 1.0f);
    private static final ColorRGBA BORDER_COLOR = new ColorRGBA(0.7f, 0.7f, 0.7f, 0.9f);
    private static final ColorRGBA TEXT_COLOR = new ColorRGBA(1.0f, 1.0f, 1.0f, 1.0f);
    private static final float TEXT_SIZE = 0.3f;

    private final Geometry bgGeometry;
    private final Geometry fillGeometry;
    private float progress = 0f; // 0..1

    public LoadingProgressGizmo(AssetManager assetManager, String modelName) {
        super("LoadingProgressGizmo");

        // Border outline (slightly larger than background)
        float borderPad = 0.04f;
        Geometry borderGeometry = createQuad("ProgressBorder", BAR_WIDTH + borderPad * 2, BAR_HEIGHT + borderPad * 2);
        Material borderMat = createTransparentMat(assetManager, BORDER_COLOR);
        borderGeometry.setMaterial(borderMat);
        borderGeometry.setQueueBucket(RenderQueue.Bucket.Transparent);
        borderGeometry.setLocalTranslation(-BAR_WIDTH / 2f - borderPad, -borderPad, -0.001f);
        attachChild(borderGeometry);

        // Background bar (full width)
        bgGeometry = createQuad("ProgressBg", BAR_WIDTH, BAR_HEIGHT);
        Material bgMat = createTransparentMat(assetManager, BG_COLOR);
        bgGeometry.setMaterial(bgMat);
        bgGeometry.setQueueBucket(RenderQueue.Bucket.Transparent);
        bgGeometry.setLocalTranslation(-BAR_WIDTH / 2f, 0, 0);
        attachChild(bgGeometry);

        // Fill bar (variable width, starts at 0)
        fillGeometry = createQuad("ProgressFill", 0.001f, BAR_HEIGHT);
        Material fillMat = createTransparentMat(assetManager, FILL_COLOR);
        fillGeometry.setMaterial(fillMat);
        fillGeometry.setQueueBucket(RenderQueue.Bucket.Transparent);
        fillGeometry.setLocalTranslation(-BAR_WIDTH / 2f, 0, 0.001f);
        attachChild(fillGeometry);

        // Text label above the bar
        BitmapFont font = assetManager.loadFont("Interface/Fonts/Default.fnt");
        BitmapText label = new BitmapText(font, false);
        String displayName = modelName != null ? modelName : "model";
        label.setText("Loading model: " + displayName);
        label.setSize(TEXT_SIZE);
        label.setColor(TEXT_COLOR);
        label.setQueueBucket(RenderQueue.Bucket.Transparent);
        // Center text above the bar
        label.setLocalTranslation(-label.getLineWidth() / 2f,
                BAR_HEIGHT + TEXT_SIZE + 0.1f, 0.002f);
        attachChild(label);
    }

    private Material createTransparentMat(AssetManager assetManager, ColorRGBA color) {
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        mat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        mat.getAdditionalRenderState().setDepthWrite(false);
        return mat;
    }

    /**
     * Sets the fill ratio (0 = empty, 1 = full).
     */
    public void setProgress(float progress) {
        this.progress = Math.max(0f, Math.min(1f, progress));
        float fillWidth = Math.max(0.001f, BAR_WIDTH * this.progress);
        fillGeometry.setMesh(createQuadMesh(fillWidth, BAR_HEIGHT));
    }

    public float getProgress() {
        return progress;
    }

    /**
     * Makes the gizmo face the camera (billboard).
     * Call this every frame from simpleUpdate().
     */
    public void faceCamera(Camera cam) {
        Vector3f dir = cam.getLocation().subtract(getWorldTranslation()).normalizeLocal();
        Quaternion rot = new Quaternion();
        rot.lookAt(dir, Vector3f.UNIT_Y);
        setLocalRotation(rot);
    }

    private Geometry createQuad(String name, float width, float height) {
        Mesh mesh = createQuadMesh(width, height);
        return new Geometry(name, mesh);
    }

    private Mesh createQuadMesh(float width, float height) {
        float halfH = height / 2f;
        FloatBuffer vb = BufferUtils.createFloatBuffer(4 * 3);
        vb.put(0).put(-halfH).put(0);
        vb.put(width).put(-halfH).put(0);
        vb.put(width).put(halfH).put(0);
        vb.put(0).put(halfH).put(0);
        vb.flip();

        ShortBuffer ib = BufferUtils.createShortBuffer(6);
        ib.put((short) 0).put((short) 1).put((short) 2);
        ib.put((short) 0).put((short) 2).put((short) 3);
        ib.flip();

        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, vb);
        mesh.setBuffer(VertexBuffer.Type.Index, 3, ib);
        mesh.updateBound();
        return mesh;
    }
}
