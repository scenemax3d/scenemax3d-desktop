package com.scenemax.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ProjectExplorerDialog extends JDialog {

    private final ProjectExplorerPanel projectExplorerPanel;

    public ProjectExplorerDialog(MainApp owner) {
        super(owner, "Project Explorer", false);
        projectExplorerPanel = new ProjectExplorerPanel(owner);
        setContentPane(projectExplorerPanel);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(860, 520));
        setSize(1040, 620);
    }

    public void refreshProjects() {
        projectExplorerPanel.refreshProjects();
    }
}

class ProjectExplorerPanel extends JPanel {

    private final MainApp app;
    private final ProjectTableModel tableModel = new ProjectTableModel();
    private final JTable projectsTable = new JTable(tableModel);
    private final JLabel statusLabel = new JLabel();
    private final JButton openButton = new JButton("Open");
    private final JButton renameButton = new JButton("Rename");
    private final JButton deleteButton = new JButton("Delete");
    private final JButton openFolderButton = new JButton("Open Folder");

    ProjectExplorerPanel(MainApp app) {
        super(new BorderLayout(10, 10));
        this.app = app;
        setBorder(new EmptyBorder(10, 10, 10, 10));
        buildUi();
        refreshProjects();
    }

    void refreshProjects() {
        String selectedName = getSelectedProjectName();
        List<ProjectRow> rows = new ArrayList<>();
        SceneMaxProject activeProject = Util.getActiveProject();
        for (SceneMaxProject project : Util.getProjects_New()) {
            rows.add(new ProjectRow(project, activeProject != null && project.name.equals(activeProject.name)));
        }
        rows.sort(Comparator
                .comparing((ProjectRow row) -> !row.active)
                .thenComparing(row -> row.project.name.toLowerCase(Locale.ROOT)));
        tableModel.setRows(rows);
        restoreSelection(selectedName);
        statusLabel.setText(rows.size() + (rows.size() == 1 ? " project" : " projects"));
        updateButtons();
    }

    private void buildUi() {
        JLabel title = new JLabel("Project Explorer");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refreshProjects());

