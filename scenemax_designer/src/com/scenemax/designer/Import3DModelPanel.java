package com.scenemax.designer;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.scenemaxeng.common.types.ResourceSetup;
import com.scenemax.designer.gizmo.GizmoMode;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.Consumer;

/**
 * A document panel for importing 3D models.  Extends DesignerPanel so the
 * user gets a live 3D preview of the candidate model and can orbit, pan,
 * zoom, select, move, rotate and scale it before committing the import.
 *
 * Layout:
 * +---------------------------------------------------+
 * | Gizmo: [Translate][Rotate] Camera: [Orbit] [Pan]  |
 * +-----------+---------------------------------------+
 * | File:     |                                       |
 * | [Select]  |     JME3 3D Viewport                  |
 * | Name:     |     (model preview)                   |
 * | --------- |                                       |
 * | Transform |                                       |
 * | Pos XYZ   |                                       |
 * | Rot XYZ   |                                       |
 * | Scale XYZ |                                       |
 * | --------- |                                       |
 * | Import    |                                       |
 * | Settings  |                                       |
 * | Scale,etc |                                       |
 * | --------- |                                       |
 * | Physics   |                                       |
 * | --------- |                                       |
 * | [Import]  |                                       |
 * | [Cancel]  |                                       |
 * +-----------+---------------------------------------+
 */
public class Import3DModelPanel extends DesignerPanel {

    // Import settings fields
    private JTextField txtFileName;
    private JTextField txtName;
    private JTextField txtImportScale;
    private JTextField txtImportTransX, txtImportTransY, txtImportTransZ;
    private JTextField txtImportRotateY;
    private JTextField txtCalibrateX, txtCalibrateY, txtCalibrateZ;
    private JTextField txtCapsuleRadius, txtCapsuleHeight;
    private JTextField txtStepHeight;

    // Transform properties (live 3D manipulation)
    private JSpinner spnPosX, spnPosY, spnPosZ;
    private JSpinner spnRotX, spnRotY, spnRotZ;
    private JSpinner spnScaleX, spnScaleY, spnScaleZ;
    private JCheckBox chkProportionalScale;
    private double lastScaleX = 1.0, lastScaleY = 1.0, lastScaleZ = 1.0;

    private static final String PENDING_MARKER_FILE = "_import_pending.json";

    private String selectedFile;
    private String selectedFileDestDir;
    private String previewModelAssetPath;
    private String resourcesFolder;
    private boolean modelImported = false;
    private boolean modelPreviewLoaded = false;

    private Consumer<Boolean> onCloseCallback; // true = imported, false = cancelled

    public Import3DModelPanel(String projectPath, File designerFile, String resourcesFolder) {
        super(projectPath, designerFile, false);
        this.resourcesFolder = resourcesFolder;
        buildUI();
        initJME3();
    }

    public void setOnCloseCallback(Consumer<Boolean> callback) {
        this.onCloseCallback = callback;
    }

    @Override
    protected void buildUI() {
        // --- Toolbar ---
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        // Gizmo mode buttons
        toolbar.add(new JLabel("  Gizmo: "));
        ButtonGroup gizmoGroup = new ButtonGroup();
        JToggleButton btnTranslate = new JToggleButton("Translate", true);
        JToggleButton btnRotate = new JToggleButton("Rotate");
        gizmoGroup.add(btnTranslate);
        gizmoGroup.add(btnRotate);
        btnTranslate.addActionListener(e -> {
            if (app != null) app.enqueue(() -> { app.setGizmoMode(GizmoMode.TRANSLATE); return null; });
        });
        btnRotate.addActionListener(e -> {
            if (app != null) app.enqueue(() -> { app.setGizmoMode(GizmoMode.ROTATE); return null; });
        });
        toolbar.add(btnTranslate);
        toolbar.add(btnRotate);

        toolbar.addSeparator();

        // Camera mode buttons
        toolbar.add(new JLabel("  Camera: "));
        ButtonGroup cameraGroup = new ButtonGroup();
        JToggleButton btnOrbit = new JToggleButton("Orbit", true);
        JToggleButton btnPan = new JToggleButton("Pan");
        cameraGroup.add(btnOrbit);
        cameraGroup.add(btnPan);
        btnOrbit.addActionListener(e -> {
            if (app != null) app.enqueue(() -> { app.setCameraMode(DesignerApp.CameraMode.ORBIT); return null; });
        });
        btnPan.addActionListener(e -> {
            if (app != null) app.enqueue(() -> { app.setCameraMode(DesignerApp.CameraMode.PAN); return null; });
        });
        toolbar.add(btnOrbit);
        toolbar.add(btnPan);

        add(toolbar, BorderLayout.NORTH);

        // --- Left panel (import settings + transform properties) ---
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(250, 0));

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // ========== File selection ==========
        form.add(createBoldLabel("Choose 3D Model File:"));
        form.add(Box.createVerticalStrut(4));

