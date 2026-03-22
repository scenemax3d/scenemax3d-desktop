package com.scenemaxeng.plugins.ide;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

public class GeminiApi {

    private static final String API_KEY = System.getenv("S3D_GEMINI_API_KEY");
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?key="+API_KEY; // Replace with the actual endpoint

    public static String post(String prompt, String sysInstruction) {
        if (API_KEY == null) {
            System.out.println("SceneMax3D - no Gemini API key found");
            return "";
        }

        OkHttpClient client = new OkHttpClient();
        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        JSONObject promptJson = new JSONObject();
        addSysInstruction(promptJson,sysInstruction);
        addGeneralSettings(promptJson);
        addSectionPart(promptJson, "contents", prompt);
        addSafetySettings(promptJson);

        RequestBody body = RequestBody.create(promptJson.toString(), JSON);
        Request request = new Request.Builder()
                .url(GEMINI_API_URL)
                //.addHeader("Authorization", "Bearer " + API_KEY)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return response.body().string();
            } else {
                System.err.println("Request failed: " + response.code() + " " + response.message());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    private static void addGeneralSettings(JSONObject promptJson) {

//        JSONObject settings = new JSONObject();
//        settings.put("temperature", 0);
//        promptJson.put("generationConfig", settings);
    }

    private static void addSafetySettings(JSONObject promptJson) {

        JSONArray settingsList = new JSONArray();
        settingsList.put(new JSONObject("{\"category\": \"HARM_CATEGORY_SEXUALLY_EXPLICIT\", \"threshold\": \"BLOCK_NONE\"}"));
        settingsList.put(new JSONObject("{\"category\": \"HARM_CATEGORY_HATE_SPEECH\", \"threshold\": \"BLOCK_NONE\"}"));
        settingsList.put(new JSONObject("{\"category\": \"HARM_CATEGORY_HARASSMENT\", \"threshold\": \"BLOCK_NONE\"}"));
        settingsList.put(new JSONObject("{\"category\": \"HARM_CATEGORY_DANGEROUS_CONTENT\", \"threshold\": \"BLOCK_NONE\"}"));

        promptJson.put("safety_settings", settingsList);
    }

    private static void addSysInstruction(JSONObject promptJson, String sysInstruction) {
        // "system_instruction"
        JSONObject sysInstruct = new JSONObject();
        JSONObject parts = new JSONObject();
        parts.put("text", sysInstruction);
        sysInstruct.put("parts", parts);
        promptJson.put("system_instruction", sysInstruct);
    }

    private static void addSectionPart(JSONObject promptJson, String sectionName, String textPart) {
        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();
        JSONArray parts = new JSONArray();
        JSONObject text = new JSONObject();
        text.put("text", textPart);
        parts.put(text);
        content.put("parts", parts);
        contents.put(content);

        promptJson.put(sectionName, contents); //
    }

}
