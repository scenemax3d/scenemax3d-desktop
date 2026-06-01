package com.scenemax.designer.video;

import com.jme3.system.AppSettings;
import com.jme3.system.JmeCanvasContext;
import com.scenemax.designer.gizmo.GizmoMode;
import org.lwjgl.input.Mouse;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.function.Consumer;

public class VideoPreviewPanel extends JPanel {
    private final VideoPreviewApp app;
    private final Canvas canvas;
    private final JLabel statusLabel = new JLabel("Choose a video file to preview.");
    private final JComboBox<VideoPreviewShape> shapeCombo = new JComboBox<>(VideoPreviewShape.values());
    private Consumer<VideoPreviewShape> shapeChangedCallback;

    public VideoPreviewPanel() {
        super(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(0, 10, 0, 0));
        setMinimumSize(new Dimension(240, 220));
        setPreferredSize(new Dimension(560, 460));

        app = new VideoPreviewApp();
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
        canvas.setPreferredSize(new Dimension(560, 400));
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

        app.setStatusCallback(status -> SwingUtilities.invokeLater(() -> statusLabel.setText(status)));

        add(buildToolbar(), BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
        app.startCanvas();
    }

    public void setShapeChangedCallback(Consumer<VideoPreviewShape> callback) {
        this.shapeChangedCallback = callback;
    }

    public VideoPreviewShape getSelectedShape() {
        Object selected = shapeCombo.getSelectedItem();
        return selected instanceof VideoPreviewShape ? (VideoPreviewShape) selected : VideoPreviewShape.PANE;
    }

    public void setVideoFile(File file, VideoMetadata metadata) {
        app.setVideoFile(file, metadata);
    }

    public void disposePreview() {
        releaseMouseCapture();
        app.stop();
        SwingUtilities.invokeLater(this::releaseMouseCapture);
    }

    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 3));
        toolbar.add(new JLabel("Shape"));
        shapeCombo.addActionListener(e -> {
            VideoPreviewShape shape = getSelectedShape();
            app.setShape(shape);
            if (shapeChangedCallback != null) {
                shapeChangedCallback.accept(shape);
            }
        });
        toolbar.add(shapeCombo);
        toolbar.add(modeButton("Move", GizmoMode.TRANSLATE));
        toolbar.add(modeButton("Rotate", GizmoMode.ROTATE));
        toolbar.add(modeButton("Scale", GizmoMode.SCALE));
        JButton reset = new JButton("Reset View");
        reset.addActionListener(e -> app.resetView());
        toolbar.add(reset);
        return toolbar;
    }

    private JButton modeButton(String label, GizmoMode mode) {
        JButton button = new JButton(label);
        button.addActionListener(e -> app.setGizmoMode(mode));
        return button;
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
