package com.scenemax.designer.selection;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

/**
 * Applies a wireframe overlay to highlight selected objects.
 */
public class OutlineEffect {

    private static final String OUTLINE_KEY = "DesignerOutline";
    private final AssetManager assetManager;
    private Node currentOutlineNode;

    public OutlineEffect(AssetManager assetManager) {
        this.assetManager = assetManager;
    }

    public void applyOutline(Node targetNode) {
        removeOutline();

        if (targetNode == null) return;

        currentOutlineNode = new Node(OUTLINE_KEY);

        // Clone geometries with wireframe material
        for (Spatial child : targetNode.getChildren()) {
            if (child instanceof Geometry) {
                Geometry geo = (Geometry) child;
                Geometry outline = new Geometry(geo.getName() + "_outline", geo.getMesh());

                Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
                mat.setColor("Color", new ColorRGBA(1f, 0.6f, 0f, 1f)); // orange
                mat.getAdditionalRenderState().setWireframe(true);
                mat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);

                outline.setMaterial(mat);
                outline.setLocalTransform(geo.getLocalTransform());
                currentOutlineNode.attachChild(outline);
            } else if (child instanceof Node) {
                // Recurse into child nodes
                addOutlineRecursive((Node) child, currentOutlineNode);
            }
        }

        targetNode.attachChild(currentOutlineNode);
    }

    private void addOutlineRecursive(Node source, Node parent) {
        Node outlineGroup = new Node(source.getName() + "_outlineGroup");
        outlineGroup.setLocalTransform(source.getLocalTransform());

        for (Spatial child : source.getChildren()) {
            if (child instanceof Geometry) {
                Geometry geo = (Geometry) child;
                Geometry outline = new Geometry(geo.getName() + "_outline", geo.getMesh());

                Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
                mat.setColor("Color", new ColorRGBA(1f, 0.6f, 0f, 1f));
                mat.getAdditionalRenderState().setWireframe(true);
                mat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);

                outline.setMaterial(mat);
                outline.setLocalTransform(geo.getLocalTransform());
                outlineGroup.attachChild(outline);
            } else if (child instanceof Node) {
                addOutlineRecursive((Node) child, outlineGroup);
            }
        }

        parent.attachChild(outlineGroup);
    }

    public void removeOutline() {
        if (currentOutlineNode != null) {
            currentOutlineNode.removeFromParent();
            currentOutlineNode = null;
        }
    }
}
