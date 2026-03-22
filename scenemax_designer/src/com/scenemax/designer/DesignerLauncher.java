package com.scenemax.designer;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;

/**
 * Standalone launcher for testing the designer outside of the IDE.
 * Opens a JFrame with a DesignerPanel for development/debugging.
 */
public class DesignerLauncher {

    /**
     * For standalone testing.
     */
    public static void main(String[] args) {
        // Create a temp .smdesign file for testing
        File tempFile = new File("test_scene.smdesign");
        if (!tempFile.exists()) {
            try {
                DesignerDocument.writeEmptyFile(tempFile);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("SceneMax3D Designer - " + tempFile.getName());
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1280, 720);

            DesignerPanel panel = new DesignerPanel(null, tempFile);
            frame.add(panel, BorderLayout.CENTER);

            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            // Stop the JME3 app when the frame closes
            frame.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent e) {
                    panel.stopDesigner();
                }
            });
        });
    }
}
