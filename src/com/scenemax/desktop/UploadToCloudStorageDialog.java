package com.scenemax.desktop;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.json.JSONException;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class UploadToCloudStorageDialog extends JDialog implements IMonitor {

    private int mode = 0;
    private String scriptPath;
    private String prg;
    private String hubId;

    private JPanel contentPane;
    private JButton btnUpload;
    private JButton btnCancel;
    private JTextField txtGameId;
    private JProgressBar progressBar1;
    private JLabel lblProgress;

    UploadToGameHubTask uploadTask = null;
    ExportProgramToZipFileTask saveTask = null;
    private Integer resourcesHash;
    private boolean finalizing;
    private JSONObject config;

    public UploadToCloudStorageDialog(String gameId) {

        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(btnUpload);

        if (gameId == null || gameId.length() == 0) {
            gameId = AppDB.getInstance().getParam("last_imported_program_from_cloud");
            if (gameId == null) {
                gameId = "";
            }
        }

        txtGameId.setText(gameId);
        this.mode = 1;
        btnUpload.setText("Download");
        initForm();
    }

    public UploadToCloudStorageDialog(final String scriptPath, final String prg) {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(btnUpload);

        this.scriptPath = scriptPath;
        this.prg = prg;

        this.hubId = Util.getProgramHubId(scriptPath);
        txtGameId.setText(hubId);

        this.config = Util.getScriptJsonParams(scriptPath);
        initForm();
    }

    private void initForm() {
        btnUpload.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });

        btnCancel.addActionListener(new ActionListener() {
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
    }

    private void onOK() {
        if (this.mode == 0) {
            uploadProgram();
        } else {
            downloadProgram();
        }
    }

    private void downloadProgram() {
        btnUpload.setEnabled(false);
        btnCancel.setEnabled(false);

        lblProgress.setText("Downloading...");

        progressBar1.setValue(0);//initially progress is 0
        progressBar1.setMaximum(100);//sets the maximum value 100

        this.hubId = txtGameId.getText().trim();
        String gameId = this.hubId;
        String resHash = "";
        JSONObject conf = getGameConfig(gameId);
        try {
            resHash = conf.getString("resourcesHash");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        String finalResHash = resHash;

        downloadCodeOnly(this.hubId, new Callback() {
            @Override
            public void run(Object res) {

                boolean downloadFullProgram = false;
                String resourcesHash = "";
                //String targetScriptPath = null;

                JSONObject retVal = (JSONObject) res;
                try {
                    //targetScriptPath = retVal.getString("targetScriptPath");
                    resourcesHash = retVal.getString("resourcesHash");
                    if (finalResHash.length() == 0 || resourcesHash.length() == 0 ||
                            !resourcesHash.equals(finalResHash)) {
                        downloadFullProgram = true;
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                if (downloadFullProgram) {

                    String finalResourcesHash = resourcesHash;

                    downloadFullProgram(gameId, new Callback() {
                        @Override
                        public void run(Object res) {

                            String targetScriptPath = null;
                            JSONObject retVal = (JSONObject) res;
                            try {
                                if (retVal.has("targetScriptPath")) {
                                    targetScriptPath = retVal.getString("targetScriptPath");
                                }

                            } catch (JSONException e) {
                                e.printStackTrace();
                            }

                            AppDB.getInstance().setParam("last_imported_program_from_cloud", gameId);

                            try {
                                conf.put("resourcesHash", finalResourcesHash);
                                if (targetScriptPath != null) {
                                    conf.put("targetScriptPath", targetScriptPath);
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            AppDB.getInstance().setParam(gameId, conf.toString());
                            UploadToCloudStorageDialog.this.dispose();

                        }
                    });
                } else {
                    AppDB.getInstance().setParam("last_imported_program_from_cloud", gameId);
                    UploadToCloudStorageDialog.this.dispose();
                }

            }
        });


    }


    private void downloadFullProgram(String gameId, Callback done) {

        String userhome = System.getProperty("user.home");
        String path = userhome + "\\Downloads";
        File f = new File(path);

        final File gameFile = new File(f, gameId + ".zip");

        new BackBlazeDownloadTask(gameId + ".zip", gameFile, this, new Runnable() {
            @Override
            public void run() {

                if (gameFile.exists()) {
                    ImportProgramZipFileTask importTask = new ImportProgramZipFileTask(gameFile.getAbsolutePath(), done);
                    importTask.setImportResourcesOnly(true);
                    importTask.run();

                }

            }
        }).execute();


    }


    private void downloadCodeOnly(String gameId, Callback done) {

        String userhome = System.getProperty("user.home");
        String path = userhome + "\\Downloads";

        File f = new File(path);
        final File gameFile = new File(f, gameId + "_code.zip");

        new BackBlazeDownloadTask(gameId + "_code.zip", gameFile, null, new Runnable() {
            @Override
            public void run() {

                if (gameFile.exists()) {
                    ImportProgramZipFileTask importTask = new ImportProgramZipFileTask(gameFile.getAbsolutePath(), done);
                    importTask.run();

                }

            }
        }).execute();
    }

    private JSONObject getGameConfig(String gameId) {
        SceneMaxProject p = Util.getActiveProject();
        String gameKey = p.name + "_" + gameId;
        String s = AppDB.getInstance().getParam(gameKey);
        JSONObject conf = null;
        try {
            if (s != null) {
                conf = new JSONObject(s);
            } else {
                conf = new JSONObject();
                conf.put("resourcesHash", "");
                AppDB.getInstance().setParam(gameKey, conf.toString());
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

        return conf;
    }


    private void uploadProgram() {

        btnUpload.setEnabled(false);
        btnCancel.setEnabled(false);

        uploadFullProgram(new Callback() {

            @Override
            public void run(Object res) {

                Integer resourcesHash = null;

                if (res instanceof JSONObject) {
                    JSONObject data = (JSONObject) res;
                    if (data.has("err")) {
                        String msg = data.getString("err");
                        JOptionPane.showMessageDialog(null, msg);
                        return;
                    }

                } else if (res instanceof Integer) {
                    resourcesHash = (Integer) res;
                }


                finalizing = true;
                uploadCodeOnly(resourcesHash, new Callback() {
                    @Override
                    public void run(Object res) {

                        finalizeUploadAndUpdateConfig();

                    }

                });

            }


        });

    }

    private void uploadFullProgram(Callback done) {

        String targetPathSelector = null;
        String userhome = System.getProperty("user.home");
        targetPathSelector = userhome + "\\Documents";

        final String targetPath = targetPathSelector;
        String hubId = txtGameId.getText();
        final String zipFilePath = targetPath + "/" + hubId + ".zip";

        // Upload full program
        saveTask = new ExportProgramToZipFileTask(this.scriptPath, this.prg, targetPath, hubId,
                ExportProgramToZipFileTask.EXPORT_TYPE_FULL, null, false,
                ExportProgramToZipFileTask.TARGET_DEVICE_ANDROID, new Runnable() {

            @Override
            public void run() {

                try {
                    resourcesHash = saveTask.get();
                    if (config.has("resources_hash")) {
                        int resHash = config.getInt("resources_hash");
                        if (resHash == resourcesHash) {
                            // same resources, no need to upload the full program
                            done.run(null);
                            return;
                        }
                    }

                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }

                // no more than 50MB files
                File checkFile = new File(zipFilePath);
                long size = checkFile.length() / 1024000;
                if (size > Util.MAX_UPLOAD_GAME_SIZE) {
                    JSONObject err = new JSONObject();
                    err.put("err", "File size exceeds limitation.\r\nYour game's file size is: " + size +
                            "MB\r\nMax file size should not exceed " + Util.MAX_UPLOAD_GAME_SIZE + "MB\r\n" +
                            "Optimize your game's assets and try uploading again");
                    done.run(err);
                    return;
                }

                uploadTask = new UploadToGameHubTask(zipFilePath, UploadToCloudStorageDialog.this, done);
                uploadTask.execute();

            }
        });

        saveTask.execute();

    }

    protected void finalizeUploadAndUpdateConfig() {
        try {
            if (uploadTask.get()) {
                btnCancel.setText("OK");
                btnCancel.setEnabled(true);
                updateScriptConfig();
            } else {
                JOptionPane.showMessageDialog(null, "Upload Failed");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    private void updateScriptConfig() {

        JSONObject config = Util.getScriptJsonParams(scriptPath);
        config.put("resources_hash", resourcesHash);

        Util.setScriptJsonParams(scriptPath, config);
    }

    private void uploadCodeOnly(Integer resHash, Callback done) {

        String targetPathSelector = null;
        String userhome = System.getProperty("user.home");
        targetPathSelector = userhome + "\\Documents";

        final String targetPath = targetPathSelector;
        String hubId = txtGameId.getText() + "_code";
        final String zipFilePath = targetPath + "/" + hubId + ".zip";


        // Upload full program
        ExportProgramToZipFileTask saveTask = new ExportProgramToZipFileTask(this.scriptPath, this.prg,
                targetPath, hubId,
                ExportProgramToZipFileTask.EXPORT_TYPE_CODE_ONLY, resHash, false,
                ExportProgramToZipFileTask.TARGET_DEVICE_ANDROID, new Runnable() {

            @Override
            public void run() {

                uploadTask = new UploadToGameHubTask(zipFilePath, UploadToCloudStorageDialog.this, done);
                uploadTask.execute();

            }
        });

        saveTask.execute();

    }

    private void onCancel() {
        // add your code here if necessary
        dispose();
    }


    ////////////////// IMONITOR ///////////

    @Override
    public void setNote(String note) {

        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    if (!finalizing) {
                        lblProgress.setText(note);
                    }
                }
            });
            return;
        }
    }

    @Override
    public void setProgress(int progress) {

        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    progressBar1.setValue(progress);
                }
            });
            return;
        }

    }

    @Override
    public void onEnd() {
        if (finalizing) {
            lblProgress.setText("Deploy Finished Successfully");
        } else {
            lblProgress.setText("Finalizing Deploy... Please Wait");
        }
    }

    ///////////////////////////////////////////////////


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
        contentPane.setLayout(new GridLayoutManager(3, 1, new Insets(10, 10, 10, 10), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1, true, false));
        panel1.add(panel2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        btnUpload = new JButton();
        btnUpload.setText("Upload");
        panel2.add(btnUpload, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnCancel = new JButton();
        btnCancel.setText("Cancel");
        panel2.add(btnCancel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel3, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel3.add(spacer2, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel4, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(500, -1), null, 0, false));
        txtGameId = new JTextField();
        Font txtGameIdFont = this.$$$getFont$$$("Courier New", Font.BOLD, 28, txtGameId.getFont());
        if (txtGameIdFont != null) txtGameId.setFont(txtGameIdFont);
        panel4.add(txtGameId, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        progressBar1 = new JProgressBar();
        panel4.add(progressBar1, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("Game ID:");
        panel4.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        lblProgress = new JLabel();
        lblProgress.setText("...");
        panel4.add(lblProgress, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
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
