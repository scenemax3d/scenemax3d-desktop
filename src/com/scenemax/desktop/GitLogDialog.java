package com.scenemax.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog for viewing Git commit history (log).
 */
public class GitLogDialog extends JDialog {

    private final String projectPath;
    private JTable logTable;
    private LogTableModel tableModel;
    private JTextArea txtDiffArea;
    private JSpinner spnMaxCount;

    public GitLogDialog(Frame owner, String projectPath) {
        super(owner, "Git - Commit History", true);
        this.projectPath = projectPath;
        buildUI();
        refreshLog();
        pack();
        setMinimumSize(new Dimension(850, 550));
        setLocationRelativeTo(owner);
    }

    private void buildUI() {
        JPanel contentPane = new JPanel(new BorderLayout(8, 8));
        contentPane.setBorder(new EmptyBorder(12, 12, 12, 12));

        // Top: controls
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("Show last"));
        spnMaxCount = new JSpinner(new SpinnerNumberModel(50, 10, 500, 10));
        topPanel.add(spnMaxCount);
        topPanel.add(new JLabel("commits"));
        JButton btnRefresh = new JButton("Refresh");
        btnRefresh.addActionListener(e -> refreshLog());
        topPanel.add(btnRefresh);

        String currentBranch = GitManager.getCurrentBranch(projectPath);
        topPanel.add(Box.createHorizontalStrut(20));
        topPanel.add(new JLabel("Branch:"));
        JLabel lblBranch = new JLabel(currentBranch);
        lblBranch.setFont(lblBranch.getFont().deriveFont(Font.BOLD));
        topPanel.add(lblBranch);

        contentPane.add(topPanel, BorderLayout.NORTH);

        // Center: split between log table and diff viewer
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.5);

        // Log table
        tableModel = new LogTableModel();
        logTable = new JTable(tableModel);
        logTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        logTable.setRowHeight(22);
        logTable.getColumnModel().getColumn(0).setPreferredWidth(60);   // hash
        logTable.getColumnModel().getColumn(1).setPreferredWidth(80);   // date
        logTable.getColumnModel().getColumn(2).setPreferredWidth(120);  // author
        logTable.getColumnModel().getColumn(3).setPreferredWidth(400);  // message

        logTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSelectedCommitDiff();
            }
        });

        JScrollPane tableScrollPane = new JScrollPane(logTable);
        splitPane.setTopComponent(tableScrollPane);

        // Diff view
        txtDiffArea = new JTextArea();
        txtDiffArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        txtDiffArea.setEditable(false);
        txtDiffArea.setLineWrap(false);
        JScrollPane diffScrollPane = new JScrollPane(txtDiffArea);
        splitPane.setBottomComponent(diffScrollPane);

        contentPane.add(splitPane, BorderLayout.CENTER);

        // Bottom: close button
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnClose = new JButton("Close");
        btnClose.addActionListener(e -> dispose());
        buttonPanel.add(btnClose);
        contentPane.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(contentPane);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        contentPane.registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    private void refreshLog() {
        int maxCount = (int) spnMaxCount.getValue();
        List<GitManager.LogEntry> entries = GitManager.getLog(projectPath, maxCount);
        tableModel.setEntries(entries);
        txtDiffArea.setText("");
    }

    private void showSelectedCommitDiff() {
        int row = logTable.getSelectedRow();
        if (row < 0 || row >= tableModel.entries.size()) return;

        GitManager.LogEntry entry = tableModel.entries.get(row);
        GitManager.GitResult result = GitManager.runGitCommand(projectPath,
                "git", "show", "--stat", "--patch", entry.hash);

        if (result.isSuccess()) {
            txtDiffArea.setText(result.output);
            txtDiffArea.setCaretPosition(0);
        } else {
            txtDiffArea.setText("Failed to load diff: " + result.error);
        }
    }

    // ─── Table Model ───

    private static class LogTableModel extends AbstractTableModel {
        private final String[] COLUMNS = {"Hash", "Date", "Author", "Message"};
        List<GitManager.LogEntry> entries = new ArrayList<>();

        void setEntries(List<GitManager.LogEntry> entries) {
            this.entries = entries;
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return entries.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            GitManager.LogEntry e = entries.get(row);
            switch (col) {
                case 0: return e.shortHash;
                case 1: return e.date;
                case 2: return e.author;
                case 3: return e.message;
                default: return "";
            }
        }
    }
}
