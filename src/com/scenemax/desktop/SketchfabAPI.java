package com.scenemax.desktop;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Client for the Sketchfab Data API v3.
 * Search is unauthenticated; download requires an API token.
 */
public class SketchfabAPI {

    private static final String BASE_URL = "https://api.sketchfab.com/v3";
    private static final int RESULTS_PER_PAGE = 24;
    public static final int DEFAULT_LOW_POLY_FACE_COUNT = 10000;

    // ── Category list ────────────────────────────────────────────────
    public static final String[][] CATEGORIES = {
            {"All Categories", ""},
            {"Animals & Pets", "animals-pets"},
            {"Architecture", "architecture"},
            {"Art & Abstract", "art-abstract"},
            {"Cars & Vehicles", "cars-vehicles"},
            {"Characters & Creatures", "characters-creatures"},
            {"Cultural Heritage & History", "cultural-heritage-history"},
            {"Electronics & Gadgets", "electronics-gadgets"},
            {"Fashion & Style", "fashion-style"},
            {"Food & Drink", "food-drink"},
            {"Furniture & Home", "furniture-home"},
            {"Music", "music"},
            {"Nature & Plants", "nature-plants"},
            {"People", "people"},
            {"Places & Travel", "places-travel"},
            {"Science & Technology", "science-technology"},
            {"Sports & Fitness", "sports-fitness"},
            {"Weapons & Military", "weapons-military"},
    };

    public static final String[][] LICENSES = {
            {"All Free Licenses", ""},
            {"CC0 (Public Domain)", "cc0"},
            {"CC BY", "by"},
            {"CC BY-SA", "by-sa"},
            {"CC BY-ND", "by-nd"},
            {"CC BY-NC", "by-nc"},
            {"CC BY-NC-SA", "by-nc-sa"},
            {"CC BY-NC-ND", "by-nc-nd"},
    };

    public static final String[][] SORT_OPTIONS = {
            {"Most Relevant", ""},
            {"Most Liked", "-likeCount"},
            {"Most Viewed", "-viewCount"},
            {"Newest", "-publishedAt"},
            {"Oldest", "publishedAt"},
    };

    // ── Search ───────────────────────────────────────────────────────

    /**
     * Search for downloadable models.
     *
     * @param query         search text (can be empty)
     * @param category      category slug (empty = all)
     * @param license       license slug (empty = all)
     * @param sortBy        sort field (empty = relevance)
     * @param maxFaceCount  max faces (0 = no limit)
     * @param animatedOnly  true to only show animated models
     * @param staffPicked   true to only show staff picks
     * @param cursor        pagination cursor (null for first page)
     * @return raw JSON response
     */
    public static JSONObject search(String query, String category, String license,
                                    String sortBy, int maxFaceCount,
                                    boolean animatedOnly, boolean staffPicked,
                                    String cursor) throws IOException {
        StringBuilder url = new StringBuilder(BASE_URL + "/search?type=models&downloadable=true");

        if (query != null && !query.trim().isEmpty()) {
            url.append("&q=").append(URLEncoder.encode(query.trim(), "UTF-8"));
        }
        if (category != null && !category.isEmpty()) {
            url.append("&categories=").append(URLEncoder.encode(category, "UTF-8"));
        }
        if (license != null && !license.isEmpty()) {
            url.append("&license=").append(URLEncoder.encode(license, "UTF-8"));
        }
        if (sortBy != null && !sortBy.isEmpty()) {
            url.append("&sort_by=").append(URLEncoder.encode(sortBy, "UTF-8"));
        }
        if (maxFaceCount > 0) {
            url.append("&max_face_count=").append(maxFaceCount);
        }
        if (animatedOnly) {
            url.append("&animated=true");
        }
        if (staffPicked) {
            url.append("&staffpicked=true");
        }
        url.append("&count=").append(RESULTS_PER_PAGE);
        if (cursor != null && !cursor.isEmpty()) {
            url.append("&cursor=").append(URLEncoder.encode(cursor, "UTF-8"));
        }

        String json = httpsGet(url.toString(), null);
        return new JSONObject(json);
    }

    // ── Download ─────────────────────────────────────────────────────

    /**
     * Get the temporary download URLs for a model (requires auth).
     *
     * @param uid      model UID
     * @param apiToken Sketchfab OAuth access token or API token
     * @return JSON with format keys (for example gltf, glb, usdz) each containing url + size
     */
    public static JSONObject getDownloadInfo(String uid, String apiToken) throws IOException {
        String url = BASE_URL + "/models/" + uid + "/download";
        String json = httpsGet(url, apiToken);
        return new JSONObject(json);
    }

