package com.scenemax.designer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Normalizes imported glTF/GLB assets by collapsing a single transformed
 * scene-root wrapper into its children. This strips common importer-added
 * axis-conversion wrappers before the asset is registered in the project.
 */
public final class ImportedModelNormalizer {

    private static final int GLB_MAGIC = 0x46546C67;
    private static final int GLB_JSON_CHUNK = 0x4E4F534A;
    private static final double IDENTITY_EPSILON = 1e-6;

    private ImportedModelNormalizer() {
    }

    public static Result normalize(Path modelPath) throws IOException {
        if (modelPath == null || !Files.isRegularFile(modelPath)) {
            return Result.skipped("missing-file");
        }

        String fileName = modelPath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".glb")) {
            return normalizeGlb(modelPath);
        }
        if (fileName.endsWith(".gltf")) {
            return normalizeGltf(modelPath);
        }
        return Result.skipped("unsupported-extension");
    }

    private static Result normalizeGltf(Path modelPath) throws IOException {
        String content = Files.readString(modelPath, StandardCharsets.UTF_8);
        JSONObject gltf = new JSONObject(content);
        if (!normalizeRootWrappers(gltf)) {
            return Result.skipped("no-wrapper-transform");
        }
        Files.writeString(modelPath, gltf.toString(2), StandardCharsets.UTF_8);
        return Result.normalized("gltf-root-wrapper");
    }

    private static Result normalizeGlb(Path modelPath) throws IOException {
        byte[] bytes = Files.readAllBytes(modelPath);
        if (bytes.length < 20) {
            return Result.skipped("glb-too-small");
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int magic = buffer.getInt();
        int version = buffer.getInt();
        int length = buffer.getInt();
        if (magic != GLB_MAGIC || length != bytes.length) {
            return Result.skipped("invalid-glb-header");
        }

        List<Chunk> chunks = new ArrayList<>();
        byte[] jsonData = null;
        while (buffer.remaining() >= 8) {
            int chunkLength = buffer.getInt();
            int chunkType = buffer.getInt();
            if (chunkLength < 0 || chunkLength > buffer.remaining()) {
                return Result.skipped("invalid-glb-chunk");
            }
            byte[] chunkData = new byte[chunkLength];
            buffer.get(chunkData);
            chunks.add(new Chunk(chunkType, chunkData));
            if (chunkType == GLB_JSON_CHUNK && jsonData == null) {
                jsonData = chunkData;
            }
        }

        if (jsonData == null) {
            return Result.skipped("missing-json-chunk");
        }

        String jsonText = new String(trimJsonPadding(jsonData), StandardCharsets.UTF_8);
        JSONObject gltf = new JSONObject(jsonText);
        if (!normalizeRootWrappers(gltf)) {
            return Result.skipped("no-wrapper-transform");
        }

        byte[] rewrittenJson = padJsonChunk(gltf.toString().getBytes(StandardCharsets.UTF_8));
        int totalLength = 12;
        for (Chunk chunk : chunks) {
            byte[] chunkData = chunk.type == GLB_JSON_CHUNK ? rewrittenJson : chunk.data;
            totalLength += 8 + chunkData.length;
        }

        ByteBuffer out = ByteBuffer.allocate(totalLength).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(GLB_MAGIC);
        out.putInt(version);
        out.putInt(totalLength);
        for (Chunk chunk : chunks) {
            byte[] chunkData = chunk.type == GLB_JSON_CHUNK ? rewrittenJson : chunk.data;
            out.putInt(chunkData.length);
            out.putInt(chunk.type);
            out.put(chunkData);
        }
        Files.write(modelPath, out.array());
        return Result.normalized("glb-root-wrapper");
    }

    private static boolean normalizeRootWrappers(JSONObject gltf) {
        JSONArray scenes = gltf.optJSONArray("scenes");
        JSONArray nodes = gltf.optJSONArray("nodes");
        if (scenes == null || nodes == null || nodes.length() == 0) {
            return false;
        }

        boolean changed = false;
        boolean passChanged;
        do {
            passChanged = false;
            Set<Integer> reachableNodes = collectReachableNodes(scenes, nodes);
            for (int i = 0; i < scenes.length(); i++) {
                JSONObject scene = scenes.optJSONObject(i);
                if (scene != null && normalizeSceneRootWrapper(gltf, nodes, scene, reachableNodes)) {
                    changed = true;
                    passChanged = true;
                }
            }
        } while (passChanged);

        return changed;
    }

    private static boolean normalizeSceneRootWrapper(JSONObject gltf, JSONArray nodes, JSONObject scene,
                                                     Set<Integer> reachableNodes) {
        JSONArray sceneNodes = scene.optJSONArray("nodes");
        if (sceneNodes == null || sceneNodes.length() != 1) {
            return false;
        }

        int rootIndex = sceneNodes.optInt(0, -1);
        JSONObject rootNode = rootIndex >= 0 ? nodes.optJSONObject(rootIndex) : null;
        if (rootNode == null) {
            return false;
        }

        JSONArray children = rootNode.optJSONArray("children");
        if (children == null || children.length() == 0) {
            return false;
        }

        if (rootNode.has("mesh") || rootNode.has("camera") || rootNode.has("skin") || rootNode.has("weights")) {
            return false;
        }
        if (isNodeExternallyReferenced(gltf, rootIndex, reachableNodes)) {
            return false;
        }

        double[][] rootMatrix = readNodeMatrix(rootNode);
        if (isIdentity(rootMatrix)) {
            return false;
        }

        JSONArray replacementRoots = new JSONArray();
        for (int i = 0; i < children.length(); i++) {
            int childIndex = children.optInt(i, -1);
            JSONObject childNode = childIndex >= 0 ? nodes.optJSONObject(childIndex) : null;
            if (childNode == null) {
                return false;
            }
            double[][] combined = multiply(rootMatrix, readNodeMatrix(childNode));
            writeNodeMatrix(childNode, combined);
            replacementRoots.put(childIndex);
        }

        scene.put("nodes", replacementRoots);
        rootNode.remove("matrix");
        rootNode.remove("translation");
        rootNode.remove("rotation");
        rootNode.remove("scale");
        return true;
    }

    private static boolean isNodeExternallyReferenced(JSONObject gltf, int nodeIndex, Set<Integer> reachableNodes) {
        JSONArray nodes = gltf.optJSONArray("nodes");
        if (nodes != null) {
            for (int i = 0; i < nodes.length(); i++) {
                if (reachableNodes != null && !reachableNodes.contains(i)) {
                    continue;
                }
                JSONObject node = nodes.optJSONObject(i);
                if (node == null) {
                    continue;
                }
                JSONArray children = node.optJSONArray("children");
                if (children == null) {
                    continue;
                }
                for (int j = 0; j < children.length(); j++) {
                    if (children.optInt(j, -1) == nodeIndex) {
                        return true;
                    }
                }
            }
        }

        JSONArray animations = gltf.optJSONArray("animations");
        if (animations != null) {
            for (int i = 0; i < animations.length(); i++) {
                JSONObject animation = animations.optJSONObject(i);
                if (animation == null) {
                    continue;
                }
                JSONArray channels = animation.optJSONArray("channels");
                if (channels == null) {
                    continue;
                }
                for (int j = 0; j < channels.length(); j++) {
                    JSONObject channel = channels.optJSONObject(j);
                    JSONObject target = channel != null ? channel.optJSONObject("target") : null;
                    if (target != null && target.optInt("node", -1) == nodeIndex) {
                        return true;
                    }
                }
            }
        }

        JSONArray skins = gltf.optJSONArray("skins");
        if (skins != null) {
            for (int i = 0; i < skins.length(); i++) {
                JSONObject skin = skins.optJSONObject(i);
                if (skin == null) {
                    continue;
                }
                if (skin.optInt("skeleton", -1) == nodeIndex) {
                    return true;
                }
                JSONArray joints = skin.optJSONArray("joints");
                if (joints == null) {
                    continue;
                }
                for (int j = 0; j < joints.length(); j++) {
                    if (joints.optInt(j, -1) == nodeIndex) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static Set<Integer> collectReachableNodes(JSONArray scenes, JSONArray nodes) {
        Set<Integer> reachable = new HashSet<>();
        if (scenes == null || nodes == null) {
            return reachable;
        }
        for (int i = 0; i < scenes.length(); i++) {
            JSONObject scene = scenes.optJSONObject(i);
            JSONArray roots = scene != null ? scene.optJSONArray("nodes") : null;
            if (roots == null) {
                continue;
            }
            for (int j = 0; j < roots.length(); j++) {
                visitNode(roots.optInt(j, -1), nodes, reachable);
            }
        }
        return reachable;
    }

    private static void visitNode(int nodeIndex, JSONArray nodes, Set<Integer> reachable) {
        if (nodeIndex < 0 || nodes == null || !reachable.add(nodeIndex)) {
            return;
        }
        JSONObject node = nodes.optJSONObject(nodeIndex);
        JSONArray children = node != null ? node.optJSONArray("children") : null;
        if (children == null) {
            return;
        }
        for (int i = 0; i < children.length(); i++) {
            visitNode(children.optInt(i, -1), nodes, reachable);
        }
    }

    private static double[][] readNodeMatrix(JSONObject node) {
        JSONArray matrix = node.optJSONArray("matrix");
        if (matrix != null && matrix.length() == 16) {
            return fromColumnMajor(matrix);
        }

        double[] translation = readVector(node.optJSONArray("translation"), new double[]{0.0, 0.0, 0.0});
        double[] rotation = readVector(node.optJSONArray("rotation"), new double[]{0.0, 0.0, 0.0, 1.0});
        double[] scale = readVector(node.optJSONArray("scale"), new double[]{1.0, 1.0, 1.0});
        return composeTrs(translation, rotation, scale);
    }

    private static void writeNodeMatrix(JSONObject node, double[][] matrix) {
        JSONArray json = new JSONArray();
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                json.put(clean(matrix[row][col]));
            }
        }
        node.put("matrix", json);
        node.remove("translation");
        node.remove("rotation");
        node.remove("scale");
    }

    private static double[] readVector(JSONArray values, double[] defaults) {
        double[] out = Arrays.copyOf(defaults, defaults.length);
        if (values == null) {
            return out;
        }
        for (int i = 0; i < values.length() && i < out.length; i++) {
            out[i] = values.optDouble(i, out[i]);
        }
        return out;
    }

    private static double[][] fromColumnMajor(JSONArray values) {
        double[][] matrix = new double[4][4];
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                matrix[row][col] = values.optDouble(col * 4 + row, row == col ? 1.0 : 0.0);
            }
        }
        return matrix;
    }

    private static double[][] composeTrs(double[] translation, double[] rotation, double[] scale) {
        double x = rotation[0];
        double y = rotation[1];
        double z = rotation[2];
        double w = rotation[3];

        double xx = x * x;
        double yy = y * y;
        double zz = z * z;
        double xy = x * y;
        double xz = x * z;
        double yz = y * z;
        double wx = w * x;
        double wy = w * y;
        double wz = w * z;

        double[][] matrix = identity();
        matrix[0][0] = (1.0 - 2.0 * (yy + zz)) * scale[0];
        matrix[0][1] = (2.0 * (xy - wz)) * scale[1];
        matrix[0][2] = (2.0 * (xz + wy)) * scale[2];
        matrix[1][0] = (2.0 * (xy + wz)) * scale[0];
        matrix[1][1] = (1.0 - 2.0 * (xx + zz)) * scale[1];
        matrix[1][2] = (2.0 * (yz - wx)) * scale[2];
        matrix[2][0] = (2.0 * (xz - wy)) * scale[0];
        matrix[2][1] = (2.0 * (yz + wx)) * scale[1];
        matrix[2][2] = (1.0 - 2.0 * (xx + yy)) * scale[2];
        matrix[0][3] = translation[0];
        matrix[1][3] = translation[1];
        matrix[2][3] = translation[2];
        return matrix;
    }

    private static double[][] multiply(double[][] left, double[][] right) {
        double[][] out = new double[4][4];
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                double sum = 0.0;
                for (int k = 0; k < 4; k++) {
                    sum += left[row][k] * right[k][col];
                }
                out[row][col] = clean(sum);
            }
        }
        return out;
    }

    private static boolean isIdentity(double[][] matrix) {
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                double expected = row == col ? 1.0 : 0.0;
                if (Math.abs(matrix[row][col] - expected) > IDENTITY_EPSILON) {
                    return false;
                }
            }
        }
        return true;
    }

    private static double[][] identity() {
        double[][] matrix = new double[4][4];
        for (int i = 0; i < 4; i++) {
            matrix[i][i] = 1.0;
        }
        return matrix;
    }

    private static double clean(double value) {
        if (Math.abs(value) < IDENTITY_EPSILON) {
            return 0.0;
        }
        if (Math.abs(value - 1.0) < IDENTITY_EPSILON) {
            return 1.0;
        }
        if (Math.abs(value + 1.0) < IDENTITY_EPSILON) {
            return -1.0;
        }
        return value;
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

    private static byte[] padJsonChunk(byte[] jsonBytes) {
        int paddedLength = ((jsonBytes.length + 3) / 4) * 4;
        byte[] padded = Arrays.copyOf(jsonBytes, paddedLength);
        for (int i = jsonBytes.length; i < padded.length; i++) {
            padded[i] = 0x20;
        }
        return padded;
    }

    private static final class Chunk {
        final int type;
        final byte[] data;

        Chunk(int type, byte[] data) {
            this.type = type;
            this.data = data;
        }
    }

    public static final class Result {
        public final boolean normalized;
        public final String reason;

        private Result(boolean normalized, String reason) {
            this.normalized = normalized;
            this.reason = reason;
        }

        public static Result normalized(String reason) {
            return new Result(true, reason);
        }

        public static Result skipped(String reason) {
            return new Result(false, reason);
        }
    }
}
