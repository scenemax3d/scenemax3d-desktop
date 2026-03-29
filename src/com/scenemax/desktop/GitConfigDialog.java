package com.scenemax.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;

/**
 * Dialog for configuring Git global settings (user.name, user.email, default branch, etc.).
 * Also helps detect whether Git is installed and guides the user through installation.
 */
public class GitConfigDialog extends JDialog {

    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;

    // Identity
    private JTextField txtUserName;
    private JTextField txtUserEmail;

    // Defaults
    private JTextField txtDefaultBranch;

    // Credential helper
    private JComboBox<String> cmbCredentialHelper;

    // Info
    private JLabel lblGitVersion;
    private JLabel lblGitStatus;

    // Remote for current project
    private JTextField txtRemoteUrl;
    private JLabel lblCurrentProject;

    private boolean applied = false;

    public GitConfigDialog(Frame owner) {
        super(owner, "Git Configuration", true);
        buildUI();
        loadCurrentConfig();
        pack();
        setMinimumSize(new Dimension(550, 480));
        setLocationRelativeTo(owner);
    }

    private void buildUI() {
        contentPane = new JPanel(new BorderLayout(8, 8));
        contentPane.setBorder(new EmptyBorder(12, 12, 12, 12));

        JTabbedPane tabs = new JTabbedPane();

        // ─── Identity Tab ───
        JPanel identityPanel = new JPanel(new GridBagLayout());
        identityPanel.setBorder(new EmptyBorder(12, 12, 12, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // Git status
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 1; gbc.weightx = 0;
        identityPanel.add(new JLabel("Git Status:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        lblGitStatus = new JLabel();
        identityPanel.add(lblGitStatus, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        identityPanel.add(new JLabel("Git Version:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        lblGitVersion = new JLabel();
        identityPanel.add(lblGitVersion, gbc);

        // Separator
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        identityPanel.add(new JSeparator(), gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        identityPanel.add(new JLabel("User Name:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        txtUserName = new JTextField(25);
        identityPanel.add(txtUserName, gbc);

        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0;
        identityPanel.add(new JLabel("User Email:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        txtUserEmail = new JTextField(25);
        identityPanel.add(txtUserEmail, gbc);

        gbc.gridx = 0; gbc.gridy = 5; gbc.weightx = 0;
        identityPanel.add(new JLabel("Default Branch:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        txtDefaultBranch = new JTextField(25);
        identityPanel.add(txtDefaultBranch, gbc);

        gbc.gridx = 0; gbc.gridy = 6; gbc.weightx = 0;
        identityPanel.add(new JLabel("Credential Helper:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        cmbCredentialHelper = new JComboBox<>(new String[]{"(none)", "store", "cache", "manager", "manager-core", "wincred"});
        identityPanel.add(cmbCredentialHelper, gbc);

        // Spacer
        gbc.gridx = 0; gbc.gridy = 7; gbc.weighty = 1; gbc.gridwidth = 2;
        identityPanel.add(Box.createVerticalGlue(), gbc);
        gbc.weighty = 0; gbc.gridwidth = 1;

        tabs.addTab("Identity & Defaults", identityPanel);

        // ─── Remote Tab ───
        JPanel remotePanel = new JPanel(new GridBagLayout());
        remotePanel.setBorder(new EmptyBorder(12, 12, 12, 12));
        GridBagConstraints gbc2 = new GridBagConstraints();
        gbc2.insets = new Insets(6, 6, 6, 6);
        gbc2.fill = GridBagConstraints.HORIZONTAL;
        gbc2.anchor = GridBagConstraints.WEST;

        gbc2.gridx = 0; gbc2.gridy = 0; gbc2.weightx = 0;
        remotePanel.add(new JLabel("Current Project:"), gbc2);
        gbc2.gridx = 1; gbc2.weightx = 1;
        lblCurrentProject = new JLabel();
        remotePanel.add(lblCurrentProject, gbc2);

        gbc2.gridx = 0; gbc2.gridy = 1; gbc2.weightx = 0;
        remotePanel.add(new JLabel("Remote URL (origin):"), gbc2);
        gbc2.gridx = 1; gbc2.weightx = 1;
        txtRemoteUrl = new JTextField(30);
        remotePanel.add(txtRemoteUrl, gbc2);

        JPanel remoteButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnTestRemote = new JButton("Test Connection");
        btnTestRemote.addActionListener(e -> testRemoteConnection());
        remoteButtonPanel.add(btnTestRemote);

        gbc2.gridx = 0; gbc2.gridy = 2; gbc2.gridwidth = 2;
        remotePanel.add(remoteButtonPanel, gbc2);

        JTextArea remoteHelp = new JTextArea(
                "Enter the remote repository URL for the current project.\n\n" +
                "Supported formats:\n" +
                "  HTTPS: https://github.com/user/repo.git\n" +
                "  SSH:   git@github.com:user/repo.git\n\n" +
                "For HTTPS, use a Personal Access Token as your password.\n" +
                "For SSH, make sure your SSH key is configured."
        );
        remoteHelp.setEditable(false);
        remoteHelp.setOpaque(false);
        remoteHelp.setFont(remoteHelp.getFont().deriveFont(Font.PLAIN, 11f));
        remoteHelp.setLineWrap(true);
        remoteHelp.setWrapStyleWord(true);
        gbc2.gridx = 0; gbc2.gridy = 3; gbc2.gridwidth = 2;
        remotePanel.add(remoteHelp, gbc2);

        // Spacer
        gbc2.gridy = 4; gbc2.weighty = 1;
        remotePanel.add(Box.createVerticalGlue(), gbc2);

        tabs.addTab("Remote Repository", remotePanel);

        contentPane.add(tabs, BorderLayout.CENTER);

        // ─── Buttons ───
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonOK = new JButton("Apply");
        buttonCancel = new JButton("Cancel");

        buttonOK.addActionListener(e -> onOK());
        buttonCancel.addActionListener(e -> onCancel());

        buttonPanel.add(buttonOK);
        buttonPanel.add(buttonCancel);
        contentPane.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(contentPane);
        getRootPane().setDefaultButton(buttonOK);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        contentPane.registerKeyboardAction(e -> onCancel(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    private void loadCurrentConfig() {
        if (!GitManager.isGitInstalled()) {
            lblGitStatus.setText("Not Installed");
            lblGitStatus.setForeground(new Color(255, 100, 100));
            lblGitVersion.setText("N/A");
            txtUserName.setEnabled(false);
            txtUserEmail.setEnabled(false);
            txtDefaultBranch.setEnabled(false);
            cmbCredentialHelper.setEnabled(false);
            txtRemoteUrl.setEnabled(false);
            buttonOK.setEnabled(false);
            return;
        }

        lblGitStatus.setText("Installed");
        lblGitStatus.setForeground(new Color(100, 255, 100));

        String version = GitManager.getGitVersion();
        lblGitVersion.setText(version != null ? version : "Unknown");

        txtUserName.setText(GitManager.getUserName());
        txtUserEmail.setText(GitManager.getUserEmail());
        txtDefaultBranch.setText(GitManager.getDefaultBranch());

        // Load credential helper
        String credHelper = GitManager.getGlobalConfig("credential.helper");
        if (credHelper.isEmpty()) {
            cmbCredentialHelper.setSelectedIndex(0);
        } else {
            for (int i = 0; i < cmbCredentialHelper.getItemCount(); i++) {
                if (cmbCredentialHelper.getItemAt(i).equals(credHelper)) {
                    cmbCredentialHelper.setSelectedIndex(i);
                    break;
                }
            }
        }

        // Load project remote
        SceneMaxProject project = Util.getActiveProject();
        if (project != null) {
            lblCurrentProject.setText(project.name);
            String projectPath = new java.io.File(project.path).getAbsolutePath();
            if (GitManager.isGitRepository(projectPath)) {
                txtRemoteUrl.setText(GitManager.getRemoteUrl(projectPath));
            }
        } else {
            lblCurrentProject.setText("(none)");
            txtRemoteUrl.setEnabled(false);
        }
    }

    private void onOK() {
        String name = txtUserName.getText().trim();
        String email = txtUserEmail.getText().trim();
        String branch = txtDefaultBranch.getText().trim();

        if (name.isEmpty() || email.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "User Name and Email are required for Git commits.",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Apply global config
        GitManager.setUserName(name);
        GitManager.setUserEmail(email);
        if (!branch.isEmpty()) {
            GitManager.setDefaultBranch(branch);
        }

        // Credential helper
        String credHelper = (String) cmbCredentialHelper.getSelectedItem();
        if (credHelper != null && !credHelper.equals("(none)")) {
            GitManager.setGlobalConfig("credential.helper", credHelper);
        }

        // Apply remote URL
        SceneMaxProject project = Util.getActiveProject();
        if (project != null && txtRemoteUrl.isEnabled()) {
            String remoteUrl = txtRemoteUrl.getText().trim();
            String projectPath = new java.io.File(project.path).getAbsolutePath();

            if (GitManager.isGitRepository(projectPath)) {
                String currentRemote = GitManager.getRemoteUrl(projectPath);
                if (!remoteUrl.equals(currentRemote)) {
                    if (currentRemote.isEmpty() && !remoteUrl.isEmpty()) {
                        GitManager.addRemote(projectPath, "origin", remoteUrl);
                    } else if (!remoteUrl.isEmpty()) {
                        GitManager.setRemoteUrl(projectPath, "origin", remoteUrl);
                    } else if (remoteUrl.isEmpty() && !currentRemote.isEmpty()) {
                        GitManager.removeRemote(projectPath, "origin");
                    }
                }
            }
        }

        applied = true;
        dispose();
    }

    private void onCancel() {
        dispose();
    }

    private void testRemoteConnection() {
        String remoteUrl = txtRemoteUrl.getText().trim();
        if (remoteUrl.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a remote URL first.",
                    "Test Connection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        GitManager.GitResult result = GitManager.runGitCommand(null, "git", "ls-remote", "--heads", remoteUrl);
        setCursor(Cursor.getDefaultCursor());

        if (result.isSuccess()) {
            JOptionPane.showMessageDialog(this,
                    "Connection successful! Repository is accessible.",
                    "Test Connection", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this,
                    "Connection failed:\n" + result.error,
                    "Test Connection", JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean isApplied() {
        return applied;
    }
}
