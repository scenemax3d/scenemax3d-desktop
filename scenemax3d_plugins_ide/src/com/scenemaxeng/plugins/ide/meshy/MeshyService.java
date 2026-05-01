package com.scenemaxeng.plugins.ide.meshy;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

final class MeshyService {
    private static final String BASE_URL = "https://api.meshy.ai/openapi/v2/text-to-3d";
    private static final String COMMUNITY_URL = "https://api.meshy.ai/web/public/v2/showcases";
    private static final String WEB_TASKS_URL = "https://api.meshy.ai/web/v2/tasks";

    private MeshyService() {
    }

    static String normalizeApiKey(String apiKey) {
        String trimmed = apiKey == null ? "" : apiKey.trim();
        if (trimmed.startsWith("Bearer ")) {
            return trimmed;
        }
        return "Bearer " + trimmed;
    }

    static JSONObject createPreview(String apiKey, String prompt, String modelType, String aiModel,
                                    boolean shouldRemesh, int targetPolycount) throws IOException {
        JSONObject body = new JSONObject();
        body.put("mode", "preview");
        body.put("prompt", prompt);
        body.put("model_type", modelType);
        body.put("ai_model", aiModel);
        body.put("target_formats", new JSONArray().put("glb"));
        if (!"lowpoly".equals(modelType)) {
            body.put("should_remesh", shouldRemesh);
            if (targetPolycount > 0) {
                body.put("target_polycount", targetPolycount);
            }
        }
        return new JSONObject(request("POST", BASE_URL, normalizeApiKey(apiKey), body.toString()));
    }

    static JSONObject createRefine(String apiKey, String previewTaskId) throws IOException {
        JSONObject body = new JSONObject();
        body.put("mode", "refine");
        body.put("preview_task_id", previewTaskId);
        body.put("target_formats", new JSONArray().put("glb"));
        return new JSONObject(request("POST", BASE_URL, normalizeApiKey(apiKey), body.toString()));
    }

    static JSONObject getTask(String apiKey, String taskId) throws IOException {
        return new JSONObject(request("GET", BASE_URL + "/" + encode(taskId), normalizeApiKey(apiKey), null));
    }

    static JSONArray listTasks(String apiKey, int pageNum, int pageSize) throws IOException {
        String url = BASE_URL + "?page_num=" + pageNum + "&page_size=" + pageSize + "&sort_by=-created_at";
        String response = request("GET", url, normalizeApiKey(apiKey), null);
        String trimmed = response == null ? "" : response.trim();
        if (trimmed.startsWith("[")) {
            return new JSONArray(trimmed);
        }
        JSONObject object = new JSONObject(trimmed);
        if (object.has("results")) {
            return object.getJSONArray("results");
        }
        if (object.has("data")) {
            return object.getJSONArray("data");
        }
        return new JSONArray();
    }

    static JSONArray searchCommunityModels(String query, String sortBy, int pageNum, int pageSize) throws IOException {
        StringBuilder url = new StringBuilder(COMMUNITY_URL)
                .append("?pageNum=").append(pageNum)
                .append("&pageSize=").append(pageSize)
                .append("&sortBy=").append(encode(sortBy == null || sortBy.isEmpty() ? "-public_popularity" : sortBy));
        if (query != null && !query.trim().isEmpty()) {
            url.append("&search=").append(encode(query.trim()));
        }

        String response = request("GET", url.toString(), null, null);
        String trimmed = response == null ? "" : response.trim();
        if (trimmed.startsWith("[")) {
            return new JSONArray(trimmed);
        }
        JSONObject object = new JSONObject(trimmed);
        if (object.has("result")) {
            return object.getJSONArray("result");
        }
        if (object.has("results")) {
            return object.getJSONArray("results");
        }
        if (object.has("data")) {
            return object.getJSONArray("data");
        }
        return new JSONArray();
    }

    static String getCommunityDownloadUrl(String apiKey, String resultId, String format) throws IOException {
        return getCommunityDownloadAsset(apiKey, resultId, format, false).url;
    }

    static CommunityDownloadAsset getCommunityDownloadAsset(String apiKey, MeshyCommunityModelItem item, String format) throws IOException {
        if (item == null || item.resultId().trim().isEmpty()) {
            throw new IOException("Meshy community model is missing a downloadable task id.");
        }
        String resultId = item.resultId().trim();
        if (item.hasAnimation()) {
            try {
                return getCommunityDownloadAsset(apiKey, resultId, format, true);
            } catch (IOException animatedDownloadError) {
                if (!isInvalidActionError(animatedDownloadError)) {
                    throw animatedDownloadError;
                }
            }
        }
        return getCommunityDownloadAsset(apiKey, resultId, format, false);
    }

