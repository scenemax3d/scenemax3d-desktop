package com.scenemax.designer.path;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A cubic Bezier path composed of multiple control points.
 * Each consecutive pair of control points defines a cubic Bezier segment
 * using P0 = cp[i].position, P1 = cp[i].position + cp[i].tangentOut,
 * P2 = cp[i+1].position + cp[i+1].tangentIn, P3 = cp[i+1].position.
 */
public class BezierPath {

    private final List<BezierControlPoint> controlPoints = new ArrayList<>();
    private boolean closed = false;
    private int subdivisions = 20; // samples per segment

    public BezierPath() {}

    // --- Control point management ---

    public List<BezierControlPoint> getControlPoints() { return controlPoints; }
    public int getPointCount() { return controlPoints.size(); }

    public BezierControlPoint getPoint(int index) { return controlPoints.get(index); }

    public void addPoint(BezierControlPoint pt) {
        controlPoints.add(pt);
    }

    public void insertPoint(int index, BezierControlPoint pt) {
        controlPoints.add(index, pt);
    }

    public void removePoint(int index) {
        if (controlPoints.size() > 2) {
            controlPoints.remove(index);
        }
    }

    public boolean isClosed() { return closed; }
    public void setClosed(boolean closed) { this.closed = closed; }

    public int getSubdivisions() { return subdivisions; }
    public void setSubdivisions(int subdivisions) { this.subdivisions = Math.max(1, Math.min(100, subdivisions)); }

    public int getSegmentCount() {
        int n = controlPoints.size();
        if (n < 2) return 0;
        return closed ? n : n - 1;
    }

    // --- Bezier evaluation ---

    /**
     * Evaluates the cubic Bezier curve at parameter t for segment segIdx.
     */
    public Vector3f evaluate(int segIdx, float t) {
        int n = controlPoints.size();
        int i0 = segIdx;
        int i1 = (segIdx + 1) % n;

        BezierControlPoint cp0 = controlPoints.get(i0);
        BezierControlPoint cp1 = controlPoints.get(i1);

        Vector3f p0 = cp0.getPosition();
        Vector3f p1 = cp0.getTangentOutWorld();
        Vector3f p2 = cp1.getTangentInWorld();
        Vector3f p3 = cp1.getPosition();

        return cubicBezier(p0, p1, p2, p3, t);
    }

    /**
     * Evaluates the first derivative (tangent) of the cubic Bezier at parameter t.
     */
    public Vector3f evaluateTangent(int segIdx, float t) {
        int n = controlPoints.size();
        int i0 = segIdx;
        int i1 = (segIdx + 1) % n;

        BezierControlPoint cp0 = controlPoints.get(i0);
        BezierControlPoint cp1 = controlPoints.get(i1);

        Vector3f p0 = cp0.getPosition();
        Vector3f p1 = cp0.getTangentOutWorld();
        Vector3f p2 = cp1.getTangentInWorld();
        Vector3f p3 = cp1.getPosition();

        return cubicBezierDerivative(p0, p1, p2, p3, t);
    }

    /**
     * Auto-computes smooth tangent handles for a control point using
     * Catmull-Rom style interpolation from its neighbours.
     */
    public void autoSmoothTangent(int index) {
        int n = controlPoints.size();
        if (n < 2) return;

        BezierControlPoint cp = controlPoints.get(index);

        Vector3f prev, next;
        if (index == 0) {
            if (closed) {
                prev = controlPoints.get(n - 1).getPosition();
            } else {
                // First point: use direction to next point
                next = controlPoints.get(1).getPosition();
                Vector3f dir = next.subtract(cp.getPosition()).multLocal(0.3f);
                cp.setTangentOut(dir);
                cp.setTangentIn(dir.negate());
                return;
            }
        } else {
            prev = controlPoints.get(index - 1).getPosition();
        }

        if (index == n - 1) {
            if (closed) {
                next = controlPoints.get(0).getPosition();
            } else {
                // Last point: use direction from previous point
                Vector3f dir = cp.getPosition().subtract(prev).multLocal(0.3f);
                cp.setTangentOut(dir);
                cp.setTangentIn(dir.negate());
                return;
            }
        } else {
            next = controlPoints.get(index + 1).getPosition();
        }

        // Catmull-Rom: tangent = (next - prev) * 0.3
        Vector3f tangent = next.subtract(prev).multLocal(0.3f);
        cp.setTangentOut(tangent);
        cp.setTangentIn(tangent.negate());
    }

    /**
     * Auto-smooth all control points.
     */
    public void autoSmoothAll() {
        for (int i = 0; i < controlPoints.size(); i++) {
            autoSmoothTangent(i);
        }
    }

    // --- Sampling ---

