package com.scenemax.desktop;

import com.scenemaxeng.common.skybox.SkyboxDefinition;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;

public class BevySkyboxSummaryPanel extends JPanel {
    private final File skyboxFile;
    private final Runnable launchDesigner;
    private final JTextArea summaryArea = new JTextArea();
    private final JLabel statusLabel = new JLabel(" ");

    public BevySkyboxSummaryPanel(File skyboxFile, Runnable launchDesigner) {
        super(new BorderLayout(8, 8));
        this.skyboxFile = skyboxFile;
        this.launchDesigner = launchDesigner;
        buildUi();
        refreshSummary();
    }

    private void buildUi() {
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton openButton = new JButton("Open Bevy Designer");
        openButton.addActionListener(e -> {
            if (launchDesigner != null) {
                statusLabel.setText("Starting Bevy skybox designer...");
                launchDesigner.run();
            }
        });
        JButton refreshButton = new JButton("Refresh Summary");
        refreshButton.addActionListener(e -> refreshSummary());
        toolbar.add(openButton);
        toolbar.add(refreshButton);

        summaryArea.setEditable(false);
        summaryArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        summaryArea.setLineWrap(false);
        summaryArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        add(toolbar, BorderLayout.NORTH);
        add(new JScrollPane(summaryArea), BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    public void refreshSummary() {
        try {
            SkyboxDefinition definition = SkyboxDefinition.load(skyboxFile);
            summaryArea.setText(definition.toSummaryText(skyboxFile));
            summaryArea.setCaretPosition(0);
            statusLabel.setText(" ");
        } catch (IOException | RuntimeException ex) {
            summaryArea.setText("Could not read skybox definition:\n" + ex.getMessage());
            statusLabel.setText("Summary refresh failed.");
        }
    }
}