    /**
     * Download a file from a URL to a local path, reporting progress.
     *
     * @param downloadUrl the temporary download URL
     * @param destFile    local file to write to
     * @param fileSize    expected size in bytes (for progress, 0 if unknown)
     * @param onProgress  callback with percentage (0-100), called on background thread
     */
    public static void downloadFile(String downloadUrl, File destFile, long fileSize,
                                    Consumer<Integer> onProgress) throws IOException {
        URL url = new URL(downloadUrl);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setDoInput(true);
        conn.connect();

        long totalSize = fileSize > 0 ? fileSize : conn.getContentLengthLong();

        try (InputStream in = conn.getInputStream();
             FileOutputStream out = new FileOutputStream(destFile)) {
            byte[] buffer = new byte[8192];
            long downloaded = 0;
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                downloaded += bytesRead;
                if (totalSize > 0 && onProgress != null) {
                    int pct = (int) (downloaded * 100 / totalSize);
                    onProgress.accept(Math.min(pct, 100));
                }
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /**
     * Find the best thumbnail URL from a model's thumbnails object.
     * Prefers ~720px width for gallery cards.
     */
    public static String getThumbnailUrl(JSONObject model, int preferredWidth) {
        if (!model.has("thumbnails")) return null;
        JSONObject thumbs = model.getJSONObject("thumbnails");
        if (!thumbs.has("images")) return null;
        JSONArray images = thumbs.getJSONArray("images");

        String bestUrl = null;
        int bestDiff = Integer.MAX_VALUE;
        for (int i = 0; i < images.length(); i++) {
            JSONObject img = images.getJSONObject(i);
            int w = img.optInt("width", 0);
            String u = img.optString("url", null);
            if (u != null) {
                int diff = Math.abs(w - preferredWidth);
                if (diff < bestDiff) {
                    bestDiff = diff;
                    bestUrl = u;
                }
            }
        }
        return bestUrl;
    }

    /**
     * Format a face count for display (e.g. 12345 → "12.3k").
     */
    public static String formatFaceCount(int count) {
        if (count < 1000) return String.valueOf(count);
        if (count < 1000000) return String.format("%.1fk", count / 1000.0);
        return String.format("%.1fM", count / 1000000.0);
    }

    /**
     * Pick the best importable download format for SceneMax.
     * Prefers a direct GLB when present, otherwise falls back to Sketchfab's glTF archive.
     */
    public static JSONObject getPreferredDownloadFormat(JSONObject downloadInfo) {
        if (downloadInfo == null) return null;
        if (downloadInfo.has("glb")) {
            return downloadInfo.optJSONObject("glb");
        }
        if (downloadInfo.has("gltf")) {
            return downloadInfo.optJSONObject("gltf");
        }
        return null;
    }

    public static String extractCursor(String pagingUrl) {
        if (pagingUrl == null || pagingUrl.isEmpty()) return null;
        int queryStart = pagingUrl.indexOf('?');
        if (queryStart < 0 || queryStart == pagingUrl.length() - 1) return null;
        String query = pagingUrl.substring(queryStart + 1);
        String[] parts = query.split("&");
        for (String part : parts) {
            int equals = part.indexOf('=');
            if (equals <= 0) continue;
            String key = part.substring(0, equals);
            if (!"cursor".equals(key)) continue;
            String value = part.substring(equals + 1);
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        }
        return null;
    }

    private static String httpsGet(String urlStr, String apiToken) throws IOException {
        if (apiToken == null || apiToken.isEmpty()) {
            return httpsGetWithAuthorization(urlStr, null);
        }

        String trimmedToken = apiToken.trim();
        if (trimmedToken.startsWith("Bearer ") || trimmedToken.startsWith("Token ")) {
            return httpsGetWithAuthorization(urlStr, trimmedToken);
        }

        IOException bearerFailure = null;
        try {
            return httpsGetWithAuthorization(urlStr, "Bearer " + trimmedToken);
        } catch (IOException e) {
            bearerFailure = e;
        }

        try {
            return httpsGetWithAuthorization(urlStr, "Token " + trimmedToken);
        } catch (IOException tokenFailure) {
            tokenFailure.addSuppressed(bearerFailure);
            throw tokenFailure;
        }
    }

    private static String httpsGetWithAuthorization(String urlStr, String authorizationHeader) throws IOException {
        URL url = new URL(urlStr);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        if (authorizationHeader != null && !authorizationHeader.isEmpty()) {
            conn.setRequestProperty("Authorization", authorizationHeader);
        }
        conn.setDoInput(true);
        conn.setUseCaches(false);
        conn.connect();

        int code = conn.getResponseCode();
        String body = readResponseBody(conn, code);
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + ": " + body);
        }
        return body;
    }

    private static String readResponseBody(HttpsURLConnection conn, int code) throws IOException {
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return "";

        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }
}
