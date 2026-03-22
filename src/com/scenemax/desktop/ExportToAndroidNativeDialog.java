package com.scenemax.desktop;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.MaskFormatter;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Locale;

import static javax.swing.JOptionPane.YES_OPTION;

public class ExportToAndroidNativeDialog extends JDialog {
    private final String scriptPath;
    private final String prg;
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextField txtTargetFolder;
    private JButton btnFolderBrowse;
    private JFormattedTextField txtPackagePrefix;
    private JFormattedTextField txtPackageCompName;
    private JFormattedTextField txtPackageProdName;
    private JRadioButton radioPortrait;
    private JRadioButton radioLandscape;
    private JRadioButton joystickLeft;
    private JRadioButton joystickRight;
    private JRadioButton joystickMiddle;
    private JRadioButton joystickNone;
    private JFormattedTextField txtAppName;
    private JTextArea txtAppText1;
    private JRadioButton radioButtonFullDeploy;
    private JRadioButton radioButtonCodeAndResOnly;
    private ExportProgramToZipFileTask saveTask;
    private File destFolder;

    public ExportToAndroidNativeDialog(final String scriptPath, final String prg) {
        setContentPane(contentPane);
        //setModal(true);
        setAlwaysOnTop(true);
        getRootPane().setDefaultButton(buttonOK);

        this.scriptPath = scriptPath;
        this.prg = prg;

        String userhome = System.getProperty("user.home");
        txtTargetFolder.setText(userhome + "\\Documents");

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

        btnFolderBrowse.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {

                String userhome = System.getProperty("user.home");
                JFileChooser jfc = null;
                jfc = new JFileChooser(userhome + "/Documents");
                jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                jfc.setAcceptAllFileFilterUsed(false);

                int returnValue = jfc.showOpenDialog(null);

                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    String path = jfc.getCurrentDirectory() + "//" + jfc.getSelectedFile().getName();
                    txtTargetFolder.setText(new File(path).getAbsolutePath());
                }

            }
        });

        ButtonGroup bg = new ButtonGroup();
        bg.add(radioPortrait);
        bg.add(radioLandscape);

        bg = new ButtonGroup();
        bg.add(joystickLeft);
        bg.add(joystickRight);
        bg.add(joystickMiddle);
        bg.add(joystickNone);

        bg = new ButtonGroup();
        bg.add(radioButtonFullDeploy);
        bg.add(radioButtonCodeAndResOnly);

        ///////
        try {
            MaskFormatter mf = new MaskFormatter("******************************");
            mf.setValidCharacters("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 _-");
            mf.install(txtAppName);

            mf = new MaskFormatter("***");
            mf.setValidCharacters("abcdefghijklmnopqrstuvwxyz ");
            mf.install(txtPackagePrefix);

            mf = new MaskFormatter("********************");
            mf.setValidCharacters("abcdefghijklmnopqrstuvwxyz_ ");
            mf.install(txtPackageCompName);

            mf = new MaskFormatter("********************");
            mf.setValidCharacters("abcdefghijklmnopqrstuvwxyz_ ");
            mf.install(txtPackageProdName);

        } catch (ParseException e) {
            e.printStackTrace();
        }

        loadConfig();
    }


    private void onOK() {
        saveConfig();
        exportToAndroidNativeApp();
        //dispose();
    }

    private void loadConfig() {
        String confPath = getConfigPath();
        JSONObject config = Util.getScriptJsonParams(confPath);
        if (config.has("andronative_app_name")) {
            setMaskedText(txtAppName, config.getString("andronative_app_name"), 30);

        }

        if (config.has("andronative_app_text")) {
            txtAppText1.setText(config.getString("andronative_app_text"));
        }

        if (config.has("andronative_package_prefix")) {
            setMaskedText(txtPackagePrefix, config.getString("andronative_package_prefix"), 3);
        } else {
            setMaskedText(txtPackagePrefix, "com", 20);
        }

        if (config.has("andronative_package_comp")) {
            setMaskedText(txtPackageCompName, config.getString("andronative_package_comp"), 20);
        }

        if (config.has("andronative_package_prod")) {
            setMaskedText(txtPackageProdName, config.getString("andronative_package_prod"), 20);
        } else {
            File f = new File(this.scriptPath).getParentFile();
            setMaskedText(txtPackageProdName, f.getName().toLowerCase().trim().replaceAll(" ", "_"), 20);
        }

        if (config.has("andronative_save_to")) {
            txtTargetFolder.setText(config.getString("andronative_save_to"));
        }

        if (config.has("andronative_is_landscape")) {
            radioLandscape.setSelected(config.getInt("andronative_is_landscape") == 1);
        }

        if (config.has("andronative_deploy_type")) {
            radioButtonCodeAndResOnly.setSelected(config.getInt("andronative_deploy_type") == 0);
        }

        if (config.has("andronative_joystick")) {
            int joystick = config.getInt("andronative_joystick");
            if (joystick == 0) {
                joystickNone.setSelected(true);
            } else if (joystick == 1) {
                joystickLeft.setSelected(true);
            } else if (joystick == 2) {
                joystickRight.setSelected(true);
            } else if (joystick == 3) {
                joystickMiddle.setSelected(true);
            }
        }

    }

    private String getConfigPath() {
        return new File(scriptPath).getParent();
    }

    private void setMaskedText(JFormattedTextField txt, String text, int maxLength) {
        txt.setText(StringUtils.rightPad(text, maxLength));
    }

    private void saveConfig() {
        String confPath = getConfigPath();
        JSONObject config = Util.getScriptJsonParams(confPath);
        config.put("andronative_app_name", txtAppName.getText().trim());
        config.put("andronative_app_text", txtAppText1.getText().trim());
        config.put("andronative_package_prefix", txtPackagePrefix.getText().trim().replaceAll(" ", "_"));
        config.put("andronative_package_comp", txtPackageCompName.getText().trim().replaceAll(" ", "_"));
        config.put("andronative_package_prod", txtPackageProdName.getText().trim().replaceAll(" ", "_"));
        config.put("andronative_save_to", txtTargetFolder.getText());
        config.put("andronative_is_landscape", radioLandscape.isSelected() ? 1 : 0);
        config.put("andronative_deploy_type", radioButtonFullDeploy.isSelected() ? 1 : 0);

        int joystick = 0;
        if (joystickLeft.isSelected()) {
            joystick = 1;
        } else if (joystickRight.isSelected()) {
            joystick = 2;
        }
        if (joystickMiddle.isSelected()) {
            joystick = 3;
        }
        config.put("andronative_joystick", joystick);

        Util.setScriptJsonParams(confPath, config);

    }

    private void exportToAndroidNativeApp() {

        int expType = ExportProgramToZipFileTask.EXPORT_TYPE_FULL;
        String targetPathSelector = txtTargetFolder.getText();

        final String targetPath = targetPathSelector;
        final String zipFilePath = targetPath + "/" +
                new File(scriptPath).getParentFile().getName() + ".zip";

        saveTask = new ExportProgramToZipFileTask(
                scriptPath,
                prg,
                targetPath,
                null,
                expType,
                null,
                true,
                ExportProgramToZipFileTask.TARGET_DEVICE_NATIVE_ANDROID,
                new Runnable() {
                    @Override
                    public void run() {

                        // extract android native app
                        File nativeAppFolder = extractAndroidBaseNativeApp();
                        if (nativeAppFolder == null) {
                            JOptionPane.showMessageDialog(null, "Failed to extract base Android application");
                            return;
                        }

                        // extract program to native app resource folder
                        extractProgramToNativeAppFolder(zipFilePath, nativeAppFolder);

                        // move final files to the destination folder
                        File destFolder = new File(txtTargetFolder.getText());
                        if (!destFolder.exists()) {
                            JOptionPane.showMessageDialog(null, "Destination folder doesn't exist. Select new one and try again");
                            return;
                        }

                        destFolder = new File(destFolder, txtPackageProdName.getText().trim().replaceAll(" ", "_"));
                        if (!destFolder.exists()) {
                            destFolder.mkdir();
                        }

                        boolean movRes = moveGeneratedAppToDestinationFolder(nativeAppFolder, destFolder);

                        if (movRes) {
                            ExportToAndroidNativeDialog.this.destFolder = destFolder;
                            int n = JOptionPane.showConfirmDialog(
                                    ExportToAndroidNativeDialog.this,
                                    "Export finished successfully. Open file location?",
                                    "Open File Location",
                                    JOptionPane.YES_NO_OPTION);

                            if (n == YES_OPTION) {
                                try {

                                    Desktop.getDesktop().open(destFolder);

                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        } else {
                            JOptionPane.showMessageDialog(null, "Failed to copy the final Android application to the destination folder.\r\n" +
                                    "Change destination folder and try again");
                            return;
                        }

                        dispose();
                    }
                }
        );

        try {
            saveTask.execute();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    private boolean moveGeneratedAppToDestinationFolder(File appFolder, File destFolder) {
        if (radioButtonFullDeploy.isSelected()) {
            try {
                if (destFolder.exists()) {
                    FileUtils.deleteDirectory(destFolder);
                }
                FileUtils.copyDirectory(appFolder, destFolder);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        } else {

            File resFolder = new File(appFolder, "app/src/main/assets");
            File assetsFolder = new File(destFolder, "app/src/main/assets");
            copyResToNative("Models", resFolder, assetsFolder);
            copyResToNative("audio", resFolder, assetsFolder);
            copyResToNative("fonts", resFolder, assetsFolder);
            copyResToNative("macro", resFolder, assetsFolder);
            //copyResToNative("probes", resFolder, assetsFolder);
            copyResToNative("skyboxes", resFolder, assetsFolder);
            copyResToNative("sprites", resFolder, assetsFolder);
            //copyResToNative("Textures", resFolder, assetsFolder);

            assetsFolder = new File(destFolder, "app/src/main/res");
            resFolder = new File(appFolder, "app/src/main/res");
            copyResToNative("raw", resFolder, assetsFolder);

        }

        return true;
    }

    private void copyResToNative(String type, File srcRoot, File targetRoot) {

        File src = new File(srcRoot, type);
        File target = new File(targetRoot, type);

        try {
            if (target.exists()) {
                FileUtils.deleteDirectory(target);
            }

            FileUtils.moveDirectory(src, target);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void extractProgramToNativeAppFolder(String zipPath, File nativeAppFolder) {

        File targetResFolder = new File(nativeAppFolder, "app/src/main/res");
        File targetAssetsFolder = new File(nativeAppFolder, "app/src/main/assets");
        File targetJavaFolder = new File(nativeAppFolder, "app/src/main/java/com/abware/test_mobile_app_eng");
        File codeFolder = new File(nativeAppFolder, "code");
        if (!codeFolder.exists()) {
            codeFolder.mkdir();
        }
        Util.unzip(zipPath, codeFolder.getAbsolutePath());

        try {
            File resFolder = new File(codeFolder, "export_res");
            FileUtils.copyDirectory(resFolder, targetAssetsFolder);
            FileUtils.deleteDirectory(resFolder);
            Util.zipFolder(codeFolder.getAbsolutePath());
            FileUtils.deleteDirectory(codeFolder);
            File codeFileZipped = new File(nativeAppFolder, "code.zip");
            FileUtils.moveFileToDirectory(codeFileZipped, new File(targetResFolder, "raw"), true);

            // deploy only code & resources?
            if (radioButtonCodeAndResOnly.isSelected()) {
                return;
            }

            // Full deploy

            // configure main activity code
            int isLandscape = radioLandscape.isSelected() ? 1 : 0;
            int joystick = 0;
            if (joystickLeft.isSelected()) {
                joystick = 1;
            } else if (joystickRight.isSelected()) {
                joystick = 2;
            } else if (joystickMiddle.isSelected()) {
                joystick = 3;
            }

            String packageName = txtPackagePrefix.getText().trim() + "." + txtPackageCompName.getText().trim() + "." + txtPackageProdName.getText().trim();
            packageName = packageName.replaceAll(" ", "_");
            File mainCodeFile = new File(targetJavaFolder, "MainActivity.java");
            String code = FileUtils.readFileToString(mainCodeFile, StandardCharsets.UTF_8);
            code = code.replace("${joystick}", "" + joystick);
            code = code.replace("${is_landscape}", "" + isLandscape);
            code = code.replace("${package_name}", packageName);
            FileUtils.writeStringToFile(mainCodeFile, code, StandardCharsets.UTF_8);

            // configure string resources values
            File stringResFile = new File(targetResFolder, "values/strings.xml");
            code = FileUtils.readFileToString(stringResFile, StandardCharsets.UTF_8);
            code = code.replace("${app_name}", txtAppName.getText().trim());
            code = code.replace("${game_name}", txtAppName.getText().trim());
            code = code.replace("${game_text1}", txtAppText1.getText().trim());
            FileUtils.writeStringToFile(stringResFile, code, StandardCharsets.UTF_8);

            // configure gradle settings file
            File targetGradleSettings = new File(nativeAppFolder, "settings.gradle");
            code = FileUtils.readFileToString(targetGradleSettings, StandardCharsets.UTF_8);
            code = code.replace("${project_name}", txtPackageProdName.getText().trim());
            FileUtils.writeStringToFile(targetGradleSettings, code, StandardCharsets.UTF_8);

            // android manifest
            File androidManifest = new File(nativeAppFolder, "app/src/main/AndroidManifest.xml");
            code = FileUtils.readFileToString(androidManifest, StandardCharsets.UTF_8);
            code = code.replace("${package_name}", packageName);
            FileUtils.writeStringToFile(androidManifest, code, StandardCharsets.UTF_8);

            // change code files path according to package name
            File mainJavaFolder = new File(nativeAppFolder, "app/src/main/java");
            File dir = new File(mainJavaFolder, "com");
            dir.renameTo(new File(mainJavaFolder, txtPackagePrefix.getText().trim()));
            dir = new File(mainJavaFolder, txtPackagePrefix.getText().trim() + "/abware");
            dir.renameTo(new File(mainJavaFolder, txtPackagePrefix.getText().trim() + "/" + txtPackageCompName.getText().trim()));
            dir = new File(mainJavaFolder, txtPackagePrefix.getText().trim() + "/" + txtPackageCompName.getText().trim() + "/test_mobile_app_eng");
            String folderName = txtPackagePrefix.getText().trim() + "/" + txtPackageCompName.getText().trim() + "/" + txtPackageProdName.getText().trim();
            folderName = folderName.replaceAll(" ", "_");
            dir.renameTo(new File(mainJavaFolder, folderName));

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private File extractAndroidBaseNativeApp() {

        File folder = new File(Util.getWorkingDir(), "export_targets");
        File zipFile = new File(folder, "android_native.zip");
        File currFolder = new File(folder, "android_native");
        if (currFolder.exists()) {
            try {
                FileUtils.deleteDirectory(currFolder);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        Util.unzip(zipFile.getAbsolutePath(), folder.getAbsolutePath());
        File andNative = new File(folder, "android_native");
        File build = new File(andNative, "app/build.gradle");
        try {
            String buildScript = FileUtils.readFileToString(build, StandardCharsets.UTF_8);
            String appId = txtPackagePrefix.getText().trim().replaceAll(" ", "_") + "." +
                    txtPackageCompName.getText().trim().replaceAll(" ", "_") + "." +
                    txtPackageProdName.getText().trim().replaceAll(" ", "_");
            buildScript = buildScript.replace("${eng-ver}", "v0.4.3").
                    replace("${app-id}", appId);
            FileUtils.writeStringToFile(build, buildScript, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return andNative;
    }

    public String getProjectFolder() {
        if (this.destFolder == null) {
            return null;
        }
        return this.destFolder.getAbsolutePath();
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
        panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1, true, false));
        panel1.add(panel2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        buttonOK = new JButton();
        buttonOK.setText("OK");
        panel2.add(buttonOK, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonCancel = new JButton();
        buttonCancel.setText("Cancel");
        panel2.add(buttonCancel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(7, 10, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        txtTargetFolder = new JTextField();
        Font txtTargetFolderFont = this.$$$getFont$$$(null, -1, 18, txtTargetFolder.getFont());
        if (txtTargetFolderFont != null) txtTargetFolder.setFont(txtTargetFolderFont);
        panel3.add(txtTargetFolder, new GridConstraints(2, 1, 1, 8, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel3.add(spacer2, new GridConstraints(5, 8, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("Save To:");
        panel3.add(label1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnFolderBrowse = new JButton();
        btnFolderBrowse.setText("Browse...");
        panel3.add(btnFolderBrowse, new GridConstraints(2, 9, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(2, 3, new Insets(0, 0, 0, 0), -1, -1));
        panel3.add(panel4, new GridConstraints(3, 1, 1, 8, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        txtPackagePrefix = new JFormattedTextField();
        Font txtPackagePrefixFont = this.$$$getFont$$$(null, -1, 18, txtPackagePrefix.getFont());
        if (txtPackagePrefixFont != null) txtPackagePrefix.setFont(txtPackagePrefixFont);
        txtPackagePrefix.setText("com");
        panel4.add(txtPackagePrefix, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, 1, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        txtPackageCompName = new JFormattedTextField();
        Font txtPackageCompNameFont = this.$$$getFont$$$(null, -1, 18, txtPackageCompName.getFont());
        if (txtPackageCompNameFont != null) txtPackageCompName.setFont(txtPackageCompNameFont);
        txtPackageCompName.setText("company_name");
        panel4.add(txtPackageCompName, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        txtPackageProdName = new JFormattedTextField();
        Font txtPackageProdNameFont = this.$$$getFont$$$(null, -1, 18, txtPackageProdName.getFont());
        if (txtPackageProdNameFont != null) txtPackageProdName.setFont(txtPackageProdNameFont);
        txtPackageProdName.setText("product_name");
        panel4.add(txtPackageProdName, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Prefix");
        panel4.add(label2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("Company Name");
        panel4.add(label3, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("Product Name");
        panel4.add(label4, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        panel3.add(panel5, new GridConstraints(5, 1, 1, 7, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        radioPortrait = new JRadioButton();
        radioPortrait.setSelected(true);
        radioPortrait.setText("Portrait");
        panel5.add(radioPortrait, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        radioLandscape = new JRadioButton();
        radioLandscape.setText("Landscape");
        panel5.add(radioLandscape, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer3 = new Spacer();
        panel5.add(spacer3, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JLabel label5 = new JLabel();
        label5.setText("View Config:");
        panel3.add(label5, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel6 = new JPanel();
        panel6.setLayout(new GridLayoutManager(1, 5, new Insets(0, 0, 0, 0), -1, -1));
        panel3.add(panel6, new GridConstraints(4, 1, 1, 6, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        joystickLeft = new JRadioButton();
        joystickLeft.setSelected(true);
        joystickLeft.setText("Left");
        panel6.add(joystickLeft, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        joystickRight = new JRadioButton();
        joystickRight.setText("Right");
        panel6.add(joystickRight, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        joystickMiddle = new JRadioButton();
        joystickMiddle.setText("Middle");
        panel6.add(joystickMiddle, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        joystickNone = new JRadioButton();
        joystickNone.setText("None");
        panel6.add(joystickNone, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer4 = new Spacer();
        panel6.add(spacer4, new GridConstraints(0, 4, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JLabel label6 = new JLabel();
        label6.setText("Virtual Joystick:");
        panel3.add(label6, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtAppName = new JFormattedTextField();
        Font txtAppNameFont = this.$$$getFont$$$(null, -1, 18, txtAppName.getFont());
        if (txtAppNameFont != null) txtAppName.setFont(txtAppNameFont);
        panel3.add(txtAppName, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label7 = new JLabel();
        label7.setText("Application Name:");
        panel3.add(label7, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtAppText1 = new JTextArea();
        txtAppText1.setText("Place here game credits, copyright notice, instructions \nor whatever text which suites your application");
        panel3.add(txtAppText1, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(150, 50), null, 0, false));
        final JLabel label8 = new JLabel();
        label8.setText("App general text:");
        panel3.add(label8, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel7 = new JPanel();
        panel7.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel3.add(panel7, new GridConstraints(6, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        radioButtonFullDeploy = new JRadioButton();
        radioButtonFullDeploy.setSelected(true);
        radioButtonFullDeploy.setText("Full Deploy");
        panel7.add(radioButtonFullDeploy, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        radioButtonCodeAndResOnly = new JRadioButton();
        radioButtonCodeAndResOnly.setText("Code And Resources Only");
        panel7.add(radioButtonCodeAndResOnly, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label9 = new JLabel();
        label9.setText("Deploy Type:");
        panel3.add(label9, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
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
