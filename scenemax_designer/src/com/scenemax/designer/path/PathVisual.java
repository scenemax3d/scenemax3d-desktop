package com.scenemax.designer.path;

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
import java.nio.ShortBuffer;
import java.util.List;

/**
 * Renders a BezierPath in the 3D viewport:
 * - Orange polyline for the curve
 * - White spheres at each control point
 * - Cyan lines + small spheres for tangent handles (shown when selected)
 * - Small yellow arrows along the curve to indicate direction
 */
public class PathVisual extends Node {

    private static final ColorRGBA CURVE_COLOR = new ColorRGBA(1.0f, 0.6f, 0.0f, 1.0f);
    private static final ColorRGBA CONTROL_POINT_COLOR = ColorRGBA.White;
    private static final ColorRGBA TANGENT_LINE_COLOR = new ColorRGBA(0.3f, 0.85f, 1.0f, 1.0f);
    private static final ColorRGBA TANGENT_HANDLE_COLOR = new ColorRGBA(0.3f, 0.85f, 1.0f, 1.0f);
    private static final ColorRGBA DIRECTION_COLOR = new ColorRGBA(1.0f, 0.9f, 0.2f, 1.0f);

    private static final float CONTROL_POINT_RADIUS = 0.12f;
    private static final float TANGENT_HANDLE_RADIUS = 0.07f;

    private final AssetManager assetManager;
    private boolean showHandles = false;

    private Node curveNode;
    private Node controlPointsNode;
    private Node tangentHandlesNode;
    private Node directionNode;

    public PathVisual(AssetManager assetManager) {
        super("PathVisual");
        this.assetManager = assetManager;

        curveNode = new Node("PathCurve");
        controlPointsNode = new Node("PathControlPoints");
        tangentHandlesNode = new Node("PathTangentHandles");
        directionNode = new Node("PathDirection");

        attachChild(curveNode);
        attachChild(controlPointsNode);
        attachChild(tangentHandlesNode);
        attachChild(directionNode);

        tangentHandlesNode.setCullHint(CullHint.Always);
    }

    public void setShowHandles(boolean show) {
        this.showHandles = show;
        tangentHandlesNode.setCullHint(show ? CullHint.Never : CullHint.Always);
    }

    public boolean isShowingHandles() {
        return showHandles;
    }

    /**
     * Rebuilds the entire visual from the given path data.
     */
    public void rebuild(BezierPath path) {
        curveNode.detachAllChildren();
        controlPointsNode.detachAllChildren();
        tangentHandlesNode.detachAllChildren();
        directionNode.detachAllChildren();

        if (path == null || path.getPointCount() < 2) {
            // If only 1 point, show it as a control point
            if (path != null && path.getPointCount() == 1) {
                addControlPointSphere(path.getPoint(0).getPosition(), 0);
            }
            return;
        }

        // 1. Build curve polyline
        buildCurvePolyline(path);

        // 2. Build control point markers
        for (int i = 0; i < path.getPointCount(); i++) {
            addControlPointSphere(path.getPoint(i).getPosition(), i);
        }

        // 3. Build tangent handles
        for (int i = 0; i < path.getPointCount(); i++) {
            BezierControlPoint cp = path.getPoint(i);
            addTangentHandle(cp.getPosition(), cp.getTangentInWorld(), i, "In");
            addTangentHandle(cp.getPosition(), cp.getTangentOutWorld(), i, "Out");
        }

        // 4. Build direction indicators
        buildDirectionArrows(path);
    }

    private void buildCurvePolyline(BezierPath path) {
        List<Vector3f> points = path.evaluateAllPoints();
        if (points.size() < 2) return;

        Mesh lineMesh = new Mesh();
        lineMesh.setMode(Mesh.Mode.LineStrip);

        FloatBuffer pb = BufferUtils.createFloatBuffer(points.size() * 3);
        for (Vector3f p : points) {
            pb.put(p.x).put(p.y).put(p.z);
        }
        pb.flip();
        lineMesh.setBuffer(VertexBuffer.Type.Position, 3, pb);
        lineMesh.updateBound();

        Geometry geo = new Geometry("CurveLine", lineMesh);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", CURVE_COLOR);
        mat.getAdditionalRenderState().setLineWidth(2.5f);
        mat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        geo.setMaterial(mat);
        geo.setQueueBucket(RenderQueue.Bucket.Transparent);

        curveNode.attachChild(geo);
    }

