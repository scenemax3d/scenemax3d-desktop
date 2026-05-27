package com.scenemax.designer.physics;

import com.jme3.math.Vector3f;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeCanvasContext;
import org.lwjgl.input.Mouse;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.Locale;

public class PhysicsSimulationPanel extends JPanel implements AutoCloseable {
    private final PhysicsSimulationPreviewApp app;
    private final Canvas canvas;
    private final PhysicsSimulationSettings settings = new PhysicsSimulationSettings();
    private final JLabel statusLabel = new JLabel("Physics Simulation");
    private final JTextArea commandPreview = new JTextArea(5, 32);
    private final JButton playStopButton = new JButton("Play");
    private boolean updatingUi;
    private boolean playing;

    private final JTextField objectName = new JTextField(settings.objectName);
    private final JTextField targetName = new JTextField(settings.targetName);
    private final JComboBox<ComboItem<PhysicsSimulationSettings.CommandType>> commandType =
            combo(
                    item("Throw", PhysicsSimulationSettings.CommandType.THROW),
                    item("Impulse", PhysicsSimulationSettings.CommandType.IMPULSE),
                    item("Force", PhysicsSimulationSettings.CommandType.FORCE),
                    item("Velocity", PhysicsSimulationSettings.CommandType.VELOCITY),
                    item("Angular Velocity", PhysicsSimulationSettings.CommandType.ANGULAR_VELOCITY),
                    item("Torque", PhysicsSimulationSettings.CommandType.TORQUE),
                    item("Stop", PhysicsSimulationSettings.CommandType.STOP)
            );
    private final JComboBox<ComboItem<PhysicsSimulationSettings.TargetMode>> targetMode =
            combo(
                    item("At Target", PhysicsSimulationSettings.TargetMode.AT),
                    item("Toward Target", PhysicsSimulationSettings.TargetMode.TOWARD),
                    item("Vector", PhysicsSimulationSettings.TargetMode.VECTOR),
                    item("Forward", PhysicsSimulationSettings.TargetMode.FORWARD),
                    item("Backward", PhysicsSimulationSettings.TargetMode.BACKWARD),
                    item("Left", PhysicsSimulationSettings.TargetMode.LEFT),
                    item("Right", PhysicsSimulationSettings.TargetMode.RIGHT),
                    item("Up", PhysicsSimulationSettings.TargetMode.UP),
                    item("Down", PhysicsSimulationSettings.TargetMode.DOWN)
            );
    private final JComboBox<ComboItem<PhysicsSimulationSettings.ArcMode>> arcMode =
            combo(
                    item("None", PhysicsSimulationSettings.ArcMode.NONE),
                    item("Low", PhysicsSimulationSettings.ArcMode.LOW),
                    item("Medium", PhysicsSimulationSettings.ArcMode.MEDIUM),
                    item("High", PhysicsSimulationSettings.ArcMode.HIGH),
                    item("Custom", PhysicsSimulationSettings.ArcMode.CUSTOM)
            );

    private final NumberField power = number(settings.power, 0, 999, 0.5);
    private final JCheckBox useAngle = new JCheckBox();
    private final NumberField angle = number(settings.angleDegrees, -89, 89, 1);
    private final NumberField customArc = number(settings.customArc, 0, 1, 0.05);
    private final JCheckBox useSpin = new JCheckBox();
    private final NumberField spinX = number(settings.spin.x, -999, 999, 0.5);
    private final NumberField spinY = number(settings.spin.y, -999, 999, 0.5);
    private final NumberField spinZ = number(settings.spin.z, -999, 999, 0.5);
    private final NumberField vectorX = number(settings.vector.x, -999, 999, 0.5);
    private final NumberField vectorY = number(settings.vector.y, -999, 999, 0.5);
    private final NumberField vectorZ = number(settings.vector.z, -999, 999, 0.5);
    private final JCheckBox torqueImpulse = new JCheckBox();
    private final JCheckBox useForceDuration = new JCheckBox();
    private final NumberField forceDuration = number(settings.forceDuration, 0, 60, 0.05);
    private final NumberField mass = number(settings.mass, 0.01, 999, 0.1);
    private final NumberField drag = number(settings.drag, 0, 10, 0.01);
    private final NumberField gravity = number(settings.gravity, 0, 50, 0.1);
    private final NumberField restitution = number(settings.restitution, 0, 1, 0.05);
    private final NumberField floorFriction = number(settings.floorFriction, 0, 1, 0.05);
    private final NumberField duration = number(settings.simulationDuration, 0.1, 60, 0.1);
    private final NumberField objectX = number(settings.objectPosition.x, -100, 100, 0.25);
    private final NumberField objectY = number(settings.objectPosition.y, 0.45, 100, 0.25);
    private final NumberField objectZ = number(settings.objectPosition.z, -100, 100, 0.25);
    private final NumberField targetX = number(settings.targetPosition.x, -100, 100, 0.25);
    private final NumberField targetY = number(settings.targetPosition.y, 0.45, 100, 0.25);
    private final NumberField targetZ = number(settings.targetPosition.z, -100, 100, 0.25);

