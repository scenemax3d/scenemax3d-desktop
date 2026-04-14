package com.scenemax.desktop.ai.gemma.service;

import com.scenemax.desktop.ai.gemma.install.GemmaInstallManifest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LocalGemmaHttpService {

    private static final int STATUS_DLL_NOT_FOUND = -1073741515; // 0xC0000135

    private HttpServer server;
    private int port;
    private String endpointUrl;
    private String lastError;
    private GemmaInstallManifest manifest;

    public synchronized void start(URI endpointUri, GemmaInstallManifest manifest) {
        stop();

        if (endpointUri == null) {
            throw new IllegalArgumentException("Local Gemma endpoint is required.");
        }
        if (manifest == null) {
            throw new IllegalArgumentException("Gemma install manifest is required.");
        }

        try {
            Path runtimePath = Path.of(manifest.getRuntimePath());
            Path modelPath = Path.of(manifest.getModelPath());
            if (!Files.exists(runtimePath)) {
                throw new IOException("Runtime executable not found: " + runtimePath);
            }
            if (!Files.exists(modelPath)) {
                throw new IOException("Model file not found: " + modelPath);
            }
            preflightRuntime(runtimePath, modelPath);

            this.manifest = manifest;
            String host = endpointUri.getHost() == null || endpointUri.getHost().isBlank() ? "127.0.0.1" : endpointUri.getHost();
            port = endpointUri.getPort() >= 0 ? endpointUri.getPort() : 8787;

            server = HttpServer.create(new InetSocketAddress(host, port), 0);
            server.createContext("/v1/models", new ModelsHandler());
            server.createContext("/v1/chat/completions", new ChatHandler());
            server.createContext("/health", new HealthHandler());
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();

            port = server.getAddress().getPort();
            endpointUrl = "http://" + host + ":" + port + "/v1/chat/completions";
            lastError = null;
        } catch (Exception ex) {
            lastError = ex.getMessage();
            stop();
            throw new RuntimeException(ex);
        }
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public synchronized boolean isRunning() {
        return server != null;
    }

    public synchronized int getPort() {
        return port;
    }

    public synchronized String getEndpointUrl() {
        return endpointUrl;
    }

    public synchronized String getLastError() {
        return lastError;
    }

    private final class ModelsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            JSONObject response = new JSONObject()
                    .put("object", "list")
                    .put("data", new JSONArray().put(new JSONObject()
                            .put("id", manifest.getModelId())
                            .put("object", "model")
                            .put("owned_by", "scenemax-local")));
            writeJson(exchange, 200, response);
        }
    }

    private final class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            JSONObject response = new JSONObject()
                    .put("ok", isRunning())
                    .put("modelId", manifest.getModelId());
            writeJson(exchange, 200, response);
        }
    }

    private final class ChatHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                JSONObject request = new JSONObject(readBody(exchange.getRequestBody()));
                String prompt = buildPrompt(request);
                String output = runLiteRtPrompt(prompt);
                JSONObject response = new JSONObject()
                        .put("id", "chatcmpl-scenemax-local")
                        .put("object", "chat.completion")
                        .put("choices", new JSONArray().put(new JSONObject()
                                .put("index", 0)
                                .put("message", new JSONObject()
                                        .put("role", "assistant")
                                        .put("content", output))
                                .put("finish_reason", "stop")));
                writeJson(exchange, 200, response);
            } catch (Exception ex) {
                lastError = ex.getMessage();
                writeJson(exchange, 500, new JSONObject()
                        .put("error", new JSONObject().put("message", ex.getMessage())));
            }
        }
    }

    private synchronized String runLiteRtPrompt(String prompt) throws Exception {
        List<String> command = new ArrayList<>();
        Path runtimePath = Path.of(manifest.getRuntimePath());
        String runtimeName = runtimePath.getFileName().toString().toLowerCase();
        Path promptFile = createPromptFile(runtimePath, prompt);
        if (runtimeName.endsWith(".cmd") || runtimeName.endsWith(".bat")) {
            command.add("cmd.exe");
            command.add("/c");
            command.add(runtimePath.toString());
        } else {
            command.add(runtimePath.toString());
        }
        command.add("--min_log_level");
        command.add("4");
        command.add("run");
        command.add(manifest.getModelPath());
        command.add("--backend=cpu");
        command.add("-f");
        command.add(promptFile.toString());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        if (runtimePath.getParent() != null) {
            builder.directory(runtimePath.getParent().toFile());
        }
        String output;
        try {
            Process process = builder.start();
            output = readProcess(process.getInputStream());
            int exitCode = waitForProcess(process, "Local Gemma execution was interrupted.");
            if (exitCode == STATUS_DLL_NOT_FOUND) {
                throw buildMissingDllException(runtimePath, Path.of(manifest.getModelPath()));
            }
            if (exitCode != 0) {
                String details = output == null ? "" : output.trim();
                if (details.isEmpty()) {
                    details = "No stdout/stderr was captured. Exit code=" + exitCode
                            + ", runtime=" + runtimePath + ", model=" + manifest.getModelPath()
                            + ". This can happen when the LiteRT-LM executable rejects the prompt/flags or Windows blocks startup.";
                }
                throw new IOException("Local Gemma process failed: " + details);
            }
        } finally {
            Files.deleteIfExists(promptFile);
        }
        return extractAssistantText(output, prompt);
    }

    private void preflightRuntime(Path runtimePath, Path modelPath) throws Exception {
        String runtimeName = runtimePath.getFileName().toString().toLowerCase();
        if (runtimeName.endsWith(".cmd") || runtimeName.endsWith(".bat")) {
            return;
        }

        ProcessBuilder builder = new ProcessBuilder(runtimePath.toString(), "--help");
        builder.redirectErrorStream(true);
        if (runtimePath.getParent() != null) {
            builder.directory(runtimePath.getParent().toFile());
        }
        Process process = builder.start();
        String output = readProcess(process.getInputStream());
        int exitCode = waitForProcess(process, "Local Gemma preflight check was interrupted.");
        if (exitCode == STATUS_DLL_NOT_FOUND) {
            throw buildMissingDllException(runtimePath, modelPath);
        }
        if (exitCode != 0 && (output == null || output.trim().isEmpty())) {
            lastError = "Local Gemma runtime preflight exited with code " + exitCode + ". Runtime=" + runtimePath;
        }
    }

    private int waitForProcess(Process process, String interruptedMessage) throws IOException {
        try {
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return 0;
            }
            return process.exitValue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException(interruptedMessage);
        }
    }

    private IOException buildMissingDllException(Path runtimePath, Path modelPath) {
        return new IOException(
                "Local Gemma could not start because Windows reported a missing DLL dependency (exit code 0xC0000135). "
                        + "The LiteRT-LM Windows binary usually needs the Microsoft Visual C++ runtime. "
                        + "Use 'Install VC++ Runtime' in Settings > Local Gemma, or install/repair the Microsoft Visual C++ Redistributable 2015-2022 x64 manually, then start Local Gemma again. "
                        + "Runtime=" + runtimePath + ", model=" + modelPath);
    }

    private Path createPromptFile(Path runtimePath, String prompt) throws IOException {
        Path dir = runtimePath.getParent() != null ? runtimePath.getParent() : Path.of(".");
        Path promptFile = Files.createTempFile(dir, "scenemax-gemma-", ".prompt.txt");
        Files.writeString(promptFile, prompt == null ? "" : prompt, StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING);
        return promptFile;
    }

    private String buildPrompt(JSONObject request) {
        StringBuilder prompt = new StringBuilder();
        JSONArray messages = request.optJSONArray("messages");
        if (messages != null) {
            for (int i = 0; i < messages.length(); i++) {
                JSONObject message = messages.getJSONObject(i);
                String role = message.optString("role", "user");
                String content = sanitizePromptPart(message.optString("content", ""));
                if (!content.isBlank()) {
                    if (prompt.length() > 0) {
                        prompt.append(' ');
                    }
                    prompt.append(role.toUpperCase()).append(": ").append(content);
                }
            }
        }
        if (prompt.length() > 0) {
            prompt.append(' ');
        }
        prompt.append("ASSISTANT:");
        return prompt.toString();
    }

    private String extractAssistantText(String rawOutput, String prompt) {
        String raw = rawOutput == null ? "" : rawOutput.replace('\r', '\n');
        StringBuilder filtered = new StringBuilder();
        String[] lines = raw.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("INFO:")
                    || trimmed.startsWith("WARNING:")
                    || trimmed.startsWith("ERROR:")
                    || trimmed.startsWith("VERBOSE:")) {
                continue;
            }
            if (filtered.length() > 0) {
                filtered.append('\n');
            }
            filtered.append(trimmed);
        }

        String text = filtered.toString().trim();
        if (text.startsWith(prompt)) {
            text = text.substring(prompt.length()).trim();
        }
        int assistantIndex = text.lastIndexOf("ASSISTANT:");
        if (assistantIndex >= 0) {
            text = text.substring(assistantIndex + "ASSISTANT:".length()).trim();
        }
        return text;
    }

    private String sanitizePromptPart(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replaceAll(" +", " ")
                .trim();
    }

    private String readBody(InputStream stream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = stream.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private String readProcess(InputStream stream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = stream.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private void writeJson(HttpExchange exchange, int statusCode, JSONObject payload) throws IOException {
        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
