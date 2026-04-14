package com.scenemax.desktop;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AiCliSupport {

    public static final String CLAUDE_NPM_PACKAGE = "@anthropic-ai/claude-code";
    public static final String CODEX_NPM_PACKAGE = "@openai/codex";

    private AiCliSupport() {
    }

    public static String getConfiguredClaudePath() {
        return normalizeOptionalPath(AppDB.getInstance().getParam("mcp_claude_cli_path"));
    }

    public static String getConfiguredCodexPath() {
        return normalizeOptionalPath(AppDB.getInstance().getParam("mcp_codex_cli_path"));
    }

    public static String resolveClaudeExecutable() throws IOException {
        String configured = normalizeWindowsExecutable(getConfiguredClaudePath());
        if (!configured.isBlank() && new File(configured).exists()) {
            return configured;
        }

        String discovered = discoverViaWhere("claude");
        if (discovered != null) {
            return discovered;
        }

        String appData = System.getenv("APPDATA");
        String userProfile = System.getenv("USERPROFILE");
        String[] candidates = {
                combine(appData, "npm", "claude.cmd"),
                combine(appData, "npm", "claude.ps1"),
                combine(appData, "npm", "claude.exe"),
                combine(userProfile, "AppData", "Roaming", "npm", "claude.cmd"),
                combine(userProfile, "AppData", "Roaming", "npm", "claude.ps1"),
                combine(userProfile, "AppData", "Roaming", "npm", "claude.exe"),
                combine(userProfile, ".local", "bin", "claude"),
                combine(userProfile, ".bun", "bin", "claude.exe")
        };
        for (String candidate : candidates) {
            if (candidate != null && new File(candidate).exists()) {
                return candidate;
            }
        }

        throw new IOException("Claude Code CLI was not found automatically. Install it or set the CLI path in Settings > MCP.");
    }

    public static String resolveCodexExecutable() throws IOException {
        String configured = normalizeWindowsExecutable(getConfiguredCodexPath());
        if (!configured.isBlank() && new File(configured).exists()) {
            return configured;
        }

        String discovered = discoverViaWhere("codex");
        if (discovered != null) {
            return discovered;
        }

        String programFiles = System.getenv("ProgramFiles");
        if (programFiles != null) {
            File windowsApps = new File(programFiles, "WindowsApps");
            File[] matches = windowsApps.listFiles((dir, name) -> name.startsWith("OpenAI.Codex_"));
            if (matches != null) {
                for (File match : matches) {
                    File candidate = new File(match, "app\\resources\\codex.exe");
                    if (candidate.exists()) {
                        return candidate.getAbsolutePath();
                    }
                }
            }
        }

        throw new IOException("Codex CLI was not found automatically. Install it or set the CLI path in Settings > MCP.");
    }

    public static String resolveNpmExecutable() throws IOException {
        String discovered = discoverViaWhere("npm");
        if (discovered != null) {
            return discovered;
        }

        String programFiles = System.getenv("ProgramFiles");
        String[] candidates = {
                combine(programFiles, "nodejs", "npm.cmd"),
                combine(programFiles, "nodejs", "npm.exe"),
                combine(programFiles, "nodejs", "npm")
        };
        for (String candidate : candidates) {
            if (candidate != null && new File(candidate).exists()) {
                return candidate;
            }
        }

        throw new IOException("npm was not found automatically. Install Node.js first or install the CLI manually.");
    }

    public static void ensureClaudeAuthenticated(File workingDirectory) throws IOException {
        String executable = normalizeForInvocation(resolveClaudeExecutable(), "claude");
        ProcessResult result = runCommand(executable, List.of("auth", "status"), workingDirectory);
        if (result.exitCode == 0) {
            return;
        }

        String details = !result.stderr.isBlank() ? result.stderr : result.stdout;
        StringBuilder message = new StringBuilder();
        message.append("Claude Code CLI is installed but not logged in.\n\n");
        message.append("Open Settings > MCP and click the Claude Code Login button,\n");
        message.append("or run this once in a terminal:\n");
        message.append("claude auth login\n\n");
        message.append("Then return to the AI Console and try again.");
        if (!details.isBlank()) {
            message.append("\n\nCLI output:\n").append(details.trim());
        }
        throw new IOException(message.toString());
    }

    public static String discoverViaWhere(String executable) {
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "where", executable);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) {
                        lines.add(line.trim());
                    }
                }
            }
            int exitCode = process.waitFor();
            if (exitCode == 0 && !lines.isEmpty()) {
                String preferred = preferWindowsLauncher(lines);
                if (preferred != null) {
                    return preferred;
                }
                return lines.get(0);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static String normalizeForInvocation(String executable, String alias) {
        String raw = normalizeWindowsExecutable(normalizeOptionalPath(executable));
        if (raw.isBlank()) {
            return alias;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        if (!raw.contains("\\") && !raw.contains("/")) {
            return raw;
        }
        if (lower.contains("\\windowsapps\\")) {
            return alias;
        }
        return raw;
    }

    public static String normalizeOptionalPath(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static String preferWindowsLauncher(List<String> candidates) {
        String[] preferredExtensions = {".cmd", ".bat", ".exe", ".ps1", ".com", ""};
        for (String extension : preferredExtensions) {
            for (String candidate : candidates) {
                String normalized = normalizeWindowsExecutable(candidate);
                if (!normalized.isBlank() && new File(normalized).exists()
                        && normalized.toLowerCase(Locale.ROOT).endsWith(extension)) {
                    return normalized;
                }
            }
        }
        return null;
    }

    private static String normalizeWindowsExecutable(String raw) {
        String candidate = normalizeOptionalPath(raw);
        if (candidate.isBlank()) {
            return candidate;
        }

        File file = new File(candidate);
        if (file.exists() && file.isFile()) {
            String lower = candidate.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".cmd") || lower.endsWith(".bat") || lower.endsWith(".exe")
                    || lower.endsWith(".ps1") || lower.endsWith(".com")) {
                return file.getAbsolutePath();
            }

            String[] launcherExtensions = {".cmd", ".bat", ".exe", ".ps1", ".com"};
            for (String extension : launcherExtensions) {
                File sibling = new File(candidate + extension);
                if (sibling.exists() && sibling.isFile()) {
                    return sibling.getAbsolutePath();
                }
            }
            return file.getAbsolutePath();
        }

        if (file.getParentFile() != null) {
            String[] launcherExtensions = {".cmd", ".bat", ".exe", ".ps1", ".com"};
            for (String extension : launcherExtensions) {
                File sibling = new File(candidate + extension);
                if (sibling.exists() && sibling.isFile()) {
                    return sibling.getAbsolutePath();
                }
            }
        }

        return candidate;
    }

    private static ProcessResult runCommand(String executable, List<String> args, File workingDirectory) throws IOException {
        List<String> command = new ArrayList<>();
        boolean useCmd = !executable.contains("\\") && !executable.contains("/")
                || executable.toLowerCase(Locale.ROOT).endsWith(".cmd");
        if (useCmd) {
            command.add("cmd.exe");
            command.add("/c");
        }
        command.add(executable);
        command.addAll(args);

        ProcessBuilder pb = new ProcessBuilder(command);
        if (workingDirectory != null && workingDirectory.exists()) {
            pb.directory(workingDirectory);
        }
        pb.redirectErrorStream(false);
        try {
            Process process = pb.start();
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            readStream(process.getInputStream(), stdout);
            readStream(process.getErrorStream(), stderr);
            int exitCode = process.waitFor();
            return new ProcessResult(exitCode, stdout.toString().trim(), stderr.toString().trim());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("CLI command was interrupted.", ex);
        }
    }

    private static void readStream(java.io.InputStream input, StringBuilder target) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                target.append(line).append(System.lineSeparator());
            }
        }
    }

    private static String combine(String root, String... parts) {
        if (root == null || root.isBlank()) {
            return null;
        }
        File file = new File(root);
        for (String part : parts) {
            file = new File(file, part);
        }
        return file.getAbsolutePath();
    }

    private static class ProcessResult {
        final int exitCode;
        final String stdout;
        final String stderr;

        ProcessResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }
    }
}
