package com.scenemaxeng.projector;

import com.jme3.math.FastMath;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * A hollow cylinder (tube/pipe) mesh with independent outer and inner radii
 * at top and bottom, allowing frustum and cone-like hollow shapes.
 * <p>
 * The mesh is composed of four surfaces:
 * <ul>
 *   <li>Outer wall  -- normals point outward</li>
 *   <li>Inner wall   -- normals point inward (into the hole)</li>
 *   <li>Top cap      -- annular ring between outer and inner radius at the top</li>
 *   <li>Bottom cap   -- annular ring between outer and inner radius at the bottom</li>
 * </ul>
 */
public class HollowCylinderMesh extends Mesh {

    private static final int DEFAULT_AXIS_SAMPLES = 16;
    private static final int DEFAULT_RADIAL_SAMPLES = 32;
    private static final float MIN_DIMENSION = 0.0001f;

    /**
     * Simplified constructor using default axis (16) and radial (32) samples.
     */
    public HollowCylinderMesh(float outerRadiusTop, float outerRadiusBottom,
                               float innerRadiusTop, float innerRadiusBottom,
                               float height) {
        this(DEFAULT_AXIS_SAMPLES, DEFAULT_RADIAL_SAMPLES,
             outerRadiusTop, outerRadiusBottom,
             innerRadiusTop, innerRadiusBottom,
             height);
    }

    /**
     * Full constructor.
     *
     * @param axisSamples      number of vertical segments along the height (&gt;= 2)
     * @param radialSamples    number of segments around the circumference (&gt;= 3)
     * @param outerRadiusTop   outer radius at the top of the cylinder
     * @param outerRadiusBottom outer radius at the bottom of the cylinder
     * @param innerRadiusTop   inner radius at the top (the hole)
     * @param innerRadiusBottom inner radius at the bottom (the hole)
     * @param height           height of the cylinder
     */
    public HollowCylinderMesh(int axisSamples, int radialSamples,
                               float outerRadiusTop, float outerRadiusBottom,
                               float innerRadiusTop, float innerRadiusBottom,
                               float height) {

        // --- guard dimensions ---------------------------------------------------
        if (axisSamples < 2) axisSamples = 2;
        if (radialSamples < 3) radialSamples = 3;

        outerRadiusTop = Math.max(outerRadiusTop, MIN_DIMENSION);
        outerRadiusBottom = Math.max(outerRadiusBottom, MIN_DIMENSION);
        innerRadiusTop = Math.max(innerRadiusTop, 0f);
        innerRadiusBottom = Math.max(innerRadiusBottom, 0f);
        height = Math.max(height, MIN_DIMENSION);

        // clamp inner to 90% of outer if it would meet or exceed outer
        if (innerRadiusTop >= outerRadiusTop) {
            innerRadiusTop = outerRadiusTop * 0.9f;
        }
        if (innerRadiusBottom >= outerRadiusBottom) {
            innerRadiusBottom = outerRadiusBottom * 0.9f;
        }

        // --- vertex / index counts -----------------------------------------------
        // Each wall (outer & inner): (axisSamples) * (radialSamples + 1) vertices
        //   radialSamples+1 because the last column duplicates the first for UV seam.
        int wallVerts = axisSamples * (radialSamples + 1);
        // Each cap (top & bottom): (radialSamples + 1) * 2 vertices (outer ring + inner ring)
        int capVerts = (radialSamples + 1) * 2;

        int totalVerts = wallVerts * 2 + capVerts * 2; // outer wall + inner wall + top cap + bottom cap

        // Wall triangles: (axisSamples-1) * radialSamples * 2 triangles per wall
        int wallTris = (axisSamples - 1) * radialSamples * 2;
        // Cap triangles: radialSamples * 2 triangles per cap
        int capTris = radialSamples * 2;

        int totalTris = wallTris * 2 + capTris * 2;
        int totalIndices = totalTris * 3;

        // --- allocate buffers ----------------------------------------------------
        FloatBuffer posBuf = BufferUtils.createFloatBuffer(totalVerts * 3);
        FloatBuffer normBuf = BufferUtils.createFloatBuffer(totalVerts * 3);
        FloatBuffer texBuf = BufferUtils.createFloatBuffer(totalVerts * 2);
        ShortBuffer idxBuf = BufferUtils.createShortBuffer(totalIndices);

        float halfHeight = height * 0.5f;
        int vertexOffset = 0;

        // =====================================================================
        // 1. OUTER WALL
        // =====================================================================
        int outerWallStart = vertexOffset;
        vertexOffset = buildWall(posBuf, normBuf, texBuf,
                axisSamples, radialSamples,
                outerRadiusTop, outerRadiusBottom,
                halfHeight, true, vertexOffset);

        buildWallIndices(idxBuf, outerWallStart, axisSamples, radialSamples, false);

        // =====================================================================
        // 2. INNER WALL
        // =====================================================================
        int innerWallStart = vertexOffset;
        vertexOffset = buildWall(posBuf, normBuf, texBuf,
                axisSamples, radialSamples,
                innerRadiusTop, innerRadiusBottom,
                halfHeight, false, vertexOffset);

        buildWallIndices(idxBuf, innerWallStart, axisSamples, radialSamples, true);

        // =====================================================================
        // 3. TOP CAP  (y = +halfHeight)
        // =====================================================================
        int topCapStart = vertexOffset;
        vertexOffset = buildCap(posBuf, normBuf, texBuf,
                radialSamples,
                outerRadiusTop, innerRadiusTop,
                halfHeight, true, vertexOffset);

        buildCapIndices(idxBuf, topCapStart, radialSamples, true);

        // =====================================================================
        // 4. BOTTOM CAP  (y = -halfHeight)
        // =====================================================================
        int bottomCapStart = vertexOffset;
        vertexOffset = buildCap(posBuf, normBuf, texBuf,
                radialSamples,
                outerRadiusBottom, innerRadiusBottom,
                -halfHeight, false, vertexOffset);

        buildCapIndices(idxBuf, bottomCapStart, radialSamples, false);

        // --- set buffers on mesh -------------------------------------------------
        posBuf.flip();
        normBuf.flip();
        texBuf.flip();
        idxBuf.flip();

        setBuffer(VertexBuffer.Type.Position, 3, posBuf);
        setBuffer(VertexBuffer.Type.Normal, 3, normBuf);
        setBuffer(VertexBuffer.Type.TexCoord, 2, texBuf);
        setBuffer(VertexBuffer.Type.Index, 3, idxBuf);

        updateBound();
        setStatic();
    }

