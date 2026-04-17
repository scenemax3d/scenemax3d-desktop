package com.scenemax.desktop.ai.tools;
import com.scenemax.designer.SketchfabService;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class SketchfabImportSupport {

    private SketchfabImportSupport() {
    }

    static ImportResult importModel(Path resourcesRoot, String uidOrUrl, String modelName,
                                    String apiToken, boolean replaceExisting) throws Exception {
        if (resourcesRoot == null) {
            throw new IOException("No active resources folder is available.");
        }
        if (apiToken == null || apiToken.trim().isEmpty()) {
            throw new IllegalArgumentException("A Sketchfab API token is required to download models.");
        }

        String uid = extractUid(uidOrUrl);
        JSONObject modelInfo = fetchModelInfo(uid);
        String resolvedName = resolveModelName(modelName, modelInfo, uid);

        if (modelNameExists(resolvedName, "./resources", "models.json")) {
            throw new IllegalArgumentException("Model name already exists as a built-in model: " + resolvedName);
        }

        JSONObject downloadInfo = SketchfabService.getDownloadInfo(uid, apiToken);
        JSONObject preferredFormat = SketchfabService.getPreferredDownloadFormat(downloadInfo);
        if (preferredFormat == null) {
            throw new IOException("Sketchfab did not provide a GLB or glTF download for this model.");
        }

        String downloadUrl = preferredFormat.optString("url", "").trim();
        if (downloadUrl.isEmpty()) {
            throw new IOException("Sketchfab returned an empty download URL.");
        }

        long size = preferredFormat.optLong("size", 0L);
        boolean isGlb = isGlbDownload(downloadInfo, preferredFormat, downloadUrl);
        String extension = isGlb ? ".glb" : ".zip";

        Path modelsRoot = resourcesRoot.resolve("Models");
        Files.createDirectories(modelsRoot);

        Path tempRoot = Files.createTempDirectory(modelsRoot, resolvedName + "-sketchfab-");
        try {
            File downloadFile = tempRoot.resolve("download" + extension).toFile();
            SketchfabService.downloadFile(downloadUrl, downloadFile, size, null);

            File importedSource = downloadFile;
            if (!isGlb) {
                Path extractedFolder = tempRoot.resolve("extracted");
                unzip(downloadFile, extractedFolder.toFile());
                String gltfPath = findGltfFile(extractedFolder.toFile());
                if (gltfPath == null) {
                    throw new IOException("No .gltf or .glb file was found in the downloaded Sketchfab archive.");
                }
                importedSource = new File(gltfPath);
            }

            File srcFile = importedSource;
            File srcDir = srcFile.getParentFile();
            Path destDir = modelsRoot.resolve(resolvedName);

            boolean existsInExt = modelNameExists(resolvedName, resourcesRoot.toString(), "models-ext.json");
            if (existsInExt || Files.exists(destDir)) {
                if (!replaceExisting) {
                    throw new IllegalArgumentException("Model already exists: " + resolvedName);
                }
                removeModelFromIndex(resourcesRoot.resolve("Models").resolve("models-ext.json"), resolvedName);
                if (Files.exists(destDir)) {
                    FileUtils.deleteDirectory(destDir.toFile());
                }
            }

            Files.createDirectories(destDir);
            if (srcFile.getName().toLowerCase(Locale.ROOT).endsWith(".glb")) {
                FileUtils.copyFileToDirectory(srcFile, destDir.toFile());
            } else {
                FileUtils.copyDirectory(srcDir, destDir.toFile());
            }

            String modelPath = "Models/" + resolvedName + "/" + srcFile.getName();
            updateModelsExt(resourcesRoot.resolve("Models").resolve("models-ext.json"), resolvedName, modelPath,
                    1.0f, 1.0f, 1.0f);

            ImportResult result = new ImportResult();
            result.uid = uid;
            result.modelName = resolvedName;
            result.modelPath = modelPath;
            result.modelDirectory = destDir.toAbsolutePath().toString();
            result.absoluteModelFile = destDir.resolve(srcFile.getName()).toAbsolutePath().toString();
            result.format = srcFile.getName().toLowerCase(Locale.ROOT).endsWith(".glb") ? "glb" : "gltf";
            result.viewerUrl = modelInfo != null ? modelInfo.optString("viewerUrl", "") : "";
            result.title = modelInfo != null ? modelInfo.optString("name", resolvedName) : resolvedName;
            result.author = modelInfo != null && modelInfo.optJSONObject("user") != null
                    ? modelInfo.getJSONObject("user").optString("displayName", "")
                    : "";
            result.license = modelInfo != null && modelInfo.optJSONObject("license") != null
                    ? modelInfo.getJSONObject("license").optString("label", "")
                    : "";
            result.thumbnailUrl = modelInfo != null ? SketchfabService.getThumbnailUrl(modelInfo, 256) : "";
            result.scaleX = 1.0f;
            result.scaleY = 1.0f;
            result.scaleZ = 1.0f;
            return result;
        } finally {
            FileUtils.deleteQuietly(tempRoot.toFile());
        }
    }

    static final class ImportResult {
        String uid;
        String modelName;
        String modelPath;
        String modelDirectory;
        String absoluteModelFile;
        String format;
        String viewerUrl;
        String title;
        String author;
        String license;
        String thumbnailUrl;
        float scaleX;
        float scaleY;
        float scaleZ;
    }

    private static JSONObject fetchModelInfo(String uid) {
        try {
            return SketchfabService.getModel(uid);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String extractUid(String uidOrUrl) {
        if (uidOrUrl == null || uidOrUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("A Sketchfab UID or viewer URL is required.");
        }
        String value = uidOrUrl.trim();
        for (int i = 0; i <= value.length() - 32; i++) {
            String candidate = value.substring(i, i + 32);
            if (isHex(candidate)) {
                return candidate.toLowerCase(Locale.ROOT);
            }
        }
        throw new IllegalArgumentException("Could not extract a Sketchfab model UID from: " + uidOrUrl);
    }

    private static boolean isHex(String candidate) {
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            boolean digit = c >= '0' && c <= '9';
            boolean lower = c >= 'a' && c <= 'f';
            boolean upper = c >= 'A' && c <= 'F';
            if (!digit && !lower && !upper) {
                return false;
            }
        }
        return true;
    }

    private static String resolveModelName(String explicitName, JSONObject modelInfo, String uid) {
        String candidate = explicitName;
        if (candidate == null || candidate.trim().isEmpty()) {
            candidate = modelInfo != null ? modelInfo.optString("name", "") : "";
        }
        if (candidate == null || candidate.trim().isEmpty()) {
            candidate = "sketchfab_" + uid.substring(0, Math.min(uid.length(), 8));
        }
        String normalized = sanitizeForFileName(candidate).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            normalized = "sketchfab_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }
        return normalized;
    }

    private static String sanitizeForFileName(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        return text.trim().replaceAll("[^a-zA-Z0-9._-]+", "_");
    }

    private static boolean isGlbDownload(JSONObject downloadInfo, JSONObject preferredFormat, String downloadUrl) {
        if (downloadInfo != null && preferredFormat != null && downloadInfo.has("glb")) {
            return preferredFormat == downloadInfo.optJSONObject("glb");
        }
        return downloadUrl != null && downloadUrl.toLowerCase(Locale.ROOT).contains(".glb");
    }

    private static boolean modelNameExists(String name, String path, String fileName) {
        JSONObject res = readJsonObject(new File(path + "/Models/" + fileName).toPath(), "{\"models\":[]}");
        if (!res.has("models")) {
            return false;
        }
        JSONArray models = res.getJSONArray("models");
        for (int i = 0; i < models.length(); i++) {
            JSONObject model = models.getJSONObject(i);
            if (name.equalsIgnoreCase(model.optString("name"))) {
                return true;
            }
        }
        return false;
    }

    static void updateImportedModelScale(Path resourcesRoot, String name, float scaleX, float scaleY, float scaleZ) throws IOException {
        Path modelsExtPath = resourcesRoot.resolve("Models").resolve("models-ext.json");
        JSONObject res = readJsonObject(modelsExtPath, "{\"models\":[]}");
        JSONArray models = res.optJSONArray("models");
        if (models == null) {
            return;
        }
        for (int i = 0; i < models.length(); i++) {
            JSONObject model = models.getJSONObject(i);
            if (name.equalsIgnoreCase(model.optString("name"))) {
                model.put("scaleX", scaleX);
                model.put("scaleY", scaleY);
                model.put("scaleZ", scaleZ);
                writeJsonObject(modelsExtPath, res);
                return;
            }
        }
    }

    private static void updateModelsExt(Path modelsExtPath, String name, String modelPath,
                                        float scaleX, float scaleY, float scaleZ) throws IOException {
        JSONObject res = readJsonObject(modelsExtPath, "{\"models\":[]}");
        JSONArray models = res.optJSONArray("models");
        if (models == null) {
            models = new JSONArray();
            res.put("models", models);
        }

        JSONObject model = new JSONObject("{\"physics\":{\"character\":{}}}");
        model.put("name", name);
        model.put("path", modelPath);
        model.put("scaleX", scaleX);
        model.put("scaleY", scaleY);
        model.put("scaleZ", scaleZ);
        model.put("transX", 0.0f);
        model.put("transY", 0.0f);
        model.put("transZ", 0.0f);
        model.put("rotateY", 0.0f);
        model.put("isStatic", false);

        JSONObject character = model.getJSONObject("physics").getJSONObject("character");
        character.put("calibrateX", 0.0f);
        character.put("calibrateY", 0.0f);
        character.put("calibrateZ", 0.0f);
        character.put("capsuleRadius", 2.0f);
        character.put("capsuleHeight", 2.0f);
        character.put("stepHeight", 0.05f);

        models.put(model);
        writeJsonObject(modelsExtPath, res);
    }

    private static void removeModelFromIndex(Path modelsExtPath, String name) throws IOException {
        JSONObject res = readJsonObject(modelsExtPath, "{\"models\":[]}");
        JSONArray models = res.optJSONArray("models");
        if (models == null) {
            return;
        }
        for (int i = 0; i < models.length(); i++) {
            JSONObject model = models.getJSONObject(i);
            if (name.equalsIgnoreCase(model.optString("name"))) {
                models.remove(i);
                break;
            }
        }
        writeJsonObject(modelsExtPath, res);
    }

    private static JSONObject readJsonObject(Path path, String defaultJson) {
        try {
            if (!Files.exists(path)) {
                return new JSONObject(defaultJson);
            }
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (content == null || content.trim().isEmpty()) {
                return new JSONObject(defaultJson);
            }
            return new JSONObject(content);
        } catch (Exception ignored) {
            return new JSONObject(defaultJson);
        }
    }

    private static void writeJsonObject(Path path, JSONObject json) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, json.toString(2), StandardCharsets.UTF_8);
    }

    private static String findGltfFile(File folder) {
        File[] files = folder.listFiles();
        if (files == null) {
            return null;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                String nested = findGltfFile(file);
                if (nested != null) {
                    return nested;
                }
            } else {
                String lower = file.getName().toLowerCase(Locale.ROOT);
                if (lower.endsWith(".gltf") || lower.endsWith(".glb")) {
                    return file.getAbsolutePath();
                }
            }
        }
        return null;
    }

    private static void unzip(File source, File destination) throws IOException {
        if (!destination.exists()) {
            destination.mkdirs();
        }
        byte[] buffer = new byte[8192];
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(source))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File outFile = new File(destination, entry.getName());
                String destRoot = destination.getCanonicalPath() + File.separator;
                String outPath = outFile.getCanonicalPath();
                if (!outPath.startsWith(destRoot) && !outPath.equals(destination.getCanonicalPath())) {
                    throw new IOException("Unsafe zip entry path: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    File parent = outFile.getParentFile();
                    if (parent != null) {
                        parent.mkdirs();
                    }
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }
}
