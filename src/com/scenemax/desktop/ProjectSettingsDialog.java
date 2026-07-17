package com.scenemax.desktop;

import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.awt.*;

public class ProjectSettingsDialog extends JDialog {

    private final SceneMaxProject project;
    private final JTextField txtGamePage = new JTextField();
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
        JTextArea hint = new JTextArea(
                "Game page accepts either an itch.io URL such as https://user.itch.io/game or a target like user/game.\n" +
                "Leave the API key blank to keep the currently saved key. If no API key is saved, SceneMax will use your local butler login session if one exists.\n" +
                "Configure the Butler executable and login once in File > Settings > Butler."
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
        JButton btnClearApiKey = new JButton("Clear Saved API Key");
        JButton btnCancel = new JButton("Cancel");
        JButton btnSave = new JButton("Save");
        buttons.add(btnClearApiKey);
        buttons.add(btnCancel);
        buttons.add(btnSave);
        root.add(buttons, BorderLayout.SOUTH);

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
