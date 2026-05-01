package com.scenemax.desktop;

import com.scenemaxeng.common.types.PluginsManager;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

public class PluginsDialog extends JDialog {

    private final MainApp host;
    private final PluginTableModel tableModel;
    private final JTable table;

    public PluginsDialog(MainApp host) {
        super(host, "Plugins", true);
        this.host = host;
        this.tableModel = new PluginTableModel();
        this.table = new JTable(tableModel);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        table.setFillsViewportHeight(true);
        table.setRowHeight(26);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);

        TableColumn enabledColumn = table.getColumnModel().getColumn(0);
        enabledColumn.setMaxWidth(80);
        enabledColumn.setPreferredWidth(70);
        table.getColumnModel().getColumn(1).setPreferredWidth(180);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.getColumnModel().getColumn(3).setPreferredWidth(520);

        root.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnRefresh = new JButton("Refresh");
        btnRefresh.addActionListener(this::refreshAction);
        JButton btnToggle = new JButton("Enable / Disable");
        btnToggle.addActionListener(this::toggleSelectedAction);
        JButton btnClose = new JButton("Close");
        btnClose.addActionListener(e -> dispose());
        buttons.add(btnRefresh);
        buttons.add(btnToggle);
        buttons.add(btnClose);
        root.add(buttons, BorderLayout.SOUTH);

        setContentPane(root);
        setSize(900, 420);
        setLocationRelativeTo(host);
        loadPlugins();
    }

    private void refreshAction(ActionEvent e) {
        loadPlugins();
    }

    private void toggleSelectedAction(ActionEvent e) {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Select a plugin first.", "Plugins", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        PluginRow plugin = tableModel.getPluginAt(modelRow);
        setPluginActive(plugin, !plugin.active);
    }

    private void loadPlugins() {
        JSONArray plugins = PluginsManager.getPluginsIndex();
        List<PluginRow> rows = new ArrayList<>();
        for (int i = 0; i < plugins.length(); i++) {
            JSONObject item = plugins.optJSONObject(i);
            if (item == null) {
                continue;
            }
            rows.add(new PluginRow(
                    item.optString("name", ""),
                    item.optString("desc", ""),
                    item.optBoolean("active", true)
            ));
        }
        tableModel.setPlugins(rows);
    }

    private void setPluginActive(PluginRow plugin, boolean active) {
        String action = active ? "enable" : "disable";
        int result = JOptionPane.showConfirmDialog(
                this,
                "SceneMax will restart after this change.\nDo you want to " + action + " \"" + plugin.name + "\" now?",
                "Plugins",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );
        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        if (!PluginsManager.setPluginActive(plugin.name, active)) {
            JOptionPane.showMessageDialog(
                    this,
                    "Could not update the plugin configuration.",
                    "Plugins",
                    JOptionPane.ERROR_MESSAGE
            );
            loadPlugins();
            return;
        }

        dispose();
        host.restartApplicationFromPluginManager();
    }

    private static final class PluginRow {
        private final String name;
        private final String description;
        private final boolean active;

        private PluginRow(String name, String description, boolean active) {
            this.name = name;
            this.description = description;
            this.active = active;
        }
    }

    private final class PluginTableModel extends AbstractTableModel {
        private final String[] columns = {"Enabled", "Plugin", "Status", "Description"};
        private List<PluginRow> plugins = new ArrayList<>();

        private void setPlugins(List<PluginRow> plugins) {
            this.plugins = plugins;
            fireTableDataChanged();
        }

        private PluginRow getPluginAt(int row) {
            return plugins.get(row);
        }

        @Override
        public int getRowCount() {
            return plugins.size();
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
            return columnIndex == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            PluginRow plugin = plugins.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    return plugin.active;
                case 1:
                    return plugin.name;
                case 2:
                    return plugin.active ? "Enabled" : "Disabled";
                case 3:
                    return plugin.description;
                default:
                    return "";
            }
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            if (columnIndex != 0) {
                return;
            }
            PluginRow plugin = plugins.get(rowIndex);
            boolean active = Boolean.TRUE.equals(value);
            if (plugin.active != active) {
                SwingUtilities.invokeLater(() -> setPluginActive(plugin, active));
            }
        }
    }
}
