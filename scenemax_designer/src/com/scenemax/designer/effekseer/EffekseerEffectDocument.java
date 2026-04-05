package com.scenemax.designer.effekseer;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class EffekseerEffectDocument {

    private static final int FORMAT_VERSION = 1;

    private final String filePath;
    private String name;
    private String assetId;
    private String importedEffectFile = "";
    private String originalFormat = "";
    private String originalImportPath = "";
    private String importedAt = "";
    private boolean loop = true;
    private double playbackSpeed = 1.0;
    private String backgroundMode = "dark";
    private boolean showGround = true;
    private double cameraDistance = 12.0;
    private double cameraYawDeg = 35.0;
    private double cameraPitchDeg = -15.0;
    private double motionForceScale = 1.0;
    private double motionOrbitStrength = 0.0;
    private double motionDamping = 0.0;

    public EffekseerEffectDocument(String filePath) {
        this.filePath = filePath;
        String fileName = new File(filePath).getName();
        if (fileName.endsWith(".smeffectdesign")) {
            this.name = fileName.substring(0, fileName.length() - ".smeffectdesign".length());
            this.assetId = this.name;
        } else {
            this.name = fileName;
            this.assetId = sanitizeAssetId(fileName);
        }
    }

    public String getFilePath() {
        return filePath;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name.trim() : "";
    }

    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId != null ? sanitizeAssetId(assetId) : "";
    }

    public String getImportedEffectFile() {
        return importedEffectFile;
    }

    public void setImportedEffectFile(String importedEffectFile) {
        this.importedEffectFile = importedEffectFile != null ? importedEffectFile.replace("\\", "/") : "";
    }

    public String getOriginalFormat() {
        return originalFormat;
    }

    public void setOriginalFormat(String originalFormat) {
        this.originalFormat = originalFormat != null ? originalFormat : "";
    }

    public String getOriginalImportPath() {
        return originalImportPath;
    }

    public void setOriginalImportPath(String originalImportPath) {
        this.originalImportPath = originalImportPath != null ? originalImportPath : "";
    }

    public String getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(String importedAt) {
        this.importedAt = importedAt != null ? importedAt : "";
    }

    public boolean isLoop() {
        return loop;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    public double getPlaybackSpeed() {
        return playbackSpeed;
    }

    public void setPlaybackSpeed(double playbackSpeed) {
        this.playbackSpeed = playbackSpeed;
    }

    public String getBackgroundMode() {
        return backgroundMode;
    }

    public void setBackgroundMode(String backgroundMode) {
        this.backgroundMode = backgroundMode != null ? backgroundMode : "dark";
    }

    public boolean isShowGround() {
        return showGround;
    }

    public void setShowGround(boolean showGround) {
        this.showGround = showGround;
    }

    public double getCameraDistance() {
        return cameraDistance;
    }

    public void setCameraDistance(double cameraDistance) {
        this.cameraDistance = cameraDistance;
    }

    public double getCameraYawDeg() {
        return cameraYawDeg;
    }

    public void setCameraYawDeg(double cameraYawDeg) {
        this.cameraYawDeg = cameraYawDeg;
    }

    public double getCameraPitchDeg() {
        return cameraPitchDeg;
    }

    public void setCameraPitchDeg(double cameraPitchDeg) {
        this.cameraPitchDeg = cameraPitchDeg;
    }

    public double getMotionForceScale() {
        return motionForceScale;
    }

    public void setMotionForceScale(double motionForceScale) {
        this.motionForceScale = motionForceScale;
    }

    public double getMotionOrbitStrength() {
        return motionOrbitStrength;
    }

    public void setMotionOrbitStrength(double motionOrbitStrength) {
        this.motionOrbitStrength = motionOrbitStrength;
    }

    public double getMotionDamping() {
        return motionDamping;
    }

    public void setMotionDamping(double motionDamping) {
        this.motionDamping = motionDamping;
    }

    public void save(File file) throws IOException {
        JSONObject root = new JSONObject();
        root.put("version", FORMAT_VERSION);
        root.put("name", name);
        root.put("assetId", assetId);

        JSONObject source = new JSONObject();
        source.put("importedEffectFile", importedEffectFile);
        source.put("originalFormat", originalFormat);
        source.put("originalImportPath", originalImportPath);
        source.put("importedAt", importedAt);
        root.put("source", source);

        JSONObject preview = new JSONObject();
        preview.put("loop", loop);
        preview.put("playbackSpeed", playbackSpeed);
        preview.put("backgroundMode", backgroundMode);
        preview.put("showGround", showGround);
        JSONObject camera = new JSONObject();
        camera.put("distance", cameraDistance);
        camera.put("yawDeg", cameraYawDeg);
        camera.put("pitchDeg", cameraPitchDeg);
        preview.put("camera", camera);
        JSONObject motion = new JSONObject();
        motion.put("forceScale", motionForceScale);
        motion.put("orbitStrength", motionOrbitStrength);
        motion.put("damping", motionDamping);
        preview.put("motion", motion);
        root.put("preview", preview);

        Files.write(file.toPath(), root.toString(2).getBytes(StandardCharsets.UTF_8));
    }

    public static EffekseerEffectDocument load(File file) throws IOException {
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        JSONObject root = new JSONObject(content);
        EffekseerEffectDocument doc = new EffekseerEffectDocument(file.getAbsolutePath());
        doc.name = root.optString("name", doc.name);
        doc.assetId = sanitizeAssetId(root.optString("assetId", doc.assetId));

        JSONObject source = root.optJSONObject("source");
        if (source != null) {
            doc.importedEffectFile = source.optString("importedEffectFile", "");
            doc.originalFormat = source.optString("originalFormat", "");
            doc.originalImportPath = source.optString("originalImportPath", "");
            doc.importedAt = source.optString("importedAt", "");
        }

        JSONObject preview = root.optJSONObject("preview");
        if (preview != null) {
            doc.loop = preview.optBoolean("loop", true);
            doc.playbackSpeed = preview.optDouble("playbackSpeed", 1.0);
            doc.backgroundMode = preview.optString("backgroundMode", "dark");
            doc.showGround = preview.optBoolean("showGround", true);
            JSONObject camera = preview.optJSONObject("camera");
            if (camera != null) {
                doc.cameraDistance = camera.optDouble("distance", 12.0);
                doc.cameraYawDeg = camera.optDouble("yawDeg", 35.0);
                doc.cameraPitchDeg = camera.optDouble("pitchDeg", -15.0);
            }
            JSONObject motion = preview.optJSONObject("motion");
            if (motion != null) {
                doc.motionForceScale = motion.optDouble("forceScale", 1.0);
                doc.motionOrbitStrength = motion.optDouble("orbitStrength", 0.0);
                doc.motionDamping = motion.optDouble("damping", 0.0);
            }
        }

        return doc;
    }

    public static EffekseerEffectDocument createImported(String filePath,
                                                         String name,
                                                         String assetId,
                                                         String importedEffectFile,
                                                         String originalFormat,
                                                         String originalImportPath,
                                                         String importedAt) {
        EffekseerEffectDocument doc = new EffekseerEffectDocument(filePath);
        doc.setName(name);
        doc.setAssetId(assetId);
        doc.setImportedEffectFile(importedEffectFile);
        doc.setOriginalFormat(originalFormat);
        doc.setOriginalImportPath(originalImportPath);
        doc.setImportedAt(importedAt);
        return doc;
    }

    public static String sanitizeAssetId(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "effect";
        }
        String normalized = value.trim().replaceAll("[^A-Za-z0-9._-]+", "_");
        normalized = normalized.replaceAll("_+", "_");
        normalized = normalized.replaceAll("^[_\\.]+", "");
        normalized = normalized.replaceAll("[_\\.]+$", "");
        return normalized.isEmpty() ? "effect" : normalized;
    }
}
