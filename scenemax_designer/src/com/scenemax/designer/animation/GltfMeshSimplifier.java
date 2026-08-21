package com.scenemax.designer.animation;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class GltfMeshSimplifier {
    private static final int FLOAT = 5126;
    private static final int UNSIGNED_BYTE = 5121;
    private static final int UNSIGNED_SHORT = 5123;
    private static final int UNSIGNED_INT = 5125;
    private static final int ARRAY_BUFFER = 34962;
    private static final int ELEMENT_ARRAY_BUFFER = 34963;

    private GltfMeshSimplifier() {
    }

    static Result simplify(JSONObject gltf, File modelDir, String assetBaseName,
                           ModelJ3oClipExporter.TextureOptimizationOptions options) throws IOException {
        if (options == null || !options.meshSimplificationEnabled) {
            return Result.skipped("off");
        }
        if (hasEntries(gltf, "skins") || hasEntries(gltf, "animations")) {
            return Result.skipped("skinned or animated model");
        }
        if (hasEmbeddedBufferImages(gltf)) {
            return Result.skipped("embedded image buffers still present");
        }

        JSONArray meshes = gltf.optJSONArray("meshes");
        if (meshes == null || meshes.isEmpty()) {
            return Result.skipped("no meshes");
        }

        BufferData buffers = BufferData.read(gltf, modelDir);
        List<OutputAccessor> outputAccessors = new ArrayList<>();
        long verticesBefore = 0;
        long verticesAfter = 0;
        long trianglesBefore = 0;
        long trianglesAfter = 0;

        for (int meshIndex = 0; meshIndex < meshes.length(); meshIndex++) {
            JSONObject mesh = meshes.getJSONObject(meshIndex);
            JSONArray primitives = mesh.optJSONArray("primitives");
            if (primitives == null) {
                continue;
            }
            for (int primitiveIndex = 0; primitiveIndex < primitives.length(); primitiveIndex++) {
                JSONObject primitive = primitives.getJSONObject(primitiveIndex);
                SimplifiedPrimitive simplified = simplifyPrimitive(gltf, buffers, primitive, options);
                if (simplified == null) {
                    return Result.skipped("unsupported mesh primitive");
                }
                verticesBefore += simplified.verticesBefore;
                verticesAfter += simplified.verticesAfter;
                trianglesBefore += simplified.trianglesBefore;
                trianglesAfter += simplified.trianglesAfter;
                primitive.put("indices", outputAccessors.size());
                outputAccessors.add(OutputAccessor.indices(simplified.indices));

                JSONObject attributes = new JSONObject();
                attributes.put("POSITION", outputAccessors.size());
                outputAccessors.add(OutputAccessor.vec3("POSITION", simplified.positions));
                if (simplified.uvs != null) {
                    attributes.put("TEXCOORD_0", outputAccessors.size());
                    outputAccessors.add(OutputAccessor.vec2("TEXCOORD_0", simplified.uvs));
                }
                if (simplified.normals != null) {
                    attributes.put("NORMAL", outputAccessors.size());
                    outputAccessors.add(OutputAccessor.vec3("NORMAL", simplified.normals));
                }
                primitive.put("attributes", attributes);
            }
        }

        if (trianglesAfter <= 0 || verticesAfter <= 0) {
            return Result.skipped("empty simplified mesh");
        }

        byte[] bin = writeAccessors(gltf, outputAccessors);
        String binName = assetBaseName + "_mesh_optimized.bin";
        File outputBin = new File(modelDir, binName);
        Files.write(outputBin.toPath(), bin);

        JSONArray outputBuffers = new JSONArray();
        outputBuffers.put(new JSONObject()
                .put("uri", binName)
                .put("byteLength", bin.length));
        gltf.put("buffers", outputBuffers);

        long bytesBefore = buffers.totalBytes();
        return new Result(true, "", verticesBefore, verticesAfter, trianglesBefore, trianglesAfter,
                bytesBefore, bin.length);
    }

    private static SimplifiedPrimitive simplifyPrimitive(JSONObject gltf, BufferData buffers, JSONObject primitive,
                                                         ModelJ3oClipExporter.TextureOptimizationOptions options)
            throws IOException {
        if (primitive.optInt("mode", 4) != 4 || primitive.has("targets")) {
            return null;
        }
        JSONObject attributes = primitive.optJSONObject("attributes");
        if (attributes == null || !attributes.has("POSITION") || !primitive.has("indices")) {
            return null;
        }
        Set<String> supported = Set.of("POSITION", "NORMAL", "TEXCOORD_0");
        for (String key : attributes.keySet()) {
            if (!supported.contains(key)) {
                return null;
            }
        }

        float[][] positions = readFloatVectors(gltf, buffers, attributes.getInt("POSITION"), 3);
        float[][] normals = attributes.has("NORMAL")
                ? readFloatVectors(gltf, buffers, attributes.getInt("NORMAL"), 3)
                : null;
        float[][] uvs = attributes.has("TEXCOORD_0")
                ? readFloatVectors(gltf, buffers, attributes.getInt("TEXCOORD_0"), 2)
                : null;
        int[] indices = readIndices(gltf, buffers, primitive.getInt("indices"));
        if (positions == null || indices == null || indices.length < 3) {
            return null;
        }
        if ((normals != null && normals.length != positions.length) || (uvs != null && uvs.length != positions.length)) {
            return null;
        }

        ClusterBuild build = buildClusters(positions, normals, uvs, options.meshGridCells, options.meshUvGridCells);
        IntList outIndices = new IntList(indices.length / 2);
        boolean[] used = new boolean[build.clusters.size()];
        Set<Long> seenTriangles = new HashSet<>();
        long trianglesBefore = indices.length / 3L;

        for (int i = 0; i + 2 < indices.length; i += 3) {
            int a = build.inverse[indices[i]];
            int b = build.inverse[indices[i + 1]];
            int c = build.inverse[indices[i + 2]];
            if (a == b || a == c || b == c) {
                continue;
            }
            long key = triangleKey(a, b, c);
            if (!seenTriangles.add(key)) {
                continue;
            }
            outIndices.add(a);
            outIndices.add(b);
            outIndices.add(c);
            used[a] = true;
            used[b] = true;
            used[c] = true;
        }
        if (outIndices.size() < 3) {
            return null;
        }

        int[] remap = new int[build.clusters.size()];
        Arrays.fill(remap, -1);
        int vertexCount = 0;
        for (int i = 0; i < used.length; i++) {
            if (used[i]) {
                remap[i] = vertexCount++;
            }
        }

        float[] outPositions = new float[vertexCount * 3];
        float[] outNormals = normals == null ? null : new float[vertexCount * 3];
        float[] outUvs = uvs == null ? null : new float[vertexCount * 2];
        for (int i = 0; i < build.clusters.size(); i++) {
            int mapped = remap[i];
            if (mapped < 0) {
                continue;
            }
            Cluster cluster = build.clusters.get(i);
            putVec(outPositions, mapped, 3, cluster.averagePosition());
            if (outNormals != null) {
                putVec(outNormals, mapped, 3, cluster.averageNormal());
            }
            if (outUvs != null) {
                putVec(outUvs, mapped, 2, cluster.averageUv());
            }
        }

        int[] remappedIndices = outIndices.toArray();
        for (int i = 0; i < remappedIndices.length; i++) {
            remappedIndices[i] = remap[remappedIndices[i]];
        }
        return new SimplifiedPrimitive(positions.length, vertexCount, trianglesBefore,
                remappedIndices.length / 3L, outPositions, outNormals, outUvs, remappedIndices);
    }

    private static ClusterBuild buildClusters(float[][] positions, float[][] normals, float[][] uvs,
                                              int meshGridCells, int uvGridCells) {
        float[] min = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
        float[] max = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (float[] position : positions) {
            for (int i = 0; i < 3; i++) {
                min[i] = Math.min(min[i], position[i]);
                max[i] = Math.max(max[i], position[i]);
            }
        }
        float[] span = {
                Math.max(0.000000001f, max[0] - min[0]),
                Math.max(0.000000001f, max[1] - min[1]),
                Math.max(0.000000001f, max[2] - min[2])
        };
        int cells = Math.max(8, Math.min(1024, meshGridCells));
        int uvCells = Math.max(1, Math.min(1024, uvGridCells));
        Map<Long, Integer> clusterByKey = new HashMap<>();
        List<Cluster> clusters = new ArrayList<>();
        int[] inverse = new int[positions.length];
        for (int i = 0; i < positions.length; i++) {
            int qx = quantize(positions[i][0], min[0], span[0], cells);
            int qy = quantize(positions[i][1], min[1], span[1], cells);
            int qz = quantize(positions[i][2], min[2], span[2], cells);
            int qu = 0;
            int qv = 0;
            if (uvs != null) {
                qu = quantizeUnit(uvs[i][0], uvCells);
                qv = quantizeUnit(uvs[i][1], uvCells);
            }
            long key = (((long) qx) << 40) | (((long) qy) << 30) | (((long) qz) << 20)
                    | (((long) qu) << 10) | (long) qv;
            Integer clusterIndex = clusterByKey.get(key);
            if (clusterIndex == null) {
                clusterIndex = clusters.size();
                clusterByKey.put(key, clusterIndex);
                clusters.add(new Cluster(uvs != null, normals != null));
            }
            clusters.get(clusterIndex).add(positions[i], normals == null ? null : normals[i], uvs == null ? null : uvs[i]);
            inverse[i] = clusterIndex;
        }
        return new ClusterBuild(clusters, inverse);
    }

    private static int quantize(float value, float min, float span, int cells) {
        int q = (int) Math.floor(((value - min) / span) * cells);
        return Math.max(0, Math.min(cells - 1, q));
    }

    private static int quantizeUnit(float value, int cells) {
        int q = (int) Math.floor(Math.max(0f, Math.min(1f, value)) * cells);
        return Math.max(0, Math.min(cells - 1, q));
    }

    private static long triangleKey(int a, int b, int c) {
        if (a < 2_097_152 && b < 2_097_152 && c < 2_097_152) {
            return (((long) a) << 42) | (((long) b) << 21) | (long) c;
        }
        long hash = 1469598103934665603L;
        hash = (hash ^ a) * 1099511628211L;
        hash = (hash ^ b) * 1099511628211L;
        hash = (hash ^ c) * 1099511628211L;
        return hash;
    }

    private static void putVec(float[] target, int index, int components, float[] values) {
        int offset = index * components;
        for (int i = 0; i < components; i++) {
            target[offset + i] = values[i];
        }
    }

    private static float[][] readFloatVectors(JSONObject gltf, BufferData buffers, int accessorIndex, int expectedComponents)
            throws IOException {
        JSONArray accessors = gltf.optJSONArray("accessors");
        JSONArray views = gltf.optJSONArray("bufferViews");
        if (accessors == null || views == null || accessorIndex < 0 || accessorIndex >= accessors.length()) {
            return null;
        }
        JSONObject accessor = accessors.getJSONObject(accessorIndex);
        if (accessor.optInt("componentType") != FLOAT || componentCount(accessor.optString("type")) != expectedComponents) {
            return null;
        }
        JSONObject view = views.getJSONObject(accessor.getInt("bufferView"));
        byte[] data = buffers.get(view.optInt("buffer", 0));
        int count = accessor.optInt("count", 0);
        int offset = view.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0);
        int stride = view.optInt("byteStride", expectedComponents * Float.BYTES);
        float[][] result = new float[count][expectedComponents];
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < count; i++) {
            int base = offset + i * stride;
            for (int c = 0; c < expectedComponents; c++) {
                result[i][c] = buffer.getFloat(base + c * Float.BYTES);
            }
        }
        return result;
    }

    private static int[] readIndices(JSONObject gltf, BufferData buffers, int accessorIndex) throws IOException {
        JSONArray accessors = gltf.optJSONArray("accessors");
        JSONArray views = gltf.optJSONArray("bufferViews");
        if (accessors == null || views == null || accessorIndex < 0 || accessorIndex >= accessors.length()) {
            return null;
        }
        JSONObject accessor = accessors.getJSONObject(accessorIndex);
        int componentType = accessor.optInt("componentType");
        if (componentType != UNSIGNED_BYTE && componentType != UNSIGNED_SHORT && componentType != UNSIGNED_INT) {
            return null;
        }
        JSONObject view = views.getJSONObject(accessor.getInt("bufferView"));
        byte[] data = buffers.get(view.optInt("buffer", 0));
        int count = accessor.optInt("count", 0);
        int offset = view.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0);
        int componentBytes = componentSize(componentType);
        int stride = view.optInt("byteStride", componentBytes);
        int[] result = new int[count];
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < count; i++) {
            int base = offset + i * stride;
            if (componentType == UNSIGNED_BYTE) {
                result[i] = Byte.toUnsignedInt(buffer.get(base));
            } else if (componentType == UNSIGNED_SHORT) {
                result[i] = Short.toUnsignedInt(buffer.getShort(base));
            } else {
                result[i] = buffer.getInt(base);
            }
        }
        return result;
    }

    private static byte[] writeAccessors(JSONObject gltf, List<OutputAccessor> outputAccessors) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        JSONArray views = new JSONArray();
        JSONArray accessors = new JSONArray();
        for (OutputAccessor accessor : outputAccessors) {
            pad4(output);
            int offset = output.size();
            byte[] bytes = accessor.bytes();
            output.write(bytes);
            int length = bytes.length;
            views.put(new JSONObject()
                    .put("buffer", 0)
                    .put("byteOffset", offset)
                    .put("byteLength", length)
                    .put("target", accessor.target));
            JSONObject accessorJson = new JSONObject()
                    .put("bufferView", views.length() - 1)
                    .put("byteOffset", 0)
                    .put("componentType", accessor.componentType)
                    .put("count", accessor.count)
                    .put("type", accessor.type);
            if (accessor.min != null && accessor.max != null) {
                accessorJson.put("min", new JSONArray(accessor.min));
                accessorJson.put("max", new JSONArray(accessor.max));
            }
            accessors.put(accessorJson);
        }
        pad4(output);
        gltf.put("bufferViews", views);
        gltf.put("accessors", accessors);
        return output.toByteArray();
    }

    private static void pad4(ByteArrayOutputStream output) {
        while ((output.size() % 4) != 0) {
            output.write(0);
        }
    }

    private static int componentCount(String type) {
        switch (type) {
            case "SCALAR":
                return 1;
            case "VEC2":
                return 2;
            case "VEC3":
                return 3;
            case "VEC4":
                return 4;
            default:
                return 0;
        }
    }

    private static int componentSize(int componentType) {
        switch (componentType) {
            case UNSIGNED_BYTE:
                return 1;
            case UNSIGNED_SHORT:
                return 2;
            case UNSIGNED_INT:
            case FLOAT:
                return 4;
            default:
                return 0;
        }
    }

    private static boolean hasEntries(JSONObject gltf, String key) {
        JSONArray array = gltf.optJSONArray(key);
        return array != null && !array.isEmpty();
    }

    private static boolean hasEmbeddedBufferImages(JSONObject gltf) {
        JSONArray images = gltf.optJSONArray("images");
        if (images == null) {
            return false;
        }
        for (int i = 0; i < images.length(); i++) {
            if (images.getJSONObject(i).has("bufferView")) {
                return true;
            }
        }
        return false;
    }

    private static File resolveUriFile(File modelDir, String uri) {
        try {
            return new File(modelDir, URI.create(uri.replace("\\", "/")).getPath());
        } catch (IllegalArgumentException ignored) {
            return new File(modelDir, uri.replace('/', File.separatorChar));
        }
    }

    private static class BufferData {
        private final List<byte[]> buffers;

        private BufferData(List<byte[]> buffers) {
            this.buffers = buffers;
        }

        static BufferData read(JSONObject gltf, File modelDir) throws IOException {
            JSONArray bufferArray = gltf.optJSONArray("buffers");
            if (bufferArray == null || bufferArray.isEmpty()) {
                throw new IOException("GLTF has no buffers.");
            }
            List<byte[]> buffers = new ArrayList<>();
            for (int i = 0; i < bufferArray.length(); i++) {
                String uri = bufferArray.getJSONObject(i).optString("uri", "");
                if (uri.startsWith("data:")) {
                    int comma = uri.indexOf(',');
                    if (comma < 0) {
                        throw new IOException("Invalid data URI buffer.");
                    }
                    buffers.add(java.util.Base64.getDecoder().decode(uri.substring(comma + 1)));
                } else if (!uri.isBlank()) {
                    File bufferFile = resolveUriFile(modelDir, uri);
                    if (!bufferFile.isFile()) {
                        bufferFile = new File(modelDir, uri.replace('/', File.separatorChar));
                    }
                    buffers.add(Files.readAllBytes(bufferFile.toPath()));
                } else {
                    throw new IOException("Binary GLTF buffer must be externalized before simplification.");
                }
            }
            return new BufferData(buffers);
        }

        byte[] get(int index) throws IOException {
            if (index < 0 || index >= buffers.size()) {
                throw new IOException("Invalid GLTF buffer index: " + index);
            }
            return buffers.get(index);
        }

        long totalBytes() {
            long total = 0;
            for (byte[] buffer : buffers) {
                total += buffer.length;
            }
            return total;
        }
    }

    private static class ClusterBuild {
        final List<Cluster> clusters;
        final int[] inverse;

        ClusterBuild(List<Cluster> clusters, int[] inverse) {
            this.clusters = clusters;
            this.inverse = inverse;
        }
    }

    private static class Cluster {
        final boolean hasUv;
        final boolean hasNormal;
        int count;
        double px;
        double py;
        double pz;
        double nx;
        double ny;
        double nz;
        double u;
        double v;

        Cluster(boolean hasUv, boolean hasNormal) {
            this.hasUv = hasUv;
            this.hasNormal = hasNormal;
        }

        void add(float[] position, float[] normal, float[] uv) {
            count++;
            px += position[0];
            py += position[1];
            pz += position[2];
            if (hasNormal && normal != null) {
                nx += normal[0];
                ny += normal[1];
                nz += normal[2];
            }
            if (hasUv && uv != null) {
                u += uv[0];
                v += uv[1];
            }
        }

        float[] averagePosition() {
            return new float[]{(float) (px / count), (float) (py / count), (float) (pz / count)};
        }

        float[] averageNormal() {
            double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length <= 0.000000001) {
                return new float[]{0f, 1f, 0f};
            }
            return new float[]{(float) (nx / length), (float) (ny / length), (float) (nz / length)};
        }

        float[] averageUv() {
            return new float[]{(float) (u / count), (float) (v / count)};
        }
    }

    private static class SimplifiedPrimitive {
        final long verticesBefore;
        final long verticesAfter;
        final long trianglesBefore;
        final long trianglesAfter;
        final float[] positions;
        final float[] normals;
        final float[] uvs;
        final int[] indices;

        SimplifiedPrimitive(long verticesBefore, long verticesAfter, long trianglesBefore, long trianglesAfter,
                            float[] positions, float[] normals, float[] uvs, int[] indices) {
            this.verticesBefore = verticesBefore;
            this.verticesAfter = verticesAfter;
            this.trianglesBefore = trianglesBefore;
            this.trianglesAfter = trianglesAfter;
            this.positions = positions;
            this.normals = normals;
            this.uvs = uvs;
            this.indices = indices;
        }
    }

    private static class OutputAccessor {
        final String semantic;
        final int target;
        final int componentType;
        final int count;
        final String type;
        final float[] floats;
        final int[] ints;
        final List<Number> min;
        final List<Number> max;

        private OutputAccessor(String semantic, int target, int componentType, int count, String type,
                               float[] floats, int[] ints, List<Number> min, List<Number> max) {
            this.semantic = semantic;
            this.target = target;
            this.componentType = componentType;
            this.count = count;
            this.type = type;
            this.floats = floats;
            this.ints = ints;
            this.min = min;
            this.max = max;
        }

        static OutputAccessor indices(int[] indices) {
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (int index : indices) {
                min = Math.min(min, index);
                max = Math.max(max, index);
            }
            return new OutputAccessor("indices", ELEMENT_ARRAY_BUFFER, UNSIGNED_INT, indices.length, "SCALAR",
                    null, indices, List.of(min), List.of(max));
        }

        static OutputAccessor vec2(String semantic, float[] values) {
            return new OutputAccessor(semantic, ARRAY_BUFFER, FLOAT, values.length / 2, "VEC2",
                    values, null, min(values, 2), max(values, 2));
        }

        static OutputAccessor vec3(String semantic, float[] values) {
            return new OutputAccessor(semantic, ARRAY_BUFFER, FLOAT, values.length / 3, "VEC3",
                    values, null, min(values, 3), max(values, 3));
        }

        byte[] bytes() {
            if (ints != null) {
                ByteBuffer buffer = ByteBuffer.allocate(ints.length * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
                for (int value : ints) {
                    buffer.putInt(value);
                }
                return buffer.array();
            }
            ByteBuffer buffer = ByteBuffer.allocate(floats.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            for (float value : floats) {
                buffer.putFloat(value);
            }
            return buffer.array();
        }

        private static List<Number> min(float[] values, int components) {
            Double[] mins = new Double[components];
            Arrays.fill(mins, Double.POSITIVE_INFINITY);
            for (int i = 0; i < values.length; i += components) {
                for (int c = 0; c < components; c++) {
                    mins[c] = Math.min(mins[c], values[i + c]);
                }
            }
            return Arrays.asList(mins);
        }

        private static List<Number> max(float[] values, int components) {
            Double[] maxes = new Double[components];
            Arrays.fill(maxes, Double.NEGATIVE_INFINITY);
            for (int i = 0; i < values.length; i += components) {
                for (int c = 0; c < components; c++) {
                    maxes[c] = Math.max(maxes[c], values[i + c]);
                }
            }
            return Arrays.asList(maxes);
        }
    }

    private static class IntList {
        private int[] values;
        private int size;

        IntList(int capacity) {
            values = new int[Math.max(16, capacity)];
        }

        void add(int value) {
            if (size >= values.length) {
                values = Arrays.copyOf(values, values.length * 2);
            }
            values[size++] = value;
        }

        int size() {
            return size;
        }

        int[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }

    static class Result {
        final boolean simplified;
        final String skippedReason;
        final long verticesBefore;
        final long verticesAfter;
        final long trianglesBefore;
        final long trianglesAfter;
        final long bytesBefore;
        final long bytesAfter;

        Result(boolean simplified, String skippedReason, long verticesBefore, long verticesAfter,
               long trianglesBefore, long trianglesAfter, long bytesBefore, long bytesAfter) {
            this.simplified = simplified;
            this.skippedReason = skippedReason;
            this.verticesBefore = verticesBefore;
            this.verticesAfter = verticesAfter;
            this.trianglesBefore = trianglesBefore;
            this.trianglesAfter = trianglesAfter;
            this.bytesBefore = bytesBefore;
            this.bytesAfter = bytesAfter;
        }

        static Result skipped(String reason) {
            return new Result(false, reason == null ? "" : reason, 0, 0, 0, 0, 0, 0);
        }
    }
}
