package com.scenemax.desktop;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.apache.commons.io.FileUtils;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

public class SettingsDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTabbedPane tabbedPane1;
    private JTextField txtClassrommsServer;
    private JTextField txtGalleryHost;
    private JTextField txtFtpUploadUrl;
    private JTextField txtConsumerKey;
    private JTextField txtConsumerSecret;
    private JTextField txtFtpHostName;
    private JTextField txtFtpUser;
    private JTextField txtFtpPassword;
    private JButton btnRestoreGalleryDefaults;
    private JTextField txtProjJvmArch;
    private JButton btnResetRuntime;
    private JTextField txtScenemaxServer;

    public SettingsDialog() {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

        String jvmArch = AppDB.getInstance().getParam("projector_jvm_arch");
        if (jvmArch != null && (jvmArch.equals("64") || jvmArch.equals("32"))) {
            txtProjJvmArch.setText(jvmArch);
        }

        txtGalleryHost.setText(isDefault("gallery_host", Util.GALLERY_HOST) ? "DEFAULT" : "CUSTOM");
        txtConsumerKey.setText(Util.WRITE_CONSUMER_KEY.isEmpty() ? "MISSING" : "CUSTOM");
        txtConsumerSecret.setText(Util.WRITE_CONSUMER_SECRET.isEmpty() ? "MISSING" : "CUSTOM");
        txtFtpUploadUrl.setText(isDefault("ftp_upload_folder", Util.FTP_UPLOAD_URL) ? "DEFAULT" : "CUSTOM");
        txtFtpHostName.setText(isDefault("ftp_host_name", Util.FTP_HOST_NAME) ? "DEFAULT" : "CUSTOM");

        txtFtpUser.setText(isDefault("ftp_user", Util.FTP_USER_NAME) ? "DEFAULT" : "CUSTOM");
        txtFtpPassword.setText(isDefault("ftp_password", Util.FTP_PASSWORD) ? "DEFAULT" : "CUSTOM");


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

        btnRestoreGalleryDefaults.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                txtGalleryHost.setText("DEFAULT");
                txtConsumerKey.setText("DEFAULT");
                txtConsumerSecret.setText("DEFAULT");
                txtFtpUploadUrl.setText("DEFAULT");
                txtFtpHostName.setText("DEFAULT");
                txtFtpUser.setText("DEFAULT");
                txtFtpPassword.setText("DEFAULT");
            }
        });

        btnResetRuntime.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                try {
                    FileUtils.deleteQuietly(new File("bulletjme.dll"));
                    FileUtils.deleteQuietly(new File("jni4net.n.w64.v40-0.8.8.0.dll"));
                    FileUtils.deleteQuietly(new File("jni4net.n.w32.v40-0.8.8.0.dll"));
                    FileUtils.deleteQuietly(new File("jni4net.n-0.8.8.0.dll"));
                    FileUtils.deleteQuietly(new File("lwjgl.dll"));
                    FileUtils.deleteQuietly(new File("lwjgl64.dll"));
                    FileUtils.deleteQuietly(new File("OpenAL64.dll"));
                    String ver = Util.getAppVersion();
                    String launcherName = "launcher" + ver + ".jar";
                    FileUtils.forceDelete(new File(launcherName));
                    JOptionPane.showMessageDialog(null, "RunTime reset finished successfully", "RunTime Reset", JOptionPane.INFORMATION_MESSAGE);

                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });

    }

    private void onOK() {

        updateParam("projector_jvm_arch", txtProjJvmArch.getText().trim());
        updateParam("classrooms_server", txtClassrommsServer.getText().trim());
        updateParam("gallery_host", txtGalleryHost.getText().trim());
        if (txtConsumerKey.getText().toLowerCase().trim().equals("missing")) {
            updateParam("write_gallery_api_key", "");
        } else {
            updateParam("write_gallery_api_key", txtConsumerKey.getText().trim());
        }

        if (txtConsumerSecret.getText().toLowerCase().trim().equals("missing")) {
            updateParam("write_gallery_api_secret", "");
        } else {
            updateParam("write_gallery_api_secret", txtConsumerSecret.getText().trim());
        }

        updateParam("ftp_upload_folder", txtFtpUploadUrl.getText().trim());
        updateParam("ftp_host_name", txtFtpHostName.getText().trim());
        updateParam("ftp_user", txtFtpUser.getText().trim());
        updateParam("ftp_password", txtFtpPassword.getText().trim());

        dispose();
    }

    private void updateParam(String key, String val) {
        val = val.toLowerCase();

        if (val.equals("custom")) {
            return;
        }

        if (val.equals("default")) {
            AppDB.getInstance().setParam(key, "");
        } else {
            AppDB.getInstance().setParam(key, val);
        }

    }

    private static boolean isDefault(String configKey, String currentValue) {
        String configDefault = AppConfig.get(configKey);
        return currentValue.equalsIgnoreCase(configDefault);
    }

    private void onCancel() {
        // add your code here if necessary
        dispose();
    }

    public static void main(String[] args) {
        SettingsDialog dialog = new SettingsDialog();
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
        panel3.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        tabbedPane1 = new JTabbedPane();
        panel3.add(tabbedPane1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(200, 200), null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(4, 4, new Insets(0, 0, 0, 0), -1, -1));
        tabbedPane1.addTab("General", panel4);
        final JLabel label1 = new JLabel();
        label1.setText("Classrooms Server:");
        panel4.add(label1, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel4.add(spacer2, new GridConstraints(3, 1, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        txtClassrommsServer = new JTextField();
        txtClassrommsServer.setText("DEFAULT");
        panel4.add(txtClassrommsServer, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Projector JVM Arch:");
        panel4.add(label2, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtProjJvmArch = new JTextField();
        txtProjJvmArch.setText("DEFAULT");
        panel4.add(txtProjJvmArch, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final Spacer spacer3 = new Spacer();
        panel4.add(spacer3, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        btnResetRuntime = new JButton();
        btnResetRuntime.setText("Reset RunTime");
        panel4.add(btnResetRuntime, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridLayoutManager(8, 3, new Insets(0, 0, 0, 0), -1, -1));
        tabbedPane1.addTab("Online Gallery", panel5);
        final JLabel label3 = new JLabel();
        label3.setText("Host:");
        panel5.add(label3, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtGalleryHost = new JTextField();
        txtGalleryHost.setText("DEFAULT");
        panel5.add(txtGalleryHost, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("FTP Upload URL:");
        panel5.add(label4, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtFtpUploadUrl = new JTextField();
        txtFtpUploadUrl.setText("DEFAULT");
        panel5.add(txtFtpUploadUrl, new GridConstraints(3, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label5 = new JLabel();
        label5.setText("Consumer Key:");
        panel5.add(label5, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtConsumerKey = new JTextField();
        txtConsumerKey.setText("DEFAULT");
        panel5.add(txtConsumerKey, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label6 = new JLabel();
        label6.setText("Consumer Secret:");
        panel5.add(label6, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtConsumerSecret = new JTextField();
        txtConsumerSecret.setText("DEFAULT");
        panel5.add(txtConsumerSecret, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label7 = new JLabel();
        label7.setText("FTP Host Name:");
        panel5.add(label7, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtFtpHostName = new JTextField();
        txtFtpHostName.setText("DEFAULT");
        panel5.add(txtFtpHostName, new GridConstraints(4, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label8 = new JLabel();
        label8.setText("FTP User:");
        panel5.add(label8, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtFtpUser = new JTextField();
        txtFtpUser.setText("DEFAULT");
        panel5.add(txtFtpUser, new GridConstraints(5, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label9 = new JLabel();
        label9.setText("FTP Password:");
        panel5.add(label9, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtFtpPassword = new JTextField();
        txtFtpPassword.setText("DEFAULT");
        panel5.add(txtFtpPassword, new GridConstraints(6, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JPanel panel6 = new JPanel();
        panel6.setLayout(new GridLayoutManager(4, 4, new Insets(0, 0, 0, 0), -1, -1));
        panel5.add(panel6, new GridConstraints(7, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label10 = new JLabel();
        Font label10Font = this.$$$getFont$$$(null, Font.BOLD, -1, label10.getFont());
        if (label10Font != null) label10.setFont(label10Font);
        label10.setText("Private schools -");
        panel6.add(label10, new GridConstraints(2, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label11 = new JLabel();
        label11.setForeground(new Color(-393216));
        label11.setText("Warning!");
        panel6.add(label11, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label12 = new JLabel();
        label12.setText("* Do not change any of these fields unless you absolutely know what you are doing. ");
        panel6.add(label12, new GridConstraints(1, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JLabel label13 = new JLabel();
        label13.setText("Contact: scenemax3d@gmail.com for creating online galley for your students ");
        panel6.add(label13, new GridConstraints(3, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnRestoreGalleryDefaults = new JButton();
        btnRestoreGalleryDefaults.setText("Restore Defaults");
        panel6.add(btnRestoreGalleryDefaults, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer4 = new Spacer();
        panel6.add(spacer4, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
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
