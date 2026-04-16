package com.scenemaxeng.projector;

import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;

public class WedgeMesh extends Mesh {

    public WedgeMesh(float width, float height, float depth) {
        float hx = Math.max(0.01f, width) * 0.5f;
        float hy = Math.max(0.01f, height) * 0.5f;
        float hz = Math.max(0.01f, depth) * 0.5f;

        Vector3f blf = new Vector3f(-hx, -hy, hz);
        Vector3f brf = new Vector3f(hx, -hy, hz);
        Vector3f trf = new Vector3f(hx, hy, hz);
        Vector3f blb = new Vector3f(-hx, -hy, -hz);
        Vector3f brb = new Vector3f(hx, -hy, -hz);
        Vector3f trb = new Vector3f(hx, hy, -hz);

        Vector3f slopeNormal = new Vector3f(-height, width, 0).normalizeLocal();
        Vector3f down = Vector3f.UNIT_Y.negate();
        Vector3f back = Vector3f.UNIT_Z.negate();

        Vector3f[] positions = new Vector3f[]{
                blf, brf, brb,
                blf, brb, blb,
                blf, trf, brf,
                blb, brb, trb,
                brf, trf, trb,
                brf, trb, brb,
                trf, trb, brb,
                trf, brb, brf
        };

        Vector3f[] normals = new Vector3f[]{
                down, down, down,
                down, down, down,
                Vector3f.UNIT_Z, Vector3f.UNIT_Z, Vector3f.UNIT_Z,
                back, back, back,
                Vector3f.UNIT_X, Vector3f.UNIT_X, Vector3f.UNIT_X,
                Vector3f.UNIT_X, Vector3f.UNIT_X, Vector3f.UNIT_X,
                slopeNormal, slopeNormal, slopeNormal,
                slopeNormal, slopeNormal, slopeNormal
        };

        Vector2f[] tex = new Vector2f[positions.length];
        for (int i = 0; i < tex.length; i++) {
            tex[i] = new Vector2f((i % 3 == 0) ? 0f : 1f, (i % 3 == 2) ? 1f : 0f);
        }

        int[] indices = new int[positions.length];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = i;
        }

        setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(positions));
        setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(normals));
        setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(tex));
        setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indices));
        updateBound();
        updateCounts();
    }
}