    // =========================================================================
    //  Wall generation
    // =========================================================================

    /**
     * Generates vertices, normals and UVs for a cylindrical wall surface.
     *
     * @param outer  true = normals face outward; false = normals face inward
     * @return the new vertex offset after appending
     */
    private int buildWall(FloatBuffer pos, FloatBuffer norm, FloatBuffer tex,
                          int axisSamples, int radialSamples,
                          float radiusTop, float radiusBottom,
                          float halfHeight, boolean outer,
                          int vertexOffset) {

        float inverseRadial = 1.0f / radialSamples;

        // slope for the normal (accounts for cone/frustum shape)
        float dr = radiusBottom - radiusTop;
        float slopeLen = FastMath.sqrt(dr * dr + (halfHeight * 2f) * (halfHeight * 2f));
        float nY = (slopeLen > MIN_DIMENSION) ? dr / slopeLen : 0f;
        float nRadial = (slopeLen > MIN_DIMENSION) ? (halfHeight * 2f) / slopeLen : 1f;

        for (int axisIdx = 0; axisIdx < axisSamples; axisIdx++) {
            float axisFraction = (float) axisIdx / (axisSamples - 1); // 0..1
            float y = halfHeight - axisFraction * halfHeight * 2f;    // top to bottom
            float radius = FastMath.interpolateLinear(axisFraction, radiusTop, radiusBottom);

            for (int radIdx = 0; radIdx <= radialSamples; radIdx++) {
                float angle = FastMath.TWO_PI * radIdx * inverseRadial;
                float cos = FastMath.cos(angle);
                float sin = FastMath.sin(angle);

                // position
                float x = radius * cos;
                float z = radius * sin;
                pos.put(x).put(y).put(z);

                // normal
                float nx = nRadial * cos;
                float ny = nY;
                float nz = nRadial * sin;
                if (!outer) {
                    nx = -nx;
                    ny = -ny;
                    nz = -nz;
                }
                // normalize
                float len = FastMath.sqrt(nx * nx + ny * ny + nz * nz);
                if (len > MIN_DIMENSION) {
                    nx /= len;
                    ny /= len;
                    nz /= len;
                }
                norm.put(nx).put(ny).put(nz);

                // UV:  u = angle fraction, v = axis fraction
                float u = (float) radIdx * inverseRadial;
                float v = axisFraction;
                tex.put(u).put(v);
            }
        }

        return vertexOffset + axisSamples * (radialSamples + 1);
    }

