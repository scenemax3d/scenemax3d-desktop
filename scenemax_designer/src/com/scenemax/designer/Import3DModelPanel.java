package com.scenemax.designer;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.scenemaxeng.common.types.ResourceSetup;
import com.scenemax.designer.gizmo.GizmoMode;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

    private static final String PENDING_MARKER_FILE = "_import_pending.json";
    private static final ImageIcon THUMBNAIL_PLACEHOLDER = createPlaceholderIcon();

    // Import settings fields
    private JTextField txtFileName;
    private JTextField txtName;
    private JTextField txtImportTransX, txtImportTransY, txtImportTransZ;
    private JTextField txtImportRotateY;
    private JTextField txtCalibrateX, txtCalibrateY, txtCalibrateZ;
    private JTextField txtCapsuleRadius, txtCapsuleHeight;
    private JTextField txtStepHeight;
    private JCheckBox chkStatic;

    // Transform properties (live 3D manipulation)
    private JSpinner spnPosX, spnPosY, spnPosZ;
    private JSpinner spnRotX, spnRotY, spnRotZ;
    private JSpinner spnScaleX, spnScaleY, spnScaleZ;
    private JCheckBox chkProportionalScale;
    private double lastScaleX = 1.0, lastScaleY = 1.0, lastScaleZ = 1.0;

    // Sketchfab browser fields
    private JTextField txtSketchfabQuery;
    private JTextField txtSketchfabToken;
    private JComboBox<FilterOption> cmbSketchfabCategory;
    private JComboBox<FilterOption> cmbSketchfabLicense;
    private JComboBox<FilterOption> cmbSketchfabSort;
    private JSpinner spnSketchfabMaxFaces;
    private JCheckBox chkSketchfabLowPoly;
    private JCheckBox chkSketchfabAnimated;
    private JCheckBox chkSketchfabStaffPicked;
    private JButton btnSketchfabSearch;
    private JButton btnSketchfabPrev;
    private JButton btnSketchfabNext;
    private JButton btnSketchfabDownload;
    private JLabel lblSketchfabStatus;
    private JProgressBar progressSketchfabDownload;
    private DefaultListModel<SketchfabModelItem> sketchfabResultsModel;
    private JList<SketchfabModelItem> listSketchfabResults;
    private final Map<String, ImageIcon> thumbnailCache = new ConcurrentHashMap<>();
    private final Set<String> loadingThumbnailUrls = ConcurrentHashMap.newKeySet();
    private String sketchfabPrevCursor;
    private String sketchfabNextCursor;
    private boolean sketchfabSearchRunning = false;
    private boolean sketchfabDownloadRunning = false;

    private String selectedFile;
    private String selectedFileDestDir;
    private String previewModelAssetPath;
    private String resourcesFolder;
    private final String initialSketchfabToken;
    private final Consumer<String> onSketchfabTokenChanged;
    private boolean modelImported = false;
    private boolean modelPreviewLoaded = false;

    private Consumer<Boolean> onCloseCallback; // true = imported, false = cancelled

    public Import3DModelPanel(String projectPath, File designerFile, String resourcesFolder,
                              String initialSketchfabToken, Consumer<String> onSketchfabTokenChanged) {
        super(projectPath, designerFile, false);
        this.resourcesFolder = resourcesFolder;
        this.initialSketchfabToken = initialSketchfabToken == null ? "" : initialSketchfabToken;
        this.onSketchfabTokenChanged = onSketchfabTokenChanged;
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
        leftPanel.setPreferredSize(new Dimension(430, 0));

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // ========== Sketchfab browser ==========
        form.add(createBoldLabel("Browse Free Sketchfab Models:"));
        form.add(Box.createVerticalStrut(4));

        JPanel sketchfabTokenRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        sketchfabTokenRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        sketchfabTokenRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        sketchfabTokenRow.add(new JLabel("Token:"));
        txtSketchfabToken = new JTextField(loadSketchfabToken(), 19);
        sketchfabTokenRow.add(txtSketchfabToken);
        JButton btnTokenHelp = new JButton("Help");
        btnTokenHelp.addActionListener(e -> openSketchfabTokenHelp());
        sketchfabTokenRow.add(btnTokenHelp);
        form.add(sketchfabTokenRow);

        JPanel sketchfabSearchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        sketchfabSearchRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        sketchfabSearchRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        sketchfabSearchRow.add(new JLabel("Search:"));
        txtSketchfabQuery = new JTextField(20);
        txtSketchfabQuery.addActionListener(e -> runSketchfabSearch(null));
        sketchfabSearchRow.add(txtSketchfabQuery);
        btnSketchfabSearch = new JButton("Search");
        btnSketchfabSearch.addActionListener(e -> runSketchfabSearch(null));
        sketchfabSearchRow.add(btnSketchfabSearch);
        form.add(sketchfabSearchRow);

        JPanel sketchfabFilterRow1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        sketchfabFilterRow1.setAlignmentX(Component.LEFT_ALIGNMENT);
        sketchfabFilterRow1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        sketchfabFilterRow1.add(new JLabel("Category:"));
        cmbSketchfabCategory = new JComboBox<>(toFilterOptions(SketchfabService.CATEGORIES));
        cmbSketchfabCategory.setPreferredSize(new Dimension(155, 24));
        sketchfabFilterRow1.add(cmbSketchfabCategory);
        sketchfabFilterRow1.add(new JLabel("License:"));
        cmbSketchfabLicense = new JComboBox<>(toFilterOptions(SketchfabService.LICENSES));
        cmbSketchfabLicense.setPreferredSize(new Dimension(135, 24));
        sketchfabFilterRow1.add(cmbSketchfabLicense);
        form.add(sketchfabFilterRow1);

        JPanel sketchfabFilterRow2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        sketchfabFilterRow2.setAlignmentX(Component.LEFT_ALIGNMENT);
        sketchfabFilterRow2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        sketchfabFilterRow2.add(new JLabel("Sort:"));
        cmbSketchfabSort = new JComboBox<>(toFilterOptions(SketchfabService.SORT_OPTIONS));
        cmbSketchfabSort.setPreferredSize(new Dimension(135, 24));
        sketchfabFilterRow2.add(cmbSketchfabSort);
        sketchfabFilterRow2.add(new JLabel("Max Faces:"));
        spnSketchfabMaxFaces = createSpinner(0.0, 0.0, 10000000.0, 1000.0);
        spnSketchfabMaxFaces.setPreferredSize(new Dimension(95, 24));
        sketchfabFilterRow2.add(spnSketchfabMaxFaces);
        chkSketchfabLowPoly = new JCheckBox("Low Poly");
        chkSketchfabLowPoly.addActionListener(e -> {
            if (chkSketchfabLowPoly.isSelected() && ((Number) spnSketchfabMaxFaces.getValue()).intValue() == 0) {
                spnSketchfabMaxFaces.setValue((double) SketchfabService.DEFAULT_LOW_POLY_FACE_COUNT);
            }
        });
        sketchfabFilterRow2.add(chkSketchfabLowPoly);
        form.add(sketchfabFilterRow2);

        JPanel sketchfabFilterRow3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        sketchfabFilterRow3.setAlignmentX(Component.LEFT_ALIGNMENT);
        sketchfabFilterRow3.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        chkSketchfabAnimated = new JCheckBox("Animated");
        chkSketchfabStaffPicked = new JCheckBox("Staff Picks");
        sketchfabFilterRow3.add(chkSketchfabAnimated);
        sketchfabFilterRow3.add(chkSketchfabStaffPicked);
        form.add(sketchfabFilterRow3);

        sketchfabResultsModel = new DefaultListModel<>();
        listSketchfabResults = new JList<>(sketchfabResultsModel);
        listSketchfabResults.setVisibleRowCount(6);
        listSketchfabResults.setFixedCellHeight(108);
        listSketchfabResults.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listSketchfabResults.setCellRenderer(new SketchfabResultRenderer());
        listSketchfabResults.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                syncSketchfabSelectionToForm();
            }
        });
        JScrollPane sketchfabResultsScroll = new JScrollPane(listSketchfabResults);
        sketchfabResultsScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        sketchfabResultsScroll.setPreferredSize(new Dimension(380, 400));
        sketchfabResultsScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 400));
        form.add(sketchfabResultsScroll);

        JPanel sketchfabPagingRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        sketchfabPagingRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        sketchfabPagingRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        btnSketchfabPrev = new JButton("Previous");
        btnSketchfabPrev.addActionListener(e -> runSketchfabSearch(sketchfabPrevCursor));
        btnSketchfabPrev.setEnabled(false);
        sketchfabPagingRow.add(btnSketchfabPrev);
        btnSketchfabNext = new JButton("Next");
        btnSketchfabNext.addActionListener(e -> runSketchfabSearch(sketchfabNextCursor));
        btnSketchfabNext.setEnabled(false);
        sketchfabPagingRow.add(btnSketchfabNext);
        btnSketchfabDownload = new JButton("Download To Import");
        btnSketchfabDownload.addActionListener(e -> downloadSelectedSketchfabModel());
        btnSketchfabDownload.setEnabled(false);
        sketchfabPagingRow.add(btnSketchfabDownload);
        form.add(sketchfabPagingRow);

        lblSketchfabStatus = new JLabel("Search Sketchfab to browse downloadable models.");
        lblSketchfabStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(lblSketchfabStatus);

        progressSketchfabDownload = new JProgressBar(0, 100);
        progressSketchfabDownload.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressSketchfabDownload.setStringPainted(true);
        progressSketchfabDownload.setVisible(false);
        progressSketchfabDownload.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        form.add(progressSketchfabDownload);

        form.add(Box.createVerticalStrut(8));
        form.add(createSeparator());
        form.add(Box.createVerticalStrut(8));

        // ========== File selection ==========
        form.add(createBoldLabel("Or Choose A Local 3D Model File:"));
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
        txtImportRotateY = addLabeledTextField(form, "Rotate Y:", "0", 8);
        txtImportTransX = addLabeledTextField(form, "Translate X:", "0", 8);
        txtImportTransY = addLabeledTextField(form, "Translate Y:", "0", 8);
        txtImportTransZ = addLabeledTextField(form, "Translate Z:", "0", 8);

        form.add(Box.createVerticalStrut(4));
        chkStatic = new JCheckBox("Static");
        chkStatic.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(chkStatic);

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
        mainSplit.setDividerLocation(430);
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
        Dimension spinSize = new Dimension(80, 24);
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

    private FilterOption[] toFilterOptions(String[][] source) {
        FilterOption[] options = new FilterOption[source.length];
        for (int i = 0; i < source.length; i++) {
            options[i] = new FilterOption(source[i][0], source[i][1]);
        }
        return options;
    }

    private String loadSketchfabToken() {
        return initialSketchfabToken == null ? "" : initialSketchfabToken;
    }

    private void saveSketchfabToken(String token) {
        if (onSketchfabTokenChanged != null) {
            onSketchfabTokenChanged.accept(token == null ? "" : token.trim());
        }
    }

    private void openSketchfabTokenHelp() {
        try {
            Desktop.getDesktop().browse(new URI("https://sketchfab.com/settings/password"));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Unable to open Sketchfab settings in your browser.\nVisit https://sketchfab.com/settings/password",
                    "Sketchfab Token Help", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void syncSketchfabSelectionToForm() {
        SketchfabModelItem selected = listSketchfabResults.getSelectedValue();
        btnSketchfabDownload.setEnabled(selected != null && !sketchfabDownloadRunning);
        if (selected == null) {
            return;
        }

        if (txtName.getText().trim().isEmpty() || txtName.getText().trim().startsWith("sketchfab-")) {
            txtName.setText(selected.defaultImportName);
        }
        lblSketchfabStatus.setText("Selected: " + selected.title + " by " + selected.author + " | " + selected.license);
    }

    private void runSketchfabSearch(String cursor) {
        if (sketchfabSearchRunning || sketchfabDownloadRunning) return;

        saveSketchfabToken(txtSketchfabToken.getText());
        sketchfabSearchRunning = true;
        setSketchfabControlsEnabled(false);
        lblSketchfabStatus.setText(cursor == null ? "Searching Sketchfab..." : "Loading more Sketchfab results...");

        final String query = txtSketchfabQuery.getText().trim();
        final FilterOption category = (FilterOption) cmbSketchfabCategory.getSelectedItem();
        final FilterOption license = (FilterOption) cmbSketchfabLicense.getSelectedItem();
        final FilterOption sort = (FilterOption) cmbSketchfabSort.getSelectedItem();
        int maxFaces = ((Number) spnSketchfabMaxFaces.getValue()).intValue();
        if (chkSketchfabLowPoly.isSelected() && maxFaces == 0) {
            maxFaces = SketchfabService.DEFAULT_LOW_POLY_FACE_COUNT;
        }
        final int finalMaxFaces = maxFaces;
        final boolean animatedOnly = chkSketchfabAnimated.isSelected();
        final boolean staffPicked = chkSketchfabStaffPicked.isSelected();

        SwingWorker<JSONObject, Void> worker = new SwingWorker<JSONObject, Void>() {
            @Override
            protected JSONObject doInBackground() throws Exception {
                return SketchfabService.search(
                        query,
                        category == null ? "" : category.value,
                        license == null ? "" : license.value,
                        sort == null ? "" : sort.value,
                        finalMaxFaces,
                        animatedOnly,
                        staffPicked,
                        cursor
                );
            }

            @Override
            protected void done() {
                sketchfabSearchRunning = false;
                try {
                    JSONObject response = get();
                    populateSketchfabResults(response);
                } catch (Exception e) {
                    sketchfabResultsModel.clear();
                    listSketchfabResults.clearSelection();
                    sketchfabPrevCursor = null;
                    sketchfabNextCursor = null;
                    lblSketchfabStatus.setText("Sketchfab search failed.");
                    JOptionPane.showMessageDialog(Import3DModelPanel.this,
                            "Sketchfab search failed:\n" + rootMessage(e),
                            "Sketchfab Search", JOptionPane.ERROR_MESSAGE);
                } finally {
                    setSketchfabControlsEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void populateSketchfabResults(JSONObject response) {
        sketchfabResultsModel.clear();
        JSONArray results = response.optJSONArray("results");
        if (results != null) {
            for (int i = 0; i < results.length(); i++) {
                JSONObject model = results.optJSONObject(i);
                if (model != null) {
                    sketchfabResultsModel.addElement(new SketchfabModelItem(model));
                }
            }
        }

        sketchfabPrevCursor = SketchfabService.extractCursor(response.optString("previous", null));
        sketchfabNextCursor = SketchfabService.extractCursor(response.optString("next", null));
        btnSketchfabPrev.setEnabled(sketchfabPrevCursor != null);
        btnSketchfabNext.setEnabled(sketchfabNextCursor != null);

        if (sketchfabResultsModel.isEmpty()) {
            lblSketchfabStatus.setText("No downloadable Sketchfab models matched the current filters.");
            listSketchfabResults.clearSelection();
            btnSketchfabDownload.setEnabled(false);
            return;
        }

        lblSketchfabStatus.setText("Found " + sketchfabResultsModel.size() + " Sketchfab models on this page.");
        listSketchfabResults.setSelectedIndex(0);
    }

    private void setSketchfabControlsEnabled(boolean enabled) {
        btnSketchfabSearch.setEnabled(enabled && !sketchfabDownloadRunning);
        btnSketchfabPrev.setEnabled(enabled && sketchfabPrevCursor != null && !sketchfabDownloadRunning);
        btnSketchfabNext.setEnabled(enabled && sketchfabNextCursor != null && !sketchfabDownloadRunning);
        btnSketchfabDownload.setEnabled(enabled && listSketchfabResults.getSelectedValue() != null && !sketchfabDownloadRunning);
    }

    private void downloadSelectedSketchfabModel() {
        SketchfabModelItem selected = listSketchfabResults.getSelectedValue();
        if (selected == null || sketchfabDownloadRunning) return;

        String token = txtSketchfabToken.getText().trim();
        if (token.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Enter a Sketchfab token first. The official Sketchfab download API requires authentication.",
                    "Sketchfab Download", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (modelPreviewLoaded) {
            rollbackImport();
        }

        saveSketchfabToken(token);
        sketchfabDownloadRunning = true;
        setSketchfabControlsEnabled(false);
        progressSketchfabDownload.setVisible(true);
        progressSketchfabDownload.setValue(0);
        progressSketchfabDownload.setString("Preparing download...");
        lblSketchfabStatus.setText("Downloading " + selected.title + " from Sketchfab...");

        SwingWorker<DownloadedModelSource, Integer> worker = new SwingWorker<DownloadedModelSource, Integer>() {
            @Override
            protected DownloadedModelSource doInBackground() throws Exception {
                JSONObject downloadInfo = SketchfabService.getDownloadInfo(selected.uid, token);
                JSONObject preferredFormat = SketchfabService.getPreferredDownloadFormat(downloadInfo);
                if (preferredFormat == null) {
                    throw new IOException("Sketchfab did not provide a GLB or glTF download for this model.");
                }

                String downloadUrl = preferredFormat.optString("url", "");
                if (downloadUrl.isEmpty()) {
                    throw new IOException("Sketchfab returned an empty download URL.");
                }

                long size = preferredFormat.optLong("size", 0L);
                String extension = ".zip";
                if (downloadInfo.has("glb") && preferredFormat == downloadInfo.optJSONObject("glb")) {
                    extension = ".glb";
                } else if (downloadUrl.toLowerCase().contains(".glb")) {
                    extension = ".glb";
                }

                File tempRoot = createSketchfabTempFolder(selected.defaultImportName);
                File downloadFile = new File(tempRoot, "download" + extension);
                SketchfabService.downloadFile(downloadUrl, downloadFile, size, pct -> publish(pct));
                return new DownloadedModelSource(downloadFile, tempRoot, selected);
            }

            @Override
            protected void process(java.util.List<Integer> chunks) {
                if (chunks.isEmpty()) return;
                int value = chunks.get(chunks.size() - 1);
                progressSketchfabDownload.setValue(value);
                progressSketchfabDownload.setString("Downloading... " + value + "%");
            }

            @Override
            protected void done() {
                try {
                    DownloadedModelSource source = get();
                    progressSketchfabDownload.setValue(100);
                    progressSketchfabDownload.setString("Preparing preview...");
                    boolean loaded = loadSelectedModelSource(
                            source.downloadedFile,
                            "Sketchfab: " + source.item.title + " by " + source.item.author,
                            source.item.defaultImportName,
                            source.tempRoot
                    );
                    if (loaded) {
                        lblSketchfabStatus.setText("Sketchfab model ready in the import document.");
                    } else {
                        FileUtils.deleteQuietly(source.tempRoot);
                    }
                } catch (Exception e) {
                    progressSketchfabDownload.setVisible(false);
                    progressSketchfabDownload.setValue(0);
                    lblSketchfabStatus.setText("Sketchfab download failed.");
                    JOptionPane.showMessageDialog(Import3DModelPanel.this,
                            "Sketchfab download failed:\n" + rootMessage(e),
                            "Sketchfab Download", JOptionPane.ERROR_MESSAGE);
                } finally {
                    sketchfabDownloadRunning = false;
                    progressSketchfabDownload.setVisible(false);
                    progressSketchfabDownload.setValue(0);
                    setSketchfabControlsEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private File createSketchfabTempFolder(String modelName) throws IOException {
        File baseDir = new File(designerFile.getParentFile(), "sketchfab");
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        return Files.createTempDirectory(baseDir.toPath(), sanitizeForFileName(modelName) + "-").toFile();
    }

    private boolean loadSelectedModelSource(File sourceFile, String displayLabel, String defaultName, File tempRootToDelete) {
        selectedFile = sourceFile.getAbsolutePath();
        txtFileName.setText(displayLabel == null ? selectedFile : displayLabel);
        txtName.setText(defaultName == null ? stripModelExtension(sourceFile.getName()) : defaultName);

        if (selectedFile.toLowerCase().endsWith(".zip")) {
            String extractedFolder = extractModelZip(selectedFile);
            selectedFile = findGltfFile(extractedFolder);
        }

        if (selectedFile == null) {
            JOptionPane.showMessageDialog(this, "No .gltf or .glb file found in archive",
                    "Error", JOptionPane.ERROR_MESSAGE);
            if (tempRootToDelete != null) {
                FileUtils.deleteQuietly(tempRootToDelete);
            }
            return false;
        }

        boolean imported = temporaryImport();
        if (imported) {
            loadModelPreview();
        }
        if (tempRootToDelete != null) {
            FileUtils.deleteQuietly(tempRootToDelete);
        }
        return imported;
    }

    private String stripModelExtension(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".gltf")) return fileName.substring(0, fileName.length() - 5);
        if (lower.endsWith(".glb")) return fileName.substring(0, fileName.length() - 4);
        if (lower.endsWith(".zip")) return fileName.substring(0, fileName.length() - 4);
        return fileName;
    }

    private static String sanitizeForFileName(String text) {
        if (text == null || text.trim().isEmpty()) return "sketchfab-model";
        return text.trim().replaceAll("[^a-zA-Z0-9._-]+", "_");
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.toString() : current.getMessage();
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

        File file = jfc.getSelectedFile();
        if (modelPreviewLoaded) {
            rollbackImport();
        }
        loadSelectedModelSource(file, file.getAbsolutePath(), stripModelExtension(file.getName()), null);
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

        boolean isGlb = selectedFile.toLowerCase().endsWith(".glb");
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

        destDir.mkdirs();

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
            model.put("isStatic", false);

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
        float scaleX = ((Number) spnScaleX.getValue()).floatValue();
        float scaleY = ((Number) spnScaleY.getValue()).floatValue();
        float scaleZ = ((Number) spnScaleZ.getValue()).floatValue();
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
        boolean isStatic = chkStatic.isSelected();

        JSONObject res = getResourcesFolderIndex(resourcesFolder + "/Models/models-ext.json");
        JSONArray models = res.getJSONArray("models");

        for (int i = 0; i < models.length(); i++) {
            JSONObject m = models.getJSONObject(i);
            if (m.getString("name").equalsIgnoreCase(name)) {
                m.put("scaleX", scaleX);
                m.put("scaleY", scaleY);
                m.put("scaleZ", scaleZ);
                m.put("transX", transX);
                m.put("transY", transY);
                m.put("transZ", transZ);
                m.put("rotateY", rotateY);
                m.put("isStatic", isStatic);

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
                resSetup.scaleX = scaleX;
                resSetup.scaleY = scaleY;
                resSetup.scaleZ = scaleZ;
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

    private static ImageIcon createPlaceholderIcon() {
        BufferedImage img = new BufferedImage(96, 72, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(new Color(238, 238, 238));
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.setColor(new Color(180, 180, 180));
            g.drawRect(0, 0, img.getWidth() - 1, img.getHeight() - 1);
            g.setColor(new Color(120, 120, 120));
            g.drawString("No preview", 18, 38);
        } finally {
            g.dispose();
        }
        return new ImageIcon(img);
    }

    private ImageIcon getThumbnailIcon(SketchfabModelItem item) {
        if (item.thumbnailUrl == null || item.thumbnailUrl.isEmpty()) {
            return THUMBNAIL_PLACEHOLDER;
        }

        ImageIcon cached = thumbnailCache.get(item.thumbnailUrl);
        if (cached != null) {
            return cached;
        }

        if (loadingThumbnailUrls.add(item.thumbnailUrl)) {
            SwingWorker<ImageIcon, Void> worker = new SwingWorker<ImageIcon, Void>() {
                @Override
                protected ImageIcon doInBackground() {
                    try (BufferedInputStream in = new BufferedInputStream(new URL(item.thumbnailUrl).openStream())) {
                        BufferedImage image = ImageIO.read(in);
                        if (image == null) {
                            return THUMBNAIL_PLACEHOLDER;
                        }
                        Image scaled = image.getScaledInstance(96, 72, Image.SCALE_SMOOTH);
                        return new ImageIcon(scaled);
                    } catch (Exception e) {
                        return THUMBNAIL_PLACEHOLDER;
                    }
                }

                @Override
                protected void done() {
                    try {
                        thumbnailCache.put(item.thumbnailUrl, get());
                    } catch (Exception ignored) {
                        thumbnailCache.put(item.thumbnailUrl, THUMBNAIL_PLACEHOLDER);
                    } finally {
                        loadingThumbnailUrls.remove(item.thumbnailUrl);
                        listSketchfabResults.repaint();
                    }
                }
            };
            worker.execute();
        }

        return THUMBNAIL_PLACEHOLDER;
    }

    private static class FilterOption {
        private final String label;
        private final String value;

        private FilterOption(String label, String value) {
            this.label = label;
            this.value = value;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static class DownloadedModelSource {
        private final File downloadedFile;
        private final File tempRoot;
        private final SketchfabModelItem item;

        private DownloadedModelSource(File downloadedFile, File tempRoot, SketchfabModelItem item) {
            this.downloadedFile = downloadedFile;
            this.tempRoot = tempRoot;
            this.item = item;
        }
    }

    private static class SketchfabModelItem {
        private final String uid;
        private final String title;
        private final String description;
        private final String author;
        private final String license;
        private final String thumbnailUrl;
        private final int faceCount;
        private final boolean animated;
        private final String defaultImportName;

        private SketchfabModelItem(JSONObject model) {
            uid = model.optString("uid", "");
            title = model.optString("name", "Untitled");
            description = model.optString("description", "");
            JSONObject user = model.optJSONObject("user");
            author = user == null ? "Unknown author" : user.optString("displayName", "Unknown author");
            JSONObject licenseObj = model.optJSONObject("license");
            license = licenseObj == null ? "License unknown" : licenseObj.optString("label", licenseObj.optString("slug", "License unknown"));
            thumbnailUrl = SketchfabService.getThumbnailUrl(model, 256);
            faceCount = model.optInt("faceCount", 0);
            animated = model.optBoolean("animated", false);
            defaultImportName = sanitizeForFileName(title).toLowerCase();
        }

        private String shortDescription() {
            String clean = description == null ? "" : description.replaceAll("\\s+", " ").trim();
            if (clean.isEmpty()) {
                return "No description provided.";
            }
            if (clean.length() > 120) {
                return clean.substring(0, 117) + "...";
            }
            return clean;
        }
    }

    private class SketchfabResultRenderer extends JPanel implements ListCellRenderer<SketchfabModelItem> {
        private final JLabel lblThumb = new JLabel();
        private final JLabel lblText = new JLabel();

        private SketchfabResultRenderer() {
            setLayout(new BorderLayout(8, 4));
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
            lblThumb.setPreferredSize(new Dimension(96, 72));
            add(lblThumb, BorderLayout.WEST);
            lblText.setVerticalAlignment(SwingConstants.TOP);
            add(lblText, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends SketchfabModelItem> list,
                                                      SketchfabModelItem value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            lblThumb.setIcon(getThumbnailIcon(value));
            String faces = value.faceCount > 0 ? SketchfabService.formatFaceCount(value.faceCount) + " faces" : "face count unknown";
            String animatedText = value.animated ? " | animated" : "";
            lblText.setText("<html><b>" + escapeHtml(value.title) + "</b><br/>"
                    + escapeHtml(value.author) + " | " + escapeHtml(value.license) + "<br/>"
                    + escapeHtml(faces + animatedText) + "<br/>"
                    + escapeHtml(value.shortDescription()) + "</html>");
            setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            lblThumb.setBackground(getBackground());
            lblText.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            return this;
        }
    }

    private static String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
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
            if (f2.isDirectory()) {
                String nested = findGltfFile(f2.getAbsolutePath());
                if (nested != null) {
                    return nested;
                }
            } else if (f2.getName().toLowerCase().endsWith(".gltf") || f2.getName().toLowerCase().endsWith(".glb")) {
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
