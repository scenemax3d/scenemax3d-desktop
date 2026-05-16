package com.scenemax.designer.motion;

import com.scenemaxeng.common.motion.ThrowMotionDefinition;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ThrowMotionDesignerPanel extends JPanel {
    private final File motionFile;
    private ThrowMotionDefinition document;
    private boolean dirty;
    private boolean updatingUi;
    private Runnable onDirtyCallback;
    private Runnable onSavedCallback;
    private ThrowMotionPreviewPanel previewPanel;
    private Timer previewRefreshTimer;
    private String lastPreviewSnapshot = "";

    private final JTextField txtId = new JTextField();
    private final JTextField txtName = new JTextField();
    private final JComboBox<MotionTypeItem> cboMotionType = new JComboBox<>(new MotionTypeItem[]{
            new MotionTypeItem(ThrowMotionDefinition.TYPE_TARGET_ARC),
            new MotionTypeItem(ThrowMotionDefinition.TYPE_BALLISTIC),
            new MotionTypeItem(ThrowMotionDefinition.TYPE_STRAIGHT),
            new MotionTypeItem(ThrowMotionDefinition.TYPE_HOMING),
            new MotionTypeItem(ThrowMotionDefinition.TYPE_RETURNING),
            new MotionTypeItem(ThrowMotionDefinition.TYPE_PHYSICS)
    });
    private final JPanel parameterPanel = new JPanel(new BorderLayout());

    private final NumberField initialSpeed = num(16, 0, 999, 0.1);
    private final NumberField launchAngle = num(35, -89, 89, 1);
    private final NumberField gravityScale = num(1, 0, 20, 0.05);
    private final NumberField duration = num(1.2, 0.05, 60, 0.05);
    private final NumberField arcHeight = num(3, -100, 100, 0.1);
    private final JComboBox<String> easing = new JComboBox<>(new String[]{"linear", "ease_in", "ease_out", "ease_in_out"});
    private final NumberField speed = num(18, 0, 999, 0.1);
    private final NumberField acceleration = num(0, -999, 999, 0.1);
    private final NumberField maxDistance = num(30, 0, 9999, 0.5);
    private final NumberField maxLifetime = num(4, 0.05, 999, 0.1);
    private final NumberField spinSpeed = num(720, -9999, 9999, 5);
    private final JComboBox<String> collisionMode = new JComboBox<>(new String[]{"none", "raycast", "spherecast", "bounding_volume"});
    private final NumberField collisionRadius = num(0.25, 0, 100, 0.01);
    private final JCheckBox stopOnImpact = new JCheckBox();
    private final JCheckBox alignToVelocity = new JCheckBox();
    private final JCheckBox alignToPath = new JCheckBox();
    private final JComboBox<String> targetMode = new JComboBox<>(new String[]{"point", "object", "bone", "cursor_position", "forward_distance"});
    private final NumberField turnRate = num(180, 1, 3600, 5);
    private final NumberField homingDelay = num(0.15, 0, 60, 0.05);
    private final NumberField homingStrength = num(1, 0, 10, 0.05);
    private final JComboBox<String> loseTargetBehavior = new JComboBox<>(new String[]{"continue", "stop", "fall", "switch_to_physics"});
    private final NumberField outboundDuration = num(0.75, 0.05, 60, 0.05);
    private final NumberField outboundDistance = num(12, 0, 999, 0.5);
    private final NumberField outboundArcHeight = num(1, -100, 100, 0.1);
    private final NumberField returnSpeed = num(18, 0, 999, 0.1);
    private final NumberField returnDelay = num(0.15, 0, 60, 0.05);
    private final JCheckBox canHitOnReturn = new JCheckBox();
    private final NumberField catchRadius = num(0.75, 0, 100, 0.05);
    private final JComboBox<String> forceMode = new JComboBox<>(new String[]{"impulse", "velocity", "continuous_force"});
    private final NumberField massOverride = num(0, 0, 999, 0.1);
    private final NumberField drag = num(0, 0, 999, 0.01);
    private final JComboBox<String> impactBehavior = new JComboBox<>(new String[]{"stop", "destroy", "stick", "bounce", "switch_to_physics", "return"});

    public ThrowMotionDesignerPanel(File motionFile) {
        super(new BorderLayout());
        this.motionFile = motionFile;
        setMinimumSize(new Dimension(0, 0));
        loadDocument();
        buildUi();
        refreshFromDocument();
    }

    public void setOnDirtyCallback(Runnable onDirtyCallback) {
        this.onDirtyCallback = onDirtyCallback;
    }

    public void setOnSavedCallback(Runnable onSavedCallback) {
        this.onSavedCallback = onSavedCallback;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void saveDocument() {
        applyUiToDocument();
        try {
            document.save(motionFile);
            exportRuntimeResource();
            dirty = false;
            if (onSavedCallback != null) {
                onSavedCallback.run();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error saving throw motion: " + ex.getMessage(),
                    "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void reloadFromDisk() {
        loadDocument();
        dirty = false;
        refreshFromDocument();
        if (onSavedCallback != null) {
            onSavedCallback.run();
        }
    }

    public void discardEditorState() {
        dirty = false;
    }

    public void activatePanel() {
    }

    public void deactivatePanel() {
        if (dirty) {
            saveDocument();
        }
    }

    public void clearAndDeactivatePanel() {
        deactivatePanel();
        if (previewRefreshTimer != null) {
            previewRefreshTimer.stop();
        }
        if (previewPanel != null) {
            previewPanel.disposePreview();
        }
    }

    private void loadDocument() {
        try {
            if (motionFile.exists() && motionFile.length() > 0) {
                document = ThrowMotionDefinition.load(motionFile);
            } else {
                document = ThrowMotionDefinition.createTemplate(stripExtension(motionFile.getName()),
                        ThrowMotionDefinition.TYPE_TARGET_ARC);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            document = ThrowMotionDefinition.createTemplate(stripExtension(motionFile.getName()),
                    ThrowMotionDefinition.TYPE_TARGET_ARC);
        }
    }

    private void buildUi() {
        setBorder(new EmptyBorder(12, 12, 12, 12));
        JPanel header = new JPanel(new BorderLayout(8, 2));
        JLabel title = new JLabel("Throw Motion Designer");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        header.add(title, BorderLayout.WEST);
        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> saveDocument());
        header.add(saveButton, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        previewPanel = new ThrowMotionPreviewPanel(findResourcesRoot());

        Component editorPanel = buildOverviewTab();
        editorPanel.setMinimumSize(new Dimension(300, 200));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editorPanel, previewPanel);
        split.setContinuousLayout(true);
        split.setOneTouchExpandable(true);
        split.setResizeWeight(0.52);
        split.setDividerLocation(600);
        add(split, BorderLayout.CENTER);

        previewRefreshTimer = new Timer(160, e -> refreshPreviewFromUi());
        previewRefreshTimer.setRepeats(false);
        bindDirtyTracking((Container) editorPanel);
        cboMotionType.addActionListener(e -> {
            if (!updatingUi) {
                applyUiToDocument();
                rebuildParameterPanel();
                markDirty();
            }
        });
    }

    private Component buildOverviewTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel overview = form(
                row("Motion ID", txtId),
                row("Name", txtName),
                row("Motion Type", cboMotionType)
        );
        overview.setBorder(BorderFactory.createTitledBorder("Overview"));
        panel.add(overview, BorderLayout.NORTH);

        parameterPanel.setBorder(BorderFactory.createTitledBorder("Motion"));
        panel.add(parameterPanel, BorderLayout.CENTER);
        return scroll(panel);
    }

    private void rebuildParameterPanel() {
        String type = selectedMotionType();
        List<Object[]> rows = new ArrayList<>();
        if (ThrowMotionDefinition.TYPE_PHYSICS.equals(type)) {
            Collections.addAll(rows,
                    row("Initial Speed", initialSpeed),
                    row("Upward Angle", launchAngle),
                    row("Force Mode", forceMode),
                    row("Mass Override", massOverride),
                    row("Drag", drag),
                    row("Gravity Scale", gravityScale),
                    row("Max Lifetime", maxLifetime));
        } else if (ThrowMotionDefinition.TYPE_BALLISTIC.equals(type)) {
            Collections.addAll(rows,
                    row("Initial Speed", initialSpeed),
                    row("Launch Angle", launchAngle),
                    row("Gravity Scale", gravityScale),
                    row("Max Distance", maxDistance),
                    row("Max Lifetime", maxLifetime),
                    row("Align To Velocity", alignToVelocity));
        } else if (ThrowMotionDefinition.TYPE_STRAIGHT.equals(type)) {
            Collections.addAll(rows,
                    row("Speed", speed),
                    row("Acceleration", acceleration),
                    row("Max Distance", maxDistance),
                    row("Max Lifetime", maxLifetime),
                    row("Align To Velocity", alignToVelocity));
        } else if (ThrowMotionDefinition.TYPE_HOMING.equals(type)) {
            Collections.addAll(rows,
                    row("Speed", speed),
                    row("Acceleration", acceleration),
                    row("Turn Rate", turnRate),
                    row("Homing Delay", homingDelay),
                    row("Homing Strength", homingStrength),
                    row("Lose Target", loseTargetBehavior),
                    row("Max Lifetime", maxLifetime));
        } else if (ThrowMotionDefinition.TYPE_RETURNING.equals(type)) {
            Collections.addAll(rows,
                    row("Outbound Duration", outboundDuration),
                    row("Outbound Distance", outboundDistance),
                    row("Outbound Arc Height", outboundArcHeight),
                    row("Return Speed", returnSpeed),
                    row("Return Delay", returnDelay),
                    row("Can Hit On Return", canHitOnReturn),
                    row("Catch Radius", catchRadius),
                    row("Easing", easing));
        } else {
            Collections.addAll(rows,
                    row("Duration", duration),
                    row("Arc Height", arcHeight),
                    row("Easing", easing),
                    row("Align To Path", alignToPath));
        }
        parameterPanel.removeAll();
        parameterPanel.add(form(rows.toArray()), BorderLayout.NORTH);
        bindDirtyTracking(parameterPanel);
        parameterPanel.revalidate();
        parameterPanel.repaint();
    }

    private void refreshFromDocument() {
        updatingUi = true;
        txtId.setText(document.getId());
        txtName.setText(document.getDisplayName());
        selectMotionType(document.getMotionType());
        setParameterFields(document.getParameters());
        updatingUi = false;
        rebuildParameterPanel();
        refreshPreviewFromUi();
    }

    private void setParameterFields(ThrowMotionDefinition.MotionParameters p) {
        initialSpeed.setValue(p.initialSpeed);
        launchAngle.setValue(p.launchAngle);
        gravityScale.setValue(p.gravityScale);
        duration.setValue(p.duration);
        arcHeight.setValue(p.arcHeight);
        easing.setSelectedItem(p.easingFunction);
        speed.setValue(p.speed);
        acceleration.setValue(p.acceleration);
        maxDistance.setValue(p.maxDistance);
        maxLifetime.setValue(p.maxLifetime);
        spinSpeed.setValue(p.spinSpeed);
        collisionMode.setSelectedItem(p.collisionMode);
        collisionRadius.setValue(p.collisionRadius);
        stopOnImpact.setSelected(p.stopOnImpact);
        alignToVelocity.setSelected(p.alignToVelocity);
        alignToPath.setSelected(p.alignToPath);
        targetMode.setSelectedItem(p.targetMode);
        turnRate.setValue(p.turnRate);
        homingDelay.setValue(p.homingDelay);
        homingStrength.setValue(p.homingStrength);
        loseTargetBehavior.setSelectedItem(p.loseTargetBehavior);
        outboundDuration.setValue(p.outboundDuration);
        outboundDistance.setValue(p.outboundDistance);
        outboundArcHeight.setValue(p.outboundArcHeight);
        returnSpeed.setValue(p.returnSpeed);
        returnDelay.setValue(p.returnDelay);
        canHitOnReturn.setSelected(p.canHitOnReturn);
        catchRadius.setValue(p.catchRadius);
        forceMode.setSelectedItem(p.forceMode);
        massOverride.setValue(p.massOverride);
        drag.setValue(p.drag);
        impactBehavior.setSelectedItem(p.impactBehavior);
    }

    private void applyUiToDocument() {
        if (document == null) {
            return;
        }
        document.setId(txtId.getText().trim());
        document.setDisplayName(txtName.getText().trim());
        document.setMotionType(selectedMotionType());
        ThrowMotionDefinition.MotionParameters p = document.getParameters();
        p.initialSpeed = initialSpeed.getDoubleValue();
        p.launchAngle = launchAngle.getDoubleValue();
        p.gravityScale = gravityScale.getDoubleValue();
        p.duration = duration.getDoubleValue();
        p.arcHeight = arcHeight.getDoubleValue();
        p.easingFunction = selectedComboValue(easing);
        p.speed = speed.getDoubleValue();
        p.acceleration = acceleration.getDoubleValue();
        p.maxDistance = maxDistance.getDoubleValue();
        p.maxLifetime = maxLifetime.getDoubleValue();
        p.alignToVelocity = alignToVelocity.isSelected();
        p.alignToPath = alignToPath.isSelected();
        p.turnRate = turnRate.getDoubleValue();
        p.homingDelay = homingDelay.getDoubleValue();
        p.homingStrength = homingStrength.getDoubleValue();
        p.loseTargetBehavior = selectedComboValue(loseTargetBehavior);
        p.outboundDuration = outboundDuration.getDoubleValue();
        p.outboundDistance = outboundDistance.getDoubleValue();
        p.outboundArcHeight = outboundArcHeight.getDoubleValue();
        p.returnSpeed = returnSpeed.getDoubleValue();
        p.returnDelay = returnDelay.getDoubleValue();
        p.canHitOnReturn = canHitOnReturn.isSelected();
        p.catchRadius = catchRadius.getDoubleValue();
        p.forceMode = selectedComboValue(forceMode);
        p.massOverride = massOverride.getDoubleValue();
        p.drag = drag.getDoubleValue();
        p.switchToPhysicsOnImpact = ThrowMotionDefinition.TYPE_PHYSICS.equals(selectedMotionType());
    }

    private void refreshPreviewFromUi() {
        if (previewPanel == null || updatingUi) {
            return;
        }
        applyUiToDocument();
        String snapshot = document.toJSON().toString();
        if (snapshot.equals(lastPreviewSnapshot)) {
            return;
        }
        lastPreviewSnapshot = snapshot;
        previewPanel.setThrowMotionDefinition(document);
    }

    private File findResourcesRoot() {
        File current = motionFile.getAbsoluteFile().getParentFile();
        while (current != null) {
            if ("resources".equalsIgnoreCase(current.getName())) {
                return current;
            }
            File resources = new File(current, "resources");
            if (resources.isDirectory()) {
                return resources;
            }
            current = current.getParentFile();
        }
        return null;
    }

    private void exportRuntimeResource() throws IOException {
        File resourcesRoot = findResourcesRoot();
        if (resourcesRoot == null) {
            JOptionPane.showMessageDialog(this,
                    "The throw motion was saved, but SceneMax could not find this project's resources folder.",
                    "Runtime Resource Not Exported", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String resourceId = document.getId() == null ? "" : document.getId().trim();
        if (resourceId.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Motion ID is required before SceneMax can export the runtime resource.",
                    "Runtime Resource Not Exported", JOptionPane.WARNING_MESSAGE);
            return;
        }

        File runtimeFolder = new File(resourcesRoot, "throw_motions");
        if (!runtimeFolder.exists() && !runtimeFolder.mkdirs()) {
            throw new IOException("Unable to create " + runtimeFolder.getAbsolutePath());
        }

        File target = new File(runtimeFolder, runtimeFileName(resourceId));
        document.save(target);
        deleteDuplicateRuntimeResources(resourcesRoot, target, resourceId);
    }

    private void deleteDuplicateRuntimeResources(File resourcesRoot, File keepFile, String resourceId) {
        List<File> roots = new ArrayList<>();
        roots.add(new File(resourcesRoot, "throw_motions"));
        roots.add(new File(resourcesRoot, "ThrowMotions"));
        String normalizedId = resourceId.trim().toLowerCase(Locale.ROOT);

        for (File root : roots) {
            if (!root.isDirectory()) {
                continue;
            }
            Collection<File> files = org.apache.commons.io.FileUtils.listFiles(root, new String[]{"smmotion"}, true);
            for (File file : files) {
                if (sameFile(file, keepFile)) {
                    continue;
                }
                try {
                    ThrowMotionDefinition existing = ThrowMotionDefinition.load(file);
                    String existingId = existing.getId() == null ? "" : existing.getId().trim().toLowerCase(Locale.ROOT);
                    if (normalizedId.equals(existingId) && isUnder(file, resourcesRoot)) {
                        java.nio.file.Files.deleteIfExists(file.toPath());
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    private boolean sameFile(File a, File b) {
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (IOException ignored) {
            return a.getAbsoluteFile().equals(b.getAbsoluteFile());
        }
    }

    private boolean isUnder(File file, File folder) {
        try {
            return file.getCanonicalPath().startsWith(folder.getCanonicalPath() + File.separator);
        } catch (IOException ignored) {
            return file.getAbsolutePath().startsWith(folder.getAbsolutePath() + File.separator);
        }
    }

    private String runtimeFileName(String resourceId) {
        String safe = resourceId.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[\\\\/:*?\"<>|]+", "_")
                .replaceAll("\\s+", "_");
        if (safe.isEmpty()) {
            safe = stripExtension(motionFile.getName()).toLowerCase(Locale.ROOT);
        }
        return safe + ThrowMotionDefinition.FILE_EXTENSION;
    }

    private void markDirty() {
        if (updatingUi) {
            return;
        }
        dirty = true;
        if (onDirtyCallback != null) {
            onDirtyCallback.run();
        }
        if (previewRefreshTimer != null) {
            previewRefreshTimer.restart();
        }
    }

    private void bindDirtyTracking(Container root) {
        for (Component c : root.getComponents()) {
            if (c instanceof JTextField) {
                ((JTextField) c).getDocument().addDocumentListener(dirtyListener());
            } else if (c instanceof JTextArea) {
                ((JTextArea) c).getDocument().addDocumentListener(dirtyListener());
            } else if (c instanceof JComboBox) {
                ((JComboBox<?>) c).addActionListener(e -> markDirty());
            } else if (c instanceof JSpinner) {
                ((JSpinner) c).addChangeListener(e -> markDirty());
            } else if (c instanceof JCheckBox) {
                ((JCheckBox) c).addActionListener(e -> markDirty());
            } else if (c instanceof NumberField) {
                ((NumberField) c).addChangeListener(e -> markDirty());
            }
            if (c instanceof Container) {
                bindDirtyTracking((Container) c);
            }
        }
    }

    private DocumentListener dirtyListener() {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                markDirty();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                markDirty();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                markDirty();
            }
        };
    }

    private String selectedMotionType() {
        Object selected = cboMotionType.getSelectedItem();
        return selected instanceof MotionTypeItem
                ? ((MotionTypeItem) selected).type
                : ThrowMotionDefinition.normalizeMotionType(String.valueOf(selected));
    }

    private void selectMotionType(String type) {
        String normalized = ThrowMotionDefinition.normalizeMotionType(type);
        for (int i = 0; i < cboMotionType.getItemCount(); i++) {
            if (normalized.equals(cboMotionType.getItemAt(i).type)) {
                cboMotionType.setSelectedIndex(i);
                return;
            }
        }
        cboMotionType.setSelectedIndex(0);
    }

    private String selectedComboValue(JComboBox<?> combo) {
        Object selected = combo.getSelectedItem();
        return selected == null ? "" : String.valueOf(selected).trim();
    }

    private static JPanel form(Object... rows) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 4, 4, 8);
        for (Object rowObj : rows) {
            Object[] parts = (Object[]) rowObj;
            JLabel label = new JLabel(String.valueOf(parts[0]));
            panel.add(label, gbc);
            gbc.gridx = 1;
            gbc.weightx = 1;
            panel.add((Component) parts[1], gbc);
            gbc.gridx = 0;
            gbc.weightx = 0;
            gbc.gridy++;
        }
        gbc.weighty = 1;
        panel.add(Box.createVerticalGlue(), gbc);
        return panel;
    }

    private static Object[] row(String label, Component component) {
        return new Object[]{label, component};
    }

    private static JScrollPane scroll(Component component) {
        return new JScrollPane(component);
    }

    private static NumberField num(double value, double min, double max, double step) {
        return new NumberField(value, min, max, step);
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static class MotionTypeItem {
        private final String type;

        MotionTypeItem(String type) {
            this.type = ThrowMotionDefinition.normalizeMotionType(type);
        }

        @Override
        public String toString() {
            return ThrowMotionDefinition.displayNameForType(type);
        }
    }

    private static class NumberField extends JPanel {
        private final JSpinner spinner;

        NumberField(double value, double min, double max, double step) {
            super(new BorderLayout());
            spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
            spinner.setEditor(new JSpinner.NumberEditor(spinner, step >= 1 ? "0" : "0.###"));
            add(spinner, BorderLayout.CENTER);
        }

        double getDoubleValue() {
            Object value = spinner.getValue();
            return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
        }

        void setValue(double value) {
            spinner.setValue(value);
        }

        void addChangeListener(javax.swing.event.ChangeListener listener) {
            spinner.addChangeListener(listener);
        }
    }
}
