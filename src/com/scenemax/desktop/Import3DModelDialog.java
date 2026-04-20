package com.scenemax.desktop;

import com.scenemax.designer.ImportedModelNormalizer;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URL;

public class Import3DModelDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextField txtFileName;
    private JButton btnChooseFile;
    private JTextField txtName;
    private JTextField txtScaleX;
    private JTextField txtScaleY;
    private JTextField txtScaleZ;
    private JTextField txtTransX;
    private JTextField txtTransY;
    private JTextField txtTransZ;
    private JTextField txtCapsouleRadius;
    private JTextField txtCapsouleHeight;
    private JTextField txtStepHeight;
    private JLabel lbl;
    private JTextField txtRotateY;
    private JTextField txtCalibrateX;
    private JTextField txtCalibrateY;
    private JTextField txtCalibrateZ;
    private JButton btnTest;
    private JCheckBox chkStatic;
    private String selectedFile;
    private String selectedFileDestDir;


    public Import3DModelDialog() {
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
        btnChooseFile.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String userhome = System.getProperty("user.home");
                JFileChooser jfc = new JFileChooser(userhome + "/Downloads");

                jfc.setFileFilter(new FileFilter() {

                    public String getDescription() {
                        return "3D Model File (*.zip, *.gltf, *glb)";
                    }

                    public boolean accept(File f) {
                        if (f.isDirectory()) {
                            return true;
                        } else {
                            String filename = f.getName().toLowerCase();
                            return filename.endsWith(".zip") || filename.endsWith(".gltf") || filename.endsWith(".glb");
                        }
                    }
                });


                int returnValue = jfc.showOpenDialog(null);

                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    File file = jfc.getSelectedFile();
                    selectedFile = file.getAbsolutePath();
                    txtFileName.setText(selectedFile);

                    if (selectedFile.toLowerCase().endsWith(".zip")) {
                        txtName.setText(file.getName().replace(".zip", ""));
                        String extractedFolder = extractModelZip(selectedFile);
                        selectedFile = findGltfFile(extractedFolder);

                    } else {
                        txtName.setText(file.getName().replace(".gltf", "").replace(".glb", ""));
                    }

                }
            }
        });

        btnTest.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (importModel()) {
                    String model = txtName.getText().trim();
                    URL menuURL = MainApp.class.getResource("/code_templates/import_model_test_program");
                    String code = "";
                    try {
                        InputStream script = menuURL.openStream();
                        code = new String(Util.toByteArray(script));
                        code = code.replace("@model", model);

                        File tmpPath = new File(Util.getScriptsFolder() + "/tmp");
                        new RunLauncherTask(tmpPath.getAbsolutePath(), code, new Runnable() {
                            @Override
                            public void run() {
                                rollbackImportedModel();
                            }
                        }).execute();


                    } catch (Exception ex) {

                    }


                }
            }
        });


