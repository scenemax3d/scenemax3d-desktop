package com.scenemax.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.File;

/**
 * Dialog for cloning a remote Git repository into a new project.
 */
public class GitCloneDialog extends JDialog {

    private JTextField txtRepoUrl;
    private JTextField txtTargetFolder;
    private JTextArea txtOutput;
    private JButton btnClone;
    private JButton btnBrowse;
    private JButton btnClose;
    private JProgressBar progressBar;
    private boolean cloneSuccess = false;

    public GitCloneDialog(Frame owner) {
        super(owner, "Git - Clone Repository", true);
        buildUI();
        pack();
        setMinimumSize(new Dimension(600, 420));
        setLocationRelativeTo(owner);
    }

    private void buildUI() {
        JPanel contentPane = new JPanel(new BorderLayout(8, 8));
        contentPane.setBorder(new EmptyBorder(12, 12, 12, 12));

        // Input fields
        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        inputPanel.add(new JLabel("Repository URL:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1; gbc.gridwidth = 2;
        txtRepoUrl = new JTextField(35);
        inputPanel.add(txtRepoUrl, gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        inputPanel.add(new JLabel("Clone Into:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1;
        txtTargetFolder = new JTextField(25);
        // Default to projects folder
        SceneMaxProject active = Util.getActiveProject();
        if (active != null) {
            File projectsDir = new File(active.path).getParentFile();
            txtTargetFolder.setText(projectsDir.getAbsolutePath() + File.separator + "cloned_project");
        } else {
            txtTargetFolder.setText(new File("projects/cloned_project").getAbsolutePath());
        }
        inputPanel.add(txtTargetFolder, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        btnBrowse = new JButton("Browse...");
        btnBrowse.addActionListener(e -> browseFolder());
        inputPanel.add(btnBrowse, gbc);

        // Help text
        JTextArea helpText = new JTextArea(
                "Enter the URL of the repository to clone.\n" +
                "Examples:\n" +
                "  https://github.com/user/repo.git\n" +
                "  git@github.com:user/repo.git"
        );
        helpText.setEditable(false);
        helpText.setOpaque(false);
        helpText.setFont(helpText.getFont().deriveFont(Font.PLAIN, 11f));
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 3;
        inputPanel.add(helpText, gbc);

        contentPane.add(inputPanel, BorderLayout.NORTH);

        // Output area
        txtOutput = new JTextArea(10, 50);
        txtOutput.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        txtOutput.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(txtOutput);
        contentPane.add(scrollPane, BorderLayout.CENTER);

        // Bottom: progress bar + buttons
        JPanel bottomPanel = new JPanel(new BorderLayout(4, 4));

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(false);
        progressBar.setVisible(false);
        bottomPanel.add(progressBar, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnClone = new JButton("Clone");
        btnClone.setFont(btnClone.getFont().deriveFont(Font.BOLD));
        btnClone.addActionListener(e -> doClone());
        buttonPanel.add(btnClone);

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

    private void browseFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select Clone Target Folder");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            txtTargetFolder.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void doClone() {
        String url = txtRepoUrl.getText().trim();
        String target = txtTargetFolder.getText().trim();

        if (url.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a repository URL.",
                    "Clone", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (target.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please specify a target folder.",
                    "Clone", JOptionPane.WARNING_MESSAGE);
            return;
        }

        File targetDir = new File(target);
        if (targetDir.exists() && targetDir.list() != null && targetDir.list().length > 0) {
            JOptionPane.showMessageDialog(this,
                    "Target folder is not empty. Please choose an empty folder.",
                    "Clone", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Disable UI during clone
        btnClone.setEnabled(false);
        btnBrowse.setEnabled(false);
        txtRepoUrl.setEnabled(false);
        txtTargetFolder.setEnabled(false);
        progressBar.setIndeterminate(true);
        progressBar.setVisible(true);
        txtOutput.setText("Cloning repository...\n");

        // Run clone in background
        SwingWorker<GitManager.GitResult, String> worker = new SwingWorker<GitManager.GitResult, String>() {
            @Override
            protected GitManager.GitResult doInBackground() {
                return GitManager.runGitCommandWithProgress(null, line -> publish(line),
                        "git", "clone", "--progress", url, target);
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String line : chunks) {
                    txtOutput.append(line + "\n");
                }
                // Auto-scroll
                txtOutput.setCaretPosition(txtOutput.getDocument().getLength());
            }

            @Override
            protected void done() {
                progressBar.setIndeterminate(false);
                progressBar.setVisible(false);
                btnClone.setEnabled(true);
                btnBrowse.setEnabled(true);
                txtRepoUrl.setEnabled(true);
                txtTargetFolder.setEnabled(true);

                try {
                    GitManager.GitResult result = get();
                    if (result.isSuccess()) {
                        txtOutput.append("\nClone successful!\n");
                        cloneSuccess = true;
                        JOptionPane.showMessageDialog(GitCloneDialog.this,
                                "Repository cloned successfully to:\n" + target,
                                "Clone Complete", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        txtOutput.append("\nClone failed:\n" + result.getFullOutput() + "\n");
                        JOptionPane.showMessageDialog(GitCloneDialog.this,
                                "Clone failed. See output for details.",
                                "Clone Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    txtOutput.append("\nError: " + ex.getMessage() + "\n");
                }
            }
        };
        worker.execute();
    }

    public boolean isCloneSuccess() {
        return cloneSuccess;
    }

    public String getTargetFolder() {
        return txtTargetFolder.getText().trim();
    }
}
