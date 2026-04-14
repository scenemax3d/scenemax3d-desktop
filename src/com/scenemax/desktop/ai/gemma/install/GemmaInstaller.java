package com.scenemax.desktop.ai.gemma.install;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GemmaInstaller {

    public static final String LITERT_RUNTIME_URL =
            "https://github.com/google-ai-edge/LiteRT-LM/releases/latest/download/litert_lm_main.windows_x86_64.exe";
    public static final String RUNTIME_FILE_NAME = "litert_lm_main.windows_x86_64.exe";

    private final Path installRoot;

    public GemmaInstaller() {
        this(Paths.get("").toAbsolutePath().normalize());
    }

    public GemmaInstaller(Path installRoot) {
        this.installRoot = installRoot.toAbsolutePath().normalize();
    }

    public GemmaInstallManifest install(GemmaModelVariant variant, GemmaInstallListener listener) throws IOException {
        if (variant == null) {
            throw new IllegalArgumentException("Gemma model variant is required.");
        }

        Path aiRoot = installRoot.resolve("AI");
        Path runtimeDir = aiRoot.resolve(Paths.get("runtime", "litert-lm"));
        Path modelsDir = aiRoot.resolve("models");
        Path variantDir = modelsDir.resolve(variant.getModelId());
        Path runtimeFile = runtimeDir.resolve(RUNTIME_FILE_NAME);
        Path modelFile = variantDir.resolve(variant.getFileName());
        Path manifestFile = modelsDir.resolve("installed.json");

        Files.createDirectories(runtimeDir);
        Files.createDirectories(variantDir);

        emit(listener, 2, "Preparing install folders...");
        if (!Files.exists(runtimeFile) || Files.size(runtimeFile) == 0) {
            downloadToFile(LITERT_RUNTIME_URL, runtimeFile, listener, 5, 20, "Downloading LiteRT-LM runtime");
        } else {
            emit(listener, 20, "LiteRT-LM runtime already installed.");
        }

        if (!Files.exists(modelFile) || Files.size(modelFile) == 0) {
            downloadToFile(variant.getDownloadUrl(), modelFile, listener, 22, 96, "Downloading " + variant.getDisplayName());
        } else {
            emit(listener, 96, variant.getDisplayName() + " is already installed.");
        }

        GemmaInstallManifest manifest = GemmaInstallManifest.now(variant, modelFile, runtimeFile, LITERT_RUNTIME_URL);
        manifest.save(manifestFile);
        emit(listener, 100, "Gemma installation finished.");
        return manifest;
    }

    public GemmaInstallManifest loadInstalledManifest() {
        try {
            return GemmaInstallManifest.load(getManifestPath());
        } catch (IOException ignored) {
            return null;
        }
    }

    public Path getManifestPath() {
        return installRoot.resolve(Paths.get("AI", "models", "installed.json"));
    }

    public Path getInstallRoot() {
        return installRoot;
    }

    private void downloadToFile(String url, Path target, GemmaInstallListener listener, int fromPercent, int toPercent, String label) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName().toString() + ".part");
        HttpURLConnection connection = open(url);
        long totalBytes = connection.getContentLengthLong();

        try (InputStream in = connection.getInputStream();
             OutputStream out = Files.newOutputStream(temp)) {
            byte[] buffer = new byte[64 * 1024];
            long copied = 0;
            int read;
            emit(listener, fromPercent, label + "...");
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
                copied += read;
                int percent = totalBytes > 0
                        ? fromPercent + (int) (((double) copied / (double) totalBytes) * (toPercent - fromPercent))
                        : fromPercent;
                emit(listener, Math.min(toPercent, Math.max(fromPercent, percent)), label + "...");
            }
        } catch (IOException ex) {
            Files.deleteIfExists(temp);
            throw ex;
        }

        Files.deleteIfExists(target);
        Files.move(temp, target);
    }

    private HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "SceneMax Gemma Installer");
        connection.setRequestProperty("Accept", "*/*");
        int code = connection.getResponseCode();
        if (code >= 200 && code < 300) {
            return connection;
        }
        throw new IOException("HTTP " + code + " while downloading " + url);
    }

    private void emit(GemmaInstallListener listener, int percent, String message) {
        if (listener != null) {
            listener.onProgress(new GemmaInstallProgress(percent, message));
        }
    }
}
