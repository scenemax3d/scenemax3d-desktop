package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.ToolPaths;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class UiAuthoringSupport {

    private UiAuthoringSupport() {
    }

    // ----- Path / document loading ------------------------------------------------------------

    static Path resolveUiDocPath(SceneMaxToolContext context, JSONObject arguments) throws IOException {
        String raw = arguments.optString("path", "").trim();
        if (raw.isEmpty()) {
            throw new IOException("path is required and must point to a .smui file.");
        }
        Path path = ToolPaths.resolvePath(context, raw, arguments.optString("base", "workspace"));
        if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".smui")) {
            throw new IOException("This tool only supports .smui UI designer documents.");
        }
        return path;
    }

    static JSONObject readUiDoc(Path path) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        JSONObject root = new JSONObject(content);
        if (!root.has("layers")) {
            root.put("layers", new JSONArray());
        }
        return root;
    }

    static void writeUiDoc(Path path, JSONObject root) throws IOException {
        Files.writeString(path, root.toString(2), StandardCharsets.UTF_8);
    }

    static boolean reloadInIdeIfOpen(SceneMaxToolContext context, Path path, JSONObject arguments) {
        if (!arguments.optBoolean("reload", true)) {
            return false;
        }
        MainApp host = context.getHost();
        if (host == null) {
            return false;
        }
        return host.reloadFileFromDiskForAutomation(path.toFile());
    }

    // ----- Layer lookup -----------------------------------------------------------------------

    static int findLayerIndex(JSONObject root, String nameOrId) {
        if (nameOrId == null || nameOrId.isEmpty()) {
            return -1;
        }
        JSONArray layers = root.optJSONArray("layers");
        if (layers == null) {
            return -1;
        }
        for (int i = 0; i < layers.length(); i++) {
            JSONObject layer = layers.optJSONObject(i);
            if (layer == null) {
                continue;
            }
            if (nameOrId.equals(layer.optString("name")) || nameOrId.equals(layer.optString("id"))) {
                return i;
            }
        }
        return -1;
    }

    static JSONObject findLayer(JSONObject root, String nameOrId) {
        int idx = findLayerIndex(root, nameOrId);
        if (idx < 0) {
            return null;
        }
        return root.getJSONArray("layers").optJSONObject(idx);
    }

    // ----- Widget lookup ----------------------------------------------------------------------

    static final class WidgetHit {
        final JSONObject widget;
        final JSONArray containerArray;   // layer.widgets or parent.children
        final int index;                  // within containerArray
        final JSONObject layer;
        final JSONObject parentWidget;    // null if widget is at layer level
        final String path;                // dot path

        WidgetHit(JSONObject widget, JSONArray containerArray, int index,
                  JSONObject layer, JSONObject parentWidget, String path) {
            this.widget = widget;
            this.containerArray = containerArray;
            this.index = index;
            this.layer = layer;
            this.parentWidget = parentWidget;
            this.path = path;
        }
    }

    /**
     * Resolves "layerName.widgetName.childName..." into a WidgetHit.
     */
    static WidgetHit findWidgetByPath(JSONObject root, String dotPath) {
        if (dotPath == null || dotPath.isEmpty()) {
            return null;
        }
        String[] parts = dotPath.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        JSONObject layer = findLayer(root, parts[0]);
        if (layer == null) {
            return null;
        }
        JSONArray container = layer.optJSONArray("widgets");
        if (container == null) {
            return null;
        }
        JSONObject current = null;
        JSONObject parentWidget = null;
        int index = -1;
        for (int depth = 1; depth < parts.length; depth++) {
            String name = parts[depth];
            int foundIdx = -1;
            for (int i = 0; i < container.length(); i++) {
                JSONObject candidate = container.optJSONObject(i);
                if (candidate != null && name.equals(candidate.optString("name"))) {
                    foundIdx = i;
                    break;
                }
            }
            if (foundIdx < 0) {
                return null;
            }
            if (depth < parts.length - 1) {
                parentWidget = container.getJSONObject(foundIdx);
                JSONArray next = parentWidget.optJSONArray("children");
                if (next == null) {
                    return null;
                }
                container = next;
            } else {
                current = container.getJSONObject(foundIdx);
                index = foundIdx;
            }
        }
        if (current == null) {
            return null;
        }
        return new WidgetHit(current, container, index, layer, parentWidget, dotPath);
    }

    static WidgetHit findWidgetById(JSONObject root, String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        JSONArray layers = root.optJSONArray("layers");
        if (layers == null) {
            return null;
        }
        for (int li = 0; li < layers.length(); li++) {
            JSONObject layer = layers.optJSONObject(li);
            if (layer == null) {
                continue;
            }
            JSONArray widgets = layer.optJSONArray("widgets");
            if (widgets == null) {
                continue;
            }
            WidgetHit hit = searchIdInContainer(widgets, layer, null, id, layer.optString("name") + ".");
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private static WidgetHit searchIdInContainer(JSONArray container, JSONObject layer,
                                                 JSONObject parentWidget, String id, String pathPrefix) {
        for (int i = 0; i < container.length(); i++) {
            JSONObject widget = container.optJSONObject(i);
            if (widget == null) {
                continue;
            }
            String widgetPath = pathPrefix + widget.optString("name");
            if (id.equals(widget.optString("id"))) {
                return new WidgetHit(widget, container, i, layer, parentWidget, widgetPath);
            }
            JSONArray children = widget.optJSONArray("children");
            if (children != null) {
                WidgetHit hit = searchIdInContainer(children, layer, widget, id, widgetPath + ".");
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    /**
     * Walks every widget in the document, invoking the visitor with (widget, layerName, fullPath).
     */
    static void forEachWidget(JSONObject root, WidgetVisitor visitor) {
        JSONArray layers = root.optJSONArray("layers");
        if (layers == null) {
            return;
        }
        for (int li = 0; li < layers.length(); li++) {
            JSONObject layer = layers.optJSONObject(li);
            if (layer == null) {
                continue;
            }
            String layerName = layer.optString("name");
            JSONArray widgets = layer.optJSONArray("widgets");
            if (widgets == null) {
                continue;
            }
            visitContainer(widgets, layerName, layerName, visitor);
        }
    }

    private static void visitContainer(JSONArray container, String layerName, String pathPrefix,
                                       WidgetVisitor visitor) {
        for (int i = 0; i < container.length(); i++) {
            JSONObject widget = container.optJSONObject(i);
            if (widget == null) {
                continue;
            }
            String widgetPath = pathPrefix + "." + widget.optString("name");
            visitor.visit(widget, layerName, widgetPath);
            JSONArray children = widget.optJSONArray("children");
            if (children != null) {
                visitContainer(children, layerName, widgetPath, visitor);
            }
        }
    }

    interface WidgetVisitor {
        void visit(JSONObject widget, String layerName, String path);
    }

    // ----- Naming -----------------------------------------------------------------------------

    static Set<String> collectAllNames(JSONObject root) {
        Set<String> names = new LinkedHashSet<>();
        JSONArray layers = root.optJSONArray("layers");
        if (layers != null) {
            for (int i = 0; i < layers.length(); i++) {
                JSONObject layer = layers.optJSONObject(i);
                if (layer == null) {
                    continue;
                }
                names.add(layer.optString("name"));
            }
        }
        forEachWidget(root, (widget, layerName, path) -> names.add(widget.optString("name")));
        return names;
    }

    static String nextUniqueName(JSONObject root, String baseName) {
        Set<String> taken = collectAllNames(root);
        if (baseName == null || baseName.isEmpty()) {
            baseName = "widget";
        }
        if (!taken.contains(baseName)) {
            return baseName;
        }
        for (int i = 1; i < 10_000; i++) {
            String candidate = baseName + i;
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate unique name for " + baseName);
    }

    /**
     * Rewrites every constraint targetName that matches oldName to newName.
     * Returns the number of rewrites performed.
     */
    static int rewriteConstraintTargets(JSONObject root, String oldName, String newName) {
        if (oldName == null || newName == null || oldName.equals(newName)) {
            return 0;
        }
        int[] count = {0};
        forEachWidget(root, (widget, layerName, path) -> {
            JSONArray constraints = widget.optJSONArray("constraints");
            if (constraints == null) {
                return;
            }
            for (int i = 0; i < constraints.length(); i++) {
                JSONObject c = constraints.optJSONObject(i);
                if (c != null && oldName.equals(c.optString("targetName"))) {
                    c.put("targetName", newName);
                    count[0]++;
                }
            }
        });
        return count[0];
    }

    /**
     * Returns the list of widget paths whose constraints reference the given name.
     */
    static List<String> findConstraintReferrers(JSONObject root, String targetName) {
        List<String> referrers = new ArrayList<>();
        forEachWidget(root, (widget, layerName, path) -> {
            JSONArray constraints = widget.optJSONArray("constraints");
            if (constraints == null) {
                return;
            }
            for (int i = 0; i < constraints.length(); i++) {
                JSONObject c = constraints.optJSONObject(i);
                if (c != null && targetName.equals(c.optString("targetName"))) {
                    referrers.add(path);
                    return;
                }
            }
        });
        return referrers;
    }

    // ----- Sprite / font catalogs -------------------------------------------------------------

    static final class CatalogEntry {
        final String name;
        final String path;
        final int rows;
        final int cols;
        final String scope;    // "global" or "project"

        CatalogEntry(String name, String path, int rows, int cols, String scope) {
            this.name = name;
            this.path = path;
            this.rows = rows;
            this.cols = cols;
            this.scope = scope;
        }

        JSONObject toSpriteJson() {
            return new JSONObject()
                    .put("name", name)
                    .put("path", path == null ? "" : path)
                    .put("rows", rows)
                    .put("cols", cols)
                    .put("scope", scope);
        }

        JSONObject toFontJson() {
            return new JSONObject()
                    .put("name", name)
                    .put("path", path == null ? "" : path)
                    .put("scope", scope);
        }
    }

    static List<CatalogEntry> listSprites(SceneMaxToolContext context) {
        Map<String, CatalogEntry> byName = new LinkedHashMap<>();
        readCatalog(context.getResourcesRoot(), "sprites", "sprites.json", "sprites", "global", byName, true);
        Path projectRoot = context.getActiveProjectRoot();
        Path projectResources = projectRoot == null ? null : projectRoot.resolve("resources");
        readCatalog(projectResources, "sprites", "sprites-ext.json", "sprites", "project", byName, true);
        return new ArrayList<>(byName.values());
    }

    static List<CatalogEntry> listFonts(SceneMaxToolContext context) {
        Map<String, CatalogEntry> byName = new LinkedHashMap<>();
        readCatalog(context.getResourcesRoot(), "fonts", "fonts.json", "fonts", "global", byName, false);
        Path projectRoot = context.getActiveProjectRoot();
        Path projectResources = projectRoot == null ? null : projectRoot.resolve("resources");
        readCatalog(projectResources, "fonts", "fonts-ext.json", "fonts", "project", byName, false);
        return new ArrayList<>(byName.values());
    }

    static Set<String> spriteNames(SceneMaxToolContext context) {
        Set<String> names = new LinkedHashSet<>();
        for (CatalogEntry entry : listSprites(context)) {
            names.add(entry.name);
        }
        return names;
    }

    static Set<String> fontNames(SceneMaxToolContext context) {
        Set<String> names = new LinkedHashSet<>();
        for (CatalogEntry entry : listFonts(context)) {
            names.add(entry.name);
        }
        return names;
    }

    private static void readCatalog(Path resourcesRoot, String subdir, String indexFile, String arrayKey,
                                    String scope, Map<String, CatalogEntry> out, boolean isSprite) {
        if (resourcesRoot == null) {
            return;
        }
        Path index = resourcesRoot.resolve(subdir).resolve(indexFile);
        if (!Files.exists(index)) {
            return;
        }
        try {
            String content = Files.readString(index, StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(content);
            JSONArray items = root.optJSONArray(arrayKey);
            if (items == null) {
                return;
            }
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String name = item.optString("name", "").trim();
                if (name.isEmpty()) {
                    continue;
                }
                String path = item.optString("path", "");
                int rows = isSprite ? item.optInt("rows", 1) : 0;
                int cols = isSprite ? item.optInt("cols", 1) : 0;
                // Project entries override global entries of the same name
                out.put(name, new CatalogEntry(name, path, rows, cols, scope));
            }
        } catch (Exception ignored) {
            // malformed index: treat as empty
        }
    }
}
