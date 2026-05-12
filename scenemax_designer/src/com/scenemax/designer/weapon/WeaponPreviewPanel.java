package com.scenemax.designer.weapon;

import com.jme3.system.AppSettings;
import com.jme3.system.JmeCanvasContext;
import com.scenemax.designer.gizmo.GizmoMode;
import com.scenemaxeng.common.types.AssetsMapping;
import com.scenemaxeng.common.types.ResourceSetup;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class WeaponPreviewPanel extends JPanel {
    private final File resourcesRoot;
    private final WeaponPreviewApp app;
    private final Canvas canvas;
    private final JComboBox<String> holderCombo = new JComboBox<>();
    private final JComboBox<String> attachmentCombo = new JComboBox<>();
    private final JLabel statusLabel = new JLabel("Preview");

    private Consumer<WeaponAttachmentTransform> transformChangedCallback;
    private Consumer<String> attachmentPointChangedCallback;
    private boolean updatingAttachmentCombo;
    private String selectedAttachmentPoint = "";
    private int selectedAttackIndex;

    public WeaponPreviewPanel(File resourcesRoot) {
        super(new BorderLayout(0, 8));
        this.resourcesRoot = resourcesRoot;
        setBorder(new EmptyBorder(0, 10, 0, 0));

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
        canvas.setMinimumSize(new Dimension(260, 260));
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
                SwingUtilities.invokeLater(() -> updateAttachmentPoints(points)));
        app.setStatusChangedCallback(status ->
                SwingUtilities.invokeLater(() -> statusLabel.setText(status)));

        add(buildToolbar(), BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
        reloadHolderModels();
        app.startCanvas();
    }

    public void setTransformChangedCallback(Consumer<WeaponAttachmentTransform> callback) {
        this.transformChangedCallback = callback;
    }

    public void setAttachmentPointChangedCallback(Consumer<String> callback) {
        this.attachmentPointChangedCallback = callback;
    }

    public void setWeaponDefinition(WeaponDefinition definition) {
        selectedAttachmentPoint = definition == null ? "" : nullToEmpty(definition.getDefaultAttachmentPoint());
        selectAttachmentPoint(selectedAttachmentPoint);
        app.setSelectedAttackIndex(selectedAttackIndex);
        app.setWeaponDefinition(definition);
        String name = definition == null || definition.getName() == null || definition.getName().isBlank()
                ? "Preview"
                : definition.getName();
        statusLabel.setText(name);
    }

    public void setSelectedAttackIndex(int selectedAttackIndex) {
        this.selectedAttackIndex = Math.max(0, selectedAttackIndex);
        app.setSelectedAttackIndex(this.selectedAttackIndex);
    }

    public void refreshAssets() {
        reloadHolderModels();
    }

    public void disposePreview() {
        releaseMouseCapture();
        app.stop();
    }

    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 0, 4, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridy = 0;

        gbc.gridx = 0;
        gbc.weightx = 0;
        toolbar.add(new JLabel("Using Model"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        toolbar.add(holderCombo, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> reloadHolderModels());
        toolbar.add(refreshButton, gbc);

        gbc.gridy = 1;
        gbc.gridx = 0;
        toolbar.add(new JLabel("Attach To"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        toolbar.add(attachmentCombo, gbc);
        gbc.gridx = 2;
        gbc.weightx = 0;
        JButton clearAttachButton = new JButton("Clear");
        clearAttachButton.addActionListener(e -> chooseAttachmentPoint(""));
        toolbar.add(clearAttachButton, gbc);

        attachmentCombo.addActionListener(e -> {
            if (updatingAttachmentCombo) {
                return;
            }
            Object selected = attachmentCombo.getSelectedItem();
            chooseAttachmentPoint(selected == null ? "" : String.valueOf(selected));
        });

        gbc.gridy = 2;
        gbc.gridx = 0;
        toolbar.add(modeButton("Move", GizmoMode.TRANSLATE), gbc);
        gbc.gridx = 1;
        toolbar.add(modeButton("Rotate", GizmoMode.ROTATE), gbc);
        gbc.gridx = 2;
        JButton resetView = new JButton("Reset View");
        resetView.addActionListener(e -> app.resetCamera());
        toolbar.add(resetView, gbc);

        gbc.gridy = 3;
        gbc.gridx = 0;
        toolbar.add(testButton("Test Attack", () -> app.previewSelectedAttack()), gbc);
        gbc.gridx = 1;
        toolbar.add(testButton("Test Reload", () -> app.previewReload()), gbc);
        gbc.gridx = 2;
        toolbar.add(testButton("Stop Test", () -> app.stopAttackPreview()), gbc);

        gbc.gridy = 4;
        gbc.gridx = 0;
        gbc.gridwidth = 3;
        toolbar.add(nudgePanel(), gbc);
        return toolbar;
    }

    private JButton modeButton(String label, GizmoMode mode) {
        JButton button = new JButton(label);
        button.addActionListener(e -> app.setGizmoMode(mode));
        return button;
    }

    private JButton testButton(String label, Runnable action) {
        JButton button = new JButton(label);
        button.addActionListener(e -> action.run());
        return button;
    }

    private JPanel nudgePanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        panel.add(nudgeButton("X-", () -> app.nudgeOffset(-0.01f, 0f, 0f)));
        panel.add(nudgeButton("X+", () -> app.nudgeOffset(0.01f, 0f, 0f)));
        panel.add(nudgeButton("Y-", () -> app.nudgeOffset(0f, -0.01f, 0f)));
        panel.add(nudgeButton("Y+", () -> app.nudgeOffset(0f, 0.01f, 0f)));
        panel.add(nudgeButton("Z-", () -> app.nudgeOffset(0f, 0f, -0.01f)));
        panel.add(nudgeButton("Z+", () -> app.nudgeOffset(0f, 0f, 0.01f)));
        panel.add(nudgeButton("RX", () -> app.nudgeRotation(1f, 0f, 0f)));
        panel.add(nudgeButton("RY", () -> app.nudgeRotation(0f, 1f, 0f)));
        panel.add(nudgeButton("RZ", () -> app.nudgeRotation(0f, 0f, 1f)));
        panel.add(nudgeButton("S-", () -> app.scaleWeapon(0.9f)));
        panel.add(nudgeButton("S+", () -> app.scaleWeapon(1.1f)));
        panel.add(nudgeButton("SX+", () -> app.nudgeScale(0.01f, 0f, 0f)));
        panel.add(nudgeButton("SY+", () -> app.nudgeScale(0f, 0.01f, 0f)));
        panel.add(nudgeButton("SZ+", () -> app.nudgeScale(0f, 0f, 0.01f)));
        return panel;
    }

    private JButton nudgeButton(String label, Runnable action) {
        JButton button = new JButton(label);
        button.setMargin(new Insets(2, 6, 2, 6));
        button.addActionListener(e -> action.run());
        return button;
    }

    private void reloadHolderModels() {
        List<String> models = listModelReferences();
        String previous = holderCombo.getSelectedItem() == null ? null : String.valueOf(holderCombo.getSelectedItem());
        for (java.awt.event.ActionListener listener : holderCombo.getActionListeners()) {
            holderCombo.removeActionListener(listener);
        }
        holderCombo.removeAllItems();
        for (String model : models) {
            holderCombo.addItem(model);
        }
        if (previous != null && models.contains(previous)) {
            holderCombo.setSelectedItem(previous);
        } else if (!models.isEmpty()) {
            holderCombo.setSelectedIndex(0);
        }
        holderCombo.addActionListener(e -> {
            Object selected = holderCombo.getSelectedItem();
            app.setHolderModelId(selected == null ? null : String.valueOf(selected));
        });
        Object selected = holderCombo.getSelectedItem();
        app.setHolderModelId(selected == null ? null : String.valueOf(selected));
    }

    private void updateAttachmentPoints(List<String> points) {
        updatingAttachmentCombo = true;
        attachmentCombo.removeAllItems();
        attachmentCombo.addItem("");
        boolean hasSelected = selectedAttachmentPoint == null || selectedAttachmentPoint.isEmpty();
        for (String point : points) {
            if (point == null || point.trim().isEmpty()) {
                continue;
            }
            attachmentCombo.addItem(point);
            if (point.equals(selectedAttachmentPoint)) {
                hasSelected = true;
            }
        }
        if (!hasSelected) {
            attachmentCombo.addItem(selectedAttachmentPoint);
        }
        selectAttachmentPoint(selectedAttachmentPoint);
        updatingAttachmentCombo = false;
    }

    private void selectAttachmentPoint(String attachmentPoint) {
        updatingAttachmentCombo = true;
        attachmentCombo.setSelectedItem(nullToEmpty(attachmentPoint));
        updatingAttachmentCombo = false;
    }

    private void chooseAttachmentPoint(String attachmentPoint) {
        selectedAttachmentPoint = nullToEmpty(attachmentPoint);
        selectAttachmentPoint(selectedAttachmentPoint);
        app.setAttachmentPoint(selectedAttachmentPoint);
        if (attachmentPointChangedCallback != null) {
            attachmentPointChangedCallback.accept(selectedAttachmentPoint);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private List<String> listModelReferences() {
        if (resourcesRoot == null || !resourcesRoot.isDirectory()) {
            return Collections.emptyList();
        }
        AssetsMapping assets = new AssetsMapping(resourcesRoot.getAbsolutePath());
        List<String> values = new ArrayList<>();
        for (ResourceSetup resource : assets.get3DModelsIndex().values()) {
            if (resource != null && resource.name != null && !resource.name.trim().isEmpty()) {
                values.add(resource.name);
            }
        }
        return values.stream()
                .filter(value -> value != null && !value.trim().isEmpty())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
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