    /**
     * Emits triangle indices for a wall section.
     *
     * @param flipWinding true to reverse triangle winding (for inner wall)
     */
    private void buildWallIndices(ShortBuffer idx, int baseVertex,
                                  int axisSamples, int radialSamples,
                                  boolean flipWinding) {
        int stride = radialSamples + 1;

        for (int axisIdx = 0; axisIdx < axisSamples - 1; axisIdx++) {
            int rowStart = baseVertex + axisIdx * stride;
            int nextRowStart = rowStart + stride;

            for (int radIdx = 0; radIdx < radialSamples; radIdx++) {
                int a = rowStart + radIdx;
                int b = rowStart + radIdx + 1;
                int c = nextRowStart + radIdx;
                int d = nextRowStart + radIdx + 1;

                if (flipWinding) {
                    // triangle 1
                    idx.put((short) a).put((short) c).put((short) b);
                    // triangle 2
                    idx.put((short) b).put((short) c).put((short) d);
                } else {
                    // triangle 1
                    idx.put((short) a).put((short) b).put((short) c);
                    // triangle 2
                    idx.put((short) b).put((short) d).put((short) c);
                }
            }
        }
    }

    // =========================================================================
    //  Cap generation (annular ring)
    // =========================================================================

    /**
     * Generates vertices, normals and UVs for an annular cap.
     * Layout: first (radialSamples+1) vertices are the outer ring,
     *         next  (radialSamples+1) vertices are the inner ring.
     *
     * @param top true = top cap (normal +Y); false = bottom cap (normal -Y)
     * @return the new vertex offset after appending
     */
    private int buildCap(FloatBuffer pos, FloatBuffer norm, FloatBuffer tex,
                         int radialSamples,
                         float outerRadius, float innerRadius,
                         float y, boolean top,
                         int vertexOffset) {

        float inverseRadial = 1.0f / radialSamples;
        float ny = top ? 1f : -1f;

        // outer ring
        for (int radIdx = 0; radIdx <= radialSamples; radIdx++) {
            float angle = FastMath.TWO_PI * radIdx * inverseRadial;
            float cos = FastMath.cos(angle);
            float sin = FastMath.sin(angle);

            pos.put(outerRadius * cos).put(y).put(outerRadius * sin);
            norm.put(0f).put(ny).put(0f);

            // UV: map the annular ring into 0..1 based on angle and radius
            float u = 0.5f + 0.5f * cos;
            float v = 0.5f + 0.5f * sin;
            tex.put(u).put(v);
        }

        // inner ring
        for (int radIdx = 0; radIdx <= radialSamples; radIdx++) {
            float angle = FastMath.TWO_PI * radIdx * inverseRadial;
            float cos = FastMath.cos(angle);
            float sin = FastMath.sin(angle);

            pos.put(innerRadius * cos).put(y).put(innerRadius * sin);
            norm.put(0f).put(ny).put(0f);

            // UV: scaled inward proportionally
            float scale = (outerRadius > MIN_DIMENSION) ? innerRadius / outerRadius : 0.5f;
            float u = 0.5f + 0.5f * scale * cos;
            float v = 0.5f + 0.5f * scale * sin;
            tex.put(u).put(v);
        }

        return vertexOffset + (radialSamples + 1) * 2;
    }

    /**
     * Emits triangle indices for an annular cap.
     * Outer ring starts at baseVertex, inner ring at baseVertex + (radialSamples+1).
     */
    private void buildCapIndices(ShortBuffer idx, int baseVertex,
                                 int radialSamples, boolean top) {
        int stride = radialSamples + 1;
        int innerStart = baseVertex + stride;

        for (int radIdx = 0; radIdx < radialSamples; radIdx++) {
            int outerA = baseVertex + radIdx;
            int outerB = baseVertex + radIdx + 1;
            int innerA = innerStart + radIdx;
            int innerB = innerStart + radIdx + 1;

            if (top) {
                // winding so normal faces +Y
                idx.put((short) outerA).put((short) innerA).put((short) outerB);
                idx.put((short) outerB).put((short) innerA).put((short) innerB);
            } else {
                // winding so normal faces -Y
                idx.put((short) outerA).put((short) outerB).put((short) innerA);
                idx.put((short) outerB).put((short) innerB).put((short) innerA);
            }
        }
    }
}
