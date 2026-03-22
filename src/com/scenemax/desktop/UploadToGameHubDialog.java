package com.scenemax.desktop;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.apache.commons.lang3.RandomStringUtils;
import org.json.JSONException;
import org.json.JSONObject;
// ToolkitImage replaced with standard conversion for Java 11+ compatibility

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class UploadToGameHubDialog extends JDialog implements IMonitor {
    public static final int MAX_UPLOAD_GAME_SIZE = 80;
    private final String prg;
    private final String scriptPath;
    private JPanel contentPane;
    private JButton btnUpload;
    private JButton btnCancel;
    private JTextField txtGameId;
    private JProgressBar progressBar1;
    private JLabel lblProgress;
    private JTextField txtGameName;
    private JTextArea txtGameDesc;
    private JTextField txtGameWebsite;
    private JTextField txtGameClipUrl;
    private JComboBox cboGenre;
    private JTextField txtCreatorName;
    private JTextField txtCompany;
    private JRadioButton radioPublic;
    private JRadioButton radioPrivate;
    private JTextArea txtCredits;
    private JRadioButton radioPortrait;
    private JRadioButton radioLandscape;
    private JTextArea txtTOS;
    private JCheckBox chkTermsOfService;
    private JRadioButton radioTargetPC;
    private JRadioButton radioTargetAndroid;
    private JRadioButton radioTargetIOS;
    private JButton btnSetThumbnail;
    private JPanel picThumbnail;
    private JRadioButton joystickLeft;
    private JRadioButton joystickRight;
    private JRadioButton joystickMiddle;
    private JRadioButton joystickNone;
    UploadToGameHubTask uploadTask = null;
    ExportProgramToZipFileTask saveTask = null;
    private Integer resourcesHash;
    private boolean finalizing;
    private Image scaledPic;
    private JSONObject config;

    public UploadToGameHubDialog(final String scriptPath, final String prg) {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(btnUpload);

        this.scriptPath = scriptPath;
        this.prg = prg;

        String hubId = getProgramHubId();
        txtGameId.setText(hubId);

        btnUpload.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String errors = "";
                String txt = txtGameName.getText().trim();
                if (txt.length() < 8) {
                    errors += "Title - Should be at least 8 characters\r\n";
                }

                if (txt.length() > 25) {
                    errors += "Title - Should be no more than 25 characters\r\n";
                }

                txt = txtCreatorName.getText().trim();
                if (txt.length() < 6) {
                    errors += "Creator Name - Should be at least 6 characters\r\n";
                }

                if (txt.length() > 15) {
                    errors += "Creator Name - Should be no more than 15 characters\r\n";
                }

                txt = txtGameDesc.getText().trim();
                if (txt.length() > 1024) {
                    errors += "Description - Should be no more than 1024 characters\r\n";
                }

                txt = txtCredits.getText().trim();
                if (txt.length() > 1024) {
                    errors += "Credits - Should be no more than 1024 characters\r\n";
                }

                if (!chkTermsOfService.isSelected()) {
                    errors += "Terms Of Service - must be checked\r\n";
                }

                if (errors.length() > 0) {
                    JOptionPane.showMessageDialog(null, errors, "Missing Game Details", JOptionPane.WARNING_MESSAGE);
                } else {

                    uploadProgram();
                }
            }
        });

        btnCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

        chkTermsOfService.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                txtTOS.setEnabled(chkTermsOfService.isSelected());
            }
        });

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        ButtonGroup bg = new ButtonGroup();
        bg.add(radioPortrait);
        bg.add(radioLandscape);

        bg = new ButtonGroup();
        bg.add(radioPrivate);
        bg.add(radioPublic);

        bg = new ButtonGroup();
        bg.add(radioTargetPC);
        bg.add(radioTargetAndroid);
        bg.add(radioTargetIOS);

        bg = new ButtonGroup();
        bg.add(joystickLeft);
        bg.add(joystickRight);
        bg.add(joystickMiddle);
        bg.add(joystickNone);


        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);


        btnSetThumbnail.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {

                String userhome = System.getProperty("user.home");
                JFileChooser jfc = null;

                jfc = new JFileChooser(userhome + "/Downloads");
                jfc.setFileFilter(new FileFilter() {

                    public String getDescription() {
                        return "Images Files (*.jpg, *.png)";
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

                jfc.setFileSelectionMode(JFileChooser.FILES_ONLY);

                int returnValue = jfc.showOpenDialog(null);

                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    String path = jfc.getCurrentDirectory() + "//" + jfc.getSelectedFile().getName();
                    showProductPic(path);
                    File f = prepareProductImage();
                    AppDB.getInstance().setParam(txtGameId.getText() + "_thumbnail", f.getAbsolutePath());
                }

            }
        });

        showGameThumbnail();
        showScriptUploadConfig();
    }

    private void showScriptUploadConfig() {

        JSONObject config = Util.getScriptJsonParams(scriptPath);
        this.config = config;
        try {
            txtGameName.setText(config.getString("name"));
            txtGameDesc.setText(config.getString("desc"));
            cboGenre.setSelectedIndex(config.getInt("genre"));
            txtCreatorName.setText(config.getString("owner_name"));
            txtCompany.setText(config.getString("owner_company"));
            txtGameWebsite.setText(config.getString("game_website"));
            txtGameClipUrl.setText(config.getString("movie_link"));
            txtCredits.setText(config.getString("credentials"));
            radioPublic.setSelected(config.getInt("is_public") == 1);
            radioLandscape.setSelected(config.getInt("is_landscape") == 1);
            radioTargetAndroid.setSelected(config.getInt("target_device") == 2);

            if (config.has("joystick")) {
                int joystick = config.getInt("joystick");
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


        } catch (JSONException e) {
            // some fields are missing
        }
    }

    private void showGameThumbnail() {
        String path = AppDB.getInstance().getParam(txtGameId.getText() + "_thumbnail");
        if (path != null && path.length() > 0) {
            showProductPic(path);
        }
    }

    protected void showProductPic(String path) {

        try {
            BufferedImage myPicture = null;
            try {
                myPicture = ImageIO.read(new File(path));
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            scaledPic = myPicture.getScaledInstance(300, 300, Image.SCALE_FAST);
            JLabel picLabel = new JLabel(new ImageIcon(scaledPic));
            picThumbnail.removeAll();
            picThumbnail.add(picLabel);
            picThumbnail.updateUI();


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void uploadProgram() {

        btnUpload.setEnabled(false);
        btnCancel.setEnabled(false);

        String stationId = Util.getStationId();

        JSONObject cmd = new JSONObject();
        cmd.put("id", txtGameId.getText());

        String encBegin = Util.encStrGetBegin(txtGameName.getText());
        String encEnd = Util.encStrGetEnd(txtGameName.getText());

        cmd.put("name", txtGameName.getText());
        cmd.put("desc", txtGameDesc.getText());
        cmd.put("genre", cboGenre.getSelectedIndex());
        cmd.put("station_id", encBegin + "-" + stationId + "-" + encEnd);
        cmd.put("studio_ver", Util.getAppVersion());
        cmd.put("owner_name", txtCreatorName.getText());
        cmd.put("owner_company", txtCompany.getText());
        cmd.put("game_website", txtGameWebsite.getText());
        cmd.put("movie_link", txtGameClipUrl.getText());

        String thumbName = null;
        String thumbFile = AppDB.getInstance().getParam(txtGameId.getText() + "_thumbnail");
        if (thumbFile != null && thumbFile.length() > 0) {
            File thumb = new File(thumbFile);
            thumbName = thumb.getName();
            cmd.put("thumbnail", thumbName);
        }

        cmd.put("is_public", radioPublic.isSelected() ? 1 : 0);
        cmd.put("credits", txtCredits.getText());
        cmd.put("is_landscape", radioLandscape.isSelected() ? 1 : 0);
        cmd.put("target_device", radioTargetAndroid.isSelected() ? 2 : 1);// 1 - PC , 2 - Android , 3 - iOS

        int joystick = 0;
        if (joystickLeft.isSelected()) {
            joystick = 1;
        } else if (joystickRight.isSelected()) {
            joystick = 2;
        }
        if (joystickMiddle.isSelected()) {
            joystick = 3;
        }

        //////////// EXTRA PARAMS ////////////
        JSONObject extraParams = new JSONObject();
        extraParams.put("joystick", joystick);

        cmd.put("extra_params", extraParams);

        //////////////////////////////////////


        // first check that this station is registered

        boolean stationExists = false;
        String res = Util.doHttpGetRequest2(LicenseService.getGameHubServer() + "/station", new String[]{"station_id", stationId});
        if (res != null && !res.equals("null")) {
            JSONObject obj = new JSONObject(res);
            stationExists = true;
        } else {
            if (res == null) {
                JOptionPane.showMessageDialog(null, "Failed to check station. Check your internet connection");
                return;
            }

            String stationName = (String) JOptionPane.showInputDialog(
                    null,
                    "Give this work station an online name",
                    "Work Station Registration",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    null,
                    txtCreatorName.getText());

            // create station in Game Hub
            JSONObject station = new JSONObject();
            station.put("id", Util.encStr(stationId));//
            station.put("device_type", 1);
            station.put("studio_ver", Util.getAppVersion());
            station.put("owner_name", stationName);
            res = Util.doHttpPostRequest2(LicenseService.getGameHubServer() + "/station", station.toString(), null);
            if (res != null && !res.equals("null")) {
                JSONObject obj = new JSONObject(res);
                if (obj.has("allowed")) {
                    stationExists = true;
                }
            } else {
                JOptionPane.showMessageDialog(null, "Failed to create station");
                return;
            }
        }

        if (!stationExists) {
            JOptionPane.showMessageDialog(null, "Failed to validate station. Check your internet connection");
            return;

        }


        // create or update game record in Game Hub
        res = Util.doHttpPostRequest2(LicenseService.getGameHubServer() + "/games", cmd.toString(4), null);
        if (res != null && !res.equals("null")) {
            JSONObject obj = new JSONObject(res);
            if (obj.has("err")) {
                int err = obj.getInt("err");
                if (err == 240) {
                    JOptionPane.showMessageDialog(null, "You have exceeded the maximum amount of game uploads\r\n" +
                            "Please contact: " + Util.getAppEmail() + " to extend your quota.\r\n" +
                            "Station ID: " + stationId);
                    return;
                } else {
                    JOptionPane.showMessageDialog(null, "Failed to upload game.");
                    return;
                }
            }

        }

        String finalThumbName = thumbName;
        uploadFullProgram(new Callback() {

            @Override
            public void run(Object res) {

                if (res instanceof JSONObject) {
                    JSONObject data = (JSONObject) res;
                    if (data.has("err")) {
                        String msg = data.getString("err");
                        cmd.put("is_public", 0);
                        res = Util.doHttpPostRequest2(LicenseService.getGameHubServer() + "/games", cmd.toString(4), null);
                        JOptionPane.showMessageDialog(null, msg);
                        return;
                    }

                }

                finalizing = true;
                uploadCodeOnly(resourcesHash, new Callback() {
                    @Override
                    public void run(Object res) {

                        if (finalThumbName != null) {
                            uploadThumbnail(finalThumbName, new Callback() {

                                @Override
                                public void run(Object res) {
                                    finalizeUploadAndUpdateConfig();
                                }
                            });
                        } else {
                            finalizeUploadAndUpdateConfig();
                        }

                    }
                });

            }
        });

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
        config.put("name", txtGameName.getText());
        config.put("desc", txtGameDesc.getText());
        config.put("genre", cboGenre.getSelectedIndex());
        config.put("owner_name", txtCreatorName.getText());
        config.put("owner_company", txtCompany.getText());
        config.put("game_website", txtGameWebsite.getText());
        config.put("movie_link", txtGameClipUrl.getText());
        config.put("thumbnail", txtGameId.getText() + ".png");
        config.put("is_public", radioPublic.isSelected() ? 1 : 0);
        config.put("credentials", txtCredits.getText());
        config.put("is_landscape", radioLandscape.isSelected() ? 1 : 0);
        config.put("target_device", radioTargetAndroid.isSelected() ? 2 : 1);// 1 - PC , 2 - Android , 3 - iOS

        int joystick = 0;
        if (joystickLeft.isSelected()) {
            joystick = 1;
        } else if (joystickRight.isSelected()) {
            joystick = 2;
        }
        if (joystickMiddle.isSelected()) {
            joystick = 3;
        }
        config.put("joystick", joystick);
        config.put("resources_hash", resourcesHash);

        Util.setScriptJsonParams(scriptPath, config);
    }

    private void uploadThumbnail(String thumbFileName, Callback done) {

        final String zipFilePath = "./tmp/" + thumbFileName;
        //SFTPProgressMonitor monitor = new SFTPProgressMonitor(UploadToGameHubDialog.this);
        uploadTask = new UploadToGameHubTask(zipFilePath, this, done);
        uploadTask.execute();
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

                //SFTPProgressMonitor monitor = new SFTPProgressMonitor(UploadToGameHubDialog.this);
                uploadTask = new UploadToGameHubTask(zipFilePath, UploadToGameHubDialog.this, done);
                uploadTask.execute();

            }
        });

        saveTask.execute();

    }

    private void uploadFullProgram(Callback done) {

        String targetPathSelector = null;
        String userhome = System.getProperty("user.home");
        targetPathSelector = userhome + "\\Documents";

        final String targetPath = targetPathSelector;
        String hubId = txtGameId.getText();
        final String zipFilePath = targetPath + "/" + hubId + ".zip";

        //SFTPProgressMonitor monitor = new SFTPProgressMonitor(this);

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
                if (size > MAX_UPLOAD_GAME_SIZE) {
                    JSONObject err = new JSONObject();
                    err.put("err", "File size exceeds limitation.\r\nYour game's file size is: " + size +
                            "MB\r\nMax file size should not exceed " + MAX_UPLOAD_GAME_SIZE + "MB\r\n" +
                            "Optimize your game's assets and try uploading again");
                    done.run(err);
                    return;
                }

                uploadTask = new UploadToGameHubTask(zipFilePath, UploadToGameHubDialog.this, done);
                uploadTask.execute();

            }
        });

        saveTask.execute();

    }

    private String getProgramHubId() {

        JSONObject params = Util.getScriptJsonParams(scriptPath);
        String hubId = null;

        if (params.has("hubId")) {
            hubId = params.getString("hubId");
        }

        if (hubId == null || hubId.length() == 0) {
            hubId = RandomStringUtils.random(8, "0123456789abcdef");
            params.put("hubId", hubId);
            Util.setScriptJsonParams(scriptPath, params);
        }

        return hubId;
    }

    private void onOK() {
        // add your code here
        dispose();
    }

    private void onCancel() {
        // add your code here if necessary
        dispose();
    }

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

    private File prepareProductImage() {

        if (this.scaledPic == null) {
            return null;
        }

        try {

            String thumbCounter = AppDB.getInstance().getParam("thumbnail_counter");
            if (thumbCounter == null || thumbCounter.length() == 0) {
                thumbCounter = "0";
            }

            int thumbnailCounter = Integer.parseInt(thumbCounter);
            thumbnailCounter++;
            AppDB.getInstance().setParam("thumbnail_counter", "" + thumbnailCounter);

            String path = "./tmp";
            String name = txtGameId.getText() + "-" + thumbnailCounter + ".png";

            BufferedImage bi;
            if (this.scaledPic instanceof BufferedImage) {
                bi = (BufferedImage) this.scaledPic;
            } else {
                bi = new BufferedImage(this.scaledPic.getWidth(null), this.scaledPic.getHeight(null), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2d = bi.createGraphics();
                g2d.drawImage(this.scaledPic, 0, 0, null);
                g2d.dispose();
            }
            File outputfile = new File(path + "/" + name);
            ImageIO.write(bi, "png", outputfile);
            return outputfile;
        } catch (IOException e) {

        }

        return null;

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
        contentPane.setLayout(new GridLayoutManager(5, 4, new Insets(10, 10, 10, 10), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel1, new GridConstraints(4, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1, true, false));
        panel1.add(panel2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        btnUpload = new JButton();
        btnUpload.setEnabled(true);
        btnUpload.setText("Deploy");
        panel2.add(btnUpload, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnCancel = new JButton();
        btnCancel.setText("Cancel");
        panel2.add(btnCancel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(13, 12, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel3, new GridConstraints(1, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("Title:");
        panel3.add(label1, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtGameName = new JTextField();
        panel3.add(txtGameName, new GridConstraints(1, 7, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        txtGameWebsite = new JTextField();
        panel3.add(txtGameWebsite, new GridConstraints(8, 7, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Web Site:");
        panel3.add(label2, new GridConstraints(8, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtGameClipUrl = new JTextField();
        panel3.add(txtGameClipUrl, new GridConstraints(9, 7, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("Demo Clip:");
        panel3.add(label3, new GridConstraints(9, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("Genre:");
        panel3.add(label4, new GridConstraints(7, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        cboGenre = new JComboBox();
        final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
        defaultComboBoxModel1.addElement("Action");
        defaultComboBoxModel1.addElement("Racing");
        defaultComboBoxModel1.addElement("Adventure");
        defaultComboBoxModel1.addElement("Fighting");
        defaultComboBoxModel1.addElement("Shooter");
        defaultComboBoxModel1.addElement("Educational");
        defaultComboBoxModel1.addElement("Platformer");
        defaultComboBoxModel1.addElement("Role Playing");
        defaultComboBoxModel1.addElement("Simulation");
        defaultComboBoxModel1.addElement("Sports");
        defaultComboBoxModel1.addElement("Strategy");
        defaultComboBoxModel1.addElement("Puzzle");
        defaultComboBoxModel1.addElement("Other");
        cboGenre.setModel(defaultComboBoxModel1);
        panel3.add(cboGenre, new GridConstraints(7, 7, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label5 = new JLabel();
        label5.setText("Creator Name:");
        panel3.add(label5, new GridConstraints(10, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtCreatorName = new JTextField();
        panel3.add(txtCreatorName, new GridConstraints(10, 7, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label6 = new JLabel();
        label6.setText("Company:");
        panel3.add(label6, new GridConstraints(11, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtCompany = new JTextField();
        panel3.add(txtCompany, new GridConstraints(11, 7, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label7 = new JLabel();
        label7.setText("Deploy Type:");
        panel3.add(label7, new GridConstraints(2, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(2, 3, new Insets(0, 0, 0, 0), -1, -1));
        panel3.add(panel4, new GridConstraints(2, 7, 1, 5, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        radioPublic = new JRadioButton();
        radioPublic.setText("Public");
        panel4.add(radioPublic, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel4.add(spacer2, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        radioPrivate = new JRadioButton();
        radioPrivate.setSelected(true);
        radioPrivate.setText("Private");
        panel4.add(radioPrivate, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer3 = new Spacer();
        panel4.add(spacer3, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JScrollPane scrollPane1 = new JScrollPane();
        panel3.add(scrollPane1, new GridConstraints(6, 7, 1, 5, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        txtGameDesc = new JTextArea();
        txtGameDesc.setRows(5);
        scrollPane1.setViewportView(txtGameDesc);
        final JLabel label8 = new JLabel();
        label8.setText("Description:");
        panel3.add(label8, new GridConstraints(6, 1, 1, 6, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JScrollPane scrollPane2 = new JScrollPane();
        panel3.add(scrollPane2, new GridConstraints(12, 7, 1, 5, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        txtCredits = new JTextArea();
        txtCredits.setRows(5);
        scrollPane2.setViewportView(txtCredits);
        final JLabel label9 = new JLabel();
        label9.setText("Credits:");
        panel3.add(label9, new GridConstraints(12, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        panel3.add(panel5, new GridConstraints(4, 7, 1, 5, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        radioPortrait = new JRadioButton();
        radioPortrait.setSelected(true);
        radioPortrait.setText("Portrait");
        panel5.add(radioPortrait, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        radioLandscape = new JRadioButton();
        radioLandscape.setText("Landscape");
        panel5.add(radioLandscape, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer4 = new Spacer();
        panel5.add(spacer4, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JLabel label10 = new JLabel();
        label10.setText("View Config:");
        panel3.add(label10, new GridConstraints(4, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label11 = new JLabel();
        label11.setText("Target Platform:");
        panel3.add(label11, new GridConstraints(3, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel6 = new JPanel();
        panel6.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel3.add(panel6, new GridConstraints(3, 7, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        radioTargetPC = new JRadioButton();
        radioTargetPC.setEnabled(true);
        radioTargetPC.setText("PC");
        panel6.add(radioTargetPC, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer5 = new Spacer();
        panel3.add(spacer5, new GridConstraints(3, 10, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        radioTargetAndroid = new JRadioButton();
        radioTargetAndroid.setSelected(true);
        radioTargetAndroid.setText("Android");
        panel3.add(radioTargetAndroid, new GridConstraints(3, 8, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        radioTargetIOS = new JRadioButton();
        radioTargetIOS.setEnabled(false);
        radioTargetIOS.setText("iOS");
        panel3.add(radioTargetIOS, new GridConstraints(3, 9, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel7 = new JPanel();
        panel7.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel3.add(panel7, new GridConstraints(0, 7, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        txtGameId = new JTextField();
        txtGameId.setEditable(false);
        txtGameId.setEnabled(true);
        Font txtGameIdFont = this.$$$getFont$$$("Courier New", Font.BOLD, 28, txtGameId.getFont());
        if (txtGameIdFont != null) txtGameId.setFont(txtGameIdFont);
        panel7.add(txtGameId, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        progressBar1 = new JProgressBar();
        panel7.add(progressBar1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        lblProgress = new JLabel();
        lblProgress.setText("");
        panel7.add(lblProgress, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label12 = new JLabel();
        label12.setText("Game ID:");
        panel3.add(label12, new GridConstraints(0, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label13 = new JLabel();
        label13.setText("Virtual Joystick:");
        panel3.add(label13, new GridConstraints(5, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel8 = new JPanel();
        panel8.setLayout(new GridLayoutManager(1, 5, new Insets(0, 0, 0, 0), -1, -1));
        panel3.add(panel8, new GridConstraints(5, 7, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        joystickLeft = new JRadioButton();
        joystickLeft.setSelected(true);
        joystickLeft.setText("Left");
        panel8.add(joystickLeft, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        joystickRight = new JRadioButton();
        joystickRight.setText("Right");
        panel8.add(joystickRight, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        joystickMiddle = new JRadioButton();
        joystickMiddle.setText("Middle");
        panel8.add(joystickMiddle, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        joystickNone = new JRadioButton();
        joystickNone.setText("None");
        panel8.add(joystickNone, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer6 = new Spacer();
        panel8.add(spacer6, new GridConstraints(0, 4, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JLabel label14 = new JLabel();
        Font label14Font = this.$$$getFont$$$(null, Font.BOLD, 24, label14.getFont());
        if (label14Font != null) label14.setFont(label14Font);
        label14.setForeground(new Color(-65536));
        label14.setText("*");
        panel3.add(label14, new GridConstraints(10, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label15 = new JLabel();
        Font label15Font = this.$$$getFont$$$(null, Font.BOLD, 24, label15.getFont());
        if (label15Font != null) label15.setFont(label15Font);
        label15.setForeground(new Color(-65536));
        label15.setText("*");
        panel3.add(label15, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtTOS = new JTextArea();
        txtTOS.setEditable(false);
        txtTOS.setEnabled(false);
        txtTOS.setRows(8);
        txtTOS.setText("You affirm that you have the right to upload this game, \n and that users can use your content without violating anybody else’s rights.\nRemoval and termination of content & accounts may occur without prior notice in case of:\nSpamming or sending repeated content or any unsolicited messages.\nPosting unlawful, misleading, malicious, or discriminatory content;\nPosting content that promotes or participates in racial intolerance, \nsexism, hate crimes, hate speech, or intolerance to any group of individuals;\nViolating copyright, trademark or other intellectual property or other legal rights of others\n by posting content without permission to distribute such content;\nViolating any applicable laws or regulations.");
        contentPane.add(txtTOS, new GridConstraints(3, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(150, 50), null, 0, false));
        chkTermsOfService = new JCheckBox();
        Font chkTermsOfServiceFont = this.$$$getFont$$$(null, Font.BOLD, -1, chkTermsOfService.getFont());
        if (chkTermsOfServiceFont != null) chkTermsOfService.setFont(chkTermsOfServiceFont);
        chkTermsOfService.setSelected(false);
        chkTermsOfService.setText("Terms Of Service:");
        contentPane.add(chkTermsOfService, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel9 = new JPanel();
        panel9.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel9, new GridConstraints(1, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        picThumbnail = new JPanel();
        picThumbnail.setLayout(new BorderLayout(0, 0));
        picThumbnail.setBackground(new Color(-5921371));
        panel9.add(picThumbnail, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(300, 300), null, 0, false));
        btnSetThumbnail = new JButton();
        btnSetThumbnail.setText("Load Game Thumbnail...");
        panel9.add(btnSetThumbnail, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer7 = new Spacer();
        panel9.add(spacer7, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JLabel label16 = new JLabel();
        Font label16Font = this.$$$getFont$$$(null, Font.BOLD, 24, label16.getFont());
        if (label16Font != null) label16.setFont(label16Font);
        label16.setForeground(new Color(-65536));
        label16.setText("*");
        contentPane.add(label16, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
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
