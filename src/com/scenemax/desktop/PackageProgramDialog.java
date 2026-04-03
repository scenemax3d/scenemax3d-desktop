package com.scenemax.desktop;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static javax.swing.JOptionPane.YES_OPTION;

public class PackageProgramDialog extends JDialog implements PropertyChangeListener {

    private final JLabel lblHeader1 = new JLabel("Choose deployment targets and package your game.");
    private final JLabel lblStatus = new JLabel(" ");
    private final JCheckBox chkWindows = new JCheckBox("Windows (.exe)", true);
    private final JCheckBox chkLinux = new JCheckBox("Linux (.jar + .sh)", true);
    private final JCheckBox chkMac = new JCheckBox("Mac OSX (.jar + .command)", true);
    private final JCheckBox chkWebStart = new JCheckBox("Web Start (.jnlp + landing page)", false);
    private final JTextField txtWindowsIcon = new JTextField();
    private final JTextField txtLinuxIcon = new JTextField();
    private final JTextField txtMacIcon = new JTextField();
    private final JTextField txtWebBaseUrl = new JTextField();
    private final JTextField txtWebVendor = new JTextField("SceneMax3D");
    private final JTextField txtWebHomepage = new JTextField();
    private final JTextField txtWebRemoteFolder = new JTextField();
    private final JCheckBox chkUploadWebStart = new JCheckBox("Upload Web Start files after packaging", false);
    private final JCheckBox chkSignWebStart = new JCheckBox("Sign Web Start JAR", false);
    private final JCheckBox chkGenerateSelfSigned = new JCheckBox("Generate free self-signed certificate if missing", false);
    private final JCheckBox chkShowAdvancedWebOptions = new JCheckBox("Show advanced signing options", false);
    private final JTextField txtKeystorePath = new JTextField();
    private final JTextField txtKeystoreAlias = new JTextField();
    private final JPasswordField txtKeystorePassword = new JPasswordField();
    private final JPasswordField txtKeyPassword = new JPasswordField();
    private final JButton btnBrowseWindowsIcon = new JButton("Browse...");
    private final JButton btnBrowseLinuxIcon = new JButton("Browse...");
    private final JButton btnBrowseMacIcon = new JButton("Browse...");
    private final JButton btnBrowseKeystore = new JButton("Browse...");
    private final JButton btnBrowseFtpFolder = new JButton("Connect & Browse...");
    private final JButton buttonPackage = new JButton("Package");
    private final JButton buttonCancel = new JButton("Cancel");
    private final JProgressBar progressBar1 = new JProgressBar();
    private final JPanel windowsPanel = new JPanel(new GridBagLayout());
    private final JPanel linuxPanel = new JPanel(new GridBagLayout());
    private final JPanel macPanel = new JPanel(new GridBagLayout());
    private final JPanel webPanel = new JPanel(new GridBagLayout());
    private final JPanel signingPanel = new JPanel(new GridBagLayout());

    private PackageProgramTask packageTask;
    private Runnable done;
    private String scriptFilePath;
    private String prg;
    private boolean doneInvoked = false;

