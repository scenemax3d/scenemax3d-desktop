package com.scenemax.desktop;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.apache.commons.io.FileUtils;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.FontUIResource;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.awt.event.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MacroBuilder extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JPanel mainPanel;
    private JComboBox comboBoxMacroFiles;
    private JTable tblPatterns;
    private JPanel editorPanel;
    private JSplitPane splitPane;
    private JButton btnRTLView;
    private JButton btnAddRow;
    private JButton btnDelRow;
    private JButton btnNewMacro;
    private JButton btnSaveMacro;
    private JPanel panelPatternTableHost;
    private RSyntaxTextArea textArea;
    private List<String> programs = new ArrayList<>();

    public MacroBuilder() {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });

        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);


        loadMacroFiles();
        showProgramArea();
        tblPatterns.setMinimumSize(new Dimension(450, 250));
        tblPatterns.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent event) {

                if (programs.size() == 0) {
                    textArea.setText("");
                    return;
                }

                String prg = programs.get(tblPatterns.getSelectedRow());
                textArea.setText(prg);
            }
        });

        if (comboBoxMacroFiles.getItemCount() > 0) {
            comboBoxMacroFiles.setSelectedIndex(0);
        }

        btnRTLView.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (tblPatterns.getComponentOrientation() == ComponentOrientation.RIGHT_TO_LEFT) {
                    panelPatternTableHost.applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
                    tblPatterns.applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
                    tblPatterns.getTableHeader().setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
                } else {
                    panelPatternTableHost.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
                    tblPatterns.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
                    tblPatterns.getTableHeader().setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
                }
            }
        });
        btnSaveMacro.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveActiveMacro();
            }
        });

        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updatePatternProgram();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updatePatternProgram();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updatePatternProgram();
            }
        });

        btnNewMacro.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                createNewMacro();
            }
        });

        btnAddRow.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                createNewMacroRow();
            }
        });

        btnDelRow.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteCurrentMacroRow();
            }
        });

    }

    private void deleteCurrentMacroRow() {
        int row = tblPatterns.getSelectedRow();
        DefaultTableModel tm = (DefaultTableModel) tblPatterns.getModel();

        programs.remove(row);
        tm.removeRow(row);
        tm.fireTableDataChanged();
    }

    private void createNewMacroRow() {
        DefaultTableModel tm = (DefaultTableModel) tblPatterns.getModel();
        tm.addRow(new Object[]{""});
        programs.add("");
        tblPatterns.setRowSelectionInterval(tm.getRowCount() - 1, tm.getRowCount() - 1);

        tm.fireTableDataChanged();
    }

    private void createNewMacro() {
        String fileName = (String) JOptionPane.showInputDialog(
                null,
                "Type short simple Macro name",
                "Macro Name",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                "");

        if (fileName == null || fileName.trim().length() == 0) {
            return;
        }

        fileName += ".smm";
        File f = new File("macro/" + fileName);
        if (f.exists()) {
            JOptionPane.showMessageDialog(null, "Macro " + fileName + " already exists", "Error Message", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            f.createNewFile();
            JSONObject json = new JSONObject("{patterns:[]}");
            BufferedWriter writer = null;
            try {

                FileUtils.write(f, json.toString(), StandardCharsets.UTF_8);

//                writer = new BufferedWriter(new FileWriter(f));
//                writer.write(json.toString());
//                writer.close();
                JOptionPane.showMessageDialog(null, "Macro " + fileName + " saved successfully", "Macro Saved", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Failed saving Macro " + fileName, "Error Message", JOptionPane.INFORMATION_MESSAGE);
            }

        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Failed creating Macro " + fileName + "\r\n" + e.getMessage(), "Error Message", JOptionPane.ERROR_MESSAGE);
            return;
        }

        comboBoxMacroFiles.addItem(fileName);
        comboBoxMacroFiles.setSelectedIndex(comboBoxMacroFiles.getItemCount() - 1);
    }

    private void updatePatternProgram() {

        if (programs.size() == 0) {
            return;
        }

        int index = tblPatterns.getSelectedRow();
        programs.set(index, textArea.getText());
    }

    private void saveActiveMacro() {
        String fileName = comboBoxMacroFiles.getSelectedItem().toString();
        DefaultTableModel tm = (DefaultTableModel) tblPatterns.getModel();
        int rows = tm.getRowCount();
        JSONObject json = new JSONObject("{patterns:[]}");
        JSONArray patterns = json.getJSONArray("patterns");

        for (int i = 0; i < rows; ++i) {
            String pat = tm.getValueAt(i, 0).toString();
            String prg = programs.get(i);

            pat = pat.trim();
            if (pat != null && pat.length() > 0) {
                JSONObject item = new JSONObject();
                item.put("pat", pat);
                item.put("prg", prg);
                patterns.put(item);
            }
        }

        File f = new File("macro/" + fileName);
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(f));
            writer.write(json.toString());
            writer.close();
            JOptionPane.showMessageDialog(null, "Macro " + fileName + " saved successfully", "Macro Saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Failed saving Macro " + fileName, "Error Message", JOptionPane.INFORMATION_MESSAGE);
        }


    }

    private void showProgramArea() {

        textArea = new RSyntaxTextArea(20, 60);
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        Util.applyDarkTheme(textArea);
        //textArea.setCodeFoldingEnabled(true);
        textArea.setTabSize(2);
        int fontSize = textArea.getFont().getSize();
        textArea.setFont(textArea.getFont().deriveFont(fontSize + 2));
        RTextScrollPane textAreaSP = new RTextScrollPane(textArea);
        editorPanel.add(textAreaSP);

    }

    private void loadMacroFiles() {

        comboBoxMacroFiles.removeAllItems();

        File f = new File("macro");
        if (!f.exists()) {
            return;
        }

        for (File macro : f.listFiles()) {
            comboBoxMacroFiles.addItem(macro.getName());
        }
        comboBoxMacroFiles.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String macroFileName = comboBoxMacroFiles.getSelectedItem().toString();

                File f = new File("macro/" + macroFileName);
                String json = Util.readFile(f);

                JSONObject macroJson = null;
                programs.clear();

                DefaultTableModel tm = new DefaultTableModel();
                tm.addColumn("Pattern");
                tblPatterns.setModel(tm);
                tm.fireTableDataChanged();

                try {
                    macroJson = new JSONObject(json);
                } catch (Exception ex) {
                    //MacroBuilder.this.mainPanel.updateUI();
                    return;
                }

                if (macroJson.has("patterns")) {
                    JSONArray arrPatterns = macroJson.getJSONArray("patterns");

                    for (int i = 0; i < arrPatterns.length(); ++i) {
                        JSONObject item = arrPatterns.getJSONObject(i);
                        String pat = item.getString("pat");
                        String prg = item.getString("prg");
                        programs.add(prg);
                        tm.addRow(new Object[]{pat});

                    }
                }

                tm.fireTableDataChanged();
                //MacroBuilder.this.mainPanel.updateUI();

            }
        });

    }

    private void onOK() {
        // add your code here
        dispose();
    }

    private void onCancel() {
        // add your code here if necessary
        dispose();
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        contentPane = new JPanel();
        contentPane.setLayout(new GridLayoutManager(2, 1, new Insets(10, 10, 10, 10), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1, true, false));
        panel1.add(panel2, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        buttonOK = new JButton();
        buttonOK.setText("OK");
        panel2.add(buttonOK, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonCancel = new JButton();
        buttonCancel.setText("Cancel");
        panel2.add(buttonCancel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        mainPanel = new JPanel();
        mainPanel.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(mainPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        splitPane = new JSplitPane();
        splitPane.setOrientation(0);
        mainPanel.add(splitPane, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(200, 200), null, 0, false));
        editorPanel = new JPanel();
        editorPanel.setLayout(new BorderLayout(0, 0));
        splitPane.setRightComponent(editorPanel);
        panelPatternTableHost = new JPanel();
        panelPatternTableHost.setLayout(new BorderLayout(0, 0));
        splitPane.setLeftComponent(panelPatternTableHost);
        tblPatterns = new JTable();
        Font tblPatternsFont = this.$$$getFont$$$(null, -1, 16, tblPatterns.getFont());
        if (tblPatternsFont != null) tblPatterns.setFont(tblPatternsFont);
        panelPatternTableHost.add(tblPatterns, BorderLayout.CENTER);
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        panelPatternTableHost.add(panel3, BorderLayout.SOUTH);
        btnAddRow = new JButton();
        btnAddRow.setText("Add Row");
        panel3.add(btnAddRow, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnDelRow = new JButton();
        btnDelRow.setText("Del Row");
        panel3.add(btnDelRow, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel3.add(spacer2, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(2, 4, new Insets(0, 0, 0, 0), -1, -1));
        mainPanel.add(panel4, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
        comboBoxMacroFiles = new JComboBox();
        panel4.add(comboBoxMacroFiles, new GridConstraints(0, 0, 2, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        panel4.add(panel5, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
        btnRTLView = new JButton();
        btnRTLView.setText("RTL");
        panel5.add(btnRTLView, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnNewMacro = new JButton();
        btnNewMacro.setText("New File");
        panel5.add(btnNewMacro, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer3 = new Spacer();
        panel4.add(spacer3, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        btnSaveMacro = new JButton();
        btnSaveMacro.setText("Save Snippets");
        panel4.add(btnSaveMacro, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    private Font $$$getFont$$$(String fontName, int style, int size, Font currentFont) {
        if (currentFont == null) return null;
        String resultName;
        if (fontName == null) {
            resultName = currentFont.getName();
        } else {
            Font testFont = new Font(fontName, Font.PLAIN, 10);
            if (testFont.canDisplay('a') && testFont.canDisplay('1')) {
                resultName = fontName;
            } else {
                resultName = currentFont.getName();
            }
        }
        Font font = new Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());
        boolean isMac = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).startsWith("mac");
        Font fontWithFallback = isMac ? new Font(font.getFamily(), font.getStyle(), font.getSize()) : new StyleContext().getFont(font.getFamily(), font.getStyle(), font.getSize());
        return fontWithFallback instanceof FontUIResource ? fontWithFallback : new FontUIResource(fontWithFallback);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }

}
