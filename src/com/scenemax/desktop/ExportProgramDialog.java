package com.scenemax.desktop;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.json.JSONObject;
// ToolkitImage replaced with standard conversion for Java 11+ compatibility

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

import static javax.swing.JOptionPane.YES_OPTION;

public class ExportProgramDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonSave;
    private JButton buttonCancel;
    private JTextField textTargetFolder;
    private JButton selectButton;
    private JTextField txtName;
    private JTextField txtSku;
    private JTextPane txtDesc;
    private JCheckBox chkVisibleOnWebSite;
    private JLabel lblGeneralHeader;
    private JPanel picPanel;
    private JPanel uploadToWebPanel;
    private JCheckBox chkExportResourceOnly;
    private Image scaledPic;
    private ExportProgramToZipFileTask saveTask;
    private boolean uploadMode = false;


    public ExportProgramDialog(final String scriptPath, final String prg, boolean uploadMode) {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonSave);

        this.uploadMode = uploadMode;
        String userhome = System.getProperty("user.home");
        textTargetFolder.setText(userhome + "\\Documents");


        if (this.uploadMode) {

            textTargetFolder.setText("");
            lblGeneralHeader.setText("Select Product Picture:");
            buttonSave.setText("Upload To Web Site");
            selectButton.setText("Select..");
            JSONObject params = Util.getScriptJsonParams(scriptPath);

            if (params.has("webVisible")) {
                chkVisibleOnWebSite.setSelected(params.getString("webVisible").equals("visible"));
            }

            if (params.has("webName")) {
                txtName.setText(params.getString("webName"));
            }

            if (params.has("webDesc")) {
                txtDesc.setText(params.getString("webDesc"));
            }

            if (params.has("webSku")) {
                txtSku.setText(params.getString("webSku"));
            }

            if (params.has("localFilePic")) {
                textTargetFolder.setText(params.getString("localFilePic"));
                showProductPic();
            }

        } else {
            uploadToWebPanel.setVisible(false);
        }


        buttonSave.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                buttonSave.setEnabled(false);
                buttonCancel.setEnabled(false);

                String targetPathSelector = null;

                if (ExportProgramDialog.this.uploadMode) {
                    String userhome = System.getProperty("user.home");
                    targetPathSelector = userhome + "\\Documents";

                } else {
                    targetPathSelector = textTargetFolder.getText();
                }

                final String targetPath = targetPathSelector;
                final String zipFilePath = targetPath + "/" +
                        new File(scriptPath).getParentFile().getName() + ".zip";


                if (!validateParams()) {
                    buttonSave.setEnabled(true);
                    buttonCancel.setEnabled(true);
                    return;
                }

                boolean exportResourcesOnly = chkExportResourceOnly.isSelected();
                int expType = ExportProgramToZipFileTask.EXPORT_TYPE_FULL;
                if (exportResourcesOnly) {
                    expType = ExportProgramToZipFileTask.EXPORT_TYPE_RESOURCES_ONLY;
                }
                saveTask = new ExportProgramToZipFileTask(scriptPath, prg,
                        targetPath,
                        null,
                        expType,
                        null,
                        false,
                        ExportProgramToZipFileTask.TARGET_DEVICE_PC,
                        new Runnable() {

                            @Override
                            public void run() {

                                if (ExportProgramDialog.this.uploadMode) {

                                    WooProduct p = new WooProduct(null);
                                    p.name = txtName.getText().trim();
                                    p.sku = txtSku.getText().trim();
                                    p.localFilePic = textTargetFolder.getText();

                                    JSONObject params = uploadToWebSite(zipFilePath, scriptPath);
                                    p.visibility = chkVisibleOnWebSite.isSelected() ? "visible" : "hidden";
                                    p.desc = txtDesc.getText().trim();
                                    p.downloadFile = params.getString("webfile");
                                    if (params.has("webfilePic")) {
                                        p.downloadFilePic = params.getString("webfilePic");
                                    }

                                    if (params.has("webId")) {
                                        p.webId = params.getString("webId");
                                    }

                                    String prevSku = params.getString("webSku");
                                    p.genDownloadFileId();
                                    String retval = "" + Util.createOrUpdateProduct(p, scriptPath, prevSku);

                                    if (!Util.isJSONValid(retval)) {
                                        // failed to create or update product
                                        JOptionPane.showMessageDialog(null,
                                                "Failed to upload product\r\n" + retval,
                                                "Failed to upload product",
                                                JOptionPane.ERROR_MESSAGE);

                                        buttonSave.setEnabled(true);
                                        buttonCancel.setEnabled(true);
                                        return;
                                    } else {
                                        JOptionPane.showMessageDialog(null,
                                                "Product updated successfully",
                                                "Product Update",
                                                JOptionPane.INFORMATION_MESSAGE);
                                    }

                                } else {

                                    int n = JOptionPane.showConfirmDialog(
                                            null,
                                            "Export finished successfully. Open file location?",
                                            "Open File Location",
                                            JOptionPane.YES_NO_OPTION);

                                    if (n == YES_OPTION) {
                                        try {

                                            Desktop.getDesktop().open(new File(targetPath));

                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    }

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
        selectButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {

                String userhome = System.getProperty("user.home");
                JFileChooser jfc = null;

                if (ExportProgramDialog.this.uploadMode) {
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
                    //jfc.setAcceptAllFileFilterUsed(false);
                } else {
                    jfc = new JFileChooser(userhome + "/Documents");
                    jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                    jfc.setAcceptAllFileFilterUsed(false);
                }

                int returnValue = jfc.showOpenDialog(null);

                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    String path = jfc.getCurrentDirectory() + "//" + jfc.getSelectedFile().getName();
                    textTargetFolder.setText(new File(path).getAbsolutePath());


                    if (ExportProgramDialog.this.uploadMode) {

                        showProductPic();
                    }
                }

            }
        });
    }

    private boolean validateParams() {

        if (ExportProgramDialog.this.uploadMode) {

            WooProduct p = new WooProduct(null);
            p.name = txtName.getText().trim();
            if (p.name.length() < 10) {
                JOptionPane.showMessageDialog(null, "Product name must be at least 10 characters",
                        "Program Name Too Short", JOptionPane.INFORMATION_MESSAGE);

                return false;
            }

            p.sku = txtSku.getText().trim();
            if (p.sku.length() < 10) {
                JOptionPane.showMessageDialog(null, "Product SKU must be at least 10 characters",
                        "Program SKU Too Short", JOptionPane.INFORMATION_MESSAGE);

                return false;
            }

            p.localFilePic = textTargetFolder.getText();
            if (p.localFilePic.length() == 0) {
                JOptionPane.showMessageDialog(null, "Product must have an image (at least 300 X 300)",
                        "Missing Product Image", JOptionPane.INFORMATION_MESSAGE);

                return false;
            }

        }

        return true;
    }

    protected void showProductPic() {

        if (textTargetFolder.getText().length() == 0) {
            return;
        }

        try {
            BufferedImage myPicture = null;
            try {
                myPicture = ImageIO.read(new File(textTargetFolder.getText()));
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            ExportProgramDialog.this.scaledPic = myPicture.getScaledInstance(300, 300, Image.SCALE_FAST);
            JLabel picLabel = new JLabel(new ImageIcon(scaledPic));
            picPanel.removeAll();
            picPanel.add(picLabel);
            picPanel.updateUI();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    protected JSONObject uploadToWebSite(String zipFilePath, String scriptPath) {

        JSONObject params = Util.getScriptJsonParams(scriptPath);

        String webfile = null;

        if (params.has("webfile")) {
            webfile = params.getString("webfile");
        }

        if (webfile == null || webfile.length() == 0) {
            webfile = UUID.randomUUID() + ".zip";
            params.put("webfile", webfile);

        }

        File targetZipFile = new File(zipFilePath);
        File renamedFile = new File(targetZipFile.getParentFile().getAbsolutePath() + "/" + webfile);
        if (renamedFile.exists()) {
            renamedFile.delete();
        }
        targetZipFile.renameTo(renamedFile);

        Util.ftpUploadFile(renamedFile, null);
        Util.setScriptJsonParams(scriptPath, params);

        File imageFile = prepareProductImage(renamedFile);
        if (imageFile != null) {
            Util.ftpUploadFile(imageFile, null);
            params.put("webfilePic", imageFile.getName());
            params.put("localFilePic", imageFile.getAbsolutePath());
        }

        return params;

    }

    private File prepareProductImage(File productFile) {

        if (this.scaledPic == null) {
            return null;
        }

        try {

            String path = productFile.getParent();
            String name = productFile.getName().replace(".zip", ".png");

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
        contentPane.setLayout(new GridLayoutManager(3, 1, new Insets(10, 10, 10, 10), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1, true, false));
        panel1.add(panel2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        buttonSave = new JButton();
        buttonSave.setText("Save");
        panel2.add(buttonSave, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonCancel = new JButton();
        buttonCancel.setText("Cancel");
        panel2.add(buttonCancel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(4, 3, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel3, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        textTargetFolder = new JTextField();
        textTargetFolder.setEditable(false);
        panel3.add(textTargetFolder, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        selectButton = new JButton();
        selectButton.setText("Browse...");
        panel3.add(selectButton, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        uploadToWebPanel = new JPanel();
        uploadToWebPanel.setLayout(new GridLayoutManager(5, 3, new Insets(0, 0, 0, 0), -1, -1));
        panel3.add(uploadToWebPanel, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("Name:");
        uploadToWebPanel.add(label1, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtName = new JTextField();
        uploadToWebPanel.add(txtName, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("SKU:");
        uploadToWebPanel.add(label2, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtSku = new JTextField();
        uploadToWebPanel.add(txtSku, new GridConstraints(3, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("Description:");
        uploadToWebPanel.add(label3, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtDesc = new JTextPane();
        uploadToWebPanel.add(txtDesc, new GridConstraints(4, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(150, 50), null, 0, false));
        picPanel = new JPanel();
        picPanel.setLayout(new BorderLayout(0, 0));
        uploadToWebPanel.add(picPanel, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(300, 300), null, 0, false));
        chkVisibleOnWebSite = new JCheckBox();
        chkVisibleOnWebSite.setSelected(true);
        chkVisibleOnWebSite.setText("Visible On Web Site");
        uploadToWebPanel.add(chkVisibleOnWebSite, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        chkExportResourceOnly = new JCheckBox();
        chkExportResourceOnly.setText("Export Resources Only");
        chkExportResourceOnly.setToolTipText("Export only the resources of this game. No code files will be added to the package.");
        panel3.add(chkExportResourceOnly, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        lblGeneralHeader = new JLabel();
        lblGeneralHeader.setText("Select Folder To Save:");
        contentPane.add(lblGeneralHeader, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }

}