    public PackageProgramDialog() {
        super((Frame) null, "Package & Deploy", true);
        setAlwaysOnTop(true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setContentPane(createContentPane());
        setPreferredSize(new Dimension(920, 700));
        progressBar1.setStringPainted(true);
        txtWebBaseUrl.setColumns(16);
        txtWebHomepage.setColumns(14);
        txtWebRemoteFolder.setColumns(33);
        txtWebVendor.setColumns(9);
        txtKeystorePath.setColumns(14);
        txtKeystoreAlias.setColumns(9);
        txtKeystorePassword.setColumns(9);
        txtKeyPassword.setColumns(9);

        buttonPackage.addActionListener(e -> startPackaging());
        buttonCancel.addActionListener(e -> handleClose());
        btnBrowseWindowsIcon.addActionListener(e -> chooseIconFile(txtWindowsIcon, "Choose Windows icon (.ico preferred)"));
        btnBrowseLinuxIcon.addActionListener(e -> chooseIconFile(txtLinuxIcon, "Choose Linux icon (.png preferred)"));
        btnBrowseMacIcon.addActionListener(e -> chooseIconFile(txtMacIcon, "Choose macOS icon (.icns or .png preferred)"));
        btnBrowseKeystore.addActionListener(e -> chooseKeystoreFile());
        btnBrowseFtpFolder.addActionListener(e -> browseRemoteFtpFolder());
        chkWindows.addActionListener(e -> updatePlatformSections());
        chkLinux.addActionListener(e -> updatePlatformSections());
        chkMac.addActionListener(e -> updatePlatformSections());
        chkWebStart.addActionListener(e -> updatePlatformSections());
        chkSignWebStart.addActionListener(e -> updatePlatformSections());
        chkShowAdvancedWebOptions.addActionListener(e -> updatePlatformSections());
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                handleClose();
            }
        });
    }

    public void run(String scriptFilePath, String prg, final Runnable callback) {
        this.scriptFilePath = scriptFilePath;
        this.prg = prg;
        this.done = callback;
        this.doneInvoked = false;
        resetUi();
    }

    private JPanel createContentPane() {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        lblHeader1.setFont(lblHeader1.getFont().deriveFont(Font.BOLD, 18f));
        center.add(lblHeader1);
        center.add(Box.createVerticalStrut(4));

        JLabel subtitle = new JLabel("All selected packages include the compiled game scripts and required resources.");
        center.add(subtitle);
        center.add(Box.createVerticalStrut(8));

        JPanel targetPanel = new JPanel();
        targetPanel.setLayout(new BoxLayout(targetPanel, BoxLayout.Y_AXIS));
        targetPanel.setBorder(BorderFactory.createTitledBorder("Targets"));
        targetPanel.add(chkWindows);
        targetPanel.add(chkLinux);
        targetPanel.add(chkMac);
        targetPanel.add(chkWebStart);
        center.add(targetPanel);
        center.add(Box.createVerticalStrut(8));

        windowsPanel.setBorder(BorderFactory.createTitledBorder("Windows"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridy = 0;
        gbc.gridx = 0;
        gbc.weightx = 0;
        windowsPanel.add(new JLabel("Icon"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        windowsPanel.add(txtWindowsIcon, gbc);
        gbc.gridx = 2;
        gbc.weightx = 0;
        windowsPanel.add(btnBrowseWindowsIcon, gbc);

        linuxPanel.setBorder(BorderFactory.createTitledBorder("Linux"));
        gbc.gridy = 0;
        gbc.gridx = 0;
        gbc.weightx = 0;
        linuxPanel.add(new JLabel("Icon"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        linuxPanel.add(txtLinuxIcon, gbc);
        gbc.gridx = 2;
        gbc.weightx = 0;
        linuxPanel.add(btnBrowseLinuxIcon, gbc);

        macPanel.setBorder(BorderFactory.createTitledBorder("Mac OSX"));
        gbc.gridy = 0;
        gbc.gridx = 0;
        gbc.weightx = 0;
        macPanel.add(new JLabel("Icon"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        macPanel.add(txtMacIcon, gbc);
        gbc.gridx = 2;
        gbc.weightx = 0;
        macPanel.add(btnBrowseMacIcon, gbc);

        center.add(windowsPanel);
        center.add(Box.createVerticalStrut(6));
        center.add(linuxPanel);
        center.add(Box.createVerticalStrut(6));
        center.add(macPanel);
        center.add(Box.createVerticalStrut(8));

        webPanel.setBorder(BorderFactory.createTitledBorder("Web Start"));
        webPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        webPanel.setMaximumSize(new Dimension(445, Integer.MAX_VALUE));
        GridBagConstraints webGbc = new GridBagConstraints();
        webGbc.insets = new Insets(3, 4, 3, 4);
        webGbc.fill = GridBagConstraints.HORIZONTAL;
        webGbc.anchor = GridBagConstraints.NORTHWEST;
        webGbc.gridx = 0;
        webGbc.gridy = 0;
        webGbc.weightx = 1;

        webPanel.add(new JLabel("Public Base URL"), webGbc);
        webGbc.gridy++;
        webPanel.add(txtWebBaseUrl, webGbc);

        webGbc.gridy++;
        webPanel.add(new JLabel("Publisher"), webGbc);
        webGbc.gridy++;
        webPanel.add(txtWebVendor, webGbc);

        webGbc.gridy++;
        webPanel.add(new JLabel("Website"), webGbc);
        webGbc.gridy++;
        webPanel.add(txtWebHomepage, webGbc);

        webGbc.gridy++;
        webPanel.add(new JLabel("FTP Remote Folder"), webGbc);
        webGbc.gridy++;
        JPanel ftpFolderRow = new JPanel(new BorderLayout(6, 0));
        ftpFolderRow.setOpaque(false);
        ftpFolderRow.add(txtWebRemoteFolder);
        ftpFolderRow.add(btnBrowseFtpFolder, BorderLayout.EAST);
        webPanel.add(ftpFolderRow, webGbc);

        webGbc.gridy++;
        webPanel.add(chkUploadWebStart, webGbc);

        webGbc.gridy++;
        webPanel.add(chkSignWebStart, webGbc);

        webGbc.gridy++;
        webPanel.add(chkShowAdvancedWebOptions, webGbc);

        webGbc.gridy++;
        JLabel webHint = new JLabel("<html>Creates a browser-friendly landing page and a <b>.jnlp</b> launcher for OpenWebStart. Upload all generated files to an HTTPS host and link users to <b>index.html</b>.</html>");
        webHint.setForeground(new Color(92, 92, 92));
        webPanel.add(webHint, webGbc);

        center.add(webPanel);
        center.add(Box.createVerticalStrut(8));

        signingPanel.setBorder(BorderFactory.createTitledBorder("Signing"));
        signingPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        signingPanel.setMaximumSize(new Dimension(445, Integer.MAX_VALUE));
        GridBagConstraints signGbc = new GridBagConstraints();
        signGbc.insets = new Insets(3, 4, 3, 4);
        signGbc.fill = GridBagConstraints.HORIZONTAL;
        signGbc.anchor = GridBagConstraints.NORTHWEST;
        signGbc.gridx = 0;
        signGbc.gridy = 0;
        signGbc.weightx = 1;

        signingPanel.add(new JLabel("Keystore"), signGbc);
        signGbc.gridy++;
        JPanel keystoreRow = new JPanel(new BorderLayout(6, 0));
        keystoreRow.setOpaque(false);
        keystoreRow.add(txtKeystorePath);
        keystoreRow.add(btnBrowseKeystore, BorderLayout.EAST);
        signingPanel.add(keystoreRow, signGbc);

        signGbc.gridy++;
        signingPanel.add(new JLabel("Alias"), signGbc);
        signGbc.gridy++;
        signingPanel.add(txtKeystoreAlias, signGbc);

        signGbc.gridy++;
        signingPanel.add(new JLabel("Store Password"), signGbc);
        signGbc.gridy++;
        signingPanel.add(txtKeystorePassword, signGbc);

        signGbc.gridy++;
        signingPanel.add(new JLabel("Key Password"), signGbc);
        signGbc.gridy++;
        signingPanel.add(txtKeyPassword, signGbc);

        signGbc.gridy++;
        signingPanel.add(chkGenerateSelfSigned, signGbc);

        signGbc.gridy++;
        JLabel signHint = new JLabel("<html>Use an existing keystore, or let SceneMax create a free self-signed one for testing. Self-signed certificates still show trust prompts on player machines.</html>");
        signHint.setForeground(new Color(92, 92, 92));
        signingPanel.add(signHint, signGbc);

        center.add(signingPanel);
        center.add(Box.createVerticalStrut(8));

        lblStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(lblStatus);
        center.add(Box.createVerticalStrut(6));
        progressBar1.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressBar1.setMaximumSize(new Dimension(Integer.MAX_VALUE, progressBar1.getPreferredSize().height));
        center.add(progressBar1);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(buttonCancel);
        buttons.add(buttonPackage);

        JScrollPane scrollPane = new JScrollPane(center) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(890, 610);
            }
        };
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        center.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(scrollPane, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        return root;
    }

    private void resetUi() {
        progressBar1.setValue(0);
        lblStatus.setText(" ");
        setTargetsEnabled(true);
        buttonPackage.setEnabled(true);
        buttonCancel.setText("Cancel");
        loadWebStartDefaults();
        updatePlatformSections();
    }

    private void setTargetsEnabled(boolean enabled) {
        chkWindows.setEnabled(enabled);
        chkLinux.setEnabled(enabled);
        chkMac.setEnabled(enabled);
        txtWindowsIcon.setEnabled(enabled);
        txtLinuxIcon.setEnabled(enabled);
        txtMacIcon.setEnabled(enabled);
        txtWebBaseUrl.setEnabled(enabled);
        txtWebVendor.setEnabled(enabled);
        txtWebHomepage.setEnabled(enabled);
        txtWebRemoteFolder.setEnabled(enabled);
        chkUploadWebStart.setEnabled(enabled);
        chkShowAdvancedWebOptions.setEnabled(enabled);
        chkSignWebStart.setEnabled(enabled);
        chkGenerateSelfSigned.setEnabled(enabled);
        txtKeystorePath.setEnabled(enabled);
        txtKeystoreAlias.setEnabled(enabled);
        txtKeystorePassword.setEnabled(enabled);
        txtKeyPassword.setEnabled(enabled);
        btnBrowseWindowsIcon.setEnabled(enabled);
        btnBrowseLinuxIcon.setEnabled(enabled);
        btnBrowseMacIcon.setEnabled(enabled);
        btnBrowseKeystore.setEnabled(enabled);
        btnBrowseFtpFolder.setEnabled(enabled);
        updatePlatformSections();
    }

    private void startPackaging() {
        List<PackageProgramTask.PackageTarget> targets = new ArrayList<>();
        if (chkWindows.isSelected()) {
            targets.add(PackageProgramTask.PackageTarget.WINDOWS);
        }
        if (chkLinux.isSelected()) {
            targets.add(PackageProgramTask.PackageTarget.LINUX);
        }
        if (chkMac.isSelected()) {
            targets.add(PackageProgramTask.PackageTarget.MAC_OSX);
        }
        if (chkWebStart.isSelected()) {
            targets.add(PackageProgramTask.PackageTarget.WEB_START);
        }

        if (targets.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select at least one target platform.", "Package Error", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String webBaseUrl = txtWebBaseUrl.getText() == null ? "" : txtWebBaseUrl.getText().trim();
        if (chkWebStart.isSelected() && webBaseUrl.length() == 0) {
            JOptionPane.showMessageDialog(this, "Enter the public base URL that will host the generated Web Start files.", "Package Error", JOptionPane.INFORMATION_MESSAGE);
            txtWebBaseUrl.requestFocusInWindow();
            return;
        }

        String webRemoteFolder = valueOrBlank(txtWebRemoteFolder.getText());
        if (chkWebStart.isSelected() && chkUploadWebStart.isSelected() && webRemoteFolder.length() == 0) {
            JOptionPane.showMessageDialog(this, "Enter the FTP remote folder that should receive the Web Start files.", "Package Error", JOptionPane.INFORMATION_MESSAGE);
            txtWebRemoteFolder.requestFocusInWindow();
            return;
        }

        String keystorePath = valueOrBlank(txtKeystorePath.getText());
        String keystoreAlias = valueOrBlank(txtKeystoreAlias.getText());
        String storePassword = new String(txtKeystorePassword.getPassword());
        String keyPassword = new String(txtKeyPassword.getPassword());
        if (chkWebStart.isSelected() && chkSignWebStart.isSelected()) {
            if (keystoreAlias.length() == 0) {
                JOptionPane.showMessageDialog(this, "Enter a keystore alias for Web Start signing.", "Package Error", JOptionPane.INFORMATION_MESSAGE);
                txtKeystoreAlias.requestFocusInWindow();
                return;
            }
            if (storePassword.trim().length() == 0) {
                JOptionPane.showMessageDialog(this, "Enter a keystore password for Web Start signing.", "Package Error", JOptionPane.INFORMATION_MESSAGE);
                txtKeystorePassword.requestFocusInWindow();
                return;
            }
            if (keyPassword.trim().length() == 0) {
                keyPassword = storePassword;
            }
            if (!chkGenerateSelfSigned.isSelected() && keystorePath.length() == 0) {
                JOptionPane.showMessageDialog(this, "Choose an existing keystore file, or enable self-signed certificate generation.", "Package Error", JOptionPane.INFORMATION_MESSAGE);
                txtKeystorePath.requestFocusInWindow();
                return;
            }
        }

        try {
            AppDB.getInstance().setParam("webstart_base_url", webBaseUrl);
            AppDB.getInstance().setParam("webstart_vendor", valueOrBlank(txtWebVendor.getText()));
            AppDB.getInstance().setParam("webstart_homepage", valueOrBlank(txtWebHomepage.getText()));
            AppDB.getInstance().setParam("webstart_remote_folder", webRemoteFolder);
            AppDB.getInstance().setParam("webstart_keystore_path", keystorePath);
            AppDB.getInstance().setParam("webstart_keystore_alias", keystoreAlias);

            setTargetsEnabled(false);
            buttonPackage.setEnabled(false);
            buttonCancel.setText("Close");
            lblHeader1.setText("Packaging program. Please wait...");
            lblStatus.setText("Preparing selected target packages...");

            packageTask = new PackageProgramTask(
                    scriptFilePath,
                    prg,
                    targets,
                    new PackageProgramTask.PackageOptions(
                            toFileOrNull(txtWindowsIcon.getText()),
                            toFileOrNull(txtLinuxIcon.getText()),
                            toFileOrNull(txtMacIcon.getText()),
                            webBaseUrl,
                            valueOrBlank(txtWebVendor.getText()),
                            valueOrBlank(txtWebHomepage.getText()),
                            webRemoteFolder,
                            chkUploadWebStart.isSelected(),
                            chkSignWebStart.isSelected(),
                            chkGenerateSelfSigned.isSelected(),
                            toFileOrNull(keystorePath),
                            keystoreAlias,
                            storePassword,
                            keyPassword
                    ),
                    this::onPackagingFinished,
                    this::onPackagingCanceled
            );
            packageTask.addPropertyChangeListener(this);
            packageTask.execute();
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Packaging failed to start.", "Package Error", JOptionPane.ERROR_MESSAGE);
            safeDone();
            dispose();
        }
    }

    private void onPackagingCanceled() {
        setAlwaysOnTop(false);
        JOptionPane.showMessageDialog(
                this,
                "Program Packaging Failed",
                "Package Error",
                JOptionPane.ERROR_MESSAGE
        );
        dispose();
        safeDone();
    }

    private void onPackagingFinished() {
        setAlwaysOnTop(false);

        StringBuilder message = new StringBuilder("Program packaging finished successfully.");
        List<File> outputs = packageTask.getProducedArtifacts();
        if (!outputs.isEmpty()) {
            message.append("\r\n\r\nGenerated outputs:");
            for (File output : outputs) {
                message.append("\r\n").append(output.getAbsolutePath());
            }
        }
        String notes = packageTask.getCompletionNotes();
        if (notes != null && notes.trim().length() > 0) {
            message.append("\r\n\r\n").append(notes.trim());
        }

        int n = JOptionPane.showConfirmDialog(
                this,
                message + "\r\n\r\nOpen output folder?",
                "Open File Location",
                JOptionPane.YES_NO_OPTION);

        if (n == YES_OPTION) {
            try {
                ProcessBuilder pb = new ProcessBuilder("explorer.exe", packageTask.getOutputFolder().getAbsolutePath());
                pb.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        dispose();
        safeDone();
    }

    private void handleClose() {
        if (packageTask != null && !packageTask.isDone()) {
            JOptionPane.showMessageDialog(this, "Packaging is running. Please wait for it to finish.", "Package In Progress", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        dispose();
        safeDone();
    }

    private void safeDone() {
        if (doneInvoked) {
            return;
        }
        doneInvoked = true;
        if (done != null) {
            done.run();
        }
    }

    private void chooseIconFile(JTextField targetField, String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        if (targetField.getText() != null && targetField.getText().trim().length() > 0) {
            chooser.setSelectedFile(new File(targetField.getText().trim()));
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            targetField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void chooseKeystoreFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose keystore file");
        if (txtKeystorePath.getText() != null && txtKeystorePath.getText().trim().length() > 0) {
            chooser.setSelectedFile(new File(txtKeystorePath.getText().trim()));
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            txtKeystorePath.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void browseRemoteFtpFolder() {
        FtpBrowseDialog dialog = new FtpBrowseDialog(this);
        String selectedFolder = dialog.showDialog(valueOrBlank(txtWebRemoteFolder.getText()));
        if (selectedFolder != null && selectedFolder.trim().length() > 0) {
            txtWebRemoteFolder.setText(selectedFolder.trim());
        }
    }

    private void updatePlatformSections() {
        windowsPanel.setVisible(chkWindows.isSelected());
        linuxPanel.setVisible(chkLinux.isSelected());
        macPanel.setVisible(chkMac.isSelected());
        boolean showWeb = chkWebStart.isSelected();
        webPanel.setVisible(showWeb);
        signingPanel.setVisible(showWeb && chkShowAdvancedWebOptions.isSelected() && chkSignWebStart.isSelected());
        if (getContentPane() != null) {
            getContentPane().revalidate();
            getContentPane().repaint();
        }
    }

    private File toFileOrNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.length() == 0) {
            return null;
        }
        return new File(trimmed);
    }

    private String valueOrBlank(String text) {
        return text == null ? "" : text.trim();
    }

    private void loadWebStartDefaults() {
        txtWebBaseUrl.setText(readSavedOrConfig("webstart_base_url", ""));
        txtWebVendor.setText(readSavedOrConfig("webstart_vendor", "SceneMax3D"));
        txtWebHomepage.setText(readSavedOrConfig("webstart_homepage", ""));
        txtWebRemoteFolder.setText(readSavedOrConfig("webstart_remote_folder", ""));
        txtKeystorePath.setText(readSavedOrConfig("webstart_keystore_path", ""));
        txtKeystoreAlias.setText(readSavedOrConfig("webstart_keystore_alias", "scenemax"));
    }

    private String readSavedOrConfig(String key, String fallback) {
        String saved = AppDB.getInstance().getParam(key);
        if (saved != null && saved.trim().length() > 0) {
            return saved.trim();
        }
        String configValue = AppConfig.get(key, fallback);
        if (configValue == null || configValue.trim().length() == 0) {
            return fallback;
        }
        return configValue.trim();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (packageTask == null) {
            return;
        }

        if ("progress".equals(evt.getPropertyName())) {
            int progress = packageTask.getProgress();
            progressBar1.setValue(progress);
            progressBar1.updateUI();
            return;
        }

        if ("statusNote".equals(evt.getPropertyName())) {
            String note = packageTask.getStatusNote();
            if (note != null && note.trim().length() > 0) {
                lblStatus.setText(note);
            }
        }
    }
}