    private void addControlPointSphere(Vector3f pos, int index) {
        Sphere sphere = new Sphere(8, 8, CONTROL_POINT_RADIUS);
        Geometry geo = new Geometry("CP_" + index, sphere);

        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", CONTROL_POINT_COLOR);
        geo.setMaterial(mat);
        geo.setLocalTranslation(pos);

        controlPointsNode.attachChild(geo);
    }

    private void addTangentHandle(Vector3f cpPos, Vector3f handlePos, int index, String inOut) {
        // Line from control point to handle
        Mesh lineMesh = new Mesh();
        lineMesh.setMode(Mesh.Mode.Lines);

        FloatBuffer pb = BufferUtils.createFloatBuffer(6);
        pb.put(cpPos.x).put(cpPos.y).put(cpPos.z);
        pb.put(handlePos.x).put(handlePos.y).put(handlePos.z);
        pb.flip();
        lineMesh.setBuffer(VertexBuffer.Type.Position, 3, pb);

        ShortBuffer ib = BufferUtils.createShortBuffer(2);
        ib.put((short) 0).put((short) 1);
        ib.flip();
        lineMesh.setBuffer(VertexBuffer.Type.Index, 1, ib);
        lineMesh.updateBound();

        Geometry lineGeo = new Geometry("TangentLine_" + index + "_" + inOut, lineMesh);
        Material lineMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        lineMat.setColor("Color", TANGENT_LINE_COLOR);
        lineMat.getAdditionalRenderState().setLineWidth(1.5f);
        lineGeo.setMaterial(lineMat);
        tangentHandlesNode.attachChild(lineGeo);

        // Small sphere at handle endpoint
        Sphere sphere = new Sphere(6, 6, TANGENT_HANDLE_RADIUS);
        Geometry handleGeo = new Geometry("TangentHandle_" + index + "_" + inOut, sphere);
        Material handleMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        handleMat.setColor("Color", TANGENT_HANDLE_COLOR);
        handleGeo.setMaterial(handleMat);
        handleGeo.setLocalTranslation(handlePos);
        tangentHandlesNode.attachChild(handleGeo);
    }

    private void buildDirectionArrows(BezierPath path) {
        int segCount = path.getSegmentCount();
        if (segCount == 0) return;

        // Place direction indicators at ~25%, 50%, 75% of each segment
        for (int seg = 0; seg < segCount; seg++) {
            for (float t : new float[]{0.5f}) {
                Vector3f pos = path.evaluate(seg, t);
                Vector3f tangent = path.evaluateTangent(seg, t);
                if (tangent.lengthSquared() < 1e-6f) continue;
                tangent.normalizeLocal();

                // Create a small triangle pointing in the tangent direction
                addDirectionTriangle(pos, tangent, seg, t);
            }
        }
    }

    private void addDirectionTriangle(Vector3f pos, Vector3f forward, int seg, float t) {
        // Build a small triangle arrow (0.15 units size)
        float size = 0.12f;
        Vector3f up = Vector3f.UNIT_Y.clone();
        if (Math.abs(forward.dot(up)) > 0.95f) {
            up = Vector3f.UNIT_Z.clone();
        }
        Vector3f right = forward.cross(up).normalizeLocal().multLocal(size * 0.5f);

        Vector3f tip = pos.add(forward.mult(size));
        Vector3f left = pos.subtract(right);
        Vector3f rightPt = pos.add(right);

        Mesh triMesh = new Mesh();
        triMesh.setMode(Mesh.Mode.Triangles);

        FloatBuffer pb = BufferUtils.createFloatBuffer(9);
        pb.put(tip.x).put(tip.y).put(tip.z);
        pb.put(left.x).put(left.y).put(left.z);
        pb.put(rightPt.x).put(rightPt.y).put(rightPt.z);
        pb.flip();
        triMesh.setBuffer(VertexBuffer.Type.Position, 3, pb);

        ShortBuffer ib = BufferUtils.createShortBuffer(6);
        ib.put((short) 0).put((short) 1).put((short) 2);
        // Back face too so it's visible from both sides
        ib.put((short) 0).put((short) 2).put((short) 1);
        ib.flip();
        triMesh.setBuffer(VertexBuffer.Type.Index, 1, ib);
        triMesh.updateBound();

        Geometry geo = new Geometry("DirArrow_" + seg + "_" + t, triMesh);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", DIRECTION_COLOR);
        mat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        geo.setMaterial(mat);

        directionNode.attachChild(geo);
    }
}
