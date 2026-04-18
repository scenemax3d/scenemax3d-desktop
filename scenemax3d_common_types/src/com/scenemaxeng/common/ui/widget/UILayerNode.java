package com.scenemaxeng.common.ui.widget;

import com.jme3.asset.AssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.scenemaxeng.common.types.AssetsMapping;
import com.scenemaxeng.common.ui.layout.ConstraintLayoutEngine;
import com.scenemaxeng.common.ui.layout.LayoutRect;
import com.scenemaxeng.common.ui.model.UILayerDef;
import com.scenemaxeng.common.ui.model.UIRenderMode;
import com.scenemaxeng.common.ui.model.UIWidgetDef;
import com.scenemaxeng.common.ui.model.UIWidgetType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JME Node representing a UI layer.
 */
public class UILayerNode extends Node {

    private static final Logger LOGGER = Logger.getLogger(UILayerNode.class.getName());

    private UILayerDef layerDef;
    private AssetManager assetManager;
    private float designCanvasWidth;
    private float designCanvasHeight;
    private float runtimeCanvasWidth;
    private float runtimeCanvasHeight;
    private AssetsMapping assetsMapping;

    private Map<String, UIWidgetNode> widgetNodes = new LinkedHashMap<>();
    private Map<String, UIWidgetNode> widgetNodesByPath = new LinkedHashMap<>();

    private ConstraintLayoutEngine layoutEngine = new ConstraintLayoutEngine();
    private final Vector3f baseLocalTranslation = new Vector3f();
    private final Vector3f animationOffset = new Vector3f();

    public UILayerNode(UILayerDef layerDef, AssetManager assetManager,
                       float designCanvasWidth, float designCanvasHeight,
                       float runtimeCanvasWidth, float runtimeCanvasHeight,
                       AssetsMapping assetsMapping) {
        super(layerDef.getName());
        this.layerDef = layerDef;
        this.assetManager = assetManager;
        this.designCanvasWidth = designCanvasWidth;
        this.designCanvasHeight = designCanvasHeight;
        this.runtimeCanvasWidth = runtimeCanvasWidth;
        this.runtimeCanvasHeight = runtimeCanvasHeight;
        this.assetsMapping = assetsMapping;
    }

    public void buildAndLayout() {
        detachAllChildren();
        widgetNodes.clear();
        widgetNodesByPath.clear();
        LOGGER.log(Level.INFO, "Building UI layer ''{0}'' with {1} top-level widgets",
                new Object[]{layerDef.getName(), layerDef.getWidgets().size()});

        for (UIWidgetDef widgetDef : layerDef.getWidgets()) {
            createWidgetNodeRecursive(widgetDef, this, widgetDef.getName());
        }

        runLayout();
    }

    public void runLayout() {
        Map<String, LayoutRect> results = layoutEngine.solve(layerDef.getWidgets(), designCanvasWidth, designCanvasHeight);

        for (Map.Entry<String, LayoutRect> entry : results.entrySet()) {
            UIWidgetNode node = widgetNodes.get(entry.getKey());
            if (node != null) {
                LOGGER.log(Level.INFO, "UI widget ''{0}'' top-level rect={1}",
                        new Object[]{entry.getKey(), entry.getValue()});
                node.updateLayout(entry.getValue());
            }
        }

        for (UIWidgetDef widgetDef : layerDef.getWidgets()) {
            solveChildrenRecursive(widgetDef, results);
        }
    }

    private void solveChildrenRecursive(UIWidgetDef parent, Map<String, LayoutRect> allResults) {
        if (parent.getChildren().isEmpty()) {
            return;
        }

        LayoutRect parentRect = allResults.get(parent.getName());
        if (parentRect == null) {
            return;
        }

        layoutEngine.solveChildren(parent, parentRect, allResults);

        for (UIWidgetDef child : parent.getChildren()) {
            LayoutRect childRect = allResults.get(child.getName());
            UIWidgetNode childNode = widgetNodes.get(child.getName());
            if (childNode != null && childRect != null) {
                childNode.updateLayout(childRect);
            }
            solveChildrenRecursive(child, allResults);
        }
    }

    private void createWidgetNodeRecursive(UIWidgetDef widgetDef, Node parentNode, String widgetPath) {
        if (widgetDef.getType() == UIWidgetType.GUIDELINE) {
            return;
        }

        UIWidgetNode widgetNode = UIWidgetNode.create(widgetDef, assetManager,
                designCanvasWidth, designCanvasHeight, runtimeCanvasWidth, runtimeCanvasHeight, assetsMapping);
        if (widgetNode == null) {
            LOGGER.log(Level.WARNING, "Skipping UI widget ''{0}'' type={1} because no runtime node exists",
                    new Object[]{widgetDef.getName(), widgetDef.getType()});
            return;
        }

        LOGGER.log(Level.INFO, "Creating UI widget ''{0}'' type={1} visible={2} children={3}",
                new Object[]{widgetDef.getName(), widgetDef.getType(), widgetDef.isVisible(), widgetDef.getChildren().size()});
        widgetNode.createVisual();
        widgetNode.setWidgetVisible(widgetDef.isVisible());

        parentNode.attachChild(widgetNode);
        widgetNodes.put(widgetDef.getName(), widgetNode);
        widgetNodesByPath.put(widgetPath, widgetNode);

        for (UIWidgetDef child : widgetDef.getChildren()) {
            createWidgetNodeRecursive(child, widgetNode, widgetPath + "." + child.getName());
        }
    }

    public void setLayerVisible(boolean visible) {
        layerDef.setVisible(visible);
        setCullHint(visible ? CullHint.Inherit : CullHint.Always);
    }

    public void setAnimationOffset(float x, float y, float z) {
        animationOffset.set(x, y, z);
        applyAnimatedTranslation();
    }

    public void clearAnimationOffset() {
        setAnimationOffset(0f, 0f, 0f);
    }

    public Vector3f getBaseLocalTranslation() {
        return baseLocalTranslation.clone();
    }

    public float getRuntimeWidth() {
        return runtimeCanvasWidth;
    }

    public float getRuntimeHeight() {
        return runtimeCanvasHeight;
    }

    public UIWidgetNode findWidget(String name) {
        UIWidgetNode widget = widgetNodesByPath.get(name);
        if (widget != null) {
            return widget;
        }
        return widgetNodes.get(name);
    }

    public UILayerDef getLayerDef() {
        return layerDef;
    }

    public boolean isScreenSpace() {
        return layerDef.getRenderMode() == UIRenderMode.SCREEN_SPACE;
    }

    private void applyAnimatedTranslation() {
        super.setLocalTranslation(
                baseLocalTranslation.x + animationOffset.x,
                baseLocalTranslation.y + animationOffset.y,
                baseLocalTranslation.z + animationOffset.z
        );
    }
}