        txtFileName = new JTextField();
        txtFileName.setEditable(false);
        txtFileName.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        txtFileName.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(txtFileName);
        form.add(Box.createVerticalStrut(4));

        JButton btnSelectFile = new JButton("Select File...");
        btnSelectFile.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnSelectFile.addActionListener(e -> onSelectFile());
        form.add(btnSelectFile);

        form.add(Box.createVerticalStrut(4));
        txtName = addLabeledTextField(form, "Name:", "", 12);

        form.add(Box.createVerticalStrut(8));
        form.add(createSeparator());
        form.add(Box.createVerticalStrut(8));

        // ========== Transform properties (live 3D) ==========
        form.add(createBoldLabel("Transform (3D Preview):"));
        form.add(Box.createVerticalStrut(4));

        form.add(createSmallLabel("Position:"));
        spnPosX = createSpinner(0.0, -9999.0, 9999.0, 0.1);
        spnPosY = createSpinner(0.0, -9999.0, 9999.0, 0.1);
        spnPosZ = createSpinner(0.0, -9999.0, 9999.0, 0.1);
        addSpinnerRow3(form, "X:", spnPosX, "Y:", spnPosY, "Z:", spnPosZ);
        spnPosX.addChangeListener(e -> applyTransformFromSpinners());
        spnPosY.addChangeListener(e -> applyTransformFromSpinners());
        spnPosZ.addChangeListener(e -> applyTransformFromSpinners());

        form.add(Box.createVerticalStrut(4));
        form.add(createSmallLabel("Rotation (degrees):"));
        spnRotX = createSpinner(0.0, -360.0, 360.0, 1.0);
        spnRotY = createSpinner(0.0, -360.0, 360.0, 1.0);
        spnRotZ = createSpinner(0.0, -360.0, 360.0, 1.0);
        addSpinnerRow3(form, "X:", spnRotX, "Y:", spnRotY, "Z:", spnRotZ);
        spnRotX.addChangeListener(e -> applyTransformFromSpinners());
        spnRotY.addChangeListener(e -> applyTransformFromSpinners());
        spnRotZ.addChangeListener(e -> applyTransformFromSpinners());

        form.add(Box.createVerticalStrut(4));

        // Scale header with proportional checkbox
        JPanel scaleHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        scaleHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        scaleHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        JLabel lblScale = new JLabel("Scale:");
        lblScale.setFont(lblScale.getFont().deriveFont(Font.PLAIN, 11f));
        scaleHeader.add(lblScale);
        chkProportionalScale = new JCheckBox("Proportional", true);
        chkProportionalScale.setFont(chkProportionalScale.getFont().deriveFont(Font.PLAIN, 11f));
        scaleHeader.add(chkProportionalScale);
        form.add(scaleHeader);

        spnScaleX = createSpinner(1.0, 0.01, 999.0, 0.1);
        spnScaleY = createSpinner(1.0, 0.01, 999.0, 0.1);
        spnScaleZ = createSpinner(1.0, 0.01, 999.0, 0.1);
        addSpinnerRow3(form, "X:", spnScaleX, "Y:", spnScaleY, "Z:", spnScaleZ);
        spnScaleX.addChangeListener(e -> onScaleSpinnerChanged(spnScaleX));
        spnScaleY.addChangeListener(e -> onScaleSpinnerChanged(spnScaleY));
        spnScaleZ.addChangeListener(e -> onScaleSpinnerChanged(spnScaleZ));

        form.add(Box.createVerticalStrut(8));
        form.add(createSeparator());
        form.add(Box.createVerticalStrut(8));

