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

public class ImportAudioFileDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextField txtPath;
    private JButton btnSelect;
    private JTextField txtName;
    private JButton btnTest;

    private String selectedFile;
    private String destFilePath;

    public ImportAudioFileDialog() {
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
        btnTest.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

            }
        });
        btnSelect.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                String userhome = System.getProperty("user.home");
                JFileChooser jfc = new JFileChooser(userhome + "/Downloads");

                jfc.setFileFilter(new FileFilter() {

                    public String getDescription() {
                        return "Audio Files (*.ogg, *.wav)";
                    }

                    public boolean accept(File f) {
                        if (f.isDirectory()) {
                            return true;
                        } else {
                            String filename = f.getName().toLowerCase();
                            return filename.endsWith(".wav") || filename.endsWith(".ogg");
                        }
                    }
                });
                int returnValue = jfc.showOpenDialog(null);

                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    File file = jfc.getSelectedFile();
                    selectedFile = file.getAbsolutePath();
                    txtPath.setText(selectedFile);

                    String name = file.getName().toLowerCase();
                    if (name.endsWith(".ogg")) {
                        name = name.replace(".ogg", "");
                    }
                    if (name.endsWith(".wav")) {
                        name = name.replace(".wav", "");
                    }

                    txtName.setText(name);
                }
            }
        });
        btnTest.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (importAudio()) {

                    String audio = txtName.getText().trim();
                    String prg = "sys.print \"testing audio: " + audio + "...\"\n" +
                            "audio.play \"" + audio + "\"\n";

                    File tmpPath = new File(Util.getScriptsFolder() + "/tmp");
                    new RunLauncherTask(tmpPath.getAbsolutePath(), prg, new Runnable() {
                        @Override
                        public void run() {
                            rollbackImportedAudio();
                        }
                    }).execute();
                }
            }
        });

    }

    private boolean importAudio() {

        if (audioNameExists(txtName.getText(), "./resources", "audio.json") ||
                audioNameExists(txtName.getText(), Util.getResourcesFolder(), "audio-ext.json")) {
            JOptionPane.showMessageDialog(null, "Audio resource name: " + txtName.getText() + " already exits", "Error Message", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }

        File srcFile = new File(selectedFile);
        destFilePath = "./" + Util.getResourcesFolder() + "/audio/" + srcFile.getName();
        File destFile = new File(destFilePath);
        if (destFile.exists()) {
            JOptionPane.showMessageDialog(null, "File: " + destFile.getAbsolutePath() + " already exits", "Error Message", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }

        try {
            FileUtils.copyFile(srcFile, destFile);
            JSONObject res = getResourcesFolderIndex(("./" + Util.getResourcesFolder() + "/audio/audio-ext.json"));
            JSONArray audio = res.getJSONArray("sounds");

            JSONObject item = new JSONObject();
            item.put("name", txtName.getText());
            item.put("path", "audio/" + srcFile.getName());

            audio.put(item);

            return Util.writeFile("./" + Util.getResourcesFolder() + "/audio/audio-ext.json", res.toString(2));


        } catch (IOException e) {
            e.printStackTrace();
        }


        return true;

    }

    private boolean audioNameExists(String name, String path, String fileName) {

        JSONObject res = getResourcesFolderIndex(path + "/audio/" + fileName);
        JSONArray models = res.getJSONArray("sounds");
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
            return new JSONObject("{\"sounds\":[]}");
        }

        String s = Util.readFile(f);
        if (s == null || s.length() == 0) return null;
        return new JSONObject(s);
    }


    private void rollbackImportedAudio() {

        String name = txtName.getText();
        removeAudioFromList(name);

        File audioFile = new File(destFilePath);
        if (audioFile.exists()) {
            try {
                FileUtils.forceDelete(audioFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


    }

    private void removeAudioFromList(String name) {

        JSONObject res = getResourcesFolderIndex(("./" + Util.getResourcesFolder() + "/audio/audio-ext.json"));
        JSONArray audio = res.getJSONArray("sounds");
        for (int i = 0; i < audio.length(); ++i) {
            JSONObject m = audio.getJSONObject(i);
            if (m.getString("name").equalsIgnoreCase(name)) {
                audio.remove(i);
                break;
            }
        }

        Util.writeFile("./" + Util.getResourcesFolder() + "/audio/audio-ext.json", res.toString(2));
    }

    private void onOK() {
        if (importAudio()) {
            JOptionPane.showMessageDialog(null, "Audio resource: " + txtName.getText() + " imported successfully", "Model Import", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } else {
            JOptionPane.showMessageDialog(null, "Failed importing audio resource " + txtName.getText(), "Error Message", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void onCancel() {
        // add your code here if necessary
        dispose();
    }

    public static void main(String[] args) {
        ImportAudioFileDialog dialog = new ImportAudioFileDialog();
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
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(2, 3, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("Choose File:");
        panel3.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtPath = new JTextField();
        panel3.add(txtPath, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        btnSelect = new JButton();
        btnSelect.setText("Select...");
        panel3.add(btnSelect, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Label");
        panel3.add(label2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtName = new JTextField();
        panel3.add(txtName, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }

}
