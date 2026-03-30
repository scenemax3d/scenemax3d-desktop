package com.scenemax.designer.ui.widget;

import com.jme3.asset.AssetManager;
import com.jme3.scene.Node;
import com.scenemax.designer.ui.layout.ConstraintLayoutEngine;
import com.scenemax.designer.ui.layout.LayoutRect;
import com.scenemax.designer.ui.model.UILayerDef;
import com.scenemax.designer.ui.model.UIRenderMode;
import com.scenemax.designer.ui.model.UIWidgetDef;
import com.scenemax.designer.ui.model.UIWidgetType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JME Node representing a UI layer.
 * Contains all widget nodes for this layer and manages layout.
 *
 * For SCREEN_SPACE layers, this node should be attached to JME's guiNode.
 * For WORLD_SPACE layers, this node is attached to the regular scene graph
 * and billboard controls are added.
 */
public class UILayerNode extends Node {

    private UILayerDef layerDef;
    private AssetManager assetManager;
    private float canvasWidth;
    private float canvasHeight;

    // All widget nodes in this layer, keyed by name for fast lookup
    private Map<String, UIWidgetNode> widgetNodes = new LinkedHashMap<>();

    private ConstraintLayoutEngine layoutEngine = new ConstraintLayoutEngine();

    public UILayerNode(UILayerDef layerDef, AssetManager assetManager,
                       float canvasWidth, float canvasHeight) {
        super(layerDef.getName());
        this.layerDef = layerDef;
        this.assetManager = assetManager;
        this.canvasWidth = canvasWidth;
        this.canvasHeight = canvasHeight;
    }

    /**
     * Builds all widget nodes from the layer definition and runs the initial layout.
     */
    public void buildAndLayout() {
        // Clear existing children
        detachAllChildren();
        widgetNodes.clear();

        // Create widget nodes recursively
        for (UIWidgetDef widgetDef : layerDef.getWidgets()) {
            createWidgetNodeRecursive(widgetDef, this);
        }

        // Run the layout engine
        runLayout();
    }

    /**
     * Re-runs the constraint layout engine and updates all widget positions.
     * Call this after changing constraints, sizes, or text content.
     */
    public void runLayout() {
        // Solve the top-level layout
        Map<String, LayoutRect> results = layoutEngine.solve(
                layerDef.getWidgets(), canvasWidth, canvasHeight);

        // Apply layout to widget nodes
        for (Map.Entry<String, LayoutRect> entry : results.entrySet()) {
            UIWidgetNode node = widgetNodes.get(entry.getKey());
            if (node != null) {
                node.updateLayout(entry.getValue());
            }
        }

        // Solve children of container widgets
        for (UIWidgetDef widgetDef : layerDef.getWidgets()) {
            solveChildrenRecursive(widgetDef, results);
        }
    }

    private void solveChildrenRecursive(UIWidgetDef parent, Map<String, LayoutRect> allResults) {
        if (parent.getChildren().isEmpty()) return;

        LayoutRect parentRect = allResults.get(parent.getName());
        if (parentRect == null) return;

        layoutEngine.solveChildren(parent, parentRect, allResults);

        // Apply child layouts
        for (UIWidgetDef child : parent.getChildren()) {
            LayoutRect childRect = allResults.get(child.getName());
            UIWidgetNode childNode = widgetNodes.get(child.getName());
            if (childNode != null && childRect != null) {
                childNode.updateLayout(childRect);
            }
            solveChildrenRecursive(child, allResults);
        }
    }

    /**
     * Recursively creates widget nodes and attaches them to the parent JME node.
     */
    private void createWidgetNodeRecursive(UIWidgetDef widgetDef, Node parentNode) {
        if (widgetDef.getType() == UIWidgetType.GUIDELINE) {
            return; // guidelines are layout-only, no visual
        }

        UIWidgetNode widgetNode = UIWidgetNode.create(widgetDef, assetManager, canvasHeight);
        if (widgetNode == null) return;

        widgetNode.createVisual();
        widgetNode.setWidgetVisible(widgetDef.isVisible());

        parentNode.attachChild(widgetNode);
        widgetNodes.put(widgetDef.getName(), widgetNode);

        // Recurse into children
        for (UIWidgetDef child : widgetDef.getChildren()) {
            createWidgetNodeRecursive(child, widgetNode);
        }
    }

    // --- Public API ---

    /**
     * Shows or hides the entire layer.
     */
    public void setLayerVisible(boolean visible) {
        layerDef.setVisible(visible);
        setCullHint(visible ? CullHint.Inherit : CullHint.Always);
    }

    /**
     * Finds a widget node by name.
     */
    public UIWidgetNode findWidget(String name) {
        return widgetNodes.get(name);
    }

    /**
     * Returns the layer definition.
     */
    public UILayerDef getLayerDef() { return layerDef; }

    /**
     * Returns whether this layer uses screen-space rendering.
     */
    public boolean isScreenSpace() {
        return layerDef.getRenderMode() == UIRenderMode.SCREEN_SPACE;
    }
}
