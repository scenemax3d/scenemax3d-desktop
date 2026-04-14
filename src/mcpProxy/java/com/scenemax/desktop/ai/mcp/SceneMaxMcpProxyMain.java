package com.scenemax.desktop.ai.mcp;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class SceneMaxMcpProxyMain {

    private SceneMaxMcpProxyMain() {
    }

    public static void main(String[] args) throws Exception {
        String endpoint = args != null && args.length > 0 ? args[0] : System.getenv("SCENEMAX_MCP_URL");
        if (endpoint == null || endpoint.trim().isEmpty()) {
            System.err.println("SceneMax MCP proxy requires the IDE MCP endpoint URL as the first argument.");
            System.exit(1);
            return;
        }

        run(endpoint.trim(), System.in, System.out);
    }

    static void run(String endpoint, InputStream in, OutputStream out) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            String requestLine = line.trim();
            if (requestLine.isEmpty()) {
                continue;
            }

            String response = forward(endpoint, requestLine);
            if (response == null || response.isBlank()) {
                continue;
            }

            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.write('\n');
            out.flush();
        }
    }

    private static String forward(String endpoint, String requestLine) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json, text/event-stream");
        connection.setRequestProperty("MCP-Protocol-Version", "2025-03-26");

        byte[] requestBytes = requestLine.getBytes(StandardCharsets.UTF_8);
        try (OutputStream body = connection.getOutputStream()) {
            body.write(requestBytes);
        }

        int statusCode = connection.getResponseCode();
        InputStream bodyStream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String bodyText = readFully(bodyStream);

        if (statusCode == HttpURLConnection.HTTP_ACCEPTED || bodyText.isBlank()) {
            return null;
        }

        if (statusCode >= 200 && statusCode < 300) {
            return compactJson(bodyText);
        }

        return buildErrorResponse(requestLine, statusCode, bodyText);
    }

    private static String buildErrorResponse(String requestLine, int statusCode, String bodyText) {
        String message = bodyText == null || bodyText.isBlank()
                ? "SceneMax MCP proxy request failed with HTTP " + statusCode
                : "SceneMax MCP proxy request failed with HTTP " + statusCode + ": " + bodyText.trim();
        String id = extractJsonRpcId(requestLine);
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"error\":{\"code\":-32603,\"message\":\""
                + escapeJson(message) + "\"}}";
    }

    private static String readFully(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }

        try (InputStream input = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    private static String extractJsonRpcId(String requestLine) {
        if (requestLine == null) {
            return "null";
        }

        int idIndex = requestLine.indexOf("\"id\"");
        if (idIndex < 0) {
            return "null";
        }

        int colonIndex = requestLine.indexOf(':', idIndex);
        if (colonIndex < 0) {
            return "null";
        }

        int valueStart = colonIndex + 1;
        while (valueStart < requestLine.length() && Character.isWhitespace(requestLine.charAt(valueStart))) {
            valueStart++;
        }
        if (valueStart >= requestLine.length()) {
            return "null";
        }

        char startChar = requestLine.charAt(valueStart);
        if (startChar == '"') {
            StringBuilder sb = new StringBuilder();
            sb.append('"');
            boolean escaped = false;
            for (int i = valueStart + 1; i < requestLine.length(); i++) {
                char ch = requestLine.charAt(i);
                sb.append(ch);
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (ch == '\\') {
                    escaped = true;
                    continue;
                }
                if (ch == '"') {
                    return sb.toString();
                }
            }
            return "null";
        }

        int valueEnd = valueStart;
        while (valueEnd < requestLine.length()) {
            char ch = requestLine.charAt(valueEnd);
            if (ch == ',' || ch == '}' || Character.isWhitespace(ch)) {
                break;
            }
            valueEnd++;
        }

        String value = requestLine.substring(valueStart, valueEnd).trim();
        if (value.isEmpty()) {
            return "null";
        }
        return value;
    }

    private static String escapeJson(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
            }
        }
        return sb.toString();
    }

    private static String compactJson(String text) {
        String raw = text == null ? "" : text.trim();
        StringBuilder sb = new StringBuilder(raw.length());
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);

            if (inString) {
                sb.append(ch);
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }

            if (Character.isWhitespace(ch)) {
                continue;
            }

            sb.append(ch);
            if (ch == '"') {
                inString = true;
            }
        }

        return sb.toString();
    }
}
