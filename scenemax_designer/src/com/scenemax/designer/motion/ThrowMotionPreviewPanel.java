package com.scenemax.designer.motion;

import com.jme3.system.AppSettings;
import com.jme3.system.JmeCanvasContext;
import com.scenemaxeng.common.motion.ThrowMotionDefinition;
import org.lwjgl.input.Mouse;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class ThrowMotionPreviewPanel extends JPanel {
    private final ThrowMotionPreviewApp app;
    private final Canvas canvas;
    private final JLabel statusLabel = new JLabel("Preview");
    private final JButton playPauseButton = new JButton("Play");
    private final JSlider targetDistanceSlider = new JSlider(2, 40, 12);
    private final JSlider targetHeightSlider = new JSlider(-5, 12, 0);
    private boolean playing;

    public ThrowMotionPreviewPanel(java.io.File resourcesRoot) {
        super(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(0, 10, 0, 0));
        setMinimumSize(new Dimension(180, 180));
        setPreferredSize(new Dimension(480, 440));

        app = new ThrowMotionPreviewApp(resourcesRoot);
        AppSettings settings = new AppSettings(true);
        settings.setWidth(720);
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
        canvas.setMinimumSize(new Dimension(120, 120));
        canvas.setPreferredSize(new Dimension(420, 360));
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
        canvas.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
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
        app.setStatusChangedCallback(status -> SwingUtilities.invokeLater(() -> statusLabel.setText(status)));

        add(buildToolbar(), BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
        app.startCanvas();
    }

    public void setThrowMotionDefinition(ThrowMotionDefinition definition) {
        app.setThrowMotionDefinition(definition);
        String name = definition == null || definition.getId() == null || definition.getId().isBlank()
                ? "Preview"
                : definition.getId();
        statusLabel.setText(name);
    }

    public void disposePreview() {
        releaseMouseCapture();
        app.stop();
    }

    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel();
        toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.Y_AXIS));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 3));
        playPauseButton.addActionListener(e -> togglePlay());
        JButton step = new JButton("Step");
        step.addActionListener(e -> app.stepFrame());
        JButton reset = new JButton("Reset");
        reset.addActionListener(e -> {
            playing = false;
            playPauseButton.setText("Play");
            app.setPlaying(false);
            app.resetSimulation();
        });
        JButton resetView = new JButton("Reset View");
        resetView.addActionListener(e -> app.resetCamera());
        JCheckBox trajectory = new JCheckBox("Trajectory", true);
        trajectory.addActionListener(e -> app.setShowTrajectory(trajectory.isSelected()));
        JCheckBox markers = new JCheckBox("Samples", true);
        markers.addActionListener(e -> app.setShowSamples(markers.isSelected()));
        controls.add(playPauseButton);
        controls.add(step);
        controls.add(reset);
        controls.add(resetView);
        controls.add(trajectory);
        controls.add(markers);
        toolbar.add(controls);

        JPanel targetRow = new JPanel(new GridLayout(0, 2, 8, 2));
        targetDistanceSlider.setMajorTickSpacing(10);
        targetDistanceSlider.setMinorTickSpacing(2);
        targetDistanceSlider.setPaintTicks(true);
        targetDistanceSlider.addChangeListener(e -> app.setTargetDistance(targetDistanceSlider.getValue()));
        targetHeightSlider.setMajorTickSpacing(5);
        targetHeightSlider.setMinorTickSpacing(1);
        targetHeightSlider.setPaintTicks(true);
        targetHeightSlider.addChangeListener(e -> app.setTargetHeight(targetHeightSlider.getValue()));
        targetRow.add(labelWithControl("Target Distance", targetDistanceSlider));
        targetRow.add(labelWithControl("Target Height", targetHeightSlider));
        toolbar.add(targetRow);

        return toolbar;
    }

    private JPanel labelWithControl(String label, JComponent control) {
        JPanel panel = new JPanel(new BorderLayout(6, 0));
        JLabel l = new JLabel(label);
        l.setPreferredSize(new Dimension(112, l.getPreferredSize().height));
        panel.add(l, BorderLayout.WEST);
        panel.add(control, BorderLayout.CENTER);
        return panel;
    }

    private void togglePlay() {
        playing = !playing;
        playPauseButton.setText(playing ? "Pause" : "Play");
        app.setPlaying(playing);
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
