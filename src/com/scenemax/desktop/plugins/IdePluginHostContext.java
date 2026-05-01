package com.scenemax.desktop.plugins;

import com.scenemax.designer.ImportedModelNormalizer;
import com.scenemax.desktop.AppDB;
import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.SceneMaxProject;
import com.scenemax.desktop.Util;
import com.scenemaxeng.common.types.SceneMaxAssetProvider;
import com.scenemaxeng.common.types.SceneMaxPluginAction;
import com.scenemaxeng.common.types.SceneMaxPluginContext;
import com.scenemaxeng.common.types.SceneMaxPluginImportResult;
import com.scenemaxeng.common.types.SceneMaxPluginView;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.JOptionPane;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class IdePluginHostContext implements SceneMaxPluginContext {
    private final MainApp host;
    private final Map<String, SceneMaxPluginView> views = new LinkedHashMap<>();
    private final Map<String, SceneMaxAssetProvider> assetProviders = new LinkedHashMap<>();

    public IdePluginHostContext(MainApp host) {
        this.host = host;
    }

    @Override
    public String getProjectPath() {
        SceneMaxProject activeProject = Util.getActiveProject();
        if (activeProject == null) {
            return "";
        }
        File scriptsPath = new File(activeProject.getScriptsPath());
        File projectPath = scriptsPath.getParentFile();
        return projectPath == null ? "" : projectPath.getAbsolutePath();
    }

    @Override
    public String getResourcesFolder() {
        return Util.getResourcesFolder();
    }

    @Override
    public String getScriptsFolder() {
        return Util.getScriptsFolder();
    }

    @Override
    public String getSetting(String key, String defaultValue) {
        String value = AppDB.getInstance().getParam(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return value;
    }

    @Override
    public void setSetting(String key, String value) {
        AppDB.getInstance().setParam(key, value == null ? "" : value);
    }

    @Override
    public void registerToolbarAction(SceneMaxPluginAction action) {
        host.addPluginToolbarAction(action, this);
    }

    @Override
    public void registerMenuAction(String menuPath, SceneMaxPluginAction action) {
        host.addPluginMenuAction(menuPath, action, this);
    }

    @Override
    public void registerView(SceneMaxPluginView view) {
        views.put(view.getId(), view);
    }

    @Override
    public void registerAssetProvider(SceneMaxAssetProvider provider) {
        assetProviders.put(provider.getId(), provider);
    }

    @Override
    public void openView(String viewId) {
        SceneMaxPluginView view = views.get(viewId);
        if (view == null) {
            showStatus("Plugin view not found: " + viewId);
            return;
        }
        host.openPluginView("plugin-view-" + viewId, view.getTitle(), view.createComponent(this));
    }

    @Override
    public SceneMaxPluginImportResult importModelAsset(File modelFile, String requestedName, JSONObject metadata) throws IOException {
        if (modelFile == null || !modelFile.isFile()) {
            throw new IOException("Model file does not exist.");
        }
        String resourcesFolder = getResourcesFolder();
        if (resourcesFolder == null || resourcesFolder.isEmpty()) {
            throw new IOException("No active project resources folder is available.");
        }

        File modelsRoot = new File(resourcesFolder, "Models");
        if (!modelsRoot.exists() && !modelsRoot.mkdirs()) {
            throw new IOException("Could not create models folder: " + modelsRoot.getAbsolutePath());
        }

        String assetName = uniqueAssetName(modelsRoot, requestedName);
        File destDir = new File(modelsRoot, assetName);
        if (!destDir.mkdirs()) {
            throw new IOException("Could not create model folder: " + destDir.getAbsolutePath());
        }

        File importedFile;
        if (modelFile.getName().toLowerCase(Locale.ROOT).endsWith(".glb")) {
            FileUtils.copyFileToDirectory(modelFile, destDir);
            importedFile = new File(destDir, modelFile.getName());
        } else {
            FileUtils.copyDirectory(modelFile.getParentFile(), destDir);
            importedFile = new File(destDir, modelFile.getName());
        }

        try {
            ImportedModelNormalizer.normalize(importedFile.toPath());
        } catch (IOException normalizeError) {
            System.err.println("[PluginImport] Imported model normalization skipped: " + normalizeError.getMessage());
        }

        String assetPath = "Models/" + assetName + "/" + importedFile.getName();
        registerModel(resourcesFolder, assetName, assetPath, metadata);
        refreshProject();
        return new SceneMaxPluginImportResult(assetName, assetPath, importedFile.getAbsolutePath());
    }

    @Override
    public void previewModelAsset(File modelFile, String requestedName, JSONObject metadata) throws IOException {
        if (modelFile == null || !modelFile.isFile()) {
            throw new IOException("Model file does not exist.");
        }
        String previewName = requestedName;
        String resourcesFolder = getResourcesFolder();
        if (resourcesFolder != null && !resourcesFolder.isEmpty()) {
            File modelsRoot = new File(resourcesFolder, "Models");
            if (!modelsRoot.exists() && !modelsRoot.mkdirs()) {
                throw new IOException("Could not create models folder: " + modelsRoot.getAbsolutePath());
            }
            previewName = uniqueAssetName(modelsRoot, requestedName);
        } else {
            previewName = sanitizeName(requestedName);
            if (previewName.isEmpty()) {
                previewName = "plugin_asset_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            }
        }

        File previewFile = stagePreviewModelFile(modelFile, previewName);
        File tempRootToDelete = previewFile.getParentFile();
        host.openImport3DModelPreview(
                previewFile,
                buildPreviewLabel(requestedName, metadata),
                previewName,
                tempRootToDelete,
                metadata != null && metadata.optBoolean("isStatic", false),
                imported -> {
                    if (imported) {
                        refreshProject();
                    }
                }
        );
    }

    @Override
    public void refreshProject() {
        host.refreshAssetsMenu();
    }

    @Override
    public void showStatus(String message) {
        JOptionPane.showMessageDialog(host, message, "SceneMax Plugin", JOptionPane.INFORMATION_MESSAGE);
    }

    private void registerModel(String resourcesFolder, String name, String assetPath, JSONObject metadata) throws IOException {
        File indexFile = new File(new File(resourcesFolder, "Models"), "models-ext.json");
        JSONObject root;
        if (indexFile.exists()) {
            String text = FileUtils.readFileToString(indexFile, StandardCharsets.UTF_8);
            root = text.trim().isEmpty() ? new JSONObject("{\"models\":[]}") : new JSONObject(text);
        } else {
            root = new JSONObject("{\"models\":[]}");
        }
        JSONArray models = root.optJSONArray("models");
        if (models == null) {
            models = new JSONArray();
            root.put("models", models);
        }

        JSONObject model = new JSONObject("{\"physics\":{\"character\":{}}}");
        model.put("name", name);
        model.put("path", assetPath);
        model.put("scaleX", 1.0f);
        model.put("scaleY", 1.0f);
        model.put("scaleZ", 1.0f);
        model.put("isStatic", metadata == null ? true : metadata.optBoolean("isStatic", true));
        model.put("transX", 0.0f);
        model.put("transY", 0.0f);
        model.put("transZ", 0.0f);
        model.put("rotateY", 0.0f);
        JSONObject character = model.getJSONObject("physics").getJSONObject("character");
        character.put("calibrateX", 0.0f);
        character.put("calibrateY", 0.0f);
        character.put("calibrateZ", 0.0f);
        character.put("capsuleRadius", 2.0f);
        character.put("capsuleHeight", 2.0f);
        character.put("stepHeight", 0.05f);
        if (metadata != null) {
            model.put("pluginMetadata", metadata);
        }
        models.put(model);
        FileUtils.writeStringToFile(indexFile, root.toString(2), StandardCharsets.UTF_8);
    }

    private String uniqueAssetName(File modelsRoot, String requestedName) {
        String base = sanitizeName(requestedName);
        if (base.isEmpty()) {
            base = "plugin_asset_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }
        String candidate = base;
        int i = 2;
        while (new File(modelsRoot, candidate).exists() || modelNameExists(modelsRoot, candidate)) {
            candidate = base + "_" + i++;
        }
        return candidate;
    }

    private boolean modelNameExists(File modelsRoot, String name) {
        File indexFile = new File(modelsRoot, "models-ext.json");
        if (!indexFile.exists()) {
            return false;
        }
        try {
            JSONObject root = new JSONObject(FileUtils.readFileToString(indexFile, StandardCharsets.UTF_8));
            JSONArray models = root.optJSONArray("models");
            if (models == null) {
                return false;
            }
            for (int i = 0; i < models.length(); i++) {
                if (name.equalsIgnoreCase(models.getJSONObject(i).optString("name", ""))) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private String sanitizeName(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
        sanitized = sanitized.replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        if (!sanitized.isEmpty() && !sanitized.matches("[a-z_].*")) {
            sanitized = "model_" + sanitized;
        }
        if (sanitized.length() > 48) {
            sanitized = sanitized.substring(0, 48).replaceAll("_+$", "");
        }
        return sanitized;
    }

    private File stagePreviewModelFile(File modelFile, String previewName) throws IOException {
        String extension = extensionOf(modelFile.getName());
        File tempRoot = Files.createTempDirectory("scenemax-plugin-preview-").toFile();
        File stagedFile = new File(tempRoot, sanitizeName(previewName) + extension);
        FileUtils.copyFile(modelFile, stagedFile);
        return stagedFile;
    }

    private String extensionOf(String fileName) {
        String name = fileName == null ? "" : fileName;
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            return name.substring(dot).toLowerCase(Locale.ROOT);
        }
        return ".glb";
    }

    private String buildPreviewLabel(String requestedName, JSONObject metadata) {
        if (metadata != null) {
            String provider = metadata.optString("provider", "");
            String prompt = metadata.optString("prompt", "");
            if (!provider.isEmpty() && !prompt.isEmpty()) {
                return provider + ": " + prompt;
            }
        }
        return requestedName == null || requestedName.trim().isEmpty() ? "Plugin model preview" : requestedName;
    }
}
