package com.scenemax.designer.video;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.util.function.Consumer;

public class VideoImportPanel extends JPanel implements AutoCloseable {
    private final File resourcesFolder;
    private final VideoPreviewPanel previewPanel = new VideoPreviewPanel();
    private final JTextField nameField = new JTextField();
    private final JTextField fileField = readOnlyField();
    private final JTextField dimensionsField = readOnlyField();
    private final JTextField durationField = readOnlyField();
    private final JTextField frameRateField = readOnlyField();
    private final JTextField formatField = readOnlyField();
    private final JLabel statusLabel = new JLabel("Choose a video file to import. Runtime handles playback.");
    private final JButton importButton = new JButton("Import Video");
    private final JButton chooseButton = new JButton("Choose Video...");
    private final JButton closeButton = new JButton("Close");

    private File sourceFile;
    private VideoMetadata metadata;
    private Runnable onCloseCallback;
    private Consumer<VideoImportResult> onImportedCallback;
    private boolean closed;

    public VideoImportPanel(File resourcesFolder) {
        super(new BorderLayout(12, 12));
        this.resourcesFolder = resourcesFolder;
        buildUi();
        bindActions();
        setImportEnabled(false);
    }

    public void setOnCloseCallback(Runnable callback) {
        this.onCloseCallback = callback;
    }

    public void setOnImportedCallback(Consumer<VideoImportResult> callback) {
        this.onImportedCallback = callback;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        previewPanel.disposePreview();
    }

    private void buildUi() {
        setBackground(new Color(18, 21, 28));
        setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel header = new JPanel(new BorderLayout(8, 8));
        header.setOpaque(false);
        JLabel title = new JLabel("Import Video Asset");
        title.setFont(title.getFont().deriveFont(18f));
        title.setForeground(new Color(235, 238, 245));
        header.add(title, BorderLayout.WEST);
        statusLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        header.add(statusLabel, BorderLayout.CENTER);
        add(header, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(12, 12));
        body.setOpaque(false);
        body.add(buildInspector(), BorderLayout.WEST);
        body.add(previewPanel, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
    }

    private JPanel buildInspector() {
        JPanel inspector = new JPanel();
        inspector.setLayout(new BoxLayout(inspector, BoxLayout.Y_AXIS));
        inspector.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        inspector.setPreferredSize(new Dimension(320, 460));

        chooseButton.setAlignmentX(LEFT_ALIGNMENT);
        inspector.add(chooseButton);
        inspector.add(Box.createVerticalStrut(10));
        inspector.add(labeled("Source", fileField));
        inspector.add(labeled("Asset Name", nameField));
        inspector.add(Box.createVerticalStrut(8));
        inspector.add(labeled("Dimensions", dimensionsField));
        inspector.add(labeled("Duration", durationField));
        inspector.add(labeled("Frame Rate", frameRateField));
        inspector.add(labeled("Format", formatField));
        inspector.add(Box.createVerticalGlue());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.add(importButton);
        actions.add(closeButton);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        inspector.add(actions);
        return inspector;
    }

    private JPanel labeled(String label, JTextField field) {
        JPanel panel = new JPanel(new BorderLayout(4, 3));
        panel.setAlignmentX(LEFT_ALIGNMENT);
        JLabel jLabel = new JLabel(label);
        panel.add(jLabel, BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        return panel;
    }

    private void bindActions() {
        chooseButton.addActionListener(e -> chooseVideoFile());
        importButton.addActionListener(e -> importVideo());
        closeButton.addActionListener(e -> requestClose());
    }

    private void chooseVideoFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import Video");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Common video files (*.mp4, *.mov, *.mkv, *.avi, *.webm, *.mpeg)",
                "mp4", "mov", "mkv", "avi", "webm", "mpeg", "mpg", "m4v"));
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        loadSourceFile(chooser.getSelectedFile());
    }

    private void loadSourceFile(File file) {
        sourceFile = file;
        metadata = null;
        fileField.setText(file.getAbsolutePath());
        nameField.setText(VideoImporter.sanitizeAssetId(file.getName()));
        dimensionsField.setText("");
        durationField.setText("");
        frameRateField.setText("");
        formatField.setText("");
        setImportEnabled(false);
        setBusy(true, "Reading video file info...");

        SwingWorker<VideoMetadata, Void> worker = new SwingWorker<>() {
            @Override
            protected VideoMetadata doInBackground() throws Exception {
                return VideoMetadata.probe(file);
            }

            @Override
            protected void done() {
                if (closed) {
                    return;
                }
                try {
                    metadata = get();
                    dimensionsField.setText(metadata.dimensionsText());
                    durationField.setText(metadata.durationText());
                    frameRateField.setText(metadata.frameRateText());
                    formatField.setText(metadata.format == null || metadata.format.isBlank() ? "Unknown" : metadata.format);
                    previewPanel.setVideoFile(file, metadata);
                    setImportEnabled(true);
                    setBusy(false, "Ready to import. Playback preview runs in the runtime projector.");
                } catch (Exception ex) {
                    setBusy(false, "Video info failed.");
                    JOptionPane.showMessageDialog(VideoImportPanel.this,
                            "Could not read this video file:\n" + ex.getMessage(),
                            "Video Import",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void importVideo() {
        if (sourceFile == null) {
            return;
        }
        setBusy(true, "Importing video...");
        SwingWorker<VideoImportResult, Void> worker = new SwingWorker<>() {
            @Override
            protected VideoImportResult doInBackground() throws Exception {
                return VideoImporter.importVideo(
                        sourceFile,
                        resourcesFolder,
                        nameField.getText(),
                        metadata,
                        previewPanel.getSelectedShape().name()
                );
            }

            @Override
            protected void done() {
                if (closed) {
                    return;
                }
                try {
                    VideoImportResult result = get();
                    setBusy(false, "Imported " + result.getAssetId());
                    if (onImportedCallback != null) {
                        onImportedCallback.accept(result);
                    }
                    JOptionPane.showMessageDialog(VideoImportPanel.this,
                            "Imported video asset: " + result.getAssetId() + "\n"
                                    + "Assets: " + result.getAssetFolder().getAbsolutePath(),
                            "Video Import",
                            JOptionPane.INFORMATION_MESSAGE);
                    requestClose();
                } catch (Exception ex) {
                    setBusy(false, "Import failed.");
                    JOptionPane.showMessageDialog(VideoImportPanel.this,
                            ex.getMessage(),
                            "Video Import",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void requestClose() {
        close();
        if (onCloseCallback != null) {
            SwingUtilities.invokeLater(onCloseCallback);
        }
    }

    private void setBusy(boolean busy, String status) {
        chooseButton.setEnabled(!busy);
        importButton.setEnabled(!busy && sourceFile != null && metadata != null);
        nameField.setEnabled(!busy);
        statusLabel.setText(status);
    }

    private void setImportEnabled(boolean enabled) {
        importButton.setEnabled(enabled);
    }

    private static JTextField readOnlyField() {
        JTextField field = new JTextField();
        field.setEditable(false);
        return field;
    }
}
