package com.scenemax.designer.gizmo;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.util.BufferUtils;

import java.nio.FloatBuffer;

/**
 * Visual representation of a camera in the designer scene.
 * Draws a wireframe camera body + frustum cone, similar to Unity's camera gizmo.
 * The frustum extends along the local +Z axis (visual forward direction).
 * Includes an invisible collision proxy box for ray-cast picking.
 */
public class CameraGizmoVisual extends Node {

    // Camera body dimensions (local space)
    private static final float BODY_HW = 0.2f;   // half-width
    private static final float BODY_HH = 0.15f;  // half-height
    private static final float BODY_HD = 0.12f;   // half-depth

    // Frustum cone dimensions (extends along +Z)
    private static final float FRUSTUM_LENGTH = 1.2f;
    private static final float FRUSTUM_HW = 0.65f;  // half-width at far end
    private static final float FRUSTUM_HH = 0.4f;   // half-height at far end

    // Up-indicator triangle
    private static final float UP_BASE = 0.12f;
    private static final float UP_HEIGHT = 0.1f;

    private static final ColorRGBA GIZMO_COLOR = new ColorRGBA(0.2f, 0.8f, 1.0f, 1f);

    public CameraGizmoVisual(AssetManager assetManager) {
        super("CameraGizmoVisual");

        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", GIZMO_COLOR);
        mat.getAdditionalRenderState().setLineWidth(2f);

        attachChild(createCameraBody(mat));
        attachChild(createFrustumCone(mat));
        attachChild(createUpIndicator(mat));
        attachChild(createCollisionProxy(assetManager));
    }

    /**
     * Creates the wireframe camera body (a rectangular box outline).
     */
    private Geometry createCameraBody(Material mat) {
        // 12 edges of a box = 24 vertices (pairs for Lines mode)
        float x = BODY_HW, y = BODY_HH, z = BODY_HD;
        // 8 corners of the box
        Vector3f[] c = {
            new Vector3f(-x, -y, -z), // 0: left-bottom-back (toward frustum)
            new Vector3f( x, -y, -z), // 1: right-bottom-back
            new Vector3f( x,  y, -z), // 2: right-top-back
            new Vector3f(-x,  y, -z), // 3: left-top-back
            new Vector3f(-x, -y,  z), // 4: left-bottom-front (behind camera)
            new Vector3f( x, -y,  z), // 5: right-bottom-front
            new Vector3f( x,  y,  z), // 6: right-top-front
            new Vector3f(-x,  y,  z), // 7: left-top-front
        };

        // 12 edges as pairs of vertices
        int[][] edges = {
            {0,1},{1,2},{2,3},{3,0}, // back face (toward -Z / frustum)
            {4,5},{5,6},{6,7},{7,4}, // front face (toward +Z / behind)
            {0,4},{1,5},{2,6},{3,7}  // connecting edges
        };

        FloatBuffer vb = BufferUtils.createFloatBuffer(edges.length * 2 * 3);
        for (int[] edge : edges) {
            Vector3f a = c[edge[0]], b = c[edge[1]];
            vb.put(a.x).put(a.y).put(a.z);
            vb.put(b.x).put(b.y).put(b.z);
        }
        vb.flip();

        Mesh mesh = new Mesh();
        mesh.setMode(Mesh.Mode.Lines);
        mesh.setBuffer(com.jme3.scene.VertexBuffer.Type.Position, 3, vb);
        mesh.updateBound();

        Geometry geo = new Geometry("CamBody", mesh);
        geo.setMaterial(mat);
        return geo;
    }

