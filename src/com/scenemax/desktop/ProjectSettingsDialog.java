package com.scenemax.desktop;

import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class ProjectSettingsDialog extends JDialog {

    private final SceneMaxProject project;
    private final JTextField txtGamePage = new JTextField();
    private final JTextField txtButlerPath = new JTextField();
    private final JTextField txtWindowsChannel = new JTextField("windows");
    private final JTextField txtLinuxChannel = new JTextField("linux");
    private final JTextField txtMacChannel = new JTextField("macos");
    private final JPasswordField txtApiKey = new JPasswordField();
    private final JLabel lblApiKeyStatus = new JLabel(" ");
    private boolean clearSavedApiKey = false;

    public ProjectSettingsDialog(Window owner, SceneMaxProject project) {
        super(owner, "Project Settings", ModalityType.APPLICATION_MODAL);
        this.project = project;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(createContentPane());
        setPreferredSize(new Dimension(760, 520));
        loadValues();
        pack();
        setMinimumSize(new Dimension(760, 520));
        setLocationRelativeTo(owner);
    }

    private JPanel createContentPane() {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;

        addField(form, gbc, "itch.io Game Page", txtGamePage);

        JPanel butlerRow = new JPanel(new BorderLayout(6, 0));
        JButton btnBrowseButler = new JButton("Browse...");
        JButton btnDetectButler = new JButton("Detect");
        JPanel butlerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        butlerButtons.add(btnDetectButler);
        butlerButtons.add(btnBrowseButler);
        butlerRow.add(txtButlerPath, BorderLayout.CENTER);
        butlerRow.add(butlerButtons, BorderLayout.EAST);
        addField(form, gbc, "Butler Executable", butlerRow);

        addField(form, gbc, "Windows Channel", txtWindowsChannel);
        addField(form, gbc, "Linux Channel", txtLinuxChannel);
        addField(form, gbc, "macOS Channel", txtMacChannel);
        addField(form, gbc, "Butler API Key", txtApiKey);

        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.gridy++;
        lblApiKeyStatus.setForeground(new Color(80, 80, 80));
        form.add(lblApiKeyStatus, gbc);

        gbc.gridy++;
        JTextArea hint = new JTextArea(
                "Game page accepts either an itch.io URL such as https://user.itch.io/game or a target like user/game.\n" +
                "Leave the API key blank to keep the currently saved key. If no API key is saved, SceneMax will use your local butler login session if one exists.\n" +
                "If butler is not on PATH, browse to either butler.exe or the downloaded butler zip. SceneMax can also detect the copy bundled with the itch desktop app."
        );
        hint.setEditable(false);
        hint.setLineWrap(true);
        hint.setWrapStyleWord(true);
        hint.setOpaque(false);
        hint.setFocusable(false);
        hint.setBorder(null);
        form.add(hint, gbc);

        root.add(form, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton btnButlerLogin = new JButton("Butler Login...");
        JButton btnClearApiKey = new JButton("Clear Saved API Key");
        JButton btnCancel = new JButton("Cancel");
        JButton btnSave = new JButton("Save");
        buttons.add(btnButlerLogin);
        buttons.add(btnClearApiKey);
        buttons.add(btnCancel);
        buttons.add(btnSave);
        root.add(buttons, BorderLayout.SOUTH);

        btnBrowseButler.addActionListener(e -> browseForButler());
        btnDetectButler.addActionListener(e -> detectButler());
        btnButlerLogin.addActionListener(e -> startButlerLogin());
        btnClearApiKey.addActionListener(e -> clearSavedApiKey());
        btnCancel.addActionListener(e -> dispose());
        btnSave.addActionListener(e -> onSave());

        return root;
    }

    private void addField(JPanel panel, GridBagConstraints gbc, String label, Component field) {
        gbc.gridx = 0;
        gbc.weightx = 0;
        panel.add(new JLabel(label), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(field, gbc);
        gbc.gridy++;
    }

    private void loadValues() {
        txtGamePage.setText(StringUtils.defaultString(project.itchGamePage));
        txtButlerPath.setText(StringUtils.defaultString(project.itchButlerPath));
        txtWindowsChannel.setText(StringUtils.defaultIfBlank(project.itchWindowsChannel, "windows"));
        txtLinuxChannel.setText(StringUtils.defaultIfBlank(project.itchLinuxChannel, "linux"));
        txtMacChannel.setText(StringUtils.defaultIfBlank(project.itchMacChannel, "macos"));

        String savedApiKey = Util.getProjectItchApiKey(project);
        if (savedApiKey.length() > 0) {
            lblApiKeyStatus.setText("A project-scoped API key is already saved locally.");
        } else {
            lblApiKeyStatus.setText("No project-scoped API key is saved. SceneMax will rely on butler login unless you paste one here.");
        }
    }

    private void browseForButler() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose butler executable or downloaded zip");
        if (txtButlerPath.getText() != null && txtButlerPath.getText().trim().length() > 0) {
            chooser.setSelectedFile(new File(txtButlerPath.getText().trim()));
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();
            if (selectedFile.getName().toLowerCase().endsWith(".zip")) {
                installButlerFromZip(selectedFile);
            } else {
                txtButlerPath.setText(selectedFile.getAbsolutePath());
            }
        }
    }

    private void installButlerFromZip(File zipFile) {
        try {
            String butlerPath = ItchIoHelper.installButlerFromZip(zipFile);
            txtButlerPath.setText(butlerPath);
            JOptionPane.showMessageDialog(
                    this,
                    "Butler was extracted into SceneMax's tools folder and the executable path was saved:\n" + butlerPath,
                    "Butler Installed",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    ex.getMessage(),
                    "Butler Install Failed",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void detectButler() {
        String bundledButler = ItchIoHelper.findBundledButlerExecutable();
        if (bundledButler == null || bundledButler.trim().length() == 0) {
            JOptionPane.showMessageDialog(
                    this,
                    "SceneMax could not detect butler in its local tools folder or in the itch desktop app installation.\n\n" + ItchIoHelper.buildButlerInstallInstructions(),
                    "Butler Not Found",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        txtButlerPath.setText(bundledButler);
        JOptionPane.showMessageDialog(
                this,
                buildButlerDetectionMessage(bundledButler),
                "Butler Detected",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private String buildButlerDetectionMessage(String butlerPath) {
        StringBuilder sb = new StringBuilder();
        sb.append("SceneMax found butler successfully.\n\n");
        sb.append("Location: ").append(butlerPath).append("\n");
        sb.append("Source: ").append(describeButlerSource(butlerPath)).append("\n\n");
        if (ItchIoHelper.hasLocalCredentials()) {
            sb.append("Login status: A previous butler login session was found on this machine.");
        } else {
            sb.append("Login status: No previous butler login session was found yet.\n");
            sb.append("You can click \"Butler Login...\" to sign in now, or paste an API key for this project.");
        }
        return sb.toString();
    }

    private String describeButlerSource(String butlerPath) {
        String normalizedPath = butlerPath == null ? "" : butlerPath.toLowerCase();
        String toolsPath = new File(Util.getWorkingDir(), "tools\\butler").getAbsolutePath().toLowerCase();
        if (normalizedPath.startsWith(toolsPath)) {
            return "SceneMax tools folder";
        }
        if (normalizedPath.contains("\\itch\\broth\\butler\\")) {
            return "itch desktop app installation";
        }
        return "custom location";
    }

    private void startButlerLogin() {
        String usedButlerPath = ItchIoHelper.promptAndRunButlerLogin(this, safeText(txtButlerPath));
        if (usedButlerPath == null) {
            return;
        }

        if (!"butler".equalsIgnoreCase(usedButlerPath)) {
            txtButlerPath.setText(usedButlerPath);
        }
        lblApiKeyStatus.setText("Butler login completed. SceneMax can now use your local butler session for this project.");
    }

    private void clearSavedApiKey() {
        clearSavedApiKey = true;
        txtApiKey.setText("");
        lblApiKeyStatus.setText("The saved API key will be removed when you click Save.");
    }

    private void onSave() {
        String gamePage = txtGamePage.getText() == null ? "" : txtGamePage.getText().trim();
        if (gamePage.length() > 0) {
            try {
                ItchIoHelper.normalizeGameTarget(gamePage);
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Project Settings", JOptionPane.INFORMATION_MESSAGE);
                txtGamePage.requestFocusInWindow();
                return;
            }
        }

        project.itchGamePage = gamePage;
        project.itchButlerPath = safeText(txtButlerPath);
        project.itchWindowsChannel = safeText(txtWindowsChannel);
        project.itchLinuxChannel = safeText(txtLinuxChannel);
        project.itchMacChannel = safeText(txtMacChannel);

        if (!Util.saveProjectSettings(project)) {
            JOptionPane.showMessageDialog(this, "Failed to save project settings.", "Project Settings", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (clearSavedApiKey) {
            Util.setProjectItchApiKey(project, "");
        } else {
            String apiKey = new String(txtApiKey.getPassword()).trim();
            if (apiKey.length() > 0) {
                Util.setProjectItchApiKey(project, apiKey);
            }
        }

        dispose();
    }

    private String safeText(JTextField textField) {
        return textField.getText() == null ? "" : textField.getText().trim();
    }
}