        JPanel top = new JPanel(new BorderLayout(10, 0));
        top.add(title, BorderLayout.WEST);
        top.add(refreshButton, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        projectsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        projectsTable.setAutoCreateRowSorter(true);
        projectsTable.setFillsViewportHeight(true);
        projectsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateButtons();
            }
        });
        projectsTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    openSelectedProject();
                }
            }
        });

        TableRowSorter<ProjectTableModel> sorter = new TableRowSorter<>(tableModel);
        projectsTable.setRowSorter(sorter);
        projectsTable.getColumnModel().getColumn(0).setPreferredWidth(220);
        projectsTable.getColumnModel().getColumn(1).setPreferredWidth(80);
        projectsTable.getColumnModel().getColumn(2).setPreferredWidth(160);
        projectsTable.getColumnModel().getColumn(3).setPreferredWidth(420);
        projectsTable.getColumnModel().getColumn(4).setPreferredWidth(150);
        add(new JScrollPane(projectsTable), BorderLayout.CENTER);

        openButton.addActionListener(e -> openSelectedProject());
        renameButton.addActionListener(e -> renameSelectedProject());
        deleteButton.addActionListener(e -> deleteSelectedProject());
        openFolderButton.addActionListener(e -> openSelectedProjectFolder());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(openFolderButton);
        buttons.add(openButton);
        buttons.add(renameButton);
        buttons.add(deleteButton);

        JPanel bottom = new JPanel(new BorderLayout(10, 0));
        statusLabel.setBorder(new EmptyBorder(4, 2, 0, 2));
        bottom.add(statusLabel, BorderLayout.WEST);
        bottom.add(buttons, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);
    }

    private void updateButtons() {
        ProjectRow row = getSelectedRow();
        boolean hasSelection = row != null;
        openButton.setEnabled(hasSelection && !row.active);
        renameButton.setEnabled(hasSelection);
        deleteButton.setEnabled(hasSelection && tableModel.getRowCount() > 1);
        openFolderButton.setEnabled(hasSelection && new File(row.project.path).exists());
    }

    private void openSelectedProject() {
        ProjectRow row = getSelectedRow();
        if (row == null || row.active) {
            return;
        }
        new ProjectMenuAction(app, row.project).actionPerformed(
                new java.awt.event.ActionEvent(this, java.awt.event.ActionEvent.ACTION_PERFORMED, row.project.name));
        refreshProjects();
    }

    private void renameSelectedProject() {
        ProjectRow row = getSelectedRow();
        if (row == null) {
            return;
        }

        String newName = JOptionPane.showInputDialog(this, "Project name", row.project.name);
        if (newName == null) {
            return;
        }
        newName = newName.trim();
        if (newName.isEmpty() || newName.equals(row.project.name)) {
            return;
        }

        try {
            app.prepareProjectCatalogMutation(row.active);
            Util.renameProject(row.project.name, newName);
            app.refreshProjectCatalogViews();
            refreshProjects();
            selectProject(newName);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Rename Project", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelectedProject() {
        ProjectRow row = getSelectedRow();
        if (row == null) {
            return;
        }

        int choice = JOptionPane.showConfirmDialog(
                this,
                "Delete project \"" + row.project.name + "\" and its project folder?\n\n" + new File(row.project.path).getAbsolutePath(),
                "Delete Project",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            app.prepareProjectCatalogMutation(row.active);
            Util.deleteProject(row.project.name);
            app.refreshProjectCatalogViews();
            refreshProjects();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Delete Project", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openSelectedProjectFolder() {
        ProjectRow row = getSelectedRow();
        if (row == null) {
            return;
        }
        try {
            Desktop.getDesktop().open(new File(row.project.path));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Open Project Folder", JOptionPane.ERROR_MESSAGE);
        }
    }

    private ProjectRow getSelectedRow() {
        int viewRow = projectsTable.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        return tableModel.getRow(projectsTable.convertRowIndexToModel(viewRow));
    }

    private String getSelectedProjectName() {
        ProjectRow row = getSelectedRow();
        return row == null ? null : row.project.name;
    }

    private void restoreSelection(String projectName) {
        if (projectName != null) {
            selectProject(projectName);
        }
        if (projectsTable.getSelectedRow() < 0 && tableModel.getRowCount() > 0) {
            projectsTable.setRowSelectionInterval(0, 0);
        }
    }

    private void selectProject(String projectName) {
        for (int modelRow = 0; modelRow < tableModel.getRowCount(); ++modelRow) {
            ProjectRow row = tableModel.getRow(modelRow);
            if (row.project.name.equals(projectName)) {
                int viewRow = projectsTable.convertRowIndexToView(modelRow);
                projectsTable.setRowSelectionInterval(viewRow, viewRow);
                projectsTable.scrollRectToVisible(projectsTable.getCellRect(viewRow, 0, true));
                return;
            }
        }
    }

    private static class ProjectRow {
        final SceneMaxProject project;
        final boolean active;
        final long modified;

        ProjectRow(SceneMaxProject project, boolean active) {
            this.project = project;
            this.active = active;
            File folder = new File(project.path);
            this.modified = folder.exists() ? folder.lastModified() : 0L;
        }
    }

    private static class ProjectTableModel extends AbstractTableModel {
        private final String[] columns = {"Name", "Active", "Projector", "Path", "Modified"};
        private final List<ProjectRow> rows = new ArrayList<>();

        void setRows(List<ProjectRow> rows) {
            this.rows.clear();
            this.rows.addAll(rows);
            fireTableDataChanged();
        }

        ProjectRow getRow(int rowIndex) {
            return rows.get(rowIndex);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 1) {
                return Boolean.class;
            }
            return String.class;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ProjectRow row = rows.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    return row.project.name;
                case 1:
                    return row.active;
                case 2:
                    return row.project.getProjectorLabel();
                case 3:
                    return new File(row.project.path).getAbsolutePath();
                case 4:
                    return row.modified <= 0 ? "" : DateFormat.getDateTimeInstance().format(new Date(row.modified));
                default:
                    return "";
            }
        }
    }
}
