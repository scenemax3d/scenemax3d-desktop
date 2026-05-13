package com.scenemax.designer.weapon;

import com.jme3.system.AppSettings;
import com.jme3.system.JmeCanvasContext;
import com.scenemax.designer.gizmo.GizmoMode;
import com.scenemaxeng.common.weapons.WeaponAttachmentTransform;
import com.scenemaxeng.common.weapons.WeaponDefinition;
import org.lwjgl.input.Mouse;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

public class WeaponPreviewPanel extends JPanel {
    private final WeaponPreviewApp app;
    private final Canvas canvas;
    private final JLabel statusLabel = new JLabel("Preview");
    private final JComboBox<String> previewAnimationCombo = new JComboBox<>();
    private final JSlider animationSpeedSlider = new JSlider(1, 100, 100);
    private final JLabel animationSpeedValue = new JLabel("100%");
    private final JLabel animationPercentValue = new JLabel("--%");
    private final JButton stopResumeButton = new JButton("Stop");

    private Consumer<WeaponAttachmentTransform> transformChangedCallback;
    private Consumer<String> attachmentPointChangedCallback;
    private Consumer<List<String>> attachmentPointsChangedCallback;
    private boolean updatingPreviewAnimations;
    private boolean animationPaused;
    private String selectedAttachmentPoint = "";
    private int selectedPostureIndex;

