package com.scenemax.designer.grid;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;

import java.nio.FloatBuffer;

/**
 * An XZ-plane grid for spatial reference in the designer viewport.
 */
public class GridPlane extends Node {

    private static final int GRID_EXTENT = 50;
    private static final float GRID_SPACING = 1.0f;

    public GridPlane(AssetManager assetManager) {
        super("GridPlane");
        createGrid(assetManager);
        createAxes(assetManager);
    }

    private void createGrid(AssetManager assetManager) {
        int lines = (GRID_EXTENT * 2) / (int) GRID_SPACING + 1;
        int vertexCount = lines * 2 * 2; // 2 axes, 2 vertices per line

        FloatBuffer positions = BufferUtils.createFloatBuffer(vertexCount * 3);

        float min = -GRID_EXTENT;
        float max = GRID_EXTENT;

        // Lines along Z axis (varying X)
        for (float x = min; x <= max; x += GRID_SPACING) {
            positions.put(x).put(0).put(min);
            positions.put(x).put(0).put(max);
        }

        // Lines along X axis (varying Z)
        for (float z = min; z <= max; z += GRID_SPACING) {
            positions.put(min).put(0).put(z);
            positions.put(max).put(0).put(z);
        }

        positions.flip();

        Mesh gridMesh = new Mesh();
        gridMesh.setMode(Mesh.Mode.Lines);
        gridMesh.setBuffer(VertexBuffer.Type.Position, 3, positions);
        gridMesh.updateBound();

        Geometry gridGeo = new Geometry("Grid", gridMesh);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", new ColorRGBA(0.3f, 0.3f, 0.3f, 1f));
        gridGeo.setMaterial(mat);

        attachChild(gridGeo);
    }

    private void createAxes(AssetManager assetManager) {
        // X axis (red)
        createAxisLine(assetManager, new Vector3f(-GRID_EXTENT, 0.01f, 0),
                new Vector3f(GRID_EXTENT, 0.01f, 0), ColorRGBA.Red);
        // Z axis (blue)
        createAxisLine(assetManager, new Vector3f(0, 0.01f, -GRID_EXTENT),
                new Vector3f(0, 0.01f, GRID_EXTENT), ColorRGBA.Blue);
    }

    private void createAxisLine(AssetManager assetManager, Vector3f start, Vector3f end, ColorRGBA color) {
        FloatBuffer positions = BufferUtils.createFloatBuffer(6);
        positions.put(start.x).put(start.y).put(start.z);
        positions.put(end.x).put(end.y).put(end.z);
        positions.flip();

        Mesh mesh = new Mesh();
        mesh.setMode(Mesh.Mode.Lines);
        mesh.setBuffer(VertexBuffer.Type.Position, 3, positions);
        mesh.updateBound();

        Geometry geo = new Geometry("Axis", mesh);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);
        mat.getAdditionalRenderState().setLineWidth(2f);
        geo.setMaterial(mat);

        attachChild(geo);
    }
}
