package com.scenemaxeng.common.ui.widget;

import com.jme3.app.Application;
import com.jme3.asset.AssetManager;
import com.jme3.scene.Node;
import com.jme3.scene.control.BillboardControl;
import com.scenemaxeng.common.ui.model.UIDocument;
import com.scenemaxeng.common.ui.model.UILayerDef;
import com.scenemaxeng.common.ui.model.UIRenderMode;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runtime manager for the UI system.
 */
public class UIManager {

    private static final Logger LOGGER = Logger.getLogger(UIManager.class.getName());

    private Application app;
    private AssetManager assetManager;
    private Node guiNode;
    private Node rootNode;

    private Map<String, LoadedUI> loadedUIs = new LinkedHashMap<>();

    private static class LoadedUI {
        UIDocument document;
        Map<String, UILayerNode> layerNodes = new LinkedHashMap<>();
    }

    public UIManager(Application app, Node guiNode, Node rootNode) {
        this.app = app;
        this.assetManager = app.getAssetManager();
        this.guiNode = guiNode;
        this.rootNode = rootNode;
    }

    public String load(File file) throws IOException {
        LOGGER.log(Level.INFO, "UIManager loading document from {0}", file.getAbsolutePath());
        UIDocument doc = UIDocument.load(file);
        return loadDocument(doc);
    }

    public String loadDocument(UIDocument doc) {
        String name = doc.getName();
        LOGGER.log(Level.INFO, "UIManager building UI ''{0}'' canvas={1}x{2} layers={3}",
                new Object[]{name, doc.getCanvasWidth(), doc.getCanvasHeight(), doc.getLayers().size()});

        unload(name);

        LoadedUI loadedUI = new LoadedUI();
        loadedUI.document = doc;

        float canvasWidth = doc.getCanvasWidth();
        float canvasHeight = doc.getCanvasHeight();
        float runtimeWidth = app.getContext() != null && app.getContext().getSettings() != null
                ? app.getContext().getSettings().getWidth() : canvasWidth;
        float runtimeHeight = app.getContext() != null && app.getContext().getSettings() != null
                ? app.getContext().getSettings().getHeight() : canvasHeight;

        for (UILayerDef layerDef : doc.getLayers()) {
            UILayerNode layerNode = new UILayerNode(layerDef, assetManager,
                    canvasWidth, canvasHeight, runtimeWidth, runtimeHeight);
            layerNode.buildAndLayout();

            if (layerDef.getRenderMode() == UIRenderMode.SCREEN_SPACE) {
                guiNode.attachChild(layerNode);
                LOGGER.log(Level.INFO, "UI layer ''{0}'' attached to guiNode mode={1} visible={2}",
                        new Object[]{layerDef.getName(), layerDef.getRenderMode(), layerDef.isVisible()});
            } else {
                addBillboardControls(layerNode);
                rootNode.attachChild(layerNode);
                LOGGER.log(Level.INFO, "UI layer ''{0}'' attached to rootNode mode={1} visible={2}",
                        new Object[]{layerDef.getName(), layerDef.getRenderMode(), layerDef.isVisible()});
            }

            layerNode.setLayerVisible(layerDef.isVisible());
            loadedUI.layerNodes.put(layerDef.getName(), layerNode);
        }

        loadedUIs.put(name, loadedUI);
        LOGGER.log(Level.INFO, "UIManager finished loading UI ''{0}''", name);
        return name;
    }

    public void unload(String uiName) {
        LoadedUI loaded = loadedUIs.remove(uiName);
        if (loaded == null) {
            return;
        }

        for (UILayerNode layerNode : loaded.layerNodes.values()) {
            layerNode.removeFromParent();
        }
    }

    public void unloadAll() {
        for (String name : new java.util.ArrayList<>(loadedUIs.keySet())) {
            unload(name);
        }
    }

    public UILayerNode resolveLayer(String uiName, String layerName) {
        LoadedUI loaded = loadedUIs.get(uiName);
        if (loaded == null) {
            return null;
        }
        return loaded.layerNodes.get(layerName);
    }

    public UIWidgetNode resolveWidget(String uiName, String layerName, String widgetName) {
        UILayerNode layer = resolveLayer(uiName, layerName);
        if (layer == null) {
            return null;
        }
        return layer.findWidget(widgetName);
    }

    public UIWidgetNode resolveWidgetPath(String uiName, String dotPath) {
        LoadedUI loaded = loadedUIs.get(uiName);
        if (loaded == null) {
            return null;
        }

        String[] parts = dotPath.split("\\.", 2);
        if (parts.length < 2) {
            return null;
        }

        UILayerNode layer = loaded.layerNodes.get(parts[0]);
        if (layer == null) {
            return null;
        }

        String widgetName = parts[1];
        if (widgetName.contains(".")) {
            String[] widgetParts = widgetName.split("\\.");
            widgetName = widgetParts[widgetParts.length - 1];
        }

        return layer.findWidget(widgetName);
    }

    public UIDocument getDocument(String uiName) {
        LoadedUI loaded = loadedUIs.get(uiName);
        return loaded != null ? loaded.document : null;
    }

    public boolean isLoaded(String uiName) {
        return loadedUIs.containsKey(uiName);
    }

    private void addBillboardControls(Node node) {
        BillboardControl billboard = new BillboardControl();
        node.addControl(billboard);
    }
}
