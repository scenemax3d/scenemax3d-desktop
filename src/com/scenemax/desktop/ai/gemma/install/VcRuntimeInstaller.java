package com.scenemax.desktop.ai.gemma.install;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class VcRuntimeInstaller {

    public static final String VC_REDIST_X64_URL =
            "https://aka.ms/vs/17/release/vc_redist.x64.exe";
    public static final String INSTALLER_FILE_NAME = "vc_redist.x64.exe";

    private final Path installRoot;

    public VcRuntimeInstaller() {
        this(Paths.get("").toAbsolutePath().normalize());
    }

    public VcRuntimeInstaller(Path installRoot) {
        this.installRoot = installRoot.toAbsolutePath().normalize();
    }

    public Path download(GemmaInstallListener listener) throws IOException {
        Path target = getInstallerPath();
        Files.createDirectories(target.getParent());
        if (isDownloaded()) {
            emit(listener, 100, "Microsoft VC++ runtime installer is already downloaded.");
            return target;
        }
        downloadToFile(VC_REDIST_X64_URL, target, listener, 5, 100, "Downloading Microsoft VC++ runtime");
        emit(listener, 100, "Microsoft VC++ runtime installer is ready.");
        return target;
    }

    public Process launchInstaller(Path installerPath) throws IOException {
        if (installerPath == null || !Files.exists(installerPath)) {
            throw new IOException("VC++ runtime installer was not found: " + installerPath);
        }
        ProcessBuilder builder = new ProcessBuilder(installerPath.toString());
        if (installerPath.getParent() != null) {
            builder.directory(installerPath.getParent().toFile());
        }
        return builder.start();
    }

    public Path getInstallerPath() {
        return installRoot.resolve(Paths.get("AI", "runtime", "vcredist", INSTALLER_FILE_NAME));
    }

    public boolean isDownloaded() {
        Path installer = getInstallerPath();
        try {
            return Files.exists(installer) && Files.size(installer) > 0;
        } catch (IOException ex) {
            return false;
        }
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
        connection.setRequestProperty("User-Agent", "SceneMax VC++ Runtime Installer");
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
