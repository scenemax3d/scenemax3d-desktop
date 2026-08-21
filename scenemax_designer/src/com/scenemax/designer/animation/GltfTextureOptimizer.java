package com.scenemax.designer.animation;

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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Locale;

public final class GltfTextureOptimizer {
    private static final int GLB_MAGIC = 0x46546C67;
    private static final int GLB_JSON_CHUNK = 0x4E4F534A;
    private static final int GLB_BIN_CHUNK = 0x004E4942;

    private GltfTextureOptimizer() {
    }

    public static Result optimize(File modelFile, ModelJ3oClipExporter.TextureOptimizationOptions options)
            throws IOException {
        if (modelFile == null || !modelFile.isFile()) {
            throw new IOException("Model file was not found.");
        }
        if (options == null || !options.hasAnyOptimization()) {
            return new Result(modelFile, 0, 0, 0, "off");
        }

        String lower = modelFile.getName().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".gltf")) {
            return optimizeGltf(modelFile, options);
        }
        if (lower.endsWith(".glb")) {
            return optimizeGlbToGltf(modelFile, options);
        }
        return new Result(modelFile, 0, 0, 0, "unsupported");
    }

    private static Result optimizeGltf(File gltfFile, ModelJ3oClipExporter.TextureOptimizationOptions options)
            throws IOException {
        JSONObject gltf = new JSONObject(Files.readString(gltfFile.toPath(), StandardCharsets.UTF_8));
        Stats stats = options.enabled
                ? optimizeImages(gltf, gltfFile.getParentFile(), stripExtension(gltfFile.getName()), options, null)
                : new Stats();
        GltfMeshSimplifier.Result geometry = GltfMeshSimplifier.simplify(
                gltf, gltfFile.getParentFile(), stripExtension(gltfFile.getName()), options);
        Files.writeString(gltfFile.toPath(), gltf.toString(2), StandardCharsets.UTF_8);
        return new Result(gltfFile, stats.textureCount, stats.bytesBefore, stats.bytesAfter, options.summary(), geometry);
    }

    private static Result optimizeGlbToGltf(File glbFile, ModelJ3oClipExporter.TextureOptimizationOptions options)
            throws IOException {
        GlbData glb = readGlb(glbFile);
        File outputGltf = new File(glbFile.getParentFile(), stripExtension(glbFile.getName()) + ".gltf");
        File outputBin = new File(glbFile.getParentFile(), stripExtension(glbFile.getName()) + ".bin");

        JSONObject json = glb.json;
        JSONArray buffers = json.optJSONArray("buffers");
        if (buffers != null && buffers.length() > 0 && glb.bin != null) {
            JSONObject buffer = buffers.getJSONObject(0);
            buffer.put("uri", outputBin.getName());
            buffer.put("byteLength", glb.bin.length);
            Files.write(outputBin.toPath(), glb.bin);
        }

        Stats stats = options.enabled
                ? optimizeImages(json, outputGltf.getParentFile(), stripExtension(outputGltf.getName()), options, glb.bin)
                : new Stats();
        GltfMeshSimplifier.Result geometry = GltfMeshSimplifier.simplify(
                json, outputGltf.getParentFile(), stripExtension(outputGltf.getName()), options);
        Files.writeString(outputGltf.toPath(), json.toString(2), StandardCharsets.UTF_8);
        return new Result(outputGltf, stats.textureCount, stats.bytesBefore, stats.bytesAfter, options.summary(), geometry);
    }

    private static Stats optimizeImages(JSONObject gltf, File modelDir, String assetBaseName,
                                        ModelJ3oClipExporter.TextureOptimizationOptions options,
                                        byte[] bin) throws IOException {
        Stats stats = new Stats();
        JSONArray images = gltf.optJSONArray("images");
        if (images == null) {
            return stats;
        }
        JSONArray bufferViews = gltf.optJSONArray("bufferViews");

        for (int i = 0; i < images.length(); i++) {
            JSONObject image = images.getJSONObject(i);
            String uri = image.optString("uri", "");
            File sourceFile = null;
            byte[] embeddedBytes = null;

            if (!uri.isBlank() && !isDataUri(uri)) {
                sourceFile = resolveUriFile(modelDir, uri);
                if (!sourceFile.isFile()) {
                    sourceFile = new File(modelDir, uri.replace('/', File.separatorChar));
                }
                if (!sourceFile.isFile()) {
                    continue;
                }
            } else if (isDataUri(uri)) {
                embeddedBytes = decodeDataUri(uri);
            } else if (image.has("bufferView") && bin != null && bufferViews != null) {
                int bufferViewIndex = image.getInt("bufferView");
                if (bufferViewIndex >= 0 && bufferViewIndex < bufferViews.length()) {
                    JSONObject view = bufferViews.getJSONObject(bufferViewIndex);
                    int offset = view.optInt("byteOffset", 0);
                    int length = view.optInt("byteLength", 0);
                    if (offset >= 0 && length > 0 && offset + length <= bin.length) {
                        embeddedBytes = java.util.Arrays.copyOfRange(bin, offset, offset + length);
                    }
                }
            }

            BufferedImage original = sourceFile != null
                    ? ImageIO.read(sourceFile)
                    : embeddedBytes == null ? null : ImageIO.read(new ByteArrayInputStream(embeddedBytes));
            if (original == null) {
                continue;
            }

            long before = sourceFile != null ? sourceFile.length() : embeddedBytes.length;
            String targetName = optimizedImageName(sourceFile != null ? sourceFile.getName() : "image_" + i + ".png",
                    original, options);
            File target = new File(modelDir, "textures/" + assetBaseName + "/" + targetName);
            if (!target.getParentFile().exists() && !target.getParentFile().mkdirs()) {
                throw new IOException("Failed to create optimized texture folder: " + target.getParentFile().getAbsolutePath());
            }
            writeOptimizedImage(original, target, targetName, options);
            deleteReplacedImportedTexture(modelDir, sourceFile, target);

            image.put("uri", modelDir.toPath().relativize(target.toPath()).toString().replace("\\", "/"));
            image.remove("bufferView");
            image.remove("mimeType");

            stats.textureCount++;
            stats.bytesBefore += before;
            stats.bytesAfter += target.length();
        }
        return stats;
    }

    private static File resolveUriFile(File modelDir, String uri) {
        try {
            return new File(modelDir, URI.create(uri.replace("\\", "/")).getPath());
        } catch (IllegalArgumentException ignored) {
            return new File(modelDir, uri.replace('/', File.separatorChar));
        }
    }

    private static void deleteReplacedImportedTexture(File modelDir, File sourceFile, File targetFile) throws IOException {
        if (sourceFile == null || targetFile == null || !sourceFile.isFile()) {
            return;
        }
        File modelRoot = modelDir.getCanonicalFile();
        File source = sourceFile.getCanonicalFile();
        File target = targetFile.getCanonicalFile();
        if (source.equals(target) || !source.toPath().startsWith(modelRoot.toPath())) {
            return;
        }
        Files.deleteIfExists(source.toPath());
    }

    private static String optimizedImageName(String sourceName, BufferedImage image,
                                             ModelJ3oClipExporter.TextureOptimizationOptions options) {
        String safe = sourceName == null || sourceName.isBlank() ? "texture.png" : sourceName.replaceAll("[^A-Za-z0-9._-]+", "_");
        boolean convertToJpeg = options.convertColorPngToJpeg
                && safe.toLowerCase(Locale.ROOT).endsWith(".png")
                && !shouldKeepLossless(safe)
                && !image.getColorModel().hasAlpha();
        if (convertToJpeg) {
            return safe.substring(0, safe.length() - 4) + ".jpg";
        }
        return safe;
    }

    private static void writeOptimizedImage(BufferedImage image, File target, String targetName,
                                            ModelJ3oClipExporter.TextureOptimizationOptions options)
            throws IOException {
        BufferedImage scaled = scaleImage(image, options.maxDimension);
        String lower = targetName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            writeJpeg(toRgbImage(scaled), target, options.jpegQuality);
        } else {
            ImageIO.write(scaled, "png", target);
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

    private static void writeJpeg(BufferedImage image, File target, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam params = writer.getDefaultWriteParam();
        if (params.canWriteCompressed()) {
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(Math.max(0.05f, Math.min(1f, quality)));
        }
        try (ImageOutputStream output = ImageIO.createImageOutputStream(target)) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
    }

    private static boolean shouldKeepLossless(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return lower.contains("normal") || lower.contains("_nrm") || lower.contains("-nrm")
                || lower.contains("bump") || lower.contains("height") || lower.contains("alpha")
                || lower.contains("opacity") || lower.contains("mask");
    }

    private static boolean isDataUri(String uri) {
        return uri != null && uri.startsWith("data:");
    }

    private static byte[] decodeDataUri(String uri) {
        int comma = uri.indexOf(',');
        if (comma < 0) {
            return new byte[0];
        }
        return Base64.getDecoder().decode(uri.substring(comma + 1));
    }

    private static GlbData readGlb(File glbFile) throws IOException {
        byte[] bytes = Files.readAllBytes(glbFile.toPath());
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (buffer.remaining() < 12 || buffer.getInt() != GLB_MAGIC) {
            throw new IOException("Invalid GLB file: " + glbFile.getName());
        }
        buffer.getInt();
        int totalLength = buffer.getInt();
        if (totalLength != bytes.length) {
            throw new IOException("Invalid GLB length: " + glbFile.getName());
        }
        JSONObject json = null;
        byte[] bin = null;
        while (buffer.remaining() >= 8) {
            int chunkLength = buffer.getInt();
            int chunkType = buffer.getInt();
            if (chunkLength < 0 || chunkLength > buffer.remaining()) {
                throw new IOException("Invalid GLB chunk: " + glbFile.getName());
            }
            byte[] chunk = new byte[chunkLength];
            buffer.get(chunk);
            if (chunkType == GLB_JSON_CHUNK) {
                json = new JSONObject(new String(trimJsonPadding(chunk), StandardCharsets.UTF_8));
            } else if (chunkType == GLB_BIN_CHUNK) {
                bin = chunk;
            }
        }
        if (json == null) {
            throw new IOException("GLB has no JSON chunk: " + glbFile.getName());
        }
        return new GlbData(json, bin);
    }

    private static byte[] trimJsonPadding(byte[] data) {
        int end = data.length;
        while (end > 0 && (data[end - 1] == 0 || data[end - 1] == 0x20)) {
            end--;
        }
        return java.util.Arrays.copyOf(data, end);
    }

    private static String stripExtension(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static class GlbData {
        final JSONObject json;
        final byte[] bin;

        GlbData(JSONObject json, byte[] bin) {
            this.json = json;
            this.bin = bin;
        }
    }

    private static class Stats {
        int textureCount;
        long bytesBefore;
        long bytesAfter;
    }

    public static class Result {
        public final File modelFile;
        public final int textureCount;
        public final long bytesBefore;
        public final long bytesAfter;
        public final String summary;
        public final boolean meshSimplified;
        public final String meshSkippedReason;
        public final long verticesBefore;
        public final long verticesAfter;
        public final long trianglesBefore;
        public final long trianglesAfter;
        public final long geometryBytesBefore;
        public final long geometryBytesAfter;

        Result(File modelFile, int textureCount, long bytesBefore, long bytesAfter, String summary) {
            this(modelFile, textureCount, bytesBefore, bytesAfter, summary, GltfMeshSimplifier.Result.skipped("off"));
        }

        Result(File modelFile, int textureCount, long bytesBefore, long bytesAfter, String summary,
               GltfMeshSimplifier.Result geometry) {
            this.modelFile = modelFile;
            this.textureCount = textureCount;
            this.bytesBefore = bytesBefore;
            this.bytesAfter = bytesAfter;
            this.summary = summary;
            this.meshSimplified = geometry != null && geometry.simplified;
            this.meshSkippedReason = geometry == null ? "" : geometry.skippedReason;
            this.verticesBefore = geometry == null ? 0 : geometry.verticesBefore;
            this.verticesAfter = geometry == null ? 0 : geometry.verticesAfter;
            this.trianglesBefore = geometry == null ? 0 : geometry.trianglesBefore;
            this.trianglesAfter = geometry == null ? 0 : geometry.trianglesAfter;
            this.geometryBytesBefore = geometry == null ? 0 : geometry.bytesBefore;
            this.geometryBytesAfter = geometry == null ? 0 : geometry.bytesAfter;
        }
    }
}
