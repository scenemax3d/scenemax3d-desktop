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
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.json.JSONArray;
import org.json.JSONObject;
import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ImportAnimationPanel extends JPanel {

    private final File resourcesFolder;
    private JTextField txtFile;
    private JTextField txtName;
    private JSpinner spnBevySkipTopAnimatedTargets;
    private JTextField txtBevyExcludeBones;
    private JTextArea txtPreview;
    private JButton btnInspect;
    private JButton btnImport;
    private JButton btnRemove;
    private JButton btnBevyRetarget;
    private JTable tblFiles;
    private AnimationFilesTableModel filesTableModel;
    private JPanel previewCanvasContainer;
    private AnimationPreviewApp previewApp;
    private Canvas previewCanvas;
    private JComboBox<String> cboPreviewModel;
    private int lastDragX;
    private int lastDragY;
    private File selectedFile;
    private AnimationImportResult inspectedResult;
    private Consumer<Boolean> onCloseCallback;
    private boolean updatingNameField;
    private boolean updatingRetargetFields;
    private final List<String> availableModelNames = new ArrayList<>();
    private final Map<String, ResourceSetup> modelResources = new HashMap<>();
    private final List<BatchAnimationItem> animationItems = new ArrayList<>();

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
        form.setPreferredSize(new Dimension(430, 0));

        form.add(new JLabel("Animation files:"));
        txtFile = new JTextField();
        txtFile.setEditable(false);
        txtFile.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        form.add(txtFile);

        JPanel fileButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton btnSelect = new JButton("Select Files...");
        btnSelect.addActionListener(e -> selectFile());
        btnInspect = new JButton("Preview");
        btnInspect.setEnabled(false);
        btnInspect.addActionListener(e -> inspectSelectedFile());
        btnRemove = new JButton("Remove");
        btnRemove.setEnabled(false);
        btnRemove.addActionListener(e -> removeSelectedFile());
        fileButtons.add(btnSelect);
        fileButtons.add(btnInspect);
        fileButtons.add(btnRemove);
        form.add(fileButtons);

        filesTableModel = new AnimationFilesTableModel();
        tblFiles = new JTable(filesTableModel);
        tblFiles.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tblFiles.setRowHeight(24);
        tblFiles.getColumnModel().getColumn(0).setPreferredWidth(58);
        tblFiles.getColumnModel().getColumn(1).setPreferredWidth(130);
        tblFiles.getColumnModel().getColumn(2).setPreferredWidth(130);
        tblFiles.getColumnModel().getColumn(3).setPreferredWidth(95);
        tblFiles.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectCurrentTableRow();
            }
        });
        JScrollPane filesScroll = new JScrollPane(tblFiles);
        filesScroll.setPreferredSize(new Dimension(400, 190));
        filesScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));
        form.add(filesScroll);

        form.add(new JLabel("Runtime animation name:"));
        txtName = new JTextField();
        txtName.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        txtName.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateSelectedItemName();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateSelectedItemName();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateSelectedItemName();
            }
        });
        txtName.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                BatchAnimationItem item = selectedItem();
                if (item != null && item.animationName.trim().isEmpty()) {
                    item.animationName = uniqueAnimationName(sanitizeAssetId(stripExtension(item.sourceFile.getName())), item);
                    refreshSelectedItem();
                }
            }
        });
        form.add(txtName);

        JPanel bevyRetargetPanel = new JPanel();
        bevyRetargetPanel.setLayout(new javax.swing.BoxLayout(bevyRetargetPanel, javax.swing.BoxLayout.Y_AXIS));
        bevyRetargetPanel.setBorder(BorderFactory.createTitledBorder("Bevy Retarget"));
        bevyRetargetPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        bevyRetargetPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));

        JPanel skipRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        skipRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        skipRow.add(new JLabel("Skip top animated targets:"));
        spnBevySkipTopAnimatedTargets = new JSpinner(new SpinnerNumberModel(1, 0, 16, 1));
        spnBevySkipTopAnimatedTargets.setPreferredSize(new Dimension(64, 26));
        spnBevySkipTopAnimatedTargets.addChangeListener(e -> updateSelectedItemRetargetOptions());
        skipRow.add(spnBevySkipTopAnimatedTargets);
        bevyRetargetPanel.add(skipRow);

        bevyRetargetPanel.add(new JLabel("Exclude bones, comma-separated:"));
        txtBevyExcludeBones = new JTextField();
        txtBevyExcludeBones.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        txtBevyExcludeBones.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateSelectedItemRetargetOptions();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateSelectedItemRetargetOptions();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateSelectedItemRetargetOptions();
            }
        });
        bevyRetargetPanel.add(txtBevyExcludeBones);
        JPanel bevyButtonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        bevyButtonRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnBevyRetarget = new JButton("Open Bevy Retarget...");
        btnBevyRetarget.setEnabled(false);
        btnBevyRetarget.addActionListener(e -> openBevyRetargetDesigner());
        bevyButtonRow.add(btnBevyRetarget);
        bevyRetargetPanel.add(bevyButtonRow);
        form.add(bevyRetargetPanel);

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
        txtPreview.setText("Choose one or more FBX, DAE, BVH, GLB, GLTF, or another MonkeyWrench-supported files to inspect their animation clips.");
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
        chooser.setDialogTitle("Import Animations");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Animation/model files (*.fbx, *.dae, *.bvh, *.glb, *.gltf, *.blend, *.3ds)",
                "fbx", "dae", "bvh", "glb", "gltf", "blend", "3ds", "3mf", "lwo", "obj", "ply", "stl"));

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File[] selectedFiles = chooser.getSelectedFiles();
        if (selectedFiles == null || selectedFiles.length == 0) {
            selectedFiles = new File[]{chooser.getSelectedFile()};
        }

        int firstNewRow = animationItems.size();
        for (File file : selectedFiles) {
            if (file == null) {
                continue;
            }
            BatchAnimationItem existing = findItem(file);
            if (existing != null) {
                existing.importEnabled = true;
                continue;
            }
            String baseName = sanitizeAssetId(stripExtension(file.getName()));
            animationItems.add(new BatchAnimationItem(file, uniqueAnimationName(baseName, null)));
        }
        filesTableModel.fireTableDataChanged();
        if (!animationItems.isEmpty()) {
            tblFiles.setRowSelectionInterval(Math.min(firstNewRow, animationItems.size() - 1),
                    Math.min(firstNewRow, animationItems.size() - 1));
        }
        updateBatchControls();
        inspectSelectedFile();
    }

    private void inspectSelectedFile() {
        BatchAnimationItem item = selectedItem();
        if (item == null) {
            return;
        }
        stopTableEditing();
        persistSelectedName();

        selectedFile = item.sourceFile;
        setBusy(true, "Import preview is reading " + selectedFile.getName() + "...");
        new SwingWorker<AnimationImportResult, Void>() {
            @Override
            protected AnimationImportResult doInBackground() throws Exception {
                return AnimationImportProcessRunner.inspect(item.sourceFile);
            }

            @Override
            protected void done() {
                try {
                    AnimationImportResult result = get();
                    item.inspectedResult = result;
                    item.status = "Previewed";
                    inspectedResult = result;
                    txtPreview.setText(formatPreview(result, false));
                    refreshSelectedItem();
                    playAnimationPreview();
                } catch (Exception ex) {
                    item.status = "Preview failed";
                    refreshSelectedItem();
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
        BatchAnimationItem item = selectedItem();
        if (item != null) {
            selectedFile = item.sourceFile;
            inspectedResult = item.inspectedResult;
            persistSelectedName();
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
        stopTableEditing();
        persistSelectedName();
        List<BatchAnimationItem> itemsToImport = checkedItems();
        if (itemsToImport.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please check at least one animation file to import.", "Animation Import", JOptionPane.WARNING_MESSAGE);
            return;
        }

        setBusy(true, "Importing " + itemsToImport.size() + " animation file(s)...");
        new SwingWorker<List<BatchImportReport>, String>() {
            @Override
            protected List<BatchImportReport> doInBackground() {
                List<BatchImportReport> reports = new ArrayList<>();
                for (BatchAnimationItem item : itemsToImport) {
                    try {
                        item.status = "Importing";
                        publish(item.sourceFile.getName());
                        AnimationImportResult result = AnimationImportProcessRunner.importAnimation(
                                item.sourceFile, resourcesFolder, item.animationName.trim(), item.importOptions);
                        item.inspectedResult = result;
                        item.status = "Imported";
                        reports.add(BatchImportReport.success(item, result));
                    } catch (Exception ex) {
                        item.status = "Failed";
                        reports.add(BatchImportReport.failure(item, rootMessage(ex)));
                    }
                }
                return reports;
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    txtPreview.setText("Importing " + chunks.get(chunks.size() - 1) + "...");
                }
                filesTableModel.fireTableDataChanged();
            }

            @Override
            protected void done() {
                try {
                    List<BatchImportReport> reports = get();
                    filesTableModel.fireTableDataChanged();
                    String report = formatImportReport(reports);
                    txtPreview.setText(report);
                    JTextArea reportArea = new JTextArea(report, 18, 72);
                    reportArea.setEditable(false);
                    reportArea.setLineWrap(true);
                    reportArea.setWrapStyleWord(true);
                    JOptionPane.showMessageDialog(ImportAnimationPanel.this,
                            new JScrollPane(reportArea),
                            "Animation Import Report", JOptionPane.INFORMATION_MESSAGE);
                    if (reports.stream().anyMatch(r -> r.success)) {
                        close(true);
                    }
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

    private void openBevyRetargetDesigner() {
        stopTableEditing();
        persistSelectedName();
        updateSelectedItemRetargetOptions();
        BatchAnimationItem item = selectedItem();
        if (item == null) {
            JOptionPane.showMessageDialog(this, "Please select an animation file first.", "Bevy Retarget", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String animationName = item.animationName == null ? "" : item.animationName.trim();
        if (animationName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a runtime animation name first.", "Bevy Retarget", JOptionPane.WARNING_MESSAGE);
            return;
        }
        ResourceSetup previewModel = selectedPreviewModel();
        String previewModelName = previewModel == null ? null : previewModel.name;

        setBusy(true, "Preparing Bevy retarget preview for " + animationName + "...");
        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                AnimationImportResult result = existingImportedAnimationResult(animationName);
                if (result == null) {
                    item.status = "Importing";
                    publish("Importing " + item.sourceFile.getName() + " before Bevy retarget preview...");
                    result = AnimationImportProcessRunner.importAnimation(
                            item.sourceFile, resourcesFolder, animationName, item.importOptions);
                } else {
                    item.status = "Retargeting";
                    publish("Using existing imported animation for Bevy retarget preview.");
                }
                item.inspectedResult = result;
                item.status = "Retargeting";
                publish("Opening Bevy retarget preview. Save and close the Bevy window when the motion looks right.");

                Process process = BevyRetargetDesignerLauncher.open(resourcesFolder, animationName, previewModelName);
                StringBuilder processOutput = new StringBuilder();
                Thread drainer = new Thread(() -> captureProcessOutput(process, processOutput), "bevy-retarget-output");
                drainer.setDaemon(true);
                drainer.start();
                int exitCode = process.waitFor();
                drainer.join(1000);
                if (exitCode != 0) {
                    throw new IOException("Bevy retarget designer exited with code " + exitCode + ".\n" + tail(processOutput.toString()));
                }
                loadSavedRetargetOptions(item);
                item.status = "Imported";
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    txtPreview.setText(chunks.get(chunks.size() - 1));
                }
                filesTableModel.fireTableDataChanged();
            }

            @Override
            protected void done() {
                try {
                    get();
                    filesTableModel.fireTableDataChanged();
                    updateRetargetFields(item);
                    txtPreview.setText("Bevy retarget settings were saved for " + animationName + ".");
                } catch (Exception ex) {
                    item.status = "Failed";
                    txtPreview.setText("Bevy retarget failed:\n" + rootMessage(ex));
                    JOptionPane.showMessageDialog(ImportAnimationPanel.this,
                            rootMessage(ex), "Bevy Retarget", JOptionPane.ERROR_MESSAGE);
                } finally {
                    setBusy(false, null);
                }
            }
        }.execute();
    }

    private AnimationImportResult existingImportedAnimationResult(String animationName) throws IOException {
        File indexFile = animationIndexFile();
        if (!indexFile.isFile()) {
            return null;
        }
        JSONObject root = new JSONObject(new String(Files.readAllBytes(indexFile.toPath()), StandardCharsets.UTF_8));
        JSONArray animations = root.optJSONArray("animations");
        if (animations == null) {
            return null;
        }
        for (int i = 0; i < animations.length(); i++) {
            JSONObject animation = animations.optJSONObject(i);
            if (animation == null || !animationName.equalsIgnoreCase(animation.optString("name", ""))) {
                continue;
            }
            String path = animation.optString("path", "").trim();
            if (path.isEmpty()) {
                return null;
            }
            File animationFile = new File(resourcesFolder, path.replace('/', File.separatorChar));
            if (!animationFile.isFile() || !isInsideResources(animationFile)) {
                return null;
            }
            String clipName = animation.optString("clipName", "").trim();
            List<String> clips = clipName.isEmpty()
                    ? Collections.emptyList()
                    : Collections.singletonList(clipName);
            return new AnimationImportResult(animationFile.getParentFile(), animationFile, clips,
                    Collections.emptyList(), clipName.isEmpty() ? null : clipName, null);
        }
        return null;
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
        btnInspect.setEnabled(!busy && selectedItem() != null);
        btnImport.setEnabled(!busy && !checkedItems().isEmpty());
        btnRemove.setEnabled(!busy && selectedItem() != null);
        btnBevyRetarget.setEnabled(!busy && selectedItem() != null);
        tblFiles.setEnabled(!busy);
        txtName.setEnabled(!busy && selectedItem() != null);
        spnBevySkipTopAnimatedTargets.setEnabled(!busy && selectedItem() != null);
        txtBevyExcludeBones.setEnabled(!busy && selectedItem() != null);
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

    private void selectCurrentTableRow() {
        BatchAnimationItem item = selectedItem();
        updatingNameField = true;
        try {
            if (item == null) {
                selectedFile = null;
                inspectedResult = null;
                txtFile.setText(animationItems.isEmpty() ? "" : animationItems.size() + " file(s) selected");
                txtName.setText("");
                updateRetargetFields(null);
            } else {
                selectedFile = item.sourceFile;
                inspectedResult = item.inspectedResult;
                txtFile.setText(item.sourceFile.getAbsolutePath());
                txtName.setText(item.animationName);
                updateRetargetFields(item);
                if (item.inspectedResult != null) {
                    txtPreview.setText(formatPreview(item.inspectedResult, "Imported".equals(item.status)));
                    playAnimationPreview();
                }
            }
        } finally {
            updatingNameField = false;
        }
        updateBatchControls();
    }

    private void removeSelectedFile() {
        stopTableEditing();
        int row = tblFiles == null ? -1 : tblFiles.getSelectedRow();
        if (row < 0 || row >= animationItems.size()) {
            return;
        }
        animationItems.remove(row);
        filesTableModel.fireTableDataChanged();
        if (!animationItems.isEmpty()) {
            int next = Math.min(row, animationItems.size() - 1);
            tblFiles.setRowSelectionInterval(next, next);
        } else {
            selectCurrentTableRow();
        }
        updateBatchControls();
    }

    private void updateSelectedItemName() {
        if (updatingNameField) {
            return;
        }
        BatchAnimationItem item = selectedItem();
        if (item == null) {
            return;
        }
        item.animationName = txtName.getText().trim();
        int row = animationItems.indexOf(item);
        if (row >= 0) {
            filesTableModel.fireTableRowsUpdated(row, row);
        }
    }

    private void loadSavedRetargetOptions(BatchAnimationItem item) throws IOException {
        File indexFile = animationIndexFile();
        if (!indexFile.isFile()) {
            return;
        }
        JSONObject root = new JSONObject(new String(Files.readAllBytes(indexFile.toPath()), StandardCharsets.UTF_8));
        JSONArray animations = root.optJSONArray("animations");
        if (animations == null) {
            return;
        }
        for (int i = 0; i < animations.length(); i++) {
            JSONObject animation = animations.optJSONObject(i);
            if (animation == null || !item.animationName.equalsIgnoreCase(animation.optString("name", ""))) {
                continue;
            }
            JSONObject bevyRetarget = animation.optJSONObject("bevyRetarget");
            if (bevyRetarget == null) {
                return;
            }
            item.importOptions.setBevyRetargetProfile(bevyRetarget.optString("profile", "auto"));
            item.importOptions.setBevySkipTopAnimatedTargets(bevyRetarget.optInt("skipTopAnimatedTargets", 1));
            item.importOptions.setBevyRootBone(bevyRetarget.optString("rootBone", "Root"));
            item.importOptions.setBevyScaleBaseBone(bevyRetarget.optString("scaleBaseBone", "Hips"));
            item.importOptions.setBevyRemoveUnimportantTranslationTracks(
                    bevyRetarget.optBoolean("removeUnimportantTranslationTracks", true));
            item.importOptions.setBevyRemoveMotionTranslationTracks(
                    bevyRetarget.optBoolean("removeMotionTranslationTracks", true));
            item.importOptions.setBevyRemoveMotionRotationTracks(
                    bevyRetarget.optBoolean("removeMotionRotationTracks", false));
            item.importOptions.setBevyNormalizeMotionScale(bevyRetarget.optBoolean("normalizeMotionScale", true));
            JSONArray excludeBones = bevyRetarget.optJSONArray("excludeBones");
            List<String> bones = new ArrayList<>();
            if (excludeBones != null) {
                for (int j = 0; j < excludeBones.length(); j++) {
                    bones.add(excludeBones.optString(j, ""));
                }
            }
            item.importOptions.setBevyExcludedBones(bones);
            JSONObject translation = bevyRetarget.optJSONObject("visualTranslation");
            if (translation != null) {
                item.importOptions.setBevyVisualTranslationX((float) translation.optDouble("x", 0.0));
                item.importOptions.setBevyVisualTranslationY((float) translation.optDouble("y", 0.0));
                item.importOptions.setBevyVisualTranslationZ((float) translation.optDouble("z", 0.0));
            }
            JSONObject rotation = bevyRetarget.optJSONObject("visualRotationDegrees");
            if (rotation != null) {
                item.importOptions.setBevyVisualRotationXDegrees((float) rotation.optDouble("x", 0.0));
                item.importOptions.setBevyVisualRotationYDegrees((float) rotation.optDouble("y", 0.0));
                item.importOptions.setBevyVisualRotationZDegrees((float) rotation.optDouble("z", 0.0));
            }
            JSONObject lockedTranslationAxes = bevyRetarget.optJSONObject("lockedTranslationAxes");
            if (lockedTranslationAxes != null) {
                item.importOptions.setBevyLockTranslationX(lockedTranslationAxes.optBoolean("x", false));
                item.importOptions.setBevyLockTranslationY(lockedTranslationAxes.optBoolean("y", false));
                item.importOptions.setBevyLockTranslationZ(lockedTranslationAxes.optBoolean("z", false));
            }
            return;
        }
    }

    private File animationIndexFile() {
        File indexFile = new File(resourcesFolder, "animations/animations-ext.json");
        if (!indexFile.isFile()) {
            indexFile = new File(resourcesFolder, "Animations/animations-ext.json");
        }
        return indexFile;
    }

    private boolean isInsideResources(File file) throws IOException {
        String root = resourcesFolder.getCanonicalPath();
        String path = file.getCanonicalPath();
        return path.equals(root) || path.startsWith(root + File.separator);
    }

    private void captureProcessOutput(Process process, StringBuilder output) {
        try {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = process.getInputStream().read(buffer)) >= 0) {
                synchronized (output) {
                    output.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                    if (output.length() > 12000) {
                        output.delete(0, output.length() - 12000);
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    private String tail(String output) {
        if (output == null || output.isBlank()) {
            return "No process output was captured.";
        }
        return output.length() <= 4000 ? output.trim() : output.substring(output.length() - 4000).trim();
    }

    private void updateSelectedItemRetargetOptions() {
        if (updatingRetargetFields) {
            return;
        }
        BatchAnimationItem item = selectedItem();
        if (item == null) {
            return;
        }
        item.importOptions.setBevySkipTopAnimatedTargets(((Number) spnBevySkipTopAnimatedTargets.getValue()).intValue());
        item.importOptions.setBevyExcludedBonesCsv(txtBevyExcludeBones.getText());
    }

    private void persistSelectedName() {
        BatchAnimationItem item = selectedItem();
        if (item != null) {
            item.animationName = txtName.getText().trim();
            if (item.animationName.isEmpty()) {
                item.animationName = uniqueAnimationName(sanitizeAssetId(stripExtension(item.sourceFile.getName())), item);
                refreshSelectedItem();
            }
        }
    }

    private void stopTableEditing() {
        if (tblFiles != null && tblFiles.isEditing() && tblFiles.getCellEditor() != null) {
            tblFiles.getCellEditor().stopCellEditing();
        }
    }

    private void refreshSelectedItem() {
        BatchAnimationItem item = selectedItem();
        if (item == null) {
            return;
        }
        int row = animationItems.indexOf(item);
        if (row >= 0) {
            filesTableModel.fireTableRowsUpdated(row, row);
        }
        updatingNameField = true;
        try {
            txtName.setText(item.animationName);
        } finally {
            updatingNameField = false;
        }
        updateBatchControls();
    }

    private void updateBatchControls() {
        BatchAnimationItem item = selectedItem();
        boolean hasSelection = item != null;
        btnInspect.setEnabled(hasSelection);
        btnImport.setEnabled(!checkedItems().isEmpty());
        btnRemove.setEnabled(hasSelection);
        btnBevyRetarget.setEnabled(hasSelection);
        txtName.setEnabled(hasSelection);
        spnBevySkipTopAnimatedTargets.setEnabled(hasSelection);
        txtBevyExcludeBones.setEnabled(hasSelection);
        if (!hasSelection && !animationItems.isEmpty()) {
            txtFile.setText(animationItems.size() + " file(s) selected");
        }
    }

    private void updateRetargetFields(BatchAnimationItem item) {
        updatingRetargetFields = true;
        try {
            AnimationImportOptions options = item == null ? new AnimationImportOptions() : item.importOptions;
            spnBevySkipTopAnimatedTargets.setValue(options.getBevySkipTopAnimatedTargets());
            txtBevyExcludeBones.setText(options.getBevyExcludedBonesCsv());
        } finally {
            updatingRetargetFields = false;
        }
    }

    private BatchAnimationItem selectedItem() {
        if (tblFiles == null) {
            return null;
        }
        int row = tblFiles.getSelectedRow();
        if (row < 0 || row >= animationItems.size()) {
            return null;
        }
        return animationItems.get(row);
    }

    private BatchAnimationItem findItem(File file) {
        String path = absolutePath(file);
        for (BatchAnimationItem item : animationItems) {
            if (absolutePath(item.sourceFile).equalsIgnoreCase(path)) {
                return item;
            }
        }
        return null;
    }

    private List<BatchAnimationItem> checkedItems() {
        List<BatchAnimationItem> result = new ArrayList<>();
        for (BatchAnimationItem item : animationItems) {
            if (item.importEnabled) {
                result.add(item);
            }
        }
        return result;
    }

    private String uniqueAnimationName(String baseName, BatchAnimationItem owner) {
        String base = baseName == null || baseName.isBlank() ? "animation" : baseName;
        String candidate = base;
        int index = 2;
        while (animationNameInUse(candidate, owner)) {
            candidate = base + "_" + index++;
        }
        return candidate;
    }

    private boolean animationNameInUse(String name, BatchAnimationItem owner) {
        for (BatchAnimationItem item : animationItems) {
            if (item != owner && item.animationName.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private String absolutePath(File file) {
        return file == null ? "" : file.getAbsolutePath();
    }

    private String formatImportReport(List<BatchImportReport> reports) {
        int succeeded = 0;
        int failed = 0;
        StringBuilder sb = new StringBuilder();
        sb.append("Animation import result report\n\n");
        for (BatchImportReport report : reports) {
            if (report.success) {
                succeeded++;
            } else {
                failed++;
            }
        }
        sb.append("Imported: ").append(succeeded).append("\n");
        sb.append("Failed: ").append(failed).append("\n\n");
        for (BatchImportReport report : reports) {
            sb.append(report.success ? "[OK] " : "[FAILED] ");
            sb.append(report.item.animationName).append(" <- ").append(report.item.sourceFile.getName()).append("\n");
            if (report.success) {
                sb.append("  Saved: ").append(report.savedFile.getAbsolutePath()).append("\n");
                if (report.clipName != null && !report.clipName.isBlank()) {
                    sb.append("  Clip: ").append(report.clipName).append("\n");
                }
            } else {
                sb.append("  Error: ").append(report.message).append("\n");
            }
        }
        return sb.toString();
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

    private static class BatchAnimationItem {
        private boolean importEnabled = true;
        private final File sourceFile;
        private String animationName;
        private String status = "Ready";
        private AnimationImportResult inspectedResult;
        private final AnimationImportOptions importOptions = new AnimationImportOptions();

        private BatchAnimationItem(File sourceFile, String animationName) {
            this.sourceFile = sourceFile;
            this.animationName = animationName;
        }
    }

    private static class BatchImportReport {
        private final BatchAnimationItem item;
        private final boolean success;
        private final File savedFile;
        private final String clipName;
        private final String message;

        private BatchImportReport(BatchAnimationItem item, boolean success, File savedFile, String clipName, String message) {
            this.item = item;
            this.success = success;
            this.savedFile = savedFile;
            this.clipName = clipName;
            this.message = message;
        }

        private static BatchImportReport success(BatchAnimationItem item, AnimationImportResult result) {
            return new BatchImportReport(item, true, result.getAnimationFile(), result.getSelectedClipName(), null);
        }

        private static BatchImportReport failure(BatchAnimationItem item, String message) {
            return new BatchImportReport(item, false, null, null, message);
        }
    }

    private class AnimationFilesTableModel extends AbstractTableModel {
        private final String[] columns = {"Import", "File", "Runtime name", "Status"};

        @Override
        public int getRowCount() {
            return animationItems.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0 || columnIndex == 2;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            BatchAnimationItem item = animationItems.get(rowIndex);
            if (columnIndex == 0) {
                return item.importEnabled;
            }
            if (columnIndex == 1) {
                return item.sourceFile.getName();
            }
            if (columnIndex == 2) {
                return item.animationName;
            }
            return item.status;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            BatchAnimationItem item = animationItems.get(rowIndex);
            if (columnIndex == 0) {
                item.importEnabled = Boolean.TRUE.equals(aValue);
                updateBatchControls();
            } else if (columnIndex == 2) {
                item.animationName = sanitizeAssetId(aValue == null ? "" : aValue.toString());
                if (tblFiles.getSelectedRow() == rowIndex) {
                    updatingNameField = true;
                    try {
                        txtName.setText(item.animationName);
                    } finally {
                        updatingNameField = false;
                    }
                }
            }
            fireTableRowsUpdated(rowIndex, rowIndex);
        }
    }
}
