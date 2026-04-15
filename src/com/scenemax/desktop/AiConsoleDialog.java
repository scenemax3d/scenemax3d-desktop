package com.scenemax.desktop;

import com.scenemax.desktop.ai.SceneMaxToolResult;
import com.scenemax.desktop.ai.gemma.GemmaConsoleProtocol;
import com.scenemax.desktop.ai.gemma.LocalGemmaBridge;
import com.scenemax.desktop.ai.gemma.LocalGemmaBridgeRequest;
import com.scenemax.desktop.ai.gemma.LocalGemmaBridgeResponse;
import com.scenemax.desktop.ai.gemma.LocalGemmaMessage;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AiConsoleDialog extends JDialog {

    private static final int MAX_HISTORY_MESSAGES = 12;
    private static final String STATUS_EVENT_PREFIX = "__STATUS__:";

    private final MainApp host;
    private final JTextArea txtTranscript = new JTextArea();
    private final JTextArea txtInput = new JTextArea(4, 60);
    private final JButton btnSend = new JButton("Send");
    private final JButton btnClear = new JButton("Clear");
    private final JLabel lblStatus = new JLabel("Ready");
    private final JProgressBar progressBar = new JProgressBar();
    private final JComboBox<ConsoleProvider> cboProvider = new JComboBox<>(ConsoleProvider.values());
    private final List<ConsoleMessage> history = new ArrayList<>();

    public AiConsoleDialog(MainApp host) {
        super(host, "AI Console", false);
        this.host = host;
        buildUi();
        refreshProviderState();
    }

    public void refreshProviderState() {
        ConsoleProvider provider = getSelectedProvider();
        lblStatus.setText(describeProviderState(provider));
    }

    private void buildUi() {
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setSize(840, 680);
        setLocationByPlatform(true);

        String savedProvider = AppDB.getInstance().getParam("ai_console_provider");
        if (savedProvider != null) {
            for (ConsoleProvider provider : ConsoleProvider.values()) {
                if (provider.name().equalsIgnoreCase(savedProvider.trim())) {
                    cboProvider.setSelectedItem(provider);
                    break;
                }
            }
        }
        cboProvider.addActionListener(e -> {
            AppDB.getInstance().setParam("ai_console_provider", getSelectedProvider().name());
            refreshProviderState();
        });

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        setContentPane(root);

        JPanel top = new JPanel(new BorderLayout(8, 8));
        JPanel providerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        providerPanel.add(new JLabel("Provider:"));
        providerPanel.add(cboProvider);
        top.add(providerPanel, BorderLayout.WEST);
        top.add(new JLabel("Use Ctrl+Enter to send. Keep this window open while you work in the IDE."), BorderLayout.CENTER);
        root.add(top, BorderLayout.NORTH);

        txtTranscript.setEditable(false);
        txtTranscript.setLineWrap(true);
        txtTranscript.setWrapStyleWord(true);
        txtTranscript.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JScrollPane transcriptScroll = new JScrollPane(txtTranscript);
        root.add(transcriptScroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        txtInput.setLineWrap(true);
        txtInput.setWrapStyleWord(true);
        txtInput.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && e.isControlDown()) {
                    e.consume();
                    sendPrompt();
                }
            }
        });
        bottom.add(new JScrollPane(txtInput), BorderLayout.CENTER);

        JPanel controls = new JPanel(new BorderLayout(8, 8));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnSend.addActionListener(e -> sendPrompt());
        btnClear.addActionListener(e -> clearConversation());
        buttons.add(btnClear);
        buttons.add(btnSend);

        progressBar.setIndeterminate(false);
        progressBar.setVisible(false);
        progressBar.setStringPainted(true);
        progressBar.setString("");

        JPanel activityPanel = new JPanel(new BorderLayout(6, 6));
        activityPanel.add(lblStatus, BorderLayout.NORTH);
        activityPanel.add(progressBar, BorderLayout.CENTER);

        controls.add(buttons, BorderLayout.EAST);
        controls.add(activityPanel, BorderLayout.CENTER);
        bottom.add(controls, BorderLayout.SOUTH);

        root.add(bottom, BorderLayout.SOUTH);

        appendTranscript("SceneMax AI console ready.\n");
        appendTranscript("Choose Local Gemma, Claude Code, or Codex from the provider list.\n\n");
    }

    private void sendPrompt() {
        String userText = txtInput.getText().trim();
        if (userText.isEmpty()) {
            return;
        }

        ConsoleProvider provider = getSelectedProvider();
        try {
            ensureProviderReady(provider);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    ex.getMessage(),
                    "AI Console",
                    JOptionPane.ERROR_MESSAGE);
            refreshProviderState();
            return;
        }

        txtInput.setText("");
        appendTranscript("You\n" + userText + "\n\n");
        history.add(new ConsoleMessage("user", userText));
        setBusy(true, "Thinking with " + provider + "...");

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                if (provider == ConsoleProvider.LOCAL_GEMMA) {
                    publish(statusEvent("Preparing Local Gemma request..."));
                    runGemmaConversationLoop(this::publish, status -> publish(statusEvent(status)));
                } else {
                    publish(statusEvent("Preparing " + provider + " request..."));
                    String answer = runExternalProvider(provider, userText, status -> publish(statusEvent(status)));
                    publish(provider + "\n" + answer + "\n\n");
                    history.add(new ConsoleMessage("assistant", answer));
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String chunk : chunks) {
                    if (chunk.startsWith(STATUS_EVENT_PREFIX)) {
                        updateProgressStatus(chunk.substring(STATUS_EVENT_PREFIX.length()));
                    } else {
                        appendTranscript(chunk);
                    }
                }
            }

            @Override
            protected void done() {
                setBusy(false, describeProviderState(provider));
                try {
                    get();
                } catch (Exception ex) {
                    appendTranscript("System\n" + ex.getMessage() + "\n\n");
                }
            }
        };
        worker.execute();
    }

    private void ensureProviderReady(ConsoleProvider provider) throws Exception {
        if (provider == ConsoleProvider.LOCAL_GEMMA) {
            LocalGemmaBridge bridge = host.getLocalGemmaBridge();
            if (bridge == null || host.getLocalGemmaBridgeStatus() == null || !host.getLocalGemmaBridgeStatus().isReachable()) {
                throw new IOException("Local Gemma is not ready yet.\n\nDownload/start Gemma from Settings > Local Gemma first.");
            }
            return;
        }

        if (provider == ConsoleProvider.CLAUDE_CODE) {
            AiCliSupport.resolveClaudeExecutable();
            AiCliSupport.ensureClaudeAuthenticated(resolveConsoleWorkingDirectory());
        } else {
            AiCliSupport.resolveCodexExecutable();
        }

        if (host != null) {
            host.restartAutomationServerFromSettings();
        }
    }

    private void runGemmaConversationLoop(java.util.function.Consumer<String> sink,
                                          java.util.function.Consumer<String> statusSink) throws Exception {
        for (int step = 0; step < 6; step++) {
            statusSink.accept("Sending request to Local Gemma...");
            JSONArray gemmaTools = host.getAutomationToolRegistry().describeTools();
            LocalGemmaBridgeResponse response = host.getLocalGemmaBridge().generate(new LocalGemmaBridgeRequest(
                    GemmaConsoleProtocol.buildSystemPrompt(gemmaTools, buildGemmaIdeContext()),
                    toGemmaHistory(),
                    gemmaTools,
                    0.2
            ));

            statusSink.accept("Receiving response from Local Gemma...");
            GemmaConsoleProtocol.ParsedResponse parsed = GemmaConsoleProtocol.parseResponse(response);
            if (!parsed.getAssistantText().isBlank()) {
                sink.accept("Local Gemma\n" + parsed.getAssistantText() + "\n\n");
            }

            if (parsed.getToolCalls().isEmpty()) {
                statusSink.accept("Updating conversation...");
                history.add(new ConsoleMessage("assistant",
                        parsed.getAssistantText().isBlank() ? response.getText() : parsed.getAssistantText()));
                return;
            }

            if (!parsed.getAssistantText().isBlank()) {
                statusSink.accept("Updating conversation...");
                history.add(new ConsoleMessage("assistant", parsed.getAssistantText()));
            }

            for (JSONObject toolCall : parsed.getToolCalls()) {
                String name = toolCall.optString("name", "");
                JSONObject arguments = toolCall.optJSONObject("arguments");
                if (arguments == null) {
                    arguments = new JSONObject();
                }

                sink.accept("Tool\n" + name + " " + arguments + "\n");
                statusSink.accept("Running tool: " + name + "...");
                SceneMaxToolResult result = host.getAutomationToolRegistry().call(name, host.getAutomationToolContext(), arguments);
                JSONObject payload = result.getData();
                String toolSummary = result.isError()
                        ? "ERROR: " + payload.optString("message", "Tool failed")
                        : payload.toString(2);
                sink.accept(toolSummary + "\n\n");
                statusSink.accept("Updating tool results...");
                history.add(new ConsoleMessage("tool", "Tool " + name + " result:\n" + toolSummary));
            }
        }

        history.add(new ConsoleMessage("assistant", "I stopped after several tool steps to avoid getting stuck."));
        throw new IllegalStateException("The AI console stopped after several tool iterations.");
    }

    private List<LocalGemmaMessage> toGemmaHistory() {
        List<LocalGemmaMessage> messages = new ArrayList<>();
        for (ConsoleMessage entry : history) {
            messages.add(new LocalGemmaMessage(entry.role, entry.content));
        }
        return messages;
    }

    private String runExternalProvider(ConsoleProvider provider, String userPrompt,
                                       java.util.function.Consumer<String> statusSink) throws Exception {
        File workingDirectory = resolveConsoleWorkingDirectory();
        if (provider == ConsoleProvider.CLAUDE_CODE) {
            return runClaudeCode(userPrompt, workingDirectory, statusSink);
        }
        return runCodex(buildExternalPrompt(provider, userPrompt), workingDirectory, statusSink);
    }

    private String runClaudeCode(String userPrompt, File workingDirectory,
                                 java.util.function.Consumer<String> statusSink) throws Exception {
        String executable = AiCliSupport.normalizeForInvocation(AiCliSupport.resolveClaudeExecutable(), "claude");
        String endpoint = resolveLiveMcpEndpoint();
        statusSink.accept("Creating Claude MCP config...");
        File mcpConfig = createClaudeMcpConfigFile(endpoint);
        statusSink.accept("Preparing Claude system prompt...");
        File systemPromptFile = createClaudeSystemPromptFile(buildExternalSystemPrompt(ConsoleProvider.CLAUDE_CODE, userPrompt));
        try {
            List<String> args = new ArrayList<>();
            args.add("-p");
            args.add(compactPrompt(userPrompt));
            args.add("--output-format");
            args.add("json");
            args.add("--append-system-prompt-file");
            args.add(systemPromptFile.getAbsolutePath());
            args.add("--allowedTools");
            args.add("mcp__scenemax__*");
            args.add("--mcp-config");
            args.add(mcpConfig.getAbsolutePath());

            ProcessResult result = runExternalCommand(executable, args, workingDirectory, statusSink, "Claude Code");
            if (result.exitCode != 0) {
                throw new IOException(buildProcessFailure("Claude Code", result));
            }

            statusSink.accept("Parsing Claude response...");
            String parsed = extractClaudeResult(result.stdout);
            if (!parsed.isBlank()) {
                return parsed;
            }
            if (!result.stdout.isBlank()) {
                return result.stdout.trim();
            }
            throw new IOException("Claude Code returned no output.");
        } finally {
            Files.deleteIfExists(mcpConfig.toPath());
            Files.deleteIfExists(systemPromptFile.toPath());
        }
    }

    private String runCodex(String prompt, File workingDirectory,
                            java.util.function.Consumer<String> statusSink) throws Exception {
        String executable = AiCliSupport.normalizeForInvocation(AiCliSupport.resolveCodexExecutable(), "codex");
        List<String> args = new ArrayList<>();
        args.add("exec");
        args.add("--skip-git-repo-check");
        args.add("--json");
        args.add(prompt);

        ProcessResult result = runExternalCommand(executable, args, workingDirectory, statusSink, "Codex");
        if (result.exitCode != 0) {
            throw new IOException(buildProcessFailure("Codex", result));
        }

        statusSink.accept("Parsing Codex response...");
        String parsed = extractCodexResult(result.stdout);
        if (!parsed.isBlank()) {
            return parsed;
        }
        if (!result.stdout.isBlank()) {
            return result.stdout.trim();
        }
        throw new IOException("Codex returned no output.");
    }

    private ProcessResult runExternalCommand(String executable, List<String> args, File workingDirectory,
                                             java.util.function.Consumer<String> statusSink,
                                             String providerName) throws Exception {
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
        statusSink.accept("Sending request to " + providerName + "...");
        Process process = pb.start();
        statusSink.accept("Waiting for " + providerName + " response...");

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        Thread stdoutThread = readStreamAsync(process.getInputStream(), stdout);
        Thread stderrThread = readStreamAsync(process.getErrorStream(), stderr);
        int exitCode = process.waitFor();
        statusSink.accept("Receiving " + providerName + " output...");
        stdoutThread.join();
        stderrThread.join();
        return new ProcessResult(exitCode, stdout.toString().trim(), stderr.toString().trim(), String.join(" ", command));
    }

    private Thread readStreamAsync(java.io.InputStream input, StringBuilder target) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    target.append(line).append(System.lineSeparator());
                }
            } catch (IOException ignored) {
            }
        }, "ai-console-stream");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private String extractClaudeResult(String stdout) {
        if (stdout == null || stdout.isBlank()) {
            return "";
        }
        try {
            JSONObject json = new JSONObject(stdout.trim());
            return json.optString("result", "").trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String extractCodexResult(String stdout) {
        if (stdout == null || stdout.isBlank()) {
            return "";
        }

        List<String> assistantMessages = new ArrayList<>();
        String[] lines = stdout.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || !trimmed.startsWith("{")) {
                continue;
            }
            try {
                JSONObject json = new JSONObject(trimmed);
                collectAssistantText(json, assistantMessages);
            } catch (Exception ignored) {
            }
        }

        if (!assistantMessages.isEmpty()) {
            return assistantMessages.get(assistantMessages.size() - 1).trim();
        }
        return "";
    }

    private void collectAssistantText(JSONObject json, List<String> assistantMessages) {
        if (json == null) {
            return;
        }

        String type = json.optString("type", "");
        if (isAssistantEvent(type)) {
            String text = extractTextPayload(json);
            if (!text.isBlank()) {
                assistantMessages.add(text);
            }
        }

        JSONObject item = json.optJSONObject("item");
        if (item != null) {
            String itemType = item.optString("type", item.optString("item_type", ""));
            if (isAssistantEvent(itemType)) {
                String text = extractTextPayload(item);
                if (!text.isBlank()) {
                    assistantMessages.add(text);
                }
            }
        }

        JSONObject message = json.optJSONObject("message");
        if (message != null) {
            String text = extractTextPayload(message);
            if (!text.isBlank()) {
                assistantMessages.add(text);
            }
        }
    }

    private boolean isAssistantEvent(String type) {
        return "agent_message".equalsIgnoreCase(type)
                || "assistant_message".equalsIgnoreCase(type)
                || "message".equalsIgnoreCase(type);
    }

    private String extractTextPayload(JSONObject object) {
        if (object == null) {
            return "";
        }
        if (object.has("text")) {
            return object.optString("text", "");
        }
        JSONArray content = object.optJSONArray("content");
        if (content != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < content.length(); i++) {
                Object entry = content.get(i);
                if (entry instanceof JSONObject) {
                    JSONObject contentObj = (JSONObject) entry;
                    if (contentObj.has("text")) {
                        if (sb.length() > 0) {
                            sb.append('\n');
                        }
                        sb.append(contentObj.optString("text", ""));
                    } else if (contentObj.has("content")) {
                        String nested = extractTextPayload(contentObj);
                        if (!nested.isBlank()) {
                            if (sb.length() > 0) {
                                sb.append('\n');
                            }
                            sb.append(nested);
                        }
                    }
                }
            }
            return sb.toString().trim();
        }
        JSONObject message = object.optJSONObject("message");
        if (message != null) {
            return extractTextPayload(message);
        }
        return "";
    }

    private String buildProcessFailure(String providerName, ProcessResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(providerName).append(" command failed.");
        sb.append("\n\nCommand:\n").append(result.commandLine);
        if (!result.stderr.isBlank()) {
            sb.append("\n\nError output:\n").append(result.stderr);
        }
        if (!result.stdout.isBlank()) {
            sb.append("\n\nOutput:\n").append(result.stdout);
        }
        return sb.toString();
    }

    private File createClaudeMcpConfigFile(String endpoint) throws IOException {
        JSONObject server = new JSONObject()
                .put("type", "http")
                .put("url", endpoint);
        JSONObject config = new JSONObject()
                .put("mcpServers", new JSONObject().put("scenemax", server));
        File file = File.createTempFile("scenemax-claude-mcp-", ".json");
        Files.writeString(file.toPath(), config.toString(2), StandardCharsets.UTF_8);
        return file;
    }

    private String resolveLiveMcpEndpoint() throws IOException {
        if (host != null && host.getAutomationHttpServer() != null && host.getAutomationHttpServer().isRunning()) {
            String endpoint = host.getAutomationHttpServer().getEndpointUrl();
            if (endpoint != null && !endpoint.isBlank()) {
                return endpoint;
            }
        }

        String port = AppDB.getInstance().getParam("mcp_server_port");
        if (port != null && !port.trim().isEmpty()) {
            return "http://127.0.0.1:" + port.trim() + "/mcp";
        }
        return "http://127.0.0.1:8765/mcp";
    }

    private File resolveConsoleWorkingDirectory() {
        SceneMaxProject project = Util.getActiveProject();
        if (project != null && project.path != null && !project.path.isBlank()) {
            File dir = new File(project.path);
            if (dir.exists()) {
                return dir;
            }
        }
        return new File(Util.getWorkingDir());
    }

    private String buildExternalPrompt(ConsoleProvider provider, String userPrompt) {
        String systemPrompt = buildExternalSystemPrompt(provider, userPrompt);
        return systemPrompt + "\n\nLatest user request:\n" + userPrompt;
    }

    private String buildExternalSystemPrompt(ConsoleProvider provider, String latestUserPrompt) {
        JSONObject activeDocument = host.getAutomationActiveDocumentSnapshot();
        SceneMaxProject project = Util.getActiveProject();

        StringBuilder sb = new StringBuilder();
        sb.append("You are the SceneMax IDE assistant embedded inside the IDE.\n");
        sb.append("The user is chatting with you from inside SceneMax.\n");
        sb.append("Be concise, practical, and prefer the SceneMax MCP tools for IDE-specific actions when they are available.\n");
        if (provider == ConsoleProvider.CLAUDE_CODE) {
            sb.append("The SceneMax MCP server is attached for this turn.\n");
        } else {
            sb.append("If the 'scenemax' MCP server is configured in Codex, use it for IDE actions. Otherwise answer using the provided IDE context and say briefly if MCP tools are unavailable.\n");
        }
        sb.append("Avoid asking the user to leave the IDE unless that is truly necessary.\n\n");

        if (project != null) {
            sb.append("Active project:\n");
            sb.append("- Name: ").append(project.name).append("\n");
            sb.append("- Path: ").append(project.path).append("\n");
            sb.append("- Scripts: ").append(project.getScriptsPath()).append("\n\n");
        } else {
            sb.append("Active project: none selected.\n\n");
        }

        sb.append("Active document snapshot:\n");
        sb.append(activeDocument.toString(2)).append("\n\n");

        sb.append("Recent conversation:\n");
        int start = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
        for (int i = start; i < history.size(); i++) {
            ConsoleMessage message = history.get(i);
            sb.append(message.role.toUpperCase(Locale.ROOT)).append(":\n");
            sb.append(message.content).append("\n\n");
        }

        sb.append("Latest user request:\n");
        sb.append(latestUserPrompt).append("\n\n");
        sb.append("Respond to the latest user request now.");
        return sb.toString();
    }

    private File createClaudeSystemPromptFile(String systemPrompt) throws IOException {
        File file = File.createTempFile("scenemax-claude-system-", ".txt");
        Files.writeString(file.toPath(), systemPrompt, StandardCharsets.UTF_8);
        return file;
    }

    private String compactPrompt(String userPrompt) {
        String[] lines = userPrompt == null ? new String[0] : userPrompt.trim().split("\\R+");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(trimmed);
        }
        return sb.length() == 0 ? "Help with the SceneMax IDE task." : sb.toString();
    }

    private String buildGemmaIdeContext() {
        JSONObject activeDocument = host.getAutomationActiveDocumentSnapshot();
        SceneMaxProject project = Util.getActiveProject();
        StringBuilder sb = new StringBuilder();
        if (project != null) {
            sb.append("Active project:\n");
            sb.append("- Name: ").append(project.name).append("\n");
            sb.append("- Path: ").append(project.path).append("\n");
            sb.append("- Scripts: ").append(project.getScriptsPath()).append("\n\n");
        } else {
            sb.append("Active project: none selected.\n\n");
        }

        sb.append("Active document snapshot:\n");
        sb.append(activeDocument.toString(2));
        return sb.toString();
    }

    private void clearConversation() {
        history.clear();
        txtTranscript.setText("");
        appendTranscript("SceneMax AI console cleared.\n\n");
    }

    private void appendTranscript(String text) {
        txtTranscript.append(text);
        txtTranscript.setCaretPosition(txtTranscript.getDocument().getLength());
    }

    private void setBusy(boolean busy, String status) {
        btnSend.setEnabled(!busy);
        btnClear.setEnabled(!busy);
        cboProvider.setEnabled(!busy);
        lblStatus.setText(status);
        progressBar.setVisible(busy);
        progressBar.setIndeterminate(busy);
        progressBar.setString(busy ? status : "");
    }

    private String statusEvent(String text) {
        return STATUS_EVENT_PREFIX + text;
    }

    private void updateProgressStatus(String status) {
        lblStatus.setText(status);
        progressBar.setString(status);
    }

    private ConsoleProvider getSelectedProvider() {
        ConsoleProvider provider = (ConsoleProvider) cboProvider.getSelectedItem();
        return provider == null ? ConsoleProvider.LOCAL_GEMMA : provider;
    }

    private String describeProviderState(ConsoleProvider provider) {
        try {
            if (provider == ConsoleProvider.LOCAL_GEMMA) {
                LocalGemmaBridge bridge = host.getLocalGemmaBridge();
                if (bridge == null || host.getLocalGemmaBridgeStatus() == null) {
                    return "Local Gemma: checking";
                }
                if (!host.getLocalGemmaBridgeStatus().isReachable()) {
                    return "Local Gemma: unavailable";
                }
                return "Local Gemma: ready";
            }

            if (provider == ConsoleProvider.CLAUDE_CODE) {
                AiCliSupport.resolveClaudeExecutable();
                return "Claude Code: ready";
            }

            AiCliSupport.resolveCodexExecutable();
            return "Codex: ready";
        } catch (Exception ex) {
            return provider + ": setup needed";
        }
    }

    private enum ConsoleProvider {
        LOCAL_GEMMA("Local Gemma"),
        CLAUDE_CODE("Claude Code"),
        CODEX("Codex");

        private final String label;

        ConsoleProvider(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static class ConsoleMessage {
        final String role;
        final String content;

        ConsoleMessage(String role, String content) {
            this.role = role;
            this.content = content == null ? "" : content;
        }
    }

    private static class ProcessResult {
        final int exitCode;
        final String stdout;
        final String stderr;
        final String commandLine;

        ProcessResult(int exitCode, String stdout, String stderr, String commandLine) {
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
            this.commandLine = commandLine == null ? "" : commandLine;
        }
    }
}
