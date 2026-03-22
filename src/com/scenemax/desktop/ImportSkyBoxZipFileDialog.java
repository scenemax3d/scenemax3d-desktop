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

public class ImportSkyBoxZipFileDialog extends JDialog {
    private JPanel contentPane;
    private JButton btnImport;
    private JButton buttonCancel;
    private JButton btnTest;
    private JTextField txtFile;
    private JButton btnSelectFile;
    private JTextField txtSkyBoxName;
    private String destFilePath;
    private String selectedFile;
    private String selectedFileDestDir;
    private String upFile;
    private String downFile;
    private String leftFile;
    private String rightFile;
    private String frontFile;
    private String backFile;

    public ImportSkyBoxZipFileDialog() {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(btnImport);

        btnImport.addActionListener(new ActionListener() {
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
        btnSelectFile.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String userhome = System.getProperty("user.home");
                JFileChooser jfc = new JFileChooser(userhome + "/Downloads");

                jfc.setFileFilter(new FileFilter() {

                    public String getDescription() {
                        return "Skybox Zip File (*.zip)";
                    }

                    public boolean accept(File f) {
                        if (f.isDirectory()) {
                            return true;
                        } else {
                            String filename = f.getName().toLowerCase();
                            return filename.endsWith(".zip");
                        }
                    }
                });


                int returnValue = jfc.showOpenDialog(null);

                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    File file = jfc.getSelectedFile();
                    selectedFile = file.getAbsolutePath();
                    txtFile.setText(selectedFile);

                    if (selectedFile.toLowerCase().endsWith(".zip")) {
                        String extractedFolder = extractSkyboxZip(selectedFile);
                        if (!findSkyboxFiles(extractedFolder)) {
                            return;
                        }

                        txtSkyBoxName.setText(file.getName().replace(".zip", ""));

                    }

                }

            }
        });

        btnTest.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {


                if (importSkybox()) {
                    String skybox = txtSkyBoxName.getText().trim();
                    String prg = "sys.print \"Testing " + skybox + ": skybox\" : pos=(0,0,0)\r\n" +
                            "skybox.show \"" + skybox + "\"\r\n" +
                            "wait 3 seconds\r\n";

                    File tmpPath = new File(Util.getScriptsFolder() + "/tmp");
                    new RunLauncherTask(tmpPath.getAbsolutePath(), prg, new Runnable() {
                        @Override
                        public void run() {
                            rollbackImportedSkybox();
                        }
                    }).execute();

                }


            }
        });
    }

    private boolean findSkyboxFiles(String extractedFolder) {
        File folder = new File(extractedFolder);

        for (File f : folder.listFiles()) {
            String name = f.getName();
            if (name.contains("up") || name.contains("top")) {
                upFile = name;
            } else if (name.contains("down") || name.contains("bottom")) {
                downFile = name;
            } else if (name.contains("left") || name.contains("west")) {
                leftFile = name;
            } else if (name.contains("right") || name.contains("east")) {
                rightFile = name;
            } else if (name.contains("front") || name.contains("north")) {
                frontFile = name;
            } else if (name.contains("back") || name.contains("south")) {
                backFile = name;
            }
        }

        return (upFile != null && downFile != null &&
                leftFile != null && rightFile != null &&
                frontFile != null && backFile != null);

    }

    private void onOK() {
        if (importSkybox()) {
            JOptionPane.showMessageDialog(null, "Skybox resource: " + txtSkyBoxName.getText() + " imported successfully", "Model Import", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } else {
            JOptionPane.showMessageDialog(null, "Failed importing Skybox resource " + txtSkyBoxName.getText(), "Error Message", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void onCancel() {
        // add your code here if necessary
        dispose();
    }

    private JSONObject getResourcesFolderIndex(String path) {
        File f = new File(path);
        if (!f.exists()) {
            return new JSONObject("{\"skyboxes\":[]}");
        }

        String s = Util.readFile(f);
        if (s == null || s.length() == 0) return null;
        return new JSONObject(s);
    }

    private boolean skyboxNameExists(String name, String path, String fileName) {

        JSONObject res = getResourcesFolderIndex(path + "/skyboxes/" + fileName);
        JSONArray models = res.getJSONArray("skyboxes");
        for (int i = 0; i < models.length(); ++i) {
            JSONObject m = models.getJSONObject(i);
            if (m.getString("name").equalsIgnoreCase(name)) {
                return true;
            }
        }

        return false;
    }

    private void rollbackImportedSkybox() {

        String name = txtSkyBoxName.getText();
        removeSkyboxFromList(name);

        File skyboxFolder = new File(selectedFileDestDir);
        if (skyboxFolder.exists()) {
            try {
                FileUtils.forceDelete(skyboxFolder);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


    }

    private void removeSkyboxFromList(String name) {

        JSONObject res = getResourcesFolderIndex((Util.getResourcesFolder() + "/skyboxes/skyboxes-ext.json"));
        JSONArray audio = res.getJSONArray("skyboxes");
        for (int i = 0; i < audio.length(); ++i) {
            JSONObject m = audio.getJSONObject(i);
            if (m.getString("name").equalsIgnoreCase(name)) {
                audio.remove(i);
                break;
            }
        }

        Util.writeFile(Util.getResourcesFolder() + "/skyboxes/skyboxes-ext.json", res.toString(2));
    }

    private boolean importSkybox() {

        if (skyboxNameExists(txtSkyBoxName.getText(), "./resources", "skyboxes.json") ||
                skyboxNameExists(txtSkyBoxName.getText(), Util.getResourcesFolder(), "skyboxes-ext.json")) {
            JOptionPane.showMessageDialog(null, "Skybox name: " + txtSkyBoxName.getText() + " already exits", "Error Message", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }

        File srcDir = new File(selectedFile.replace(".zip", ""));
        selectedFileDestDir = Util.getResourcesFolder() + "/skyboxes/" + txtSkyBoxName.getText().trim();
        File destDir = new File(selectedFileDestDir);
        if (destDir.exists()) {
            JOptionPane.showMessageDialog(null, "Skybox directory: " + destDir.getAbsolutePath() + " already exits", "Error Message", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }

        destDir.mkdir();

        try {
            FileUtils.copyDirectory(srcDir, destDir);
            JSONObject res = getResourcesFolderIndex((Util.getResourcesFolder() + "/skyboxes/skyboxes-ext.json"));
            JSONArray skyboxes = res.getJSONArray("skyboxes");

            JSONObject skybox = new JSONObject("{}");
            skybox.put("name", txtSkyBoxName.getText());

            String destFolderName = destDir.getName();
            skybox.put("up", "skyboxes/" + destFolderName + "/" + upFile);
            skybox.put("down", "skyboxes/" + destFolderName + "/" + downFile);
            skybox.put("left", "skyboxes/" + destFolderName + "/" + leftFile);
            skybox.put("right", "skyboxes/" + destFolderName + "/" + rightFile);
            skybox.put("front", "skyboxes/" + destFolderName + "/" + frontFile);
            skybox.put("back", "skyboxes/" + destFolderName + "/" + backFile);

            skyboxes.put(skybox);

            return Util.writeFile(Util.getResourcesFolder() + "/skyboxes/skyboxes-ext.json", res.toString(2));


        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }

    private String extractSkyboxZip(String zipFile) {

        File f = new File(zipFile);
        File folder = new File(f.getParentFile().getAbsolutePath() + "/" + f.getName().toLowerCase().replace(".zip", ""));
        if (folder.exists()) {
            try {
                FileUtils.deleteDirectory(folder);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Util.unzip(zipFile, folder.getAbsolutePath());

        return folder.getAbsolutePath();
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
        btnImport = new JButton();
        btnImport.setText("Import");
        panel2.add(btnImport, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonCancel = new JButton();
        buttonCancel.setText("Cancel");
        panel2.add(buttonCancel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnTest = new JButton();
        btnTest.setText("Test");
        panel1.add(btnTest, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(3, 3, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        txtFile = new JTextField();
        panel3.add(txtFile, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel3.add(spacer2, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("File:");
        panel3.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnSelectFile = new JButton();
        btnSelectFile.setText("Select...");
        panel3.add(btnSelectFile, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtSkyBoxName = new JTextField();
        panel3.add(txtSkyBoxName, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("SkyBox Name:");
        panel3.add(label2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }

}
