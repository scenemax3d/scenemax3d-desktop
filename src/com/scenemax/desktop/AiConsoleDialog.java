package com.scenemax.desktop;

import com.scenemax.desktop.ai.SceneMaxToolRegistry;
import com.scenemax.desktop.ai.SceneMaxToolResult;
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
import java.util.ArrayList;
import java.util.List;

public class AiConsoleDialog extends JDialog {

    private final MainApp host;
    private final JTextArea txtTranscript = new JTextArea();
    private final JTextArea txtInput = new JTextArea(4, 60);
    private final JButton btnSend = new JButton("Send");
    private final JButton btnClear = new JButton("Clear");
    private final JLabel lblStatus = new JLabel("Ready");
    private final List<LocalGemmaMessage> history = new ArrayList<>();

    public AiConsoleDialog(MainApp host) {
        super(host, "AI Console", false);
        this.host = host;
        buildUi();
    }

    private void buildUi() {
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setSize(760, 620);
        setLocationByPlatform(true);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        setContentPane(root);

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
        controls.add(buttons, BorderLayout.EAST);
        controls.add(lblStatus, BorderLayout.WEST);
        bottom.add(controls, BorderLayout.SOUTH);

        root.add(bottom, BorderLayout.SOUTH);

        appendTranscript("SceneMax AI console ready.\n");
        appendTranscript("Use Ctrl+Enter to send. Keep this window open while you work in the IDE.\n\n");
    }

    private void sendPrompt() {
        String userText = txtInput.getText().trim();
        if (userText.isEmpty()) {
            return;
        }

        LocalGemmaBridge bridge = host.getLocalGemmaBridge();
        if (bridge == null || host.getLocalGemmaBridgeStatus() == null || !host.getLocalGemmaBridgeStatus().isReachable()) {
            JOptionPane.showMessageDialog(this,
                    "Local Gemma is not ready yet.\n\nDownload/start Gemma from Settings > Local Gemma first.",
                    "AI Console",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        txtInput.setText("");
        appendTranscript("You\n" + userText + "\n\n");
        history.add(new LocalGemmaMessage("user", userText));
        setBusy(true, "Thinking...");

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                runConversationLoop(this::publish);
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String chunk : chunks) {
                    appendTranscript(chunk);
                }
            }

            @Override
            protected void done() {
                setBusy(false, "Ready");
                try {
                    get();
                } catch (Exception ex) {
                    appendTranscript("System\n" + ex.getMessage() + "\n\n");
                }
            }
        };
        worker.execute();
    }

    private void runConversationLoop(java.util.function.Consumer<String> sink) throws Exception {
        for (int step = 0; step < 6; step++) {
            LocalGemmaBridgeResponse response = host.getLocalGemmaBridge().generate(new LocalGemmaBridgeRequest(
                    buildSystemPrompt(host.getAutomationToolRegistry()),
                    history,
                    new JSONArray(),
                    0.2
            ));

            ParsedConsoleResponse parsed = parseConsoleResponse(response.getText());
            if (!parsed.assistantText.isBlank()) {
                sink.accept("Assistant\n" + parsed.assistantText + "\n\n");
            }

            if (parsed.toolCalls.isEmpty()) {
                history.add(new LocalGemmaMessage("assistant", parsed.assistantText.isBlank() ? response.getText() : parsed.assistantText));
                return;
            }

            if (!parsed.assistantText.isBlank()) {
                history.add(new LocalGemmaMessage("assistant", parsed.assistantText));
            }

            for (JSONObject toolCall : parsed.toolCalls) {
                String name = toolCall.optString("name", "");
                JSONObject arguments = toolCall.optJSONObject("arguments");
                if (arguments == null) {
                    arguments = new JSONObject();
                }

                sink.accept("Tool\n" + name + " " + arguments + "\n");
                SceneMaxToolResult result = host.getAutomationToolRegistry().call(name, host.getAutomationToolContext(), arguments);
                JSONObject payload = result.getData();
                String toolSummary = result.isError()
                        ? "ERROR: " + payload.optString("message", "Tool failed")
                        : payload.toString(2);
                sink.accept(toolSummary + "\n\n");
                history.add(new LocalGemmaMessage("tool", "Tool " + name + " result:\n" + toolSummary));
            }
        }

        history.add(new LocalGemmaMessage("assistant", "I stopped after several tool steps to avoid getting stuck."));
        throw new IllegalStateException("The AI console stopped after several tool iterations.");
    }

    private String buildSystemPrompt(SceneMaxToolRegistry registry) {
        return "You are the SceneMax IDE assistant. "
                + "You help the user work inside the IDE using the available tools when needed.\n\n"
                + "When you want to use tools, reply with JSON only in this exact shape:\n"
                + "{\"assistant\":\"short reason\",\"tool_calls\":[{\"name\":\"tool.name\",\"arguments\":{}}]}\n"
                + "When no tool is needed, reply with JSON only:\n"
                + "{\"assistant\":\"your answer\",\"tool_calls\":[]}\n\n"
                + "Available tools:\n"
                + registry.describeTools().toString(2);
    }

    private ParsedConsoleResponse parseConsoleResponse(String text) {
        if (text == null) {
            return new ParsedConsoleResponse("", new ArrayList<>());
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
            return new ParsedConsoleResponse(trimmed, new ArrayList<>());
        }

        List<JSONObject> toolCalls = new ArrayList<>();
        JSONArray calls = json.optJSONArray("tool_calls");
        if (calls != null) {
            for (int i = 0; i < calls.length(); i++) {
                if (calls.optJSONObject(i) != null) {
                    toolCalls.add(calls.getJSONObject(i));
                }
            }
        }
        return new ParsedConsoleResponse(json.optString("assistant", ""), toolCalls);
    }

    private JSONObject tryParseJson(String text) {
        try {
            return new JSONObject(text);
        } catch (Exception ignored) {
            return null;
        }
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
        lblStatus.setText(status);
    }

    private static class ParsedConsoleResponse {
        final String assistantText;
        final List<JSONObject> toolCalls;

        ParsedConsoleResponse(String assistantText, List<JSONObject> toolCalls) {
            this.assistantText = assistantText == null ? "" : assistantText;
            this.toolCalls = toolCalls;
        }
    }
}
