package com.scenemax.designer.path;

import org.json.JSONArray;

/**
 * A single sampled point along a Bezier path, containing position and
 * rotation (Euler angles in degrees).  This is the output format for
 * runtime path-following.
 */
public class PathSample {

    public final float x, y, z;
    public final float rx, ry, rz;

    public PathSample(float x, float y, float z, float rx, float ry, float rz) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.rx = rx;
        this.ry = ry;
        this.rz = rz;
    }

    /** Returns a flat array [x, y, z, rx, ry, rz]. */
    public float[] toArray() {
        return new float[]{x, y, z, rx, ry, rz};
    }

    public JSONArray toJSON() {
        JSONArray arr = new JSONArray();
        arr.put(round(x));
        arr.put(round(y));
        arr.put(round(z));
        arr.put(round(rx));
        arr.put(round(ry));
        arr.put(round(rz));
        return arr;
    }

    private static double round(float v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
