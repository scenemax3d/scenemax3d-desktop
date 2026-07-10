package com.scenemax.designer.animation;

import com.jme3.anim.AnimClip;
import com.jme3.anim.AnimComposer;
import com.jme3.anim.AnimTrack;
import com.jme3.anim.Armature;
import com.jme3.anim.SkinningControl;
import com.jme3.anim.TransformTrack;
import com.jme3.animation.AnimControl;
import com.jme3.asset.AssetManager;
import com.jme3.asset.TextureKey;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.export.binary.BinaryExporter;
import com.jme3.material.MatParam;
import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.plugins.gltf.GlbLoader;
import com.jme3.scene.plugins.gltf.GltfLoader;
import com.jme3.system.JmeSystem;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.scenemax.designer.ImportedModelNormalizer;
import jme3utilities.wes.AnimationEdit;
import jme3utilities.wes.TweenTransforms;
import jme3tools.converters.ImageToAwt;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ModelJ3oClipExporter {
    private static final int GLB_MAGIC = 0x46546C67;
    private static final int GLB_JSON_CHUNK = 0x4E4F534A;
    private static final int GLB_BIN_CHUNK = 0x004E4942;

    private ModelJ3oClipExporter() {
    }

    public static void export(File sourceFile, File outputFile, String sourceClipName,
                              List<AnimationFrameRange> frameRanges) throws IOException {
        export(sourceFile, outputFile, sourceClipName, frameRanges, TextureOptimizationOptions.disabled());
    }

    public static void export(File sourceFile, File outputFile, String sourceClipName,
                              List<AnimationFrameRange> frameRanges,
                              TextureOptimizationOptions textureOptions) throws IOException {
        textureOptions = textureOptions == null ? TextureOptimizationOptions.disabled() : textureOptions;
        validate(sourceFile, outputFile, frameRanges);
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create model output folder: " + parent.getAbsolutePath());
        }

        LoadedSource loadedSource = prepareSource(sourceFile);
        try {
            Spatial spatial = loadModel(loadedSource.file);
            if (sourceClipName != null && !sourceClipName.trim().isEmpty()
                    && frameRanges != null && !frameRanges.isEmpty()) {
                addSplitAnimClips(spatial, sourceClipName, frameRanges);
            }
            rebuildSkinningControlsForBinaryExport(spatial);
            validateModelConversionPreservedAnimationData(sourceFile, spatial);
            externalizeTexturesForRuntime(spatial, loadedSource.file.getParentFile(), outputFile, textureOptions);
            BinaryExporter.getInstance().save(spatial, outputFile);
        } finally {
            loadedSource.cleanup();
        }
    }

    private static LoadedSource prepareSource(File sourceFile) throws IOException {
        String ext = extension(sourceFile.getName());
        if (".fbx".equals(ext)) {
            Fbx2GltfConverter.ConversionResult conversion = Fbx2GltfConverter.convertToGlb(sourceFile);
            try {
                ImportedModelNormalizer.applyFbx2GltfAxisCorrection(conversion.getGlbFile().toPath());
                ExpandedGltf expanded = expandGlbForExternalTextures(conversion.getGlbFile());
                return new LoadedSource(expanded.gltfFile, conversion, expanded.rootDir);
            } catch (IOException | RuntimeException ex) {
                conversion.cleanup();
                throw ex;
            }
        }
        if (".glb".equals(ext)) {
            ExpandedGltf expanded = expandGlbForExternalTextures(sourceFile);
            return new LoadedSource(expanded.gltfFile, null, expanded.rootDir);
        }
        return new LoadedSource(sourceFile, null, null);
    }

    private static Spatial loadModel(File sourceFile) throws IOException {
        String ext = extension(sourceFile.getName());
        if (!".glb".equals(ext) && !".gltf".equals(ext) && !".j3o".equals(ext)) {
            throw new IOException("Save As Native Model supports GLB, GLTF, FBX, and J3O source models.");
        }

        URL configUrl = JmeSystem.getPlatformAssetConfigURL();
        AssetManager assetManager = configUrl == null
                ? JmeSystem.newAssetManager()
                : JmeSystem.newAssetManager(configUrl);
        File parent = sourceFile.getParentFile();
        if (parent != null) {
            assetManager.registerLocator(parent.getCanonicalPath(), FileLocator.class);
        }
        File resourcesRoot = findResourcesRoot(sourceFile);
        if (resourcesRoot != null) {
            assetManager.registerLocator(resourcesRoot.getCanonicalPath(), FileLocator.class);
        }
        assetManager.registerLoader(GlbLoader.class, "glb");
        assetManager.registerLoader(GltfLoader.class, "gltf");
        try {
            return assetManager.loadModel(sourceFile.getName());
        } catch (Exception ex) {
            IOException io = new IOException("Failed to load model file: " + sourceFile.getName());
            io.initCause(ex);
            throw io;
        }
    }

    private static void addSplitAnimClips(Spatial spatial, String requestedSourceClipName,
                                          List<AnimationFrameRange> frameRanges) throws IOException {
        String sourceClipName = requestedSourceClipName == null ? "" : requestedSourceClipName.trim();
        AnimComposer composer = findComposerWithClip(spatial, sourceClipName);
        if (composer == null || sourceClipName == null || sourceClipName.isBlank()) {
            throw new IOException("Source animation clip was not found for splitting: " + sourceClipName);
        }

        AnimClip sourceClip = composer.getAnimClip(sourceClipName);
        if (sourceClip == null) {
            throw new IOException("Source animation clip was not found: " + sourceClipName);
        }

        ClipTimeline timeline = new ClipTimeline(sourceClip.getLength(), timesFromClip(sourceClip));
        int maxFrame = timeline.maxFrame();
        Set<String> seen = new LinkedHashSet<>();
        TweenTransforms tweenTransforms = new TweenTransforms();
        for (AnimationFrameRange range : frameRanges) {
            String clipName = range.name.trim();
            String key = clipName.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                throw new IOException("Duplicate split clip name: " + clipName);
            }
            if (clipName.equals(sourceClipName)) {
                throw new IOException("Split clip name matches the source clip: " + clipName);
            }

            int start = Math.max(0, Math.min(range.startFrame, maxFrame));
            int end = Math.max(start, Math.min(range.endFrame, maxFrame));
            if (end <= start) {
                throw new IOException("Frame range for '" + clipName + "' must contain at least 2 frames.");
            }

            float startTime = (float) timeline.timeAtFrame(start);
            float endTime = (float) timeline.timeAtFrame(end);
            if (endTime <= startTime) {
                throw new IOException("Frame range for '" + clipName + "' resolved to an empty time range.");
            }

            if (composer.hasAnimClip(clipName)) {
                composer.removeAnimClip(composer.getAnimClip(clipName));
            }
            if (composer.hasAction(clipName)) {
                composer.removeAction(clipName);
            }
            composer.addAnimClip(AnimationEdit.extractAnimation(sourceClip, startTime, endTime, tweenTransforms, clipName));
        }
    }

    private static AnimComposer findComposerWithClip(Spatial spatial, String clipName) {
        if (spatial == null || clipName == null || clipName.trim().isEmpty()) {
            return null;
        }
        AnimComposer composer = spatial.getControl(AnimComposer.class);
        if (composer != null && composer.hasAnimClip(clipName)) {
            return composer;
        }
        if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                AnimComposer found = findComposerWithClip(child, clipName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static ExpandedGltf expandGlbForExternalTextures(File glbFile) throws IOException {
        GlbData glb = readGlb(glbFile);
        File root = Files.createTempDirectory("scenemax-j3o-gltf-").toFile();
        String baseName = stripExtension(glbFile.getName());
        File binFile = new File(root, baseName + ".bin");
        if (glb.binData != null) {
            Files.write(binFile.toPath(), glb.binData);
            JSONArray buffers = glb.root.optJSONArray("buffers");
            if (buffers != null && buffers.length() > 0) {
                JSONObject buffer = buffers.optJSONObject(0);
                if (buffer != null) {
                    buffer.put("uri", binFile.getName());
                    buffer.put("byteLength", glb.binData.length);
                }
            }
        }

        extractEmbeddedImages(glb, root);

        File gltfFile = new File(root, baseName + ".gltf");
        Files.write(gltfFile.toPath(), glb.root.toString(2).getBytes(StandardCharsets.UTF_8));
        return new ExpandedGltf(gltfFile, root);
    }

    private static void extractEmbeddedImages(GlbData glb, File root) throws IOException {
        JSONArray images = glb.root.optJSONArray("images");
        JSONArray bufferViews = glb.root.optJSONArray("bufferViews");
        if (images == null || bufferViews == null || glb.binData == null) {
            return;
        }

        Set<String> usedNames = new HashSet<>();
        for (int i = 0; i < images.length(); i++) {
            JSONObject image = images.optJSONObject(i);
            if (image == null || image.has("uri") || !image.has("bufferView")) {
                continue;
            }
            JSONObject bufferView = bufferViews.optJSONObject(image.optInt("bufferView", -1));
            if (bufferView == null || bufferView.optInt("buffer", 0) != 0) {
                continue;
            }
            int offset = bufferView.optInt("byteOffset", 0);
            int length = bufferView.optInt("byteLength", 0);
            if (offset < 0 || length <= 0 || offset + length > glb.binData.length) {
                continue;
            }

            String fileName = uniqueTextureName(
                    safeTextureBaseName(image.optString("name", "image_" + i)) + extensionForMime(image.optString("mimeType", "")),
                    usedNames);
            Files.write(new File(root, fileName).toPath(), Arrays.copyOfRange(glb.binData, offset, offset + length));
            image.put("uri", fileName);
            image.remove("bufferView");
        }
    }

    private static GlbData readGlb(File glbFile) throws IOException {
        byte[] bytes = Files.readAllBytes(glbFile.toPath());
        if (bytes.length < 20) {
            throw new IOException("Invalid GLB file: " + glbFile.getName());
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int magic = buffer.getInt();
        buffer.getInt();
        int length = buffer.getInt();
        if (magic != GLB_MAGIC || length != bytes.length) {
            throw new IOException("Invalid GLB header: " + glbFile.getName());
        }

        JSONObject root = null;
        byte[] binData = null;
        while (buffer.remaining() >= 8) {
            int chunkLength = buffer.getInt();
            int chunkType = buffer.getInt();
            if (chunkLength < 0 || chunkLength > buffer.remaining()) {
                throw new IOException("Invalid GLB chunk in " + glbFile.getName());
            }
            byte[] chunk = new byte[chunkLength];
            buffer.get(chunk);
            if (chunkType == GLB_JSON_CHUNK) {
                root = new JSONObject(new String(trimJsonPadding(chunk), StandardCharsets.UTF_8));
            } else if (chunkType == GLB_BIN_CHUNK) {
                binData = chunk;
            }
        }
        if (root == null) {
            throw new IOException("GLB has no JSON chunk: " + glbFile.getName());
        }
        return new GlbData(root, binData);
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

    private static String extensionForMime(String mimeType) {
        String lower = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        if (lower.contains("jpeg") || lower.contains("jpg")) {
            return ".jpg";
        }
        if (lower.contains("webp")) {
            return ".webp";
        }
        return ".png";
    }

    private static String safeTextureBaseName(String name) {
        String safe = name == null || name.isBlank() ? "texture" : name.replaceAll("[^A-Za-z0-9._-]+", "_");
        safe = stripExtension(safe).replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        return safe.isBlank() ? "texture" : safe;
    }

    private static String stripExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "model";
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mesh.xml")) {
            return fileName.substring(0, fileName.length() - ".mesh.xml".length());
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static float[] timesFromClip(AnimClip clip) {
        if (clip == null || clip.getTracks() == null) {
            return null;
        }
        float[] bestTimes = null;
        for (AnimTrack track : clip.getTracks()) {
            if (track instanceof TransformTrack) {
                float[] times = ((TransformTrack) track).getTimes();
                if (times != null && (bestTimes == null || times.length > bestTimes.length)) {
                    bestTimes = times;
                }
            }
        }
        return bestTimes;
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

    private static void externalizeTexturesForRuntime(Spatial spatial, File locatorRoot, File outputFile,
                                                       TextureOptimizationOptions textureOptions) throws IOException {
        File outputParent = outputFile.getParentFile();
        if (spatial == null || outputParent == null || locatorRoot == null) {
            return;
        }
        File texturesDir = textureOutputFolder(outputFile);
        String textureAssetPrefix = runtimeTextureAssetPrefix(outputFile);
        externalizeTexturesForRuntime(spatial, locatorRoot, texturesDir, textureAssetPrefix,
                new HashSet<>(), new HashMap<>(), textureOptions);
    }

    private static void externalizeTexturesForRuntime(Spatial spatial, File locatorRoot, File texturesDir,
                                                       String textureAssetPrefix, Set<String> usedNames,
                                                       Map<String, String> copiedTextures,
                                                       TextureOptimizationOptions textureOptions) throws IOException {
        if (spatial instanceof Geometry) {
            Material material = ((Geometry) spatial).getMaterial();
            if (material != null) {
                for (MatParam param : material.getParams()) {
                    Object value = param.getValue();
                    if (value instanceof Texture) {
                        externalizeTexture((Texture) value, locatorRoot, texturesDir, textureAssetPrefix,
                                usedNames, copiedTextures, textureOptions, param.getName());
                    }
                }
            }
        }
        if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                externalizeTexturesForRuntime(child, locatorRoot, texturesDir, textureAssetPrefix,
                        usedNames, copiedTextures, textureOptions);
            }
        }
    }

    private static void externalizeTexture(Texture texture, File locatorRoot, File texturesDir,
                                           String textureAssetPrefix, Set<String> usedNames,
                                           Map<String, String> copiedTextures,
                                           TextureOptimizationOptions textureOptions,
                                           String materialParamName) throws IOException {
        if (texture == null) {
            return;
        }
        if (!texturesDir.exists() && !texturesDir.mkdirs()) {
            throw new IOException("Failed to create model texture folder: " + texturesDir.getAbsolutePath());
        }

        TextureKey sourceTextureKey = texture.getKey() instanceof TextureKey ? (TextureKey) texture.getKey() : null;
        String targetName;
        if (sourceTextureKey != null && sourceTextureKey.getName() != null) {
            String keyName = sourceTextureKey.getName().replace('\\', '/');
            File sourceTexture = resolveTextureFile(locatorRoot, keyName);
            if (!sourceTexture.isFile()) {
                return;
            }
            String sourceKey = sourceTexture.getCanonicalPath();
            targetName = copiedTextures.get(sourceKey);
            if (targetName == null) {
                targetName = uniqueTextureName(optimizedTextureFileName(textureFileName(keyName), sourceTexture, textureOptions), usedNames);
                File targetTexture = new File(texturesDir, targetName);
                copyTexture(sourceTexture, targetTexture, targetName, textureOptions);
                deleteSiblingTextureVariants(targetTexture);
                copiedTextures.put(sourceKey, targetName);
            }
        } else {
            Image image = texture.getImage();
            if (image == null || image.getData() == null || image.getData().isEmpty()) {
                return;
            }
            String sourceKey = "embedded:" + System.identityHashCode(image) + ":" + materialParamName;
            targetName = copiedTextures.get(sourceKey);
            if (targetName == null) {
                BufferedImage bufferedImage = ImageToAwt.convert(image, false, true, 0);
                if (bufferedImage == null) {
                    return;
                }
                targetName = uniqueTextureName(embeddedTextureFileName(materialParamName, bufferedImage, textureOptions), usedNames);
                File targetTexture = new File(texturesDir, targetName);
                writeBufferedTexture(bufferedImage, targetTexture, targetName, textureOptions);
                copiedTextures.put(sourceKey, targetName);
            }
        }

        TextureKey runtimeKey = new TextureKey(textureAssetPrefix + "/" + targetName,
                sourceTextureKey != null && sourceTextureKey.isFlipY());
        runtimeKey.setGenerateMips(sourceTextureKey != null && sourceTextureKey.isGenerateMips());
        if (sourceTextureKey != null) {
            runtimeKey.setAnisotropy(sourceTextureKey.getAnisotropy());
            runtimeKey.setTextureTypeHint(sourceTextureKey.getTextureTypeHint());
        } else {
            runtimeKey.setTextureTypeHint(texture.getType());
        }
        texture.setKey(runtimeKey);
    }

    private static String embeddedTextureFileName(String materialParamName, BufferedImage image,
                                                  TextureOptimizationOptions textureOptions) {
        String base = materialParamName == null || materialParamName.isBlank()
                ? "embedded_texture"
                : materialParamName;
        String safe = base.replaceAll("[^A-Za-z0-9._-]+", "_");
        boolean canUseJpeg = textureOptions != null && textureOptions.enabled
                && textureOptions.convertColorPngToJpeg
                && !shouldKeepLossless(safe)
                && !image.getColorModel().hasAlpha();
        return safe + (canUseJpeg ? ".jpg" : ".png");
    }

    private static String optimizedTextureFileName(String fileName, File sourceTexture,
                                                   TextureOptimizationOptions textureOptions) {
        if (textureOptions == null || !textureOptions.enabled || !textureOptions.convertColorPngToJpeg) {
            return fileName;
        }
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".png") || shouldKeepLossless(fileName) || imageHasAlpha(sourceTexture)) {
            return fileName;
        }
        return fileName.substring(0, fileName.length() - 4) + ".jpg";
    }

    private static void copyTexture(File sourceTexture, File targetTexture, String targetName,
                                    TextureOptimizationOptions textureOptions) throws IOException {
        boolean sameFile = sourceTexture.getCanonicalFile().equals(targetTexture.getCanonicalFile());
        File writeTarget = sameFile ? tempSibling(targetTexture) : targetTexture;
        if (textureOptions == null || !textureOptions.enabled || !isImageTexture(sourceTexture.getName())) {
            if (!sameFile) {
                Files.copy(sourceTexture.toPath(), targetTexture.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return;
        }
        BufferedImage image = ImageIO.read(sourceTexture);
        if (image == null) {
            if (!sameFile) {
                Files.copy(sourceTexture.toPath(), targetTexture.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return;
        }
        writeBufferedTexture(image, writeTarget, targetName, textureOptions);
        if (sameFile) {
            Files.move(writeTarget.toPath(), targetTexture.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void writeBufferedTexture(BufferedImage image, File targetTexture, String targetName,
                                             TextureOptimizationOptions textureOptions) throws IOException {
        BufferedImage scaled = scaleImage(image, textureOptions.maxDimension);
        String lowerTarget = targetName.toLowerCase(Locale.ROOT);
        boolean writeJpeg = lowerTarget.endsWith(".jpg") || lowerTarget.endsWith(".jpeg");
        boolean changedSize = scaled.getWidth() != image.getWidth() || scaled.getHeight() != image.getHeight();
        if (writeJpeg) {
            writeJpeg(toRgbImage(scaled), targetTexture, textureOptions.jpegQuality);
        } else {
            ImageIO.write(scaled, "png", targetTexture);
        }
    }

    private static BufferedImage scaleImage(BufferedImage source, int maxDimension) {
        if (maxDimension <= 0 || Math.max(source.getWidth(), source.getHeight()) <= maxDimension) {
            return source;
        }
        double ratio = maxDimension / (double) Math.max(source.getWidth(), source.getHeight());
        int width = Math.max(1, (int) Math.round(source.getWidth() * ratio));
        int height = Math.max(1, (int) Math.round(source.getHeight() * ratio));
        BufferedImage scaled = new BufferedImage(width, height,
                source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return scaled;
    }

    private static BufferedImage toRgbImage(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    private static void writeJpeg(BufferedImage image, File targetFile, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam params = writer.getDefaultWriteParam();
        if (params.canWriteCompressed()) {
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(Math.max(0.05f, Math.min(1f, quality)));
        }
        try (ImageOutputStream output = ImageIO.createImageOutputStream(targetFile)) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
    }

    private static boolean isImageTexture(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
    }

    private static boolean shouldKeepLossless(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return lower.contains("normal") || lower.contains("_nrm") || lower.contains("-nrm")
                || lower.contains("bump") || lower.contains("height") || lower.contains("alpha")
                || lower.contains("opacity") || lower.contains("mask");
    }

    private static boolean imageHasAlpha(File file) {
        try {
            BufferedImage image = ImageIO.read(file);
            return image != null && image.getColorModel().hasAlpha();
        } catch (IOException ignored) {
            return true;
        }
    }

    private static File tempSibling(File targetTexture) throws IOException {
        File parent = targetTexture.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create model texture folder: " + parent.getAbsolutePath());
        }
        return File.createTempFile(targetTexture.getName() + ".", ".tmp", parent);
    }

    private static void deleteSiblingTextureVariants(File targetTexture) throws IOException {
        String name = targetTexture.getName();
        int dot = name.lastIndexOf('.');
        if (dot <= 0) {
            return;
        }
        String base = name.substring(0, dot);
        String[] extensions = {".png", ".jpg", ".jpeg"};
        for (String extension : extensions) {
            File sibling = new File(targetTexture.getParentFile(), base + extension);
            if (sibling.isFile() && !sibling.getCanonicalFile().equals(targetTexture.getCanonicalFile())
                    && !sibling.getName().toLowerCase(Locale.ROOT).endsWith(".tmp")) {
                Files.deleteIfExists(sibling.toPath());
            }
        }
    }

    private static String runtimeTextureAssetPrefix(File outputFile) {
        Path outputParent = outputFile.getParentFile().toPath().toAbsolutePath().normalize();
        Path texturePath = outputParent.resolve("textures").resolve(stripExtension(outputFile.getName()));
        Path current = outputParent;
        while (current != null) {
            Path name = current.getFileName();
            if (name != null && "resources".equalsIgnoreCase(name.toString())) {
                return current.relativize(texturePath).toString().replace("\\", "/");
            }
            current = current.getParent();
        }
        return "textures/" + stripExtension(outputFile.getName());
    }

    private static File textureOutputFolder(File outputFile) {
        return new File(new File(outputFile.getParentFile(), "textures"), stripExtension(outputFile.getName()));
    }

    private static File resolveTextureFile(File locatorRoot, String keyName) throws IOException {
        File direct = new File(locatorRoot, keyName);
        if (direct.isFile()) {
            return direct;
        }
        File resourcesRoot = findResourcesRoot(locatorRoot);
        if (resourcesRoot != null) {
            File projectRelative = new File(resourcesRoot, keyName);
            if (projectRelative.isFile()) {
                return projectRelative;
            }
        }
        return direct;
    }

    private static File findResourcesRoot(File file) {
        File current = file == null ? null : file.getAbsoluteFile();
        if (current != null && current.isFile()) {
            current = current.getParentFile();
        }
        while (current != null) {
            if ("resources".equalsIgnoreCase(current.getName())) {
                return current;
            }
            current = current.getParentFile();
        }
        return null;
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

    private static String textureFileName(String textureRef) {
        if (textureRef == null) {
            return "";
        }
        int slash = Math.max(textureRef.lastIndexOf('/'), textureRef.lastIndexOf('\\'));
        return slash >= 0 ? textureRef.substring(slash + 1) : textureRef;
    }

    private static void validateModelConversionPreservedAnimationData(File sourceFile, Spatial spatial) throws IOException {
        if (!sourceLooksAnimatedOrRigged(sourceFile)) {
            return;
        }
        if (hasAnimationRuntimeData(spatial)) {
            return;
        }
        throw new IOException("Model import lost the rig/animation data from " + sourceFile.getName()
                + ". The converted J3O would be static and could not play animations.");
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
        if (!".fbx".equals(ext) && !".glb".equals(ext) && !".gltf".equals(ext)) {
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

    private static void validate(File sourceFile, File outputFile, List<AnimationFrameRange> frameRanges) throws IOException {
        if (sourceFile == null || !sourceFile.isFile()) {
            throw new IOException("Model source file was not found.");
        }
        if (outputFile == null) {
            throw new IOException("No output file was provided for converted model.");
        }
        String ext = extension(sourceFile.getName());
        if (!".glb".equals(ext) && !".gltf".equals(ext) && !".fbx".equals(ext) && !".j3o".equals(ext)) {
            throw new IOException("Save As Native Model supports GLB, GLTF, FBX, and J3O source models.");
        }
        if (frameRanges != null) {
            for (AnimationFrameRange range : frameRanges) {
                if (range == null || range.name == null || range.name.trim().isEmpty()) {
                    throw new IOException("Every J3O split clip must have a name.");
                }
            }
        }
    }

    private static String extension(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        return dot >= 0 ? lower.substring(dot) : "";
    }

    public static class AnimationFrameRange {
        public final String name;
        public final int startFrame;
        public final int endFrame;

        public AnimationFrameRange(String name, int startFrame, int endFrame) {
            this.name = name;
            this.startFrame = startFrame;
            this.endFrame = endFrame;
        }
    }

    public static class TextureOptimizationOptions {
        public final boolean enabled;
        public final int maxDimension;
        public final float jpegQuality;
        public final boolean convertColorPngToJpeg;

        private TextureOptimizationOptions(boolean enabled, int maxDimension, float jpegQuality,
                                           boolean convertColorPngToJpeg) {
            this.enabled = enabled;
            this.maxDimension = Math.max(0, maxDimension);
            this.jpegQuality = Math.max(0.05f, Math.min(1f, jpegQuality));
            this.convertColorPngToJpeg = convertColorPngToJpeg;
        }

        public static TextureOptimizationOptions disabled() {
            return new TextureOptimizationOptions(false, 0, 0.82f, false);
        }

        public static TextureOptimizationOptions enabled(int maxDimension, int jpegQualityPercent,
                                                         boolean convertColorPngToJpeg) {
            return new TextureOptimizationOptions(true, maxDimension,
                    Math.max(1, Math.min(100, jpegQualityPercent)) / 100f,
                    convertColorPngToJpeg);
        }

        public String summary() {
            if (!enabled) {
                return "off";
            }
            String size = maxDimension > 0 ? "max " + maxDimension + "px" : "original dimensions";
            return size + ", JPEG " + Math.round(jpegQuality * 100f) + "%"
                    + (convertColorPngToJpeg ? ", color PNG to JPEG" : "");
        }
    }

    private static class LoadedSource {
        final File file;
        final Fbx2GltfConverter.ConversionResult conversion;
        final File cleanupRoot;

        LoadedSource(File file, Fbx2GltfConverter.ConversionResult conversion, File cleanupRoot) {
            this.file = file;
            this.conversion = conversion;
            this.cleanupRoot = cleanupRoot;
        }

        void cleanup() {
            if (conversion != null) {
                conversion.cleanup();
            }
            if (cleanupRoot != null) {
                FileUtils.deleteQuietly(cleanupRoot);
            }
        }
    }

    private static class ExpandedGltf {
        final File gltfFile;
        final File rootDir;

        ExpandedGltf(File gltfFile, File rootDir) {
            this.gltfFile = gltfFile;
            this.rootDir = rootDir;
        }
    }

    private static class GlbData {
        final JSONObject root;
        final byte[] binData;

        GlbData(JSONObject root, byte[] binData) {
            this.root = root;
            this.binData = binData;
        }
    }

    private static class ClipTimeline {
        private static final double FALLBACK_ANIMATION_FPS = 24.0;
        private final double length;
        private final float[] times;

        ClipTimeline(double length, float[] times) {
            this.length = length;
            this.times = times == null || times.length == 0 ? null : times;
        }

        int maxFrame() {
            if (times != null) {
                return Math.max(0, times.length - 1);
            }
            return Math.max(0, (int) Math.round(length * FALLBACK_ANIMATION_FPS));
        }

        double timeAtFrame(double frame) {
            if (times == null) {
                return length <= 0 ? 0 : frame / FALLBACK_ANIMATION_FPS;
            }
            if (times.length == 1) {
                return times[0];
            }
            double clampedFrame = Math.max(0, Math.min(maxFrame(), frame));
            int left = (int) Math.floor(clampedFrame);
            int right = Math.min(times.length - 1, (int) Math.ceil(clampedFrame));
            if (left == right) {
                return times[left];
            }
            double amount = clampedFrame - left;
            return times[left] + (times[right] - times[left]) * amount;
        }
    }
}
