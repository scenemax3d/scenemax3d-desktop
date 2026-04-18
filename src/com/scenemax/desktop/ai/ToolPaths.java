package com.scenemax.desktop.ai;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ToolPaths {

    private static final DateTimeFormatter STAGED_FILE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private ToolPaths() {
    }

    public static Path resolvePath(SceneMaxToolContext context, String rawPath, String base) throws IOException {
        if (rawPath == null || rawPath.trim().isEmpty()) {
            throw new IOException("Path is required");
        }

        Path candidate = Paths.get(rawPath.trim());
        Path resolved;
        if (candidate.isAbsolute()) {
            resolved = candidate.normalize().toAbsolutePath();
        } else {
            Path basePath = resolveBase(context, base);
            resolved = basePath.resolve(candidate).normalize().toAbsolutePath();
        }

        ensureAllowed(context, resolved);
        return resolved;
    }

    public static Path resolveBase(SceneMaxToolContext context, String base) throws IOException {
        if (base == null || base.isBlank() || "workspace".equalsIgnoreCase(base)) {
            return context.getWorkspaceRoot();
        }
        if ("scripts".equalsIgnoreCase(base)) {
            Path scriptsRoot = context.getScriptsRoot();
            if (scriptsRoot == null) {
                throw new IOException("No active scripts folder is available");
            }
            return scriptsRoot;
        }
        if ("resources".equalsIgnoreCase(base)) {
            Path resourcesRoot = context.getResourcesRoot();
            if (resourcesRoot == null) {
                throw new IOException("No active resources folder is available");
            }
            return resourcesRoot;
        }
        if ("project".equalsIgnoreCase(base)) {
            Path projectRoot = context.getActiveProjectRoot();
            if (projectRoot == null) {
                throw new IOException("No active project is available");
            }
            return projectRoot;
        }
        throw new IOException("Unsupported base path: " + base);
    }

    public static Path resolveFlexibleFileInput(SceneMaxToolContext context, Object rawValue,
                                                String base, String stagedNamePrefix) throws IOException {
        if (rawValue == null || JSONObject.NULL.equals(rawValue)) {
            throw new IOException("File input is required");
        }

        if (rawValue instanceof String) {
            return resolvePath((String) rawValue, context, base);
        }

        if (rawValue instanceof JSONObject) {
            return resolveFlexibleFileObject(context, (JSONObject) rawValue, base, stagedNamePrefix);
        }

        throw new IOException("Unsupported file input type: " + rawValue.getClass().getSimpleName());
    }

    private static Path resolvePath(String rawPath, SceneMaxToolContext context, String base) throws IOException {
        if (rawPath == null || rawPath.trim().isEmpty()) {
            throw new IOException("Path is required");
        }

        Path candidate = Paths.get(rawPath.trim());
        Path resolved;
        if (candidate.isAbsolute()) {
            resolved = candidate.normalize().toAbsolutePath();
        } else {
            Path basePath = resolveBase(context, base);
            resolved = basePath.resolve(candidate).normalize().toAbsolutePath();
        }

        ensureAllowed(context, resolved);
        return resolved;
    }

    private static Path resolveFlexibleFileObject(SceneMaxToolContext context, JSONObject fileObject,
                                                  String base, String stagedNamePrefix) throws IOException {
        String localPath = firstNonBlank(
                fileObject.optString("local_path", ""),
                fileObject.optString("path", ""),
                fileObject.optString("file", ""),
                fileObject.optString("uri", "")
        );
        String remoteUrl = firstNonBlank(
                fileObject.optString("download_url", ""),
                fileObject.optString("url", "")
        );
        String preferredName = firstNonBlank(
                fileObject.optString("name", ""),
                fileObject.optString("filename", ""),
                fileObject.optString("file_name", "")
        );
        String contentType = firstNonBlank(
                fileObject.optString("content_type", ""),
                fileObject.optString("mime_type", "")
        );

        if (!localPath.isEmpty()) {
            if (looksLikeRemoteUrl(localPath)) {
                remoteUrl = localPath;
            } else {
                Path localCandidate = Paths.get(localPath.trim());
                if (!localCandidate.isAbsolute()) {
                    return resolvePath(localPath, context, base);
                }
                localCandidate = localCandidate.normalize().toAbsolutePath();
                if (!Files.exists(localCandidate) || Files.isDirectory(localCandidate)) {
                    throw new IOException("Attached file path does not exist or is not a file: " + localCandidate);
                }
                if (isAllowed(context, localCandidate)) {
                    return localCandidate;
                }
                return stageLocalFile(context, localCandidate, preferredName, stagedNamePrefix);
            }
        }

        if (!remoteUrl.isEmpty()) {
            return stageRemoteFile(context, remoteUrl, preferredName, contentType, stagedNamePrefix);
        }

        throw new IOException("Attached file object is missing a usable local path or download URL.");
    }

    private static Path stageLocalFile(SceneMaxToolContext context, Path source,
                                       String preferredName, String stagedNamePrefix) throws IOException {
        Path target = allocateStagedFile(context, preferredName, stagedNamePrefix, source.getFileName() != null
                ? source.getFileName().toString()
                : "");
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private static Path stageRemoteFile(SceneMaxToolContext context, String remoteUrl, String preferredName,
                                        String contentType, String stagedNamePrefix) throws IOException {
        if (remoteUrl.startsWith("file:")) {
            try {
                Path source = Paths.get(URI.create(remoteUrl)).normalize().toAbsolutePath();
                if (!Files.exists(source) || Files.isDirectory(source)) {
                    throw new IOException("Attached file URI does not exist or is not a file: " + remoteUrl);
                }
                if (isAllowed(context, source)) {
                    return source;
                }
                return stageLocalFile(context, source, preferredName, stagedNamePrefix);
            } catch (IllegalArgumentException ex) {
                throw new IOException("Invalid file URI for attached file: " + remoteUrl, ex);
            }
        }

        if (!looksLikeRemoteUrl(remoteUrl)) {
            throw new IOException("Unsupported attached file URL: " + remoteUrl);
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(remoteUrl).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(30_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "SceneMaxMcp/1.0");

        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new IOException("Failed to download attached file: HTTP " + status);
        }

        Path target = allocateStagedFile(context, preferredName, stagedNamePrefix, extensionHint(preferredName, contentType, remoteUrl));
        Files.createDirectories(target.getParent());
        try (InputStream in = connection.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            connection.disconnect();
        }
        return target;
    }

    private static Path allocateStagedFile(SceneMaxToolContext context, String preferredName,
                                           String stagedNamePrefix, String fallbackSourceName) {
        String safeStem = sanitizeFileStem(firstNonBlank(preferredName, stagedNamePrefix, "attached_input"));
        String extension = extractExtension(firstNonBlank(preferredName, fallbackSourceName, ""));
        if (extension.isEmpty()) {
            extension = ".png";
        }
        String fileName = safeStem + "_" + STAGED_FILE_FORMATTER.format(LocalDateTime.now()) + extension;
        return context.getWorkspaceRoot()
                .resolve("tmp")
                .resolve("mcp-inputs")
                .resolve(fileName)
                .normalize()
                .toAbsolutePath();
    }

    private static String extensionHint(String preferredName, String contentType, String remoteUrl) {
        String extension = extractExtension(preferredName);
        if (!extension.isEmpty()) {
            return extension;
        }
        if (contentType != null) {
            String normalized = contentType.trim().toLowerCase(Locale.ROOT);
            if (normalized.contains("png")) {
                return ".png";
            }
            if (normalized.contains("jpeg") || normalized.contains("jpg")) {
                return ".jpg";
            }
            if (normalized.contains("webp")) {
                return ".webp";
            }
            if (normalized.contains("gif")) {
                return ".gif";
            }
        }
        return extractExtension(remoteUrl);
    }

    private static String extractExtension(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        int queryIndex = trimmed.indexOf('?');
        if (queryIndex >= 0) {
            trimmed = trimmed.substring(0, queryIndex);
        }
        int slashIndex = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
        int dotIndex = trimmed.lastIndexOf('.');
        if (dotIndex <= slashIndex || dotIndex < 0 || dotIndex == trimmed.length() - 1) {
            return "";
        }
        String extension = trimmed.substring(dotIndex).toLowerCase(Locale.ROOT);
        return extension.length() <= 8 ? extension : "";
    }

    private static String sanitizeFileStem(String value) {
        String candidate = value == null ? "" : value.trim();
        if (candidate.isEmpty()) {
            return "attached_input";
        }
        int slashIndex = Math.max(candidate.lastIndexOf('/'), candidate.lastIndexOf('\\'));
        if (slashIndex >= 0 && slashIndex < candidate.length() - 1) {
            candidate = candidate.substring(slashIndex + 1);
        }
        int dotIndex = candidate.lastIndexOf('.');
        if (dotIndex > 0) {
            candidate = candidate.substring(0, dotIndex);
        }
        candidate = candidate.replaceAll("[^A-Za-z0-9._-]+", "_");
        candidate = candidate.replaceAll("^_+", "").replaceAll("_+$", "");
        return candidate.isEmpty() ? "attached_input" : candidate;
    }

    private static boolean looksLikeRemoteUrl(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("http://")
                || normalized.startsWith("https://")
                || normalized.startsWith("file://");
    }

    private static boolean isAllowed(SceneMaxToolContext context, Path path) {
        for (Path root : context.getAllowedRoots()) {
            if (root != null && path.startsWith(root)) {
                return true;
            }
        }
        return false;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    public static void ensureAllowed(SceneMaxToolContext context, Path path) throws IOException {
        for (Path root : context.getAllowedRoots()) {
            if (root != null && path.startsWith(root)) {
                return;
            }
        }
        throw new IOException("Path is outside the allowed workspace/project roots: " + path);
    }
}