    /**
     * Creates the frustum cone extending along +Z (visual forward direction).
     * The JME camera looks along -Z, so a Y_FLIP is applied in the preview
     * and code generation to align the camera with this visual direction.
     * 4 lines from body face corners to far plane corners + far rectangle.
     */
    private Geometry createFrustumCone(Material mat) {
        // Near plane corners (body face at z = +BODY_HD, the +Z side)
        float nz = BODY_HD;
        Vector3f n0 = new Vector3f(-BODY_HW, -BODY_HH, nz);
        Vector3f n1 = new Vector3f( BODY_HW, -BODY_HH, nz);
        Vector3f n2 = new Vector3f( BODY_HW,  BODY_HH, nz);
        Vector3f n3 = new Vector3f(-BODY_HW,  BODY_HH, nz);

        // Far plane corners (further along +Z)
        float fz = BODY_HD + FRUSTUM_LENGTH;
        Vector3f f0 = new Vector3f(-FRUSTUM_HW, -FRUSTUM_HH, fz);
        Vector3f f1 = new Vector3f( FRUSTUM_HW, -FRUSTUM_HH, fz);
        Vector3f f2 = new Vector3f( FRUSTUM_HW,  FRUSTUM_HH, fz);
        Vector3f f3 = new Vector3f(-FRUSTUM_HW,  FRUSTUM_HH, fz);

        // 8 edges: 4 from near to far + 4 for far rectangle
        Vector3f[][] edges = {
            {n0, f0}, {n1, f1}, {n2, f2}, {n3, f3}, // near-to-far
            {f0, f1}, {f1, f2}, {f2, f3}, {f3, f0}   // far rectangle
        };

        FloatBuffer vb = BufferUtils.createFloatBuffer(edges.length * 2 * 3);
        for (Vector3f[] edge : edges) {
            vb.put(edge[0].x).put(edge[0].y).put(edge[0].z);
            vb.put(edge[1].x).put(edge[1].y).put(edge[1].z);
        }
        vb.flip();

        Mesh mesh = new Mesh();
        mesh.setMode(Mesh.Mode.Lines);
        mesh.setBuffer(com.jme3.scene.VertexBuffer.Type.Position, 3, vb);
        mesh.updateBound();

        Geometry geo = new Geometry("CamFrustum", mesh);
        geo.setMaterial(mat);
        return geo;
    }

    /**
     * Creates a small triangle on top of the camera body to indicate "up" direction.
     */
    private Geometry createUpIndicator(Material mat) {
        float baseY = BODY_HH;
        float tipY = BODY_HH + UP_HEIGHT;

        Vector3f[] verts = {
            new Vector3f(-UP_BASE, baseY, 0),
            new Vector3f( UP_BASE, baseY, 0),
            new Vector3f( UP_BASE, baseY, 0),
            new Vector3f(0, tipY, 0),
            new Vector3f(0, tipY, 0),
            new Vector3f(-UP_BASE, baseY, 0),
        };

        FloatBuffer vb = BufferUtils.createFloatBuffer(verts.length * 3);
        for (Vector3f v : verts) {
            vb.put(v.x).put(v.y).put(v.z);
        }
        vb.flip();

        Mesh mesh = new Mesh();
        mesh.setMode(Mesh.Mode.Lines);
        mesh.setBuffer(com.jme3.scene.VertexBuffer.Type.Position, 3, vb);
        mesh.updateBound();

        Geometry geo = new Geometry("CamUp", mesh);
        geo.setMaterial(mat);
        return geo;
    }

    /**
     * Creates an invisible solid box for ray-cast collision picking.
     * The box covers the camera body + frustum area so the user can click on it.
     */
    private Geometry createCollisionProxy(AssetManager assetManager) {
        Box box = new Box(BODY_HW * 1.5f, BODY_HH * 1.5f, (BODY_HD + FRUSTUM_LENGTH / 2f));
        Geometry geo = new Geometry("CamCollisionProxy", box);
        geo.setLocalTranslation(0, 0, FRUSTUM_LENGTH / 2f); // centered on the +Z frustum
        geo.setCullHint(Spatial.CullHint.Always); // invisible
        // Needs a material for the geometry to be valid, even if invisible
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", ColorRGBA.BlackNoAlpha);
        geo.setMaterial(mat);
        return geo;
    }
}
