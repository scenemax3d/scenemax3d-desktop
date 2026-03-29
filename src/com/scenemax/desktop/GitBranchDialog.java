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
 * Dialog for managing Git branches: create, switch, merge, rename, delete.
 */
public class GitBranchDialog extends JDialog {

    private final String projectPath;
    private JTable branchTable;
    private BranchTableModel tableModel;
    private JLabel lblCurrentBranch;
    private JCheckBox chkShowRemote;

    public GitBranchDialog(Frame owner, String projectPath) {
        super(owner, "Git Branches", true);
        this.projectPath = projectPath;
        buildUI();
        refreshBranches();
        pack();
        setMinimumSize(new Dimension(600, 400));
        setLocationRelativeTo(owner);
    }

    private void buildUI() {
        JPanel contentPane = new JPanel(new BorderLayout(8, 8));
        contentPane.setBorder(new EmptyBorder(12, 12, 12, 12));

        // Top: current branch info
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("Current Branch:"));
        lblCurrentBranch = new JLabel();
        lblCurrentBranch.setFont(lblCurrentBranch.getFont().deriveFont(Font.BOLD));
        topPanel.add(lblCurrentBranch);

        topPanel.add(Box.createHorizontalStrut(20));
        chkShowRemote = new JCheckBox("Show Remote Branches");
        chkShowRemote.addActionListener(e -> refreshBranches());
        topPanel.add(chkShowRemote);

        contentPane.add(topPanel, BorderLayout.NORTH);

        // Center: branch table
        tableModel = new BranchTableModel();
        branchTable = new JTable(tableModel);
        branchTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        branchTable.setRowHeight(24);
        branchTable.getColumnModel().getColumn(0).setPreferredWidth(30);
        branchTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        branchTable.getColumnModel().getColumn(2).setPreferredWidth(60);
        branchTable.getColumnModel().getColumn(3).setPreferredWidth(200);

