package com.scenemaxeng.common.types;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RuntimeIssueLog {

    private static final Object LOCK = new Object();
    private static final String RELATIVE_LOG_PATH = ".scenemax/runtime-issues.jsonl";
    private static final Pattern LINE_NUMBER_PATTERN = Pattern.compile("(?i)\\bline\\s*[:]?\\s*(\\d+)\\b");

    private RuntimeIssueLog() {
    }

    public static File resolveLogFile(File projectRoot) {
        if (projectRoot != null) {
            return new File(projectRoot, RELATIVE_LOG_PATH);
        }
        return new File(new File(System.getProperty("java.io.tmpdir"), "scenemax"), "runtime-issues.jsonl");
    }

    public static void clear(File projectRoot) {
        File logFile = resolveLogFile(projectRoot);
        synchronized (LOCK) {
            try {
                Path path = logFile.toPath();
                Path parent = path.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.write(path, new byte[0], StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException ignored) {
            }
        }
    }

    public static void appendIssue(File projectRoot,
                                   String issueType,
                                   String phase,
                                   String source,
                                   String message,
                                   String scriptPath) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        JSONObject entry = new JSONObject();
        entry.put("timestamp", Instant.now().toString());
        entry.put("issueType", normalizeIssueType(issueType));
        entry.put("phase", phase == null ? "" : phase.trim());
        entry.put("source", source == null ? "" : source.trim());
        entry.put("message", message.trim());
        entry.put("scriptPath", scriptPath == null || scriptPath.isBlank() ? JSONObject.NULL : scriptPath);

        int lineNumber = extractLineNumber(message);
        if (lineNumber > 0) {
            entry.put("lineNumber", lineNumber);
        }

        appendEntry(resolveLogFile(projectRoot), entry);
    }

    public static void appendIssues(File projectRoot,
                                    String issueType,
                                    String phase,
                                    String source,
                                    List<String> messages,
                                    String scriptPath) {
        if (messages == null) {
            return;
        }
        for (String message : messages) {
            appendIssue(projectRoot, issueType, phase, source, message, scriptPath);
        }
    }

    public static JSONObject readIssues(File projectRoot, int limit, String issueTypeFilter) {
        File logFile = resolveLogFile(projectRoot);
        JSONObject result = new JSONObject();
        result.put("logPath", logFile.getAbsolutePath());
        result.put("exists", logFile.isFile());

        List<JSONObject> parsedEntries = readEntries(logFile);
        Map<String, JSONObject> uniqueEntries = new LinkedHashMap<>();
        int syntaxCount = 0;
        int runtimeCount = 0;
        String normalizedFilter = normalizeFilter(issueTypeFilter);

        for (JSONObject entry : parsedEntries) {
            String issueType = entry.optString("issueType", "runtime");
            if ("syntax".equals(issueType)) {
                syntaxCount++;
            } else if ("runtime".equals(issueType)) {
                runtimeCount++;
            }

            if (!"all".equals(normalizedFilter) && !normalizedFilter.equals(issueType)) {
                continue;
            }

            String signature = buildSignature(entry);
            uniqueEntries.put(signature, entry);
        }

        JSONArray issues = new JSONArray();
        List<JSONObject> uniqueList = new ArrayList<>(uniqueEntries.values());
        int start = Math.max(0, uniqueList.size() - Math.max(limit, 1));
        for (int i = uniqueList.size() - 1; i >= start; i--) {
            issues.put(uniqueList.get(i));
        }

        result.put("hasIssues", syntaxCount > 0 || runtimeCount > 0);
        result.put("syntaxIssueCount", syntaxCount);
        result.put("runtimeIssueCount", runtimeCount);
        result.put("uniqueIssueCount", uniqueEntries.size());
        result.put("issues", issues);
        return result;
    }

    private static void appendEntry(File logFile, JSONObject entry) {
        synchronized (LOCK) {
            try {
                Path path = logFile.toPath();
                Path parent = path.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(path,
                        entry.toString() + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            } catch (IOException ignored) {
            }
        }
    }

    private static List<JSONObject> readEntries(File logFile) {
        List<JSONObject> entries = new ArrayList<>();
        synchronized (LOCK) {
            if (!logFile.isFile()) {
                return entries;
            }
            try {
                List<String> lines = Files.readAllLines(logFile.toPath(), StandardCharsets.UTF_8);
                for (String line : lines) {
                    String trimmed = line == null ? "" : line.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }
                    try {
                        entries.add(new JSONObject(trimmed));
                    } catch (Exception ignored) {
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return entries;
    }

    private static String normalizeIssueType(String issueType) {
        if (issueType == null) {
            return "runtime";
        }
        String normalized = issueType.trim().toLowerCase(Locale.ROOT);
        if ("syntax".equals(normalized)) {
            return "syntax";
        }
        return "runtime";
    }

    private static String normalizeFilter(String issueTypeFilter) {
        if (issueTypeFilter == null || issueTypeFilter.isBlank()) {
            return "all";
        }
        String normalized = issueTypeFilter.trim().toLowerCase(Locale.ROOT);
        if ("syntax".equals(normalized) || "runtime".equals(normalized)) {
            return normalized;
        }
        return "all";
    }

    private static int extractLineNumber(String message) {
        if (message == null || message.isBlank()) {
            return -1;
        }
        Matcher matcher = LINE_NUMBER_PATTERN.matcher(message);
        if (!matcher.find()) {
            return -1;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static String buildSignature(JSONObject entry) {
        String issueType = entry.optString("issueType", "");
        String scriptPath = entry.optString("scriptPath", "");
        String message = entry.optString("message", "");
        return issueType + "|" + scriptPath + "|" + message;
    }
}
