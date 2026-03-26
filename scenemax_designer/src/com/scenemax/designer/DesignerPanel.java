package com.scenemax.designer;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeCanvasContext;
import com.scenemax.designer.gizmo.GizmoMode;
import com.scenemax.designer.selection.SelectionManager;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Swing panel that embeds the JME3 3D viewport and tool panels.
 * This is hosted inside the EditorTabPanel as a tab.
 *
 * Layout:
 * +-------------------------------------------+
 * | Toolbar: [Sphere] [Box] [Model] | mode    |
 * +----------+----------------------------+
 * | Scene    |                            |
 * | Tree     |     JME3 3D Viewport       |
 * |          |     (AWT Canvas)           |
 * |----------|                            |
 * | Props    |                            |
 * | Panel    |                            |
 * +----------+----------------------------+
 */
public class DesignerPanel extends JPanel {

    // --- Shared JME3 canvas (singleton across all designer documents) ---
    private static DesignerApp sharedApp;
    private static Canvas sharedCanvas;
    private static DesignerPanel activeDesignerPanel;

    protected DesignerApp app;
    protected Canvas canvas;
    protected String projectPath;
    protected File designerFile;
    protected JPanel canvasContainer;

    // Per-panel saved orbit camera state (saved on deactivate, restored on activate)
    private float savedCamDistance = 15f;
    private float savedCamYaw = (float) Math.toRadians(45);
    private float savedCamPitch = (float) Math.toRadians(30);
    private Vector3f savedCamTarget = new Vector3f(0, 0, 0);
    private boolean hasSavedCameraState = false;

    // Left panel components
    private JTree sceneTree;
    private DefaultTreeModel sceneTreeModel;
    private DefaultMutableTreeNode sceneTreeRoot;

    // Properties panel
    private JTextField txtName;
    private JSpinner spnPosX, spnPosY, spnPosZ;
    private JSpinner spnRotX, spnRotY, spnRotZ;
    private JSpinner spnScaleX, spnScaleY, spnScaleZ;
    private JCheckBox chkProportionalScale;
    private double lastScaleX = 1.0, lastScaleY = 1.0, lastScaleZ = 1.0;
    private JSpinner spnSizeX, spnSizeY, spnSizeZ;
    private JPanel sizeFieldsPanel;
    private JSpinner spnRadius;
    private JPanel radiusPanel;
    private JCheckBox chkStaticEntity, chkColliderEntity;
    private JCheckBox chkHidden;
    private JPanel hiddenPanel;
    private JComboBox<String> cboShadowMode;
    private JPanel shadowModePanel;
    private JCheckBox chkJointMapping;
    private JButton btnEditJointMapping;
    private JPanel jointMappingPanel;
    private JPanel staticColliderPanel;
    private JComboBox<String> cboMaterial;
    private JPanel materialPanel;
    private JLabel lblType;
    private JPanel propertiesForm;

    // Loading progress overlay
    private JPanel loadingOverlay;
    private JProgressBar loadingProgressBar;
    private JLabel loadingLabel;

    // Floating code editor panel (for CODE nodes)
    private JPanel codeEditorOverlay;
    private JTextArea codeEditorArea;
    private DesignerEntity editingCodeEntity;

    // Toolbar buttons
    private JToggleButton btnTranslate, btnRotate;

    protected boolean updatingProperties = false;
    private boolean updatingTreeSelection = false;
    private Runnable scriptsTreeRefreshCallback;
    private java.util.function.Consumer<String> codeFileUpdatedCallback;

    public DesignerPanel(String projectPath, File designerFile) {
        this(projectPath, designerFile, true);
    }

    /**
     * Constructor for subclasses that need to build their own UI before
     * initializing JME3.  Pass {@code autoInit=false} and call
     * {@link #initJME3()} manually after your UI is ready.
     */
    protected DesignerPanel(String projectPath, File designerFile, boolean autoInit) {
        super(new BorderLayout());
        this.projectPath = projectPath;
        this.designerFile = designerFile;

        if (autoInit) {
            buildUI();
            initJME3();
        }
    }

    protected void buildUI() {
        // --- Toolbar ---
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        JButton btnAddSphere = new JButton(createDesignerToolbarIcon("sphere"));
        btnAddSphere.setToolTipText("Add Sphere");
        btnAddSphere.addActionListener(e -> {
            if (app != null) app.enqueue(() -> { app.addDefaultSphere(); return null; });
        });

        JButton btnAddBox = new JButton(createDesignerToolbarIcon("box"));
        btnAddBox.setToolTipText("Add Box");
        btnAddBox.addActionListener(e -> {
            if (app != null) app.enqueue(() -> { app.addDefaultBox(); return null; });
        });

        JButton btnAddModel = new JButton(createDesignerToolbarIcon("model"));
        btnAddModel.setToolTipText("Add 3D Model");
        btnAddModel.addActionListener(e -> showModelPickerDialog());

        JButton btnDelete = new JButton(createDesignerToolbarIcon("delete"));
        btnDelete.setToolTipText("Delete Selected");
        btnDelete.addActionListener(e -> {
            if (app != null) {
                app.enqueue(() -> {
                    DesignerEntity sel = app.getSelectionManager().getSelected();
                    if (sel != null) app.removeEntity(sel);
                    return null;
                });
            }
        });

        toolbar.add(new JLabel("  Add: "));
        toolbar.add(btnAddSphere);
        toolbar.add(btnAddBox);
        toolbar.add(btnAddModel);
        toolbar.addSeparator();
        toolbar.add(btnDelete);
        toolbar.addSeparator();

        toolbar.add(new JLabel("  Gizmo: "));
        ButtonGroup gizmoGroup = new ButtonGroup();
        btnTranslate = new JToggleButton(createDesignerToolbarIcon("translate"), true);
        btnRotate = new JToggleButton(createDesignerToolbarIcon("rotate"));
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
        toolbar.add(new JLabel("  Camera: "));
        ButtonGroup cameraGroup = new ButtonGroup();
        JToggleButton btnOrbit = new JToggleButton(createDesignerToolbarIcon("orbit"), true);
        JToggleButton btnPan = new JToggleButton(createDesignerToolbarIcon("pan"));
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

        // --- Left Panel (Scene Tree + Properties) ---
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(220, 0));

