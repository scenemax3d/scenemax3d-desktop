package com.scenemax.desktop.ai;

import com.scenemax.desktop.ai.gemma.install.GemmaInstallManifest;
import com.scenemax.desktop.ai.gemma.install.GemmaModelVariant;
import com.scenemax.desktop.ai.gemma.service.LocalGemmaHttpService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LocalGemmaHttpServiceTest {

    @Test
    public void serviceExposesModelsAndChatCompletions() throws Exception {
        Path dir = Files.createTempDirectory("gemma-http-service");
        LocalGemmaHttpService service = new LocalGemmaHttpService();
        try {
            Path runtime = dir.resolve("fake_runtime.cmd");
            Files.writeString(runtime, "@echo off\r\necho ASSISTANT: local gemma is alive\r\n", StandardCharsets.UTF_8);
            Path model = dir.resolve("model.litertlm");
            Files.writeString(model, "fake-model", StandardCharsets.UTF_8);

            GemmaInstallManifest manifest = GemmaInstallManifest.now(
                    GemmaModelVariant.E2B,
                    model,
                    runtime,
                    "https://example.com/runtime.exe"
            );

            service.start(new URI("http://127.0.0.1:0/v1/chat/completions"), manifest);

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> modelsResponse = client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + service.getPort() + "/v1/models")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, modelsResponse.statusCode());
            assertTrue(modelsResponse.body().contains(GemmaModelVariant.E2B.getModelId()));

            JSONObject request = new JSONObject()
                    .put("messages", new JSONArray().put(new JSONObject()
                            .put("role", "user")
                            .put("content", "hello")));
            HttpResponse<String> chatResponse = client.send(
                    HttpRequest.newBuilder(URI.create(service.getEndpointUrl()))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(request.toString()))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, chatResponse.statusCode());
            assertTrue(chatResponse.body().contains("local gemma is alive"));
        } finally {
            service.stop();
            deleteRecursively(dir);
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            Files.walk(path)
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }
}