        // ========== Import metadata settings ==========
        form.add(createBoldLabel("Import Settings:"));
        form.add(Box.createVerticalStrut(4));
        txtImportScale = addLabeledTextField(form, "Scale:", "1", 8);
        txtImportRotateY = addLabeledTextField(form, "Rotate Y:", "0", 8);
        txtImportTransX = addLabeledTextField(form, "Translate X:", "0", 8);
        txtImportTransY = addLabeledTextField(form, "Translate Y:", "0", 8);
        txtImportTransZ = addLabeledTextField(form, "Translate Z:", "0", 8);

        form.add(Box.createVerticalStrut(8));
        form.add(createSeparator());
        form.add(Box.createVerticalStrut(8));

        // ========== Physics settings ==========
        JLabel physicsLabel = createBoldLabel("Physics Dimensions (meters):");
        physicsLabel.setForeground(new Color(0, 0, 255));
        form.add(physicsLabel);
        form.add(Box.createVerticalStrut(4));
        txtCalibrateX = addLabeledTextField(form, "Calibrate X:", "0", 8);
        txtCalibrateY = addLabeledTextField(form, "Calibrate Y:", "0", 8);
        txtCalibrateZ = addLabeledTextField(form, "Calibrate Z:", "0", 8);
        txtCapsuleRadius = addLabeledTextField(form, "Capsule Radius:", "2", 8);
        txtCapsuleHeight = addLabeledTextField(form, "Capsule Height:", "2", 8);
        txtStepHeight = addLabeledTextField(form, "Step Height:", "0.05", 8);

        form.add(Box.createVerticalStrut(12));
        form.add(createSeparator());
        form.add(Box.createVerticalStrut(8));

        // ========== Buttons ==========
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JButton btnImport = new JButton("Import");
        btnImport.addActionListener(e -> onImport());
        buttonPanel.add(btnImport);

        JButton btnCancel = new JButton("Cancel");
        btnCancel.addActionListener(e -> onCancel());
        buttonPanel.add(btnCancel);

        form.add(buttonPanel);
        form.add(Box.createVerticalGlue());

        JScrollPane scrollPane = new JScrollPane(form);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Import 3D Model"));
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        leftPanel.add(scrollPane, BorderLayout.CENTER);

