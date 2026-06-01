package com.scenemax.designer.video;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;

public final class VideoImporter {
    private VideoImporter() {
    }

    public static VideoImportResult importVideo(File sourceFile, File resourcesFolder, String requestedName,
                                                VideoMetadata metadata, String previewShape) throws IOException {
        validate(sourceFile, resourcesFolder);
        String assetId = uniqueAssetId(new File(resourcesFolder, "videos"), sanitizeAssetId(requestedName));

        File videosRoot = new File(resourcesFolder, "videos");
        if (!videosRoot.exists() && !videosRoot.mkdirs()) {
            throw new IOException("Failed to create videos folder: " + videosRoot.getAbsolutePath());
        }

        File assetFolder = new File(videosRoot, assetId);
        if (assetFolder.exists()) {
            FileUtils.deleteDirectory(assetFolder);
        }
        if (!assetFolder.mkdirs()) {
            throw new IOException("Failed to create video asset folder: " + assetFolder.getAbsolutePath());
        }

        String extension = extension(sourceFile.getName());
        String targetName = assetId + (extension.isEmpty() ? ".video" : extension);
        File targetFile = new File(assetFolder, targetName);
        FileUtils.copyFile(sourceFile, targetFile);

        JSONObject index = readIndex(new File(videosRoot, "videos-ext.json"));
        JSONArray videos = index.optJSONArray("videos");
        if (videos == null) {
            videos = new JSONArray();
            index.put("videos", videos);
        }

        JSONObject entry = new JSONObject();
        entry.put("name", assetId);
        entry.put("path", resourcesFolder.toPath().relativize(targetFile.toPath()).toString().replace("\\", "/"));
        entry.put("originalImportPath", sourceFile.getAbsolutePath());
        entry.put("importedAt", Instant.now().toString());
        entry.put("previewShape", previewShape == null ? "PANE" : previewShape);
        if (metadata != null) {
            entry.put("width", metadata.width);
            entry.put("height", metadata.height);
            entry.put("frameRate", metadata.frameRate);
            entry.put("durationSeconds", metadata.durationSeconds);
            entry.put("frames", metadata.frames);
            entry.put("audioChannels", metadata.audioChannels);
            entry.put("format", metadata.format);
        }
        videos.put(entry);

        writeIndex(new File(videosRoot, "videos-ext.json"), index);
        return new VideoImportResult(assetId, assetFolder, targetFile);
    }

    public static String sanitizeAssetId(String raw) {
        if (raw == null) {
            raw = "";
        }
        String value = raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\.[^.]+$", "")
                .replaceAll("[^a-z0-9_\\-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        return value.isEmpty() ? "video" : value;
    }

    private static void validate(File sourceFile, File resourcesFolder) throws IOException {
        if (sourceFile == null || !sourceFile.isFile()) {
            throw new IOException("Choose a video file to import.");
        }
        if (resourcesFolder == null) {
            throw new IOException("Project resources folder is not available.");
        }
        if (!resourcesFolder.exists() && !resourcesFolder.mkdirs()) {
            throw new IOException("Failed to create resources folder: " + resourcesFolder.getAbsolutePath());
        }
    }

    private static JSONObject readIndex(File file) throws IOException {
        if (!file.isFile()) {
            return new JSONObject().put("videos", new JSONArray());
        }
        String text = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        if (text.trim().isEmpty()) {
            return new JSONObject().put("videos", new JSONArray());
        }
        return new JSONObject(text);
    }

    private static void writeIndex(File file, JSONObject index) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create index folder: " + parent.getAbsolutePath());
        }
        FileUtils.writeStringToFile(file, index.toString(2), StandardCharsets.UTF_8);
    }

    private static String uniqueAssetId(File videosRoot, String baseAssetId) {
        String base = sanitizeAssetId(baseAssetId);
        String candidate = base;
        int suffix = 2;
        while (new File(videosRoot, candidate).exists() || indexContains(videosRoot, candidate)) {
            candidate = base + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private static boolean indexContains(File videosRoot, String assetId) {
        File indexFile = new File(videosRoot, "videos-ext.json");
        if (!indexFile.isFile()) {
            return false;
        }
        try {
            JSONArray videos = readIndex(indexFile).optJSONArray("videos");
            if (videos == null) {
                return false;
            }
            for (int i = 0; i < videos.length(); i++) {
                JSONObject obj = videos.optJSONObject(i);
                if (obj != null && assetId.equalsIgnoreCase(obj.optString("name"))) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static String extension(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot) : "";
    }
}
