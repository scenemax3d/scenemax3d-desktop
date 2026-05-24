package com.scenemax.designer.ik;

import com.jme3.system.AppSettings;
import com.jme3.system.JmeCanvasContext;
import com.scenemax.designer.gizmo.GizmoMode;
import com.scenemaxeng.common.ik.IKLayerDefinition;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

public class IKPreviewPanel extends JPanel {
    private final IKPreviewApp app;
    private final Canvas canvas;
    private final JLabel statusLabel = new JLabel("Preview");
    private final JProgressBar progressBar = new JProgressBar();
    private final JComboBox<String> targetVisualCombo = new JComboBox<>();
    private final JComboBox<String> animationCombo = new JComboBox<>();
    private boolean disposed;

    public IKPreviewPanel(File resourcesRoot) {
        super(new BorderLayout(0, 6));
        setBorder(new EmptyBorder(0, 0, 8, 0));
        setMinimumSize(new Dimension(180, 180));
        setPreferredSize(new Dimension(520, 300));

        app = new IKPreviewApp(resourcesRoot);
        AppSettings settings = new AppSettings(true);
        settings.setWidth(720);
        settings.setHeight(360);
        settings.setSamples(4);
        settings.setVSync(true);
        settings.setFrameRate(60);
        settings.setGammaCorrection(false);

        app.setSettings(settings);
        app.setPauseOnLostFocus(false);
        app.setShowSettings(false);
        app.createCanvas();

        JmeCanvasContext context = (JmeCanvasContext) app.getContext();
        context.setSystemListener(app);
        canvas = context.getCanvas();
        canvas.setMinimumSize(new Dimension(80, 80));
        canvas.setPreferredSize(new Dimension(480, 240));
        canvas.setCursor(Cursor.getDefaultCursor());
        canvas.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (canvas.getWidth() > 0 && canvas.getHeight() > 0) {
                    app.enqueue(() -> {
                        app.reshape(canvas.getWidth(), canvas.getHeight());
                        return null;
                    });
                }
            }
        });

        app.setStatusChangedCallback(status -> SwingUtilities.invokeLater(() -> statusLabel.setText(status)));
        app.setBusyChangedCallback((busy, message) -> SwingUtilities.invokeLater(() -> setBusy(busy, message)));
        app.setTargetVisualOptionsChangedCallback(options -> SwingUtilities.invokeLater(() -> updateTargetVisualOptions(options)));
        app.setAnimationOptionsChangedCallback(options -> SwingUtilities.invokeLater(() -> updateAnimationOptions(options)));
        add(buildToolbar(), BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        add(buildStatusPanel(), BorderLayout.SOUTH);
        app.startCanvas();
    }

    public void setCompatibleModelsChangedCallback(Consumer<List<String>> callback) {
        if (callback == null) {
            app.setCompatibleModelsChangedCallback(null);
            return;
        }
        app.setCompatibleModelsChangedCallback(models -> SwingUtilities.invokeLater(() -> callback.accept(models)));
    }

    public void setJointNamesChangedCallback(Consumer<List<String>> callback) {
        if (callback == null) {
            app.setJointNamesChangedCallback(null);
            return;
        }
        app.setJointNamesChangedCallback(joints -> SwingUtilities.invokeLater(() -> callback.accept(joints)));
    }

    public void setTargetModelId(String modelId) {
        app.setTargetModelId(modelId);
    }

    public void setHighlightedJoints(List<String> jointNames) {
        app.setHighlightedJoints(jointNames);
    }

    public void setHighlightedJoints(java.util.Map<String, String> jointLabels) {
        app.setHighlightedJoints(jointLabels);
    }

    public void runLayerPreview(IKLayerDefinition layer) {
        app.runLayerPreview(layer);
    }

    public void playLayerPreview(IKLayerDefinition layer) {
        app.playLayerPreview(layer);
    }

    public void stopLayerPreview() {
        app.stopLayerPreview();
    }

    public void resetModelPose() {
        app.resetModelPose();
    }

    public void disposePreview() {
        if (disposed) {
            return;
        }
        disposed = true;
        app.stop();
    }

    private JComponent buildToolbar() {
        JPanel toolbar = new JPanel();
        toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.Y_AXIS));

        JPanel toolsRow = toolbarRow();
        toolsRow.add(modeButton("Move", GizmoMode.TRANSLATE));
        toolsRow.add(modeButton("Rotate", GizmoMode.ROTATE));
        toolsRow.add(modeButton("Scale", GizmoMode.SCALE));
        toolsRow.add(new JLabel("Edit"));
        toolsRow.add(editButton("Model", false));
        toolsRow.add(editButton("Target", true));
        toolsRow.add(new JLabel("Target"));
        targetVisualCombo.addItem("Sphere");
        targetVisualCombo.addItem("Box");
        targetVisualCombo.addActionListener(e -> app.setPreviewTargetVisual(String.valueOf(targetVisualCombo.getSelectedItem())));
        targetVisualCombo.setPreferredSize(new Dimension(120, 24));
        toolsRow.add(targetVisualCombo);
        JButton resetView = new JButton("Reset View");
        resetView.addActionListener(e -> app.resetCamera());
        toolsRow.add(resetView);

        JPanel animationRow = toolbarRow();
        animationRow.add(new JLabel("Animation"));
        animationCombo.setPrototypeDisplayValue("MMMMMMMMMMMM");
        animationCombo.addItem("");
        animationCombo.setPreferredSize(new Dimension(160, 24));
        animationRow.add(animationCombo);
        JButton playAnimation = new JButton("Play Anim");
        playAnimation.addActionListener(e -> app.playAnimation(String.valueOf(animationCombo.getSelectedItem())));
        animationRow.add(playAnimation);
        JButton stopAnimation = new JButton("Stop Anim");
        stopAnimation.addActionListener(e -> app.stopAnimation());
        animationRow.add(stopAnimation);

        toolbar.add(toolsRow);
        toolbar.add(animationRow);
        return toolbar;
    }

    private JPanel toolbarRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        return row;
    }

    private JComponent buildStatusPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 3));
        progressBar.setIndeterminate(true);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        panel.add(progressBar, BorderLayout.NORTH);
        panel.add(statusLabel, BorderLayout.SOUTH);
        return panel;
    }

    private JButton modeButton(String label, GizmoMode mode) {
        JButton button = new JButton(label);
        button.addActionListener(e -> app.setGizmoMode(mode));
        return button;
    }

    private JButton editButton(String label, boolean target) {
        JButton button = new JButton(label);
        button.addActionListener(e -> app.setEditingPreviewTarget(target));
        return button;
    }

    private void setBusy(boolean busy, String message) {
        progressBar.setVisible(busy);
        progressBar.setIndeterminate(busy);
        progressBar.setString(busy ? (message == null ? "Preparing preview..." : message) : "");
        if (message != null && !message.isBlank()) {
            statusLabel.setText(message);
        }
    }

    private void updateTargetVisualOptions(List<String> options) {
        String current = String.valueOf(targetVisualCombo.getSelectedItem());
        targetVisualCombo.removeAllItems();
        boolean hasCurrent = current != null && !current.isBlank();
        if (options != null) {
            for (String option : options) {
                if (option == null || option.isBlank()) {
                    continue;
                }
                targetVisualCombo.addItem(option);
                if (option.equals(current)) {
                    hasCurrent = true;
                }
            }
        }
        if (!hasCurrent && current != null && !current.isBlank()) {
            targetVisualCombo.addItem(current);
        }
        if (targetVisualCombo.getItemCount() > 0) {
            targetVisualCombo.setSelectedItem(hasCurrent ? current : targetVisualCombo.getItemAt(0));
        }
    }

    private void updateAnimationOptions(List<String> options) {
        String current = String.valueOf(animationCombo.getSelectedItem());
        animationCombo.removeAllItems();
        animationCombo.addItem("");
        boolean hasCurrent = current == null || current.isBlank();
        if (options != null) {
            for (String option : options) {
                if (option == null || option.isBlank()) {
                    continue;
                }
                animationCombo.addItem(option);
                if (option.equals(current)) {
                    hasCurrent = true;
                }
            }
        }
        if (!hasCurrent && current != null && !current.isBlank()) {
            animationCombo.addItem(current);
        }
        animationCombo.setSelectedItem(hasCurrent ? current : "");
    }
}
