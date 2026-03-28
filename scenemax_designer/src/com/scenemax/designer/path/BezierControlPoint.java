package com.scenemax.designer.path;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A single control point on a cubic Bezier path.
 * Each point has a position and two tangent handles (in/out) expressed
 * as local offsets from the position.  When tangentBroken is false the
 * handles are mirrored (smooth joint); when true they move independently.
 * An optional rotationOverride lets the user set a manual orientation;
 * when null the rotation is auto-calculated from the path tangent.
 */
public class BezierControlPoint {

    private Vector3f position;
    private Vector3f tangentIn;   // local offset from position (incoming handle)
    private Vector3f tangentOut;  // local offset from position (outgoing handle)
    private boolean tangentBroken;
    private float[] rotationOverride; // euler degrees (rx, ry, rz) or null for auto

    public BezierControlPoint(Vector3f position) {
        this.position = position.clone();
        this.tangentIn = new Vector3f(-1, 0, 0);
        this.tangentOut = new Vector3f(1, 0, 0);
        this.tangentBroken = false;
        this.rotationOverride = null;
    }

    public BezierControlPoint(Vector3f position, Vector3f tangentIn, Vector3f tangentOut) {
        this.position = position.clone();
        this.tangentIn = tangentIn.clone();
        this.tangentOut = tangentOut.clone();
        this.tangentBroken = false;
        this.rotationOverride = null;
    }

    // --- Getters / Setters ---

    public Vector3f getPosition() { return position; }
    public void setPosition(Vector3f position) { this.position.set(position); }

    public Vector3f getTangentIn() { return tangentIn; }
    public void setTangentIn(Vector3f tangentIn) { this.tangentIn.set(tangentIn); }

    public Vector3f getTangentOut() { return tangentOut; }
    public void setTangentOut(Vector3f tangentOut) { this.tangentOut.set(tangentOut); }

    public boolean isTangentBroken() { return tangentBroken; }
    public void setTangentBroken(boolean tangentBroken) { this.tangentBroken = tangentBroken; }

    public float[] getRotationOverride() { return rotationOverride; }
    public void setRotationOverride(float[] rotationOverride) { this.rotationOverride = rotationOverride; }
    public boolean hasRotationOverride() { return rotationOverride != null; }

    /** World position of the tangent-in handle endpoint. */
    public Vector3f getTangentInWorld() { return position.add(tangentIn); }

    /** World position of the tangent-out handle endpoint. */
    public Vector3f getTangentOutWorld() { return position.add(tangentOut); }

    /**
     * Sets the tangent-out handle and mirrors tangent-in if not broken.
     */
    public void setTangentOutMirrored(Vector3f out) {
        this.tangentOut.set(out);
        if (!tangentBroken) {
            this.tangentIn.set(out.negate());
        }
    }

    /**
     * Sets the tangent-in handle and mirrors tangent-out if not broken.
     */
    public void setTangentInMirrored(Vector3f in) {
        this.tangentIn.set(in);
        if (!tangentBroken) {
            this.tangentOut.set(in.negate());
        }
    }

    // --- Serialization ---

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("position", vec3ToArray(position));
        json.put("tangentIn", vec3ToArray(tangentIn));
        json.put("tangentOut", vec3ToArray(tangentOut));
        json.put("tangentBroken", tangentBroken);
        if (rotationOverride != null) {
            json.put("rotationOverride", new float[]{rotationOverride[0], rotationOverride[1], rotationOverride[2]});
        }
        return json;
    }

    public static BezierControlPoint fromJSON(JSONObject json) {
        Vector3f pos = arrayToVec3(json.getJSONArray("position"));
        Vector3f tIn = arrayToVec3(json.getJSONArray("tangentIn"));
        Vector3f tOut = arrayToVec3(json.getJSONArray("tangentOut"));

        BezierControlPoint cp = new BezierControlPoint(pos, tIn, tOut);
        cp.tangentBroken = json.optBoolean("tangentBroken", false);

        if (json.has("rotationOverride") && !json.isNull("rotationOverride")) {
            JSONArray rot = json.getJSONArray("rotationOverride");
            cp.rotationOverride = new float[]{
                (float) rot.getDouble(0), (float) rot.getDouble(1), (float) rot.getDouble(2)
            };
        }
        return cp;
    }

    private static float[] vec3ToArray(Vector3f v) {
        return new float[]{v.x, v.y, v.z};
    }

    private static Vector3f arrayToVec3(JSONArray arr) {
        return new Vector3f((float) arr.getDouble(0), (float) arr.getDouble(1), (float) arr.getDouble(2));
    }
}
