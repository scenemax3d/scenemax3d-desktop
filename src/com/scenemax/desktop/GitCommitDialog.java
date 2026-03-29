package com.scenemax.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog for staging files and creating commits.
 * Shows file status, allows staging/unstaging, and committing with a message.
 */
public class GitCommitDialog extends JDialog {

    private final String projectPath;
    private JTable fileTable;
    private FileStatusTableModel tableModel;
    private JTextArea txtCommitMessage;
    private JLabel lblBranch;
    private JLabel lblStatusSummary;
    private JButton btnCommit;
    private JButton btnStageAll;
    private JButton btnUnstageAll;

    public GitCommitDialog(Frame owner, String projectPath) {
        super(owner, "Git - Stage & Commit", true);
        this.projectPath = projectPath;
        buildUI();
        refreshStatus();
        pack();
        setMinimumSize(new Dimension(750, 550));
        setLocationRelativeTo(owner);
    }

    private void buildUI() {
        JPanel contentPane = new JPanel(new BorderLayout(8, 8));
        contentPane.setBorder(new EmptyBorder(12, 12, 12, 12));

        // ─── Top: Branch & Status ───
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("Branch:"));
        lblBranch = new JLabel();
        lblBranch.setFont(lblBranch.getFont().deriveFont(Font.BOLD));
        topPanel.add(lblBranch);
        topPanel.add(Box.createHorizontalStrut(20));
        lblStatusSummary = new JLabel();
        topPanel.add(lblStatusSummary);
        contentPane.add(topPanel, BorderLayout.NORTH);

