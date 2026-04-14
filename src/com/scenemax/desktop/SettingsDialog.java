package com.scenemax.desktop;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.scenemax.desktop.ai.gemma.LocalGemmaBridgeConfig;
import com.scenemax.desktop.ai.gemma.LocalGemmaBridgeStatus;
import com.scenemax.desktop.ai.gemma.install.GemmaInstallManifest;
import com.scenemax.desktop.ai.gemma.install.GemmaInstallProgress;
import com.scenemax.desktop.ai.gemma.install.GemmaInstaller;
import com.scenemax.desktop.ai.gemma.install.GemmaModelVariant;
import com.scenemax.desktop.ai.gemma.install.VcRuntimeInstaller;
import org.apache.commons.io.FileUtils;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.awt.event.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SettingsDialog extends JDialog {
    private final MainApp host;
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTabbedPane tabbedPane1;
    private JTextField txtClassrommsServer;
    private JTextField txtGalleryHost;
    private JTextField txtFtpUploadUrl;
    private JTextField txtConsumerKey;
    private JTextField txtConsumerSecret;
    private JTextField txtFtpHostName;
    private JTextField txtFtpUser;
    private JTextField txtFtpPassword;
    private JButton btnRestoreGalleryDefaults;
    private JTextField txtProjJvmArch;
    private JButton btnResetRuntime;
    private JTextField txtScenemaxServer;
    private JTextField txtMcpPort;
    private JLabel lblMcpEndpoint;
    private JButton btnConnectClaude;
    private JButton btnConnectCodex;
    private JTextField txtClaudeCliPath;
    private JTextField txtCodexCliPath;
    private JCheckBox chkGemmaEnabled;
    private JTextField txtGemmaEndpoint;
    private JTextField txtGemmaModel;
    private JTextField txtGemmaApiKey;
    private JTextField txtGemmaTimeout;
    private JLabel lblGemmaStatus;
    private JButton btnTestGemma;
    private JComboBox<GemmaModelVariant> cboGemmaVariant;
    private JButton btnDownloadGemma;
    private JButton btnStartGemma;
    private JButton btnInstallVcRuntime;
    private JLabel lblGemmaInstallInfo;

    public SettingsDialog() {
        this(null);
    }

    public SettingsDialog(MainApp host) {
        this.host = host;
        setContentPane(contentPane);
        setModal(true);
        setMinimumSize(new Dimension(960, 840));
        getRootPane().setDefaultButton(buttonOK);

        String jvmArch = AppDB.getInstance().getParam("projector_jvm_arch");
        if (jvmArch != null && (jvmArch.equals("64") || jvmArch.equals("32"))) {
            txtProjJvmArch.setText(jvmArch);
        }
        String mcpPort = AppDB.getInstance().getParam("mcp_server_port");
        txtMcpPort = new JTextField();
        txtMcpPort.setText(mcpPort == null || mcpPort.trim().isEmpty() ? "8765" : mcpPort.trim());
        txtClaudeCliPath = new JTextField(AppDB.getInstance().getParam("mcp_claude_cli_path"));
        txtCodexCliPath = new JTextField(AppDB.getInstance().getParam("mcp_codex_cli_path"));
        chkGemmaEnabled = new JCheckBox("Enable local Gemma bridge");
        chkGemmaEnabled.setSelected(Boolean.parseBoolean(AppDB.getInstance().getParam("local_gemma_enabled")));
        txtGemmaEndpoint = new JTextField(defaultValue(AppDB.getInstance().getParam("local_gemma_endpoint"), LocalGemmaBridgeConfig.DEFAULT_ENDPOINT));
        txtGemmaModel = new JTextField(defaultValue(AppDB.getInstance().getParam("local_gemma_model"), LocalGemmaBridgeConfig.DEFAULT_MODEL));
        txtGemmaApiKey = new JTextField(defaultValue(AppDB.getInstance().getParam("local_gemma_api_key"), ""));
        txtGemmaTimeout = new JTextField(defaultValue(AppDB.getInstance().getParam("local_gemma_timeout_seconds"), String.valueOf(LocalGemmaBridgeConfig.DEFAULT_TIMEOUT_SECONDS)));
        cboGemmaVariant = new JComboBox<>(GemmaModelVariant.values());
        cboGemmaVariant.setSelectedItem(resolveVariant(defaultValue(AppDB.getInstance().getParam("local_gemma_model"), LocalGemmaBridgeConfig.DEFAULT_MODEL)));
        tabbedPane1.addTab("MCP", createMcpPanel());
        tabbedPane1.addTab("Local Gemma", createGemmaPanel());
        refreshMcpPanelState();
        refreshGemmaPanelState();

        txtGalleryHost.setText(isDefault("gallery_host", Util.GALLERY_HOST) ? "DEFAULT" : "CUSTOM");
        txtConsumerKey.setText(Util.WRITE_CONSUMER_KEY.isEmpty() ? "MISSING" : "CUSTOM");
        txtConsumerSecret.setText(Util.WRITE_CONSUMER_SECRET.isEmpty() ? "MISSING" : "CUSTOM");
        txtFtpUploadUrl.setText(isDefault("ftp_upload_folder", Util.FTP_UPLOAD_URL) ? "DEFAULT" : "CUSTOM");
        txtFtpHostName.setText(isDefault("ftp_host_name", Util.FTP_HOST_NAME) ? "DEFAULT" : "CUSTOM");

        txtFtpUser.setText(isDefault("ftp_user", Util.FTP_USER_NAME) ? "DEFAULT" : "CUSTOM");
        txtFtpPassword.setText(isDefault("ftp_password", Util.FTP_PASSWORD) ? "DEFAULT" : "CUSTOM");


        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });

        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        btnRestoreGalleryDefaults.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                txtGalleryHost.setText("DEFAULT");
                txtConsumerKey.setText("DEFAULT");
                txtConsumerSecret.setText("DEFAULT");
                txtFtpUploadUrl.setText("DEFAULT");
                txtFtpHostName.setText("DEFAULT");
                txtFtpUser.setText("DEFAULT");
                txtFtpPassword.setText("DEFAULT");
            }
        });

        btnResetRuntime.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                try {
                    FileUtils.deleteQuietly(new File("bulletjme.dll"));
                    FileUtils.deleteQuietly(new File("jni4net.n.w64.v40-0.8.8.0.dll"));
                    FileUtils.deleteQuietly(new File("jni4net.n.w32.v40-0.8.8.0.dll"));
                    FileUtils.deleteQuietly(new File("jni4net.n-0.8.8.0.dll"));
                    FileUtils.deleteQuietly(new File("lwjgl.dll"));
                    FileUtils.deleteQuietly(new File("lwjgl64.dll"));
                    FileUtils.deleteQuietly(new File("OpenAL64.dll"));
                    String ver = Util.getAppVersion();
                    String launcherName = "launcher" + ver + ".jar";
                    FileUtils.forceDelete(new File(launcherName));
                    JOptionPane.showMessageDialog(null, "RunTime reset finished successfully", "RunTime Reset", JOptionPane.INFORMATION_MESSAGE);

                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });

    }

    private void onOK() {

        updateParam("projector_jvm_arch", txtProjJvmArch.getText().trim());
        updateParam("classrooms_server", txtClassrommsServer.getText().trim());
        updateParam("gallery_host", txtGalleryHost.getText().trim());
        if (txtConsumerKey.getText().toLowerCase().trim().equals("missing")) {
            updateParam("write_gallery_api_key", "");
        } else {
            updateParam("write_gallery_api_key", txtConsumerKey.getText().trim());
        }

        if (txtConsumerSecret.getText().toLowerCase().trim().equals("missing")) {
            updateParam("write_gallery_api_secret", "");
        } else {
            updateParam("write_gallery_api_secret", txtConsumerSecret.getText().trim());
        }

        updateParam("ftp_upload_folder", txtFtpUploadUrl.getText().trim());
        updateParam("ftp_host_name", txtFtpHostName.getText().trim());
        updateParam("ftp_user", txtFtpUser.getText().trim());
        updateParam("ftp_password", txtFtpPassword.getText().trim());
        try {
            updateMcpPort();
        } catch (NumberFormatException ex) {
            return;
        }
        AppDB.getInstance().setParam("mcp_claude_cli_path", normalizeOptionalPath(txtClaudeCliPath.getText()));
        AppDB.getInstance().setParam("mcp_codex_cli_path", normalizeOptionalPath(txtCodexCliPath.getText()));
        try {
            updateLocalGemmaSettings();
        } catch (NumberFormatException ex) {
            return;
        }

        dispose();
    }

    private String normalizeOptionalPath(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private void updateMcpPort() {
        String portText = txtMcpPort.getText().trim();
        if (portText.isEmpty() || portText.equalsIgnoreCase("default")) {
            AppDB.getInstance().setParam("mcp_server_port", "");
            return;
        }

        try {
            int port = Integer.parseInt(portText);
            if (port < 1 || port > 65535) {
                throw new NumberFormatException("Port out of range");
            }
            AppDB.getInstance().setParam("mcp_server_port", String.valueOf(port));
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                    "MCP port must be a number between 1 and 65535.",
                    "Invalid MCP Port",
                    JOptionPane.ERROR_MESSAGE);
            throw ex;
        }
    }

    private void updateLocalGemmaSettings() {
        AppDB.getInstance().setParam("local_gemma_enabled", String.valueOf(chkGemmaEnabled.isSelected()));
        AppDB.getInstance().setParam("local_gemma_endpoint", normalizeOptionalPath(txtGemmaEndpoint.getText()));
        AppDB.getInstance().setParam("local_gemma_model", normalizeOptionalPath(txtGemmaModel.getText()));
        AppDB.getInstance().setParam("local_gemma_api_key", normalizeOptionalPath(txtGemmaApiKey.getText()));

        String timeoutText = txtGemmaTimeout.getText().trim();
        int timeout;
        try {
            timeout = Integer.parseInt(timeoutText);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                    "Local Gemma timeout must be a number of seconds.",
                    "Invalid Gemma Timeout",
                    JOptionPane.ERROR_MESSAGE);
            throw ex;
        }
        if (timeout < 5) {
            JOptionPane.showMessageDialog(this,
                    "Local Gemma timeout must be at least 5 seconds.",
                    "Invalid Gemma Timeout",
                    JOptionPane.ERROR_MESSAGE);
            throw new NumberFormatException("Timeout too small");
        }
        AppDB.getInstance().setParam("local_gemma_timeout_seconds", String.valueOf(timeout));
    }

    private void updateParam(String key, String val) {
        val = val.toLowerCase();

        if (val.equals("custom")) {
            return;
        }

        if (val.equals("default")) {
            AppDB.getInstance().setParam(key, "");
        } else {
            AppDB.getInstance().setParam(key, val);
        }

    }

    private static boolean isDefault(String configKey, String currentValue) {
        String configDefault = AppConfig.get(configKey);
        return currentValue.equalsIgnoreCase(configDefault);
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private JPanel createMcpPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(new JLabel("Fixed MCP Port:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(txtMcpPort, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        panel.add(new JLabel("Set a fixed localhost port for Claude Code / Codex MCP clients."), gbc);

        gbc.gridy = 2;
        panel.add(new JLabel("Use \"DEFAULT\" or leave blank to fall back to automatic local port selection."), gbc);

        gbc.gridy = 3;
        panel.add(new JLabel("Current Endpoint:"), gbc);

        gbc.gridy = 4;
        lblMcpEndpoint = new JLabel("Unavailable");
        panel.add(lblMcpEndpoint, gbc);

        gbc.gridy = 5;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        btnConnectClaude = new JButton("Connect Claude Code");
        btnConnectClaude.addActionListener(e -> connectClient("Claude Code"));
        panel.add(btnConnectClaude, gbc);

        gbc.gridx = 1;
        btnConnectCodex = new JButton("Connect Codex");
        btnConnectCodex.addActionListener(e -> connectClient("Codex"));
        panel.add(btnConnectCodex, gbc);

        gbc.gridy = 6;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        panel.add(new JLabel("Claude CLI Path Override (optional):"), gbc);

        gbc.gridy = 7;
        gbc.gridwidth = 1;
        gbc.weightx = 1;
        panel.add(txtClaudeCliPath, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        JButton btnBrowseClaude = new JButton("Browse...");
        btnBrowseClaude.addActionListener(e -> chooseCliPath(txtClaudeCliPath, "Select Claude Code CLI"));
        panel.add(btnBrowseClaude, gbc);

        gbc.gridx = 0;
        gbc.gridy = 8;
        gbc.gridwidth = 2;
        panel.add(new JLabel("Codex CLI Path Override (optional):"), gbc);

        gbc.gridy = 9;
        gbc.gridwidth = 1;
        gbc.weightx = 1;
        panel.add(txtCodexCliPath, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        JButton btnBrowseCodex = new JButton("Browse...");
        btnBrowseCodex.addActionListener(e -> chooseCliPath(txtCodexCliPath, "Select Codex CLI"));
        panel.add(btnBrowseCodex, gbc);

        gbc.gridx = 0;
        gbc.gridy = 10;
        gbc.gridwidth = 2;
        panel.add(new JLabel("The buttons will register this local MCP endpoint with the selected client."), gbc);

        gbc.gridy = 11;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(new JPanel(), gbc);

        return panel;
    }

    private JPanel createGemmaPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(chkGemmaEnabled, gbc);

        gbc.gridy = 1;
        panel.add(new JLabel("Endpoint URL:"), gbc);

        gbc.gridy = 2;
        panel.add(txtGemmaEndpoint, gbc);

        gbc.gridy = 3;
        panel.add(new JLabel("Model Name:"), gbc);

        gbc.gridy = 4;
        panel.add(txtGemmaModel, gbc);

        gbc.gridy = 5;
        panel.add(new JLabel("API Key (optional):"), gbc);

        gbc.gridy = 6;
        panel.add(txtGemmaApiKey, gbc);

        gbc.gridy = 7;
        panel.add(new JLabel("Timeout Seconds:"), gbc);

        gbc.gridy = 8;
        panel.add(txtGemmaTimeout, gbc);

        gbc.gridy = 9;
        panel.add(new JLabel("Download Variant:"), gbc);

        gbc.gridy = 10;
        gbc.gridwidth = 1;
        panel.add(cboGemmaVariant, gbc);

        gbc.gridx = 1;
        btnDownloadGemma = new JButton("Download Gemma");
        btnDownloadGemma.addActionListener(e -> downloadGemma());
        panel.add(btnDownloadGemma, gbc);

        gbc.gridx = 0;
        gbc.gridy = 11;
        gbc.gridwidth = 2;
        lblGemmaInstallInfo = new JLabel("No local Gemma install detected.");
        panel.add(lblGemmaInstallInfo, gbc);

        gbc.gridy = 12;
        lblGemmaStatus = new JLabel("Status unavailable");
        panel.add(lblGemmaStatus, gbc);

        gbc.gridy = 13;
        gbc.gridwidth = 2;
        btnInstallVcRuntime = new JButton("Install VC++ Runtime");
        btnInstallVcRuntime.addActionListener(e -> installVcRuntime());
        panel.add(btnInstallVcRuntime, gbc);

        gbc.gridy = 14;
        panel.add(new JLabel("If Local Gemma reports missing DLLs, install or repair the Microsoft VC++ runtime here."), gbc);

        gbc.gridy = 15;
        gbc.gridwidth = 1;
        btnStartGemma = new JButton("Start Local Gemma");
        btnStartGemma.addActionListener(e -> startLocalGemma());
        panel.add(btnStartGemma, gbc);

        gbc.gridx = 1;
        btnTestGemma = new JButton("Test Bridge");
        btnTestGemma.addActionListener(e -> testLocalGemmaBridge());
        panel.add(btnTestGemma, gbc);

        gbc.gridx = 0;
        gbc.gridy = 16;
        gbc.gridwidth = 2;
        JButton btnApplyGemma = new JButton("Apply Settings");
        btnApplyGemma.addActionListener(e -> {
            try {
                updateLocalGemmaSettings();
                if (host != null) {
                    host.reloadLocalGemmaBridgeFromSettings();
                }
                refreshGemmaPanelState();
            } catch (NumberFormatException ignored) {
            }
        });
        panel.add(btnApplyGemma, gbc);

        gbc.gridx = 0;
        gbc.gridy = 17;
        gbc.gridwidth = 2;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(new JPanel(), gbc);

        return panel;
    }

    private void refreshMcpPanelState() {
        if (lblMcpEndpoint == null) {
            return;
        }
        String endpoint = resolveCurrentEndpoint();
        lblMcpEndpoint.setText(endpoint == null || endpoint.isBlank() ? "Unavailable" : endpoint);
    }

    private void refreshGemmaPanelState() {
        if (lblGemmaStatus == null) {
            return;
        }

        GemmaInstallManifest installed = new GemmaInstaller().loadInstalledManifest();
        VcRuntimeInstaller vcInstaller = new VcRuntimeInstaller();
        if (lblGemmaInstallInfo != null) {
            if (installed == null) {
                String details = "Install location: " + new File("AI\\models").getAbsolutePath();
                if (vcInstaller.isDownloaded()) {
                    details += " | VC++ installer ready";
                }
                lblGemmaInstallInfo.setText(details);
            } else {
                String details = "Installed: " + installed.getModelDisplayName() + " at " + installed.getModelPath();
                if (vcInstaller.isDownloaded()) {
                    details += " | VC++ installer ready";
                }
                lblGemmaInstallInfo.setText(details);
            }
        }

        LocalGemmaBridgeStatus status = host != null ? host.getLocalGemmaBridgeStatus() : null;
        if (status == null) {
            LocalGemmaBridgeConfig config = buildGemmaConfigFromUi();
            String state = config.isEnabled() ? "Configured: " + config.getEndpointUrl() : "Disabled";
            if (installed != null) {
                state += " | Local files ready";
            }
            lblGemmaStatus.setText(state);
            return;
        }

        String state;
        if (!status.isEnabled()) {
            state = "Disabled";
        } else if (status.isReachable()) {
            state = "Ready: " + status.getModel() + " @ " + status.getEndpointUrl();
            if (host != null && host.isLocalGemmaServiceRunning()) {
                state += " | local runtime started";
            }
        } else {
            state = "Offline: " + status.getMessage();
            if (installed != null) {
                state += " | Installed model: " + installed.getModelDisplayName();
            }
        }
        lblGemmaStatus.setText(state);
    }

    private String resolveCurrentEndpoint() {
        if (host != null && host.getAutomationHttpServer() != null && host.getAutomationHttpServer().isRunning()) {
            return host.getAutomationHttpServer().getEndpointUrl();
        }

        String portText = txtMcpPort != null ? txtMcpPort.getText().trim() : "";
        if (portText.isEmpty() || portText.equalsIgnoreCase("default")) {
            String saved = AppDB.getInstance().getParam("mcp_server_port");
            portText = saved == null ? "" : saved.trim();
        }
        if (portText.isEmpty()) {
            return "http://127.0.0.1:8765/mcp";
        }
        return "http://127.0.0.1:" + portText + "/mcp";
    }

    private void connectClient(String clientName) {
        try {
            updateMcpPort();
        } catch (NumberFormatException ex) {
            return;
        }

        if (host != null) {
            host.restartAutomationServerFromSettings();
        }
        refreshMcpPanelState();

        String endpoint = resolveCurrentEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            JOptionPane.showMessageDialog(this,
                    "SceneMax MCP endpoint is unavailable.",
                    "MCP Connection",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        setConnectButtonsEnabled(false);

        SwingWorker<CommandResult, Void> worker = new SwingWorker<>() {
            @Override
            protected CommandResult doInBackground() throws Exception {
                if ("Claude Code".equals(clientName)) {
                    return runClientCommand(resolveClaudeExecutable(), buildClaudeArgs(endpoint));
                }
                return runClientCommand(resolveCodexExecutable(), buildCodexArgs(endpoint));
            }

            @Override
            protected void done() {
                setConnectButtonsEnabled(true);
                try {
                    CommandResult result = get();
                    if (result.exitCode == 0 || result.output.toLowerCase(Locale.ROOT).contains("already exists")) {
                        JOptionPane.showMessageDialog(SettingsDialog.this,
                                buildSuccessMessage(clientName, endpoint, result.output),
                                clientName + " Connected",
                                JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(SettingsDialog.this,
                                clientName + " connection failed.\n\nCommand:\n" + result.commandLine +
                                        "\n\nOutput:\n" + result.output,
                                clientName + " Connection Failed",
                                JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(SettingsDialog.this,
                            clientName + " connection failed.\n\n" + ex.getMessage(),
                            clientName + " Connection Failed",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void testLocalGemmaBridge() {
        try {
            updateLocalGemmaSettings();
        } catch (NumberFormatException ex) {
            return;
        }

        btnTestGemma.setEnabled(false);
        if (host != null) {
            host.reloadLocalGemmaBridgeFromSettings();
        }
        refreshGemmaPanelState();

        SwingWorker<LocalGemmaBridgeStatus, Void> worker = new SwingWorker<>() {
            @Override
            protected LocalGemmaBridgeStatus doInBackground() {
                if (host != null) {
                    return host.pingLocalGemmaBridge();
                }
                return new com.scenemax.desktop.ai.gemma.OpenAiCompatibleGemmaBridge(buildGemmaConfigFromUi()).checkStatus();
            }

            @Override
            protected void done() {
                btnTestGemma.setEnabled(true);
                try {
                    LocalGemmaBridgeStatus status = get();
                    refreshGemmaPanelState();
                    if (status.isEnabled() && status.isReachable()) {
                        JOptionPane.showMessageDialog(SettingsDialog.this,
                                "Local Gemma bridge is reachable.\n\nModel: " + status.getModel()
                                        + "\nEndpoint: " + status.getEndpointUrl(),
                                "Gemma Ready",
                                JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(SettingsDialog.this,
                                "Local Gemma bridge is not ready.\n\n" + status.getMessage(),
                                "Gemma Offline",
                                JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(SettingsDialog.this,
                            "Local Gemma bridge test failed.\n\n" + ex.getMessage(),
                            "Gemma Test Failed",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private LocalGemmaBridgeConfig buildGemmaConfigFromUi() {
        int timeout = LocalGemmaBridgeConfig.DEFAULT_TIMEOUT_SECONDS;
        try {
            timeout = Integer.parseInt(txtGemmaTimeout.getText().trim());
        } catch (NumberFormatException ignored) {
        }
        return new LocalGemmaBridgeConfig(
                chkGemmaEnabled.isSelected(),
                txtGemmaEndpoint.getText().trim(),
                txtGemmaModel.getText().trim(),
                txtGemmaApiKey.getText().trim(),
                timeout
        );
    }

    private GemmaModelVariant resolveVariant(String modelName) {
        if (modelName != null) {
            for (GemmaModelVariant variant : GemmaModelVariant.values()) {
                if (variant.getModelId().equalsIgnoreCase(modelName.trim())) {
                    return variant;
                }
            }
        }
        return GemmaModelVariant.E2B;
    }

    private void downloadGemma() {
        GemmaModelVariant variant = (GemmaModelVariant) cboGemmaVariant.getSelectedItem();
        if (variant == null) {
            variant = GemmaModelVariant.E2B;
        }
        final GemmaModelVariant selectedVariant = variant;

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "SceneMax will download:\n\n"
                        + "1. LiteRT-LM Windows runtime\n"
                        + "2. " + selectedVariant.getDisplayName() + "\n\n"
                        + "Install folder:\n" + new File("AI").getAbsolutePath() + "\n\n"
                        + "By continuing you confirm that you reviewed the Gemma terms:\n"
                        + "https://ai.google.dev/gemma/terms",
                "Download Gemma",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.INFORMATION_MESSAGE
        );
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }

        btnDownloadGemma.setEnabled(false);
        if (btnTestGemma != null) {
            btnTestGemma.setEnabled(false);
        }

        GemmaDownloadDialog progressDialog = new GemmaDownloadDialog(this, selectedVariant);
        SwingWorker<GemmaInstallManifest, GemmaInstallProgress> worker = new SwingWorker<>() {
            @Override
            protected GemmaInstallManifest doInBackground() throws Exception {
                GemmaInstaller installer = new GemmaInstaller();
                return installer.install(selectedVariant, this::publish);
            }

            @Override
            protected void process(List<GemmaInstallProgress> chunks) {
                if (!chunks.isEmpty()) {
                    progressDialog.update(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                btnDownloadGemma.setEnabled(true);
                if (btnTestGemma != null) {
                    btnTestGemma.setEnabled(true);
                }
                progressDialog.dispose();
                try {
                    GemmaInstallManifest manifest = get();
                    txtGemmaModel.setText(selectedVariant.getModelId());
                    chkGemmaEnabled.setSelected(true);
                    refreshGemmaPanelState();
                    JOptionPane.showMessageDialog(SettingsDialog.this,
                            "Gemma files downloaded successfully.\n\n"
                                    + "Model: " + manifest.getModelDisplayName() + "\n"
                                    + "Model file: " + manifest.getModelPath() + "\n"
                                    + "Runtime: " + manifest.getRuntimePath() + "\n\n"
                                    + "Next step:\n"
                                    + "Use the local Gemma bridge/service configuration to point SceneMax at the installed runtime.",
                            "Gemma Download Complete",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(SettingsDialog.this,
                            "Gemma download failed.\n\n" + ex.getMessage(),
                            "Gemma Download Failed",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
        progressDialog.setVisible(true);
    }

    private void installVcRuntime() {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "SceneMax will download the Microsoft Visual C++ Redistributable installer to:\n\n"
                        + new VcRuntimeInstaller().getInstallerPath() + "\n\n"
                        + "After the download finishes, SceneMax will open the installer for you.\n"
                        + "Windows may ask for permission to continue.",
                "Install VC++ Runtime",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.INFORMATION_MESSAGE
        );
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }

        if (btnInstallVcRuntime != null) {
            btnInstallVcRuntime.setEnabled(false);
        }
        if (btnStartGemma != null) {
            btnStartGemma.setEnabled(false);
        }
        if (btnTestGemma != null) {
            btnTestGemma.setEnabled(false);
        }

        GemmaDownloadDialog progressDialog = new GemmaDownloadDialog(this, "Installing Microsoft VC++ Runtime");
        SwingWorker<File, GemmaInstallProgress> worker = new SwingWorker<>() {
            @Override
            protected File doInBackground() throws Exception {
                VcRuntimeInstaller installer = new VcRuntimeInstaller();
                File installerFile = installer.download(this::publish).toFile();
                installer.launchInstaller(installerFile.toPath());
                return installerFile;
            }

            @Override
            protected void process(List<GemmaInstallProgress> chunks) {
                if (!chunks.isEmpty()) {
                    progressDialog.update(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                if (btnInstallVcRuntime != null) {
                    btnInstallVcRuntime.setEnabled(true);
                }
                if (btnStartGemma != null) {
                    btnStartGemma.setEnabled(true);
                }
                if (btnTestGemma != null) {
                    btnTestGemma.setEnabled(true);
                }
                progressDialog.dispose();
                try {
                    File installerFile = get();
                    refreshGemmaPanelState();
                    JOptionPane.showMessageDialog(SettingsDialog.this,
                            "The Microsoft VC++ runtime installer was opened.\n\n"
                                    + "Installer: " + installerFile.getAbsolutePath() + "\n\n"
                                    + "Next step:\n"
                                    + "1. Finish the Microsoft installer.\n"
                                    + "2. If it offers Repair, choose Repair.\n"
                                    + "3. Return here and click Start Local Gemma.\n"
                                    + "4. Click Test Bridge after the service starts.",
                            "VC++ Installer Ready",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(SettingsDialog.this,
                            "Installing the VC++ runtime helper failed.\n\n" + ex.getMessage(),
                            "VC++ Runtime Install Failed",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
        progressDialog.setVisible(true);
    }

    private void startLocalGemma() {
        try {
            updateLocalGemmaSettings();
        } catch (NumberFormatException ex) {
            return;
        }

        if (host == null) {
            JOptionPane.showMessageDialog(this,
                    "Start Local Gemma is available only when opened from the SceneMax IDE.",
                    "Local Gemma",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        btnStartGemma.setEnabled(false);
        if (btnTestGemma != null) {
            btnTestGemma.setEnabled(false);
        }

        SwingWorker<LocalGemmaBridgeStatus, Void> worker = new SwingWorker<>() {
            @Override
            protected LocalGemmaBridgeStatus doInBackground() {
                return host.startInstalledLocalGemmaService();
            }

            @Override
            protected void done() {
                btnStartGemma.setEnabled(true);
                if (btnTestGemma != null) {
                    btnTestGemma.setEnabled(true);
                }
                try {
                    LocalGemmaBridgeStatus status = get();
                    refreshGemmaPanelState();
                    JOptionPane.showMessageDialog(SettingsDialog.this,
                            "Local Gemma started successfully.\n\n"
                                    + "Model: " + status.getModel() + "\n"
                                    + "Endpoint: " + status.getEndpointUrl() + "\n\n"
                                    + "How to test:\n"
                                    + "1. Click Test Bridge.\n"
                                    + "2. Ask the future AI console a simple question.\n"
                                    + "3. Watch the top bar for 'Gemma: ready'.",
                            "Local Gemma Ready",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(SettingsDialog.this,
                            "Starting Local Gemma failed.\n\n" + ex.getMessage(),
                            "Local Gemma Failed",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void setConnectButtonsEnabled(boolean enabled) {
        if (btnConnectClaude != null) {
            btnConnectClaude.setEnabled(enabled);
        }
        if (btnConnectCodex != null) {
            btnConnectCodex.setEnabled(enabled);
        }
    }

    private List<String> buildClaudeArgs(String endpoint) {
        List<String> args = new ArrayList<>();
        args.add("mcp");
        args.add("add");
        args.add("--transport");
        args.add("http");
        args.add("--scope");
        args.add("user");
        args.add("scenemax");
        args.add(endpoint);
        return args;
    }

    private List<String> buildCodexArgs(String endpoint) {
        List<String> args = new ArrayList<>();
        args.add("mcp");
        args.add("add");
        args.add("scenemax");
        args.add("--url");
        args.add(endpoint);
        return args;
    }

    private CommandResult runClientCommand(String executable, List<String> args) throws Exception {
        String commandText = buildPowerShellInvocation(executable, args);
        ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-Command", commandText);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }
        int exitCode = process.waitFor();
        return new CommandResult(exitCode, output.toString().trim(), commandText);
    }

    private String resolveClaudeExecutable() throws IOException {
        String configured = normalizeOptionalPath(txtClaudeCliPath.getText());
        if (!configured.isBlank() && new File(configured).exists()) {
            return configured;
        }

        String discovered = discoverViaWhere("claude");
        if (discovered != null) {
            txtClaudeCliPath.setText(discovered);
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
                txtClaudeCliPath.setText(candidate);
                return candidate;
            }
        }

        throw new IOException("Claude Code CLI was not found automatically. Set the CLI path in the MCP tab and try again.");
    }

    private String resolveCodexExecutable() throws IOException {
        String configured = normalizeOptionalPath(txtCodexCliPath.getText());
        if (!configured.isBlank() && new File(configured).exists()) {
            return configured;
        }

        String discovered = discoverViaWhere("codex");
        if (discovered != null) {
            txtCodexCliPath.setText(discovered);
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
                        txtCodexCliPath.setText(candidate.getAbsolutePath());
                        return candidate.getAbsolutePath();
                    }
                }
            }
        }

        throw new IOException("Codex CLI was not found automatically. Set the CLI path in the MCP tab and try again.");
    }

    private String discoverViaWhere(String executable) {
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
                for (String line : lines) {
                    if (new File(line).exists()) {
                        return line;
                    }
                }
                return lines.get(0);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String combine(String root, String... parts) {
        if (root == null || root.isBlank()) {
            return null;
        }
        File file = new File(root);
        for (String part : parts) {
            file = new File(file, part);
        }
        return file.getAbsolutePath();
    }

    private void chooseCliPath(JTextField target, String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (!target.getText().trim().isEmpty()) {
            chooser.setSelectedFile(new File(target.getText().trim()));
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            target.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private String buildPowerShellInvocation(String executable, List<String> args) {
        StringBuilder sb = new StringBuilder();
        sb.append("& ").append(psQuote(executable));
        for (String arg : args) {
            sb.append(' ').append(psQuote(arg));
        }
        return sb.toString();
    }

    private String psQuote(String raw) {
        return "'" + raw.replace("'", "''") + "'";
    }

    private String buildSuccessMessage(String clientName, String endpoint, String output) {
        StringBuilder sb = new StringBuilder();
        sb.append(clientName).append(" is now configured to use SceneMax MCP.\n\n");
        sb.append("Endpoint:\n").append(endpoint).append("\n\n");
        if ("Claude Code".equals(clientName)) {
            sb.append("How to test:\n");
            sb.append("1. Open Claude Code.\n");
            sb.append("2. Run /mcp and verify that 'scenemax' appears.\n");
            sb.append("3. Ask: Use the SceneMax MCP tools to list available tools.\n");
            sb.append("4. Then ask: Use SceneMax to search for 'camera' in the project.\n");
        } else {
            sb.append("How to test:\n");
            sb.append("1. Open Codex.\n");
            sb.append("2. Run: codex mcp list\n");
            sb.append("3. Verify that 'scenemax' appears.\n");
            sb.append("4. Then ask: Use the SceneMax MCP server to list tools and search for 'camera'.\n");
        }
        if (output != null && !output.isBlank()) {
            sb.append("\nCLI output:\n").append(output);
        }
        return sb.toString();
    }

    private static class CommandResult {
        final int exitCode;
        final String output;
        final String commandLine;

        CommandResult(int exitCode, String output, String commandLine) {
            this.exitCode = exitCode;
            this.output = output;
            this.commandLine = commandLine;
        }
    }

    private static class GemmaDownloadDialog extends JDialog {
        private final JProgressBar progressBar = new JProgressBar(0, 100);
        private final JLabel lblMessage = new JLabel("Preparing download...");

        GemmaDownloadDialog(Dialog owner, GemmaModelVariant variant) {
            super(owner, "Downloading " + variant.getDisplayName(), false);
            initUi(owner);
        }

        GemmaDownloadDialog(Dialog owner, String title) {
            super(owner, title, false);
            initUi(owner);
        }

        private void initUi(Dialog owner) {
            JPanel panel = new JPanel(new BorderLayout(10, 10));
            panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            panel.add(lblMessage, BorderLayout.NORTH);
            panel.add(progressBar, BorderLayout.CENTER);
            progressBar.setStringPainted(true);
            setContentPane(panel);
            setSize(420, 120);
            setLocationRelativeTo(owner);
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        }

        void update(GemmaInstallProgress progress) {
            progressBar.setValue(progress.getPercent());
            lblMessage.setText(progress.getMessage());
        }
    }

    private void onCancel() {
        // add your code here if necessary
        dispose();
    }

    public static void main(String[] args) {
        SettingsDialog dialog = new SettingsDialog();
        dialog.setSize(960, 840);
        dialog.setVisible(true);
        System.exit(0);
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        contentPane = new JPanel();
        contentPane.setLayout(new GridLayoutManager(2, 1, new Insets(10, 10, 10, 10), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1, true, false));
        panel1.add(panel2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        buttonOK = new JButton();
        buttonOK.setText("OK");
        panel2.add(buttonOK, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonCancel = new JButton();
        buttonCancel.setText("Cancel");
        panel2.add(buttonCancel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        tabbedPane1 = new JTabbedPane();
        panel3.add(tabbedPane1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(200, 200), null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(4, 4, new Insets(0, 0, 0, 0), -1, -1));
        tabbedPane1.addTab("General", panel4);
        final JLabel label1 = new JLabel();
        label1.setText("Classrooms Server:");
        panel4.add(label1, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel4.add(spacer2, new GridConstraints(3, 1, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        txtClassrommsServer = new JTextField();
        txtClassrommsServer.setText("DEFAULT");
        panel4.add(txtClassrommsServer, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Projector JVM Arch:");
        panel4.add(label2, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtProjJvmArch = new JTextField();
        txtProjJvmArch.setText("DEFAULT");
        panel4.add(txtProjJvmArch, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final Spacer spacer3 = new Spacer();
        panel4.add(spacer3, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        btnResetRuntime = new JButton();
        btnResetRuntime.setText("Reset RunTime");
        panel4.add(btnResetRuntime, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridLayoutManager(8, 3, new Insets(0, 0, 0, 0), -1, -1));
        tabbedPane1.addTab("Online Gallery", panel5);
        final JLabel label3 = new JLabel();
        label3.setText("Host:");
        panel5.add(label3, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtGalleryHost = new JTextField();
        txtGalleryHost.setText("DEFAULT");
        panel5.add(txtGalleryHost, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("FTP Upload URL:");
        panel5.add(label4, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtFtpUploadUrl = new JTextField();
        txtFtpUploadUrl.setText("DEFAULT");
        panel5.add(txtFtpUploadUrl, new GridConstraints(3, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label5 = new JLabel();
        label5.setText("Consumer Key:");
        panel5.add(label5, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtConsumerKey = new JTextField();
        txtConsumerKey.setText("DEFAULT");
        panel5.add(txtConsumerKey, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label6 = new JLabel();
        label6.setText("Consumer Secret:");
        panel5.add(label6, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtConsumerSecret = new JTextField();
        txtConsumerSecret.setText("DEFAULT");
        panel5.add(txtConsumerSecret, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label7 = new JLabel();
        label7.setText("FTP Host Name:");
        panel5.add(label7, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtFtpHostName = new JTextField();
        txtFtpHostName.setText("DEFAULT");
        panel5.add(txtFtpHostName, new GridConstraints(4, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label8 = new JLabel();
        label8.setText("FTP User:");
        panel5.add(label8, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtFtpUser = new JTextField();
        txtFtpUser.setText("DEFAULT");
        panel5.add(txtFtpUser, new GridConstraints(5, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label9 = new JLabel();
        label9.setText("FTP Password:");
        panel5.add(label9, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtFtpPassword = new JTextField();
        txtFtpPassword.setText("DEFAULT");
        panel5.add(txtFtpPassword, new GridConstraints(6, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JPanel panel6 = new JPanel();
        panel6.setLayout(new GridLayoutManager(4, 4, new Insets(0, 0, 0, 0), -1, -1));
        panel5.add(panel6, new GridConstraints(7, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label10 = new JLabel();
        Font label10Font = this.$$$getFont$$$(null, Font.BOLD, -1, label10.getFont());
        if (label10Font != null) label10.setFont(label10Font);
        label10.setText("Private schools -");
        panel6.add(label10, new GridConstraints(2, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label11 = new JLabel();
        label11.setForeground(new Color(-393216));
        label11.setText("Warning!");
        panel6.add(label11, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label12 = new JLabel();
        label12.setText("* Do not change any of these fields unless you absolutely know what you are doing. ");
        panel6.add(label12, new GridConstraints(1, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JLabel label13 = new JLabel();
        label13.setText("Contact: scenemax3d@gmail.com for creating online galley for your students ");
        panel6.add(label13, new GridConstraints(3, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnRestoreGalleryDefaults = new JButton();
        btnRestoreGalleryDefaults.setText("Restore Defaults");
        panel6.add(btnRestoreGalleryDefaults, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer4 = new Spacer();
        panel6.add(spacer4, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    private Font $$$getFont$$$(String fontName, int style, int size, Font currentFont) {
        if (currentFont == null) return null;
        String resultName;
        if (fontName == null) {
            resultName = currentFont.getName();
        } else {
            Font testFont = new Font(fontName, Font.PLAIN, 10);
            if (testFont.canDisplay('a') && testFont.canDisplay('1')) {
                resultName = fontName;
            } else {
                resultName = currentFont.getName();
            }
        }
        Font font = new Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());
        boolean isMac = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).startsWith("mac");
        Font fontWithFallback = isMac ? new Font(font.getFamily(), font.getStyle(), font.getSize()) : new StyleContext().getFont(font.getFamily(), font.getStyle(), font.getSize());
        return fontWithFallback instanceof FontUIResource ? fontWithFallback : new FontUIResource(fontWithFallback);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }

}
