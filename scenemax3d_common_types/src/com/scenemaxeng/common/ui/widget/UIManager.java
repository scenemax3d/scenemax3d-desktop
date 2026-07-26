package com.scenemaxeng.common.ui.widget;

import com.jme3.app.Application;
import com.jme3.asset.AssetManager;
import com.jme3.input.KeyInput;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.JoyAxisEvent;
import com.jme3.input.event.JoyButtonEvent;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.input.event.TouchEvent;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.control.BillboardControl;
import com.scenemaxeng.common.ui.model.UIDocument;
import com.scenemaxeng.common.ui.model.UILayerDef;
import com.scenemaxeng.common.ui.model.UIRenderMode;
import com.scenemaxeng.common.ui.model.UIWidgetDef;
import com.scenemaxeng.common.ui.model.UIWidgetType;

import com.scenemaxeng.common.types.AssetsMapping;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
    private UIEditTextNode focusedEditText;
    private boolean rawInputListenerInstalled;
    private boolean shiftDown;
    private boolean ctrlDown;

    private Map<String, LoadedUI> loadedUIs = new LinkedHashMap<>();
    private Map<String, UITextViewNode> multiplayerTextViews = new LinkedHashMap<>();

    private static class LoadedUI {
        String name;
        UIDocument document;
        Map<String, UILayerNode> layerNodes = new LinkedHashMap<>();
    }

    private static class ResolvedUIPath {
        String uiName;
        LoadedUI loadedUI;
        String layerName;
        String widgetPath;
    }

    public UIManager(Application app, Node guiNode, Node rootNode) {
        this.app = app;
        this.assetManager = app.getAssetManager();
        this.guiNode = guiNode;
        this.rootNode = rootNode;
        installRawInputListener();
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

    public String load(java.io.InputStream inputStream, String filePathHint) throws IOException {
        LOGGER.log(Level.INFO, "UIManager loading document from packaged resource {0}", filePathHint);
        UIDocument doc = UIDocument.load(inputStream, filePathHint);
        return loadDocument(doc);
    }

    public String loadDocument(UIDocument doc) {
        String name = doc.getName();
        LOGGER.log(Level.INFO, "UIManager building UI ''{0}'' canvas={1}x{2} layers={3}",
                new Object[]{name, doc.getCanvasWidth(), doc.getCanvasHeight(), doc.getLayers().size()});

        unload(name);

        LoadedUI loadedUI = new LoadedUI();
        loadedUI.name = name;
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
            registerMultiplayerTextViews(name, layerDef, layerNode);
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
        multiplayerTextViews.entrySet().removeIf(entry -> entry.getKey().startsWith(multiplayerSyncKeyPrefix(uiName)));
        if (focusedEditText != null) {
            focusedEditText.setFocused(false);
            focusedEditText = null;
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

    public String multiplayerTextSyncKey(String uiName, String layerName, String widgetPath) {
        if (uiName == null || uiName.trim().isEmpty()
                || layerName == null || layerName.trim().isEmpty()
                || widgetPath == null || widgetPath.trim().isEmpty()) {
            return null;
        }
        return multiplayerSyncKeyPrefix(uiName) + sha1Hex(layerName.trim() + "." + widgetPath.trim()).substring(0, 16);
    }

    public String multiplayerTextSyncKeyForPath(String uiName, String layerName, String widgetPath) {
        ResolvedUIPath resolved = resolvePath(uiName, layerName, widgetPath);
        if (resolved == null || resolved.uiName == null) {
            return null;
        }
        return multiplayerTextSyncKey(resolved.uiName, resolved.layerName, resolved.widgetPath);
    }

    public boolean applyMultiplayerTextSync(String syncKey, String text) {
        UITextViewNode node = multiplayerTextViews.get(syncKey);
        if (node == null) {
            return false;
        }
        node.setText(text == null ? "" : text);
        return true;
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
            resolved.uiName = loaded.name;
            resolved.loadedUI = loaded;
            resolved.layerName = layerName;
            resolved.widgetPath = widgetPath;
            return resolved;
        }

        LoadedUI activeLoaded = getLoadedUI(null);
        if (activeLoaded != null && uiName != null && activeLoaded.layerNodes.containsKey(uiName)) {
            ResolvedUIPath resolved = new ResolvedUIPath();
            resolved.uiName = activeLoaded.name;
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

    private void registerMultiplayerTextViews(String uiName, UILayerDef layerDef, UILayerNode layerNode) {
        if (uiName == null || layerDef == null || layerNode == null) {
            return;
        }
        for (UIWidgetDef widget : layerDef.getWidgets()) {
            registerMultiplayerTextViewRecursive(uiName, layerDef.getName(), widget, widget.getName(), layerNode);
        }
    }

    private void registerMultiplayerTextViewRecursive(String uiName, String layerName, UIWidgetDef widget,
                                                       String widgetPath, UILayerNode layerNode) {
        if (widget == null) {
            return;
        }
        if (widget.getType() == UIWidgetType.TEXT_VIEW && widget.isMultiplayer()) {
            UIWidgetNode node = layerNode.findWidget(widgetPath);
            if (node instanceof UITextViewNode) {
                String key = multiplayerTextSyncKey(uiName, layerName, widgetPath);
                if (key != null) {
                    multiplayerTextViews.put(key, (UITextViewNode) node);
                }
            }
        }
        for (UIWidgetDef child : widget.getChildren()) {
            registerMultiplayerTextViewRecursive(uiName, layerName, child, widgetPath + "." + child.getName(), layerNode);
        }
    }

    private String multiplayerSyncKeyPrefix(String uiName) {
        return "$ui:" + sha1Hex(uiName == null ? "" : uiName.trim()).substring(0, 8) + ":";
    }

    private String sha1Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            return Integer.toHexString((value == null ? "" : value).hashCode()) + "0000000000000000000000000000000000000000";
        }
    }

    private void addBillboardControls(Node node) {
        BillboardControl billboard = new BillboardControl();
        node.addControl(billboard);
    }

    private void installRawInputListener() {
        if (rawInputListenerInstalled || app.getInputManager() == null) {
            return;
        }
        app.getInputManager().addRawInputListener(new RawInputListener() {
            @Override public void beginInput() { }
            @Override public void endInput() { }
            @Override public void onJoyAxisEvent(JoyAxisEvent evt) { }
            @Override public void onJoyButtonEvent(JoyButtonEvent evt) { }
            @Override public void onMouseMotionEvent(MouseMotionEvent evt) { }
            @Override public void onTouchEvent(TouchEvent evt) { }

            @Override
            public void onMouseButtonEvent(MouseButtonEvent evt) {
                if (!evt.isPressed() || evt.getButtonIndex() != 0) {
                    return;
                }
                UIEditTextNode hit = findEditTextAt(evt.getX(), evt.getY());
                focusEditText(hit);
                if (hit != null) {
                    Vector3f world = hit.getWorldTranslation();
                    hit.setCaretFromLocal(evt.getX() - world.x, evt.getY() - world.y, shiftDown);
                    evt.setConsumed();
                }
            }

            @Override
            public void onKeyEvent(KeyInputEvent evt) {
                int keyCode = evt.getKeyCode();
                if (keyCode == KeyInput.KEY_LSHIFT || keyCode == KeyInput.KEY_RSHIFT) {
                    shiftDown = evt.isPressed();
                } else if (keyCode == KeyInput.KEY_LCONTROL || keyCode == KeyInput.KEY_RCONTROL) {
                    ctrlDown = evt.isPressed();
                }

                if (!evt.isPressed() || focusedEditText == null) {
                    return;
                }
                if (keyCode == KeyInput.KEY_ESCAPE) {
                    focusEditText(null);
                    evt.setConsumed();
                    return;
                }
                if (focusedEditText.handleKey(keyCode, evt.getKeyChar(), shiftDown, ctrlDown)) {
                    evt.setConsumed();
                }
            }
        });
        rawInputListenerInstalled = true;
    }

    private void focusEditText(UIEditTextNode editText) {
        if (focusedEditText == editText) {
            return;
        }
        if (focusedEditText != null) {
            focusedEditText.setFocused(false);
        }
        focusedEditText = editText;
        if (focusedEditText != null) {
            focusedEditText.setFocused(true);
        }
    }

    private UIEditTextNode findEditTextAt(float x, float y) {
        UIEditTextNode hit = null;
        for (LoadedUI loaded : loadedUIs.values()) {
            for (UILayerNode layer : loaded.layerNodes.values()) {
                if (!layer.isScreenSpace()) {
                    continue;
                }
                for (UIWidgetNode widget : layer.getWidgetNodes()) {
                    if (!(widget instanceof UIEditTextNode)
                            || widget.getCullHint() == com.jme3.scene.Spatial.CullHint.Always) {
                        continue;
                    }
                    Vector3f world = widget.getWorldTranslation();
                    if (x >= world.x && x <= world.x + widget.getRuntimeWidth()
                            && y >= world.y && y <= world.y + widget.getRuntimeHeight()) {
                        hit = (UIEditTextNode) widget;
                    }
                }
            }
        }
        return hit;
    }
}
