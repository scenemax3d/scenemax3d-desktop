package com.scenemax.desktop;

import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class ProjectSettingsDialog extends JDialog {

    private final SceneMaxProject project;
    private final JComboBox<String> cmbProjector = new JComboBox<>(new String[]{
            SceneMaxProject.PROJECTOR_CLASSIC_LABEL,
            SceneMaxProject.PROJECTOR_NEXTGEN_LABEL
    });
    private final JTextField txtGamePage = new JTextField();
    private final JTextField txtWindowsChannel = new JTextField("windows");
    private final JTextField txtLinuxChannel = new JTextField("linux");
    private final JTextField txtMacChannel = new JTextField("macos");
    private final JPasswordField txtApiKey = new JPasswordField();
    private final JLabel lblApiKeyStatus = new JLabel(" ");
    private final JTextField txtServerIp = new JTextField("127.0.0.1");
    private final JSpinner spnServerPort = new JSpinner(new SpinnerNumberModel(SceneMaxProject.DEFAULT_MULTIPLAYER_PORT, 1, 65535, 1));
    private final JComboBox<String> cmbDeployOs = new JComboBox<>(new String[]{"Windows", "Linux", "macOS"});
    private final JPasswordField txtServerPassword = new JPasswordField();
    private boolean clearSavedApiKey = false;

    public ProjectSettingsDialog(Window owner, SceneMaxProject project) {
        super(owner, "Project Settings", ModalityType.APPLICATION_MODAL);
        this.project = project;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(createContentPane());
        setPreferredSize(new Dimension(800, 560));
        loadValues();
        pack();
        setMinimumSize(new Dimension(760, 520));
        setLocationRelativeTo(owner);
    }

    private JPanel createContentPane() {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("General", createGeneralPanel());
        tabs.addTab("Multiplayer", createMultiplayerPanel());
        root.add(tabs, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton btnCancel = new JButton("Cancel");
        JButton btnSave = new JButton("Save");
        buttons.add(btnCancel);
        buttons.add(btnSave);
        root.add(buttons, BorderLayout.SOUTH);

        btnCancel.addActionListener(e -> dispose());
        btnSave.addActionListener(e -> onSave());

        return root;
    }

    private JPanel createGeneralPanel() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = formConstraints();

        addField(form, gbc, "Projector", cmbProjector);
        addField(form, gbc, "itch.io Game Page", txtGamePage);
        addField(form, gbc, "Windows Channel", txtWindowsChannel);
        addField(form, gbc, "Linux Channel", txtLinuxChannel);
        addField(form, gbc, "macOS Channel", txtMacChannel);
        addField(form, gbc, "itch.io API Key", txtApiKey);

        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.gridy++;
        lblApiKeyStatus.setForeground(new Color(80, 80, 80));
        form.add(lblApiKeyStatus, gbc);

        gbc.gridy++;
        JButton btnClearApiKey = new JButton("Clear Saved API Key");
        btnClearApiKey.addActionListener(e -> clearSavedApiKey());
        form.add(btnClearApiKey, gbc);

        gbc.gridy++;
        JTextArea hint = new JTextArea(
                "Game page accepts either an itch.io URL such as https://user.itch.io/game or a target like user/game.\n" +
                        "Leave the API key blank to keep the currently saved key. Configure Butler in File > Settings > Butler."
        );
        hint.setEditable(false);
        hint.setLineWrap(true);
        hint.setWrapStyleWord(true);
        hint.setOpaque(false);
        hint.setFocusable(false);
        hint.setBorder(null);
        form.add(hint, gbc);

        return form;
    }

    private JPanel createMultiplayerPanel() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = formConstraints();

        addField(form, gbc, "Server IP", txtServerIp);
        addField(form, gbc, "Server Port", spnServerPort);
        addField(form, gbc, "Deploy OS", cmbDeployOs);
        addField(form, gbc, "Server Password", txtServerPassword);

        gbc.gridx = 1;
        gbc.gridy++;
        JButton btnBuildServer = new JButton("Build Multiplayer Server");
        btnBuildServer.addActionListener(e -> buildProjectServer());
        form.add(btnBuildServer, gbc);

        return form;
    }

    private GridBagConstraints formConstraints() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        return gbc;
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
        cmbProjector.setSelectedItem(project.getProjectorLabel());
        txtGamePage.setText(StringUtils.defaultString(project.itchGamePage));
        txtWindowsChannel.setText(StringUtils.defaultIfBlank(project.itchWindowsChannel, "windows"));
        txtLinuxChannel.setText(StringUtils.defaultIfBlank(project.itchLinuxChannel, "linux"));
        txtMacChannel.setText(StringUtils.defaultIfBlank(project.itchMacChannel, "macos"));
        txtServerIp.setText(StringUtils.defaultIfBlank(project.multiplayerServerIp, "127.0.0.1"));
        spnServerPort.setValue(project.multiplayerServerPort <= 0 ? SceneMaxProject.DEFAULT_MULTIPLAYER_PORT : project.multiplayerServerPort);
        cmbDeployOs.setSelectedItem(StringUtils.defaultIfBlank(project.multiplayerDeployOs, "Windows"));
        txtServerPassword.setText(StringUtils.defaultString(project.multiplayerPassword));

        String savedApiKey = Util.getProjectItchApiKey(project);
        if (savedApiKey.length() > 0) {
            lblApiKeyStatus.setText("A project-scoped API key is already saved locally.");
        } else {
            lblApiKeyStatus.setText("No project-scoped API key is saved. SceneMax will rely on butler login unless you paste one here.");
        }
    }

    private void clearSavedApiKey() {
        clearSavedApiKey = true;
        txtApiKey.setText("");
        lblApiKeyStatus.setText("The saved API key will be removed when you click Save.");
    }

    private void buildProjectServer() {
        saveProjectFields();
        try {
            File server = new MultiplayerServerBuilder().build(project);
            Util.saveProjectSettings(project);
            int choice = JOptionPane.showOptionDialog(this,
                    "Multiplayer server built:\n" + server.getAbsolutePath(),
                    "Multiplayer Server",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.INFORMATION_MESSAGE,
                    null,
                    new Object[]{"Open Folder", "Close"},
                    "Open Folder");
            if (choice == 0) {
                openServerFolder(server);
            }
        } catch (Exception ex) {
            Util.showScrollableMessageDialog(this, ex.getMessage(), "Multiplayer Server Build Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openServerFolder(File server) {
        File folder = server == null ? null : server.getParentFile();
        if (folder == null || !folder.isDirectory()) {
            JOptionPane.showMessageDialog(this, "The multiplayer server folder could not be found.",
                    "Multiplayer Server", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            Desktop.getDesktop().open(folder);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not open folder:\n" + folder.getAbsolutePath() + "\n\n" + ex.getMessage(),
                    "Multiplayer Server",
                    JOptionPane.ERROR_MESSAGE);
        }
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

        saveProjectFields();
        project.projectorType = SceneMaxProject.projectorTypeFromLabel(String.valueOf(cmbProjector.getSelectedItem()));
        project.itchGamePage = gamePage;
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

    private void saveProjectFields() {
        project.multiplayerServerIp = safeText(txtServerIp);
        project.multiplayerServerPort = ((Number) spnServerPort.getValue()).intValue();
        project.multiplayerDeployOs = String.valueOf(cmbDeployOs.getSelectedItem());
        project.multiplayerPassword = new String(txtServerPassword.getPassword()).trim();
    }

    private String safeText(JTextField textField) {
        return textField.getText() == null ? "" : textField.getText().trim();
    }
}