        // Highlight current branch
        branchTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected && row < tableModel.branches.size() && tableModel.branches.get(row).isCurrent) {
                    c.setForeground(new Color(100, 255, 100));
                } else if (!isSelected) {
                    c.setForeground(UIManager.getColor("Table.foreground"));
                }
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(branchTable);
        contentPane.add(scrollPane, BorderLayout.CENTER);

        // Bottom: action buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));

        JButton btnNew = new JButton("New Branch...");
        btnNew.addActionListener(e -> createNewBranch());
        buttonPanel.add(btnNew);

        JButton btnCheckout = new JButton("Switch To");
        btnCheckout.addActionListener(e -> checkoutSelected());
        buttonPanel.add(btnCheckout);

        JButton btnMerge = new JButton("Merge Into Current");
        btnMerge.addActionListener(e -> mergeSelected());
        buttonPanel.add(btnMerge);

        JButton btnRename = new JButton("Rename...");
        btnRename.addActionListener(e -> renameSelected());
        buttonPanel.add(btnRename);

        JButton btnDelete = new JButton("Delete");
        btnDelete.addActionListener(e -> deleteSelected());
        buttonPanel.add(btnDelete);

        JButton btnRefresh = new JButton("Refresh");
        btnRefresh.addActionListener(e -> refreshBranches());
        buttonPanel.add(btnRefresh);

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

    private void refreshBranches() {
        String current = GitManager.getCurrentBranch(projectPath);
        lblCurrentBranch.setText(current);

        List<GitManager.BranchInfo> branches = GitManager.getBranches(projectPath, chkShowRemote.isSelected());
        tableModel.setBranches(branches);
    }

    private void createNewBranch() {
        String name = JOptionPane.showInputDialog(this, "Enter new branch name:", "New Branch", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;
        name = name.trim().replaceAll("\\s+", "-");

        int choice = JOptionPane.showConfirmDialog(this,
                "Switch to the new branch '" + name + "' after creation?",
                "New Branch", JOptionPane.YES_NO_CANCEL_OPTION);

        if (choice == JOptionPane.CANCEL_OPTION) return;

        GitManager.GitResult result;
        if (choice == JOptionPane.YES_OPTION) {
            result = GitManager.createAndCheckoutBranch(projectPath, name);
        } else {
            result = GitManager.createBranch(projectPath, name);
        }

        if (result.isSuccess()) {
            refreshBranches();
        } else {
            JOptionPane.showMessageDialog(this, "Failed to create branch:\n" + result.error,
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private GitManager.BranchInfo getSelectedBranch() {
        int row = branchTable.getSelectedRow();
        if (row < 0 || row >= tableModel.branches.size()) {
            JOptionPane.showMessageDialog(this, "Please select a branch first.",
                    "No Selection", JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        return tableModel.branches.get(row);
    }

    private void checkoutSelected() {
        GitManager.BranchInfo branch = getSelectedBranch();
        if (branch == null) return;
        if (branch.isCurrent) {
            JOptionPane.showMessageDialog(this, "Already on this branch.", "Switch Branch", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String branchName = branch.name;
        // For remote branches, create a local tracking branch
        if (branch.isRemote) {
            branchName = branchName.replaceFirst("^origin/", "");
        }

        GitManager.GitResult result = GitManager.checkoutBranch(projectPath, branchName);
        if (result.isSuccess()) {
            refreshBranches();
        } else {
            JOptionPane.showMessageDialog(this, "Failed to switch branch:\n" + result.getFullOutput(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void mergeSelected() {
        GitManager.BranchInfo branch = getSelectedBranch();
        if (branch == null) return;
        if (branch.isCurrent) {
            JOptionPane.showMessageDialog(this, "Cannot merge a branch into itself.",
                    "Merge", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String currentBranch = GitManager.getCurrentBranch(projectPath);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Merge '" + branch.name + "' into '" + currentBranch + "'?",
                "Confirm Merge", JOptionPane.YES_NO_OPTION);

        if (confirm != JOptionPane.YES_OPTION) return;

        GitManager.GitResult result = GitManager.mergeBranch(projectPath, branch.name);
        if (result.isSuccess()) {
            JOptionPane.showMessageDialog(this, "Merge successful.\n" + result.output,
                    "Merge", JOptionPane.INFORMATION_MESSAGE);
            refreshBranches();
        } else {
            JOptionPane.showMessageDialog(this, "Merge failed (conflicts may exist):\n" + result.getFullOutput(),
                    "Merge Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void renameSelected() {
        GitManager.BranchInfo branch = getSelectedBranch();
        if (branch == null) return;
        if (branch.isRemote) {
            JOptionPane.showMessageDialog(this, "Cannot rename a remote branch directly.",
                    "Rename", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String newName = JOptionPane.showInputDialog(this, "Enter new name for '" + branch.name + "':",
                "Rename Branch", JOptionPane.PLAIN_MESSAGE);
        if (newName == null || newName.trim().isEmpty()) return;

        GitManager.GitResult result = GitManager.renameBranch(projectPath, branch.name, newName.trim());
        if (result.isSuccess()) {
            refreshBranches();
        } else {
            JOptionPane.showMessageDialog(this, "Failed to rename:\n" + result.error,
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelected() {
        GitManager.BranchInfo branch = getSelectedBranch();
        if (branch == null) return;
        if (branch.isCurrent) {
            JOptionPane.showMessageDialog(this, "Cannot delete the current branch. Switch to another branch first.",
                    "Delete Branch", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete branch '" + branch.name + "'?\nThis cannot be undone for unmerged changes.",
                "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) return;

        GitManager.GitResult result = GitManager.deleteBranch(projectPath, branch.name);
        if (!result.isSuccess()) {
            // Branch not fully merged - ask to force delete
            int forceConfirm = JOptionPane.showConfirmDialog(this,
                    "Branch is not fully merged. Force delete?\n\nThis will permanently lose unmerged commits.",
                    "Force Delete?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (forceConfirm == JOptionPane.YES_OPTION) {
                result = GitManager.forceDeleteBranch(projectPath, branch.name);
            }
        }

        if (result.isSuccess()) {
            refreshBranches();
        } else {
            JOptionPane.showMessageDialog(this, "Failed to delete:\n" + result.error,
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ─── Table Model ───

    private static class BranchTableModel extends AbstractTableModel {
        private final String[] COLUMNS = {"", "Branch Name", "Type", "Tracking"};
        List<GitManager.BranchInfo> branches = new ArrayList<>();

        void setBranches(List<GitManager.BranchInfo> branches) {
            this.branches = branches;
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return branches.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            GitManager.BranchInfo b = branches.get(row);
            switch (col) {
                case 0: return b.isCurrent ? "*" : "";
                case 1: return b.name;
                case 2: return b.isRemote ? "Remote" : "Local";
                case 3: return b.tracking;
                default: return "";
            }
        }
    }
}
