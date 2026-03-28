package com.scenemax.designer.path;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Plane;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Sphere;
import com.jme3.util.BufferUtils;

import java.nio.FloatBuffer;

/**
 * State machine for drawing a new Bezier path.
 * <p>
 * UX flow (Unity-style):
 * 1. User clicks toolbar "Path" button -> enters drawing mode
 * 2. Click on viewport places control points on the XZ ground plane (Y=0)
 * 3. Double-click or Enter finalizes the path (minimum 2 points)
 * 4. ESC cancels drawing
 * <p>
 * While drawing, a rubber-band preview line is shown from the last placed
 * point to the current cursor position.
 */
public class PathDrawingMode {

    public interface PathDrawingCallback {
        void onPathFinished(BezierPath path);
        void onPathCancelled();
    }

    private boolean active = false;
    private BezierPath currentPath;
    private PathVisual previewVisual;
    private final Node rootNode;
    private final AssetManager assetManager;
    private PathDrawingCallback callback;

    // Rubber-band preview
    private Geometry rubberBandLine;
    private Geometry cursorDot;
    private Vector3f lastCursorWorldPos = new Vector3f();

    // Double-click detection
    private long lastClickTime = 0;
    private Vector2f lastClickPos = new Vector2f();
    private static final long DOUBLE_CLICK_MS = 350;
    private static final float DOUBLE_CLICK_PX = 10f;

    public PathDrawingMode(Node rootNode, AssetManager assetManager) {
        this.rootNode = rootNode;
        this.assetManager = assetManager;
    }

    public void setCallback(PathDrawingCallback callback) {
        this.callback = callback;
    }

    public boolean isActive() { return active; }

    /**
     * Enters path drawing mode. Creates an empty path and preview visual.
     */
    public void enter() {
        active = true;
        currentPath = new BezierPath();
        previewVisual = new PathVisual(assetManager);
        rootNode.attachChild(previewVisual);

        // Create rubber-band line geometry
        rubberBandLine = createRubberBandLine();
        rubberBandLine.setCullHint(Node.CullHint.Always);
        rootNode.attachChild(rubberBandLine);

        // Create cursor dot
        cursorDot = createCursorDot();
        cursorDot.setCullHint(Node.CullHint.Always);
        rootNode.attachChild(cursorDot);

        lastClickTime = 0;
    }

    /**
     * Exits path drawing mode without creating an entity.
     */
    public void cancel() {
        cleanup();
        active = false;
        if (callback != null) callback.onPathCancelled();
    }

    /**
     * Finalizes the drawn path if it has >= 2 points.
     */
    public void finish() {
        if (currentPath == null || currentPath.getPointCount() < 2) {
            cancel();
            return;
        }

        // Auto-smooth all tangents for a nice initial curve
        currentPath.autoSmoothAll();

        BezierPath finishedPath = currentPath;
        cleanup();
        active = false;

        if (callback != null) {
            callback.onPathFinished(finishedPath);
        }
    }

    /**
     * Handles a left click during path drawing.
     * Returns true if the click was consumed.
     */
    public boolean onLeftClick(Camera cam, Vector2f screenPos) {
        if (!active) return false;

        long now = System.currentTimeMillis();

        // Double-click detection
        if (now - lastClickTime < DOUBLE_CLICK_MS
                && lastClickPos.distance(screenPos) < DOUBLE_CLICK_PX
                && currentPath.getPointCount() >= 2) {
            finish();
            return true;
        }
        lastClickTime = now;
        lastClickPos.set(screenPos);

        // Ray-cast to ground plane (XZ at Y=0)
        Vector3f hitPos = raycastGroundPlane(cam, screenPos);
        if (hitPos == null) return true; // consumed but no valid hit

        // Add control point
        BezierControlPoint cp = new BezierControlPoint(hitPos);
        currentPath.addPoint(cp);

        // Auto-smooth tangents as points are added
        if (currentPath.getPointCount() >= 2) {
            currentPath.autoSmoothAll();
        }

        // Update preview
        previewVisual.setShowHandles(true);
        previewVisual.rebuild(currentPath);

        // Show rubber-band line
        rubberBandLine.setCullHint(Node.CullHint.Never);

        return true;
    }

    /**
     * Updates the rubber-band preview line on mouse move.
     */
    public void onMouseMove(Camera cam, Vector2f screenPos) {
        if (!active) return;

        Vector3f hitPos = raycastGroundPlane(cam, screenPos);
        if (hitPos == null) return;
        lastCursorWorldPos.set(hitPos);

        // Update cursor dot position
        cursorDot.setCullHint(Node.CullHint.Never);
        cursorDot.setLocalTranslation(hitPos);

        // Update rubber-band line from last control point to cursor
        if (currentPath.getPointCount() > 0) {
            Vector3f lastPt = currentPath.getPoint(currentPath.getPointCount() - 1).getPosition();
            updateRubberBandLine(lastPt, hitPos);
            rubberBandLine.setCullHint(Node.CullHint.Never);
        }
    }

    // --- Internals ---

    private Vector3f raycastGroundPlane(Camera cam, Vector2f screenPos) {
        Vector3f near = cam.getWorldCoordinates(screenPos, 0f);
        Vector3f far = cam.getWorldCoordinates(screenPos, 1f);
        Vector3f dir = far.subtract(near).normalizeLocal();

        // Intersect with Y=0 plane
        float denom = Vector3f.UNIT_Y.dot(dir);
        if (Math.abs(denom) < 1e-6f) return null; // parallel to ground

        float t = -(Vector3f.UNIT_Y.dot(near)) / denom;
        if (t < 0) return null; // behind camera

        return near.add(dir.mult(t));
    }

    private Geometry createRubberBandLine() {
        Mesh mesh = new Mesh();
        mesh.setMode(Mesh.Mode.Lines);

        FloatBuffer pb = BufferUtils.createFloatBuffer(6);
        pb.put(0).put(0).put(0).put(0).put(0).put(0);
        pb.flip();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, pb);
        mesh.updateBound();

        Geometry geo = new Geometry("RubberBand", mesh);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", new ColorRGBA(1f, 0.6f, 0f, 0.5f));
        mat.getAdditionalRenderState().setLineWidth(1.5f);
        geo.setMaterial(mat);
        return geo;
    }

    private void updateRubberBandLine(Vector3f from, Vector3f to) {
        Mesh mesh = rubberBandLine.getMesh();
        FloatBuffer pb = (FloatBuffer) mesh.getBuffer(VertexBuffer.Type.Position).getData();
        pb.clear();
        pb.put(from.x).put(from.y).put(from.z);
        pb.put(to.x).put(to.y).put(to.z);
        pb.flip();
        mesh.getBuffer(VertexBuffer.Type.Position).updateData(pb);
        mesh.updateBound();
    }

    private Geometry createCursorDot() {
        Sphere sphere = new Sphere(8, 8, 0.1f);
        Geometry geo = new Geometry("CursorDot", sphere);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", new ColorRGBA(1f, 0.8f, 0.2f, 0.8f));
        geo.setMaterial(mat);
        return geo;
    }

    private void cleanup() {
        if (previewVisual != null) {
            previewVisual.removeFromParent();
            previewVisual = null;
        }
        if (rubberBandLine != null) {
            rubberBandLine.removeFromParent();
            rubberBandLine = null;
        }
        if (cursorDot != null) {
            cursorDot.removeFromParent();
            cursorDot = null;
        }
        currentPath = null;
    }
}
