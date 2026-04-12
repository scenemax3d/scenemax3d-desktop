package com.scenemax.designer.animation;

import com.jme3.system.AppSettings;
import com.jme3.system.JmeCanvasContext;
import com.scenemaxeng.common.types.AssetsMapping;
import com.scenemaxeng.common.types.ResourceSetup;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ImportAnimationPanel extends JPanel {

    private final File resourcesFolder;
    private JTextField txtFile;
    private JTextField txtName;
    private JTextArea txtPreview;
    private JButton btnInspect;
    private JButton btnImport;
    private JPanel previewCanvasContainer;
    private AnimationPreviewApp previewApp;
    private Canvas previewCanvas;
    private JComboBox<String> cboPreviewModel;
    private int lastDragX;
    private int lastDragY;
    private File selectedFile;
    private AnimationImportResult inspectedResult;
    private Consumer<Boolean> onCloseCallback;
    private final List<String> availableModelNames = new ArrayList<>();
    private final Map<String, ResourceSetup> modelResources = new HashMap<>();

    public ImportAnimationPanel(File resourcesFolder) {
        super(new BorderLayout(8, 8));
        this.resourcesFolder = resourcesFolder;
        loadAvailableModels();
        buildUi();
    }

    public void setOnCloseCallback(Consumer<Boolean> onCloseCallback) {
        this.onCloseCallback = onCloseCallback;
    }

    private void buildUi() {
        JPanel form = new JPanel();
        form.setLayout(new javax.swing.BoxLayout(form, javax.swing.BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        form.setPreferredSize(new Dimension(360, 0));

        form.add(new JLabel("Animation file:"));
        txtFile = new JTextField();
        txtFile.setEditable(false);
        txtFile.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        form.add(txtFile);

        JPanel fileButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton btnSelect = new JButton("Select File...");
        btnSelect.addActionListener(e -> selectFile());
        btnInspect = new JButton("Preview");
        btnInspect.setEnabled(false);
        btnInspect.addActionListener(e -> inspectSelectedFile());
        fileButtons.add(btnSelect);
        fileButtons.add(btnInspect);
        form.add(fileButtons);

        form.add(new JLabel("Runtime animation name:"));
        txtName = new JTextField();
        txtName.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        form.add(txtName);

        JPanel importButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 8));
        btnImport = new JButton("Import");
        btnImport.setEnabled(false);
        btnImport.addActionListener(e -> importSelectedFile());
        JButton btnCancel = new JButton("Cancel");
        btnCancel.addActionListener(e -> close(false));
        importButtons.add(btnImport);
        importButtons.add(btnCancel);
        form.add(importButtons);

        add(form, BorderLayout.WEST);

        JPanel previewPanel = new JPanel(new BorderLayout(6, 6));
        previewPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 10));

        JPanel previewControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        previewControls.add(new JLabel("Preview model:"));
        cboPreviewModel = new JComboBox<>(availableModelNames.toArray(new String[0]));
        cboPreviewModel.setPreferredSize(new Dimension(210, 28));
        cboPreviewModel.setEnabled(!availableModelNames.isEmpty());
        cboPreviewModel.addActionListener(e -> playAnimationPreview());
        previewControls.add(cboPreviewModel);
        previewControls.add(new JLabel("Left drag rotates. Mouse wheel zooms."));
        previewPanel.add(previewControls, BorderLayout.NORTH);

        previewCanvasContainer = new JPanel(new BorderLayout());
        previewCanvasContainer.setPreferredSize(new Dimension(640, 420));
        previewCanvasContainer.setMinimumSize(new Dimension(240, 180));
        previewCanvasContainer.setBorder(BorderFactory.createTitledBorder("Live Animation Preview"));

        txtPreview = new JTextArea();
        txtPreview.setEditable(false);
        txtPreview.setLineWrap(true);
        txtPreview.setWrapStyleWord(true);
        txtPreview.setText("Choose an FBX, DAE, BVH, GLB, GLTF, or another MonkeyWrench-supported file to inspect its animation clips.");
        JScrollPane previewScroll = new JScrollPane(txtPreview);
        previewScroll.setBorder(BorderFactory.createTitledBorder("Import Diagnostics"));
        previewScroll.setPreferredSize(new Dimension(640, 180));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, previewCanvasContainer, previewScroll);
        split.setResizeWeight(0.72);
        split.setBorder(null);
        previewPanel.add(split, BorderLayout.CENTER);
        add(previewPanel, BorderLayout.CENTER);

        initPreview();
    }

    private void selectFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(downloadsFolder());
        chooser.setDialogTitle("Import Animation");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Animation/model files (*.fbx, *.dae, *.bvh, *.glb, *.gltf, *.blend, *.3ds)",
                "fbx", "dae", "bvh", "glb", "gltf", "blend", "3ds", "3mf", "lwo", "obj", "ply", "stl"));

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        selectedFile = chooser.getSelectedFile();
        inspectedResult = null;
        txtFile.setText(selectedFile.getAbsolutePath());
        txtName.setText(sanitizeAssetId(stripExtension(selectedFile.getName())));
        btnInspect.setEnabled(true);
        btnImport.setEnabled(true);
        inspectSelectedFile();
    }

    private void inspectSelectedFile() {
        if (selectedFile == null) {
            return;
        }

        setBusy(true, "Import preview is reading " + selectedFile.getName() + "...");
        new SwingWorker<AnimationImportResult, Void>() {
            @Override
            protected AnimationImportResult doInBackground() throws Exception {
                return AnimationImportProcessRunner.inspect(selectedFile);
            }

            @Override
            protected void done() {
                try {
                    AnimationImportResult result = get();
                    inspectedResult = result;
                    txtPreview.setText(formatPreview(result, false));
                    playAnimationPreview();
                } catch (Exception ex) {
                    txtPreview.setText("Preview failed:\n" + rootMessage(ex));
                } finally {
                    setBusy(false, null);
                }
            }
        }.execute();
    }

    private void playAnimationPreview() {
        if (previewApp == null) {
            initPreview();
        }
        ResourceSetup model = selectedPreviewModel();
        if (model == null) {
            return;
        }
        if (inspectedResult == null || inspectedResult.getAnimationFile() == null) {
            return;
        }

        String animationName = txtName.getText().trim();
        if (animationName.isEmpty()) {
            animationName = "candidate";
        }
        String clipName = inspectedResult.getSelectedClipName();
        if (clipName == null || clipName.isBlank()) {
            clipName = inspectedResult.getClipNames().isEmpty()
                    ? "mixamo.com"
                    : inspectedResult.getClipNames().get(0);
        }
        previewApp.preview(model, inspectedResult.getAnimationFile(), animationName, clipName);
    }

    private void importSelectedFile() {
        if (selectedFile == null) {
            JOptionPane.showMessageDialog(this, "Please select an animation file first.", "Animation Import", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String name = txtName.getText().trim();
        setBusy(true, "Importing " + selectedFile.getName() + "...");
        new SwingWorker<AnimationImportResult, Void>() {
            @Override
            protected AnimationImportResult doInBackground() throws Exception {
                return AnimationImportProcessRunner.importAnimation(selectedFile, resourcesFolder, name);
            }

            @Override
            protected void done() {
                try {
                    AnimationImportResult result = get();
                    txtPreview.setText(formatPreview(result, true)
                            + "\nSaved: " + result.getAnimationFile().getAbsolutePath());
                    JOptionPane.showMessageDialog(ImportAnimationPanel.this,
                            "Animation " + name + " imported successfully.",
                            "Animation Import", JOptionPane.INFORMATION_MESSAGE);
                    close(true);
                } catch (Exception ex) {
                    txtPreview.setText("Import failed:\n" + rootMessage(ex));
                    JOptionPane.showMessageDialog(ImportAnimationPanel.this,
                            rootMessage(ex), "Animation Import", JOptionPane.ERROR_MESSAGE);
                } finally {
                    setBusy(false, null);
                }
            }
        }.execute();
    }

    private String formatPreview(AnimationImportResult result, boolean imported) {
        StringBuilder sb = new StringBuilder();
        sb.append(imported ? "Imported animation clips:\n" : "Animation clips found:\n");
        List<String> clips = result.getClipNames();
        if (clips == null || clips.isEmpty()) {
            sb.append("No animation clips found.");
            return sb.toString();
        }
        for (String clip : clips) {
            sb.append("- ").append(clip);
            if (clip.equals(result.getSelectedClipName())) {
                sb.append(" (preview/import clip)");
            }
            sb.append("\n");
        }
        List<String> summaries = result.getClipSummaries();
        if (summaries != null && !summaries.isEmpty()) {
            sb.append("\nMotion summary:\n");
            for (String summary : summaries) {
                sb.append("- ").append(summary).append("\n");
            }
        }
        sb.append("\nAt runtime, call the animation by the name in the left panel.");
        return sb.toString();
    }

    private void setBusy(boolean busy, String message) {
        btnInspect.setEnabled(!busy && selectedFile != null);
        btnImport.setEnabled(!busy && selectedFile != null);
        if (message != null) {
            txtPreview.setText(message);
        }
    }

    private void close(boolean imported) {
        disposePreview();
        if (onCloseCallback != null) {
            onCloseCallback.accept(imported);
        }
    }

    private String rootMessage(Exception ex) {
        Throwable root = ex;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.toString() : root.getMessage();
    }

    private String stripExtension(String name) {
        if (name == null) {
            return "";
        }
        String lower = name.toLowerCase();
        if (lower.endsWith(".mesh.xml")) {
            return name.substring(0, name.length() - ".mesh.xml".length());
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String sanitizeAssetId(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "animation";
        }
        String sanitized = raw.trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9_\\-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        return sanitized.isEmpty() ? "animation" : sanitized;
    }

    private void initPreview() {
        if (previewApp != null || previewCanvasContainer == null) {
            return;
        }
        previewApp = new AnimationPreviewApp();
        previewApp.setStatusListener(message -> SwingUtilities.invokeLater(() -> appendPreviewStatus(message)));
        if (resourcesFolder != null) {
            previewApp.setResourcesFolder(resourcesFolder.getAbsolutePath());
        }

        AppSettings settings = new AppSettings(true);
        settings.setWidth(900);
        settings.setHeight(640);
        settings.setSamples(4);
        settings.setVSync(true);
        settings.setFrameRate(60);
        settings.setAudioRenderer(null);
        previewApp.setSettings(settings);
        previewApp.setPauseOnLostFocus(false);
        previewApp.setShowSettings(false);
        previewApp.createCanvas();

        JmeCanvasContext ctx = (JmeCanvasContext) previewApp.getContext();
        ctx.setSystemListener(previewApp);
        previewCanvas = ctx.getCanvas();
        previewCanvas.setFocusable(true);
        previewCanvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastDragX = e.getX();
                lastDragY = e.getY();
                previewCanvas.requestFocusInWindow();
            }
        });
        previewCanvas.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (previewApp == null) {
                    return;
                }
                int dx = e.getX() - lastDragX;
                int dy = e.getY() - lastDragY;
                lastDragX = e.getX();
                lastDragY = e.getY();
                previewApp.orbit(dx, dy);
            }
        });
        previewCanvas.addMouseWheelListener(e -> {
            if (previewApp != null) {
                previewApp.zoom(e.getWheelRotation() * 0.8f);
            }
        });
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
        previewCanvasContainer.add(previewCanvas, BorderLayout.CENTER);
    }

    private void disposePreview() {
        if (previewApp != null) {
            previewApp.stop();
            previewApp = null;
        }
        if (previewCanvas != null && previewCanvasContainer != null) {
            previewCanvasContainer.remove(previewCanvas);
            previewCanvasContainer.revalidate();
            previewCanvasContainer.repaint();
            previewCanvas = null;
        }
    }

    private File downloadsFolder() {
        File downloads = new File(System.getProperty("user.home"), "Downloads");
        return downloads.isDirectory() ? downloads : new File(System.getProperty("user.home"));
    }

    private void appendPreviewStatus(String message) {
        if (message == null || message.isBlank() || txtPreview == null) {
            return;
        }
        String text = txtPreview.getText();
        if (text == null || text.isBlank()) {
            txtPreview.setText(message);
        } else if (!text.endsWith(message)) {
            txtPreview.setText(text + "\n\nLive preview:\n" + message);
        }
    }

    private void loadAvailableModels() {
        availableModelNames.clear();
        modelResources.clear();
        if (resourcesFolder == null) {
            return;
        }
        try {
            AssetsMapping mapping = new AssetsMapping(resourcesFolder.getCanonicalPath());
            List<ResourceSetup> setups = new ArrayList<>(mapping.get3DModelsIndex().values());
            setups.sort(Comparator.comparing(res -> res.name.toLowerCase()));
            for (ResourceSetup setup : setups) {
                availableModelNames.add(setup.name);
                modelResources.put(setup.name, setup);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private ResourceSetup selectedPreviewModel() {
        if (cboPreviewModel == null || cboPreviewModel.getSelectedItem() == null) {
            return null;
        }
        return modelResources.get(cboPreviewModel.getSelectedItem().toString());
    }
}