    public PhysicsSimulationPanel() {
        super(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(12, 12, 12, 12));

        app = new PhysicsSimulationPreviewApp();
        AppSettings appSettings = new AppSettings(true);
        appSettings.setWidth(860);
        appSettings.setHeight(580);
        appSettings.setSamples(4);
        appSettings.setVSync(true);
        appSettings.setFrameRate(60);
        appSettings.setGammaCorrection(false);
        app.setSettings(appSettings);
        app.setPauseOnLostFocus(false);
        app.setShowSettings(false);
        app.createCanvas();

        JmeCanvasContext ctx = (JmeCanvasContext) app.getContext();
        ctx.setSystemListener(app);
        canvas = ctx.getCanvas();
        canvas.setMinimumSize(new Dimension(260, 220));
        canvas.setPreferredSize(new Dimension(760, 520));
        canvas.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                releaseMouseCapture();
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                releaseMouseCapture();
            }
        });
        canvas.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (canvas.getWidth() > 0 && canvas.getHeight() > 0) {
                    app.enqueue(() -> {
                        app.reshape(canvas.getWidth(), canvas.getHeight());
                        return null;
                    });
                }
            }
        });
        app.setPositionsChangedCallback(next -> SwingUtilities.invokeLater(() -> {
            settings.objectPosition = next.objectPosition.clone();
            settings.targetPosition = next.targetPosition.clone();
            refreshPositionFields();
            refreshCommand();
        }));
        app.setStatusChangedCallback(status -> SwingUtilities.invokeLater(() -> statusLabel.setText(status)));
        app.setPlaybackChangedCallback(active -> SwingUtilities.invokeLater(() -> {
            playing = active;
            playStopButton.setText(active ? "Stop" : "Play");
        }));

        buildUi();
        bindUi();
        refreshUiState();
        refreshCommand();
        app.startCanvas();
        pushSettingsToPreview();
    }

    @Override
    public void close() {
        releaseMouseCapture();
        app.stop();
    }

    private void buildUi() {
        JLabel title = new JLabel("Physics Simulation");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        add(title, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildPreviewPanel(), buildPropertiesPanel());
        split.setResizeWeight(0.68);
        split.setContinuousLayout(true);
        split.setDividerLocation(760);
        add(split, BorderLayout.CENTER);
    }

    private Component buildPreviewPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 3));
        playStopButton.addActionListener(e -> togglePlayStop());
        JButton resetView = new JButton("Reset View");
        resetView.addActionListener(e -> app.resetCamera());
        toolbar.add(playStopButton);
        toolbar.add(resetView);
        toolbar.add(new JLabel("Minie/Bullet preview. Drag a sphere or axis gizmo to tune positions."));
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(canvas, BorderLayout.CENTER);
        panel.add(statusLabel, BorderLayout.SOUTH);
        return panel;
    }

    private Component buildPropertiesPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setMinimumSize(new Dimension(340, 200));
        panel.setPreferredSize(new Dimension(390, 520));

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(section("Scene",
                row("Object Name", objectName),
                row("Target Name", targetName),
                row("Object Position", vectorFields(objectX, objectY, objectZ)),
                row("Target Position", vectorFields(targetX, targetY, targetZ))));
        form.add(section("Physics Command",
                row("Command", commandType),
                row("Target", targetMode),
                row("Power", power),
                row("Use Angle", useAngle),
                row("Angle", angle),
                row("Arc", arcMode),
                row("Custom Arc", customArc),
                row("Use Spin", useSpin),
                row("Spin", vectorFields(spinX, spinY, spinZ)),
                row("Vector", vectorFields(vectorX, vectorY, vectorZ)),
                row("Torque Impulse", torqueImpulse),
                row("Force Duration", useForceDuration),
                row("Seconds", forceDuration)));
        form.add(section("Rigid Body / World",
                row("Mass", mass),
                row("Drag", drag),
                row("Gravity", gravity),
                row("Floor Bounce", restitution),
                row("Floor Friction", floorFriction),
                row("Duration", duration)));

        panel.add(new JScrollPane(form), BorderLayout.CENTER);

        commandPreview.setEditable(false);
        commandPreview.setLineWrap(true);
        commandPreview.setWrapStyleWord(true);
        commandPreview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JPanel commandPanel = new JPanel(new BorderLayout(4, 4));
        commandPanel.setBorder(BorderFactory.createTitledBorder("Command Preview"));
        commandPanel.add(new JScrollPane(commandPreview), BorderLayout.CENTER);
        JButton copy = new JButton("Copy");
        copy.addActionListener(e -> copyCommand());
        commandPanel.add(copy, BorderLayout.SOUTH);
        panel.add(commandPanel, BorderLayout.SOUTH);
        return panel;
    }

    private void bindUi() {
        DocumentListener textListener = new SimpleDocumentListener() {
            @Override
            protected void changed() {
                applyUiAndRefresh();
            }
        };
        objectName.getDocument().addDocumentListener(textListener);
        targetName.getDocument().addDocumentListener(textListener);

        java.awt.event.ActionListener action = e -> applyUiAndRefresh();
        commandType.addActionListener(action);
        targetMode.addActionListener(action);
        arcMode.addActionListener(action);
        useAngle.addActionListener(action);
        useSpin.addActionListener(action);
        torqueImpulse.addActionListener(action);
        useForceDuration.addActionListener(action);

        for (NumberField field : allNumberFields()) {
            field.addChangeListener(e -> applyUiAndRefresh());
        }
    }

    private void applyUiAndRefresh() {
        if (updatingUi) {
            return;
        }
        applyUiToSettings();
        refreshUiState();
        refreshCommand();
        playing = false;
        playStopButton.setText("Play");
        pushSettingsToPreview();
    }

    private void applyUiToSettings() {
        settings.objectName = sanitizedName(objectName.getText(), "throwable");
        settings.targetName = sanitizedName(targetName.getText(), "target");
        settings.commandType = selected(commandType);
        settings.targetMode = selected(targetMode);
        settings.arcMode = selected(arcMode);
        settings.power = power.floatValue();
        settings.useAngle = useAngle.isSelected();
        settings.angleDegrees = angle.floatValue();
        settings.customArc = customArc.floatValue();
        settings.useSpin = useSpin.isSelected();
        settings.spin = new Vector3f(spinX.floatValue(), spinY.floatValue(), spinZ.floatValue());
        settings.vector = new Vector3f(vectorX.floatValue(), vectorY.floatValue(), vectorZ.floatValue());
        settings.torqueImpulse = torqueImpulse.isSelected();
        settings.useForceDuration = useForceDuration.isSelected();
        settings.forceDuration = forceDuration.floatValue();
        settings.mass = mass.floatValue();
        settings.drag = drag.floatValue();
        settings.gravity = gravity.floatValue();
        settings.restitution = restitution.floatValue();
        settings.floorFriction = floorFriction.floatValue();
        settings.simulationDuration = duration.floatValue();
        settings.objectPosition = new Vector3f(objectX.floatValue(), objectY.floatValue(), objectZ.floatValue());
        settings.targetPosition = new Vector3f(targetX.floatValue(), targetY.floatValue(), targetZ.floatValue());
    }

    private void refreshUiState() {
        PhysicsSimulationSettings.CommandType type = selected(commandType);
        PhysicsSimulationSettings.TargetMode mode = selected(targetMode);
        boolean stop = type == PhysicsSimulationSettings.CommandType.STOP;
        boolean throwCommand = type == PhysicsSimulationSettings.CommandType.THROW;
        boolean rawVector = type == PhysicsSimulationSettings.CommandType.VELOCITY
                || type == PhysicsSimulationSettings.CommandType.ANGULAR_VELOCITY
                || type == PhysicsSimulationSettings.CommandType.TORQUE;
        boolean force = type == PhysicsSimulationSettings.CommandType.FORCE;
        boolean torque = type == PhysicsSimulationSettings.CommandType.TORQUE;

        if (throwCommand && mode != PhysicsSimulationSettings.TargetMode.AT
                && mode != PhysicsSimulationSettings.TargetMode.TOWARD) {
            setSelected(targetMode, PhysicsSimulationSettings.TargetMode.AT);
            settings.targetMode = PhysicsSimulationSettings.TargetMode.AT;
            mode = PhysicsSimulationSettings.TargetMode.AT;
        } else if (!throwCommand && mode == PhysicsSimulationSettings.TargetMode.AT) {
            setSelected(targetMode, PhysicsSimulationSettings.TargetMode.TOWARD);
            settings.targetMode = PhysicsSimulationSettings.TargetMode.TOWARD;
            mode = PhysicsSimulationSettings.TargetMode.TOWARD;
        }

        targetMode.setEnabled(!stop && !rawVector);
        power.setEnabled(!stop && !rawVector);
        useAngle.setEnabled(throwCommand);
        angle.setEnabled(throwCommand && useAngle.isSelected());
        arcMode.setEnabled(throwCommand);
        customArc.setEnabled(throwCommand && selected(arcMode) == PhysicsSimulationSettings.ArcMode.CUSTOM);
        useSpin.setEnabled(throwCommand);
        spinX.setEnabled(throwCommand && useSpin.isSelected());
        spinY.setEnabled(throwCommand && useSpin.isSelected());
        spinZ.setEnabled(throwCommand && useSpin.isSelected());
        boolean vectorEnabled = !stop && (rawVector || mode == PhysicsSimulationSettings.TargetMode.VECTOR);
        vectorX.setEnabled(vectorEnabled);
        vectorY.setEnabled(vectorEnabled);
        vectorZ.setEnabled(vectorEnabled);
        torqueImpulse.setEnabled(torque);
        useForceDuration.setEnabled(force);
        forceDuration.setEnabled(force && useForceDuration.isSelected());
    }

    private void refreshPositionFields() {
        updatingUi = true;
        objectX.setValue((double) settings.objectPosition.x);
        objectY.setValue((double) settings.objectPosition.y);
        objectZ.setValue((double) settings.objectPosition.z);
        targetX.setValue((double) settings.targetPosition.x);
        targetY.setValue((double) settings.targetPosition.y);
        targetZ.setValue((double) settings.targetPosition.z);
        updatingUi = false;
    }

    private void refreshCommand() {
        commandPreview.setText(generateCommand());
        commandPreview.setCaretPosition(0);
    }

    private String generateCommand() {
        String object = settings.objectName;
        String target = settings.targetName;
        switch (settings.commandType) {
            case THROW:
                StringBuilder throwCommand = new StringBuilder(object)
                        .append(".throw ")
                        .append(settings.targetMode == PhysicsSimulationSettings.TargetMode.TOWARD ? "toward " : "at ")
                        .append(target)
                        .append(" power ")
                        .append(fmt(settings.power));
                if (settings.useAngle) {
                    throwCommand.append(" angle ").append(fmt(settings.angleDegrees));
                } else if (settings.arcMode != PhysicsSimulationSettings.ArcMode.NONE) {
                    throwCommand.append(" arc ").append(arcText());
                }
                if (settings.useSpin) {
                    throwCommand.append(" spin ").append(vectorText(settings.spin));
                }
                return throwCommand.toString();
            case IMPULSE:
                return object + ".physics impulse " + linearTargetText();
            case FORCE:
                String force = object + ".physics force " + linearTargetText();
                if (settings.useForceDuration) {
                    force += " for " + fmt(settings.forceDuration) + " seconds";
                }
                return force;
            case VELOCITY:
                return object + ".physics velocity " + vectorText(settings.vector);
            case ANGULAR_VELOCITY:
                return object + ".physics angular velocity " + vectorText(settings.vector);
            case TORQUE:
                return object + ".physics torque " + vectorText(settings.vector)
                        + (settings.torqueImpulse ? " impulse" : "");
            case STOP:
                return object + ".physics stop";
            default:
                return "";
        }
    }

    private String linearTargetText() {
        if (settings.targetMode == PhysicsSimulationSettings.TargetMode.VECTOR) {
            return vectorText(settings.vector);
        }
        if (settings.targetMode == PhysicsSimulationSettings.TargetMode.TOWARD
                || settings.targetMode == PhysicsSimulationSettings.TargetMode.AT) {
            return "toward " + settings.targetName + " power " + fmt(settings.power);
        }
        return directionName(settings.targetMode) + " " + fmt(settings.power);
    }

    private String arcText() {
        switch (settings.arcMode) {
            case LOW:
                return "low";
            case HIGH:
                return "high";
            case CUSTOM:
                return fmt(settings.customArc);
            case MEDIUM:
            default:
                return "medium";
        }
    }

    private String directionName(PhysicsSimulationSettings.TargetMode mode) {
        return mode.name().toLowerCase(Locale.US);
    }

    private String vectorText(Vector3f vector) {
        return "(" + fmt(vector.x) + ", " + fmt(vector.y) + ", " + fmt(vector.z) + ")";
    }

    private String fmt(float value) {
        if (Math.abs(value - Math.round(value)) < 0.0001f) {
            return Integer.toString(Math.round(value));
        }
        return String.format(Locale.US, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private void pushSettingsToPreview() {
        app.setSettings(settings.copy());
        app.setPlaying(false);
    }

    private void togglePlayStop() {
        applyUiToSettings();
        refreshCommand();
        playing = !playing;
        if (playing) {
            playStopButton.setText("Stop");
            app.setSettings(settings.copy());
            app.setPlaying(true);
        } else {
            playStopButton.setText("Play");
            app.stopAndReset();
        }
    }

    private void copyCommand() {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                new StringSelection(commandPreview.getText()), null);
    }

    private String sanitizedName(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return fallback;
        }
        return trimmed.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private JPanel section(String title, Object[]... rows) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        for (Object[] row : rows) {
            gbc.gridx = 0;
            gbc.weightx = 0;
            JLabel label = new JLabel(String.valueOf(row[0]));
            label.setPreferredSize(new Dimension(112, label.getPreferredSize().height));
            panel.add(label, gbc);
            gbc.gridx = 1;
            gbc.weightx = 1;
            panel.add((Component) row[1], gbc);
            gbc.gridy++;
        }
        return panel;
    }

    private Object[] row(String label, Component component) {
        return new Object[]{label, component};
    }

    private JPanel vectorFields(NumberField x, NumberField y, NumberField z) {
        JPanel panel = new JPanel(new GridLayout(1, 3, 4, 0));
        panel.add(x);
        panel.add(y);
        panel.add(z);
        return panel;
    }

    private NumberField[] allNumberFields() {
        return new NumberField[]{power, angle, customArc, spinX, spinY, spinZ, vectorX, vectorY, vectorZ,
                forceDuration, mass, drag, gravity, restitution, floorFriction, duration,
                objectX, objectY, objectZ, targetX, targetY, targetZ};
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

    private static NumberField number(double value, double min, double max, double step) {
        return new NumberField(value, min, max, step);
    }

    private static <T> ComboItem<T> item(String label, T value) {
        return new ComboItem<>(label, value);
    }

    @SafeVarargs
    private static <T> JComboBox<ComboItem<T>> combo(ComboItem<T>... items) {
        return new JComboBox<>(items);
    }

    private static <T> T selected(JComboBox<ComboItem<T>> combo) {
        int index = combo.getSelectedIndex();
        ComboItem<T> item = index < 0 ? null : combo.getItemAt(index);
        return item == null ? null : item.value;
    }

    private static <T> void setSelected(JComboBox<ComboItem<T>> combo, T value) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            ComboItem<T> item = combo.getItemAt(i);
            if (item.value == value || (item.value != null && item.value.equals(value))) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }

    private static class ComboItem<T> {
        final String label;
        final T value;

        ComboItem(String label, T value) {
            this.label = label;
            this.value = value;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static class NumberField extends JSpinner {
        NumberField(double value, double min, double max, double step) {
            super(new SpinnerNumberModel(value, min, max, step));
            setPreferredSize(new Dimension(84, getPreferredSize().height));
            JSpinner.NumberEditor editor = new JSpinner.NumberEditor(this, "0.###");
            setEditor(editor);
        }

        float floatValue() {
            return ((Number) getValue()).floatValue();
        }
    }

    private static abstract class SimpleDocumentListener implements DocumentListener {
        protected abstract void changed();

        @Override
        public void insertUpdate(DocumentEvent e) {
            changed();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            changed();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            changed();
        }
    }
}
