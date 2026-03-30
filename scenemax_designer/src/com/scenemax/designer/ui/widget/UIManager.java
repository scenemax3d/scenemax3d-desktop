package com.scenemax.designer.ui.widget;

import com.jme3.app.Application;
import com.jme3.asset.AssetManager;
import com.jme3.scene.Node;
import com.jme3.scene.control.BillboardControl;
import com.scenemax.designer.ui.model.UIDocument;
import com.scenemax.designer.ui.model.UILayerDef;
import com.scenemax.designer.ui.model.UIRenderMode;
import com.scenemax.designer.ui.model.UIWidgetDef;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime manager for the UI system.
 * Handles loading, rendering, and scripting access to UI documents.
 *
 * Usage from the JME application:
 *   UIManager uiManager = new UIManager(app);
 *   uiManager.load("myUI", new File("path/to/myUI.smui"));
 *
 * Access from SceneMax scripting:
 *   UI.load "myUI"
 *   UI.hud.show
 *   UI.hud.healthText.text = "100"
 */
public class UIManager {

    private Application app;
    private AssetManager assetManager;
    private Node guiNode;     // JME's gui node for screen-space UI
    private Node rootNode;    // JME's root node for world-space UI

    // All loaded UI systems, keyed by name
    private Map<String, LoadedUI> loadedUIs = new LinkedHashMap<>();

    /**
     * Represents a fully loaded and active UI system.
     */
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

    /**
     * Loads a UI document and creates all layers and widgets.
     *
     * @param file the .smui file to load
     * @return the UI name for scripting access
     */
    public String load(File file) throws IOException {
        UIDocument doc = UIDocument.load(file);
        return loadDocument(doc);
    }

    /**
     * Loads a UI from an already-parsed document.
     */
    public String loadDocument(UIDocument doc) {
        String name = doc.getName();

        // Unload existing UI with the same name
        unload(name);

        LoadedUI loadedUI = new LoadedUI();
        loadedUI.document = doc;

        float canvasWidth = doc.getCanvasWidth();
        float canvasHeight = doc.getCanvasHeight();

        for (UILayerDef layerDef : doc.getLayers()) {
            UILayerNode layerNode = new UILayerNode(layerDef, assetManager, canvasWidth, canvasHeight);
            layerNode.buildAndLayout();

            // Attach to the correct parent based on render mode
            if (layerDef.getRenderMode() == UIRenderMode.SCREEN_SPACE) {
                guiNode.attachChild(layerNode);
            } else {
                // World-space: add billboard control to each widget
                addBillboardControls(layerNode);
                rootNode.attachChild(layerNode);
            }

            // Apply initial visibility
            layerNode.setLayerVisible(layerDef.isVisible());

            loadedUI.layerNodes.put(layerDef.getName(), layerNode);
        }

        loadedUIs.put(name, loadedUI);
        return name;
    }

    /**
     * Unloads a UI system, removing all nodes from the scene.
     */
    public void unload(String uiName) {
        LoadedUI loaded = loadedUIs.remove(uiName);
        if (loaded == null) return;

        for (UILayerNode layerNode : loaded.layerNodes.values()) {
            layerNode.removeFromParent();
        }
    }

    /**
     * Unloads all UI systems.
     */
    public void unloadAll() {
        for (String name : new java.util.ArrayList<>(loadedUIs.keySet())) {
            unload(name);
        }
    }

    // ========================================================================
    // Scripting access — resolves dot-paths from the SceneMax language
    // ========================================================================

    /**
     * Resolves "uiName.layerName" to a UILayerNode.
     */
    public UILayerNode resolveLayer(String uiName, String layerName) {
        LoadedUI loaded = loadedUIs.get(uiName);
        if (loaded == null) return null;
        return loaded.layerNodes.get(layerName);
    }

    /**
     * Resolves "uiName.layerName.widgetName" to a UIWidgetNode.
     * The widget name is searched recursively across all widgets in the layer.
     */
    public UIWidgetNode resolveWidget(String uiName, String layerName, String widgetName) {
        UILayerNode layer = resolveLayer(uiName, layerName);
        if (layer == null) return null;
        return layer.findWidget(widgetName);
    }

    /**
     * Resolves a full dot-path like "hud.healthPanel.healthText" into the target widget.
     * The path is relative to a loaded UI (the UI name is passed separately).
     *
     * @param uiName  the loaded UI name
     * @param dotPath "layerName.widgetName" or "layerName.parentWidget.childWidget"
     * @return the widget node, or null
     */
    public UIWidgetNode resolveWidgetPath(String uiName, String dotPath) {
        LoadedUI loaded = loadedUIs.get(uiName);
        if (loaded == null) return null;

        String[] parts = dotPath.split("\\.", 2);
        if (parts.length < 2) return null;

        UILayerNode layer = loaded.layerNodes.get(parts[0]);
        if (layer == null) return null;

        // Search for the widget by the remaining path
        // For simplicity, widgets must have globally unique names within a layer
        String widgetName = parts[1];
        if (widgetName.contains(".")) {
            // Multi-level path: extract the leaf name
            String[] widgetParts = widgetName.split("\\.");
            widgetName = widgetParts[widgetParts.length - 1];
        }

        return layer.findWidget(widgetName);
    }

    /**
     * Returns the document for a loaded UI.
     */
    public UIDocument getDocument(String uiName) {
        LoadedUI loaded = loadedUIs.get(uiName);
        return loaded != null ? loaded.document : null;
    }

    /**
     * Returns whether a UI with the given name is loaded.
     */
    public boolean isLoaded(String uiName) {
        return loadedUIs.containsKey(uiName);
    }

    // ========================================================================
    // Internal
    // ========================================================================

    /**
     * Adds BillboardControl to all child nodes of a world-space layer
     * so they always face the camera.
     */
    private void addBillboardControls(Node node) {
        BillboardControl billboard = new BillboardControl();
        node.addControl(billboard);
    }
}
