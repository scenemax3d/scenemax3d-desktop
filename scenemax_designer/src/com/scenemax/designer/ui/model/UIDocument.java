package com.scenemax.designer.ui.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The root document for a UI definition. Saved as .smui (JSON format).
 *
 * Structure:
 *   UIDocument
 *     |- UILayerDef "hud"
 *     |    |- UIWidgetDef (PANEL) "healthBar"
 *     |    |    |- UIWidgetDef (TEXT_VIEW) "healthText"
 *     |    |    |- UIWidgetDef (IMAGE) "healthIcon"
 *     |    |- UIWidgetDef (BUTTON) "pauseBtn"
 *     |- UILayerDef "pauseMenu"
 *          |- UIWidgetDef (PANEL) "menuPanel"
 *               |- UIWidgetDef (BUTTON) "resumeBtn"
 *               |- UIWidgetDef (BUTTON) "quitBtn"
 */
public class UIDocument {

    private static final int FORMAT_VERSION = 1;

    private String filePath;
    private String name;   // the UI system name used in scripting: UI.load "name"

    // Canvas dimensions — the virtual resolution the layout is designed for.
    // At runtime this maps to the actual screen size.
    private float canvasWidth = 1920;
    private float canvasHeight = 1080;

    private List<UILayerDef> layers = new ArrayList<>();

    public UIDocument(String filePath) {
        this.filePath = filePath;
        // Derive name from file name by default
        File f = new File(filePath);
        String fileName = f.getName();
        if (fileName.endsWith(".smui")) {
            this.name = fileName.substring(0, fileName.length() - ".smui".length());
        } else {
            this.name = fileName;
        }
    }

    // --- Getters/Setters ---

    public String getFilePath() { return filePath; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public float getCanvasWidth() { return canvasWidth; }
    public void setCanvasWidth(float canvasWidth) { this.canvasWidth = canvasWidth; }

    public float getCanvasHeight() { return canvasHeight; }
    public void setCanvasHeight(float canvasHeight) { this.canvasHeight = canvasHeight; }

    public List<UILayerDef> getLayers() { return layers; }
    public void addLayer(UILayerDef layer) { layers.add(layer); }
    public void removeLayer(UILayerDef layer) { layers.remove(layer); }

    // --- Query helpers ---

    /**
     * Finds a layer by name.
     */
    public UILayerDef findLayerByName(String layerName) {
        for (UILayerDef layer : layers) {
            if (layer.getName().equals(layerName)) return layer;
        }
        return null;
    }

    /**
     * Resolves a dot-path like "layer1.panel1.button1" into the target widget.
     * @return the widget, or null if the path is invalid
     */
    public UIWidgetDef resolveWidgetPath(String dotPath) {
        String[] parts = dotPath.split("\\.");
        if (parts.length < 2) return null;

        UILayerDef layer = findLayerByName(parts[0]);
        if (layer == null) return null;

        UIWidgetDef current = layer.findWidgetByName(parts[1]);
        for (int i = 2; i < parts.length && current != null; i++) {
            UIWidgetDef next = null;
            for (UIWidgetDef child : current.getChildren()) {
                if (child.getName().equals(parts[i])) {
                    next = child;
                    break;
                }
            }
            current = next;
        }
        return current;
    }

    /**
     * Validates that all widget names are unique across the entire document.
     * @return list of duplicate names (empty if valid)
     */
    public List<String> validateUniqueNames() {
        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();

        for (UILayerDef layer : layers) {
            // Layer names must be unique too
            if (!seen.add(layer.getName())) {
                duplicates.add("layer:" + layer.getName());
            }
            for (UIWidgetDef widget : layer.getWidgets()) {
                collectNames(widget, seen, duplicates);
            }
        }
        return duplicates;
    }

    private void collectNames(UIWidgetDef widget, Set<String> seen, List<String> duplicates) {
        if (!seen.add(widget.getName())) {
            duplicates.add(widget.getName());
        }
        for (UIWidgetDef child : widget.getChildren()) {
            collectNames(child, seen, duplicates);
        }
    }

    // --- Persistence ---

    public void save(File file) throws IOException {
        JSONObject root = new JSONObject();
        root.put("version", FORMAT_VERSION);
        root.put("name", name);
        root.put("canvasWidth", canvasWidth);
        root.put("canvasHeight", canvasHeight);

        JSONArray layersArr = new JSONArray();
        for (UILayerDef layer : layers) {
            layersArr.put(layer.toJSON());
        }
        root.put("layers", layersArr);

        Files.write(file.toPath(), root.toString(2).getBytes(StandardCharsets.UTF_8));
    }

    public static UIDocument load(File file) throws IOException {
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        JSONObject root = new JSONObject(content);

        UIDocument doc = new UIDocument(file.getAbsolutePath());
        doc.name = root.optString("name", doc.name);
        doc.canvasWidth = (float) root.optDouble("canvasWidth", 1920);
        doc.canvasHeight = (float) root.optDouble("canvasHeight", 1080);

        if (root.has("layers")) {
            JSONArray layersArr = root.getJSONArray("layers");
            for (int i = 0; i < layersArr.length(); i++) {
                doc.layers.add(UILayerDef.fromJSON(layersArr.getJSONObject(i)));
            }
        }

        return doc;
    }

    public static UIDocument createEmpty(String filePath) {
        return new UIDocument(filePath);
    }

    public static void writeEmptyFile(File file) throws IOException {
        UIDocument doc = new UIDocument(file.getAbsolutePath());
        // Create a default layer
        UILayerDef defaultLayer = new UILayerDef("layer1");
        doc.addLayer(defaultLayer);
        doc.save(file);
    }

    /**
     * Returns the companion .code file for a given .smui file.
     */
    public static File getCodeFile(File smuiFile) {
        String name = smuiFile.getName();
        String baseName = name.substring(0, name.length() - ".smui".length());
        return new File(smuiFile.getParentFile(), baseName + "_ui.code");
    }

    /**
     * Generates and saves a companion .code file with UI.load and initialization commands.
     */
    public boolean saveCodeFile(File smuiFile) throws IOException {
        File codeFile = getCodeFile(smuiFile);
        boolean isNew = !codeFile.exists();

        StringBuilder sb = new StringBuilder();
        sb.append("// Auto-generated UI code from: ").append(smuiFile.getName()).append("\n");
        sb.append("// Do not edit manually - changes will be overwritten on save.\n\n");
        sb.append("UI.load \"").append(name).append("\"\n");

        // Generate initial visibility state
        for (UILayerDef layer : layers) {
            if (!layer.isVisible()) {
                sb.append("UI.").append(layer.getName()).append(".hide\n");
            }
        }

        Files.write(codeFile.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        return isNew;
    }
}
