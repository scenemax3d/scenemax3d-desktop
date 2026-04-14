package com.scenemax.desktop.ai.mcp;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SceneMaxMcpLogEntry {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public final LocalDateTime time;
    public final String method;
    public final String path;
    public final int statusCode;
    public final String rpcMethod;
    public final String toolName;
    public final String requestId;
    public final String summary;
    public final String remoteAddress;

    public SceneMaxMcpLogEntry(LocalDateTime time, String method, String path, int statusCode,
                               String rpcMethod, String toolName, String requestId,
                               String summary, String remoteAddress) {
        this.time = time;
        this.method = method;
        this.path = path;
        this.statusCode = statusCode;
        this.rpcMethod = rpcMethod;
        this.toolName = toolName;
        this.requestId = requestId;
        this.summary = summary;
        this.remoteAddress = remoteAddress;
    }

    public String formatLine() {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(time.format(FORMATTER)).append("] ");
        sb.append(method).append(' ').append(path).append(' ');
        sb.append(statusCode);
        if (rpcMethod != null && !rpcMethod.isBlank()) {
            sb.append(" rpc=").append(rpcMethod);
        }
        if (toolName != null && !toolName.isBlank()) {
            sb.append(" tool=").append(toolName);
        }
        if (requestId != null && !requestId.isBlank()) {
            sb.append(" id=").append(requestId);
        }
        if (summary != null && !summary.isBlank()) {
            sb.append(" - ").append(summary);
        }
        if (remoteAddress != null && !remoteAddress.isBlank()) {
            sb.append(" (").append(remoteAddress).append(')');
        }
        return sb.toString();
    }
}
