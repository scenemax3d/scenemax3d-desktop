package com.scenemax.desktop;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.util.List;

public class McpLogDialog extends JDialog {

    private final MainApp host;
    private final JTextField txtEndpoint;
    private final JTextArea txtLog;

    public McpLogDialog(MainApp host) {
        super(host, "MCP Monitor", false);
        this.host = host;

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel top = new JPanel(new BorderLayout(6, 6));
        top.add(new JLabel("Endpoint:"), BorderLayout.WEST);
        txtEndpoint = new JTextField();
        txtEndpoint.setEditable(false);
        top.add(txtEndpoint, BorderLayout.CENTER);
        root.add(top, BorderLayout.NORTH);

        txtLog = new JTextArea();
        txtLog.setEditable(false);
        txtLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        root.add(new JScrollPane(txtLog), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnRefresh = new JButton("Refresh");
        btnRefresh.addActionListener(this::refreshAction);
        JButton btnClear = new JButton("Clear Log");
        btnClear.addActionListener(e -> {
            host.clearMcpLogEntries();
            refreshFromHost();
        });
        JButton btnCopy = new JButton("Copy URL");
        btnCopy.addActionListener(e -> {
            String url = txtEndpoint.getText();
            if (!url.isBlank()) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(url), null);
            }
        });
        JButton btnClose = new JButton("Close");
        btnClose.addActionListener(e -> setVisible(false));
        buttons.add(btnRefresh);
        buttons.add(btnClear);
        buttons.add(btnCopy);
        buttons.add(btnClose);
        root.add(buttons, BorderLayout.SOUTH);

        setContentPane(root);
        setSize(900, 420);
        setLocationRelativeTo(host);
    }

    private void refreshAction(ActionEvent e) {
        refreshFromHost();
    }

    public void refreshFromHost() {
        String endpoint = host.getAutomationHttpServer() != null ? host.getAutomationHttpServer().getEndpointUrl() : "";
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "Unavailable";
        }
        txtEndpoint.setText(endpoint);
        List<String> lines = host.getMcpLogLinesSnapshot();
        txtLog.setText(String.join(System.lineSeparator(), lines));
        txtLog.setCaretPosition(txtLog.getDocument().getLength());
    }

    public void appendLogLine(String line) {
        if (txtLog.getDocument().getLength() > 0) {
            txtLog.append(System.lineSeparator());
        }
        txtLog.append(line);
        txtLog.setCaretPosition(txtLog.getDocument().getLength());
    }
}
