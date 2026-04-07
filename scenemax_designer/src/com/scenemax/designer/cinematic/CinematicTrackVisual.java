package com.scenemax.designer.cinematic;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Sphere;
import com.jme3.util.BufferUtils;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Visual authoring overlay for a cinematic ellipse track.
 */
public class CinematicTrackVisual extends Node {

    private static final ColorRGBA TRACK_COLOR = new ColorRGBA(0.8f, 0.88f, 1f, 1f);
    private static final ColorRGBA RANGE_COLOR = new ColorRGBA(1f, 0.8f, 0.2f, 1f);
    private static final ColorRGBA PLAYBACK_COLOR = new ColorRGBA(1f, 0.35f, 0.12f, 1f);
    private static final ColorRGBA ANCHOR_COLOR = new ColorRGBA(0.5f, 0.9f, 1f, 1f);
    private static final ColorRGBA SELECTED_COLOR = new ColorRGBA(1f, 0.45f, 0.15f, 1f);

    private final AssetManager assetManager;
    private final Node ellipseNode = new Node("CinematicEllipse");
    private final Node rangeNode = new Node("CinematicRange");
    private final Node anchorNode = new Node("CinematicAnchors");

    public CinematicTrackVisual(AssetManager assetManager) {
        super("CinematicTrackVisual");
        this.assetManager = assetManager;
        attachChild(ellipseNode);
        attachChild(rangeNode);
        attachChild(anchorNode);
    }

    public void rebuild(CinematicTrackData data) {
        ellipseNode.detachAllChildren();
        rangeNode.detachAllChildren();
        anchorNode.detachAllChildren();
        if (data == null) {
            return;
        }

        addLineStrip("TrackEllipse", sampleEllipse(data, data.getAnchorCount(), true), TRACK_COLOR, 2.5f, ellipseNode);

        int anchorCount = data.getAnchorCount();
        for (int i = 0; i < anchorCount; i++) {
            boolean emphasized = (i % 15 == 0);
            boolean selected = i == data.getSelectedStartAnchor() || i == data.getSelectedEndAnchor();
            addAnchorSphere(data.getAnchorLocalPoint(i), i, selected, emphasized);
        }

        if (data.getSelectedStartAnchor() >= 0 && data.getSelectedEndAnchor() >= 0) {
            addLineStrip("TrackRange", sampleSelectedRange(data), RANGE_COLOR, 4.0f, rangeNode);
        }

        if (data.hasPlaybackPreview()) {
            addLineStrip("PlaybackRange", samplePlaybackRange(data), PLAYBACK_COLOR, 5.0f, rangeNode);
            addCursorSphere(samplePlaybackCursorPoint(data));
        }
    }

    private List<Vector3f> sampleEllipse(CinematicTrackData data, int samples, boolean closeLoop) {
        List<Vector3f> points = new ArrayList<>();
        int count = Math.max(16, samples);
        for (int i = 0; i < count; i++) {
            points.add(data.getAnchorLocalPoint(i));
        }
        if (closeLoop && !points.isEmpty()) {
            points.add(points.get(0).clone());
        }
        return points;
    }

    private List<Vector3f> sampleSelectedRange(CinematicTrackData data) {
        List<Vector3f> points = new ArrayList<>();
        int start = data.getSelectedStartAnchor();
        int end = data.getSelectedEndAnchor();
        if (start < 0 || end < 0) {
            return points;
        }
        int count = data.getAnchorCount();
        int current = start;
        points.add(data.getAnchorLocalPoint(current));
        while (current != end) {
            current = (current + 1) % count;
            points.add(data.getAnchorLocalPoint(current));
            if (points.size() > count + 1) {
                break;
            }
        }
        return points;
    }

    private List<Vector3f> samplePlaybackRange(CinematicTrackData data) {
        List<Vector3f> points = new ArrayList<>();
        int start = data.getPlaybackStartAnchor();
        float cursor = data.getPlaybackCursorAnchor();
        if (start < 0 || cursor < 0f) {
            return points;
        }
        int count = data.getAnchorCount();
        int current = start;
        points.add(data.getAnchorLocalPoint(current));
        int guard = 0;
        while (true) {
            int next = (current + 1) % count;
            float nextAnchor = next;
            float wrappedCursor = cursor;
            if (wrappedCursor < start) {
                wrappedCursor += count;
            }
            float currentWrapped = current;
            if (currentWrapped < start) currentWrapped += count;
            float nextWrapped = nextAnchor;
            if (nextWrapped <= currentWrapped) nextWrapped += count;
            if (wrappedCursor <= nextWrapped) {
                int index0 = current;
                int index1 = next;
                float alpha = (wrappedCursor - currentWrapped) / (nextWrapped - currentWrapped);
                Vector3f p0 = data.getAnchorLocalPoint(index0);
                Vector3f p1 = data.getAnchorLocalPoint(index1);
                points.add(p0.interpolateLocal(p1, alpha));
                break;
            }
            points.add(data.getAnchorLocalPoint(next));
            current = next;
            guard++;
            if (guard > count + 1) {
                break;
            }
        }
        return points;
    }

    private Vector3f samplePlaybackCursorPoint(CinematicTrackData data) {
        int count = data.getAnchorCount();
        float wrapped = data.getPlaybackCursorAnchor() % count;
        if (wrapped < 0) wrapped += count;
        int index0 = (int) Math.floor(wrapped);
        int index1 = (index0 + 1) % count;
        float alpha = wrapped - index0;
        Vector3f p0 = data.getAnchorLocalPoint(index0);
        Vector3f p1 = data.getAnchorLocalPoint(index1);
        return p0.interpolateLocal(p1, alpha);
    }

    private void addLineStrip(String name, List<Vector3f> points, ColorRGBA color, float lineWidth, Node parent) {
        if (points.size() < 2) {
            return;
        }
        Mesh mesh = new Mesh();
        mesh.setMode(Mesh.Mode.LineStrip);
        FloatBuffer pb = BufferUtils.createFloatBuffer(points.size() * 3);
        for (Vector3f p : points) {
            pb.put(p.x).put(p.y).put(p.z);
        }
        pb.flip();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, pb);
        mesh.updateBound();

        Geometry geo = new Geometry(name, mesh);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);
        mat.getAdditionalRenderState().setLineWidth(lineWidth);
        mat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        geo.setMaterial(mat);
        geo.setQueueBucket(RenderQueue.Bucket.Transparent);
        parent.attachChild(geo);
    }

    private void addAnchorSphere(Vector3f pos, int index, boolean selected, boolean emphasized) {
        float radius = selected ? 0.1f : emphasized ? 0.05f : 0.028f;
        Sphere sphere = new Sphere(selected ? 10 : 6, selected ? 10 : 6, radius);
        Geometry geo = new Geometry("Anchor_" + index, sphere);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", selected ? SELECTED_COLOR : ANCHOR_COLOR);
        geo.setMaterial(mat);
        geo.setLocalTranslation(pos);
        anchorNode.attachChild(geo);
    }

    private void addCursorSphere(Vector3f pos) {
        Sphere sphere = new Sphere(12, 12, 0.11f);
        Geometry geo = new Geometry("PlaybackCursor", sphere);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", PLAYBACK_COLOR);
        geo.setMaterial(mat);
        geo.setLocalTranslation(pos);
        rangeNode.attachChild(geo);
    }
}
