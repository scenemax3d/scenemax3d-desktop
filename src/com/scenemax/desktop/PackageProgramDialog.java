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
    private final JTextField txtWindowsIcon = new JTextField();
    private final JTextField txtLinuxIcon = new JTextField();
    private final JTextField txtMacIcon = new JTextField();
    private final JButton btnBrowseWindowsIcon = new JButton("Browse...");
    private final JButton btnBrowseLinuxIcon = new JButton("Browse...");
    private final JButton btnBrowseMacIcon = new JButton("Browse...");
    private final JButton buttonPackage = new JButton("Package");
    private final JButton buttonCancel = new JButton("Cancel");
    private final JProgressBar progressBar1 = new JProgressBar();

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
        progressBar1.setStringPainted(true);

        buttonPackage.addActionListener(e -> startPackaging());
        buttonCancel.addActionListener(e -> handleClose());
        btnBrowseWindowsIcon.addActionListener(e -> chooseIconFile(txtWindowsIcon, "Choose Windows icon (.ico preferred)"));
        btnBrowseLinuxIcon.addActionListener(e -> chooseIconFile(txtLinuxIcon, "Choose Linux icon (.png preferred)"));
        btnBrowseMacIcon.addActionListener(e -> chooseIconFile(txtMacIcon, "Choose macOS icon (.icns or .png preferred)"));
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
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        lblHeader1.setFont(lblHeader1.getFont().deriveFont(Font.BOLD, 18f));
        center.add(lblHeader1);
        center.add(Box.createVerticalStrut(8));

        JLabel subtitle = new JLabel("All selected packages include the compiled game scripts and required resources.");
        center.add(subtitle);
        center.add(Box.createVerticalStrut(12));

        JPanel targetPanel = new JPanel();
        targetPanel.setLayout(new BoxLayout(targetPanel, BoxLayout.Y_AXIS));
        targetPanel.setBorder(BorderFactory.createTitledBorder("Targets"));
        targetPanel.add(chkWindows);
        targetPanel.add(chkLinux);
        targetPanel.add(chkMac);
        center.add(targetPanel);
        center.add(Box.createVerticalStrut(12));

        JPanel iconPanel = new JPanel(new GridBagLayout());
        iconPanel.setBorder(BorderFactory.createTitledBorder("Icons"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridy = 0;
        gbc.gridx = 0;
        gbc.weightx = 0;
        iconPanel.add(new JLabel("Windows"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        iconPanel.add(txtWindowsIcon, gbc);
        gbc.gridx = 2;
        gbc.weightx = 0;
        iconPanel.add(btnBrowseWindowsIcon, gbc);

        gbc.gridy = 1;
        gbc.gridx = 0;
        iconPanel.add(new JLabel("Linux"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        iconPanel.add(txtLinuxIcon, gbc);
        gbc.gridx = 2;
        gbc.weightx = 0;
        iconPanel.add(btnBrowseLinuxIcon, gbc);

        gbc.gridy = 2;
        gbc.gridx = 0;
        iconPanel.add(new JLabel("Mac OSX"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        iconPanel.add(txtMacIcon, gbc);
        gbc.gridx = 2;
        gbc.weightx = 0;
        iconPanel.add(btnBrowseMacIcon, gbc);

        center.add(iconPanel);
        center.add(Box.createVerticalStrut(12));

        lblStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(lblStatus);
        center.add(Box.createVerticalStrut(8));
        center.add(progressBar1);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(buttonCancel);
        buttons.add(buttonPackage);

        root.add(center, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        return root;
    }

    private void resetUi() {
        progressBar1.setValue(0);
        lblStatus.setText(" ");
        setTargetsEnabled(true);
        buttonPackage.setEnabled(true);
        buttonCancel.setText("Cancel");
    }

    private void setTargetsEnabled(boolean enabled) {
        chkWindows.setEnabled(enabled);
        chkLinux.setEnabled(enabled);
        chkMac.setEnabled(enabled);
        txtWindowsIcon.setEnabled(enabled);
        txtLinuxIcon.setEnabled(enabled);
        txtMacIcon.setEnabled(enabled);
        btnBrowseWindowsIcon.setEnabled(enabled);
        btnBrowseLinuxIcon.setEnabled(enabled);
        btnBrowseMacIcon.setEnabled(enabled);
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

        if (targets.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select at least one target platform.", "Package Error", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        try {
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
                            toFileOrNull(txtMacIcon.getText())
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

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (!"progress".equals(evt.getPropertyName()) || packageTask == null) {
            return;
        }

        int progress = packageTask.getProgress();
        progressBar1.setValue(progress);
        progressBar1.updateUI();
    }
}
