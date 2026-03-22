package com.scenemax.desktop;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.scenemaxeng.common.types.IAppObserver;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;

public class ImportSpriteSheetDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextField txtPath;
    private JButton btnSelect;
    private JButton btnTest;
    private JTextField txtName;
    private JTextField txtCols;
    private JTextField txtRows;
    private JTextField txtFrameWidth;
    private JTextField txtFrameHeight;
    private String selectedFile;
    private String destFilePath;


    public ImportSpriteSheetDialog() {
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
        btnSelect.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String userhome = System.getProperty("user.home");
                JFileChooser jfc = new JFileChooser(userhome + "/Downloads");
                jfc.setFileFilter(new FileFilter() {

                    public String getDescription() {
                        return "Sprite-Sheet Images (*.jpg, *.png)";
                    }

                    public boolean accept(File f) {
                        if (f.isDirectory()) {
                            return true;
                        } else {
                            String filename = f.getName().toLowerCase();
                            return filename.endsWith(".jpg") || filename.endsWith(".jpeg") || filename.endsWith(".png");
                        }
                    }
                });
                int returnValue = jfc.showOpenDialog(null);

                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    File file = jfc.getSelectedFile();
                    selectedFile = file.getAbsolutePath();
                    txtPath.setText(selectedFile);

                    String name = file.getName().toLowerCase();
                    if (name.endsWith(".jpg")) {
                        name = name.replace(".jpg", "");
                    }
                    if (name.endsWith(".png")) {
                        name = name.replace(".png", "");
                    }

                    txtName.setText(name);
                }
            }
        });
        btnTest.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (importSprite()) {
                    String sprite = txtName.getText().trim();
                    String prg = "b is a " + sprite + " sprite\r\n" +
                            "b.play(frames 0 to " + (Integer.parseInt(txtCols.getText()) * Integer.parseInt(txtRows.getText()) - 1) + ") loop";

                    File tmpPath = new File(Util.getScriptsFolder() + "/tmp");
                    new RunLauncherTask(tmpPath.getAbsolutePath(), prg, new Runnable() {
                        @Override
                        public void run() {
                            rollbackImportedSprite();
                        }
                    }).execute();
                }
            }
        });

    }

    private void removeSpriteFromList(String modelName) {

        JSONObject res = getResourcesFolderIndex((Util.getResourcesFolder() + "/sprites/sprites-ext.json"));
        JSONArray sprites = res.getJSONArray("sprites");
        for (int i = 0; i < sprites.length(); ++i) {
            JSONObject m = sprites.getJSONObject(i);
            if (m.getString("name").equalsIgnoreCase(modelName)) {
                sprites.remove(i);
                break;
            }
        }

        Util.writeFile(Util.getResourcesFolder() + "/sprites/sprites-ext.json", res.toString(2));

    }

    private void rollbackImportedSprite() {

        String name = txtName.getText();
        removeSpriteFromList(name);

        File spriteFile = new File(destFilePath);
        if (spriteFile.exists()) {
            try {
                FileUtils.forceDelete(spriteFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private boolean importSprite() {

        if (spriteNameExists(txtName.getText(), "./resources", "sprites.json") ||
                spriteNameExists(txtName.getText(), Util.getResourcesFolder(), "sprites-ext.json")) {
            JOptionPane.showMessageDialog(null, "Sprite name: " + txtName.getText() + " already exits", "Error Message", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }

        File srcFile = new File(selectedFile);
        destFilePath = Util.getResourcesFolder() + "/sprites/" + srcFile.getName();
        File destFile = new File(destFilePath);
        if (destFile.exists()) {
            JOptionPane.showMessageDialog(null, "File: " + destFile.getAbsolutePath() + " already exits", "Error Message", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }

        try {
            FileUtils.copyFile(srcFile, destFile);
            JSONObject res = getResourcesFolderIndex((Util.getResourcesFolder() + "/sprites/sprites-ext.json"));
            JSONArray sprites = res.getJSONArray("sprites");

            JSONObject sprite = new JSONObject();
            sprite.put("name", txtName.getText());
            sprite.put("path", "sprites/" + srcFile.getName());
            sprite.put("rows", getFloatValue(txtRows.getText(), 1.0f));
            sprite.put("cols", getFloatValue(txtCols.getText(), 1.0f));
            sprite.put("frameWidth", getFloatValue(txtFrameWidth.getText(), 1.0f));
            sprite.put("frameHeight", getFloatValue(txtFrameHeight.getText(), 0.0f));

            sprites.put(sprite);

            return Util.writeFile(Util.getResourcesFolder() + "/sprites/sprites-ext.json", res.toString(2));


        } catch (IOException e) {
            e.printStackTrace();
        }


        return true;
    }

    private float getFloatValue(String text, float defaultVal) {
        if (text == null) return defaultVal;
        text = text.trim();
        if (text.length() == 0) return defaultVal;

        float retval = defaultVal;
        try {
            retval = Float.parseFloat(text);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return retval;
    }


    private boolean spriteNameExists(String name, String path, String fileName) {

        JSONObject res = getResourcesFolderIndex(path + "/sprites/" + fileName);
        JSONArray models = res.getJSONArray("sprites");
        for (int i = 0; i < models.length(); ++i) {
            JSONObject m = models.getJSONObject(i);
            if (m.getString("name").equalsIgnoreCase(name)) {
                return true;
            }
        }

        return false;
    }

    private JSONObject getResourcesFolderIndex(String path) {
        File f = new File(path);
        if (!f.exists()) {
            return new JSONObject("{\"sprites\":[]}");
        }

        String s = Util.readFile(f);
        if (s == null || s.length() == 0) return null;
        return new JSONObject(s);
    }

    private void onOK() {

        if (importSprite()) {
            JOptionPane.showMessageDialog(null, "Sprite: " + txtName.getText() + " imported successfully", "Model Import", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } else {
            JOptionPane.showMessageDialog(null, "Failed importing sprite " + txtName.getText(), "Error Message", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void onCancel() {
        // add your code here if necessary
        dispose();
    }

    public static void main(String[] args) {
        ImportSpriteSheetDialog dialog = new ImportSpriteSheetDialog();
        dialog.pack();
        dialog.setVisible(true);
        System.exit(0);
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
        panel1.add(spacer1, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1, true, false));
        panel1.add(panel2, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        buttonOK = new JButton();
        buttonOK.setText("Import");
        panel2.add(buttonOK, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonCancel = new JButton();
        buttonCancel.setText("Cancel");
        panel2.add(buttonCancel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnTest = new JButton();
        btnTest.setText("Test");
        panel1.add(btnTest, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(11, 4, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        txtPath = new JTextField();
        panel3.add(txtPath, new GridConstraints(0, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel3.add(spacer2, new GridConstraints(1, 1, 10, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        btnSelect = new JButton();
        btnSelect.setText("Select...");
        panel3.add(btnSelect, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtName = new JTextField();
        txtName.setText("");
        panel3.add(txtName, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("Columns:");
        panel3.add(label1, new GridConstraints(3, 0, 2, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Rows:");
        panel3.add(label2, new GridConstraints(5, 0, 2, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("Frame Width:");
        panel3.add(label3, new GridConstraints(9, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtFrameHeight = new JTextField();
        txtFrameHeight.setText("1");
        panel3.add(txtFrameHeight, new GridConstraints(10, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("Frame Height:");
        panel3.add(label4, new GridConstraints(10, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label5 = new JLabel();
        label5.setText("Choose SpriteSheet:");
        panel3.add(label5, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label6 = new JLabel();
        label6.setText("Sprite Name:");
        panel3.add(label6, new GridConstraints(1, 0, 2, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtCols = new JTextField();
        txtCols.setText("1");
        panel3.add(txtCols, new GridConstraints(3, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        txtRows = new JTextField();
        txtRows.setText("1");
        panel3.add(txtRows, new GridConstraints(5, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        txtFrameWidth = new JTextField();
        txtFrameWidth.setText("1");
        panel3.add(txtFrameWidth, new GridConstraints(9, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }

}
