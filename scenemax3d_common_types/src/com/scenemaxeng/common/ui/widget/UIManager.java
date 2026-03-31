package com.scenemaxeng.common.ui.widget;

import com.jme3.app.Application;
import com.jme3.asset.AssetManager;
import com.jme3.scene.Node;
import com.jme3.scene.control.BillboardControl;
import com.scenemaxeng.common.ui.model.UIDocument;
import com.scenemaxeng.common.ui.model.UILayerDef;
import com.scenemaxeng.common.ui.model.UIRenderMode;

import com.scenemaxeng.common.types.AssetsMapping;

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
    private AssetsMapping assetsMapping;
    private String activeUIName;

    private Map<String, LoadedUI> loadedUIs = new LinkedHashMap<>();

    private static class LoadedUI {
        UIDocument document;
        Map<String, UILayerNode> layerNodes = new LinkedHashMap<>();
    }

    private static class ResolvedUIPath {
        LoadedUI loadedUI;
        String layerName;
        String widgetPath;
    }

    public UIManager(Application app, Node guiNode, Node rootNode) {
        this.app = app;
        this.assetManager = app.getAssetManager();
        this.guiNode = guiNode;
        this.rootNode = rootNode;
    }

    public UIManager(Application app, Node guiNode, Node rootNode, AssetsMapping assetsMapping) {
        this(app, guiNode, rootNode);
        this.assetsMapping = assetsMapping;
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
                    canvasWidth, canvasHeight, runtimeWidth, runtimeHeight, assetsMapping);
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
        activeUIName = name;
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

        if (uiName != null && uiName.equals(activeUIName)) {
            activeUIName = loadedUIs.isEmpty() ? null : loadedUIs.keySet().iterator().next();
        }
    }

    public void unloadAll() {
        for (String name : new java.util.ArrayList<>(loadedUIs.keySet())) {
            unload(name);
        }
    }

    public UILayerNode resolveLayer(String uiName, String layerName) {
        ResolvedUIPath resolved = resolvePath(uiName, layerName, null);
        if (resolved == null || resolved.loadedUI == null) {
            return null;
        }
        return resolved.loadedUI.layerNodes.get(resolved.layerName);
    }

    public UIWidgetNode resolveWidget(String uiName, String layerName, String widgetName) {
        ResolvedUIPath resolved = resolvePath(uiName, layerName, widgetName);
        if (resolved == null || resolved.loadedUI == null) {
            return null;
        }
        UILayerNode layer = resolved.loadedUI.layerNodes.get(resolved.layerName);
        if (layer == null) {
            return null;
        }
        return layer.findWidget(resolved.widgetPath);
    }

    public UIWidgetNode resolveWidgetPath(String uiName, String dotPath) {
        LoadedUI loaded = getLoadedUI(uiName);
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

        return layer.findWidget(parts[1]);
    }

    public UIDocument getDocument(String uiName) {
        LoadedUI loaded = getLoadedUI(uiName);
        return loaded != null ? loaded.document : null;
    }

    public boolean isLoaded(String uiName) {
        return loadedUIs.containsKey(uiName);
    }

    public String getActiveUIName() {
        return activeUIName;
    }

    private LoadedUI getLoadedUI(String uiName) {
        if (uiName != null && !uiName.isEmpty()) {
            return loadedUIs.get(uiName);
        }
        if (activeUIName != null) {
            return loadedUIs.get(activeUIName);
        }
        if (loadedUIs.size() == 1) {
            return loadedUIs.values().iterator().next();
        }
        return null;
    }

    private ResolvedUIPath resolvePath(String uiName, String layerName, String widgetPath) {
        LoadedUI loaded = getLoadedUI(uiName);
        if (loaded != null && layerName != null && loaded.layerNodes.containsKey(layerName)) {
            ResolvedUIPath resolved = new ResolvedUIPath();
            resolved.loadedUI = loaded;
            resolved.layerName = layerName;
            resolved.widgetPath = widgetPath;
            return resolved;
        }

        LoadedUI activeLoaded = getLoadedUI(null);
        if (activeLoaded != null && uiName != null && activeLoaded.layerNodes.containsKey(uiName)) {
            ResolvedUIPath resolved = new ResolvedUIPath();
            resolved.loadedUI = activeLoaded;
            resolved.layerName = uiName;
            if (layerName == null || layerName.isEmpty()) {
                resolved.widgetPath = widgetPath;
            } else if (widgetPath == null || widgetPath.isEmpty()) {
                resolved.widgetPath = layerName;
            } else {
                resolved.widgetPath = layerName + "." + widgetPath;
            }
            return resolved;
        }

        return null;
    }

    private void addBillboardControls(Node node) {
        BillboardControl billboard = new BillboardControl();
        node.addControl(billboard);
    }
}