    public WeaponPreviewPanel(File resourcesRoot) {
        super(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(0, 10, 0, 0));
        setMinimumSize(new Dimension(180, 180));
        setPreferredSize(new Dimension(420, 420));

        app = new WeaponPreviewApp(resourcesRoot);
        AppSettings settings = new AppSettings(true);
        settings.setWidth(640);
        settings.setHeight(520);
        settings.setSamples(4);
        settings.setVSync(true);
        settings.setFrameRate(60);
        settings.setGammaCorrection(false);

        app.setSettings(settings);
        app.setPauseOnLostFocus(false);
        app.setShowSettings(false);
        app.createCanvas();

        JmeCanvasContext ctx = (JmeCanvasContext) app.getContext();
        ctx.setSystemListener(app);
        canvas = ctx.getCanvas();
        canvas.setMinimumSize(new Dimension(80, 80));
        canvas.setPreferredSize(new Dimension(360, 320));
        canvas.setCursor(Cursor.getDefaultCursor());
        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                releaseMouseCapture();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                releaseMouseCapture();
            }
        });
        canvas.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                releaseMouseCapture();
            }
        });
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

        app.setTransformChangedCallback(transform -> {
            if (transformChangedCallback != null) {
                SwingUtilities.invokeLater(() -> transformChangedCallback.accept(transform));
            }
        });
        app.setAttachmentPointsChangedCallback(points ->
                SwingUtilities.invokeLater(() -> {
                    if (attachmentPointsChangedCallback != null) {
                        attachmentPointsChangedCallback.accept(points);
                    }
                }));
        app.setAnimationNamesChangedCallback(names ->
                SwingUtilities.invokeLater(() -> updatePreviewAnimationOptions(names)));
        app.setAnimationPercentChangedCallback(percent ->
                SwingUtilities.invokeLater(() -> updateAnimationPercent(percent)));
        app.setStatusChangedCallback(status ->
                SwingUtilities.invokeLater(() -> statusLabel.setText(status)));

        add(buildToolbar(), BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
        app.startCanvas();
    }

    public void setTransformChangedCallback(Consumer<WeaponAttachmentTransform> callback) {
        this.transformChangedCallback = callback;
    }

    public void setAttachmentPointChangedCallback(Consumer<String> callback) {
        this.attachmentPointChangedCallback = callback;
    }

    public void setAttachmentPointsChangedCallback(Consumer<List<String>> callback) {
        this.attachmentPointsChangedCallback = callback;
    }

    public void setWeaponDefinition(WeaponDefinition definition) {
        selectedAttachmentPoint = definition == null || definition.getDefaultPosture() == null
                ? ""
                : nullToEmpty(definition.getDefaultPosture().getAttachmentPoint());
        app.setSelectedPostureIndex(selectedPostureIndex);
        app.setWeaponDefinition(definition);
        String name = definition == null || definition.getId() == null || definition.getId().isBlank()
                ? "Preview"
                : definition.getId();
        statusLabel.setText(name);
    }

    public void setSelectedPostureIndex(int selectedPostureIndex) {
        this.selectedPostureIndex = Math.max(0, selectedPostureIndex);
        app.setSelectedPostureIndex(this.selectedPostureIndex);
    }

    public void refreshAssets() {
    }

    public void setHolderModelId(String modelId) {
        app.setHolderModelId(modelId == null || modelId.trim().isEmpty() ? null : modelId.trim());
    }

    public void setPreviewAnimation(String animationName) {
        app.setPreviewAnimation(animationName);
    }

    public void setAttachmentPoint(String attachmentPoint) {
        selectedAttachmentPoint = nullToEmpty(attachmentPoint);
        app.setAttachmentPoint(selectedAttachmentPoint);
        if (attachmentPointChangedCallback != null) {
            attachmentPointChangedCallback.accept(selectedAttachmentPoint);
        }
    }

    public void disposePreview() {
        releaseMouseCapture();
        app.stop();
    }

    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel();
        toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.Y_AXIS));

        JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 3));
        modeRow.add(modeButton("Move", GizmoMode.TRANSLATE));
        modeRow.add(modeButton("Rotate", GizmoMode.ROTATE));
        JButton resetView = new JButton("Reset View");
        resetView.addActionListener(e -> app.resetCamera());
        modeRow.add(resetView);
        toolbar.add(modeRow);

        JPanel animationRow = new JPanel(new BorderLayout(6, 3));
        animationRow.add(new JLabel("Test Animation"), BorderLayout.WEST);
        previewAnimationCombo.setPrototypeDisplayValue("MMMMMMMMMMMMMMMMMMMM");
        previewAnimationCombo.addActionListener(e -> {
            if (!updatingPreviewAnimations) {
                app.setPreviewAnimation(selectedPreviewAnimation());
            }
        });
        animationRow.add(previewAnimationCombo, BorderLayout.CENTER);
        toolbar.add(animationRow);

        JPanel playbackRow = new JPanel(new BorderLayout(6, 3));
        playbackRow.add(new JLabel("Animation Speed"), BorderLayout.WEST);
        animationSpeedSlider.setMajorTickSpacing(25);
        animationSpeedSlider.setMinorTickSpacing(5);
        animationSpeedSlider.setPaintTicks(true);
        animationSpeedSlider.addChangeListener(e -> {
            int value = animationSpeedSlider.getValue();
            animationSpeedValue.setText(value + "%");
            app.setPreviewAnimationSpeedPercent(value);
        });
        playbackRow.add(animationSpeedSlider, BorderLayout.CENTER);
        animationSpeedValue.setHorizontalAlignment(SwingConstants.RIGHT);
        animationSpeedValue.setPreferredSize(new Dimension(44, animationSpeedValue.getPreferredSize().height));
        playbackRow.add(animationSpeedValue, BorderLayout.EAST);
        toolbar.add(playbackRow);

        JPanel controlRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 3));
        stopResumeButton.addActionListener(e -> toggleAnimationPaused());
        controlRow.add(stopResumeButton);
        controlRow.add(new JLabel("Animation Index"));
        animationPercentValue.setPreferredSize(new Dimension(44, animationPercentValue.getPreferredSize().height));
        controlRow.add(animationPercentValue);
        toolbar.add(controlRow);
        return toolbar;
    }

    private JButton modeButton(String label, GizmoMode mode) {
        JButton button = new JButton(label);
        button.addActionListener(e -> app.setGizmoMode(mode));
        return button;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private void updatePreviewAnimationOptions(List<String> animations) {
        String current = selectedPreviewAnimation();
        updatingPreviewAnimations = true;
        previewAnimationCombo.removeAllItems();
        previewAnimationCombo.addItem("");
        boolean hasCurrent = current.isEmpty();
        if (animations != null) {
            for (String animation : animations) {
                if (animation == null || animation.trim().isEmpty()) {
                    continue;
                }
                previewAnimationCombo.addItem(animation);
                if (animation.equals(current)) {
                    hasCurrent = true;
                }
            }
        }
        if (!hasCurrent) {
            previewAnimationCombo.addItem(current);
        }
        previewAnimationCombo.setSelectedItem(current);
        updatingPreviewAnimations = false;
        app.setPreviewAnimation(selectedPreviewAnimation());
        app.setPreviewAnimationSpeedPercent(animationSpeedSlider.getValue());
        app.setPreviewAnimationPaused(animationPaused);
    }

    private String selectedPreviewAnimation() {
        Object selected = previewAnimationCombo.getSelectedItem();
        return selected == null ? "" : String.valueOf(selected).trim();
    }

    private void toggleAnimationPaused() {
        animationPaused = !animationPaused;
        stopResumeButton.setText(animationPaused ? "Resume" : "Stop");
        app.setPreviewAnimationPaused(animationPaused);
    }

    private void updateAnimationPercent(int percent) {
        animationPercentValue.setText(percent < 0 ? "--%" : percent + "%");
    }

    private void releaseMouseCapture() {
        canvas.setCursor(Cursor.getDefaultCursor());
        try {
            if (Mouse.isCreated() && Mouse.isGrabbed()) {
                Mouse.setGrabbed(false);
            }
        } catch (Throwable ignored) {
        }
    }
}