        // --- Main split: left panel + canvas ---
        canvasContainer = new JPanel(new BorderLayout());
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, canvasContainer);
        mainSplit.setDividerLocation(250);
        mainSplit.setResizeWeight(0.0);
        add(mainSplit, BorderLayout.CENTER);
    }

    // =====================================================================
    //  Overrides for selection / properties / scene-tree callbacks
    //  (the parent's fields are null since we built our own UI)
    // =====================================================================

    /**
     * Called by the DesignerApp callback when the selected entity changes.
     * Populates our transform spinners with the entity's current transform.
     */
    @Override
    public void updatePropertiesPanel(DesignerEntity entity) {
        updatingProperties = true;
        try {
            if (entity == null) {
                clearSpinners();
                return;
            }

            Vector3f pos = entity.getPosition();
            spnPosX.setValue((double) pos.x);
            spnPosY.setValue((double) pos.y);
            spnPosZ.setValue((double) pos.z);

            float[] euler = new float[3];
            entity.getRotation().toAngles(euler);
            spnRotX.setValue(Math.toDegrees(euler[0]));
            spnRotY.setValue(Math.toDegrees(euler[1]));
            spnRotZ.setValue(Math.toDegrees(euler[2]));

            Vector3f scale = entity.getScale();
            spnScaleX.setValue((double) scale.x);
            spnScaleY.setValue((double) scale.y);
            spnScaleZ.setValue((double) scale.z);
            lastScaleX = scale.x;
            lastScaleY = scale.y;
            lastScaleZ = scale.z;
        } finally {
            updatingProperties = false;
        }
    }

    /** No scene tree in the import panel. */
    @Override
    protected void selectEntityInTree(DesignerEntity entity) {
        // no-op
    }

    /** No scene tree in the import panel. */
    @Override
    public void refreshSceneTree() {
        // no-op
    }

    // =====================================================================
    //  Transform spinners → 3D entity
    // =====================================================================

    private void applyTransformFromSpinners() {
        if (updatingProperties || app == null) return;
        DesignerEntity sel = app.getSelectionManager().getSelected();
        if (sel == null) return;

        float px = ((Number) spnPosX.getValue()).floatValue();
        float py = ((Number) spnPosY.getValue()).floatValue();
        float pz = ((Number) spnPosZ.getValue()).floatValue();

        float rx = (float) Math.toRadians(((Number) spnRotX.getValue()).doubleValue());
        float ry = (float) Math.toRadians(((Number) spnRotY.getValue()).doubleValue());
        float rz = (float) Math.toRadians(((Number) spnRotZ.getValue()).doubleValue());

        float sx = ((Number) spnScaleX.getValue()).floatValue();
        float sy = ((Number) spnScaleY.getValue()).floatValue();
        float sz = ((Number) spnScaleZ.getValue()).floatValue();

        app.enqueue(() -> {
            sel.setPosition(new Vector3f(px, py, pz));
            Quaternion q = new Quaternion();
            q.fromAngles(rx, ry, rz);
            sel.setRotation(q);
            sel.setScale(new Vector3f(sx, sy, sz));
            return null;
        });
    }

    private void onScaleSpinnerChanged(JSpinner source) {
        if (updatingProperties) return;

        if (chkProportionalScale.isSelected()) {
            double newVal = ((Number) source.getValue()).doubleValue();
            double oldVal;
            if (source == spnScaleX) oldVal = lastScaleX;
            else if (source == spnScaleY) oldVal = lastScaleY;
            else oldVal = lastScaleZ;

            if (Math.abs(oldVal) > 0.001) {
                double ratio = newVal / oldVal;
                updatingProperties = true;
                if (source != spnScaleX) spnScaleX.setValue(lastScaleX * ratio);
                if (source != spnScaleY) spnScaleY.setValue(lastScaleY * ratio);
                if (source != spnScaleZ) spnScaleZ.setValue(lastScaleZ * ratio);
                updatingProperties = false;
            }
        }

        lastScaleX = ((Number) spnScaleX.getValue()).doubleValue();
        lastScaleY = ((Number) spnScaleY.getValue()).doubleValue();
        lastScaleZ = ((Number) spnScaleZ.getValue()).doubleValue();

        applyTransformFromSpinners();
    }

    private void clearSpinners() {
        spnPosX.setValue(0.0); spnPosY.setValue(0.0); spnPosZ.setValue(0.0);
        spnRotX.setValue(0.0); spnRotY.setValue(0.0); spnRotZ.setValue(0.0);
        spnScaleX.setValue(1.0); spnScaleY.setValue(1.0); spnScaleZ.setValue(1.0);
        lastScaleX = 1.0; lastScaleY = 1.0; lastScaleZ = 1.0;
    }

    // =====================================================================
    //  UI helpers
    // =====================================================================

    private JLabel createBoldLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
        return lbl;
    }

    private JLabel createSmallLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        lbl.setFont(lbl.getFont().deriveFont(Font.PLAIN, 11f));
        return lbl;
    }

    private JSeparator createSeparator() {
        JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
        return sep;
    }

    private JSpinner createSpinner(double value, double min, double max, double step) {
        return new JSpinner(new SpinnerNumberModel(value, min, max, step));
    }

    private JTextField addLabeledTextField(JPanel parent, String label, String defaultValue, int columns) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        row.add(new JLabel(label));
        JTextField field = new JTextField(defaultValue, columns);
        row.add(field);
        parent.add(row);
        return field;
    }

    private void addSpinnerRow3(JPanel parent, String l1, JSpinner s1,
                                String l2, JSpinner s2, String l3, JSpinner s3) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        Dimension spinSize = new Dimension(60, 24);
        s1.setPreferredSize(spinSize);
        s2.setPreferredSize(spinSize);
        s3.setPreferredSize(spinSize);
        row.add(new JLabel(l1));
        row.add(s1);
        row.add(new JLabel(l2));
        row.add(s2);
        row.add(new JLabel(l3));
        row.add(s3);
        parent.add(row);
    }

    // =====================================================================
    //  File Selection
    // =====================================================================

    private void onSelectFile() {
        String userhome = System.getProperty("user.home");
        JFileChooser jfc = new JFileChooser(userhome + "/Downloads");

        jfc.setFileFilter(new FileFilter() {
            public String getDescription() {
                return "3D Model File (*.zip, *.gltf, *.glb)";
            }

            public boolean accept(File f) {
                if (f.isDirectory()) return true;
                String name = f.getName().toLowerCase();
                return name.endsWith(".zip") || name.endsWith(".gltf") || name.endsWith(".glb");
            }
        });

        int returnValue = jfc.showOpenDialog(this);
        if (returnValue != JFileChooser.APPROVE_OPTION) return;

        // If a previous preview was loaded, rollback first
        if (modelPreviewLoaded) {
            rollbackImport();
        }

        File file = jfc.getSelectedFile();
        selectedFile = file.getAbsolutePath();
        txtFileName.setText(selectedFile);

        if (selectedFile.toLowerCase().endsWith(".zip")) {
            txtName.setText(file.getName().replace(".zip", ""));
            String extractedFolder = extractModelZip(selectedFile);
            selectedFile = findGltfFile(extractedFolder);
        } else {
            txtName.setText(file.getName().replace(".gltf", "").replace(".glb", ""));
        }

        if (selectedFile == null) {
            JOptionPane.showMessageDialog(this, "No .gltf or .glb file found in archive",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Temporarily import and show preview
        if (temporaryImport()) {
            loadModelPreview();
        }
    }

    // =====================================================================
    //  Temporary Import (copy files + register in assets mapping)
    // =====================================================================

    private boolean temporaryImport() {
        String name = txtName.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a model name", "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        if (modelNameExists(name, "./resources", "models.json")) {
            JOptionPane.showMessageDialog(this, "Model name: " + name + " already exists as a built-in model",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        boolean isGlb = selectedFile.endsWith(".glb");
        File srcFile = new File(selectedFile);
        File srcDir = srcFile.getParentFile();
        selectedFileDestDir = resourcesFolder + "/Models/" + (isGlb ? srcFile.getName().replace(".glb", "") : srcDir.getName());
        File destDir = new File(selectedFileDestDir);

        boolean nameExistsInExt = modelNameExists(name, resourcesFolder, "models-ext.json");
        if (nameExistsInExt || destDir.exists()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Model \"" + name + "\" already exists. Replace it?",
                    "Model Already Exists", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return false;
            }
            cleanupExistingModelForOverride(name, destDir);
        }

        destDir.mkdir();

        try {
            if (isGlb) {
                FileUtils.copyFileToDirectory(srcFile, destDir);
            } else {
                FileUtils.copyDirectory(srcDir, destDir);
            }

            // Register in models-ext.json with default values
            JSONObject res = getResourcesFolderIndex(resourcesFolder + "/Models/models-ext.json");
            JSONArray models = res.getJSONArray("models");

            JSONObject model = new JSONObject("{\"physics\":{ \"character\":{} } }");
            model.put("name", name);

            String sourceDirName = isGlb ? destDir.getName() : srcDir.getName();
            String modelPath = "Models/" + sourceDirName + "/" + srcFile.getName();
            model.put("path", modelPath);
            model.put("scaleX", 1.0f);
            model.put("scaleY", 1.0f);
            model.put("scaleZ", 1.0f);
            model.put("transX", 0.0f);
            model.put("transY", 0.0f);
            model.put("transZ", 0.0f);
            model.put("rotateY", 0.0f);

            JSONObject character = model.getJSONObject("physics").getJSONObject("character");
            character.put("calibrateX", 0.0f);
            character.put("calibrateY", 0.0f);
            character.put("calibrateZ", 0.0f);
            character.put("capsuleRadius", 2.0f);
            character.put("capsuleHeight", 2.0f);
            character.put("stepHeight", 0.05f);

            models.put(model);
            writeJsonFile(resourcesFolder + "/Models/models-ext.json", res.toString(2));

            // Register in the in-memory assets mapping so the running
            // DesignerApp can resolve the model immediately
            if (app != null && app.getAssetsMapping() != null) {
                ResourceSetup resSetup = new ResourceSetup(name, modelPath,
                        1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f);
                resSetup.capsuleRadius = 2.0f;
                resSetup.capsuleHeight = 2.0f;
                resSetup.stepHeight = 0.05f;
                app.getAssetsMapping().get3DModelsIndex().put(name.toLowerCase(), resSetup);
            }

            writePendingMarker(name, selectedFileDestDir);

            previewModelAssetPath = modelPath;
            modelPreviewLoaded = true;
            return true;

        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to copy model files: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }

        return false;
    }

    private void loadModelPreview() {
        String name = txtName.getText().trim();
        if (app != null && name.length() > 0) {
            app.enqueue(() -> {
                app.addModel(name, false, false, false);
                return null;
            });
        }
    }

    // =====================================================================
    //  Import / Cancel
    // =====================================================================

    private void onImport() {
        if (!modelPreviewLoaded) {
            JOptionPane.showMessageDialog(this, "Please select a 3D model file first",
                    "No Model", JOptionPane.WARNING_MESSAGE);
            return;
        }

        updateModelMetadata();
        deletePendingMarker();

        modelImported = true;
        JOptionPane.showMessageDialog(this, "Model: " + txtName.getText().trim() + " imported successfully",
                "Model Import", JOptionPane.INFORMATION_MESSAGE);

        if (onCloseCallback != null) {
            onCloseCallback.accept(true);
        }
    }

    private void onCancel() {
        if (modelPreviewLoaded && !modelImported) {
            rollbackImport();
        }
        if (onCloseCallback != null) {
            onCloseCallback.accept(false);
        }
    }

    /**
     * Updates models-ext.json with the final values from the form fields.
     */
    private void updateModelMetadata() {
        String name = txtName.getText().trim();
        float scale = getFloatValue(txtImportScale.getText(), 1.0f);
        float transX = getFloatValue(txtImportTransX.getText(), 0.0f);
        float transY = getFloatValue(txtImportTransY.getText(), 0.0f);
        float transZ = getFloatValue(txtImportTransZ.getText(), 0.0f);
        float rotateY = getFloatValue(txtImportRotateY.getText(), 0.0f);
        float calX = getFloatValue(txtCalibrateX.getText(), 0.0f);
        float calY = getFloatValue(txtCalibrateY.getText(), 0.0f);
        float calZ = getFloatValue(txtCalibrateZ.getText(), 0.0f);
        float capsuleRadius = getFloatValue(txtCapsuleRadius.getText(), 2.0f);
        float capsuleHeight = getFloatValue(txtCapsuleHeight.getText(), 2.0f);
        float stepHeight = getFloatValue(txtStepHeight.getText(), 0.05f);

        JSONObject res = getResourcesFolderIndex(resourcesFolder + "/Models/models-ext.json");
        JSONArray models = res.getJSONArray("models");

        for (int i = 0; i < models.length(); i++) {
            JSONObject m = models.getJSONObject(i);
            if (m.getString("name").equalsIgnoreCase(name)) {
                m.put("scaleX", scale);
                m.put("scaleY", scale);
                m.put("scaleZ", scale);
                m.put("transX", transX);
                m.put("transY", transY);
                m.put("transZ", transZ);
                m.put("rotateY", rotateY);

                JSONObject character = m.getJSONObject("physics").getJSONObject("character");
                character.put("calibrateX", calX);
                character.put("calibrateY", calY);
                character.put("calibrateZ", calZ);
                character.put("capsuleRadius", capsuleRadius);
                character.put("capsuleHeight", capsuleHeight);
                character.put("stepHeight", stepHeight);
                break;
            }
        }

        writeJsonFile(resourcesFolder + "/Models/models-ext.json", res.toString(2));

        // Update in-memory assets mapping
        if (app != null && app.getAssetsMapping() != null) {
            ResourceSetup resSetup = app.getAssetsMapping().get3DModelsIndex().get(name.toLowerCase());
            if (resSetup != null) {
                resSetup.scaleX = scale;
                resSetup.scaleY = scale;
                resSetup.scaleZ = scale;
                resSetup.localTranslationX = transX;
                resSetup.localTranslationY = transY;
                resSetup.localTranslationZ = transZ;
                resSetup.rotateY = rotateY;
                resSetup.calibrateX = calX;
                resSetup.calibrateY = calY;
                resSetup.calibrateZ = calZ;
                resSetup.capsuleRadius = capsuleRadius;
                resSetup.capsuleHeight = capsuleHeight;
                resSetup.stepHeight = stepHeight;
            }
        }
    }

    // =====================================================================
    //  Rollback
    // =====================================================================

    private void rollbackImport() {
        String name = txtName.getText().trim();

        removeModelFromList(name);

        if (app != null && app.getAssetsMapping() != null) {
            app.getAssetsMapping().get3DModelsIndex().remove(name.toLowerCase());
        }

        // Unload the preview model from the JME scene and asset cache before
        // deleting files — on Windows the asset manager holds a file lock on
        // the .glb until the cache entry is explicitly evicted.
        if (app != null && previewModelAssetPath != null) {
            String assetPath = previewModelAssetPath;
            try {
                app.enqueue(() -> {
                    app.removePreviewEntities(name, assetPath);
                    return null;
                }).get();
            } catch (Exception e) {
                e.printStackTrace();
            }
            previewModelAssetPath = null;
        }

        if (selectedFileDestDir != null) {
            File modelDir = new File(selectedFileDestDir);
            if (modelDir.exists()) {
                try {
                    FileUtils.deleteDirectory(modelDir);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        deletePendingMarker();
        modelPreviewLoaded = false;
    }

    /**
     * Removes an existing user-imported model so it can be replaced by a
     * fresh import.  Evicts the asset from the JME cache first to release
     * any OS-level file locks (important on Windows), then removes the
     * on-disk directory and the models-ext.json entry.
     */
    private void cleanupExistingModelForOverride(String name, File destDir) {
        // Resolve the cached asset path before removing from the mapping
        String assetPath = null;
        if (app != null && app.getAssetsMapping() != null) {
            ResourceSetup res = app.getAssetsMapping().get3DModelsIndex().get(name.toLowerCase());
            if (res != null) {
                assetPath = res.path;
            }
        }

        // Evict from JME scene + asset cache to release Windows file locks
        if (app != null && assetPath != null) {
            final String finalAssetPath = assetPath;
            try {
                app.enqueue(() -> {
                    app.removePreviewEntities(name, finalAssetPath);
                    return null;
                }).get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        removeModelFromList(name);

        if (app != null && app.getAssetsMapping() != null) {
            app.getAssetsMapping().get3DModelsIndex().remove(name.toLowerCase());
        }

        if (destDir.exists()) {
            try {
                FileUtils.deleteDirectory(destDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Called when the panel's tab is closed.  If the model was not
     * explicitly imported, rolls back the temporary import.
     */
    public void onTabClosed() {
        if (modelPreviewLoaded && !modelImported) {
            rollbackImport();
        }
    }

    public boolean isModelImported() {
        return modelImported;
    }

    // =====================================================================
    //  Helper methods (ported from Import3DModelDialog)
    // =====================================================================

    private void removeModelFromList(String modelName) {
        JSONObject res = getResourcesFolderIndex(resourcesFolder + "/Models/models-ext.json");
        if (res == null) return;
        JSONArray models = res.getJSONArray("models");
        for (int i = 0; i < models.length(); i++) {
            JSONObject m = models.getJSONObject(i);
            if (m.getString("name").equalsIgnoreCase(modelName)) {
                models.remove(i);
                break;
            }
        }
        writeJsonFile(resourcesFolder + "/Models/models-ext.json", res.toString(2));
    }

    // =====================================================================
    //  Pending-import marker (survives crashes)
    // =====================================================================

    private File getPendingMarkerFile() {
        return new File(designerFile.getParentFile(), PENDING_MARKER_FILE);
    }

    private void writePendingMarker(String modelName, String modelDir) {
        try {
            JSONObject marker = new JSONObject();
            marker.put("modelName", modelName);
            marker.put("modelDir", modelDir);
            marker.put("resourcesFolder", resourcesFolder);
            Files.write(getPendingMarkerFile().toPath(),
                    marker.toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void deletePendingMarker() {
        File marker = getPendingMarkerFile();
        if (marker.exists()) {
            marker.delete();
        }
    }

    /**
     * Cleans up leftover files from a previous import session that was
     * interrupted (e.g. app crash or force-close).  Call once at
     * application startup, before any import panel is opened.
     *
     * @param scriptsFolder the project's scripts folder (Util.getScriptsFolder())
     * @param resourcesFolder the project's resources folder (Util.getResourcesFolder())
     */
    public static void cleanupLeftovers(String scriptsFolder, String resourcesFolder) {
        // Delete the temp designer file
        File tmpDir = new File(scriptsFolder + "/tmp");
        File tmpDesignerFile = new File(tmpDir, "_import_preview.smdesign");
        if (tmpDesignerFile.exists()) {
            tmpDesignerFile.delete();
        }

        // Check for pending-import marker
        File markerFile = new File(tmpDir, PENDING_MARKER_FILE);
        if (!markerFile.exists()) return;

        try {
            String content = new String(Files.readAllBytes(markerFile.toPath()), StandardCharsets.UTF_8);
            JSONObject marker = new JSONObject(content);
            String modelName = marker.optString("modelName", "");
            String modelDir = marker.optString("modelDir", "");

            // Remove model entry from models-ext.json
            if (!modelName.isEmpty() && resourcesFolder != null) {
                File extJson = new File(resourcesFolder + "/Models/models-ext.json");
                if (extJson.exists()) {
                    String json = new String(Files.readAllBytes(extJson.toPath()), StandardCharsets.UTF_8);
                    JSONObject res = new JSONObject(json);
                    if (res.has("models")) {
                        JSONArray models = res.getJSONArray("models");
                        for (int i = 0; i < models.length(); i++) {
                            JSONObject m = models.getJSONObject(i);
                            if (m.getString("name").equalsIgnoreCase(modelName)) {
                                models.remove(i);
                                break;
                            }
                        }
                        Files.write(extJson.toPath(),
                                res.toString(2).getBytes(StandardCharsets.UTF_8));
                    }
                }
            }

            // Delete copied model directory
            if (!modelDir.isEmpty()) {
                File dir = new File(modelDir);
                if (dir.exists()) {
                    FileUtils.deleteDirectory(dir);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            markerFile.delete();
        }
    }

    private String findGltfFile(String folder) {
        File f = new File(folder);
        File[] files = f.listFiles();
        if (files == null) return null;
        for (File f2 : files) {
            if (f2.isFile() && (f2.getName().toLowerCase().endsWith(".gltf") || f2.getName().toLowerCase().endsWith(".glb"))) {
                return f2.getAbsolutePath();
            }
        }
        return null;
    }

    private String extractModelZip(String zipFile) {
        File f = new File(zipFile);
        File folder = new File(f.getParentFile().getAbsolutePath() + "/" + f.getName().toLowerCase().replace(".zip", ""));
        if (folder.exists()) {
            try {
                FileUtils.deleteDirectory(folder);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        unzip(zipFile, folder.getAbsolutePath());
        return folder.getAbsolutePath();
    }

    private boolean modelNameExists(String name, String path, String fileName) {
        JSONObject res = getResourcesFolderIndex(path + "/Models/" + fileName);
        if (res == null || !res.has("models")) return false;
        JSONArray models = res.getJSONArray("models");
        for (int i = 0; i < models.length(); i++) {
            JSONObject m = models.getJSONObject(i);
            if (m.getString("name").equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private JSONObject getResourcesFolderIndex(String path) {
        File f = new File(path);
        if (!f.exists()) {
            return new JSONObject("{\"models\":[]}");
        }
        try {
            String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            if (s.isEmpty()) return new JSONObject("{\"models\":[]}");
            return new JSONObject(s);
        } catch (IOException e) {
            return new JSONObject("{\"models\":[]}");
        }
    }

    private void writeJsonFile(String path, String content) {
        try {
            Files.write(new File(path).toPath(), content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private float getFloatValue(String text, float defaultVal) {
        if (text == null) return defaultVal;
        text = text.trim();
        if (text.isEmpty()) return defaultVal;
        try {
            return Float.parseFloat(text);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private static void unzip(String source, String destination) {
        try {
            java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                    new java.io.FileInputStream(source));
            java.util.zip.ZipEntry entry;
            byte[] buffer = new byte[4096];
            while ((entry = zis.getNextEntry()) != null) {
                File outFile = new File(destination, entry.getName());
                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    outFile.getParentFile().mkdirs();
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile);
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                }
                zis.closeEntry();
            }
            zis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
