package com.scenemax.designer.animation;

import com.scenemax.designer.ImportedModelNormalizer;
import com.github.stephengold.wrench.LwjglAssetLoader;
import com.github.stephengold.wrench.LwjglAssetKey;
import com.jme3.anim.Armature;
import com.jme3.anim.AnimClip;
import com.jme3.anim.AnimComposer;
import com.jme3.anim.AnimTrack;
import com.jme3.anim.SkinningControl;
import com.jme3.anim.TransformTrack;
import com.jme3.animation.AnimControl;
import com.jme3.asset.AssetManager;
import com.jme3.asset.TextureKey;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.export.binary.BinaryExporter;
import com.jme3.material.MatParam;
import com.jme3.material.Material;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.plugins.gltf.GlbLoader;
import com.jme3.scene.plugins.gltf.GltfLoader;
import com.jme3.system.JmeSystem;
import com.jme3.texture.Texture;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AnimationImporter {

    private static final int GLB_MAGIC = 0x46546C67;
    private static final int GLB_JSON_CHUNK = 0x4E4F534A;

    private static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".3ds", ".3mf", ".blend", ".bvh", ".dae", ".fbx", ".glb", ".gltf",
            ".j3o", ".lwo", ".meshxml", ".mesh.xml", ".obj", ".ply", ".stl"
    ));

    public static AnimationImportResult inspect(File sourceFile) throws IOException {
        validateSource(sourceFile);
        if (".fbx".equals(extension(sourceFile.getName()))) {
            return inspectFbxAnimation(sourceFile);
        }

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

        if (".fbx".equals(extension(sourceFile.getName()))) {
            return importFbxAnimation(sourceFile, resourcesFolder, requestedName);
        }

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

    private static AnimationImportResult inspectFbxAnimation(File sourceFile) throws IOException {
        validateSource(sourceFile);
        Fbx2GltfConverter.ConversionResult fbxConversion = Fbx2GltfConverter.convertToGlb(sourceFile);
        try {
            File glbFile = fbxConversion.getGlbFile();
            ImportedModelNormalizer.applyFbx2GltfAxisCorrection(glbFile.toPath());
            Spatial spatial = null;
            List<String> clipNames = collectGltfClipNames(glbFile);
            List<String> clipSummaries = collectGltfClipSummaries(glbFile);
            if (clipNames.isEmpty()) {
                spatial = loadPreparedFile(glbFile);
                clipNames = collectClipNames(spatial);
                clipSummaries = collectClipSummaries(spatial);
            }
            String selectedClipName = clipNames.isEmpty() ? null : clipNames.get(0);
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
                previewFile = new File(previewFolder, "candidate-" + System.nanoTime() + ".glb");
                Files.copy(glbFile.toPath(), previewFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return new AnimationImportResult(null, previewFile == null ? sourceFile : previewFile,
                    clipNames, clipSummaries, selectedClipName, spatial);
        } finally {
            fbxConversion.cleanup();
        }
    }

    private static AnimationImportResult importFbxAnimation(File sourceFile, File resourcesFolder, String requestedName) throws IOException {
        String assetId = sanitizeAssetId(requestedName);
        File animationsRoot = new File(resourcesFolder, "animations");
        File assetFolder = new File(animationsRoot, assetId);
        if (assetFolder.exists()) {
            FileUtils.deleteDirectory(assetFolder);
        }
        if (!assetFolder.mkdirs()) {
            throw new IOException("Failed to create animation folder: " + assetFolder.getAbsolutePath());
        }

        Fbx2GltfConverter.ConversionResult fbxConversion = Fbx2GltfConverter.convertToGlb(sourceFile);
        try {
            File glbFile = fbxConversion.getGlbFile();
            ImportedModelNormalizer.applyFbx2GltfAxisCorrection(glbFile.toPath());
            Spatial imported = null;
            List<String> clipNames = collectGltfClipNames(glbFile);
            List<String> clipSummaries = collectGltfClipSummaries(glbFile);
            if (clipNames.isEmpty()) {
                imported = loadPreparedFile(glbFile);
                clipNames = collectClipNames(imported);
                clipSummaries = collectClipSummaries(imported);
            }
            if (clipNames.isEmpty()) {
                throw new IOException("No animation clips were found in " + sourceFile.getName());
            }
            String selectedClipName = imported == null ? clipNames.get(0) : selectBestClipName(imported);
            if (selectedClipName == null) {
                selectedClipName = clipNames.get(0);
            }

            File runtimeFile = new File(assetFolder, assetId + ".glb");
            Files.copy(glbFile.toPath(), runtimeFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            updateIndex(resourcesFolder, requestedName, resourcesFolder.toPath().relativize(runtimeFile.toPath()).toString().replace("\\", "/"),
                    selectedClipName, sourceFile);

            return new AnimationImportResult(assetFolder, runtimeFile, clipNames, clipSummaries, selectedClipName, imported);
        } finally {
            fbxConversion.cleanup();
        }
    }

    private static List<String> collectGltfClipNames(File glbFile) throws IOException {
        JSONObject gltf = readGlbJson(glbFile);
        JSONArray animations = gltf == null ? null : gltf.optJSONArray("animations");
        List<String> names = new ArrayList<>();
        if (animations == null) {
            return names;
        }
        for (int i = 0; i < animations.length(); i++) {
            JSONObject animation = animations.optJSONObject(i);
            String name = animation == null ? "" : animation.optString("name", "");
            names.add(name == null || name.isBlank() ? "animation_" + i : name);
        }
        return names;
    }

    private static List<String> collectGltfClipSummaries(File glbFile) throws IOException {
        JSONObject gltf = readGlbJson(glbFile);
        JSONArray animations = gltf == null ? null : gltf.optJSONArray("animations");
        List<String> summaries = new ArrayList<>();
        if (animations == null) {
            return summaries;
        }
        for (int i = 0; i < animations.length(); i++) {
            JSONObject animation = animations.optJSONObject(i);
            if (animation == null) {
                continue;
            }
            String name = animation.optString("name", "animation_" + i);
            JSONArray channels = animation.optJSONArray("channels");
            summaries.add(name + " - glTF channels: " + (channels == null ? 0 : channels.length()));
        }
        return summaries;
    }

    private static JSONObject readGlbJson(File glbFile) throws IOException {
        if (glbFile == null || !glbFile.isFile()) {
            return null;
        }
        byte[] bytes = Files.readAllBytes(glbFile.toPath());
        if (bytes.length < 20) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int magic = buffer.getInt();
        buffer.getInt();
        int length = buffer.getInt();
        if (magic != GLB_MAGIC || length != bytes.length) {
            return null;
        }
        while (buffer.remaining() >= 8) {
            int chunkLength = buffer.getInt();
            int chunkType = buffer.getInt();
            if (chunkLength < 0 || chunkLength > buffer.remaining()) {
                return null;
            }
            byte[] chunk = new byte[chunkLength];
            buffer.get(chunk);
            if (chunkType == GLB_JSON_CHUNK) {
                return new JSONObject(new String(trimJsonPadding(chunk), StandardCharsets.UTF_8));
            }
        }
        return null;
    }

    private static byte[] trimJsonPadding(byte[] jsonData) {
        int end = jsonData.length;
        while (end > 0) {
            byte b = jsonData[end - 1];
            if (b == 0 || b == 0x20 || b == '\n' || b == '\r' || b == '\t') {
                end--;
            } else {
                break;
            }
        }
        return Arrays.copyOf(jsonData, end);
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

        Fbx2GltfConverter.ConversionResult fbxConversion = convertFbxToGlb(sourceFile);
        try {
            File loadSource = fbxConversion == null ? sourceFile : fbxConversion.getGlbFile();
            if (fbxConversion != null) {
                ImportedModelNormalizer.applyFbx2GltfAxisCorrection(loadSource.toPath());
            }
            return loadPreparedFile(loadSource);
        } finally {
            if (fbxConversion != null) {
                fbxConversion.cleanup();
            }
        }
    }

    public static void convertModelToJ3o(File sourceFile, File outputFile) throws IOException {
        validateSource(sourceFile);
        if (outputFile == null) {
            throw new IOException("No output file was provided for converted model.");
        }
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create model output folder: " + parent.getAbsolutePath());
        }
        Fbx2GltfConverter.ConversionResult fbxConversion = convertFbxToGlb(sourceFile);
        try {
            File loadSource = fbxConversion == null ? sourceFile : fbxConversion.getGlbFile();
            if (fbxConversion != null) {
                ImportedModelNormalizer.applyFbx2GltfAxisCorrection(loadSource.toPath());
            }
            PreparedSource preparedSource = prepareSourceForLoading(loadSource);
            try {
                Spatial spatial = loadPreparedSource(preparedSource);
                rebuildSkinningControlsForBinaryExport(spatial);
                validateModelConversionPreservedAnimationData(sourceFile, spatial);
                externalizeTexturesForRuntime(spatial, preparedSource, outputFile);
                BinaryExporter.getInstance().save(spatial, outputFile);
            } finally {
                preparedSource.cleanup();
            }
        } finally {
            if (fbxConversion != null) {
                fbxConversion.cleanup();
            }
        }
    }

    public static File convertModelForRuntime(File sourceFile, File outputDir, String modelName) throws IOException {
        validateSource(sourceFile);
        if (outputDir == null) {
            throw new IOException("No output folder was provided for converted model.");
        }
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Failed to create model output folder: " + outputDir.getAbsolutePath());
        }

        String ext = extension(sourceFile.getName());
        if (".fbx".equals(ext)) {
            Fbx2GltfConverter.ConversionResult fbxConversion = Fbx2GltfConverter.convertToGlb(sourceFile);
            try {
                File glbFile = fbxConversion.getGlbFile();
                ImportedModelNormalizer.applyFbx2GltfAxisCorrection(glbFile.toPath());
                PreparedSource preparedSource = prepareSourceForLoading(glbFile);
                try {
                    Spatial spatial = loadPreparedSource(preparedSource);
                    validateModelConversionPreservedAnimationData(sourceFile, spatial);
                } finally {
                    preparedSource.cleanup();
                }

                File runtimeFile = new File(outputDir, sanitizeAssetId(modelName) + ".glb");
                Files.copy(glbFile.toPath(), runtimeFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return runtimeFile;
            } finally {
                fbxConversion.cleanup();
            }
        }

        if (".glb".equals(ext)) {
            PreparedSource preparedSource = prepareSourceForLoading(sourceFile);
            try {
                Spatial spatial = loadPreparedSource(preparedSource);
                if (hasAnimationRuntimeData(spatial)) {
                    File runtimeFile = new File(outputDir, sanitizeAssetId(modelName) + ".glb");
                    Files.copy(sourceFile.toPath(), runtimeFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    return runtimeFile;
                }
            } finally {
                preparedSource.cleanup();
            }
        }

        File runtimeFile = new File(outputDir, sanitizeAssetId(modelName) + ".j3o");
        convertModelToJ3o(sourceFile, runtimeFile);
        return runtimeFile;
    }

    private static Fbx2GltfConverter.ConversionResult convertFbxToGlb(File sourceFile) throws IOException {
        if (!".fbx".equals(extension(sourceFile.getName()))) {
            return null;
        }
        return Fbx2GltfConverter.convertToGlb(sourceFile);
    }

    private static Spatial loadPreparedFile(File sourceFile) throws IOException {
        PreparedSource preparedSource = prepareSourceForLoading(sourceFile);
        try {
            return loadPreparedSource(preparedSource);
        } finally {
            preparedSource.cleanup();
        }
    }

    private static Spatial loadPreparedSource(PreparedSource preparedSource) throws IOException {
        URL configUrl = JmeSystem.getPlatformAssetConfigURL();
        AssetManager assetManager = configUrl == null
                ? JmeSystem.newAssetManager()
                : JmeSystem.newAssetManager(configUrl);
        File locatorRoot = preparedSource.locatorRoot;
        if (locatorRoot != null) {
            assetManager.registerLocator(locatorRoot.getCanonicalPath(), FileLocator.class);
        }
        String sourceExtension = extension(preparedSource.sourceFile.getName());
        if (sourceExtension.equals(".j3o") || sourceExtension.equals(".glb") || sourceExtension.equals(".gltf")) {
            try {
                assetManager.registerLoader(GlbLoader.class, "glb");
                assetManager.registerLoader(GltfLoader.class, "gltf");
                return assetManager.loadModel(preparedSource.assetKey);
            } catch (Exception ex) {
                IOException io = new IOException("Failed to load converted model file: " + preparedSource.sourceFile.getName());
                io.initCause(ex);
                throw io;
            }
        }

        assetManager.registerLoader(LwjglAssetLoader.class,
                "3ds", "3mf", "blend", "bvh", "dae",
                "lwo", "meshxml", "mesh.xml", "obj", "ply", "stl");

        try {
            LwjglAssetKey key = new LwjglAssetKey(preparedSource.assetKey);
            key.setVerboseLogging(false);
            return (Spatial) assetManager.loadAsset(key);
        } catch (Exception ex) {
            IOException io = new IOException("Failed to import animation file: " + preparedSource.sourceFile.getName());
            io.initCause(ex);
            throw io;
        }
    }

    private static void rebuildSkinningControlsForBinaryExport(Spatial spatial) {
        if (spatial == null) {
            return;
        }

        SkinningControl skinningControl = spatial.getControl(SkinningControl.class);
        if (skinningControl != null && skinningControl.getArmature() != null) {
            Armature armature = skinningControl.getArmature();
            Armature rebuiltArmature = new Armature(armature.getJointList().toArray(new com.jme3.anim.Joint[0]));
            SkinningControl rebuiltControl = new SkinningControl(rebuiltArmature);
            rebuiltControl.setHardwareSkinningPreferred(skinningControl.isHardwareSkinningPreferred());
            spatial.removeControl(skinningControl);
            spatial.addControl(rebuiltControl);
        }

        if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                rebuildSkinningControlsForBinaryExport(child);
            }
        }
    }

    private static void externalizeTexturesForRuntime(Spatial spatial, PreparedSource preparedSource, File outputFile) throws IOException {
        File outputParent = outputFile.getParentFile();
        if (spatial == null || outputParent == null || preparedSource.locatorRoot == null) {
            return;
        }
        File texturesDir = new File(outputParent, "textures");
        String textureAssetPrefix = runtimeTextureAssetPrefix(outputFile);
        externalizeTexturesForRuntime(spatial, preparedSource.locatorRoot, texturesDir, textureAssetPrefix, new HashSet<>());
    }

    private static void externalizeTexturesForRuntime(Spatial spatial, File locatorRoot, File texturesDir,
                                                       String textureAssetPrefix, Set<String> usedNames) throws IOException {
        if (spatial instanceof Geometry) {
            Material material = ((Geometry) spatial).getMaterial();
            if (material != null) {
                for (MatParam param : material.getParams()) {
                    Object value = param.getValue();
                    if (value instanceof Texture) {
                        externalizeTexture((Texture) value, locatorRoot, texturesDir, textureAssetPrefix, usedNames);
                    }
                }
            }
        }
        if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                externalizeTexturesForRuntime(child, locatorRoot, texturesDir, textureAssetPrefix, usedNames);
            }
        }
    }

    private static void externalizeTexture(Texture texture, File locatorRoot, File texturesDir,
                                           String textureAssetPrefix, Set<String> usedNames) throws IOException {
        if (texture == null || texture.getKey() == null || texture.getKey().getName() == null) {
            return;
        }
        String keyName = texture.getKey().getName().replace('\\', '/');
        File sourceTexture = new File(locatorRoot, keyName);
        if (!sourceTexture.isFile()) {
            return;
        }
        if (!texturesDir.exists() && !texturesDir.mkdirs()) {
            throw new IOException("Failed to create model texture folder: " + texturesDir.getAbsolutePath());
        }
        String targetName = uniqueTextureName(textureFileName(keyName), usedNames);
        File targetTexture = new File(texturesDir, targetName);
        Files.copy(sourceTexture.toPath(), targetTexture.toPath(), StandardCopyOption.REPLACE_EXISTING);

        TextureKey sourceKey = (TextureKey) texture.getKey();
        TextureKey runtimeKey = new TextureKey(textureAssetPrefix + "/" + targetName, sourceKey.isFlipY());
        runtimeKey.setGenerateMips(sourceKey.isGenerateMips());
        runtimeKey.setAnisotropy(sourceKey.getAnisotropy());
        runtimeKey.setTextureTypeHint(sourceKey.getTextureTypeHint());
        texture.setKey(runtimeKey);
    }

    private static String runtimeTextureAssetPrefix(File outputFile) {
        Path outputParent = outputFile.getParentFile().toPath().toAbsolutePath().normalize();
        Path current = outputParent;
        while (current != null) {
            Path name = current.getFileName();
            if (name != null && "resources".equalsIgnoreCase(name.toString())) {
                return current.relativize(outputParent.resolve("textures")).toString().replace("\\", "/");
            }
            current = current.getParent();
        }
        return "textures";
    }

    private static String uniqueTextureName(String fileName, Set<String> usedNames) {
        String safe = fileName == null || fileName.isBlank() ? "texture.png" : fileName.replaceAll("[^A-Za-z0-9._-]+", "_");
        String base = safe;
        String ext = "";
        int dot = safe.lastIndexOf('.');
        if (dot > 0) {
            base = safe.substring(0, dot);
            ext = safe.substring(dot);
        }
        String candidate = safe;
        int index = 1;
        while (!usedNames.add(candidate.toLowerCase(Locale.ROOT))) {
            candidate = base + "_" + index++ + ext;
        }
        return candidate;
    }

    private static PreparedSource prepareSourceForLoading(File sourceFile) throws IOException {
        File parent = sourceFile.getParentFile();
        if (!".fbx".equals(extension(sourceFile.getName())) || parent == null) {
            return PreparedSource.direct(sourceFile);
        }

        List<String> textureRefs = extractTextureReferences(sourceFile);
        if (textureRefs.isEmpty()) {
            return PreparedSource.direct(sourceFile);
        }
        List<File> textureCandidates = findTextureCandidates(parent);
        if (textureCandidates.isEmpty()) {
            return PreparedSource.direct(sourceFile);
        }

        int depth = Math.max(1, maxLeadingParentReferences(textureRefs));
        File root = Files.createTempDirectory("scenemax-model-import-assets-").toFile();
        File assetDir = root;
        for (int i = 0; i < depth; i++) {
            assetDir = new File(assetDir, "asset" + i);
        }
        if (!assetDir.mkdirs() && !assetDir.isDirectory()) {
            FileUtils.deleteQuietly(root);
            return PreparedSource.direct(sourceFile);
        }

        File stagedSource = new File(assetDir, sourceFile.getName());
        Files.copy(sourceFile.toPath(), stagedSource.toPath(), StandardCopyOption.REPLACE_EXISTING);
        Path rootPath = root.toPath().toAbsolutePath().normalize();
        int copiedTextures = 0;
        for (String textureRef : textureRefs) {
            File localTexture = findTextureForReference(textureRef, textureCandidates);
            if (localTexture == null) {
                continue;
            }
            Path target = assetDir.toPath().resolve(textureRef.replace('\\', '/')).normalize();
            if (!target.toAbsolutePath().startsWith(rootPath)) {
                continue;
            }
            Files.createDirectories(target.getParent());
            Files.copy(localTexture.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            copiedTextures++;
        }

        if (copiedTextures == 0) {
            FileUtils.deleteQuietly(root);
            return PreparedSource.direct(sourceFile);
        }
        String assetKey = root.toPath().relativize(stagedSource.toPath()).toString().replace("\\", "/");
        return new PreparedSource(stagedSource, root, assetKey, root);
    }

    private static List<String> extractTextureReferences(File sourceFile) throws IOException {
        byte[] bytes = Files.readAllBytes(sourceFile.toPath());
        List<String> refs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (byte value : bytes) {
            int ch = value & 0xff;
            if (ch >= 32 && ch < 127) {
                current.append((char) ch);
            } else {
                addTextureReference(current, refs);
                current.setLength(0);
            }
        }
        addTextureReference(current, refs);
        return refs;
    }

    private static void addTextureReference(StringBuilder value, List<String> refs) {
        if (value.length() == 0) {
            return;
        }
        String text = value.toString();
        String lower = text.toLowerCase(Locale.ROOT);
        if ((lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".webp") || lower.endsWith(".tga") || lower.endsWith(".bmp"))
                && !refs.contains(text)) {
            refs.add(text);
        }
    }

    private static List<File> findTextureCandidates(File folder) {
        List<File> result = new ArrayList<>();
        collectTextureCandidates(folder, result);
        return result;
    }

    private static void collectTextureCandidates(File folder, List<File> result) {
        File[] files = folder == null ? null : folder.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                collectTextureCandidates(file, result);
            } else if (isTextureName(file.getName())) {
                result.add(file);
            }
        }
    }

    private static File findTextureForReference(String textureRef, List<File> candidates) {
        String name = textureFileName(textureRef);
        for (File candidate : candidates) {
            if (candidate.getName().equalsIgnoreCase(name)) {
                return candidate;
            }
        }
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private static String textureFileName(String textureRef) {
        if (textureRef == null) {
            return "";
        }
        int slash = Math.max(textureRef.lastIndexOf('/'), textureRef.lastIndexOf('\\'));
        return slash >= 0 ? textureRef.substring(slash + 1) : textureRef;
    }

    private static int maxLeadingParentReferences(List<String> refs) {
        int max = 0;
        for (String ref : refs) {
            String normalized = ref == null ? "" : ref.replace('\\', '/');
            int count = 0;
            while (normalized.startsWith("../")) {
                count++;
                normalized = normalized.substring(3);
            }
            max = Math.max(max, count);
        }
        return max;
    }

    private static boolean isTextureName(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".webp") || lower.endsWith(".tga") || lower.endsWith(".bmp");
    }

    private static void validateModelConversionPreservedAnimationData(File sourceFile, Spatial spatial) throws IOException {
        if (!sourceLooksAnimatedOrRigged(sourceFile)) {
            return;
        }
        if (hasAnimationRuntimeData(spatial)) {
            return;
        }

        throw new IOException("Model import lost the rig/animation data from " + sourceFile.getName()
                + ". The converted J3O would be static and could not play Mixamo animations. "
                + "For FBX sources, the importer converts through FBX2glTF first; this file still converted without usable skinning or animation data. "
                + "Try importing a GLB/glTF export of the same character.");
    }

    private static boolean hasAnimationRuntimeData(Spatial spatial) {
        if (spatial == null) {
            return false;
        }
        if (spatial.getControl(SkinningControl.class) != null
                || spatial.getControl(AnimComposer.class) != null
                || spatial.getControl(AnimControl.class) != null) {
            return true;
        }
        if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                if (hasAnimationRuntimeData(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean sourceLooksAnimatedOrRigged(File sourceFile) throws IOException {
        String ext = extension(sourceFile.getName());
        if (!".fbx".equals(ext) && !".dae".equals(ext) && !".glb".equals(ext) && !".gltf".equals(ext)) {
            return false;
        }

        byte[] bytes = Files.readAllBytes(sourceFile.toPath());
        String text = new String(bytes, StandardCharsets.ISO_8859_1);
        return text.contains("AnimationStack")
                || text.contains("AnimStack")
                || text.contains("AnimationCurve")
                || text.contains("Deformer")
                || text.contains("SubDeformer")
                || text.contains("Cluster")
                || text.contains("LimbNode")
                || text.contains("Skin")
                || text.contains("\"skins\"")
                || text.contains("\"animations\"");
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

    private static class PreparedSource {
        private final File sourceFile;
        private final File locatorRoot;
        private final String assetKey;
        private final File cleanupRoot;

        private PreparedSource(File sourceFile, File locatorRoot, String assetKey, File cleanupRoot) {
            this.sourceFile = sourceFile;
            this.locatorRoot = locatorRoot;
            this.assetKey = assetKey;
            this.cleanupRoot = cleanupRoot;
        }

        private static PreparedSource direct(File sourceFile) {
            return new PreparedSource(sourceFile, sourceFile.getParentFile(), sourceFile.getName(), null);
        }

        private void cleanup() {
            if (cleanupRoot != null) {
                FileUtils.deleteQuietly(cleanupRoot);
            }
        }
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