        // ─── Center: Split - Files on top, commit message on bottom ───
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.6);

        // File table
        JPanel filePanel = new JPanel(new BorderLayout(4, 4));
        tableModel = new FileStatusTableModel();
        fileTable = new JTable(tableModel);
        fileTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        fileTable.setRowHeight(22);
        fileTable.getColumnModel().getColumn(0).setPreferredWidth(30);   // checkbox
        fileTable.getColumnModel().getColumn(1).setPreferredWidth(80);   // status
        fileTable.getColumnModel().getColumn(2).setPreferredWidth(400);  // file path

        // Color code statuses
        fileTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                if (!isSelected && row < tableModel.files.size()) {
                    FileStatusRow f = tableModel.files.get(row);
                    if (f.status.status.equals("??")) {
                        c.setForeground(new Color(130, 130, 130));
                    } else if (f.status.status.startsWith("M") || f.status.status.startsWith("A")) {
                        c.setForeground(new Color(100, 200, 100));
                    } else if (f.status.status.startsWith("D")) {
                        c.setForeground(new Color(255, 120, 120));
                    } else {
                        c.setForeground(UIManager.getColor("Table.foreground"));
                    }
                }
                return c;
            }
        });

        // Double-click to toggle stage
        fileTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = fileTable.rowAtPoint(e.getPoint());
                    if (row >= 0 && row < tableModel.files.size()) {
                        toggleStage(row);
                    }
                }
            }
        });

        JScrollPane fileScrollPane = new JScrollPane(fileTable);
        filePanel.add(fileScrollPane, BorderLayout.CENTER);

        // File action buttons
        JPanel fileButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton btnStageSelected = new JButton("Stage Selected");
        btnStageSelected.addActionListener(e -> stageSelected());
        fileButtonPanel.add(btnStageSelected);

        JButton btnUnstageSelected = new JButton("Unstage Selected");
        btnUnstageSelected.addActionListener(e -> unstageSelected());
        fileButtonPanel.add(btnUnstageSelected);

        btnStageAll = new JButton("Stage All");
        btnStageAll.addActionListener(e -> stageAll());
        fileButtonPanel.add(btnStageAll);

        btnUnstageAll = new JButton("Unstage All");
        btnUnstageAll.addActionListener(e -> unstageAll());
        fileButtonPanel.add(btnUnstageAll);

        JButton btnRefresh = new JButton("Refresh");
        btnRefresh.addActionListener(e -> refreshStatus());
        fileButtonPanel.add(btnRefresh);

        JButton btnDiscardSelected = new JButton("Discard Changes");
        btnDiscardSelected.setForeground(new Color(255, 120, 120));
        btnDiscardSelected.addActionListener(e -> discardSelected());
        fileButtonPanel.add(btnDiscardSelected);

        filePanel.add(fileButtonPanel, BorderLayout.SOUTH);
        splitPane.setTopComponent(filePanel);

        // Commit message area
        JPanel commitPanel = new JPanel(new BorderLayout(4, 4));
        commitPanel.setBorder(new EmptyBorder(4, 0, 0, 0));
        commitPanel.add(new JLabel("Commit Message:"), BorderLayout.NORTH);

        txtCommitMessage = new JTextArea(4, 50);
        txtCommitMessage.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        txtCommitMessage.setLineWrap(true);
        txtCommitMessage.setWrapStyleWord(true);
        JScrollPane msgScrollPane = new JScrollPane(txtCommitMessage);
        commitPanel.add(msgScrollPane, BorderLayout.CENTER);

        // Commit buttons
        JPanel commitButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        btnCommit = new JButton("Commit");
        btnCommit.setFont(btnCommit.getFont().deriveFont(Font.BOLD));
        btnCommit.addActionListener(e -> doCommit());
        commitButtonPanel.add(btnCommit);

        JButton btnCommitAndPush = new JButton("Commit & Push");
        btnCommitAndPush.addActionListener(e -> doCommitAndPush());
        commitButtonPanel.add(btnCommitAndPush);

        JButton btnClose = new JButton("Close");
        btnClose.addActionListener(e -> dispose());
        commitButtonPanel.add(btnClose);

        commitPanel.add(commitButtonPanel, BorderLayout.SOUTH);
        splitPane.setBottomComponent(commitPanel);

        contentPane.add(splitPane, BorderLayout.CENTER);

        setContentPane(contentPane);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        contentPane.registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    private void refreshStatus() {
        lblBranch.setText(GitManager.getCurrentBranch(projectPath));

        List<GitManager.GitFileStatus> statuses = GitManager.getStatus(projectPath);
        List<FileStatusRow> rows = new ArrayList<>();
        int staged = 0, modified = 0, untracked = 0;

        for (GitManager.GitFileStatus s : statuses) {
            rows.add(new FileStatusRow(s, s.isStaged()));
            if (s.isStaged()) staged++;
            else if (s.status.equals("??")) untracked++;
            else modified++;
        }

        tableModel.setFiles(rows);
        lblStatusSummary.setText(String.format("%d staged, %d modified, %d untracked", staged, modified, untracked));
        btnCommit.setEnabled(staged > 0 || rows.stream().anyMatch(r -> r.staged));
    }

    private void toggleStage(int row) {
        FileStatusRow f = tableModel.files.get(row);
        if (f.staged) {
            GitManager.unstageFile(projectPath, f.status.filePath);
        } else {
            GitManager.addFile(projectPath, f.status.filePath);
        }
        refreshStatus();
    }

    private void stageSelected() {
        int[] rows = fileTable.getSelectedRows();
        for (int row : rows) {
            if (row < tableModel.files.size()) {
                GitManager.addFile(projectPath, tableModel.files.get(row).status.filePath);
            }
        }
        refreshStatus();
    }

    private void unstageSelected() {
        int[] rows = fileTable.getSelectedRows();
        for (int row : rows) {
            if (row < tableModel.files.size()) {
                GitManager.unstageFile(projectPath, tableModel.files.get(row).status.filePath);
            }
        }
        refreshStatus();
    }

    private void stageAll() {
        GitManager.addAll(projectPath);
        refreshStatus();
    }

    private void unstageAll() {
        GitManager.unstageAll(projectPath);
        refreshStatus();
    }

    private void discardSelected() {
        int[] rows = fileTable.getSelectedRows();
        if (rows.length == 0) {
            JOptionPane.showMessageDialog(this, "Select files to discard.", "Discard", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Discard changes to " + rows.length + " file(s)?\nThis cannot be undone!",
                "Discard Changes", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        for (int row : rows) {
            if (row < tableModel.files.size()) {
                String filePath = tableModel.files.get(row).status.filePath;
                String status = tableModel.files.get(row).status.status;
                if (status.equals("??")) {
                    // Delete untracked file
                    new java.io.File(projectPath, filePath).delete();
                } else {
                    GitManager.discardFileChanges(projectPath, filePath);
                }
            }
        }
        refreshStatus();
    }

    private void doCommit() {
        String message = txtCommitMessage.getText().trim();
        if (message.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a commit message.",
                    "Commit", JOptionPane.WARNING_MESSAGE);
            txtCommitMessage.requestFocus();
            return;
        }

        // Check if anything is staged
        boolean hasStaged = tableModel.files.stream().anyMatch(f -> f.staged);
        if (!hasStaged) {
            JOptionPane.showMessageDialog(this, "No files are staged for commit.\nUse 'Stage All' or stage individual files first.",
                    "Nothing to Commit", JOptionPane.WARNING_MESSAGE);
            return;
        }

        GitManager.GitResult result = GitManager.commit(projectPath, message);
        if (result.isSuccess()) {
            txtCommitMessage.setText("");
            refreshStatus();
            JOptionPane.showMessageDialog(this, "Commit successful!\n" + result.output,
                    "Commit", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, "Commit failed:\n" + result.getFullOutput(),
                    "Commit Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doCommitAndPush() {
        doCommit();
        // If commit was successful (message is now empty)
        if (txtCommitMessage.getText().trim().isEmpty()) {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

            String branch = GitManager.getCurrentBranch(projectPath);
            GitManager.GitResult result;

            if (GitManager.hasRemote(projectPath)) {
                // Check if upstream is set
                GitManager.GitResult trackingResult = GitManager.runGitCommand(projectPath,
                        "git", "rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}");
                if (trackingResult.isSuccess()) {
                    result = GitManager.push(projectPath);
                } else {
                    result = GitManager.pushSetUpstream(projectPath, "origin", branch);
                }
            } else {
                setCursor(Cursor.getDefaultCursor());
                JOptionPane.showMessageDialog(this,
                        "No remote repository configured.\nUse Git > Configuration to add a remote.",
                        "Push", JOptionPane.WARNING_MESSAGE);
                return;
            }

            setCursor(Cursor.getDefaultCursor());

            if (result.isSuccess()) {
                JOptionPane.showMessageDialog(this, "Push successful!\n" + result.getFullOutput(),
                        "Push", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Push failed:\n" + result.getFullOutput(),
                        "Push Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ─── Data model ───

    private static class FileStatusRow {
        final GitManager.GitFileStatus status;
        boolean staged;

        FileStatusRow(GitManager.GitFileStatus status, boolean staged) {
            this.status = status;
            this.staged = staged;
        }
    }

    private static class FileStatusTableModel extends AbstractTableModel {
        private final String[] COLUMNS = {"Staged", "Status", "File"};
        List<FileStatusRow> files = new ArrayList<>();

        void setFiles(List<FileStatusRow> files) {
            this.files = files;
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return files.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            FileStatusRow f = files.get(row);
            switch (col) {
                case 0: return f.staged ? "\u2713" : "";
                case 1: return f.status.getStatusLabel();
                case 2: return f.status.filePath;
                default: return "";
            }
        }
    }
}