    /**
     * Samples the entire path and returns a list of PathSample values
     * with position (x,y,z) and rotation (rx,ry,rz) in degrees.
     */
    public List<PathSample> sample() {
        List<PathSample> samples = new ArrayList<>();
        int segCount = getSegmentCount();
        if (segCount == 0) return samples;

        for (int seg = 0; seg < segCount; seg++) {
            int steps = subdivisions;
            // For the last segment, include the endpoint (t=1)
            int end = (seg == segCount - 1) ? steps : steps - 1;

            for (int s = 0; s <= end; s++) {
                float t = (float) s / steps;
                Vector3f pos = evaluate(seg, t);
                Vector3f tangent = evaluateTangent(seg, t);

                float rx, ry, rz;

                // Check if the control point at the start of this segment has a rotation override
                // and we're at t=0
                BezierControlPoint cp = controlPoints.get(seg);
                BezierControlPoint cpNext = controlPoints.get((seg + 1) % controlPoints.size());

                // Interpolate rotation override if present at endpoints
                if (s == 0 && cp.hasRotationOverride()) {
                    float[] rot = cp.getRotationOverride();
                    rx = rot[0]; ry = rot[1]; rz = rot[2];
                } else if (s == end && seg == segCount - 1 && cpNext.hasRotationOverride()) {
                    float[] rot = cpNext.getRotationOverride();
                    rx = rot[0]; ry = rot[1]; rz = rot[2];
                } else {
                    // Auto-calculate rotation from tangent direction
                    float[] euler = tangentToEuler(tangent);
                    rx = euler[0]; ry = euler[1]; rz = euler[2];

                    // Blend with rotation overrides at segment endpoints
                    if (cp.hasRotationOverride() || cpNext.hasRotationOverride()) {
                        float[] startRot = cp.hasRotationOverride() ? cp.getRotationOverride()
                                : tangentToEuler(evaluateTangent(seg, 0));
                        float[] endRot = cpNext.hasRotationOverride() ? cpNext.getRotationOverride()
                                : tangentToEuler(evaluateTangent(seg, 1));
                        rx = startRot[0] + (endRot[0] - startRot[0]) * t;
                        ry = startRot[1] + (endRot[1] - startRot[1]) * t;
                        rz = startRot[2] + (endRot[2] - startRot[2]) * t;
                    }
                }

                samples.add(new PathSample(pos.x, pos.y, pos.z, rx, ry, rz));
            }
        }

        return samples;
    }

    /**
     * Converts a tangent direction vector to Euler angles (degrees).
     * Uses a lookAt rotation: Z-forward along tangent, Y-up.
     */
    private float[] tangentToEuler(Vector3f tangent) {
        if (tangent.lengthSquared() < 1e-8f) {
            return new float[]{0, 0, 0};
        }

        Vector3f forward = tangent.normalize();
        Vector3f up = Vector3f.UNIT_Y.clone();

        // Handle case where tangent is nearly parallel to up
        if (Math.abs(forward.dot(up)) > 0.999f) {
            up = Vector3f.UNIT_Z.clone();
        }

        Quaternion q = new Quaternion();
        q.lookAt(forward, up);

        float[] angles = new float[3];
        q.toAngles(angles);

        return new float[]{
            (float) Math.toDegrees(angles[0]),
            (float) Math.toDegrees(angles[1]),
            (float) Math.toDegrees(angles[2])
        };
    }

    // --- Cubic Bezier math ---

    private static Vector3f cubicBezier(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float t) {
        float u = 1 - t;
        float u2 = u * u;
        float u3 = u2 * u;
        float t2 = t * t;
        float t3 = t2 * t;

        return new Vector3f(
            u3 * p0.x + 3 * u2 * t * p1.x + 3 * u * t2 * p2.x + t3 * p3.x,
            u3 * p0.y + 3 * u2 * t * p1.y + 3 * u * t2 * p2.y + t3 * p3.y,
            u3 * p0.z + 3 * u2 * t * p1.z + 3 * u * t2 * p2.z + t3 * p3.z
        );
    }

    private static Vector3f cubicBezierDerivative(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float t) {
        float u = 1 - t;
        float u2 = u * u;
        float t2 = t * t;

        return new Vector3f(
            3 * u2 * (p1.x - p0.x) + 6 * u * t * (p2.x - p1.x) + 3 * t2 * (p3.x - p2.x),
            3 * u2 * (p1.y - p0.y) + 6 * u * t * (p2.y - p1.y) + 3 * t2 * (p3.y - p2.y),
            3 * u2 * (p1.z - p0.z) + 6 * u * t * (p2.z - p1.z) + 3 * t2 * (p3.z - p2.z)
        );
    }

    /**
     * Evaluates all subdivided points on the path for visual rendering.
     * Returns a flat list of Vector3f positions.
     */
    public List<Vector3f> evaluateAllPoints() {
        List<Vector3f> points = new ArrayList<>();
        int segCount = getSegmentCount();
        if (segCount == 0) return points;

        for (int seg = 0; seg < segCount; seg++) {
            int end = (seg == segCount - 1) ? subdivisions : subdivisions - 1;
            for (int s = 0; s <= end; s++) {
                float t = (float) s / subdivisions;
                points.add(evaluate(seg, t));
            }
        }
        return points;
    }

    // --- Serialization ---

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("closed", closed);
        json.put("subdivisions", subdivisions);

        JSONArray cpArray = new JSONArray();
        for (BezierControlPoint cp : controlPoints) {
            cpArray.put(cp.toJSON());
        }
        json.put("controlPoints", cpArray);

        return json;
    }

    public static BezierPath fromJSON(JSONObject json) {
        BezierPath path = new BezierPath();
        path.closed = json.optBoolean("closed", false);
        path.subdivisions = json.optInt("subdivisions", 20);

        if (json.has("controlPoints")) {
            JSONArray cpArray = json.getJSONArray("controlPoints");
            for (int i = 0; i < cpArray.length(); i++) {
                path.controlPoints.add(BezierControlPoint.fromJSON(cpArray.getJSONObject(i)));
            }
        }

        return path;
    }

    /**
     * Exports the sampled path as a JSON array of [x,y,z,rx,ry,rz] arrays.
     */
    public JSONArray exportSamplesJSON() {
        List<PathSample> samples = sample();
        JSONArray arr = new JSONArray();
        for (PathSample s : samples) {
            arr.put(s.toJSON());
        }
        return arr;
    }
}
