package com.scenemax.designer.effekseer;

import com.jme3.system.AppSettings;
import com.jme3.system.JmeCanvasContext;
import com.scenemax.effekseer.runtime.EffekseerNativeBridge;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class EffekseerEffectDesignerPanel extends JPanel {

    private final String projectPath;
    private final File documentFile;
    private final File resourcesFolder;

    private EffekseerEffectDocument document;
    private boolean dirty = false;
    private boolean discardEditorStateOnDeactivate = false;
    private Runnable onDirtyCallback;
    private Runnable onSavedCallback;
    private EffekseerPreviewApp previewApp;
    private Canvas previewCanvas;
    private JPanel previewContainer;

    private JTextField txtName;
    private JTextField txtAssetId;
    private JTextField txtImportedEffect;
    private JTextField txtOriginalPath;
    private JTextField txtImportedAt;
    private JTextField txtToolPath;
    private JCheckBox chkLoop;
    private JCheckBox chkShowGround;
    private JComboBox<String> cboBackground;
    private JSpinner spnPlaybackSpeed;
    private JSpinner spnCameraDistance;
    private JSpinner spnCameraYaw;
    private JSpinner spnCameraPitch;
    private JSpinner spnMotionForceScale;
    private JSpinner spnMotionOrbitStrength;
    private JSpinner spnMotionDamping;
    private JTextArea txtDiagnostics;
    private JLabel lblPreviewStatus;
    private javax.swing.Timer diagnosticsRefreshTimer;

    public EffekseerEffectDesignerPanel(String projectPath, File documentFile) {
        super(new BorderLayout());
        this.projectPath = projectPath;
        this.documentFile = documentFile;
        this.resourcesFolder = resolveResourcesFolder(projectPath, documentFile);
        loadDocument();
        buildUi();
        loadDocumentIntoUi();
        refreshDiagnostics();
        updateModifierAvailability();
    }

    public void setOnDirtyCallback(Runnable onDirtyCallback) {
        this.onDirtyCallback = onDirtyCallback;
    }

    public void setOnSavedCallback(Runnable onSavedCallback) {
        this.onSavedCallback = onSavedCallback;
    }

    public void activatePanel() {
        ensurePreviewInitialized();
        refreshToolPath();
        refreshDiagnostics();
        updateModifierAvailability();
        if (previewCanvas != null && previewCanvas.getParent() != previewContainer) {
            previewContainer.add(previewCanvas, BorderLayout.CENTER);
            previewContainer.revalidate();
            previewContainer.repaint();
        }
        if (previewApp != null && previewCanvas != null && previewCanvas.getWidth() > 0 && previewCanvas.getHeight() > 0) {
            previewApp.enqueue(() -> {
                previewApp.reshape(previewCanvas.getWidth(), previewCanvas.getHeight());
                return null;
            });
        }
        replayEmbeddedPreview();
    }

    public void deactivatePanel() {
        if (discardEditorStateOnDeactivate) {
            discardEditorStateOnDeactivate = false;
        } else if (dirty) {
            saveDocument();
        }
        disposePreview();
    }

    public void clearAndDeactivatePanel() {
        deactivatePanel();
    }

    public void reloadFromDisk() {
        loadDocument();
        dirty = false;
        discardEditorStateOnDeactivate = false;
        loadDocumentIntoUi();
        refreshToolPath();
        refreshDiagnostics();
        refreshEmbeddedPreview();
        updateModifierAvailability();
        revalidate();
        repaint();
        if (onSavedCallback != null) {
            onSavedCallback.run();
        }
    }

    public void saveDocument() {
        try {
            applyUiToDocument();
            EffekseerImporter.ensureAssetForDocument(document, documentFile, resourcesFolder);
            document.save(documentFile);
            dirty = false;
            discardEditorStateOnDeactivate = false;
            txtAssetId.setText(document.getAssetId());
            txtImportedEffect.setText(document.getImportedEffectFile());
            refreshDiagnostics();
            refreshEmbeddedPreview();
            if (onSavedCallback != null) {
                onSavedCallback.run();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Error saving Effekseer document: " + ex.getMessage(),
                    "Save Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public void discardEditorState() {
        dirty = false;
        discardEditorStateOnDeactivate = true;
    }

    private void loadDocument() {
        try {
            if (documentFile.exists() && documentFile.length() > 0) {
                document = EffekseerEffectDocument.load(documentFile);
            } else {
                document = new EffekseerEffectDocument(documentFile.getAbsolutePath());
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            document = new EffekseerEffectDocument(documentFile.getAbsolutePath());
        }
    }

    private void buildUi() {
        setBackground(new Color(18, 21, 28));

        JPanel shell = new JPanel(new BorderLayout(12, 12));
        shell.setBackground(new Color(18, 21, 28));
        shell.setBorder(new EmptyBorder(12, 12, 12, 12));
        add(shell, BorderLayout.CENTER);

        shell.add(buildHeader(), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(12, 12));
        body.setOpaque(false);
        body.add(buildPreviewPanel(), BorderLayout.CENTER);
        body.add(buildInspector(), BorderLayout.WEST);
        body.add(buildDiagnosticsPanel(), BorderLayout.EAST);
        shell.add(body, BorderLayout.CENTER);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Effekseer Effect Designer");
        title.setForeground(new Color(245, 247, 250));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        titleBox.add(title);

        lblPreviewStatus = new JLabel();
        lblPreviewStatus.setForeground(new Color(155, 168, 186));
        titleBox.add(lblPreviewStatus);
        header.add(titleBox, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton btnChooseTool = new JButton("Choose Effekseer Tool");
        btnChooseTool.addActionListener(e -> chooseToolPath());
        JButton btnReplay = new JButton("Replay Embedded Preview");
        btnReplay.addActionListener(e -> replayEmbeddedPreview());
        JButton btnPreview = new JButton("Open In Effekseer");
        btnPreview.addActionListener(e -> launchPreview());
        JButton btnReveal = new JButton("Reveal Assets");
        btnReveal.addActionListener(e -> revealAssetFolder());
        JButton btnReimport = new JButton("Reimport");
        btnReimport.addActionListener(e -> reimportEffect());
        JButton btnSave = new JButton("Save");
        btnSave.addActionListener(e -> saveDocument());
        actions.add(btnChooseTool);
        actions.add(btnReplay);
        actions.add(btnPreview);
        actions.add(btnReveal);
        actions.add(btnReimport);
        actions.add(btnSave);
        header.add(actions, BorderLayout.EAST);

        return header;
    }

    private JPanel buildPreviewPanel() {
        JPanel panel = createCard();
        panel.setLayout(new BorderLayout(0, 10));
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        header.add(sectionLabel("Preview"), BorderLayout.WEST);
        header.add(buildPreviewControls(), BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);
        panel.setPreferredSize(new Dimension(720, 0));

        previewContainer = new JPanel(new BorderLayout());
        previewContainer.setOpaque(false);
        previewContainer.setPreferredSize(new Dimension(640, 480));
        panel.add(previewContainer, BorderLayout.CENTER);

        JTextArea note = new JTextArea(
                "This preview runs inside the SceneMax JME canvas with a custom Effekseer-compatible sprite runtime.\n" +
                "It does not use the JME particle system, and it currently focuses on imported .efkproj sprite effects.");
        note.setEditable(false);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setOpaque(false);
        note.setForeground(new Color(170, 181, 196));
        panel.add(note, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent buildPreviewControls() {
        JPanel controls = new JPanel();
        controls.setOpaque(false);
        controls.setLayout(new BoxLayout(controls, BoxLayout.X_AXIS));
        controls.add(cameraButton("Reset", () -> {
            if (previewApp != null) {
                previewApp.resetCameraToDocument();
            }
        }));
        controls.add(Box.createHorizontalStrut(8));
        controls.add(buildPreviewSlider("Zoom", 1, 200, sliderValue(spnCameraDistance, 12), value -> {
            if (spnCameraDistance != null) {
                spnCameraDistance.setValue((double) value);
            }
        }));
        controls.add(Box.createHorizontalStrut(8));
        controls.add(buildPreviewSlider("Yaw", -180, 180, sliderValue(spnCameraYaw, 35), value -> {
            if (spnCameraYaw != null) {
                spnCameraYaw.setValue((double) value);
            }
        }));
        controls.add(Box.createHorizontalStrut(8));
        controls.add(buildPreviewSlider("Pitch", -89, 89, sliderValue(spnCameraPitch, -15), value -> {
            if (spnCameraPitch != null) {
                spnCameraPitch.setValue((double) value);
            }
        }));
        return controls;
    }

    private JButton cameraButton(String label, Runnable action) {
        JButton button = new JButton(label);
        button.addActionListener(e -> action.run());
        return button;
    }

    private JComponent buildPreviewSlider(String label, int min, int max, int initial, java.util.function.IntConsumer onChange) {
        JPanel panel = new JPanel(new BorderLayout(4, 0));
        panel.setOpaque(false);
        JLabel caption = new JLabel(label);
        caption.setForeground(new Color(205, 214, 224));
        caption.setBorder(new EmptyBorder(0, 0, 0, 4));
        panel.add(caption, BorderLayout.WEST);

        JSlider slider = new JSlider(min, max, initial);
        slider.setOpaque(false);
        slider.setPreferredSize(new Dimension(110, 22));
        slider.addChangeListener(e -> onChange.accept(slider.getValue()));
        panel.add(slider, BorderLayout.CENTER);
        return panel;
    }

    private int sliderValue(JSpinner spinner, int fallback) {
        if (spinner == null) {
            return fallback;
        }
        Object value = spinner.getValue();
        return value instanceof Number ? (int) Math.round(((Number) value).doubleValue()) : fallback;
    }

    private JPanel buildInspector() {
        JPanel panel = createCard();
        panel.setLayout(new BorderLayout(0, 10));
        panel.setPreferredSize(new Dimension(420, 0));
        panel.add(sectionLabel("Effect"), BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        txtName = new JTextField();
        txtName.getDocument().addDocumentListener(SimpleDocumentListener.of(this::markDirty));
        txtAssetId = readOnlyField();
        txtAssetId.setToolTipText("Use this asset id in SceneMax code: effects.effekseer.<assetId>");
        txtImportedEffect = readOnlyField();
        txtOriginalPath = readOnlyField();
        txtImportedAt = readOnlyField();
        txtToolPath = readOnlyField();

        chkLoop = checkBox("Loop");
        chkShowGround = checkBox("Show Ground");
        cboBackground = new JComboBox<>(new String[]{"dark", "light", "transparent"});
        cboBackground.addActionListener(e -> {
            markDirty();
            refreshEmbeddedPreview();
        });
        spnPlaybackSpeed = spinner(0.1, 4.0, 0.1, 1.0);
        spnCameraDistance = spinner(1.0, 200.0, 0.5, 12.0);
        spnCameraYaw = spinner(-360.0, 360.0, 1.0, 35.0);
        spnCameraPitch = spinner(-89.0, 89.0, 1.0, -15.0);
        spnMotionForceScale = spinner(0.0, 6.0, 0.05, 1.0);
        spnMotionOrbitStrength = spinner(-4.0, 4.0, 0.05, 0.0);
        spnMotionDamping = spinner(0.0, 4.0, 0.05, 0.0);

        content.add(labeledField("Display Name", txtName));
        content.add(labeledField("Asset Id", txtAssetId));
        content.add(labeledField("Imported Effect", txtImportedEffect));
        content.add(labeledField("Original Import Path", txtOriginalPath));
        content.add(labeledField("Imported At", txtImportedAt));
        content.add(labeledField("Configured Effekseer Tool", txtToolPath));
        content.add(sectionLabel("Preview Defaults"));

        JPanel previewSettings = new JPanel();
        previewSettings.setOpaque(false);
        previewSettings.setLayout(new BoxLayout(previewSettings, BoxLayout.Y_AXIS));
        previewSettings.setBorder(new EmptyBorder(8, 0, 0, 0));
        previewSettings.add(chkLoop);
        previewSettings.add(chkShowGround);
        previewSettings.add(labeledField("Background", cboBackground));
        previewSettings.add(labeledField("Playback Speed", spnPlaybackSpeed));
        content.add(previewSettings);
        content.add(sectionLabel("Motion Tuning"));

        JPanel motionSettings = new JPanel();
        motionSettings.setOpaque(false);
        motionSettings.setLayout(new BoxLayout(motionSettings, BoxLayout.Y_AXIS));
        motionSettings.setBorder(new EmptyBorder(8, 0, 0, 0));
        motionSettings.add(labeledField("Homing Force", spnMotionForceScale));
        motionSettings.add(labeledField("Orbit Bias", spnMotionOrbitStrength));
        motionSettings.add(labeledField("Velocity Damping", spnMotionDamping));
        content.add(motionSettings);

        JTextArea note = new JTextArea(
                "Use the embedded preview for fast in-app iteration.\n" +
                "The external Effekseer tool is still useful for parity checks against the original authoring environment.");
        note.setEditable(false);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setOpaque(false);
        note.setForeground(new Color(170, 181, 196));
        note.setBorder(new EmptyBorder(12, 0, 0, 0));
        content.add(note);

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildDiagnosticsPanel() {
        JPanel panel = createCard();
        panel.setLayout(new BorderLayout(0, 10));
        panel.setPreferredSize(new Dimension(340, 0));
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(sectionLabel("Diagnostics"), BorderLayout.WEST);
        JButton btnCopyDiagnostics = new JButton("Copy");
        btnCopyDiagnostics.addActionListener(e -> copyDiagnosticsToClipboard());
        header.add(btnCopyDiagnostics, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);

        txtDiagnostics = new JTextArea();
        txtDiagnostics.setEditable(false);
        txtDiagnostics.setLineWrap(true);
        txtDiagnostics.setWrapStyleWord(true);
        txtDiagnostics.setBackground(new Color(20, 24, 31));
        txtDiagnostics.setForeground(new Color(225, 232, 239));
        txtDiagnostics.setBorder(new EmptyBorder(8, 8, 8, 8));
        panel.add(new JScrollPane(txtDiagnostics), BorderLayout.CENTER);
        return panel;
    }

    private void copyDiagnosticsToClipboard() {
        if (txtDiagnostics == null) {
            return;
        }
        StringSelection selection = new StringSelection(txtDiagnostics.getText());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        if (lblPreviewStatus != null) {
            lblPreviewStatus.setText("Diagnostics copied to clipboard.");
        }
    }

    private void loadDocumentIntoUi() {
        txtName.setText(document.getName());
        txtAssetId.setText(document.getAssetId());
        txtImportedEffect.setText(document.getImportedEffectFile());
        txtOriginalPath.setText(document.getOriginalImportPath());
        txtImportedAt.setText(document.getImportedAt());
        chkLoop.setSelected(document.isLoop());
        chkShowGround.setSelected(document.isShowGround());
        cboBackground.setSelectedItem(document.getBackgroundMode());
        spnPlaybackSpeed.setValue(document.getPlaybackSpeed());
        spnCameraDistance.setValue(document.getCameraDistance());
        spnCameraYaw.setValue(document.getCameraYawDeg());
        spnCameraPitch.setValue(document.getCameraPitchDeg());
        spnMotionForceScale.setValue(document.getMotionForceScale());
        spnMotionOrbitStrength.setValue(document.getMotionOrbitStrength());
        spnMotionDamping.setValue(document.getMotionDamping());
        refreshToolPath();
        refreshEmbeddedPreview();
    }

    private void applyUiToDocument() {
        document.setName(txtName.getText().trim());
        document.setLoop(chkLoop.isSelected());
        document.setShowGround(chkShowGround.isSelected());
        document.setBackgroundMode((String) cboBackground.getSelectedItem());
        document.setPlaybackSpeed(doubleValue(spnPlaybackSpeed));
        document.setCameraDistance(doubleValue(spnCameraDistance));
        document.setCameraYawDeg(doubleValue(spnCameraYaw));
        document.setCameraPitchDeg(doubleValue(spnCameraPitch));
        document.setMotionForceScale(doubleValue(spnMotionForceScale));
        document.setMotionOrbitStrength(doubleValue(spnMotionOrbitStrength));
        document.setMotionDamping(doubleValue(spnMotionDamping));
    }

    private void refreshToolPath() {
        txtToolPath.setText(EffekseerTooling.getConfiguredToolPath());
        if (!EffekseerTooling.isConfiguredToolAvailable()) {
            lblPreviewStatus.setText("Embedded preview is active. Choose the Effekseer executable if you also want authoring-tool validation.");
        }
    }

    private void refreshDiagnostics() {
        List<String> lines = new ArrayList<>();
        File importedEffectFile = resolveImportedEffectFile();
        File assetFolder = resolveAssetFolder();

        lines.add("Imported effect: " + safe(document.getImportedEffectFile()));
        lines.add("Original import path: " + safe(document.getOriginalImportPath()));
        lines.add(assetFolder != null && assetFolder.exists()
                ? "Asset folder exists: " + assetFolder.getAbsolutePath()
                : "Asset folder is missing.");
        lines.add(importedEffectFile != null && importedEffectFile.isFile()
                ? "Imported effect file exists."
                : "Imported effect file is missing on disk.");
        lines.add(EffekseerTooling.isConfiguredToolAvailable()
                ? "Effekseer executable: " + EffekseerTooling.getConfiguredToolPath()
                : "Effekseer executable is not configured or auto-detected.");
        lines.add(resolveImportedEffectFile() != null && resolveImportedEffectFile().getName().toLowerCase().endsWith(".efkproj")
                ? "Embedded canvas preview mode: supported for this import."
                : "Embedded canvas preview mode: currently implemented for .efkproj imports.");
        File nativeRuntimeFile = EffekseerNativeEffectResolver.resolveRuntimeEffect(importedEffectFile);
        lines.add(EffekseerNativeBridge.isAvailable()
                ? "Native Effekseer bridge: available."
                : "Native Effekseer bridge: unavailable. " + EffekseerNativeBridge.getLoadMessage());
        lines.add(nativeRuntimeFile != null
                ? "Native runtime effect candidate: " + nativeRuntimeFile.getName()
                : "Native runtime effect candidate: none. Import or export a runtime-ready .efkefc/.efk beside the asset to use native playback.");
        if (lblPreviewStatus != null && lblPreviewStatus.getText() != null && !lblPreviewStatus.getText().isBlank()) {
            lines.add("Designer status: " + lblPreviewStatus.getText());
        }
        if (previewApp != null) {
            lines.add("Camera interaction: " + previewApp.getLastCameraInteractionStatus());
            lines.add("Preview mode: " + previewApp.getPreviewMode());
            lines.add("Native preview status: " + previewApp.getNativePreviewStatus());
        }
        for (String nativePath : EffekseerNativeBridge.describeSearchPaths()) {
            lines.add(nativePath);
        }

        addOptionalFolderDiagnostic(lines, assetFolder, "Texture");
        addOptionalFolderDiagnostic(lines, assetFolder, "Model");
        addOptionalFolderDiagnostic(lines, assetFolder, "Sound");
        addOptionalFolderDiagnostic(lines, assetFolder, "Material");
        addOptionalFolderDiagnostic(lines, assetFolder, "Curve");

        if (importedEffectFile != null && importedEffectFile.isFile() && importedEffectFile.getName().toLowerCase().endsWith(".efkproj")) {
            try {
                EffekseerProject project = EffekseerProjectParser.parse(importedEffectFile);
                lines.add("");
                lines.add("Embedded parser summary:");
                lines.add("Project nodes parsed: " + project.getParsedNodeCount());
                lines.add("Previewable emitters: " + project.getPreviewableEmitterCount());
                lines.add("Skipped nodes: " + project.getSkippedNodeCount());
                if (project.getDiagnostics().isEmpty()) {
                    lines.add("Compatibility notes: none detected by the parser.");
                } else {
                    lines.add("Compatibility notes:");
                    int max = Math.min(12, project.getDiagnostics().size());
                    for (int i = 0; i < max; i++) {
                        lines.add("- " + project.getDiagnostics().get(i));
                    }
                    if (project.getDiagnostics().size() > max) {
                        lines.add("- ... " + (project.getDiagnostics().size() - max) + " more note(s).");
                    }
                }
            } catch (Exception ex) {
                lines.add("");
                lines.add("Embedded parser summary:");
                lines.add("Could not parse imported .efkproj for diagnostics: " + ex.getMessage());
            }
        }

        txtDiagnostics.setText(String.join("\n", lines));
    }

    private void addOptionalFolderDiagnostic(List<String> lines, File assetFolder, String folderName) {
        if (assetFolder == null) {
            return;
        }
        File upper = new File(assetFolder, folderName);
        File lower = new File(assetFolder, folderName.toLowerCase());
        lines.add((upper.exists() || lower.exists() ? folderName + " folder detected." : folderName + " folder not present."));
    }

    private void chooseToolPath() {
        File chosen = EffekseerTooling.chooseToolExecutable(this);
        if (chosen == null) {
            return;
        }
        EffekseerTooling.setConfiguredToolPath(chosen.getAbsolutePath());
        refreshToolPath();
        refreshDiagnostics();
    }

    private void launchPreview() {
        try {
            applyUiToDocument();
            saveDocument();
            String launchedTool = EffekseerTooling.launchPreview(resolveImportedEffectFile());
            lblPreviewStatus.setText("Preview launched with " + launchedTool);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Effekseer Preview", JOptionPane.ERROR_MESSAGE);
        }
        refreshDiagnostics();
    }

    private void revealAssetFolder() {
        try {
            File assetFolder = resolveAssetFolder();
            if (assetFolder == null || !assetFolder.exists()) {
                throw new IOException("Imported asset folder was not found.");
            }
            EffekseerTooling.revealInExplorer(assetFolder);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Reveal Effekseer Assets", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void reimportEffect() {
        try {
            EffekseerImportResult result = EffekseerImporter.reimport(document, documentFile, resourcesFolder);
            document = EffekseerEffectDocument.load(result.getDocumentFile());
            loadDocumentIntoUi();
            dirty = false;
            refreshDiagnostics();
            refreshEmbeddedPreview();
            if (onSavedCallback != null) {
                onSavedCallback.run();
            }
            JOptionPane.showMessageDialog(this,
                    "Reimported " + result.getImportedEffectFile().getName(),
                    "Effekseer Reimport",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Effekseer Reimport", JOptionPane.ERROR_MESSAGE);
        }
    }

    private File resolveImportedEffectFile() {
        if (resourcesFolder == null || document.getImportedEffectFile() == null || document.getImportedEffectFile().isBlank()) {
            return null;
        }
        return new File(resourcesFolder, document.getImportedEffectFile());
    }

    private File resolveAssetFolder() {
        File importedEffectFile = resolveImportedEffectFile();
        return importedEffectFile != null ? importedEffectFile.getParentFile() : null;
    }

    private void markDirty() {
        dirty = true;
        if (onDirtyCallback != null) {
            onDirtyCallback.run();
        }
    }

    private void initPreview() {
        previewApp = new EffekseerPreviewApp();
        previewApp.setStatusListener(status -> SwingUtilities.invokeLater(() -> {
            lblPreviewStatus.setText(status);
            refreshDiagnostics();
            updateModifierAvailability();
        }));

        AppSettings settings = new AppSettings(true);
        settings.setWidth(900);
        settings.setHeight(640);
        settings.setSamples(4);
        settings.setVSync(true);
        settings.setFrameRate(60);
        settings.setAudioRenderer(null);
        settings.setUseInput(false);
        previewApp.setSettings(settings);
        previewApp.setPauseOnLostFocus(false);
        previewApp.setShowSettings(false);
        previewApp.createCanvas();

        JmeCanvasContext ctx = (JmeCanvasContext) previewApp.getContext();
        ctx.setSystemListener(previewApp);
        previewCanvas = ctx.getCanvas();
        previewCanvas.setFocusable(true);
        previewCanvas.setMinimumSize(new Dimension(100, 100));
        previewCanvas.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (previewApp != null && previewCanvas.getWidth() > 0 && previewCanvas.getHeight() > 0) {
                    previewApp.enqueue(() -> {
                        previewApp.reshape(previewCanvas.getWidth(), previewCanvas.getHeight());
                        return null;
                    });
                }
            }
        });
        previewApp.startCanvas();
        previewContainer.add(previewCanvas, BorderLayout.CENTER);

        diagnosticsRefreshTimer = new javax.swing.Timer(750, e -> {
            refreshDiagnostics();
            updateModifierAvailability();
        });
        diagnosticsRefreshTimer.start();
    }

    private void ensurePreviewInitialized() {
        if (previewApp == null || previewCanvas == null) {
            initPreview();
            loadDocumentIntoUi();
        }
    }

    private void disposePreview() {
        if (previewApp != null) {
            previewApp.stop();
            previewApp = null;
        }
        if (diagnosticsRefreshTimer != null) {
            diagnosticsRefreshTimer.stop();
            diagnosticsRefreshTimer = null;
        }
        if (previewCanvas != null && previewContainer != null) {
            previewContainer.remove(previewCanvas);
            previewContainer.revalidate();
            previewContainer.repaint();
            previewCanvas = null;
        }
    }

    private void refreshEmbeddedPreview() {
        if (previewApp == null) {
            return;
        }
        try {
            applyUiToDocument();
        } catch (Exception ignored) {
        }
        previewApp.updateDocument(document, resolveImportedEffectFile());
        updateModifierAvailability();
    }

    private void replayEmbeddedPreview() {
        if (previewApp == null) {
            return;
        }
        try {
            applyUiToDocument();
        } catch (Exception ignored) {
        }
        previewApp.replayDocument(document, resolveImportedEffectFile());
        updateModifierAvailability();
    }

    private void updateModifierAvailability() {
        boolean nativeMode = previewApp != null && "native".equals(previewApp.getPreviewMode());
        boolean enabled = !nativeMode;
        String tooltip = nativeMode
                ? "Custom motion tuning currently applies only to the embedded preview. Native Effekseer uses the authored effect behavior."
                : null;
        if (spnMotionForceScale != null) {
            spnMotionForceScale.setEnabled(enabled);
            spnMotionForceScale.setToolTipText(tooltip);
        }
        if (spnMotionOrbitStrength != null) {
            spnMotionOrbitStrength.setEnabled(enabled);
            spnMotionOrbitStrength.setToolTipText(tooltip);
        }
        if (spnMotionDamping != null) {
            spnMotionDamping.setEnabled(enabled);
            spnMotionDamping.setToolTipText(tooltip);
        }
    }

    private JCheckBox checkBox(String title) {
        JCheckBox checkBox = new JCheckBox(title);
        checkBox.setOpaque(false);
        checkBox.setForeground(new Color(225, 232, 239));
        checkBox.addActionListener(e -> {
            markDirty();
            refreshEmbeddedPreview();
        });
        return checkBox;
    }

    private JSpinner spinner(double min, double max, double step, double initial) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(initial, min, max, step));
        spinner.addChangeListener(e -> {
            markDirty();
            refreshEmbeddedPreview();
        });
        return spinner;
    }

    private double doubleValue(JSpinner spinner) {
        Object value = spinner.getValue();
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }

    private JPanel labeledField(String label, JComponent field) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(0, 0, 10, 0));
        JLabel lbl = new JLabel(label);
        lbl.setForeground(new Color(160, 174, 191));
        panel.add(lbl, BorderLayout.NORTH);
        if (field instanceof JSpinner) {
            panel.add(createSpinnerWithSlider((JSpinner) field), BorderLayout.CENTER);
        } else {
            panel.add(field, BorderLayout.CENTER);
        }
        return panel;
    }

    private JComponent createSpinnerWithSlider(JSpinner spinner) {
        SpinnerNumberModel model = spinner.getModel() instanceof SpinnerNumberModel
                ? (SpinnerNumberModel) spinner.getModel()
                : null;
        if (model == null
                || !(model.getMinimum() instanceof Number)
                || !(model.getMaximum() instanceof Number)
                || !(model.getStepSize() instanceof Number)) {
            return spinner;
        }

        double min = ((Number) model.getMinimum()).doubleValue();
        double max = ((Number) model.getMaximum()).doubleValue();
        double step = ((Number) model.getStepSize()).doubleValue();
        if (step <= 0d || max <= min) {
            return spinner;
        }

        int sliderMax = Math.max(1, (int) Math.round((max - min) / step));
        JSlider slider = new JSlider(0, sliderMax);
        slider.setOpaque(false);
        slider.setValue(toSliderValue(spinner, min, step, sliderMax));
        slider.setToolTipText("Drag to adjust");

        final boolean[] syncing = {false};

        slider.addChangeListener(e -> {
            if (syncing[0]) {
                return;
            }
            syncing[0] = true;
            try {
                double value = min + slider.getValue() * step;
                value = Math.max(min, Math.min(max, value));
                spinner.setValue(coerceSpinnerValue(model.getValue(), value));
            } finally {
                syncing[0] = false;
            }
        });

        spinner.addChangeListener(e -> {
            if (syncing[0]) {
                return;
            }
            syncing[0] = true;
            try {
                slider.setValue(toSliderValue(spinner, min, step, sliderMax));
            } finally {
                syncing[0] = false;
            }
        });

        JPanel editorRow = new JPanel(new BorderLayout(8, 0));
        editorRow.setOpaque(false);
        editorRow.add(spinner, BorderLayout.WEST);
        editorRow.add(slider, BorderLayout.CENTER);
        return editorRow;
    }

    private int toSliderValue(JSpinner spinner, double min, double step, int sliderMax) {
        Object raw = spinner.getValue();
        double value = raw instanceof Number ? ((Number) raw).doubleValue() : min;
        int sliderValue = (int) Math.round((value - min) / step);
        return Math.max(0, Math.min(sliderMax, sliderValue));
    }

    private Object coerceSpinnerValue(Object currentValue, double value) {
        if (currentValue instanceof Integer) {
            return (int) Math.round(value);
        }
        if (currentValue instanceof Long) {
            return Math.round(value);
        }
        if (currentValue instanceof Float) {
            return (float) value;
        }
        return value;
    }

    private JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(new Color(245, 247, 250));
        label.setFont(label.getFont().deriveFont(Font.BOLD, 16f));
        return label;
    }

    private JPanel createCard() {
        JPanel panel = new JPanel();
        panel.setBackground(new Color(28, 33, 42));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(52, 60, 73)),
                new EmptyBorder(12, 12, 12, 12)
        ));
        return panel;
    }

    private JTextField readOnlyField() {
        JTextField field = new JTextField();
        field.setEditable(false);
        return field;
    }

    private File resolveResourcesFolder(String projectPath, File file) {
        if (projectPath != null && !projectPath.isBlank()) {
            return new File(projectPath, "resources");
        }
        File parent = file.getParentFile();
        if (parent == null || parent.getParentFile() == null) {
            return null;
        }
        return new File(parent.getParentFile(), "resources");
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "(not set)" : value;
    }
}
