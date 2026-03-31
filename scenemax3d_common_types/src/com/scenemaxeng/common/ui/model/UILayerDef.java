package com.scenemaxeng.common.ui.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Defines a UI layer. Each layer is a container that maps to a JME Node.
 * A layer holds widgets and can be shown/hidden as a group.
 * Layers are laid out independently — each layer fills the full screen/canvas.
 */
public class UILayerDef {

    private String id;
    private String name;
    private boolean visible = true;
    private int zOrder = 0;
    private UIRenderMode renderMode = UIRenderMode.SCREEN_SPACE;

    // Widgets directly in this layer (they can have children of their own)
    private List<UIWidgetDef> widgets = new ArrayList<>();

    public UILayerDef(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
    }

    public UILayerDef(String id, String name) {
        this.id = id;
        this.name = name;
    }

    // --- Getters/Setters ---

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }

    public int getZOrder() { return zOrder; }
    public void setZOrder(int zOrder) { this.zOrder = zOrder; }

    public UIRenderMode getRenderMode() { return renderMode; }
    public void setRenderMode(UIRenderMode renderMode) { this.renderMode = renderMode; }

    public List<UIWidgetDef> getWidgets() { return widgets; }
    public void addWidget(UIWidgetDef widget) { widgets.add(widget); }
    public void removeWidget(UIWidgetDef widget) { widgets.remove(widget); }

    /**
     * Finds a widget by name, searching recursively through all children.
     * @return the widget, or null if not found
     */
    public UIWidgetDef findWidgetByName(String widgetName) {
        for (UIWidgetDef w : widgets) {
            UIWidgetDef found = findWidgetRecursive(w, widgetName);
            if (found != null) return found;
        }
        return null;
    }

    private UIWidgetDef findWidgetRecursive(UIWidgetDef widget, String widgetName) {
        if (widget.getName().equals(widgetName)) return widget;
        for (UIWidgetDef child : widget.getChildren()) {
            UIWidgetDef found = findWidgetRecursive(child, widgetName);
            if (found != null) return found;
        }
        return null;
    }

    // --- Serialization ---

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("name", name);
        json.put("visible", visible);
        json.put("zOrder", zOrder);
        json.put("renderMode", renderMode.name());

        JSONArray widgetsArr = new JSONArray();
        for (UIWidgetDef w : widgets) {
            widgetsArr.put(w.toJSON());
        }
        json.put("widgets", widgetsArr);

        return json;
    }

    public static UILayerDef fromJSON(JSONObject json) {
        String id = json.getString("id");
        String name = json.getString("name");

        UILayerDef layer = new UILayerDef(id, name);
        layer.visible = json.optBoolean("visible", true);
        layer.zOrder = json.optInt("zOrder", 0);
        layer.renderMode = UIRenderMode.valueOf(json.optString("renderMode", "SCREEN_SPACE"));

        if (json.has("widgets")) {
            JSONArray widgetsArr = json.getJSONArray("widgets");
            for (int i = 0; i < widgetsArr.length(); i++) {
                layer.widgets.add(UIWidgetDef.fromJSON(widgetsArr.getJSONObject(i)));
            }
        }

        return layer;
    }
}