//        URL assetCfgUrl = JmeSystem.getPlatformAssetConfigURL();
//        assetManager = JmeSystem.newAssetManager(assetCfgUrl);
//        assetManager.registerLoader(LwjglAssetLoader.class,
//                "3ds", "3mf", "blend", "bvh", "dae", "fbx", "glb", "gltf",
//                "lwo", "meshxml", "mesh.xml", "obj", "ply", "stl");
//        File tmp = new File("./tmp");
//        try {
//            assetManager.registerLocator(tmp.getCanonicalPath(), FileLocator.class);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }

    }

    private void removeModelFromList(String modelName) {

        JSONObject res = getResourcesFolderIndex((Util.getResourcesFolder() + "/Models/models-ext.json"));
        JSONArray models = res.getJSONArray("models");
        for (int i = 0; i < models.length(); ++i) {
            JSONObject m = models.getJSONObject(i);
            if (m.getString("name").equalsIgnoreCase(modelName)) {
                models.remove(i);
                break;
            }
        }

        Util.writeFile(Util.getResourcesFolder() + "/Models/models-ext.json", res.toString(2));

    }

    private void rollbackImportedModel() {

        String name = txtName.getText();
        removeModelFromList(name);

        File modelDir = new File(selectedFileDestDir);
        if (modelDir.exists()) {
            try {
                FileUtils.deleteDirectory(modelDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


    }

    private String findGltfFile(String folder) {

        File f = new File(folder);
        for (File f2 : f.listFiles()) {
            if (f2.isFile() && (f2.getName().toLowerCase().endsWith(".gltf") || f2.getName().toLowerCase().endsWith(".glb"))) {
                return f2.getAbsolutePath();
            }
        }

        return null;
    }

    private void onOK() {
        if (importModel()) {
            JOptionPane.showMessageDialog(null, "Model: " + txtName.getText() + " imported successfully", "Model Import", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } else {
            JOptionPane.showMessageDialog(null, "Failed importing model " + txtName.getText(), "Error Message", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private JSONObject getResourcesFolderIndex(String path) {
        File f = new File(path);
        if (!f.exists()) {
            return new JSONObject("{\"models\":[]}");
        }

        String s = Util.readFile(f);
        if (s == null || s.length() == 0) return null;
        return new JSONObject(s);
    }

    private boolean importModel() {

        if (modelNameExists(txtName.getText(), "./resources", "models.json") ||
                modelNameExists(txtName.getText(), Util.getResourcesFolder(), "models-ext.json")) {
            JOptionPane.showMessageDialog(null, "Model name: " + txtName.getText() + " already exits", "Error Message", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }


        //selectedFile = convertModelToJ3o();
        boolean isGlb = selectedFile.endsWith(".glb");

        File srcFile = new File(selectedFile);
        File srcDir = srcFile.getParentFile();
        selectedFileDestDir = Util.getResourcesFolder() + "/Models/" + (isGlb ? srcFile.getName().replace(".glb", "") : srcDir.getName());
        File destDir = new File(selectedFileDestDir);
        if (destDir.exists()) {
            JOptionPane.showMessageDialog(null, "Directory: " + destDir.getAbsolutePath() + " already exits", "Error Message", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }

        destDir.mkdir();

        try {

            if (isGlb) {
                FileUtils.copyFileToDirectory(srcFile, destDir);
            } else {
                FileUtils.copyDirectory(srcDir, destDir);
            }

            File importedModelFile = new File(destDir, srcFile.getName());
            try {
                ImportedModelNormalizer.normalize(importedModelFile.toPath());
            } catch (IOException normalizeError) {
                System.err.println("[Import3DModelDialog] Imported model normalization skipped: " + normalizeError.getMessage());
            }
            JSONObject res = getResourcesFolderIndex((Util.getResourcesFolder() + "/Models/models-ext.json"));
            JSONArray models = res.getJSONArray("models");

            JSONObject model = new JSONObject("{\"physics\":{ \"character\":{} } }");
            model.put("name", txtName.getText());

            String sourceDirName = isGlb ? destDir.getName() : srcDir.getName();
            model.put("path", "Models/" + sourceDirName + "/" + srcFile.getName());
            model.put("scaleX", getFloatValue(txtScaleX.getText(), 1.0f));
            model.put("scaleY", getFloatValue(txtScaleY.getText(), 1.0f));
            model.put("scaleZ", getFloatValue(txtScaleZ.getText(), 1.0f));
            model.put("isStatic", chkStatic.isSelected());

            model.put("transX", getFloatValue(txtTransX.getText(), 0.0f));
            model.put("transY", getFloatValue(txtTransY.getText(), 0.0f));
            model.put("transZ", getFloatValue(txtTransZ.getText(), 0.0f));

            model.put("rotateY", getFloatValue(txtRotateY.getText(), 0.0f));

            JSONObject character = model.getJSONObject("physics").getJSONObject("character");
            character.put("calibrateX", getFloatValue(txtCalibrateX.getText(), 0.0f));
            character.put("calibrateY", getFloatValue(txtCalibrateY.getText(), 0.0f));
            character.put("calibrateZ", getFloatValue(txtCalibrateZ.getText(), 0.0f));
            character.put("capsuleRadius", getFloatValue(txtCapsouleRadius.getText(), 2.0f));
            character.put("capsuleHeight", getFloatValue(txtCapsouleHeight.getText(), 2.0f));
            character.put("stepHeight", getFloatValue(txtStepHeight.getText(), 0.05f));

            models.put(model);

            return Util.writeFile(Util.getResourcesFolder() + "/Models/models-ext.json", res.toString(2));


        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }

//    private String convertModelToJ3o() {
//        //
//        File sf = new File(selectedFile);
//        try {
//            FileUtils.copyFileToDirectory(sf, new File("tmp"));
//            selectedFile = new File(new File("tmp"), sf.getName()).getAbsolutePath();
//            sf = new File(selectedFile);
//            LwjglAssetKey key = new LwjglAssetKey(sf.getName());
//            key.setVerboseLogging(true);
//            Spatial m = assetManager.loadModel(key);
//
//            JmeExporter exporter = BinaryExporter.getInstance();
//            File file = new File(selectedFile + ".j3o");
//
//            exporter.save(m, file);
//        } catch (IOException exception) {
//            exception.printStackTrace();
//            throw new RuntimeException(exception);
//        }
//
//        return selectedFile;
//    }

    private String extractModelZip(String zipFile) {

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

    private boolean modelNameExists(String name, String path, String fileName) {

        JSONObject res = getResourcesFolderIndex(path + "/Models/" + fileName);
        JSONArray models = res.getJSONArray("models");
        for (int i = 0; i < models.length(); ++i) {
            JSONObject m = models.getJSONObject(i);
            if (m.getString("name").equalsIgnoreCase(name)) {
                return true;
            }
        }

        return false;
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

    private void onCancel() {
        // add your code here if necessary
        dispose();
    }

    public static void main(String[] args) {
        Import3DModelDialog dialog = new Import3DModelDialog();
        dialog.pack();
        dialog.setVisible(true);
        System.exit(0);
    }

    private void createUIComponents() {

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
        panel3.setLayout(new GridLayoutManager(21, 3, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        txtFileName = new JTextField();
        panel3.add(txtFileName, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        btnChooseFile = new JButton();
        btnChooseFile.setText("Select...");
        panel3.add(btnChooseFile, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("Choose 3D Model File");
        panel3.add(label1, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtName = new JTextField();
        panel3.add(txtName, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("Name:");
        panel3.add(label3, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Scale X:");
        panel3.add(label2, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtScaleX = new JTextField();
        txtScaleX.setText("1");
        panel3.add(txtScaleX, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel labelScaleY = new JLabel();
        labelScaleY.setText("Scale Y:");
        panel3.add(labelScaleY, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtScaleY = new JTextField();
        txtScaleY.setText("1");
        panel3.add(txtScaleY, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel labelScaleZ = new JLabel();
        labelScaleZ.setText("Scale Z:");
        panel3.add(labelScaleZ, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtScaleZ = new JTextField();
        txtScaleZ.setText("1");
        panel3.add(txtScaleZ, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        chkStatic = new JCheckBox();
        chkStatic.setText("Static");
        panel3.add(chkStatic, new GridConstraints(6, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtRotateY = new JTextField();
        txtRotateY.setText("0");
        panel3.add(txtRotateY, new GridConstraints(7, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label10 = new JLabel();
        label10.setText("Rotate Y:");
        panel3.add(label10, new GridConstraints(7, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtTransX = new JTextField();
        txtTransX.setText("0");
        panel3.add(txtTransX, new GridConstraints(8, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("Translate X:");
        panel3.add(label4, new GridConstraints(8, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtTransY = new JTextField();
        txtTransY.setText("0");
        panel3.add(txtTransY, new GridConstraints(9, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label5 = new JLabel();
        label5.setText("Translate Y:");
        panel3.add(label5, new GridConstraints(9, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtTransZ = new JTextField();
        txtTransZ.setText("0");
        panel3.add(txtTransZ, new GridConstraints(10, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label6 = new JLabel();
        label6.setText("Translate Z:");
        panel3.add(label6, new GridConstraints(10, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label7 = new JLabel();
        label7.setForeground(new Color(-16776961));
        label7.setText("Physics Dimentions (in meters):");
        panel3.add(label7, new GridConstraints(11, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtCalibrateX = new JTextField();
        txtCalibrateX.setText("0");
        panel3.add(txtCalibrateX, new GridConstraints(12, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label11 = new JLabel();
        label11.setText("Calibrate X:");
        panel3.add(label11, new GridConstraints(12, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtCalibrateY = new JTextField();
        txtCalibrateY.setText("0");
        panel3.add(txtCalibrateY, new GridConstraints(13, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label12 = new JLabel();
        label12.setText("Calibrate Y:");
        panel3.add(label12, new GridConstraints(13, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtCalibrateZ = new JTextField();
        txtCalibrateZ.setText("0");
        panel3.add(txtCalibrateZ, new GridConstraints(14, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label13 = new JLabel();
        label13.setText("Calibrate Z:");
        panel3.add(label13, new GridConstraints(14, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label8 = new JLabel();
        label8.setText("Capsoule Radius:");
        panel3.add(label8, new GridConstraints(15, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtCapsouleRadius = new JTextField();
        txtCapsouleRadius.setText("2");
        panel3.add(txtCapsouleRadius, new GridConstraints(15, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        txtCapsouleHeight = new JTextField();
        txtCapsouleHeight.setText("2");
        panel3.add(txtCapsouleHeight, new GridConstraints(16, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        lbl = new JLabel();
        lbl.setText("Capsoule Height:");
        panel3.add(lbl, new GridConstraints(16, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtStepHeight = new JTextField();
        txtStepHeight.setText("0.05");
        panel3.add(txtStepHeight, new GridConstraints(17, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label9 = new JLabel();
        label9.setText("Step Height:");
        panel3.add(label9, new GridConstraints(17, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }

}
