package com.scenemax.designer.animation;

import com.github.stephengold.wrench.LwjglAssetLoader;
import com.github.stephengold.wrench.LwjglAssetKey;
import com.jme3.anim.AnimClip;
import com.jme3.anim.AnimComposer;
import com.jme3.anim.AnimTrack;
import com.jme3.anim.TransformTrack;
import com.jme3.asset.AssetManager;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.export.binary.BinaryExporter;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.system.JmeSystem;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AnimationImporter {

    private static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".3ds", ".3mf", ".blend", ".bvh", ".dae", ".fbx", ".glb", ".gltf",
            ".lwo", ".meshxml", ".mesh.xml", ".obj", ".ply", ".stl"
    ));

    public static AnimationImportResult inspect(File sourceFile) throws IOException {
        Spatial spatial = loadSource(sourceFile);
        List<String> clipNames = collectClipNames(spatial);
        String selectedClipName = selectBestClipName(spatial);
        if (selectedClipName == null && !clipNames.isEmpty()) {
            selectedClipName = clipNames.get(0);
        }
        File previewFile = null;
        if (selectedClipName != null) {
            File previewFolder = new File(System.getProperty("java.io.tmpdir"),
                    "scenemax-animation-preview-" + System.nanoTime());
            if (!previewFolder.mkdirs()) {
                throw new IOException("Failed to create animation preview folder: " + previewFolder.getAbsolutePath());
            }
            previewFile = new File(previewFolder, "candidate-" + System.nanoTime() + ".j3o");
            BinaryExporter.getInstance().save(createRuntimeAnimationResource(spatial, selectedClipName), previewFile);
        }
        return new AnimationImportResult(null, previewFile == null ? sourceFile : previewFile,
                clipNames, collectClipSummaries(spatial), selectedClipName, spatial);
    }

    public static AnimationImportResult importAnimation(File sourceFile, File resourcesFolder, String requestedName) throws IOException {
        return importAnimation(sourceFile, resourcesFolder, requestedName, null);
    }

    public static AnimationImportResult importAnimation(File sourceFile, File resourcesFolder, String requestedName,
                                                       AnimationImportResult inspectedResult) throws IOException {
        validate(sourceFile, resourcesFolder, requestedName);

        String assetId = sanitizeAssetId(requestedName);
        File animationsRoot = new File(resourcesFolder, "animations");
        File assetFolder = new File(animationsRoot, assetId);
        if (assetFolder.exists()) {
            FileUtils.deleteDirectory(assetFolder);
        }
        if (!assetFolder.mkdirs()) {
            throw new IOException("Failed to create animation folder: " + assetFolder.getAbsolutePath());
        }

        Spatial imported = inspectedResult != null && inspectedResult.getImportedSpatial() != null
                ? inspectedResult.getImportedSpatial()
                : loadSource(sourceFile);
        List<String> clipNames = collectClipNames(imported);
        if (clipNames.isEmpty()) {
            throw new IOException("No animation clips were found in " + sourceFile.getName());
        }
        String selectedClipName = selectBestClipName(imported);
        if (selectedClipName == null) {
            selectedClipName = clipNames.get(0);
        }

        File runtimeFile = new File(assetFolder, assetId + ".j3o");
        BinaryExporter.getInstance().save(createRuntimeAnimationResource(imported, selectedClipName), runtimeFile);
        updateIndex(resourcesFolder, requestedName, resourcesFolder.toPath().relativize(runtimeFile.toPath()).toString().replace("\\", "/"),
                selectedClipName, sourceFile);

        return new AnimationImportResult(assetFolder, runtimeFile, clipNames, collectClipSummaries(imported), selectedClipName, imported);
    }

    public static String sanitizeAssetId(String value) {
        if (value == null) {
            return "animation";
        }
        String sanitized = value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
        sanitized = sanitized.replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        return sanitized.isBlank() ? "animation" : sanitized;
    }

    public static boolean isSupported(File file) {
        return file != null && SUPPORTED_EXTENSIONS.contains(extension(file.getName()).toLowerCase(Locale.ROOT));
    }

    private static Spatial loadSource(File sourceFile) throws IOException {
        validateSource(sourceFile);

        AssetManager assetManager = JmeSystem.newAssetManager();
        File parent = sourceFile.getParentFile();
        if (parent != null) {
            assetManager.registerLocator(parent.getCanonicalPath(), FileLocator.class);
        }
        assetManager.registerLoader(LwjglAssetLoader.class,
                "3ds", "3mf", "blend", "bvh", "dae", "fbx", "glb", "gltf",
                "lwo", "meshxml", "mesh.xml", "obj", "ply", "stl");

        try {
            LwjglAssetKey key = new LwjglAssetKey(sourceFile.getName());
            key.setVerboseLogging(true);
            return (Spatial) assetManager.loadAsset(key);
        } catch (Exception ex) {
            IOException io = new IOException("Failed to import animation file: " + sourceFile.getName());
            io.initCause(ex);
            throw io;
        }
    }

    private static List<String> collectClipNames(Spatial spatial) {
        List<String> names = new ArrayList<>();
        collectClipNames(spatial, names);
        return names;
    }

    private static List<String> collectClipSummaries(Spatial spatial) {
        List<String> summaries = new ArrayList<>();
        collectClipSummaries(spatial, summaries);
        return summaries;
    }

    private static void collectClipSummaries(Spatial spatial, List<String> summaries) {
        if (spatial == null) {
            return;
        }
        AnimComposer composer = spatial.getControl(AnimComposer.class);
        if (composer != null) {
            for (AnimClip clip : composer.getAnimClips()) {
                summaries.add(summarizeClip(clip));
            }
        }
        if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                collectClipSummaries(child, summaries);
            }
        }
    }

    private static String selectBestClipName(Spatial spatial) {
        ClipScore best = selectBestClip(spatial, null);
        return best == null ? null : best.name;
    }

    private static Spatial createRuntimeAnimationResource(Spatial imported, String selectedClipName) throws IOException {
        AnimClip selectedClip = findClip(imported, selectedClipName);
        if (selectedClip == null) {
            throw new IOException("Selected animation clip was not found: " + selectedClipName);
        }

        Node resource = new Node("animation_" + selectedClipName);
        AnimComposer composer = new AnimComposer();
        composer.addAnimClip(selectedClip);
        resource.addControl(composer);
        return resource;
    }

    private static AnimClip findClip(Spatial spatial, String clipName) {
        if (spatial == null) {
            return null;
        }

        AnimComposer composer = spatial.getControl(AnimComposer.class);
        if (composer != null && composer.hasAnimClip(clipName)) {
            return composer.getAnimClip(clipName);
        }

        if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                AnimClip clip = findClip(child, clipName);
                if (clip != null) {
                    return clip;
                }
            }
        }

        return null;
    }

    private static ClipScore selectBestClip(Spatial spatial, ClipScore currentBest) {
        if (spatial == null) {
            return currentBest;
        }
        AnimComposer composer = spatial.getControl(AnimComposer.class);
        if (composer != null) {
            for (AnimClip clip : composer.getAnimClips()) {
                ClipScore score = new ClipScore(clip.getName(), movingTrackCount(clip));
                if (currentBest == null || score.movingTracks > currentBest.movingTracks) {
                    currentBest = score;
                }
            }
        }
        if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                currentBest = selectBestClip(child, currentBest);
            }
        }
        return currentBest;
    }

    private static String summarizeClip(AnimClip clip) {
        int totalTracks = clip == null || clip.getTracks() == null ? 0 : clip.getTracks().length;
        int movingTracks = movingTrackCount(clip);
        return clip.getName() + " - moving tracks: " + movingTracks + " / " + totalTracks;
    }

    private static int movingTrackCount(AnimClip clip) {
        if (clip == null || clip.getTracks() == null) {
            return 0;
        }
        int count = 0;
        for (AnimTrack track : clip.getTracks()) {
            if (track instanceof TransformTrack) {
                TransformTrack transformTrack = (TransformTrack) track;
                if (hasRotationMotion(transformTrack.getRotations())
                        || hasTranslationMotion(transformTrack.getTranslations())) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean hasRotationMotion(Quaternion[] rotations) {
        if (rotations == null || rotations.length < 2 || rotations[0] == null) {
            return false;
        }
        Quaternion first = rotations[0];
        for (int i = 1; i < rotations.length; i++) {
            Quaternion current = rotations[i];
            if (current != null && Math.abs(first.dot(current)) < 0.9995f) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTranslationMotion(Vector3f[] translations) {
        if (translations == null || translations.length < 2 || translations[0] == null) {
            return false;
        }
        Vector3f first = translations[0];
        for (int i = 1; i < translations.length; i++) {
            Vector3f current = translations[i];
            if (current != null && first.distanceSquared(current) > 0.000001f) {
                return true;
            }
        }
        return false;
    }

    private static class ClipScore {
        private final String name;
        private final int movingTracks;

        private ClipScore(String name, int movingTracks) {
            this.name = name;
            this.movingTracks = movingTracks;
        }
    }

    private static void collectClipNames(Spatial spatial, List<String> names) {
        if (spatial == null) {
            return;
        }
        AnimComposer composer = spatial.getControl(AnimComposer.class);
        if (composer != null) {
            for (AnimClip clip : composer.getAnimClips()) {
                if (clip != null && clip.getName() != null && !names.contains(clip.getName())) {
                    names.add(clip.getName());
                }
            }
        }
        if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                collectClipNames(child, names);
            }
        }
    }

    private static void updateIndex(File resourcesFolder, String name, String relativePath,
                                    String clipName, File sourceFile) throws IOException {
        File indexFile = new File(resourcesFolder, "animations/animations-ext.json");
        JSONObject root;
        if (indexFile.isFile()) {
            root = new JSONObject(FileUtils.readFileToString(indexFile, StandardCharsets.UTF_8));
        } else {
            root = new JSONObject();
            root.put("animations", new JSONArray());
        }

        JSONArray animations = root.optJSONArray("animations");
        if (animations == null) {
            animations = new JSONArray();
            root.put("animations", animations);
        }

        for (int i = 0; i < animations.length(); i++) {
            JSONObject existing = animations.optJSONObject(i);
            if (existing != null && name.equalsIgnoreCase(existing.optString("name", ""))) {
                animations.remove(i);
                break;
            }
        }

        JSONObject animation = new JSONObject();
        animation.put("name", name);
        animation.put("path", relativePath);
        animation.put("clipName", clipName);
        animation.put("sourcePath", sourceFile.getAbsolutePath());
        animation.put("importedAt", Instant.now().toString());
        animations.put(animation);

        File parent = indexFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        FileUtils.writeStringToFile(indexFile, root.toString(2), StandardCharsets.UTF_8);
    }

    private static void validate(File sourceFile, File resourcesFolder, String requestedName) throws IOException {
        validateSource(sourceFile);
        if (resourcesFolder == null) {
            throw new IOException("Project resources folder is not available.");
        }
        if (requestedName == null || requestedName.trim().isEmpty()) {
            throw new IOException("Please enter an animation name.");
        }
    }

    private static void validateSource(File sourceFile) throws IOException {
        if (sourceFile == null || !sourceFile.isFile()) {
            throw new IOException("Animation source file was not found.");
        }
        if (!isSupported(sourceFile)) {
            throw new IOException("Unsupported animation file type: " + sourceFile.getName());
        }
    }

    private static String extension(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mesh.xml")) {
            return ".mesh.xml";
        }
        int dot = lower.lastIndexOf('.');
        return dot >= 0 ? lower.substring(dot) : "";
    }
}
