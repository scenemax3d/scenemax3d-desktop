package com.scenemax.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;

/**
 * Dialog for push/pull/fetch operations with progress output.
 */
public class GitPushPullDialog extends JDialog {

    public enum Operation { PUSH, PULL, FETCH, PULL_REBASE }

    private final String projectPath;
    private final Operation operation;
    private JTextArea txtOutput;
    private JProgressBar progressBar;
    private JButton btnExecute;
    private JButton btnClose;
    private JLabel lblInfo;

    public GitPushPullDialog(Frame owner, String projectPath, Operation operation) {
        super(owner, getTitle(operation), true);
        this.projectPath = projectPath;
        this.operation = operation;
        buildUI();
        pack();
        setMinimumSize(new Dimension(600, 400));
        setLocationRelativeTo(owner);
    }

    private static String getTitle(Operation op) {
        switch (op) {
            case PUSH: return "Git - Push";
            case PULL: return "Git - Pull";
            case FETCH: return "Git - Fetch";
            case PULL_REBASE: return "Git - Pull (Rebase)";
            default: return "Git";
        }
    }

    private void buildUI() {
        JPanel contentPane = new JPanel(new BorderLayout(8, 8));
        contentPane.setBorder(new EmptyBorder(12, 12, 12, 12));

        // Info panel
        JPanel infoPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        infoPanel.add(new JLabel("Branch:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        JLabel lblBranch = new JLabel(GitManager.getCurrentBranch(projectPath));
        lblBranch.setFont(lblBranch.getFont().deriveFont(Font.BOLD));
        infoPanel.add(lblBranch, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        infoPanel.add(new JLabel("Remote:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        String remoteUrl = GitManager.getRemoteUrl(projectPath);
        infoPanel.add(new JLabel(remoteUrl.isEmpty() ? "(no remote configured)" : remoteUrl), gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        infoPanel.add(new JLabel("Operation:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        lblInfo = new JLabel(getOperationDescription());
        infoPanel.add(lblInfo, gbc);

        contentPane.add(infoPanel, BorderLayout.NORTH);

        // Output
        txtOutput = new JTextArea(12, 50);
        txtOutput.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        txtOutput.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(txtOutput);
        contentPane.add(scrollPane, BorderLayout.CENTER);

        // Bottom
        JPanel bottomPanel = new JPanel(new BorderLayout(4, 4));

        progressBar = new JProgressBar();
        progressBar.setVisible(false);
        bottomPanel.add(progressBar, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        btnExecute = new JButton(getOperationButtonLabel());
        btnExecute.setFont(btnExecute.getFont().deriveFont(Font.BOLD));
        btnExecute.addActionListener(e -> execute());

        // Disable if no remote
        if (remoteUrl.isEmpty() && operation != GitPushPullDialog.Operation.FETCH) {
            btnExecute.setEnabled(false);
            txtOutput.setText("No remote repository configured.\nUse Git > Configuration to add a remote URL.\n");
        }

        buttonPanel.add(btnExecute);

        btnClose = new JButton("Close");
        btnClose.addActionListener(e -> dispose());
        buttonPanel.add(btnClose);

        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
        contentPane.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(contentPane);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        contentPane.registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    private String getOperationDescription() {
        switch (operation) {
            case PUSH: return "Push local commits to remote repository";
            case PULL: return "Pull changes from remote and merge";
            case FETCH: return "Fetch remote changes (no merge)";
            case PULL_REBASE: return "Pull changes from remote and rebase";
            default: return "";
        }
    }

    private String getOperationButtonLabel() {
        switch (operation) {
            case PUSH: return "Push";
            case PULL: return "Pull";
            case FETCH: return "Fetch";
            case PULL_REBASE: return "Pull (Rebase)";
            default: return "Execute";
        }
    }

    private void execute() {
        btnExecute.setEnabled(false);
        progressBar.setIndeterminate(true);
        progressBar.setVisible(true);
        txtOutput.setText("Running " + getOperationButtonLabel().toLowerCase() + "...\n\n");

        SwingWorker<GitManager.GitResult, String> worker = new SwingWorker<GitManager.GitResult, String>() {
            @Override
            protected GitManager.GitResult doInBackground() {
                String branch = GitManager.getCurrentBranch(projectPath);
                switch (operation) {
                    case PUSH:
                        // Check if upstream is set
                        GitManager.GitResult trackingResult = GitManager.runGitCommand(projectPath,
                                "git", "rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}");
                        if (trackingResult.isSuccess()) {
                            return GitManager.runGitCommandWithProgress(projectPath, line -> publish(line),
                                    "git", "push", "--progress");
                        } else {
                            return GitManager.runGitCommandWithProgress(projectPath, line -> publish(line),
                                    "git", "push", "-u", "origin", branch, "--progress");
                        }
                    case PULL:
                        return GitManager.runGitCommandWithProgress(projectPath, line -> publish(line),
                                "git", "pull", "--progress");
                    case FETCH:
                        return GitManager.runGitCommandWithProgress(projectPath, line -> publish(line),
                                "git", "fetch", "--all", "--progress");
                    case PULL_REBASE:
                        return GitManager.runGitCommandWithProgress(projectPath, line -> publish(line),
                                "git", "pull", "--rebase", "--progress");
                    default:
                        return new GitManager.GitResult(-1, "", "Unknown operation");
                }
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String line : chunks) {
                    txtOutput.append(line + "\n");
                }
                txtOutput.setCaretPosition(txtOutput.getDocument().getLength());
            }

            @Override
            protected void done() {
                progressBar.setIndeterminate(false);
                progressBar.setVisible(false);
                btnExecute.setEnabled(true);

                try {
                    GitManager.GitResult result = get();
                    if (result.isSuccess()) {
                        txtOutput.append("\n--- Operation completed successfully ---\n");
                    } else {
                        txtOutput.append("\n--- Operation failed ---\n");
                        if (result.error != null && !result.error.isEmpty()) {
                            txtOutput.append(result.error + "\n");
                        }
                    }
                } catch (Exception ex) {
                    txtOutput.append("\nError: " + ex.getMessage() + "\n");
                }
            }
        };
        worker.execute();
    }
}