        // Scene tree
        sceneTreeRoot = new DefaultMutableTreeNode("Scene");
        sceneTreeModel = new DefaultTreeModel(sceneTreeRoot);
        sceneTree = new JTree(sceneTreeModel);
        sceneTree.setRootVisible(true);
        sceneTree.setCellRenderer(new SceneTreeCellRenderer());
        sceneTree.addTreeSelectionListener(this::onTreeSelectionChanged);
        sceneTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showTreeContextMenu(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showTreeContextMenu(e);
            }
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    onTreeDoubleClick(e);
                }
            }
        });

        // Enable drag-and-drop reordering of scene entities
        sceneTree.setDragEnabled(true);
        sceneTree.setDropMode(DropMode.ON_OR_INSERT);
        sceneTree.setTransferHandler(new SceneTreeTransferHandler());

        JScrollPane treeScroll = new JScrollPane(sceneTree);
        treeScroll.setBorder(BorderFactory.createTitledBorder("Scene Hierarchy"));

        // Properties panel
        propertiesForm = new JPanel();
        propertiesForm.setLayout(new BoxLayout(propertiesForm, BoxLayout.Y_AXIS));
        propertiesForm.setBorder(BorderFactory.createTitledBorder("Properties"));
        buildPropertiesForm();

        JScrollPane propsScroll = new JScrollPane(propertiesForm);

        JSplitPane leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, treeScroll, propsScroll);
        leftSplit.setDividerLocation(250);
        leftSplit.setResizeWeight(0.4);
        leftPanel.add(leftSplit, BorderLayout.CENTER);

        // --- Main split: left panel + canvas ---
        canvasContainer = new JPanel(new BorderLayout());

        // --- Loading progress bar (shown below the 3D viewport while loading) ---
        buildLoadingBar();

        // Viewport wraps the canvas and loading bar
        JPanel viewportPanel = new JPanel(new BorderLayout());
        viewportPanel.add(canvasContainer, BorderLayout.CENTER);
        viewportPanel.add(loadingOverlay, BorderLayout.SOUTH);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, viewportPanel);
        mainSplit.setDividerLocation(220);
        mainSplit.setResizeWeight(0.0);
        add(mainSplit, BorderLayout.CENTER);
    }

    private void buildLoadingBar() {
        loadingOverlay = new JPanel(new BorderLayout());
        loadingOverlay.setVisible(false);
        loadingOverlay.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        loadingOverlay.setBackground(new Color(50, 50, 54));

        loadingLabel = new JLabel("Loading scene...");
        loadingLabel.setForeground(new Color(220, 220, 220));
        loadingLabel.setFont(loadingLabel.getFont().deriveFont(Font.BOLD, 12f));
        loadingLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));

        loadingProgressBar = new JProgressBar(0, 100);
        loadingProgressBar.setPreferredSize(new Dimension(250, 18));
        loadingProgressBar.setStringPainted(true);

        loadingOverlay.add(loadingLabel, BorderLayout.WEST);
        loadingOverlay.add(loadingProgressBar, BorderLayout.CENTER);
    }

    private void buildPropertiesForm() {
        propertiesForm.removeAll();

        lblType = new JLabel("Type: -");
        lblType.setAlignmentX(Component.LEFT_ALIGNMENT);
        propertiesForm.add(lblType);
        propertiesForm.add(Box.createVerticalStrut(4));

        txtName = new JTextField(15);
        txtName.addActionListener(e -> applyNameChange());
        addFormRow("Name:", txtName);

        propertiesForm.add(Box.createVerticalStrut(8));
        propertiesForm.add(createLabel("Position:"));

        SpinnerNumberModel xm = new SpinnerNumberModel(0.0, -9999.0, 9999.0, 0.1);
        SpinnerNumberModel ym = new SpinnerNumberModel(0.0, -9999.0, 9999.0, 0.1);
        SpinnerNumberModel zm = new SpinnerNumberModel(0.0, -9999.0, 9999.0, 0.1);
        spnPosX = new JSpinner(xm);
        spnPosY = new JSpinner(ym);
        spnPosZ = new JSpinner(zm);
        addFormRow3("X:", spnPosX, "Y:", spnPosY, "Z:", spnPosZ);

        spnPosX.addChangeListener(e -> applyTransformChange());
        spnPosY.addChangeListener(e -> applyTransformChange());
        spnPosZ.addChangeListener(e -> applyTransformChange());

        propertiesForm.add(Box.createVerticalStrut(8));
        propertiesForm.add(createLabel("Rotation (degrees):"));

        spnRotX = new JSpinner(new SpinnerNumberModel(0.0, -360.0, 360.0, 1.0));
        spnRotY = new JSpinner(new SpinnerNumberModel(0.0, -360.0, 360.0, 1.0));
        spnRotZ = new JSpinner(new SpinnerNumberModel(0.0, -360.0, 360.0, 1.0));
        addFormRow3("X:", spnRotX, "Y:", spnRotY, "Z:", spnRotZ);

        spnRotX.addChangeListener(e -> applyTransformChange());
        spnRotY.addChangeListener(e -> applyTransformChange());
        spnRotZ.addChangeListener(e -> applyTransformChange());

        propertiesForm.add(Box.createVerticalStrut(8));

        // Scale header row with proportional checkbox
        JPanel scaleHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        scaleHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        scaleHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        JLabel lblScale = new JLabel("Scale:");
        lblScale.setFont(lblScale.getFont().deriveFont(Font.BOLD));
        scaleHeader.add(lblScale);
        chkProportionalScale = new JCheckBox("Proportional", true);
        chkProportionalScale.setFont(chkProportionalScale.getFont().deriveFont(Font.PLAIN, 11f));
        scaleHeader.add(chkProportionalScale);
        propertiesForm.add(scaleHeader);

        spnScaleX = new JSpinner(new SpinnerNumberModel(1.0, 0.001, 999.0, 0.1));
        spnScaleY = new JSpinner(new SpinnerNumberModel(1.0, 0.001, 999.0, 0.1));
        spnScaleZ = new JSpinner(new SpinnerNumberModel(1.0, 0.001, 999.0, 0.1));
        addFormRow3("X:", spnScaleX, "Y:", spnScaleY, "Z:", spnScaleZ);

        spnScaleX.addChangeListener(e -> onScaleSpinnerChanged(spnScaleX));
        spnScaleY.addChangeListener(e -> onScaleSpinnerChanged(spnScaleY));
        spnScaleZ.addChangeListener(e -> onScaleSpinnerChanged(spnScaleZ));

        // Size fields (Box only - shown/hidden based on selection)
        sizeFieldsPanel = new JPanel();
        sizeFieldsPanel.setLayout(new BoxLayout(sizeFieldsPanel, BoxLayout.Y_AXIS));
        sizeFieldsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        sizeFieldsPanel.add(Box.createVerticalStrut(8));
        JLabel lblSize = new JLabel("Size:");
        lblSize.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblSize.setFont(lblSize.getFont().deriveFont(Font.BOLD));
        sizeFieldsPanel.add(lblSize);

        spnSizeX = new JSpinner(new SpinnerNumberModel(1.0, 0.01, 9999.0, 0.1));
        spnSizeY = new JSpinner(new SpinnerNumberModel(1.0, 0.01, 9999.0, 0.1));
        spnSizeZ = new JSpinner(new SpinnerNumberModel(1.0, 0.01, 9999.0, 0.1));

        JPanel sizeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        sizeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        sizeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        Dimension sizeSpinSize = new Dimension(80, 24);
        spnSizeX.setPreferredSize(sizeSpinSize);
        spnSizeY.setPreferredSize(sizeSpinSize);
        spnSizeZ.setPreferredSize(sizeSpinSize);
        sizeRow.add(new JLabel("X:"));
        sizeRow.add(spnSizeX);
        sizeRow.add(new JLabel("Y:"));
        sizeRow.add(spnSizeY);
        sizeRow.add(new JLabel("Z:"));
        sizeRow.add(spnSizeZ);
        sizeFieldsPanel.add(sizeRow);

        spnSizeX.addChangeListener(e -> applySizeChange());
        spnSizeY.addChangeListener(e -> applySizeChange());
        spnSizeZ.addChangeListener(e -> applySizeChange());

        sizeFieldsPanel.setVisible(false);
        propertiesForm.add(sizeFieldsPanel);

        // Radius field (Sphere only - shown/hidden based on selection)
        radiusPanel = new JPanel();
        radiusPanel.setLayout(new BoxLayout(radiusPanel, BoxLayout.Y_AXIS));
        radiusPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        radiusPanel.add(Box.createVerticalStrut(8));
        JLabel lblRadius = new JLabel("Radius:");
        lblRadius.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblRadius.setFont(lblRadius.getFont().deriveFont(Font.BOLD));
        radiusPanel.add(lblRadius);

        spnRadius = new JSpinner(new SpinnerNumberModel(0.5, 0.01, 9999.0, 0.1));
        JPanel radiusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        radiusRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        radiusRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        spnRadius.setPreferredSize(new Dimension(80, 24));
        radiusRow.add(spnRadius);
        radiusPanel.add(radiusRow);

        spnRadius.addChangeListener(e -> applyRadiusChange());

        radiusPanel.setVisible(false);
        propertiesForm.add(radiusPanel);

        // Static / Collider checkboxes (BOX and SPHERE only)
        staticColliderPanel = new JPanel();
        staticColliderPanel.setLayout(new BoxLayout(staticColliderPanel, BoxLayout.Y_AXIS));
        staticColliderPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        staticColliderPanel.add(Box.createVerticalStrut(8));
        JLabel lblFlags = new JLabel("Flags:");
        lblFlags.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblFlags.setFont(lblFlags.getFont().deriveFont(Font.BOLD));
        staticColliderPanel.add(lblFlags);

        JPanel flagsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        flagsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        flagsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        chkStaticEntity = new JCheckBox("Static");
        chkColliderEntity = new JCheckBox("Collider");
        flagsRow.add(chkStaticEntity);
        flagsRow.add(chkColliderEntity);
        staticColliderPanel.add(flagsRow);

        chkStaticEntity.addActionListener(e -> applyStaticColliderChange());
        chkColliderEntity.addActionListener(e -> applyStaticColliderChange());

        staticColliderPanel.setVisible(false);
        propertiesForm.add(staticColliderPanel);

        // Material dropdown (BOX and SPHERE only)
        materialPanel = new JPanel();
        materialPanel.setLayout(new BoxLayout(materialPanel, BoxLayout.Y_AXIS));
        materialPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        materialPanel.add(Box.createVerticalStrut(8));
        JLabel lblMaterial = new JLabel("Material:");
        lblMaterial.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblMaterial.setFont(lblMaterial.getFont().deriveFont(Font.BOLD));
        materialPanel.add(lblMaterial);

        cboMaterial = new JComboBox<>(new String[]{
                "", "pond", "rock", "rock2", "brickwall", "dirt", "grass", "road", "alpha", "alpha2"
        });
        cboMaterial.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        cboMaterial.setAlignmentX(Component.LEFT_ALIGNMENT);
        cboMaterial.addActionListener(e -> applyMaterialChange());
        materialPanel.add(cboMaterial);

        materialPanel.setVisible(false);
        propertiesForm.add(materialPanel);

        // Hidden checkbox (BOX, SPHERE, MODEL)
        hiddenPanel = new JPanel();
        hiddenPanel.setLayout(new BoxLayout(hiddenPanel, BoxLayout.Y_AXIS));
        hiddenPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        hiddenPanel.add(Box.createVerticalStrut(8));
        JPanel hiddenRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        hiddenRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        hiddenRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        chkHidden = new JCheckBox("Hidden");
        hiddenRow.add(chkHidden);
        hiddenPanel.add(hiddenRow);
        chkHidden.addActionListener(e -> applyHiddenChange());
        hiddenPanel.setVisible(false);
        propertiesForm.add(hiddenPanel);

        // Shadow Mode combo (BOX, SPHERE, MODEL)
        shadowModePanel = new JPanel();
        shadowModePanel.setLayout(new BoxLayout(shadowModePanel, BoxLayout.Y_AXIS));
        shadowModePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        shadowModePanel.add(Box.createVerticalStrut(8));
        JPanel shadowModeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        shadowModeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        shadowModeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        shadowModeRow.add(new JLabel("Shadow Mode:"));
        cboShadowMode = new JComboBox<>(new String[]{"None", "Cast", "Receive", "Both"});
        cboShadowMode.addActionListener(e -> applyShadowModeChange());
        shadowModeRow.add(cboShadowMode);
        shadowModePanel.add(shadowModeRow);
        shadowModePanel.setVisible(false);
        propertiesForm.add(shadowModePanel);

        // Joint Mapping checkbox + edit button (MODEL only)
        jointMappingPanel = new JPanel();
        jointMappingPanel.setLayout(new BoxLayout(jointMappingPanel, BoxLayout.Y_AXIS));
        jointMappingPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        jointMappingPanel.add(Box.createVerticalStrut(8));
        JPanel jointMappingRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        jointMappingRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        jointMappingRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        chkJointMapping = new JCheckBox("Joint Mapping");
        chkJointMapping.addActionListener(e -> applyJointMappingCheckChange());
        btnEditJointMapping = new JButton("...");
        btnEditJointMapping.setPreferredSize(new Dimension(32, 22));
        btnEditJointMapping.setEnabled(false);
        btnEditJointMapping.addActionListener(e -> showJointMappingEditor());
        jointMappingRow.add(chkJointMapping);
        jointMappingRow.add(btnEditJointMapping);
        jointMappingPanel.add(jointMappingRow);
        jointMappingPanel.setVisible(false);
        propertiesForm.add(jointMappingPanel);

        propertiesForm.revalidate();
    }

    private JLabel createLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
        return lbl;
    }

    private void addFormRow(String label, JComponent field) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        row.add(new JLabel(label));
        row.add(field);
        propertiesForm.add(row);
    }

    private void addFormRow3(String l1, JSpinner s1, String l2, JSpinner s2, String l3, JSpinner s3) {
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
        propertiesForm.add(row);
    }

    // --- Model picker dialog ---

    private void showModelPickerDialog() {
        showModelPickerDialog(null, -1);
    }

    private void showModelPickerDialog(java.util.List<DesignerEntity> targetList, int insertIndex) {
        if (app == null) return;

        List<String> modelNames = app.getAvailableModelNames();
        if (modelNames.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No 3D models available. Check that model resources are loaded.",
                    "No Models", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JComboBox<String> cmbModels = new JComboBox<>(modelNames.toArray(new String[0]));
        cmbModels.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof String && app.isModelVehicle((String) value)) {
                    setText(value + "  (vehicle)");
                }
                return this;
            }
        });
        JCheckBox chkStatic = new JCheckBox("Static");
        JCheckBox chkDynamic = new JCheckBox("Dynamic");
        chkStatic.addActionListener(e -> { if (chkStatic.isSelected()) chkDynamic.setSelected(false); });
        chkDynamic.addActionListener(e -> { if (chkDynamic.isSelected()) chkStatic.setSelected(false); });

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Model:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(cmbModels, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Options:"), gbc);
        gbc.gridx = 1;
        JPanel optionsPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
        optionsPanel.add(chkStatic);
        optionsPanel.add(chkDynamic);
        panel.add(optionsPanel, gbc);

        int result = JOptionPane.showConfirmDialog(this, panel,
                "Add 3D Model", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String selectedModel = (String) cmbModels.getSelectedItem();
            boolean isStatic = chkStatic.isSelected();
            boolean isDynamic = chkDynamic.isSelected();
            if (selectedModel != null) {
                boolean isVehicle = app.isModelVehicle(selectedModel);
                app.enqueue(() -> { app.addModel(selectedModel, isStatic, isDynamic, isVehicle, targetList, insertIndex); return null; });
            }
        }
    }

    // --- JME3 Initialization ---

    protected void initJME3() {
        if (sharedApp == null) {
            // First designer panel: create the shared JME3 app and canvas
            sharedApp = new DesignerApp();
            sharedApp.setProjectPath(projectPath);
            sharedApp.setDesignerFile(designerFile);

            // Load document
            if (designerFile.exists() && designerFile.length() > 0) {
                try {
                    sharedApp.setDocument(DesignerDocument.load(designerFile));
                } catch (IOException e) {
                    System.err.println("Failed to load designer document: " + e.getMessage());
                    sharedApp.setDocument(new DesignerDocument(designerFile.getAbsolutePath()));
                }
            } else {
                sharedApp.setDocument(new DesignerDocument(designerFile.getAbsolutePath()));
            }

            AppSettings settings = new AppSettings(true);
            settings.setWidth(800);
            settings.setHeight(600);
            settings.setSamples(4);
            settings.setVSync(true);
            settings.setFrameRate(60);
            settings.setGammaCorrection(false);

            sharedApp.setSettings(settings);
            sharedApp.setPauseOnLostFocus(false);
            sharedApp.setShowSettings(false);
            sharedApp.createCanvas();

            JmeCanvasContext ctx = (JmeCanvasContext) sharedApp.getContext();
            ctx.setSystemListener(sharedApp);
            sharedCanvas = ctx.getCanvas();
            sharedCanvas.setMinimumSize(new Dimension(100, 100));

            // Resize JME3 viewport when the canvas is resized
            sharedCanvas.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    int w = sharedCanvas.getWidth();
                    int h = sharedCanvas.getHeight();
                    if (w > 0 && h > 0 && sharedApp != null) {
                        sharedApp.enqueue(() -> {
                            sharedApp.onCanvasResized(w, h);
                            return null;
                        });
                    }
                }
            });

            sharedApp.startCanvas();

            // Workaround for LWJGL cursor issue
            Timer cursorResetTimer = new Timer(500, evt -> {
                if (sharedCanvas != null) {
                    sharedCanvas.setCursor(Cursor.getDefaultCursor());
                }
                Window window = SwingUtilities.getWindowAncestor(DesignerPanel.this);
                if (window != null) {
                    window.setCursor(Cursor.getDefaultCursor());
                }
            });
            cursorResetTimer.setRepeats(false);
            cursorResetTimer.start();

            // This is the first panel — it becomes active immediately.
            // simpleInitApp() will load the document entities asynchronously.
            activeDesignerPanel = this;
        }

        // Point instance fields to the shared app/canvas
        app = sharedApp;
        canvas = sharedCanvas;

        // Insert canvas into this panel's canvas container
        canvasContainer.add(canvas, BorderLayout.CENTER);

        // Set up callbacks pointing to this panel
        setupAppCallbacks();
    }

    /**
     * Wires the shared DesignerApp callbacks to this panel instance so that
     * selection changes, scene changes, scripts tree refreshes, and code file
     * updates are routed to this panel's UI.
     */
    private void setupAppCallbacks() {
        // Forward the scripts tree refresh callback to the app
        if (scriptsTreeRefreshCallback != null) {
            sharedApp.setScriptsTreeRefreshCallback(scriptsTreeRefreshCallback);
        }

        // Forward the code file updated callback to the app
        if (codeFileUpdatedCallback != null) {
            sharedApp.setCodeFileUpdatedCallback(codeFileUpdatedCallback);
        }

        // Set callback so the app notifies this panel of changes
        sharedApp.setDesignerPanelCallback(new DesignerApp.DesignerPanelCallback() {
            @Override
            public void onSelectionChanged(DesignerEntity entity) {
                SwingUtilities.invokeLater(() -> {
                    updatePropertiesPanel(entity);
                    selectEntityInTree(entity);
                    // Hide code editor when a 3D entity is selected via viewport
                    if (entity != null && entity.getType() != DesignerEntityType.CODE) {
                        hideCodeEditor();
                    }
                });
            }

            @Override
            public void onSceneChanged() {
                SwingUtilities.invokeLater(() -> refreshSceneTree());
            }

            @Override
            public void onLoadingProgress(int loaded, int total) {
                SwingUtilities.invokeLater(() -> updateLoadingProgress(loaded, total));
            }
        });
    }

    private void updateLoadingProgress(int loaded, int total) {
        if (total <= 0) {
            // No entities to load — hide immediately
            loadingOverlay.setVisible(false);
            return;
        }
        if (loaded >= total) {
            // Loading complete — hide the overlay
            loadingOverlay.setVisible(false);
            return;
        }
        // Show overlay and update progress
        int percent = (int) ((loaded / (float) total) * 100);
        loadingProgressBar.setValue(percent);
        loadingProgressBar.setString(loaded + " / " + total + " objects");
        loadingLabel.setText("Loading scene...");
        if (!loadingOverlay.isVisible()) {
            loadingOverlay.setVisible(true);
        }
    }

    /**
     * Activates this designer panel: moves the shared JME canvas into this
     * panel's container, switches the DesignerApp to this panel's document,
     * and restores the per-panel camera state.
     *
     * Called by EditorTabPanel when switching to a designer tab.
     */
    public void activatePanel() {
        if (activeDesignerPanel == this) return;

        if (activeDesignerPanel != null) {
            activeDesignerPanel.deactivatePanel();
        }

        // Move the shared canvas into this panel's container
        canvasContainer.add(sharedCanvas, BorderLayout.CENTER);
        canvasContainer.revalidate();
        canvasContainer.repaint();

        app = sharedApp;
        canvas = sharedCanvas;

        // Wire callbacks to this panel
        setupAppCallbacks();

        // Switch the JME app to this panel's document
        final boolean restoreCamera = hasSavedCameraState;
        final float dist = savedCamDistance;
        final float yaw = savedCamYaw;
        final float pitch = savedCamPitch;
        final Vector3f target = savedCamTarget.clone();

        sharedApp.enqueue(() -> {
            sharedApp.switchDocument(designerFile, projectPath);
            if (restoreCamera) {
                sharedApp.setOrbitCameraState(dist, yaw, pitch, target);
            }
            return null;
        });

        activeDesignerPanel = this;
    }

    /**
     * Deactivates this designer panel: saves the current orbit camera state
     * and the document.  Called when switching away from this designer tab.
     */
    public void deactivatePanel() {
        if (sharedApp == null) return;

        // Save orbit camera state for later restore
        savedCamDistance = sharedApp.getCameraDistance();
        savedCamYaw = sharedApp.getCameraYaw();
        savedCamPitch = sharedApp.getCameraPitch();
        savedCamTarget = sharedApp.getCameraTarget();
        hasSavedCameraState = true;

        // Remove canvas from this panel's container (it will be added to the next)
        canvasContainer.removeAll();
        canvasContainer.revalidate();
        canvasContainer.repaint();
    }

    /**
     * Deactivates this panel AND clears the shared app's in-memory document
     * and entities.  Called when the designer file is being deleted, so that
     * stale state is not accidentally written to a new file with the same path.
     */
    public void clearAndDeactivatePanel() {
        deactivatePanel();
        if (sharedApp != null) {
            sharedApp.enqueue(() -> {
                sharedApp.clearDocument();
                return null;
            });
        }
        activeDesignerPanel = null;
    }

    // --- Scene tree ---

    public void refreshSceneTree() {
        // Remember expanded section paths so we can restore them after reload
        java.util.Set<String> expandedIds = new java.util.HashSet<>();
        for (int i = 0; i < sceneTree.getRowCount(); i++) {
            TreePath path = sceneTree.getPathForRow(i);
            if (sceneTree.isExpanded(path)) {
                DefaultMutableTreeNode n = (DefaultMutableTreeNode) path.getLastPathComponent();
                if (n.getUserObject() instanceof EntityTreeNode) {
                    expandedIds.add(((EntityTreeNode) n.getUserObject()).entity.getId());
                }
            }
        }

        sceneTreeRoot.removeAllChildren();
        if (app != null) {
            // Snapshot the list to avoid ConcurrentModificationException —
            // the JME thread may be adding entities while Swing iterates.
            List<DesignerEntity> snapshot = new ArrayList<>(app.getEntities());
            System.out.println("[TRACE] refreshSceneTree: top-level entities count=" + snapshot.size());
            for (DesignerEntity e : snapshot) {
                System.out.println("[TRACE]   entity: " + e.getName() + " type=" + e.getType()
                        + (e.getType() == DesignerEntityType.SECTION ? " children=" + e.getChildren().size() : ""));
            }
            buildTreeNodes(sceneTreeRoot, snapshot);
        }
        sceneTreeModel.reload();
        // Expand root
        sceneTree.expandRow(0);
        // Restore expanded sections
        restoreExpandedSections(sceneTreeRoot, expandedIds);
    }

    private void buildTreeNodes(DefaultMutableTreeNode parent, List<DesignerEntity> entities) {
        for (DesignerEntity entity : entities) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(new EntityTreeNode(entity, entity.getName()));
            parent.add(node);
            if (entity.getType() == DesignerEntityType.SECTION) {
                buildTreeNodes(node, entity.getChildren());
            }
        }
    }

    private void restoreExpandedSections(DefaultMutableTreeNode parent, java.util.Set<String> expandedIds) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            if (child.getUserObject() instanceof EntityTreeNode) {
                EntityTreeNode etn = (EntityTreeNode) child.getUserObject();
                if (etn.entity.getType() == DesignerEntityType.SECTION) {
                    if (expandedIds.contains(etn.entity.getId())) {
                        sceneTree.expandPath(new TreePath(child.getPath()));
                    }
                    restoreExpandedSections(child, expandedIds);
                }
            }
        }
    }

    private void onTreeSelectionChanged(TreeSelectionEvent e) {
        if (updatingTreeSelection) return;
        DefaultMutableTreeNode selected = (DefaultMutableTreeNode) sceneTree.getLastSelectedPathComponent();
        if (selected == null || !(selected.getUserObject() instanceof EntityTreeNode)) {
            hideCodeEditor();
            return;
        }
        EntityTreeNode etn = (EntityTreeNode) selected.getUserObject();

        // CODE and SECTION nodes have no 3D scene node
        if (etn.entity.getType() == DesignerEntityType.CODE) {
            if (app != null) {
                app.enqueue(() -> {
                    app.getSelectionManager().deselect();
                    return null;
                });
            }
            showCodeEditor(etn.entity);
            updatePropertiesPanel(null);
            return;
        }

        if (etn.entity.getType() == DesignerEntityType.SECTION) {
            if (app != null) {
                app.enqueue(() -> {
                    app.getSelectionManager().deselect();
                    return null;
                });
            }
            hideCodeEditor();
            updatePropertiesPanel(null);
            return;
        }

        hideCodeEditor();
        if (app != null) {
            app.enqueue(() -> {
                app.getSelectionManager().select(etn.entity);
                return null;
            });
        }
    }

    protected void selectEntityInTree(DesignerEntity entity) {
        updatingTreeSelection = true;
        try {
            if (entity == null) {
                sceneTree.clearSelection();
                return;
            }
            DefaultMutableTreeNode found = findTreeNode(sceneTreeRoot, entity);
            if (found != null) {
                TreePath path = new TreePath(found.getPath());
                sceneTree.setSelectionPath(path);
                sceneTree.scrollPathToVisible(path);
            } else {
                sceneTree.clearSelection();
            }
        } finally {
            updatingTreeSelection = false;
        }
    }

    private DefaultMutableTreeNode findTreeNode(DefaultMutableTreeNode parent, DesignerEntity entity) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            if (child.getUserObject() instanceof EntityTreeNode) {
                EntityTreeNode etn = (EntityTreeNode) child.getUserObject();
                if (etn.entity == entity) {
                    return child;
                }
                // Search inside section children
                if (etn.entity.getType() == DesignerEntityType.SECTION) {
                    DefaultMutableTreeNode found = findTreeNode(child, entity);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    private void showTreeContextMenu(MouseEvent e) {
        int row = sceneTree.getClosestRowForLocation(e.getX(), e.getY());
        if (row < 0) return;
        sceneTree.setSelectionRow(row);

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) sceneTree.getLastSelectedPathComponent();
        if (node == null) return;

        JPopupMenu menu = new JPopupMenu();

        // Right-click on root "Scene" node
        if (node == sceneTreeRoot) {
            JMenuItem addSectionItem = new JMenuItem("New section...");
            addSectionItem.addActionListener(ev -> promptAndCreateSection(-1, null));
            menu.add(addSectionItem);
            menu.show(sceneTree, e.getX(), e.getY());
            return;
        }

        if (!(node.getUserObject() instanceof EntityTreeNode)) return;
        EntityTreeNode etn = (EntityTreeNode) node.getUserObject();

        // "New section..." - available on all nodes
        JMenuItem addSectionItem = new JMenuItem("New section...");
        addSectionItem.addActionListener(ev -> {
            if (etn.entity.getType() == DesignerEntityType.SECTION) {
                // Right-click on a section: create child section inside it
                promptAndCreateSection(-1, etn.entity);
            } else {
                // Right-click on a regular node: create section after it in its parent list
                int entityIndex = app.getEntities().indexOf(etn.entity);
                int insertIndex = entityIndex >= 0 ? entityIndex + 1 : -1;
                // Check if the entity is inside a section
                DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) node.getParent();
                if (parentNode != null && parentNode != sceneTreeRoot
                        && parentNode.getUserObject() instanceof EntityTreeNode) {
                    EntityTreeNode parentEtn = (EntityTreeNode) parentNode.getUserObject();
                    if (parentEtn.entity.getType() == DesignerEntityType.SECTION) {
                        int childIndex = parentEtn.entity.getChildren().indexOf(etn.entity);
                        promptAndCreateSectionInSection(parentEtn.entity, childIndex + 1);
                        return;
                    }
                }
                promptAndCreateSection(insertIndex, null);
            }
        });
        menu.add(addSectionItem);

        // "Add Code Node" - inserts after the selected item (or as last child in a section)
        JMenuItem addCodeItem = new JMenuItem("Add Code Node");
        addCodeItem.addActionListener(ev -> {
            String name = JOptionPane.showInputDialog(this, "Code node name:", "New Code Node",
                    JOptionPane.PLAIN_MESSAGE);
            if (name != null && !name.trim().isEmpty()) {
                String trimmedName = name.trim();
                if (app != null) {
                    java.util.List<DesignerEntity> targetList;
                    int insertIdx;
                    if (etn.entity.getType() == DesignerEntityType.SECTION) {
                        targetList = etn.entity.getChildren();
                        insertIdx = -1; // append as last child
                    } else {
                        DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) node.getParent();
                        if (parentNode != null && parentNode != sceneTreeRoot
                                && parentNode.getUserObject() instanceof EntityTreeNode) {
                            EntityTreeNode parentEtn = (EntityTreeNode) parentNode.getUserObject();
                            if (parentEtn.entity.getType() == DesignerEntityType.SECTION) {
                                targetList = parentEtn.entity.getChildren();
                                insertIdx = parentEtn.entity.getChildren().indexOf(etn.entity) + 1;
                            } else {
                                targetList = null;
                                int ei = app.getEntities().indexOf(etn.entity);
                                insertIdx = ei >= 0 ? ei + 1 : -1;
                            }
                        } else {
                            targetList = null;
                            int ei = app.getEntities().indexOf(etn.entity);
                            insertIdx = ei >= 0 ? ei + 1 : -1;
                        }
                    }
                    final java.util.List<DesignerEntity> fTargetList = targetList;
                    final int fInsertIdx = insertIdx;
                    app.enqueue(() -> {
                        app.addCodeNode(trimmedName, fTargetList, fInsertIdx);
                        return null;
                    });
                }
            }
        });
        menu.add(addCodeItem);

        menu.addSeparator();

        // "Add 3D Model..." - inserts after the selected item (or as last child in a section)
        JMenuItem addModelItem = new JMenuItem("Add 3D Model...");
        addModelItem.addActionListener(ev -> {
            java.util.List<DesignerEntity> targetList;
            int insertIdx;
            if (etn.entity.getType() == DesignerEntityType.SECTION) {
                targetList = etn.entity.getChildren();
                insertIdx = -1; // append as last child
            } else {
                DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) node.getParent();
                if (parentNode != null && parentNode != sceneTreeRoot
                        && parentNode.getUserObject() instanceof EntityTreeNode) {
                    EntityTreeNode parentEtn = (EntityTreeNode) parentNode.getUserObject();
                    if (parentEtn.entity.getType() == DesignerEntityType.SECTION) {
                        targetList = parentEtn.entity.getChildren();
                        insertIdx = parentEtn.entity.getChildren().indexOf(etn.entity) + 1;
                    } else {
                        targetList = null;
                        int ei = app.getEntities().indexOf(etn.entity);
                        insertIdx = ei >= 0 ? ei + 1 : -1;
                    }
                } else {
                    targetList = null;
                    int ei = app.getEntities().indexOf(etn.entity);
                    insertIdx = ei >= 0 ? ei + 1 : -1;
                }
            }
            showModelPickerDialog(targetList, insertIdx);
        });
        menu.add(addModelItem);

        // "Add Box..." - inserts after the selected item (or as last child in a section)
        JMenuItem addBoxItem = new JMenuItem("Add Box...");
        addBoxItem.addActionListener(ev -> {
            if (app != null) {
                java.util.List<DesignerEntity> targetList;
                int insertIdx;
                if (etn.entity.getType() == DesignerEntityType.SECTION) {
                    targetList = etn.entity.getChildren();
                    insertIdx = -1;
                } else {
                    DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) node.getParent();
                    if (parentNode != null && parentNode != sceneTreeRoot
                            && parentNode.getUserObject() instanceof EntityTreeNode) {
                        EntityTreeNode parentEtn = (EntityTreeNode) parentNode.getUserObject();
                        if (parentEtn.entity.getType() == DesignerEntityType.SECTION) {
                            targetList = parentEtn.entity.getChildren();
                            insertIdx = parentEtn.entity.getChildren().indexOf(etn.entity) + 1;
                        } else {
                            targetList = null;
                            int ei = app.getEntities().indexOf(etn.entity);
                            insertIdx = ei >= 0 ? ei + 1 : -1;
                        }
                    } else {
                        targetList = null;
                        int ei = app.getEntities().indexOf(etn.entity);
                        insertIdx = ei >= 0 ? ei + 1 : -1;
                    }
                }
                final java.util.List<DesignerEntity> fTargetList = targetList;
                final int fInsertIdx = insertIdx;
                System.out.println("[TRACE] Add Box context menu: targetList=" + (fTargetList != null ? "section children (size=" + fTargetList.size() + ")" : "null (top-level)") + ", insertIdx=" + fInsertIdx);
                app.enqueue(() -> { app.addDefaultBox(fTargetList, fInsertIdx); return null; });
            }
        });
        menu.add(addBoxItem);

        // "Add Sphere" - inserts after the selected item (or as last child in a section)
        JMenuItem addSphereItem = new JMenuItem("Add Sphere");
        addSphereItem.addActionListener(ev -> {
            if (app != null) {
                java.util.List<DesignerEntity> targetList;
                int insertIdx;
                if (etn.entity.getType() == DesignerEntityType.SECTION) {
                    targetList = etn.entity.getChildren();
                    insertIdx = -1;
                } else {
                    DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) node.getParent();
                    if (parentNode != null && parentNode != sceneTreeRoot
                            && parentNode.getUserObject() instanceof EntityTreeNode) {
                        EntityTreeNode parentEtn = (EntityTreeNode) parentNode.getUserObject();
                        if (parentEtn.entity.getType() == DesignerEntityType.SECTION) {
                            targetList = parentEtn.entity.getChildren();
                            insertIdx = parentEtn.entity.getChildren().indexOf(etn.entity) + 1;
                        } else {
                            targetList = null;
                            int ei = app.getEntities().indexOf(etn.entity);
                            insertIdx = ei >= 0 ? ei + 1 : -1;
                        }
                    } else {
                        targetList = null;
                        int ei = app.getEntities().indexOf(etn.entity);
                        insertIdx = ei >= 0 ? ei + 1 : -1;
                    }
                }
                final java.util.List<DesignerEntity> fTargetList = targetList;
                final int fInsertIdx = insertIdx;
                System.out.println("[TRACE] Add Sphere context menu: targetList=" + (fTargetList != null ? "section children (size=" + fTargetList.size() + ")" : "null (top-level)") + ", insertIdx=" + fInsertIdx);
                app.enqueue(() -> { app.addDefaultSphere(fTargetList, fInsertIdx); return null; });
            }
        });
        menu.add(addSphereItem);

        menu.addSeparator();

        // "Rename" for section nodes
        if (etn.entity.getType() == DesignerEntityType.SECTION) {
            JMenuItem renameItem = new JMenuItem("Rename...");
            renameItem.addActionListener(ev -> {
                String newName = JOptionPane.showInputDialog(this, "Rename section:", etn.entity.getName());
                if (newName != null && !newName.trim().isEmpty()) {
                    String trimmed = newName.trim();
                    if (app != null) {
                        app.enqueue(() -> {
                            etn.entity.setName(trimmed);
                            app.markDocumentDirty();
                            return null;
                        });
                    }
                    refreshSceneTree();
                }
            });
            menu.add(renameItem);
        }

        JMenuItem deleteItem = new JMenuItem("Delete");
        if (etn.entity.getType() == DesignerEntityType.CAMERA) {
            deleteItem.setEnabled(false);
            deleteItem.setToolTipText("Camera cannot be deleted");
        }
        deleteItem.addActionListener(ev -> {
            if (app != null) {
                app.enqueue(() -> {
                    app.removeEntity(etn.entity);
                    return null;
                });
            }
        });
        menu.add(deleteItem);
        menu.show(sceneTree, e.getX(), e.getY());
    }

    private void promptAndCreateSection(int insertIndex, DesignerEntity parentSection) {
        String name = JOptionPane.showInputDialog(this, "Section name:", "New Section",
                JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty()) {
            String trimmedName = name.trim();
            if (app != null) {
                if (parentSection != null && parentSection.getType() == DesignerEntityType.SECTION) {
                    // Create as child of existing section
                    app.enqueue(() -> {
                        DesignerEntity section = new DesignerEntity(trimmedName, DesignerEntityType.SECTION);
                        parentSection.addChild(section);
                        app.markDocumentDirty();
                        SwingUtilities.invokeLater(this::refreshSceneTree);
                        return null;
                    });
                } else {
                    // Create at top level
                    app.enqueue(() -> {
                        app.addSectionNode(trimmedName, insertIndex);
                        return null;
                    });
                }
            }
        }
    }

    private void promptAndCreateSectionInSection(DesignerEntity parentSection, int childIndex) {
        String name = JOptionPane.showInputDialog(this, "Section name:", "New Section",
                JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty()) {
            String trimmedName = name.trim();
            if (app != null) {
                app.enqueue(() -> {
                    DesignerEntity section = new DesignerEntity(trimmedName, DesignerEntityType.SECTION);
                    if (childIndex >= 0 && childIndex <= parentSection.getChildren().size()) {
                        parentSection.addChild(childIndex, section);
                    } else {
                        parentSection.addChild(section);
                    }
                    app.markDocumentDirty();
                    SwingUtilities.invokeLater(this::refreshSceneTree);
                    return null;
                });
            }
        }
    }

    // --- Code node double-click rename ---

    private void onTreeDoubleClick(MouseEvent e) {
        int row = sceneTree.getClosestRowForLocation(e.getX(), e.getY());
        if (row < 0) return;
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)
                sceneTree.getPathForRow(row).getLastPathComponent();
        if (node == null || !(node.getUserObject() instanceof EntityTreeNode)) return;
        EntityTreeNode etn = (EntityTreeNode) node.getUserObject();

        if (etn.entity.getType() == DesignerEntityType.CODE || etn.entity.getType() == DesignerEntityType.SECTION) {
            // CODE and SECTION nodes: show rename dialog
            String prompt = etn.entity.getType() == DesignerEntityType.SECTION ? "Rename section:" : "Rename code node:";
            String newName = JOptionPane.showInputDialog(this, prompt,
                    etn.entity.getName());
            if (newName != null && !newName.trim().isEmpty()) {
                String trimmed = newName.trim();
                if (app != null) {
                    app.enqueue(() -> {
                        etn.entity.setName(trimmed);
                        app.markDocumentDirty();
                        return null;
                    });
                }
                refreshSceneTree();
            }
        } else {
            // Non-CODE nodes: ease the editor camera to focus on the entity
            if (app != null) {
                app.enqueue(() -> {
                    app.focusCameraOnEntity(etn.entity);
                    return null;
                });
            }
        }
    }

    // --- Floating code editor ---

    private void buildCodeEditorOverlay() {
        codeEditorOverlay = new JPanel(new BorderLayout());
        codeEditorOverlay.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(80, 120, 200), 2),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        codeEditorOverlay.setBackground(new Color(40, 42, 46));
        codeEditorOverlay.setVisible(false);

        JLabel titleLabel = new JLabel("Code Editor");
        titleLabel.setForeground(new Color(220, 220, 220));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

        codeEditorArea = new JTextArea(12, 50);
        codeEditorArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        codeEditorArea.setBackground(new Color(30, 30, 34));
        codeEditorArea.setForeground(new Color(220, 220, 220));
        codeEditorArea.setCaretColor(Color.WHITE);
        codeEditorArea.setTabSize(4);
        JScrollPane editorScroll = new JScrollPane(codeEditorArea);
        editorScroll.setPreferredSize(new Dimension(500, 250));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        buttonPanel.setOpaque(false);
        JButton btnSave = new JButton("Save");
        JButton btnCancel = new JButton("Cancel");
        btnSave.addActionListener(ev -> saveCodeEditor());
        btnCancel.addActionListener(ev -> hideCodeEditor());
        buttonPanel.add(btnSave);
        buttonPanel.add(btnCancel);

        codeEditorOverlay.add(titleLabel, BorderLayout.NORTH);
        codeEditorOverlay.add(editorScroll, BorderLayout.CENTER);
        codeEditorOverlay.add(buttonPanel, BorderLayout.SOUTH);

        // ESC to cancel, Ctrl+S to save
        InputMap im = codeEditorOverlay.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap am = codeEditorOverlay.getActionMap();
        im.put(KeyStroke.getKeyStroke("ESCAPE"), "cancelCodeEditor");
        am.put("cancelCodeEditor", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) { hideCodeEditor(); }
        });
        im.put(KeyStroke.getKeyStroke("ctrl S"), "saveCodeEditor");
        am.put("saveCodeEditor", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) { saveCodeEditor(); }
        });
    }

    private void showCodeEditor(DesignerEntity codeEntity) {
        if (codeEditorOverlay == null) {
            buildCodeEditorOverlay();
            // Add as an overlay on the canvas container using layered pane
            canvasContainer.setLayout(new BorderLayout());
            // We need to use an overlay approach - add it to the canvasContainer's glass-pane-like layer
        }
        editingCodeEntity = codeEntity;
        codeEditorArea.setText(codeEntity.getCodeText());
        codeEditorArea.setCaretPosition(0);

        if (codeEditorOverlay.getParent() == null) {
            // Add overlay to canvasContainer using OverlayLayout
            JLayeredPane layered = getLayeredPaneForCanvas();
            if (layered != null) {
                layered.add(codeEditorOverlay, JLayeredPane.POPUP_LAYER);
                repositionCodeEditor(layered);
                layered.revalidate();
                layered.repaint();
            }
        }
        codeEditorOverlay.setVisible(true);
        codeEditorArea.requestFocusInWindow();
    }

    private JLayeredPane getLayeredPaneForCanvas() {
        // Walk up to find the JLayeredPane (from the root pane)
        JRootPane rootPane = SwingUtilities.getRootPane(canvasContainer);
        if (rootPane != null) {
            return rootPane.getLayeredPane();
        }
        return null;
    }

    private void repositionCodeEditor(JLayeredPane layered) {
        // Position the overlay centered above the canvas
        Point canvasLoc = SwingUtilities.convertPoint(canvasContainer, 0, 0, layered);
        int cw = canvasContainer.getWidth();
        int ch = canvasContainer.getHeight();
        int ow = Math.min(600, cw - 40);
        int oh = Math.min(350, ch - 40);
        int ox = canvasLoc.x + (cw - ow) / 2;
        int oy = canvasLoc.y + (ch - oh) / 2;
        codeEditorOverlay.setBounds(ox, oy, ow, oh);
    }

    private void hideCodeEditor() {
        if (codeEditorOverlay != null) {
            codeEditorOverlay.setVisible(false);
            editingCodeEntity = null;
        }
    }

    private void saveCodeEditor() {
        if (editingCodeEntity == null || app == null) return;
        String newCode = codeEditorArea.getText();
        DesignerEntity entity = editingCodeEntity;
        app.enqueue(() -> {
            entity.setCodeText(newCode);
            app.markDocumentDirty();
            return null;
        });
        hideCodeEditor();
    }

    // --- Properties panel ---

    public void updatePropertiesPanel(DesignerEntity entity) {
        updatingProperties = true;
        try {
            if (entity == null) {
                txtName.setText("");
                lblType.setText("Type: -");
                clearSpinners();
                sizeFieldsPanel.setVisible(false);
                radiusPanel.setVisible(false);
                staticColliderPanel.setVisible(false);
                materialPanel.setVisible(false);
                hiddenPanel.setVisible(false);
                shadowModePanel.setVisible(false);
                jointMappingPanel.setVisible(false);
                return;
            }

            txtName.setText(entity.getName());
            lblType.setText("Type: " + entity.getType().name());

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

            if (entity.getType() == DesignerEntityType.BOX) {
                spnSizeX.setValue((double) (entity.getSizeX() * 2));
                spnSizeY.setValue((double) (entity.getSizeY() * 2));
                spnSizeZ.setValue((double) (entity.getSizeZ() * 2));
                sizeFieldsPanel.setVisible(true);
            } else {
                sizeFieldsPanel.setVisible(false);
            }

            if (entity.getType() == DesignerEntityType.SPHERE) {
                spnRadius.setValue((double) entity.getRadius());
                radiusPanel.setVisible(true);
            } else {
                radiusPanel.setVisible(false);
            }

            if (entity.getType() == DesignerEntityType.BOX || entity.getType() == DesignerEntityType.SPHERE) {
                chkStaticEntity.setSelected(entity.isStaticEntity());
                chkColliderEntity.setSelected(entity.isColliderEntity());
                staticColliderPanel.setVisible(true);

                cboMaterial.setSelectedItem(entity.getMaterial());
                materialPanel.setVisible(true);
            } else {
                staticColliderPanel.setVisible(false);
                materialPanel.setVisible(false);
            }

            if (entity.getType() == DesignerEntityType.BOX || entity.getType() == DesignerEntityType.SPHERE
                    || entity.getType() == DesignerEntityType.MODEL) {
                chkHidden.setSelected(entity.isHidden());
                hiddenPanel.setVisible(true);

                String sm = entity.getShadowMode();
                if ("cast".equals(sm)) cboShadowMode.setSelectedItem("Cast");
                else if ("receive".equals(sm)) cboShadowMode.setSelectedItem("Receive");
                else if ("both".equals(sm)) cboShadowMode.setSelectedItem("Both");
                else cboShadowMode.setSelectedItem("None");
                shadowModePanel.setVisible(true);

                if (entity.getType() == DesignerEntityType.MODEL) {
                    String jm = entity.getJointMapping();
                    boolean hasJoints = jm != null && !jm.trim().isEmpty();
                    chkJointMapping.setSelected(hasJoints);
                    btnEditJointMapping.setEnabled(hasJoints);
                    jointMappingPanel.setVisible(true);
                } else {
                    jointMappingPanel.setVisible(false);
                }
            } else {
                hiddenPanel.setVisible(false);
                shadowModePanel.setVisible(false);
                jointMappingPanel.setVisible(false);
            }

            // Camera entities don't need scale
            if (entity.getType() == DesignerEntityType.CAMERA) {
                spnScaleX.setEnabled(false);
                spnScaleY.setEnabled(false);
                spnScaleZ.setEnabled(false);
            } else {
                spnScaleX.setEnabled(true);
                spnScaleY.setEnabled(true);
                spnScaleZ.setEnabled(true);
            }
        } finally {
            updatingProperties = false;
        }
    }

    private void clearSpinners() {
        spnPosX.setValue(0.0); spnPosY.setValue(0.0); spnPosZ.setValue(0.0);
        spnRotX.setValue(0.0); spnRotY.setValue(0.0); spnRotZ.setValue(0.0);
        spnScaleX.setValue(1.0); spnScaleY.setValue(1.0); spnScaleZ.setValue(1.0);
        lastScaleX = 1.0; lastScaleY = 1.0; lastScaleZ = 1.0;
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

        applyTransformChange();
    }

    private void applyNameChange() {
        if (updatingProperties || app == null) return;
        DesignerEntity sel = app.getSelectionManager().getSelected();
        if (sel != null) {
            String newName = txtName.getText().trim();
            if (!newName.isEmpty()) {
                app.enqueue(() -> {
                    sel.setName(newName);
                    app.markDocumentDirty();
                    return null;
                });
                refreshSceneTree();
            }
        }
    }

    private void applyTransformChange() {
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
            app.markDocumentDirty();
            return null;
        });
    }

    private void applySizeChange() {
        if (updatingProperties || app == null) return;
        DesignerEntity sel = app.getSelectionManager().getSelected();
        if (sel == null || sel.getType() != DesignerEntityType.BOX) return;

        float sizeX = ((Number) spnSizeX.getValue()).floatValue() / 2f;
        float sizeY = ((Number) spnSizeY.getValue()).floatValue() / 2f;
        float sizeZ = ((Number) spnSizeZ.getValue()).floatValue() / 2f;

        app.enqueue(() -> {
            sel.setSizeX(sizeX);
            sel.setSizeY(sizeY);
            sel.setSizeZ(sizeZ);
            app.updateBoxMesh(sel);
            app.markDocumentDirty();
            return null;
        });
    }

    private void applyRadiusChange() {
        if (updatingProperties || app == null) return;
        DesignerEntity sel = app.getSelectionManager().getSelected();
        if (sel == null || sel.getType() != DesignerEntityType.SPHERE) return;

        float radius = ((Number) spnRadius.getValue()).floatValue();

        app.enqueue(() -> {
            sel.setRadius(radius);
            app.updateSphereMesh(sel);
            app.markDocumentDirty();
            return null;
        });
    }

    private void applyStaticColliderChange() {
        if (updatingProperties || app == null) return;
        DesignerEntity sel = app.getSelectionManager().getSelected();
        if (sel == null) return;
        if (sel.getType() != DesignerEntityType.BOX && sel.getType() != DesignerEntityType.SPHERE) return;

        boolean isStatic = chkStaticEntity.isSelected();
        boolean isCollider = chkColliderEntity.isSelected();

        app.enqueue(() -> {
            app.recreateEntity(sel, isStatic, isCollider);
            return null;
        });
    }

    private void applyHiddenChange() {
        if (updatingProperties || app == null) return;
        DesignerEntity sel = app.getSelectionManager().getSelected();
        if (sel == null) return;
        boolean hidden = chkHidden.isSelected();
        app.enqueue(() -> {
            sel.setHidden(hidden);
            app.markDocumentDirty();
            return null;
        });
    }

    private void applyShadowModeChange() {
        if (updatingProperties || app == null) return;
        DesignerEntity sel = app.getSelectionManager().getSelected();
        if (sel == null) return;
        String selected = (String) cboShadowMode.getSelectedItem();
        String mode;
        if ("Cast".equals(selected)) mode = "cast";
        else if ("Receive".equals(selected)) mode = "receive";
        else if ("Both".equals(selected)) mode = "both";
        else mode = "none";
        String finalMode = mode;
        app.enqueue(() -> {
            sel.setShadowMode(finalMode);
            app.markDocumentDirty();
            return null;
        });
    }

    private void applyJointMappingCheckChange() {
        if (updatingProperties || app == null) return;
        DesignerEntity sel = app.getSelectionManager().getSelected();
        if (sel == null || sel.getType() != DesignerEntityType.MODEL) return;
        boolean checked = chkJointMapping.isSelected();
        btnEditJointMapping.setEnabled(checked);
        if (!checked) {
            app.enqueue(() -> {
                sel.setJointMapping("");
                app.markDocumentDirty();
                return null;
            });
        }
    }

    private void showJointMappingEditor() {
        if (app == null) return;
        DesignerEntity sel = app.getSelectionManager().getSelected();
        if (sel == null || sel.getType() != DesignerEntityType.MODEL) return;

        String[] presetNames = {"(Custom)", "Mixamo", "Blender", "Unity", "Unreal", "Autodesk Maya"};
        String[] presetValues = {
            "",
            "mixamorig:Head,mixamorig:LeftShoulder,mixamorig:LeftArm,mixamorig:LeftForeArm,mixamorig:LeftHand,"
                + "mixamorig:RightShoulder,mixamorig:RightArm,mixamorig:RightForeArm,mixamorig:RightHand,"
                + "mixamorig:LeftUpLeg,mixamorig:LeftLeg,mixamorig:LeftFoot,"
                + "mixamorig:RightUpLeg,mixamorig:RightLeg,mixamorig:RightFoot",
            "head,neck,spine,spine.001,spine.002,shoulder.L,upper_arm.L,forearm.L,hand.L,"
                + "shoulder.R,upper_arm.R,forearm.R,hand.R,thigh.L,shin.L,foot.L,thigh.R,shin.R,foot.R",
            "Head,Neck,Chest,Spine,Hips,LeftUpperArm,LeftLowerArm,LeftHand,"
                + "RightUpperArm,RightLowerArm,RightHand,LeftUpperLeg,LeftLowerLeg,LeftFoot,"
                + "RightUpperLeg,RightLowerLeg,RightFoot",
            "head,neck_01,spine_01,spine_02,spine_03,clavicle_l,upperarm_l,lowerarm_l,hand_l,"
                + "clavicle_r,upperarm_r,lowerarm_r,hand_r,thigh_l,calf_l,foot_l,thigh_r,calf_r,foot_r",
            "Hips,Spine,Spine1,Spine2,Neck,Head,LeftShoulder,LeftArm,LeftForeArm,LeftHand,"
                + "RightShoulder,RightArm,RightForeArm,RightHand,LeftUpLeg,LeftLeg,LeftFoot,"
                + "RightUpLeg,RightLeg,RightFoot"
        };

        JComboBox<String> cboPreset = new JComboBox<>(presetNames);
        JTextArea txtJoints = new JTextArea(8, 40);
        txtJoints.setFont(new Font("Monospaced", Font.PLAIN, 12));
        txtJoints.setLineWrap(true);
        txtJoints.setWrapStyleWord(true);

        String currentMapping = sel.getJointMapping();
        txtJoints.setText(currentMapping != null ? currentMapping : "");

        cboPreset.addActionListener(e -> {
            int idx = cboPreset.getSelectedIndex();
            if (idx > 0) {
                txtJoints.setText(presetValues[idx]);
            }
        });

        JPanel panel = new JPanel(new BorderLayout(4, 4));
        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        topRow.add(new JLabel("Preset:"));
        topRow.add(cboPreset);
        panel.add(topRow, BorderLayout.NORTH);
        panel.add(new JScrollPane(txtJoints), BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(this, panel,
                "Edit Joint Mapping", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String joints = txtJoints.getText().trim();
            app.enqueue(() -> {
                sel.setJointMapping(joints);
                app.markDocumentDirty();
                return null;
            });
            if (joints.isEmpty()) {
                chkJointMapping.setSelected(false);
                btnEditJointMapping.setEnabled(false);
            }
        }
    }

    private void applyMaterialChange() {
        if (updatingProperties || app == null) return;
        DesignerEntity sel = app.getSelectionManager().getSelected();
        if (sel == null) return;
        if (sel.getType() != DesignerEntityType.BOX && sel.getType() != DesignerEntityType.SPHERE) return;

        String material = (String) cboMaterial.getSelectedItem();
        if (material == null) material = "";
        String mat = material;

        app.enqueue(() -> {
            app.applyMaterial(sel, mat);
            return null;
        });
    }

    // --- Lifecycle ---

    /**
     * Clears the 3D scene and reloads all entities from the .smdesign file.
     * Called when a cached panel is reused after its tab was closed, ensuring
     * the designer is 100% aligned with the companion .code file.
     */
    public void reloadScene() {
        if (sharedApp != null) {
            setupAppCallbacks();
            sharedApp.enqueue(() -> {
                sharedApp.reloadDocument();
                return null;
            });
        }
    }

    public void stopDesigner() {
        // Individual panels no longer own the JME context.
        // Use stopSharedDesigner() at application shutdown.
        if (activeDesignerPanel == this) {
            activeDesignerPanel = null;
        }
        app = null;
    }

    /**
     * Stops the shared JME3 application.  Call once at application shutdown
     * to release all JME3 resources.
     */
    public static void stopSharedDesigner() {
        if (sharedApp != null) {
            if (sharedCanvas != null) {
                sharedCanvas.setCursor(Cursor.getDefaultCursor());
            }
            sharedApp.stop();
            sharedApp = null;
            sharedCanvas = null;
            activeDesignerPanel = null;
        }
    }

    public DesignerApp getApp() {
        return app;
    }

    /**
     * Sets a callback to be invoked when the scripts tree in MainApp
     * needs refreshing (e.g., when a .code file is first created).
     */
    public void setScriptsTreeRefreshCallback(Runnable callback) {
        this.scriptsTreeRefreshCallback = callback;
        if (app != null) {
            app.setScriptsTreeRefreshCallback(callback);
        }
    }

    /**
     * Sets a callback to be invoked whenever the companion .code file is
     * updated on disk, so the editor tab can refresh its displayed content.
     */
    public void setCodeFileUpdatedCallback(java.util.function.Consumer<String> callback) {
        this.codeFileUpdatedCallback = callback;
        if (app != null) {
            app.setCodeFileUpdatedCallback(callback);
        }
    }

    /**
     * TransferHandler that allows drag-and-drop reordering of entity nodes
     * within the scene hierarchy tree. Supports dropping into section nodes
     * and reordering within the root or within sections.
     */
    private class SceneTreeTransferHandler extends TransferHandler {
        private final DataFlavor entityNodeFlavor = new DataFlavor(EntityTreeNode.class, "EntityTreeNode");

        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            JTree tree = (JTree) c;
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (node == null || node == sceneTreeRoot) return null;
            Object userObj = node.getUserObject();
            if (!(userObj instanceof EntityTreeNode)) return null;
            EntityTreeNode etn = (EntityTreeNode) userObj;
            return new Transferable() {
                @Override
                public DataFlavor[] getTransferDataFlavors() {
                    return new DataFlavor[]{entityNodeFlavor};
                }

                @Override
                public boolean isDataFlavorSupported(DataFlavor flavor) {
                    return entityNodeFlavor.equals(flavor);
                }

                @Override
                public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                    if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
                    return etn;
                }
            };
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (!support.isDrop()) return false;
            if (!support.isDataFlavorSupported(entityNodeFlavor)) return false;
            JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
            TreePath destPath = dl.getPath();
            if (destPath == null) return false;
            DefaultMutableTreeNode destNode = (DefaultMutableTreeNode) destPath.getLastPathComponent();

            // Allow drops under the root node or under section nodes
            if (destNode == sceneTreeRoot) return true;
            if (destNode.getUserObject() instanceof EntityTreeNode) {
                EntityTreeNode destEtn = (EntityTreeNode) destNode.getUserObject();
                if (destEtn.entity.getType() == DesignerEntityType.SECTION) {
                    // Prevent dropping a section into itself or its own descendants
                    try {
                        EntityTreeNode draggedEtn = (EntityTreeNode) support.getTransferable().getTransferData(entityNodeFlavor);
                        if (draggedEtn.entity == destEtn.entity) return false;
                        if (isDescendantSection(draggedEtn.entity, destEtn.entity)) return false;
                    } catch (Exception ex) {
                        return false;
                    }
                    return true;
                }
            }
            return false;
        }

        /** Check if targetSection is a descendant of ancestor (prevent circular nesting). */
        private boolean isDescendantSection(DesignerEntity ancestor, DesignerEntity target) {
            if (ancestor.getType() != DesignerEntityType.SECTION) return false;
            for (DesignerEntity child : ancestor.getChildren()) {
                if (child == target) return true;
                if (child.getType() == DesignerEntityType.SECTION && isDescendantSection(child, target)) return true;
            }
            return false;
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            try {
                EntityTreeNode draggedEtn = (EntityTreeNode) support.getTransferable().getTransferData(entityNodeFlavor);
                JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
                int dropIndex = dl.getChildIndex();
                DefaultMutableTreeNode destNode = (DefaultMutableTreeNode) dl.getPath().getLastPathComponent();

                if (app == null) return false;

                DesignerEntity draggedEntity = draggedEtn.entity;

                if (destNode == sceneTreeRoot) {
                    // Dropping at the top level
                    List<DesignerEntity> entities = app.getEntities();
                    int sourceIndex = entities.indexOf(draggedEntity);
                    boolean wasTopLevel = sourceIndex >= 0;

                    if (wasTopLevel) {
                        // Reorder within top level
                        int targetIndex = computeDropTargetIndex(dropIndex, sourceIndex, entities, sceneTreeRoot);
                        if (sourceIndex == targetIndex) return false;
                        final int finalTarget = targetIndex;
                        app.enqueue(() -> {
                            app.reorderEntity(sourceIndex, finalTarget);
                            return null;
                        });
                    } else {
                        // Move from a section back to top level
                        int targetIndex = dropIndex >= 0 && dropIndex <= entities.size() ? dropIndex : entities.size();
                        final int finalTarget = targetIndex;
                        app.enqueue(() -> {
                            app.moveEntityToTopLevel(draggedEntity, finalTarget);
                            return null;
                        });
                    }
                    return true;
                }

                // Dropping into a section node
                if (destNode.getUserObject() instanceof EntityTreeNode) {
                    EntityTreeNode destEtn = (EntityTreeNode) destNode.getUserObject();
                    if (destEtn.entity.getType() == DesignerEntityType.SECTION) {
                        DesignerEntity section = destEtn.entity;
                        int childIdx = dropIndex >= 0 ? dropIndex : section.getChildren().size();
                        // Adjust index if moving within the same section
                        int currentIdx = section.getChildren().indexOf(draggedEntity);
                        if (currentIdx >= 0 && childIdx > currentIdx) {
                            childIdx--;
                        }
                        final int finalChildIdx = childIdx;
                        app.enqueue(() -> {
                            app.moveEntityToSection(draggedEntity, section, finalChildIdx);
                            return null;
                        });
                        return true;
                    }
                }

                return false;
            } catch (Exception e) {
                return false;
            }
        }

        private int computeDropTargetIndex(int dropIndex, int sourceIndex,
                                           List<DesignerEntity> entities, DefaultMutableTreeNode parent) {
            if (dropIndex < 0) {
                return entities.size() - 1;
            }
            if (dropIndex >= parent.getChildCount()) {
                return entities.size() - 1;
            }
            if (dropIndex == 0) {
                return 0;
            }
            // Get the entity at the tree node just before the drop position
            int lookupIndex = dropIndex > sourceIndex ? dropIndex - 1 : dropIndex;
            DefaultMutableTreeNode nodeBefore = (DefaultMutableTreeNode) parent.getChildAt(lookupIndex);
            if (nodeBefore.getUserObject() instanceof EntityTreeNode) {
                EntityTreeNode etnBefore = (EntityTreeNode) nodeBefore.getUserObject();
                int idx = entities.indexOf(etnBefore.entity);
                return idx >= 0 ? idx : entities.size() - 1;
            }
            return entities.size() - 1;
        }
    }

    // ── Programmatic designer toolbar icons (white on transparent, 20×20) ──

    private static final int DT_ICON = 20;
    private static final Color DT_COLOR = new Color(220, 220, 220);
    private static final Color DT_HIGHLIGHT = new Color(255, 255, 255, 100);

    private static Icon createDesignerToolbarIcon(String key) {
        BufferedImage img = new BufferedImage(DT_ICON, DT_ICON, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(DT_COLOR);
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        switch (key) {
            case "sphere":    drawToolbarSphere(g);    break;
            case "box":       drawToolbarBox(g);       break;
            case "model":     drawToolbarModel(g);     break;
            case "delete":    drawToolbarDelete(g);    break;
            case "translate": drawToolbarTranslate(g); break;
            case "rotate":    drawToolbarRotate(g);    break;
            case "orbit":     drawToolbarOrbit(g);     break;
            case "pan":       drawToolbarPan(g);       break;
        }
        g.dispose();
        return new ImageIcon(img);
    }

    /** Sphere: circle with highlight arc */
    private static void drawToolbarSphere(Graphics2D g) {
        g.draw(new Ellipse2D.Float(2, 2, 16, 16));
        // highlight arc for 3D feel
        g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(DT_HIGHLIGHT);
        g.drawArc(5, 4, 9, 9, 30, 120);
    }

    /** Box: isometric cube */
    private static void drawToolbarBox(Graphics2D g) {
        int cx = 10, top = 2, bot = 18, mid = 10;
        int left = 2, right = 18;
        // top face
        GeneralPath topFace = new GeneralPath();
        topFace.moveTo(cx, top);
        topFace.lineTo(right, top + 4);
        topFace.lineTo(cx, mid);
        topFace.lineTo(left, top + 4);
        topFace.closePath();
        g.draw(topFace);
        // left face
        g.draw(new Line2D.Float(left, top + 4, left, bot - 4));
        g.draw(new Line2D.Float(left, bot - 4, cx, bot));
        // right face
        g.draw(new Line2D.Float(right, top + 4, right, bot - 4));
        g.draw(new Line2D.Float(right, bot - 4, cx, bot));
        // center vertical
        g.draw(new Line2D.Float(cx, mid, cx, bot));
    }

    /** 3D Model: diamond/gem shape */
    private static void drawToolbarModel(Graphics2D g) {
        GeneralPath p = new GeneralPath();
        p.moveTo(10, 1);
        p.lineTo(18, 6);
        p.lineTo(14, 18);
        p.lineTo(10, 19);
        p.lineTo(6, 18);
        p.lineTo(2, 6);
        p.closePath();
        g.draw(p);
        // inner structure
        g.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(2, 6, 18, 6));
        g.draw(new Line2D.Float(6, 6, 10, 19));
        g.draw(new Line2D.Float(14, 6, 10, 19));
    }

    /** Delete: trash can */
    private static void drawToolbarDelete(Graphics2D g) {
        // lid
        g.draw(new Line2D.Float(3, 5, 17, 5));
        g.draw(new Line2D.Float(7, 5, 7, 3));
        g.draw(new Line2D.Float(7, 3, 13, 3));
        g.draw(new Line2D.Float(13, 3, 13, 5));
        // can body
        GeneralPath can = new GeneralPath();
        can.moveTo(4, 5);
        can.lineTo(5, 17);
        can.lineTo(15, 17);
        can.lineTo(16, 5);
        g.draw(can);
        // vertical lines inside
        g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(8, 8, 8, 14));
        g.draw(new Line2D.Float(10, 8, 10, 14));
        g.draw(new Line2D.Float(12, 8, 12, 14));
    }

    /** Translate: four-direction arrow / move cross */
    private static void drawToolbarTranslate(Graphics2D g) {
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // cross lines
        g.draw(new Line2D.Float(10, 2, 10, 18));
        g.draw(new Line2D.Float(2, 10, 18, 10));
        // top arrow
        g.draw(new Line2D.Float(10, 2, 7, 5));
        g.draw(new Line2D.Float(10, 2, 13, 5));
        // bottom arrow
        g.draw(new Line2D.Float(10, 18, 7, 15));
        g.draw(new Line2D.Float(10, 18, 13, 15));
        // left arrow
        g.draw(new Line2D.Float(2, 10, 5, 7));
        g.draw(new Line2D.Float(2, 10, 5, 13));
        // right arrow
        g.draw(new Line2D.Float(18, 10, 15, 7));
        g.draw(new Line2D.Float(18, 10, 15, 13));
    }

    /** Rotate: circular arrow */
    private static void drawToolbarRotate(Graphics2D g) {
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // arc (most of a circle)
        g.drawArc(3, 3, 14, 14, 30, 280);
        // arrowhead at the end of the arc (pointing clockwise, at ~30 degrees)
        float ax = 14.5f, ay = 13f;
        g.draw(new Line2D.Float(ax, ay, ax + 2, ay - 3));
        g.draw(new Line2D.Float(ax, ay, ax - 3, ay - 1));
    }

    /** Orbit: eye with circular arrow around it */
    private static void drawToolbarOrbit(Graphics2D g) {
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // orbit ring (ellipse)
        g.draw(new Ellipse2D.Float(2, 4, 16, 12));
        // center dot (focal point)
        g.fill(new Ellipse2D.Float(8, 8, 4, 4));
        // small arrow on the orbit ring (top-right)
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(15, 6, 17, 4));
        g.draw(new Line2D.Float(15, 6, 17, 7));
    }

    /** Pan: open hand */
    private static void drawToolbarPan(Graphics2D g) {
        g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // palm
        g.draw(new RoundRectangle2D.Float(4, 8, 12, 10, 4, 4));
        // fingers (four lines going up from palm)
        g.draw(new Line2D.Float(6, 8, 6, 3));
        g.draw(new Line2D.Float(9, 8, 9, 2));
        g.draw(new Line2D.Float(12, 8, 12, 3));
        g.draw(new Line2D.Float(15, 8, 15, 5));
        // finger tips (small rounds)
        g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Ellipse2D.Float(5, 2, 2, 2));
        g.draw(new Ellipse2D.Float(8, 1, 2, 2));
        g.draw(new Ellipse2D.Float(11, 2, 2, 2));
        g.draw(new Ellipse2D.Float(14, 4, 2, 2));
        // thumb
        g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(4, 11, 2, 9));
    }

    /**
     * Custom tree cell renderer that draws elegant white icons for each entity type.
     */
    private static class SceneTreeCellRenderer extends DefaultTreeCellRenderer {
        private static final int ICON_SIZE = 16;
        private static final Map<DesignerEntityType, Icon> ICON_CACHE = new EnumMap<>(DesignerEntityType.class);

        static {
            for (DesignerEntityType type : DesignerEntityType.values()) {
                ICON_CACHE.put(type, createIcon(type));
            }
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            if (value instanceof DefaultMutableTreeNode) {
                Object userObj = ((DefaultMutableTreeNode) value).getUserObject();
                if (userObj instanceof EntityTreeNode) {
                    EntityTreeNode etn = (EntityTreeNode) userObj;
                    Icon icon = ICON_CACHE.get(etn.entity.getType());
                    if (icon != null) {
                        setIcon(icon);
                    }
                }
            }
            return this;
        }

        private static Icon createIcon(DesignerEntityType type) {
            BufferedImage img = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setColor(new Color(220, 220, 220));
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            switch (type) {
                case SPHERE:
                    drawSphere(g);
                    break;
                case BOX:
                    drawBox(g);
                    break;
                case MODEL:
                    drawModel(g);
                    break;
                case CAMERA:
                    drawCamera(g);
                    break;
                case CODE:
                    drawCode(g);
                    break;
                case SECTION:
                    drawSection(g);
                    break;
            }
            g.dispose();
            return new ImageIcon(img);
        }

        /** Sphere: circle with a subtle highlight arc */
        private static void drawSphere(Graphics2D g) {
            g.draw(new Ellipse2D.Float(2, 2, 12, 12));
            // highlight arc for 3D feel
            g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(255, 255, 255, 100));
            g.drawArc(4, 3, 7, 7, 30, 120);
        }

        /** Box: isometric cube */
        private static void drawBox(Graphics2D g) {
            int cx = 8, top = 2, bot = 14, mid = 8;
            int left = 2, right = 14;
            // top face
            GeneralPath topFace = new GeneralPath();
            topFace.moveTo(cx, top);
            topFace.lineTo(right, top + 3);
            topFace.lineTo(cx, mid);
            topFace.lineTo(left, top + 3);
            topFace.closePath();
            g.draw(topFace);
            // left face
            g.draw(new Line2D.Float(left, top + 3, left, bot - 3));
            g.draw(new Line2D.Float(left, bot - 3, cx, bot));
            // right face
            g.draw(new Line2D.Float(right, top + 3, right, bot - 3));
            g.draw(new Line2D.Float(right, bot - 3, cx, bot));
            // center vertical
            g.draw(new Line2D.Float(cx, mid, cx, bot));
        }

        /** Model: 3D diamond / gem shape */
        private static void drawModel(Graphics2D g) {
            GeneralPath p = new GeneralPath();
            // top point
            p.moveTo(8, 1);
            // upper right
            p.lineTo(14, 5);
            // lower right
            p.lineTo(11, 14);
            // bottom
            p.lineTo(8, 15);
            // lower left
            p.lineTo(5, 14);
            // upper left
            p.lineTo(2, 5);
            p.closePath();
            g.draw(p);
            // inner structure lines
            g.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Float(2, 5, 14, 5));
            g.draw(new Line2D.Float(5, 5, 8, 15));
            g.draw(new Line2D.Float(11, 5, 8, 15));
        }

        /** Camera: body rectangle + lens triangle */
        private static void drawCamera(Graphics2D g) {
            // camera body
            g.draw(new RoundRectangle2D.Float(1, 4, 10, 8, 2, 2));
            // lens / viewfinder triangle
            GeneralPath lens = new GeneralPath();
            lens.moveTo(11, 6);
            lens.lineTo(15, 4);
            lens.lineTo(15, 12);
            lens.lineTo(11, 10);
            g.draw(lens);
            // small record dot
            g.setColor(new Color(220, 220, 220));
            g.fillOval(4, 7, 3, 3);
        }

        /** Code: curly braces */
        private static void drawCode(Graphics2D g) {
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            // left brace {
            GeneralPath left = new GeneralPath();
            left.moveTo(6, 2);
            left.curveTo(4, 2, 4, 4, 4, 6);
            left.curveTo(4, 7, 2, 8, 2, 8);
            left.curveTo(2, 8, 4, 9, 4, 10);
            left.curveTo(4, 12, 4, 14, 6, 14);
            g.draw(left);
            // right brace }
            GeneralPath right = new GeneralPath();
            right.moveTo(10, 2);
            right.curveTo(12, 2, 12, 4, 12, 6);
            right.curveTo(12, 7, 14, 8, 14, 8);
            right.curveTo(14, 8, 12, 9, 12, 10);
            right.curveTo(12, 12, 12, 14, 10, 14);
            g.draw(right);
        }

        /** Section: folder icon */
        private static void drawSection(Graphics2D g) {
            // folder tab
            GeneralPath folder = new GeneralPath();
            folder.moveTo(2, 4);
            folder.lineTo(2, 13);
            folder.lineTo(14, 13);
            folder.lineTo(14, 6);
            folder.lineTo(9, 6);
            folder.lineTo(7, 4);
            folder.closePath();
            g.draw(folder);
            // folder tab top
            g.draw(new Line2D.Float(2, 4, 7, 4));
        }
    }

    /**
     * Wrapper for tree node display.
     */
    private static class EntityTreeNode {
        final DesignerEntity entity;
        final String displayName;

        EntityTreeNode(DesignerEntity entity, String displayName) {
            this.entity = entity;
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