    private static CommunityDownloadAsset getCommunityDownloadAsset(String apiKey, String resultId, String format,
                                                                    boolean includeAnimation) throws IOException {
        if (resultId == null || resultId.trim().isEmpty()) {
            throw new IOException("Meshy community model is missing a downloadable task id.");
        }
        String normalizedFormat = format == null || format.isEmpty() ? "glb" : format;
        String url = WEB_TASKS_URL + "/" + encode(resultId.trim()) + "/asset-url"
                + "?type=Showcase&format=" + encode(normalizedFormat);
        if (includeAnimation) {
            url += "&includeAnimation=true"
                    + "&action=all"
                    + "&includeRiggedCharacter=false"
                    + "&withoutSkin=false"
                    + "&framesPerSecond=30"
                    + "&singleFile=true";
        }
        String response = request("GET", url, normalizeApiKey(apiKey), null);
        String trimmed = response == null ? "" : response.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            String directUrl = new org.json.JSONTokener(trimmed).nextValue().toString();
            return new CommunityDownloadAsset(directUrl, extensionForUrl(directUrl, normalizedFormat));
        }
        JSONObject object = new JSONObject(trimmed);
        Object result = object.opt("result");
        if (result instanceof String) {
            String directUrl = (String) result;
            return new CommunityDownloadAsset(directUrl, extensionForUrl(directUrl, normalizedFormat));
        }
        if (result instanceof JSONObject) {
            CommunityDownloadAsset asset = firstDownloadAsset((JSONObject) result, normalizedFormat);
            if (asset != null) {
                return asset;
            }
        }
        CommunityDownloadAsset asset = firstDownloadAsset(object, normalizedFormat);
        if (asset != null) {
            return asset;
        }
        throw new IOException("Meshy did not return a downloadable " + normalizedFormat + " URL.");
    }

    static void downloadFile(String downloadUrl, File destFile, Consumer<Integer> onProgress) throws IOException {
        URL url = new URL(downloadUrl);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setDoInput(true);
        conn.connect();

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + ": " + readResponseBody(conn, code));
        }

        long totalSize = conn.getContentLengthLong();
        try (InputStream in = conn.getInputStream();
             FileOutputStream out = new FileOutputStream(destFile)) {
            byte[] buffer = new byte[8192];
            long downloaded = 0;
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                downloaded += bytesRead;
                if (totalSize > 0 && onProgress != null) {
                    onProgress.accept((int) Math.min(100, downloaded * 100 / totalSize));
                }
            }
        }
    }

    private static String request(String method, String urlStr, String authorization, String body) throws IOException {
        URL url = new URL(urlStr);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Accept", "application/json");
        if (authorization != null && !authorization.trim().isEmpty()) {
            conn.setRequestProperty("Authorization", authorization);
        }
        conn.setUseCaches(false);

        if (body != null) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));
            try (OutputStream out = conn.getOutputStream()) {
                out.write(bytes);
            }
        }

        int code = conn.getResponseCode();
        String responseBody = readResponseBody(conn, code);
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + ": " + responseBody);
        }
        return responseBody;
    }

    private static String readResponseBody(HttpsURLConnection conn, int code) throws IOException {
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) {
            return "";
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private static String encode(String value) throws IOException {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static String firstNonEmpty(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.optString(key, "").trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static CommunityDownloadAsset firstDownloadAsset(JSONObject object, String fallbackFormat) {
        String[][] keys = {
                {"glbUrl", ".glb"},
                {"modelUrl", extensionForFormat(fallbackFormat)},
                {"url", extensionForFormat(fallbackFormat)},
                {"downloadUrl", extensionForFormat(fallbackFormat)},
                {"assetUrl", extensionForFormat(fallbackFormat)},
                {"zipUrl", ".zip"}
        };
        for (String[] key : keys) {
            String value = object.optString(key[0], "").trim();
            if (!value.isEmpty()) {
                return new CommunityDownloadAsset(value, extensionForUrl(value, key[1]));
            }
        }
        return null;
    }

    private static boolean isInvalidActionError(IOException error) {
        String message = error.getMessage();
        return message != null && message.toLowerCase().contains("invalid action");
    }

    private static String extensionForUrl(String url, String fallbackFormat) {
        String lower = url == null ? "" : url.toLowerCase();
        int queryStart = lower.indexOf('?');
        if (queryStart >= 0) {
            lower = lower.substring(0, queryStart);
        }
        if (lower.endsWith(".zip")) {
            return ".zip";
        }
        if (lower.endsWith(".gltf")) {
            return ".gltf";
        }
        if (lower.endsWith(".glb")) {
            return ".glb";
        }
        return extensionForFormat(fallbackFormat);
    }

    private static String extensionForFormat(String format) {
        String normalized = format == null ? "glb" : format.trim().toLowerCase();
        if (normalized.startsWith(".")) {
            return normalized;
        }
        return "." + (normalized.isEmpty() ? "glb" : normalized);
    }

    static final class CommunityDownloadAsset {
        final String url;
        final String extension;

        CommunityDownloadAsset(String url, String extension) {
            this.url = url;
            this.extension = extension == null || extension.trim().isEmpty() ? ".glb" : extension;
        }
    }
}
