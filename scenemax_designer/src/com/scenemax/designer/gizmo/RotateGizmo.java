package com.scenemax.designer.gizmo;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Rotation gizmo with three colored rings (X=Red, Y=Green, Z=Blue).
 * Each ring is a circle lying in the plane perpendicular to its axis.
 * Picking is handled via screen-space distance in GizmoManager.
 */
public class RotateGizmo extends Node {

    public static final float RING_RADIUS = 1.5f;
    private static final int RING_SEGMENTS = 48;

    public RotateGizmo(AssetManager assetManager) {
        super("RotateGizmo");

        // X rotation ring: circle in YZ plane (rotate around X axis)
        attachChild(createRing(assetManager, "X", ColorRGBA.Red, 0));
        // Y rotation ring: circle in XZ plane (rotate around Y axis)
        attachChild(createRing(assetManager, "Y", ColorRGBA.Green, 1));
        // Z rotation ring: circle in XY plane (rotate around Z axis)
        attachChild(createRing(assetManager, "Z", ColorRGBA.Blue, 2));
    }

    /**
     * Creates a ring (line-loop circle) for the given axis.
     * @param axisIndex 0=X (circle in YZ), 1=Y (circle in XZ), 2=Z (circle in XY)
     */
    private Geometry createRing(AssetManager assetManager, String axis, ColorRGBA color, int axisIndex) {
        Mesh mesh = new Mesh();
        mesh.setMode(Mesh.Mode.LineStrip);

        int vertexCount = RING_SEGMENTS + 1; // +1 to close the loop
        FloatBuffer verts = BufferUtils.createFloatBuffer(vertexCount * 3);
        ShortBuffer indices = BufferUtils.createShortBuffer(vertexCount);

        for (int i = 0; i <= RING_SEGMENTS; i++) {
            float angle = (float) i / RING_SEGMENTS * FastMath.TWO_PI;
            float cos = FastMath.cos(angle) * RING_RADIUS;
            float sin = FastMath.sin(angle) * RING_RADIUS;

            switch (axisIndex) {
                case 0: // X axis: circle in YZ plane
                    verts.put(0).put(cos).put(sin);
                    break;
                case 1: // Y axis: circle in XZ plane
                    verts.put(cos).put(0).put(sin);
                    break;
                case 2: // Z axis: circle in XY plane
                    verts.put(cos).put(sin).put(0);
                    break;
            }
            indices.put((short) i);
        }

        verts.flip();
        indices.flip();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, verts);
        mesh.setBuffer(VertexBuffer.Type.Index, 1, indices);
        mesh.updateBound();

        Geometry geo = new Geometry("GizmoRing_" + axis, mesh);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);
        mat.getAdditionalRenderState().setLineWidth(2f);
        geo.setMaterial(mat);

        return geo;
    }

    /**
     * Samples points on the ring for the given axis in local space.
     * Used by GizmoManager for screen-space distance picking.
     * @param axis "X", "Y", or "Z"
     * @param sampleCount number of points to sample
     * @return array of world-space points on the ring
     */
    public Vector3f[] getRingPoints(String axis, int sampleCount) {
        int axisIndex;
        switch (axis) {
            case "X": axisIndex = 0; break;
            case "Y": axisIndex = 1; break;
            case "Z": axisIndex = 2; break;
            default: return new Vector3f[0];
        }

        Vector3f[] points = new Vector3f[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            float angle = (float) i / sampleCount * FastMath.TWO_PI;
            float cos = FastMath.cos(angle) * RING_RADIUS;
            float sin = FastMath.sin(angle) * RING_RADIUS;

            Vector3f local;
            switch (axisIndex) {
                case 0: local = new Vector3f(0, cos, sin); break;
                case 1: local = new Vector3f(cos, 0, sin); break;
                case 2: local = new Vector3f(cos, sin, 0); break;
                default: local = new Vector3f(); break;
            }
            points[i] = localToWorld(local, null);
        }
        return points;
    }
}
