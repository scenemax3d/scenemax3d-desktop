package com.scenemax.desktop.ai.gemma;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GemmaConsoleProtocolTest {

    @Test
    public void parseResponseUsesNativeToolCallsWhenBridgeProvidesThem() {
        LocalGemmaBridgeResponse response = new LocalGemmaBridgeResponse(
                "I can help with that.",
                List.of(new LocalGemmaToolCall(
                        "call_1",
                        "project.list_tree",
                        new JSONObject().put("base", "project"))),
                new JSONObject());

        GemmaConsoleProtocol.ParsedResponse parsed = GemmaConsoleProtocol.parseResponse(response);

        assertEquals("I can help with that.", parsed.getAssistantText());
        assertEquals(1, parsed.getToolCalls().size());
        assertEquals("project.list_tree", parsed.getToolCalls().get(0).getString("name"));
        assertEquals("project", parsed.getToolCalls().get(0).getJSONObject("arguments").getString("base"));
    }

    @Test
    public void parseResponseFallsBackToJsonInAssistantTextForLiteRt() {
        LocalGemmaBridgeResponse response = new LocalGemmaBridgeResponse(
                "{\"assistant\":\"Listing files now.\",\"tool_calls\":[{\"name\":\"project.list_tree\",\"arguments\":{\"base\":\"project\"}}]}",
                Collections.emptyList(),
                new JSONObject());

        GemmaConsoleProtocol.ParsedResponse parsed = GemmaConsoleProtocol.parseResponse(response);

        assertEquals("Listing files now.", parsed.getAssistantText());
        assertEquals(1, parsed.getToolCalls().size());
        assertEquals("project.list_tree", parsed.getToolCalls().get(0).getString("name"));
    }

    @Test
    public void buildSystemPromptEncouragesActionAndIncludesTools() {
        JSONArray tools = new JSONArray().put(new JSONObject()
                .put("name", "project.list_tree")
                .put("description", "List files")
                .put("inputSchema", new JSONObject().put("type", "object")));

        String prompt = GemmaConsoleProtocol.buildSystemPrompt(
                tools,
                "Active project:\n- Name: fighting_game");

        assertTrue(prompt.contains("Default to action."));
        assertTrue(prompt.contains("Never wrap the JSON in markdown fences."));
        assertTrue(prompt.contains("project.list_tree"));
        assertTrue(prompt.contains("search for it with project.search_files"));
        assertTrue(prompt.contains("Active project:\n- Name: fighting_game"));
    }
}
