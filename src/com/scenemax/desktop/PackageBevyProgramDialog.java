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

public class PackageBevyProgramDialog extends JDialog implements PropertyChangeListener {

    private final JLabel lblHeader = new JLabel("Choose NextGen deployment targets and package your game.");
    private final JLabel lblStatus = new JLabel(" ");
    private final JCheckBox chkWindows = new JCheckBox("Windows (.exe)", true);
    private final JCheckBox chkLinux = new JCheckBox("Linux executable package (.zip)", false);
    private final JCheckBox chkMac = new JCheckBox("macOS executable package (.zip)", false);
    private final JCheckBox chkWeb = new JCheckBox("Web / WebAssembly package", false);
    private final JCheckBox chkUploadToItch = new JCheckBox("Automatically upload selected builds to itch.io with butler", false);
    private final JLabel lblItchInfo = new JLabel(" ");
    private final JButton buttonPackage = new JButton("Package");
    private final JButton buttonCancel = new JButton("Cancel");
    private final JProgressBar progressBar = new JProgressBar();
    private PackageBevyProgramTask packageTask;
    private Runnable done;
    private String scriptFilePath;
    private String prg;
    private boolean doneInvoked = false;

    public PackageBevyProgramDialog() {
        super((Frame) null, "Package & Deploy NextGen", true);
        setAlwaysOnTop(true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setContentPane(createContentPane());
        setPreferredSize(new Dimension(760, 520));
        progressBar.setStringPainted(true);

        buttonPackage.addActionListener(e -> startPackaging());
        buttonCancel.addActionListener(e -> handleClose());
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                handleClose();
            }
        });
    }

    public void run(String scriptFilePath, String prg, Runnable callback) {
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

        lblHeader.setFont(lblHeader.getFont().deriveFont(Font.BOLD, 18f));
        center.add(lblHeader);
        center.add(Box.createVerticalStrut(4));

        JLabel subtitle = new JLabel("<html>Packages the Rust/Bevy projector with staged SceneMax code and project resources.</html>");
        center.add(subtitle);
        center.add(Box.createVerticalStrut(8));

        JPanel targetPanel = new JPanel();
        targetPanel.setLayout(new BoxLayout(targetPanel, BoxLayout.Y_AXIS));
        targetPanel.setBorder(BorderFactory.createTitledBorder("Targets"));
        targetPanel.add(chkWindows);
        targetPanel.add(chkLinux);
        targetPanel.add(chkMac);
        targetPanel.add(chkWeb);

        JLabel targetHint = new JLabel("<html>Windows can be built on this machine. Linux, macOS, and Web may require additional Rust targets, linkers, SDKs, or CI runners.</html>");
        targetHint.setForeground(new Color(92, 92, 92));
        targetHint.setBorder(BorderFactory.createEmptyBorder(4, 20, 0, 0));
        targetPanel.add(targetHint);
        center.add(targetPanel);
        center.add(Box.createVerticalStrut(8));

        JPanel itchPanel = new JPanel(new GridBagLayout());
        itchPanel.setBorder(BorderFactory.createTitledBorder("itch.io"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        itchPanel.add(chkUploadToItch, gbc);
        gbc.gridy++;
        lblItchInfo.setForeground(new Color(92, 92, 92));
        itchPanel.add(lblItchInfo, gbc);
        center.add(itchPanel);
        center.add(Box.createVerticalStrut(8));

        JPanel notesPanel = new JPanel(new BorderLayout());
        notesPanel.setBorder(BorderFactory.createTitledBorder("Notes"));
        JTextArea notes = new JTextArea(
                "NextGen packaging is separate from the Classic Java/JME3 packaging task.\n" +
                        "It builds the Rust/Bevy projector and packages it with resources/ and running/main.\n" +
                        "Web packaging is experimental until the Bevy browser runtime path is finalized."
        );
        notes.setEditable(false);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        notes.setOpaque(false);
        notes.setFocusable(false);
        notesPanel.add(notes, BorderLayout.CENTER);
        center.add(notesPanel);

        JPanel footer = new JPanel(new BorderLayout(0, 8));
        lblStatus.setPreferredSize(new Dimension(680, lblStatus.getPreferredSize().height));
        progressBar.setPreferredSize(new Dimension(680, Math.max(22, progressBar.getPreferredSize().height)));
        footer.add(lblStatus, BorderLayout.NORTH);
        footer.add(progressBar, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(buttonCancel);
        buttons.add(buttonPackage);
        footer.add(buttons, BorderLayout.SOUTH);

        root.add(center, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        return root;
    }

    private void resetUi() {
        progressBar.setValue(0);
        lblStatus.setText(" ");
        buttonPackage.setEnabled(true);
        buttonCancel.setText("Cancel");
        lblHeader.setText("Choose NextGen deployment targets and package your game.");
        setTargetsEnabled(true);
        loadItchDefaults();
    }

    private void setTargetsEnabled(boolean enabled) {
        chkWindows.setEnabled(enabled);
        chkLinux.setEnabled(enabled);
        chkMac.setEnabled(enabled);
        chkWeb.setEnabled(enabled);
        chkUploadToItch.setEnabled(enabled);
    }

    private void startPackaging() {
        List<PackageBevyProgramTask.PackageTarget> targets = new ArrayList<>();
        if (chkWindows.isSelected()) {
            targets.add(PackageBevyProgramTask.PackageTarget.WINDOWS);
        }
        if (chkLinux.isSelected()) {
            targets.add(PackageBevyProgramTask.PackageTarget.LINUX);
        }
        if (chkMac.isSelected()) {
            targets.add(PackageBevyProgramTask.PackageTarget.MAC_OSX);
        }
        if (chkWeb.isSelected()) {
            targets.add(PackageBevyProgramTask.PackageTarget.WEB);
        }

        if (targets.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select at least one NextGen package target.", "Package Error", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        SceneMaxProject activeProject = Util.getActiveProject();
        String itchTarget = "";
        String butlerPath = "";
        String itchApiKey = "";
        if (chkUploadToItch.isSelected()) {
            if (activeProject == null) {
                JOptionPane.showMessageDialog(this, "Create or select a project first, then configure itch.io in File > Projects > Project Settings...", "Package Error", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            try {
                itchTarget = ItchIoHelper.normalizeGameTarget(activeProject.itchGamePage);
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, "Open File > Projects > Project Settings... and enter a valid itch.io game page before enabling automatic upload.\r\n\r\n" + ex.getMessage(), "Package Error", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            butlerPath = Util.getItchButlerPath(activeProject);
            itchApiKey = Util.getProjectItchApiKey(activeProject);
            if (itchApiKey.length() == 0 && !ItchIoHelper.hasLocalCredentials()) {
                int loginChoice = JOptionPane.showConfirmDialog(
                        this,
                        "No project API key is saved for this itch.io upload.\r\n\r\n" +
                                "Would you like SceneMax to start `butler login` for you now?\r\n" +
                                "This opens the itch.io sign-in flow in your browser and brings you back here when it finishes.",
                        "itch.io Login",
                        JOptionPane.YES_NO_OPTION
                );
                if (loginChoice != JOptionPane.YES_OPTION) {
                    return;
                }

                String usedButlerPath = ItchIoHelper.promptAndRunButlerLogin(this, butlerPath);
                if (usedButlerPath == null || !ItchIoHelper.hasLocalCredentials()) {
                    JOptionPane.showMessageDialog(
                            this,
                            "Butler login did not complete, so SceneMax cannot upload to itch.io yet.\r\n\r\n" +
                                    "You can try again in File > Settings > Butler, or open File > Projects > Project Settings... to paste an API key instead.",
                            "Package Error",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                    return;
                }

                if (!"butler".equalsIgnoreCase(usedButlerPath)) {
                    butlerPath = usedButlerPath;
                    Util.setItchButlerPath(usedButlerPath);
                }
            }
        }

        setTargetsEnabled(false);
        buttonPackage.setEnabled(false);
        buttonCancel.setText("Close");
        lblHeader.setText("Packaging NextGen program. Please wait...");
        lblStatus.setText("Preparing selected NextGen target packages...");

        packageTask = new PackageBevyProgramTask(
                scriptFilePath,
                prg,
                targets,
                new PackageBevyProgramTask.PackageOptions(
                        chkUploadToItch.isSelected(),
                        butlerPath,
                        itchTarget,
                        itchApiKey,
                        activeProject == null ? "" : valueOrBlank(activeProject.itchWindowsChannel),
                        activeProject == null ? "" : valueOrBlank(activeProject.itchLinuxChannel),
                        activeProject == null ? "" : valueOrBlank(activeProject.itchMacChannel),
                        "web"
                ),
                this::onPackagingFinished,
                this::onPackagingCanceled
        );
        packageTask.addPropertyChangeListener(this);
        packageTask.execute();
    }

    private void onPackagingCanceled() {
        setAlwaysOnTop(false);
        String failureMessage = packageTask == null ? "" : valueOrBlank(packageTask.getFailureMessage());
        if (failureMessage.length() == 0) {
            failureMessage = "NextGen packaging failed.";
        }
        Util.showScrollableMessageDialog(this, failureMessage, "Package Error", JOptionPane.ERROR_MESSAGE);
        dispose();
        safeDone();
    }

    private void onPackagingFinished() {
        setAlwaysOnTop(false);

        StringBuilder message = new StringBuilder("NextGen packaging finished successfully.");
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
        if (!doneInvoked && done != null) {
            doneInvoked = true;
            done.run();
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("progress".equals(evt.getPropertyName())) {
            progressBar.setValue((Integer) evt.getNewValue());
        }
        if (packageTask != null) {
            String status = valueOrBlank(packageTask.getStatusNote());
            if (status.length() > 0) {
                lblStatus.setText(status);
            }
        }
    }

    private void loadItchDefaults() {
        SceneMaxProject project = Util.getActiveProject();
        if (project == null || project.itchGamePage == null || project.itchGamePage.trim().length() == 0) {
            lblItchInfo.setText("<html>Configure File > Projects > Project Settings... to set the itch.io page, channels, and optional API key.</html>");
            return;
        }
        try {
            String savedTarget = ItchIoHelper.normalizeGameTarget(project.itchGamePage);
            lblItchInfo.setText("<html>Ready to upload to <b>" + savedTarget + "</b>. Channels: Windows="
                    + ItchIoHelper.defaultChannel("windows", project.itchWindowsChannel)
                    + ", Linux=" + ItchIoHelper.defaultChannel("linux", project.itchLinuxChannel)
                    + ", macOS=" + ItchIoHelper.defaultChannel("macos", project.itchMacChannel)
                    + ", Web=web.</html>");
        } catch (Exception ex) {
            lblItchInfo.setText("<html>Project itch.io target needs attention in Project Settings: " + valueOrBlank(ex.getMessage()) + "</html>");
        }
    }

    private String valueOrBlank(String value) {
        return value == null ? "" : value.trim();
    }
}
