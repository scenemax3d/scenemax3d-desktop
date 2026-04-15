package com.scenemax.desktop.ai.gemma;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GemmaConsoleProtocol {

    private GemmaConsoleProtocol() {
    }

    public static String buildSystemPrompt(JSONArray toolsDescription, String ideContext) {
        JSONArray tools = toolsDescription == null ? new JSONArray() : toolsDescription;
        String context = ideContext == null ? "" : ideContext.trim();
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are the SceneMax IDE assistant. ")
                .append("Complete the user's request inside the IDE whenever you can.\n\n")
                .append("Default to action. Use the available tools when they can move the task forward.\n")
                .append("Do not ask for clarification if a reasonable default, the current IDE context, or a quick tool call can answer it.\n")
                .append("Ask a clarifying question only when the request is destructive, when multiple materially different outcomes are equally likely, or when a required detail cannot be discovered with tools.\n")
                .append("Prefer short progress updates and tool use over handing work back to the user.\n")
                .append("If the user mentions an asset, project, file, or scene by name, search for it with project.search_files, project.list_tree, or project.get_context before asking for a path.\n")
                .append("If the user describes game logic like health, power, winning, or triggers, inspect the relevant scripts with project.search_text and file.read before asking how it works.\n")
                .append("Use the IDE context below as real context. Do not ask for the active project or active document if it is already provided.\n\n");
        if (!context.isBlank()) {
            prompt.append("Current IDE context:\n")
                    .append(context)
                    .append("\n\n");
        }
        prompt.append("When you want to use tools, reply with JSON only in this exact shape:\n")
                .append("{\"assistant\":\"short progress note\",\"tool_calls\":[{\"name\":\"tool.name\",\"arguments\":{}}]}\n")
                .append("When the request is complete or no tool is needed, reply with JSON only:\n")
                .append("{\"assistant\":\"your answer\",\"tool_calls\":[]}\n")
                .append("Never wrap the JSON in markdown fences.\n\n")
                .append("Available tools:\n")
                .append(tools.toString(2));
        return prompt.toString();
    }

    public static ParsedResponse parseResponse(LocalGemmaBridgeResponse response) {
        if (response == null) {
            return new ParsedResponse("", Collections.emptyList());
        }

        ParsedResponse textResponse = parseTextResponse(response.getText());
        List<JSONObject> nativeToolCalls = toJsonToolCalls(response.getToolCalls());
        if (!nativeToolCalls.isEmpty()) {
            return new ParsedResponse(textResponse.getAssistantText(), nativeToolCalls);
        }
        return textResponse;
    }

    private static ParsedResponse parseTextResponse(String text) {
        if (text == null) {
            return new ParsedResponse("", Collections.emptyList());
        }

        String trimmed = text.trim();
        JSONObject json = tryParseJson(trimmed);
        if (json == null && trimmed.startsWith("```")) {
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) {
                json = tryParseJson(trimmed.substring(start, end + 1));
            }
        }
        if (json == null) {
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) {
                json = tryParseJson(trimmed.substring(start, end + 1));
            }
        }
        if (json == null) {
            return new ParsedResponse(trimmed, Collections.emptyList());
        }

        List<JSONObject> toolCalls = new ArrayList<>();
        JSONArray calls = json.optJSONArray("tool_calls");
        if (calls != null) {
            for (int i = 0; i < calls.length(); i++) {
                JSONObject toolCall = calls.optJSONObject(i);
                if (toolCall != null) {
                    toolCalls.add(toolCall);
                }
            }
        }
        return new ParsedResponse(json.optString("assistant", ""), toolCalls);
    }

    private static JSONObject tryParseJson(String text) {
        try {
            return new JSONObject(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<JSONObject> toJsonToolCalls(List<LocalGemmaToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return Collections.emptyList();
        }

        List<JSONObject> result = new ArrayList<>();
        for (LocalGemmaToolCall toolCall : toolCalls) {
            if (toolCall == null || toolCall.getName() == null || toolCall.getName().isBlank()) {
                continue;
            }
            result.add(new JSONObject()
                    .put("name", toolCall.getName())
                    .put("arguments", toolCall.getArguments() == null ? new JSONObject() : toolCall.getArguments()));
        }
        return result;
    }

    public static final class ParsedResponse {
        private final String assistantText;
        private final List<JSONObject> toolCalls;

        public ParsedResponse(String assistantText, List<JSONObject> toolCalls) {
            this.assistantText = assistantText == null ? "" : assistantText;
            this.toolCalls = toolCalls == null ? Collections.emptyList() : new ArrayList<>(toolCalls);
        }

        public String getAssistantText() {
            return assistantText;
        }

        public List<JSONObject> getToolCalls() {
            return new ArrayList<>(toolCalls);
        }
    }
}
