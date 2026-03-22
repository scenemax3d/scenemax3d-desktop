package com.scenemax.designer.gizmo;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.debug.Arrow;

/**
 * Translation gizmo with three colored arrows (X=Red, Y=Green, Z=Blue).
 * Picking is handled via screen-space distance in GizmoManager, so no
 * invisible collision geometry is needed here.
 */
public class TranslateGizmo extends Node {

    public static final float ARROW_LENGTH = 1.5f;

    public TranslateGizmo(AssetManager assetManager) {
        super("TranslateGizmo");

        attachChild(createArrow(assetManager, "X", new Vector3f(ARROW_LENGTH, 0, 0), ColorRGBA.Red));
        attachChild(createArrow(assetManager, "Y", new Vector3f(0, ARROW_LENGTH, 0), ColorRGBA.Green));
        attachChild(createArrow(assetManager, "Z", new Vector3f(0, 0, ARROW_LENGTH), ColorRGBA.Blue));
    }

    private Geometry createArrow(AssetManager assetManager, String axis, Vector3f extent, ColorRGBA color) {
        Arrow arrow = new Arrow(extent);
        arrow.setLineWidth(3f);
        Geometry geo = new Geometry("GizmoArrow_" + axis, arrow);

        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);
        mat.getAdditionalRenderState().setLineWidth(3f);
        geo.setMaterial(mat);

        return geo;
    }

    /**
     * Returns the world-space endpoint of an axis arrow,
     * accounting for the gizmo's transform (position + scale).
     */
    public Vector3f getAxisEndpoint(String axis) {
        Vector3f local;
        switch (axis) {
            case "X": local = new Vector3f(ARROW_LENGTH, 0, 0); break;
            case "Y": local = new Vector3f(0, ARROW_LENGTH, 0); break;
            case "Z": local = new Vector3f(0, 0, ARROW_LENGTH); break;
            default: return getWorldTranslation().clone();
        }
        return localToWorld(local, null);
    }
}
