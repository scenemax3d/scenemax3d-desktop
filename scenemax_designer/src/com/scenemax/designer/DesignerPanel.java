package com.scenemax.designer;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeCanvasContext;
import com.scenemax.designer.cinematic.CinematicSegment;
import com.scenemax.designer.cinematic.CinematicTrackData;
import com.scenemax.designer.gizmo.GizmoMode;
import com.scenemax.designer.path.BezierPath;
import com.scenemax.designer.path.PathSample;
import com.scenemax.designer.selection.SelectionManager;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import javax.imageio.ImageIO;
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
import java.nio.charset.StandardCharsets;
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

    private class CinematicSegmentTableModel extends javax.swing.table.AbstractTableModel {
        private final String[] columns = {"Rail", "Start", "End", "Weight"};

        @Override
        public int getRowCount() {
            DesignerEntity rig = getSelectedTreeEntity();
            if (rig == null || rig.getType() != DesignerEntityType.CINEMATIC_RIG) {
                return 0;
            }
            return rig.getCinematicSegments().size();
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
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex >= 1;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            DesignerEntity rig = getSelectedTreeEntity();
            if (rig == null || rig.getType() != DesignerEntityType.CINEMATIC_RIG
                    || rowIndex < 0 || rowIndex >= rig.getCinematicSegments().size()) {
                return null;
            }
            CinematicSegment segment = rig.getCinematicSegments().get(rowIndex);
            switch (columnIndex) {
                case 0:
                    return segment.getTrackName();
                case 1:
                    return segment.getStartAnchor();
                case 2:
                    return segment.getEndAnchor();
                case 3:
                    return segment.getSpeed();
                default:
                    return null;
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (updatingProperties || app == null) return;
            DesignerEntity rig = getSelectedTreeEntity();
            if (rig == null || rig.getType() != DesignerEntityType.CINEMATIC_RIG
                    || rowIndex < 0 || rowIndex >= rig.getCinematicSegments().size()) {
                return;
            }
            CinematicSegment segment = rig.getCinematicSegments().get(rowIndex);
            try {
                if (columnIndex == 1 || columnIndex == 2) {
                    int start = segment.getStartAnchor();
                    int end = segment.getEndAnchor();
                    int parsed = Integer.parseInt(String.valueOf(aValue).trim());
                    if (columnIndex == 1) {
                        start = parsed;
                    } else {
                        end = parsed;
                    }
                    final int newStart = start;
                    final int newEnd = end;
                    app.enqueue(() -> {
                        app.updateCinematicSegmentRange(rig, rowIndex, newStart, newEnd);
                        return null;
                    });
                } else if (columnIndex == 3) {
                    float weight = Float.parseFloat(String.valueOf(aValue).trim());
                    app.enqueue(() -> {
                        app.updateCinematicSegmentSpeed(rig, rowIndex, weight);
                        return null;
                    });
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

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
    private JPanel wedgePanel;
    private JSpinner spnWedgeWidth, spnWedgeHeight, spnWedgeDepth;
    private JSpinner spnRadius;
    private JPanel radiusPanel;
    private JSpinner spnRadiusTop, spnRadiusBottom, spnCylinderHeight;
    private JPanel cylinderPanel;
    private JSpinner spnHcRadiusTop, spnHcRadiusBottom, spnHcInnerRadiusTop, spnHcInnerRadiusBottom, spnHcHeight;
    private JPanel hollowCylinderPanel;
    private JPanel stairsPanel;
    private JSpinner spnStairsWidth, spnStairsStepHeight, spnStairsStepDepth, spnStairsStepCount;
    private JPanel archPanel;
    private JSpinner spnArchWidth, spnArchHeight, spnArchDepth, spnArchThickness, spnArchSegments;
    private JCheckBox chkStaticEntity, chkColliderEntity;
    private JCheckBox chkHidden;
    private JPanel hiddenPanel;
    private JComboBox<String> cboShader;
    private JPanel shaderPanel;
    private JComboBox<String> cboSceneShader;
    private JPanel sceneShaderPanel;
    private JComboBox<String> cboShadowMode;
    private JPanel shadowModePanel;
    private JCheckBox chkJointMapping;
    private JButton btnEditJointMapping;
    private JPanel jointMappingPanel;
    private JPanel pathPropertiesPanel;
    private JLabel lblPathPointCount;
    private JSpinner spnPathSubdivisions;
    private JCheckBox chkPathClosed;
    private JPanel cinematicTrackPanel;
    private JSpinner spnCinematicRadiusX, spnCinematicRadiusZ;
    private JSpinner spnCinematicPreviewSpeed;
    private JLabel lblCinematicSelection;
    private JPanel cinematicRigPanel;
    private JTable cinematicStackTable;
    private CinematicSegmentTableModel cinematicStackTableModel;
    private JSpinner spnCinematicPreviewDuration;
    private JComboBox<String> cboCinematicTarget;
    private JTextField txtCinematicRuntimeId;
    private JTextField txtCinematicRuntimePosHint;
    private JComboBox<EasingOption> cboCinematicEaseIn, cboCinematicEaseOut;
    private JSpinner spnCinematicTargetOffsetX, spnCinematicTargetOffsetY, spnCinematicTargetOffsetZ;
    private java.util.List<DesignerEntity> cinematicTargetChoices = new ArrayList<>();
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
    private ReferenceImageOverlay referenceImageOverlay;

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

        JButton btnAddWedge = new JButton(createDesignerToolbarIcon("wedge"));
        btnAddWedge.setToolTipText("Add Wedge");
        btnAddWedge.addActionListener(e -> {
            if (app != null) app.enqueue(() -> { app.addDefaultWedge(); return null; });
        });

        JButton btnAddCylinder = new JButton(createDesignerToolbarIcon("cylinder"));
        btnAddCylinder.setToolTipText("Add Cylinder");
        btnAddCylinder.addActionListener(e -> {
            if (app != null) app.enqueue(() -> { app.addDefaultCylinder(); return null; });
        });

        JButton btnAddCone = new JButton(createDesignerToolbarIcon("cone"));
        btnAddCone.setToolTipText("Add Cone");
        btnAddCone.addActionListener(e -> {
            if (app != null) app.enqueue(() -> { app.addDefaultCone(); return null; });
        });

        JButton btnAddHollowCylinder = new JButton(createDesignerToolbarIcon("hollowcylinder"));
        btnAddHollowCylinder.setToolTipText("Add Hollow Cylinder");
        btnAddHollowCylinder.addActionListener(e -> {
            if (app != null) app.enqueue(() -> { app.addDefaultHollowCylinder(); return null; });
        });

        JButton btnAddQuad = new JButton(createDesignerToolbarIcon("quad"));
        btnAddQuad.setToolTipText("Add Quad");
        btnAddQuad.addActionListener(e -> {
            if (app != null) app.enqueue(() -> { app.addDefaultQuad(); return null; });
        });

        JButton btnAddStairs = new JButton(createDesignerToolbarIcon("stairs"));
        btnAddStairs.setToolTipText("Add Stairs");
        btnAddStairs.addActionListener(e -> {
            if (app != null) app.enqueue(() -> { app.addDefaultStairs(); return null; });
        });

        JButton btnAddArch = new JButton(createDesignerToolbarIcon("arch"));
        btnAddArch.setToolTipText("Add Arch");
        btnAddArch.addActionListener(e -> {
            if (app != null) app.enqueue(() -> { app.addDefaultArch(); return null; });
        });

        JButton btnAddModel = new JButton(createDesignerToolbarIcon("model"));
        btnAddModel.setToolTipText("Add 3D Model");
        btnAddModel.addActionListener(e -> showModelPickerDialog());

        JButton btnAddPath = new JButton(createDesignerToolbarIcon("path"));
        btnAddPath.setToolTipText("Draw Path (click to place points, double-click to finish, ESC to cancel)");
        btnAddPath.addActionListener(e -> {
            if (app != null) app.enqueue(() -> { app.enterPathMode(); return null; });
        });

        JButton btnAddCinematic = new JButton(createDesignerToolbarIcon("cinematic"));
        btnAddCinematic.setToolTipText("Create Cinematic Rig or Add Rail");
        btnAddCinematic.addActionListener(e -> {
            if (app == null) return;
            if (app.findCinematicRig() == null) {
                showCreateCinematicRigDialog();
            } else {
                app.enqueue(() -> { app.addDefaultCinematicTrack(); return null; });
            }
        });

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
        toolbar.add(btnAddWedge);
        toolbar.add(btnAddCylinder);
        toolbar.add(btnAddCone);
        toolbar.add(btnAddHollowCylinder);
        toolbar.add(btnAddQuad);
        toolbar.add(btnAddStairs);
        toolbar.add(btnAddArch);
        toolbar.add(btnAddModel);
        toolbar.add(btnAddPath);
        toolbar.add(btnAddCinematic);
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

        wedgePanel = new JPanel();
        wedgePanel.setLayout(new BoxLayout(wedgePanel, BoxLayout.Y_AXIS));
        wedgePanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        wedgePanel.add(Box.createVerticalStrut(8));
        JLabel lblWedge = new JLabel("Wedge:");
        lblWedge.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblWedge.setFont(lblWedge.getFont().deriveFont(Font.BOLD));
        wedgePanel.add(lblWedge);

        spnWedgeWidth = new JSpinner(new SpinnerNumberModel(1.0, 0.01, 9999.0, 0.1));
        spnWedgeHeight = new JSpinner(new SpinnerNumberModel(1.0, 0.01, 9999.0, 0.1));
        spnWedgeDepth = new JSpinner(new SpinnerNumberModel(1.0, 0.01, 9999.0, 0.1));

        JPanel wedgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        wedgeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        wedgeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        spnWedgeWidth.setPreferredSize(sizeSpinSize);
        spnWedgeHeight.setPreferredSize(sizeSpinSize);
        spnWedgeDepth.setPreferredSize(sizeSpinSize);
        wedgeRow.add(new JLabel("W:"));
        wedgeRow.add(spnWedgeWidth);
        wedgeRow.add(new JLabel("H:"));
        wedgeRow.add(spnWedgeHeight);
        wedgeRow.add(new JLabel("D:"));
        wedgeRow.add(spnWedgeDepth);
        wedgePanel.add(wedgeRow);

        spnWedgeWidth.addChangeListener(e -> applyWedgeChange());
        spnWedgeHeight.addChangeListener(e -> applyWedgeChange());
        spnWedgeDepth.addChangeListener(e -> applyWedgeChange());

        wedgePanel.setVisible(false);
        propertiesForm.add(wedgePanel);

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

        // Cylinder fields (radiusTop, radiusBottom, height)
        cylinderPanel = new JPanel();
        cylinderPanel.setLayout(new BoxLayout(cylinderPanel, BoxLayout.Y_AXIS));
        cylinderPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        cylinderPanel.add(Box.createVerticalStrut(8));
        JLabel lblCylinder = new JLabel("Cylinder:");
        lblCylinder.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblCylinder.setFont(lblCylinder.getFont().deriveFont(Font.BOLD));
        cylinderPanel.add(lblCylinder);

        spnRadiusTop = new JSpinner(new SpinnerNumberModel(1.0, 0.01, 9999.0, 0.1));
        spnRadiusBottom = new JSpinner(new SpinnerNumberModel(1.0, 0.01, 9999.0, 0.1));
        spnCylinderHeight = new JSpinner(new SpinnerNumberModel(2.0, 0.01, 9999.0, 0.1));

        JPanel cylRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        cylRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        cylRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        Dimension cylSpinSize = new Dimension(70, 24);
        spnRadiusTop.setPreferredSize(cylSpinSize);
        spnRadiusBottom.setPreferredSize(cylSpinSize);
        spnCylinderHeight.setPreferredSize(cylSpinSize);
        cylRow.add(new JLabel("R-Top:"));
        cylRow.add(spnRadiusTop);
        cylRow.add(new JLabel("R-Btm:"));
        cylRow.add(spnRadiusBottom);
        cylRow.add(new JLabel("H:"));
        cylRow.add(spnCylinderHeight);
        cylinderPanel.add(cylRow);

        spnRadiusTop.addChangeListener(e -> applyCylinderChange());
        spnRadiusBottom.addChangeListener(e -> applyCylinderChange());
        spnCylinderHeight.addChangeListener(e -> applyCylinderChange());

        cylinderPanel.setVisible(false);
        propertiesForm.add(cylinderPanel);

        // Hollow Cylinder fields (outer radii, inner radii, height)
        hollowCylinderPanel = new JPanel();
        hollowCylinderPanel.setLayout(new BoxLayout(hollowCylinderPanel, BoxLayout.Y_AXIS));
        hollowCylinderPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        hollowCylinderPanel.add(Box.createVerticalStrut(8));
        JLabel lblHollowCyl = new JLabel("Hollow Cylinder:");
        lblHollowCyl.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblHollowCyl.setFont(lblHollowCyl.getFont().deriveFont(Font.BOLD));
        hollowCylinderPanel.add(lblHollowCyl);

        spnHcRadiusTop = new JSpinner(new SpinnerNumberModel(1.0, 0.01, 9999.0, 0.1));
        spnHcRadiusBottom = new JSpinner(new SpinnerNumberModel(1.0, 0.01, 9999.0, 0.1));
        spnHcInnerRadiusTop = new JSpinner(new SpinnerNumberModel(0.5, 0.01, 9999.0, 0.1));
        spnHcInnerRadiusBottom = new JSpinner(new SpinnerNumberModel(0.5, 0.01, 9999.0, 0.1));
        spnHcHeight = new JSpinner(new SpinnerNumberModel(2.0, 0.01, 9999.0, 0.1));

        Dimension hcSpinSize = new Dimension(60, 24);
        spnHcRadiusTop.setPreferredSize(hcSpinSize);
        spnHcRadiusBottom.setPreferredSize(hcSpinSize);
        spnHcInnerRadiusTop.setPreferredSize(hcSpinSize);
        spnHcInnerRadiusBottom.setPreferredSize(hcSpinSize);
        spnHcHeight.setPreferredSize(hcSpinSize);

        JPanel hcRow1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        hcRow1.setAlignmentX(Component.LEFT_ALIGNMENT);
        hcRow1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        hcRow1.add(new JLabel("R-Top:"));
        hcRow1.add(spnHcRadiusTop);
        hcRow1.add(new JLabel("R-Btm:"));
        hcRow1.add(spnHcRadiusBottom);
        hcRow1.add(new JLabel("H:"));
        hcRow1.add(spnHcHeight);
        hollowCylinderPanel.add(hcRow1);

        JPanel hcRow2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        hcRow2.setAlignmentX(Component.LEFT_ALIGNMENT);
        hcRow2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        hcRow2.add(new JLabel("Inner R-Top:"));
        hcRow2.add(spnHcInnerRadiusTop);
        hcRow2.add(new JLabel("Inner R-Btm:"));
        hcRow2.add(spnHcInnerRadiusBottom);
        hollowCylinderPanel.add(hcRow2);

        spnHcRadiusTop.addChangeListener(e -> applyHollowCylinderChange());
        spnHcRadiusBottom.addChangeListener(e -> applyHollowCylinderChange());
        spnHcInnerRadiusTop.addChangeListener(e -> applyHollowCylinderChange());
        spnHcInnerRadiusBottom.addChangeListener(e -> applyHollowCylinderChange());
        spnHcHeight.addChangeListener(e -> applyHollowCylinderChange());

        hollowCylinderPanel.setVisible(false);
        propertiesForm.add(hollowCylinderPanel);

        stairsPanel = new JPanel();
        stairsPanel.setLayout(new BoxLayout(stairsPanel, BoxLayout.Y_AXIS));
        stairsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        stairsPanel.add(Box.createVerticalStrut(8));
        JLabel lblStairs = new JLabel("Stairs:");
        lblStairs.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblStairs.setFont(lblStairs.getFont().deriveFont(Font.BOLD));
        stairsPanel.add(lblStairs);

        spnStairsWidth = new JSpinner(new SpinnerNumberModel(2.0, 0.05, 9999.0, 0.1));
        spnStairsStepHeight = new JSpinner(new SpinnerNumberModel(0.25, 0.01, 9999.0, 0.05));
        spnStairsStepDepth = new JSpinner(new SpinnerNumberModel(0.4, 0.01, 9999.0, 0.05));
        spnStairsStepCount = new JSpinner(new SpinnerNumberModel(6, 1, 200, 1));

        JPanel stairsRow1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        stairsRow1.setAlignmentX(Component.LEFT_ALIGNMENT);
        stairsRow1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        spnStairsWidth.setPreferredSize(cylSpinSize);
        spnStairsStepHeight.setPreferredSize(cylSpinSize);
        spnStairsStepDepth.setPreferredSize(cylSpinSize);
        stairsRow1.add(new JLabel("W:"));
        stairsRow1.add(spnStairsWidth);
        stairsRow1.add(new JLabel("Step H:"));
        stairsRow1.add(spnStairsStepHeight);
        stairsRow1.add(new JLabel("Step D:"));
        stairsRow1.add(spnStairsStepDepth);
        stairsPanel.add(stairsRow1);

        JPanel stairsRow2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        stairsRow2.setAlignmentX(Component.LEFT_ALIGNMENT);
        stairsRow2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        spnStairsStepCount.setPreferredSize(new Dimension(70, 24));
        stairsRow2.add(new JLabel("Steps:"));
        stairsRow2.add(spnStairsStepCount);
        stairsPanel.add(stairsRow2);

        spnStairsWidth.addChangeListener(e -> applyStairsChange());
        spnStairsStepHeight.addChangeListener(e -> applyStairsChange());
        spnStairsStepDepth.addChangeListener(e -> applyStairsChange());
        spnStairsStepCount.addChangeListener(e -> applyStairsChange());

        stairsPanel.setVisible(false);
        propertiesForm.add(stairsPanel);

        archPanel = new JPanel();
        archPanel.setLayout(new BoxLayout(archPanel, BoxLayout.Y_AXIS));
        archPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        archPanel.add(Box.createVerticalStrut(8));
        JLabel lblArch = new JLabel("Arch:");
        lblArch.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblArch.setFont(lblArch.getFont().deriveFont(Font.BOLD));
        archPanel.add(lblArch);

        spnArchWidth = new JSpinner(new SpinnerNumberModel(2.0, 0.05, 9999.0, 0.1));
        spnArchHeight = new JSpinner(new SpinnerNumberModel(2.5, 0.05, 9999.0, 0.1));
        spnArchDepth = new JSpinner(new SpinnerNumberModel(0.5, 0.01, 9999.0, 0.05));
        spnArchThickness = new JSpinner(new SpinnerNumberModel(0.35, 0.01, 9999.0, 0.05));
        spnArchSegments = new JSpinner(new SpinnerNumberModel(12, 4, 128, 1));

        JPanel archRow1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        archRow1.setAlignmentX(Component.LEFT_ALIGNMENT);
        archRow1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        spnArchWidth.setPreferredSize(cylSpinSize);
        spnArchHeight.setPreferredSize(cylSpinSize);
        spnArchDepth.setPreferredSize(cylSpinSize);
        archRow1.add(new JLabel("W:"));
        archRow1.add(spnArchWidth);
        archRow1.add(new JLabel("H:"));
        archRow1.add(spnArchHeight);
        archRow1.add(new JLabel("D:"));
        archRow1.add(spnArchDepth);
        archPanel.add(archRow1);

        JPanel archRow2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        archRow2.setAlignmentX(Component.LEFT_ALIGNMENT);
        archRow2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        spnArchThickness.setPreferredSize(cylSpinSize);
        spnArchSegments.setPreferredSize(new Dimension(70, 24));
        archRow2.add(new JLabel("Thickness:"));
        archRow2.add(spnArchThickness);
        archRow2.add(new JLabel("Segments:"));
        archRow2.add(spnArchSegments);
        archPanel.add(archRow2);

        spnArchWidth.addChangeListener(e -> applyArchChange());
        spnArchHeight.addChangeListener(e -> applyArchChange());
        spnArchDepth.addChangeListener(e -> applyArchChange());
        spnArchThickness.addChangeListener(e -> applyArchChange());
        spnArchSegments.addChangeListener(e -> applyArchChange());

        archPanel.setVisible(false);
        propertiesForm.add(archPanel);

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

        cboMaterial = new JComboBox<>();
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

        // Shader combo (3D entities only)
        shaderPanel = new JPanel();
        shaderPanel.setLayout(new BoxLayout(shaderPanel, BoxLayout.Y_AXIS));
        shaderPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        shaderPanel.add(Box.createVerticalStrut(8));
        JPanel shaderRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        shaderRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        shaderRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        shaderRow.add(new JLabel("Shader:"));
        cboShader = new JComboBox<>(new String[]{"None"});
        cboShader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        cboShader.addActionListener(e -> applyShaderChange());
        shaderRow.add(cboShader);
        shaderPanel.add(shaderRow);
        shaderPanel.setVisible(false);
        propertiesForm.add(shaderPanel);

        sceneShaderPanel = new JPanel();
        sceneShaderPanel.setLayout(new BoxLayout(sceneShaderPanel, BoxLayout.Y_AXIS));
        sceneShaderPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sceneShaderPanel.add(Box.createVerticalStrut(8));
        JPanel sceneShaderRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        sceneShaderRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        sceneShaderRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        sceneShaderRow.add(new JLabel("Environment Shader:"));
        cboSceneShader = new JComboBox<>(new String[]{"None"});
        cboSceneShader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        cboSceneShader.addActionListener(e -> applySceneShaderChange());
        sceneShaderRow.add(cboSceneShader);
        sceneShaderPanel.add(sceneShaderRow);
        sceneShaderPanel.setVisible(false);
        propertiesForm.add(sceneShaderPanel);

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

        // Path properties panel (PATH type only)
        pathPropertiesPanel = new JPanel();
        pathPropertiesPanel.setLayout(new BoxLayout(pathPropertiesPanel, BoxLayout.Y_AXIS));
        pathPropertiesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        pathPropertiesPanel.add(Box.createVerticalStrut(8));
        JLabel lblPath = new JLabel("Path:");
        lblPath.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblPath.setFont(lblPath.getFont().deriveFont(Font.BOLD));
        pathPropertiesPanel.add(lblPath);

        lblPathPointCount = new JLabel("Points: 0");
        lblPathPointCount.setAlignmentX(Component.LEFT_ALIGNMENT);
        pathPropertiesPanel.add(lblPathPointCount);

        JPanel pathSubdivRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        pathSubdivRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        pathSubdivRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        spnPathSubdivisions = new JSpinner(new SpinnerNumberModel(20, 1, 100, 1));
        spnPathSubdivisions.setPreferredSize(new Dimension(60, 24));
        pathSubdivRow.add(new JLabel("Subdivisions:"));
        pathSubdivRow.add(spnPathSubdivisions);
        pathPropertiesPanel.add(pathSubdivRow);

        spnPathSubdivisions.addChangeListener(e -> applyPathSubdivisionsChange());

        chkPathClosed = new JCheckBox("Closed Loop");
        chkPathClosed.setAlignmentX(Component.LEFT_ALIGNMENT);
        chkPathClosed.addActionListener(e -> applyPathClosedChange());
        pathPropertiesPanel.add(chkPathClosed);

        pathPropertiesPanel.add(Box.createVerticalStrut(4));

        JPanel pathButtonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        pathButtonRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        pathButtonRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        JButton btnAutoSmooth = new JButton("Auto Smooth");
        btnAutoSmooth.setToolTipText("Auto-smooth all tangent handles");
        btnAutoSmooth.addActionListener(e -> autoSmoothPath());
        pathButtonRow.add(btnAutoSmooth);
        pathPropertiesPanel.add(pathButtonRow);

        pathPropertiesPanel.setVisible(false);
        propertiesForm.add(pathPropertiesPanel);

        cinematicTrackPanel = new JPanel();
        cinematicTrackPanel.setLayout(new BoxLayout(cinematicTrackPanel, BoxLayout.Y_AXIS));
        cinematicTrackPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        cinematicTrackPanel.add(Box.createVerticalStrut(8));
        JLabel lblCinematicTrack = new JLabel("Cinematic Track:");
        lblCinematicTrack.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblCinematicTrack.setFont(lblCinematicTrack.getFont().deriveFont(Font.BOLD));
        cinematicTrackPanel.add(lblCinematicTrack);

        JPanel radiusXRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        radiusXRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        radiusXRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        spnCinematicRadiusX = new JSpinner(new SpinnerNumberModel(2.5, 0.1, 9999.0, 0.1));
        spnCinematicRadiusX.setPreferredSize(new Dimension(80, 24));
        radiusXRow.add(new JLabel("Radius X:"));
        radiusXRow.add(spnCinematicRadiusX);
        cinematicTrackPanel.add(radiusXRow);

        JPanel radiusZRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        radiusZRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        radiusZRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        spnCinematicRadiusZ = new JSpinner(new SpinnerNumberModel(2.5, 0.1, 9999.0, 0.1));
        spnCinematicRadiusZ.setPreferredSize(new Dimension(80, 24));
        radiusZRow.add(new JLabel("Radius Z:"));
        radiusZRow.add(spnCinematicRadiusZ);
        cinematicTrackPanel.add(radiusZRow);

        JPanel previewSpeedRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        previewSpeedRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        previewSpeedRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        spnCinematicPreviewSpeed = new JSpinner(new SpinnerNumberModel(30.0, 0.1, 9999.0, 1.0));
        spnCinematicPreviewSpeed.setPreferredSize(new Dimension(80, 24));
        previewSpeedRow.add(new JLabel("Preview Speed:"));
        previewSpeedRow.add(spnCinematicPreviewSpeed);
        cinematicTrackPanel.add(previewSpeedRow);

        lblCinematicSelection = new JLabel("Selected Range: none");
        lblCinematicSelection.setAlignmentX(Component.LEFT_ALIGNMENT);
        cinematicTrackPanel.add(lblCinematicSelection);

        JPanel cinematicButtonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        cinematicButtonRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        cinematicButtonRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        JButton btnAddRangeToStack = new JButton("Add Range");
        btnAddRangeToStack.addActionListener(e -> addSelectedCinematicRange());
        JButton btnClearRange = new JButton("Clear Range");
        btnClearRange.addActionListener(e -> clearSelectedCinematicRange());
        JButton btnPlayTrack = new JButton("Play");
        btnPlayTrack.addActionListener(e -> playSelectedCinematicTrack());
        JButton btnStopTrack = new JButton("Stop");
        btnStopTrack.addActionListener(e -> stopCinematicPlayback());
        cinematicButtonRow.add(btnAddRangeToStack);
        cinematicButtonRow.add(btnClearRange);
        cinematicButtonRow.add(btnPlayTrack);
        cinematicButtonRow.add(btnStopTrack);
        cinematicTrackPanel.add(cinematicButtonRow);

        spnCinematicRadiusX.addChangeListener(e -> applyCinematicTrackChange());
        spnCinematicRadiusZ.addChangeListener(e -> applyCinematicTrackChange());
        spnCinematicPreviewSpeed.addChangeListener(e -> applyCinematicTrackChange());

        cinematicTrackPanel.setVisible(false);
        propertiesForm.add(cinematicTrackPanel);

        cinematicRigPanel = new JPanel();
        cinematicRigPanel.setLayout(new BoxLayout(cinematicRigPanel, BoxLayout.Y_AXIS));
        cinematicRigPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        cinematicRigPanel.add(Box.createVerticalStrut(8));
        JLabel lblRig = new JLabel("Cinematic Stack:");
        lblRig.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblRig.setFont(lblRig.getFont().deriveFont(Font.BOLD));
        cinematicRigPanel.add(lblRig);

        JPanel rigTopButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        rigTopButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton btnAddTrackToRig = new JButton("Add Rail");
        btnAddTrackToRig.setToolTipText("Add a new cinematic circle/ellipse rail to this rig");
        btnAddTrackToRig.addActionListener(e -> addTrackToSelectedRig());
        rigTopButtons.add(btnAddTrackToRig);
        cinematicRigPanel.add(rigTopButtons);

        JPanel runtimeIdRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        runtimeIdRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        runtimeIdRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        runtimeIdRow.add(new JLabel("Runtime ID:"));
        txtCinematicRuntimeId = new JTextField(18);
        txtCinematicRuntimeId.setToolTipText("Use this id in SceneMax code: my_camera=>cinematic.camera.<runtime_id>");
        txtCinematicRuntimeId.addActionListener(e -> applyCinematicRigRuntimeIdChange());
        txtCinematicRuntimeId.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                applyCinematicRigRuntimeIdChange();
            }
        });
        runtimeIdRow.add(txtCinematicRuntimeId);
        cinematicRigPanel.add(runtimeIdRow);

        JPanel targetRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        targetRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        targetRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        targetRow.add(new JLabel("Look At Target:"));
        cboCinematicTarget = new JComboBox<>(new String[]{"None"});
        cboCinematicTarget.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        cboCinematicTarget.addActionListener(e -> applyCinematicRigTargetChange());
        targetRow.add(cboCinematicTarget);
        cinematicRigPanel.add(targetRow);

        JPanel targetOffsetRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        targetOffsetRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        targetOffsetRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        spnCinematicTargetOffsetX = new JSpinner(new SpinnerNumberModel(0.0, -9999.0, 9999.0, 0.1));
        spnCinematicTargetOffsetY = new JSpinner(new SpinnerNumberModel(1.5, -9999.0, 9999.0, 0.1));
        spnCinematicTargetOffsetZ = new JSpinner(new SpinnerNumberModel(0.0, -9999.0, 9999.0, 0.1));
        targetOffsetRow.add(new JLabel("Target Offset:"));
        targetOffsetRow.add(new JLabel("X:"));
        targetOffsetRow.add(spnCinematicTargetOffsetX);
        targetOffsetRow.add(new JLabel("Y:"));
        targetOffsetRow.add(spnCinematicTargetOffsetY);
        targetOffsetRow.add(new JLabel("Z:"));
        targetOffsetRow.add(spnCinematicTargetOffsetZ);
        cinematicRigPanel.add(targetOffsetRow);

        JPanel runtimePosHintRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        runtimePosHintRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        runtimePosHintRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        runtimePosHintRow.add(new JLabel("Runtime Pos Hint:"));
        txtCinematicRuntimePosHint = new JTextField(20);
        txtCinematicRuntimePosHint.setEditable(false);
        txtCinematicRuntimePosHint.setToolTipText("Suggested relative runtime start position, for example: player1 forward 10.5 up 3.1");
        runtimePosHintRow.add(txtCinematicRuntimePosHint);
        cinematicRigPanel.add(runtimePosHintRow);

        JPanel easeInRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        easeInRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        easeInRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        easeInRow.add(new JLabel("Ease In:"));
        cboCinematicEaseIn = new JComboBox<>(buildCinematicEasingOptions());
        cboCinematicEaseIn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        cboCinematicEaseIn.addActionListener(e -> applyCinematicRigEasingChange());
        easeInRow.add(cboCinematicEaseIn);
        cinematicRigPanel.add(easeInRow);

        JPanel easeOutRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        easeOutRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        easeOutRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        easeOutRow.add(new JLabel("Ease Out:"));
        cboCinematicEaseOut = new JComboBox<>(buildCinematicEasingOptions());
        cboCinematicEaseOut.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        cboCinematicEaseOut.addActionListener(e -> applyCinematicRigEasingChange());
        easeOutRow.add(cboCinematicEaseOut);
        cinematicRigPanel.add(easeOutRow);

        JPanel previewDurationRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        previewDurationRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        previewDurationRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        previewDurationRow.add(new JLabel("Preview Duration:"));
        spnCinematicPreviewDuration = new JSpinner(new SpinnerNumberModel(10.0, 0.1, 9999.0, 0.5));
        spnCinematicPreviewDuration.setPreferredSize(new Dimension(80, 24));
        previewDurationRow.add(spnCinematicPreviewDuration);
        previewDurationRow.add(new JLabel("seconds"));
        cinematicRigPanel.add(previewDurationRow);

        cinematicStackTableModel = new CinematicSegmentTableModel();
        cinematicStackTable = new JTable(cinematicStackTableModel);
        cinematicStackTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        cinematicStackTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        JScrollPane cinematicStackScroll = new JScrollPane(cinematicStackTable);
        cinematicStackScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        cinematicStackScroll.setPreferredSize(new Dimension(200, 120));
        cinematicRigPanel.add(cinematicStackScroll);

        JPanel stackButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        stackButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton btnRemoveSegment = new JButton("Remove Selected");
        btnRemoveSegment.addActionListener(e -> removeSelectedCinematicSegment());
        JButton btnPlayCinematic = new JButton("Play");
        btnPlayCinematic.addActionListener(e -> playSelectedCinematicRig());
        JButton btnPlaySelectedSegment = new JButton("Play Selected Track");
        btnPlaySelectedSegment.addActionListener(e -> playSelectedCinematicSegment());
        JButton btnStopCinematic = new JButton("Stop");
        btnStopCinematic.addActionListener(e -> stopCinematicPlayback());
        stackButtons.add(btnPlayCinematic);
        stackButtons.add(btnPlaySelectedSegment);
        stackButtons.add(btnStopCinematic);
        stackButtons.add(btnRemoveSegment);
        cinematicRigPanel.add(stackButtons);

        cinematicStackTable.getSelectionModel().addListSelectionListener(e -> onCinematicSegmentSelectionChanged());
        spnCinematicPreviewDuration.addChangeListener(e -> applyCinematicRigPreviewDurationChange());
        spnCinematicTargetOffsetX.addChangeListener(e -> applyCinematicRigTargetOffsetChange());
        spnCinematicTargetOffsetY.addChangeListener(e -> applyCinematicRigTargetOffsetChange());
        spnCinematicTargetOffsetZ.addChangeListener(e -> applyCinematicRigTargetOffsetChange());

        cinematicRigPanel.setVisible(false);
        propertiesForm.add(cinematicRigPanel);

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

    private void showCreateCinematicRigDialog() {
        if (app == null) return;

        JComboBox<CinematicPresetOption> cboPreset = new JComboBox<>(new CinematicPresetOption[]{
                new CinematicPresetOption("empty", "Empty Rig - start from scratch"),
                new CinematicPresetOption("football_flyover", "Football Flyover - high arc then drop toward the field"),
                new CinematicPresetOption("orbit_push_in", "Orbit Push-In - wide orbit resolving into a close orbit"),
                new CinematicPresetOption("sideline_sweep", "Sideline Sweep - long low-angle stadium-style pass")
        });
        cboPreset.setPreferredSize(new Dimension(340, 26));

        List<DesignerEntity> targetCandidates = app.getCinematicTargetCandidates();
        List<DesignerEntity> targetValues = new ArrayList<>();
        DefaultComboBoxModel<String> targetModel = new DefaultComboBoxModel<>();
        targetModel.addElement("None");
        for (DesignerEntity entity : targetCandidates) {
            targetModel.addElement(entity.getName());
            targetValues.add(entity);
        }
        JComboBox<String> cboTarget = new JComboBox<>(targetModel);
        cboTarget.setPreferredSize(new Dimension(340, 26));

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Preset Rig:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(cboPreset, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Look At Target:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(cboTarget, gbc);

        int result = JOptionPane.showConfirmDialog(this, panel,
                "Create Cinematic Rig", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            CinematicPresetOption preset = (CinematicPresetOption) cboPreset.getSelectedItem();
            int targetIndex = cboTarget.getSelectedIndex();
            final String presetId = preset != null ? preset.id : "empty";
            final String targetId;
            final String targetName;
            if (targetIndex > 0 && targetIndex - 1 < targetValues.size()) {
                DesignerEntity target = targetValues.get(targetIndex - 1);
                targetId = target.getId();
                targetName = target.getName();
            } else {
                targetId = "";
                targetName = "";
            }
            app.enqueue(() -> {
                app.createCinematicRigWithPreset(presetId, targetId, targetName);
                return null;
            });
        }
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
        JCheckBox chkStatic = new JCheckBox("Static");
        JCheckBox chkDynamic = new JCheckBox("Dynamic");
        cmbModels.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof String) {
                    String name = (String) value;
                    String label = name;
                    if (app.isModelVehicle(name)) {
                        label += "  (vehicle)";
                    }
                    if (app.isModelStatic(name)) {
                        label += "  (static)";
                    }
                    setText(label);
                }
                return this;
            }
        });
        cmbModels.addActionListener(e -> {
            String selected = (String) cmbModels.getSelectedItem();
            if (selected != null && app.isModelStatic(selected)) {
                chkStatic.setSelected(true);
                chkDynamic.setSelected(false);
            }
        });
        chkStatic.addActionListener(e -> { if (chkStatic.isSelected()) chkDynamic.setSelected(false); });
        chkDynamic.addActionListener(e -> { if (chkDynamic.isSelected()) chkStatic.setSelected(false); });
        // Auto-check static for the initially selected model
        String initialModel = (String) cmbModels.getSelectedItem();
        if (initialModel != null && app.isModelStatic(initialModel)) {
            chkStatic.setSelected(true);
        }

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
                    DesignerEntity effective = entity;
                    if (effective == null) {
                        DesignerEntity treeEntity = getSelectedTreeEntity();
                    if (treeEntity != null && (treeEntity.getType() == DesignerEntityType.SECTION
                                || treeEntity.getType() == DesignerEntityType.CINEMATIC_RIG)) {
                            effective = treeEntity;
                        }
                    }
                    if (app != null) {
                        final DesignerEntity cinematicTarget = effective;
                        if (effective != null && (effective.getType() == DesignerEntityType.CINEMATIC_TRACK
                                || effective.getType() == DesignerEntityType.CINEMATIC_RIG)) {
                            app.enqueue(() -> {
                                app.setCinematicAuthoringTarget(cinematicTarget);
                                return null;
                            });
                        } else {
                            app.enqueue(() -> {
                                app.setCinematicAuthoringTarget(null);
                                return null;
                            });
                        }
                    }
                    updatePropertiesPanel(effective);
                    selectEntityInTree(effective);
                    // Hide code editor when a 3D entity is selected via viewport
                    if (effective != null && effective.getType() != DesignerEntityType.CODE) {
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

            @Override
            public void onCinematicRuntimeHintChanged(String hintText) {
                SwingUtilities.invokeLater(() -> {
                    if (txtCinematicRuntimePosHint != null) {
                        txtCinematicRuntimePosHint.setText(hintText != null ? hintText : "");
                    }
                });
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

        try {
            sharedApp.enqueue(() -> {
                sharedApp.flushPendingDocumentPersistNow();
                return null;
            }).get();
        } catch (Exception ignored) {
            // Best-effort flush only; don't block tab switching on transient render-thread issues.
        }

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

        if (activeDesignerPanel == this) {
            activeDesignerPanel = null;
        }
    }

    public static void deactivateActiveDesignerPanel() {
        if (activeDesignerPanel != null) {
            activeDesignerPanel.deactivatePanel();
        }
    }

    public void reloadFromDisk() {
        if (sharedApp == null) {
            return;
        }

        final boolean restoreCamera = hasSavedCameraState;
        final float dist = savedCamDistance;
        final float yaw = savedCamYaw;
        final float pitch = savedCamPitch;
        final Vector3f target = savedCamTarget != null ? savedCamTarget.clone() : new Vector3f(0, 0, 0);

        sharedApp.enqueue(() -> {
            sharedApp.switchDocument(designerFile, projectPath);
            if (restoreCamera) {
                sharedApp.setOrbitCameraState(dist, yaw, pitch, target);
            }
            return null;
        });
        SwingUtilities.invokeLater(this::refreshSceneTree);
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

    /**
     * Clears the shared designer app's in-memory scene/document state without
     * disposing this panel, so a subsequent reload-from-disk starts clean.
     */
    public void discardEditorState() {
        if (sharedApp == null) {
            return;
        }
        sharedApp.enqueue(() -> {
            sharedApp.clearDocument();
            return null;
        });
    }

    // --- Scene tree ---

    public void refreshSceneTree() {
        String selectedEntityId = null;
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) sceneTree.getLastSelectedPathComponent();
        if (selectedNode != null && selectedNode.getUserObject() instanceof EntityTreeNode) {
            selectedEntityId = ((EntityTreeNode) selectedNode.getUserObject()).entity.getId();
        }

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
                        + ((e.getType() == DesignerEntityType.SECTION || e.getType() == DesignerEntityType.CINEMATIC_RIG)
                        ? " children=" + e.getChildren().size() : ""));
            }
            buildTreeNodes(sceneTreeRoot, snapshot);
        }
        sceneTreeModel.reload();
        // Expand root
        sceneTree.expandRow(0);
        // Restore expanded sections
        restoreExpandedSections(sceneTreeRoot, expandedIds);

        if (selectedEntityId != null) {
            DefaultMutableTreeNode restored = findTreeNodeById(sceneTreeRoot, selectedEntityId);
            if (restored != null) {
                TreePath restoredPath = new TreePath(restored.getPath());
                sceneTree.setSelectionPath(restoredPath);
                sceneTree.scrollPathToVisible(restoredPath);
            } else {
                sceneTree.clearSelection();
                updatePropertiesPanel(null);
            }
        } else {
            sceneTree.clearSelection();
            updatePropertiesPanel(null);
        }
    }

    private void buildTreeNodes(DefaultMutableTreeNode parent, List<DesignerEntity> entities) {
        for (DesignerEntity entity : entities) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(new EntityTreeNode(entity, entity.getName()));
            parent.add(node);
            if (entity.getType() == DesignerEntityType.SECTION || entity.getType() == DesignerEntityType.CINEMATIC_RIG) {
                buildTreeNodes(node, entity.getChildren());
            }
        }
    }

    private void restoreExpandedSections(DefaultMutableTreeNode parent, java.util.Set<String> expandedIds) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            if (child.getUserObject() instanceof EntityTreeNode) {
                EntityTreeNode etn = (EntityTreeNode) child.getUserObject();
                if (etn.entity.getType() == DesignerEntityType.SECTION || etn.entity.getType() == DesignerEntityType.CINEMATIC_RIG) {
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
        if (selected == sceneTreeRoot) {
            if (app != null) {
                app.enqueue(() -> {
                    app.getSelectionManager().deselect();
                    app.setCinematicAuthoringTarget(null);
                    return null;
                });
            }
            hideCodeEditor();
            updateScenePropertiesPanel();
            return;
        }
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
                    app.setCinematicAuthoringTarget(null);
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
                    app.setCinematicAuthoringTarget(null);
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
                app.setCinematicAuthoringTarget(
                        (etn.entity.getType() == DesignerEntityType.CINEMATIC_TRACK
                                || etn.entity.getType() == DesignerEntityType.CINEMATIC_RIG) ? etn.entity : null);
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
                if (etn.entity.getType() == DesignerEntityType.SECTION || etn.entity.getType() == DesignerEntityType.CINEMATIC_RIG) {
                    DefaultMutableTreeNode found = findTreeNode(child, entity);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    private DefaultMutableTreeNode findTreeNodeById(DefaultMutableTreeNode parent, String entityId) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            if (child.getUserObject() instanceof EntityTreeNode) {
                EntityTreeNode etn = (EntityTreeNode) child.getUserObject();
                if (etn.entity != null && entityId.equals(etn.entity.getId())) {
                    return child;
                }
                if (etn.entity.getType() == DesignerEntityType.SECTION || etn.entity.getType() == DesignerEntityType.CINEMATIC_RIG) {
                    DefaultMutableTreeNode found = findTreeNodeById(child, entityId);
                    if (found != null) {
                        return found;
                    }
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

        // "Add Cylinder" - inserts after the selected item (or as last child in a section)
        JMenuItem addCylinderItem = new JMenuItem("Add Cylinder");
        addCylinderItem.addActionListener(ev -> {
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
                app.enqueue(() -> { app.addDefaultCylinder(fTargetList, fInsertIdx); return null; });
            }
        });
        menu.add(addCylinderItem);

        // "Add Hollow Cylinder" - inserts after the selected item (or as last child in a section)
        JMenuItem addHollowCylinderItem = new JMenuItem("Add Hollow Cylinder");
        addHollowCylinderItem.addActionListener(ev -> {
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
                app.enqueue(() -> { app.addDefaultHollowCylinder(fTargetList, fInsertIdx); return null; });
            }
        });
        menu.add(addHollowCylinderItem);

        // "Add Quad" - inserts after the selected item (or as last child in a section)
        JMenuItem addQuadItem = new JMenuItem("Add Quad");
        addQuadItem.addActionListener(ev -> {
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
                app.enqueue(() -> { app.addDefaultQuad(fTargetList, fInsertIdx); return null; });
            }
        });
        menu.add(addQuadItem);

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

    private void repositionReferenceOverlay(JLayeredPane layered) {
        if (referenceImageOverlay == null) {
            return;
        }
        Point canvasLoc = SwingUtilities.convertPoint(canvasContainer, 0, 0, layered);
        referenceImageOverlay.setBounds(
                canvasLoc.x,
                canvasLoc.y,
                Math.max(0, canvasContainer.getWidth()),
                Math.max(0, canvasContainer.getHeight()));
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
                wedgePanel.setVisible(false);
                radiusPanel.setVisible(false);
                cylinderPanel.setVisible(false);
                hollowCylinderPanel.setVisible(false);
                stairsPanel.setVisible(false);
                archPanel.setVisible(false);
                staticColliderPanel.setVisible(false);
                materialPanel.setVisible(false);
                hiddenPanel.setVisible(false);
                shaderPanel.setVisible(false);
                sceneShaderPanel.setVisible(false);
                shadowModePanel.setVisible(false);
                jointMappingPanel.setVisible(false);
                pathPropertiesPanel.setVisible(false);
                cinematicTrackPanel.setVisible(false);
                cinematicRigPanel.setVisible(false);
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

            if (entity.getType() == DesignerEntityType.WEDGE) {
                spnWedgeWidth.setValue((double) entity.getWedgeWidth());
                spnWedgeHeight.setValue((double) entity.getWedgeHeight());
                spnWedgeDepth.setValue((double) entity.getWedgeDepth());
                wedgePanel.setVisible(true);
            } else {
                wedgePanel.setVisible(false);
            }

            if (entity.getType() == DesignerEntityType.SPHERE) {
                spnRadius.setValue((double) entity.getRadius());
                radiusPanel.setVisible(true);
            } else {
                radiusPanel.setVisible(false);
            }

            if (entity.getType() == DesignerEntityType.CYLINDER || entity.getType() == DesignerEntityType.CONE) {
                spnRadiusTop.setValue((double) entity.getRadiusTop());
                spnRadiusBottom.setValue((double) entity.getRadiusBottom());
                spnCylinderHeight.setValue((double) entity.getHeight());
                cylinderPanel.setVisible(true);
            } else {
                cylinderPanel.setVisible(false);
            }

            if (entity.getType() == DesignerEntityType.HOLLOW_CYLINDER) {
                spnHcRadiusTop.setValue((double) entity.getRadiusTop());
                spnHcRadiusBottom.setValue((double) entity.getRadiusBottom());
                spnHcInnerRadiusTop.setValue((double) entity.getInnerRadiusTop());
                spnHcInnerRadiusBottom.setValue((double) entity.getInnerRadiusBottom());
                spnHcHeight.setValue((double) entity.getHeight());
                hollowCylinderPanel.setVisible(true);
            } else {
                hollowCylinderPanel.setVisible(false);
            }

            if (entity.getType() == DesignerEntityType.STAIRS) {
                spnStairsWidth.setValue((double) entity.getStairsWidth());
                spnStairsStepHeight.setValue((double) entity.getStairsStepHeight());
                spnStairsStepDepth.setValue((double) entity.getStairsStepDepth());
                spnStairsStepCount.setValue(entity.getStairsStepCount());
                stairsPanel.setVisible(true);
            } else {
                stairsPanel.setVisible(false);
            }

            if (entity.getType() == DesignerEntityType.ARCH) {
                spnArchWidth.setValue((double) entity.getArchWidth());
                spnArchHeight.setValue((double) entity.getArchHeight());
                spnArchDepth.setValue((double) entity.getArchDepth());
                spnArchThickness.setValue((double) entity.getArchThickness());
                spnArchSegments.setValue(entity.getArchSegments());
                archPanel.setVisible(true);
            } else {
                archPanel.setVisible(false);
            }

            if (entity.getType() == DesignerEntityType.BOX || entity.getType() == DesignerEntityType.SPHERE
                    || entity.getType() == DesignerEntityType.WEDGE
                    || entity.getType() == DesignerEntityType.CYLINDER || entity.getType() == DesignerEntityType.CONE
                    || entity.getType() == DesignerEntityType.HOLLOW_CYLINDER
                    || entity.getType() == DesignerEntityType.QUAD
                    || entity.getType() == DesignerEntityType.STAIRS
                    || entity.getType() == DesignerEntityType.ARCH) {
                chkStaticEntity.setSelected(entity.isStaticEntity());
                chkColliderEntity.setSelected(entity.isColliderEntity());
                staticColliderPanel.setVisible(true);

                refreshMaterialChoices(entity.getMaterial());
                materialPanel.setVisible(true);
            } else {
                staticColliderPanel.setVisible(false);
                materialPanel.setVisible(false);
            }

            if (entity.getType() == DesignerEntityType.BOX || entity.getType() == DesignerEntityType.SPHERE
                    || entity.getType() == DesignerEntityType.WEDGE
                    || entity.getType() == DesignerEntityType.CYLINDER || entity.getType() == DesignerEntityType.CONE
                    || entity.getType() == DesignerEntityType.HOLLOW_CYLINDER
                    || entity.getType() == DesignerEntityType.QUAD
                    || entity.getType() == DesignerEntityType.STAIRS
                    || entity.getType() == DesignerEntityType.ARCH
                    || entity.getType() == DesignerEntityType.MODEL) {
                chkHidden.setSelected(entity.isHidden());
                hiddenPanel.setVisible(true);

                refreshShaderChoices(entity.getShader());
                shaderPanel.setVisible(true);

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
                shaderPanel.setVisible(false);
                shadowModePanel.setVisible(false);
                jointMappingPanel.setVisible(false);
            }

            sceneShaderPanel.setVisible(false);

            // PATH properties
            if (entity.getType() == DesignerEntityType.PATH && entity.getBezierPath() != null) {
                BezierPath path = entity.getBezierPath();
                lblPathPointCount.setText("Points: " + path.getPointCount());
                spnPathSubdivisions.setValue(path.getSubdivisions());
                chkPathClosed.setSelected(path.isClosed());
                pathPropertiesPanel.setVisible(true);
            } else {
                pathPropertiesPanel.setVisible(false);
            }

            if (entity.getType() == DesignerEntityType.CINEMATIC_TRACK && entity.getCinematicTrackData() != null) {
                CinematicTrackData data = entity.getCinematicTrackData();
                spnCinematicRadiusX.setValue((double) data.getRadiusX());
                spnCinematicRadiusZ.setValue((double) data.getRadiusZ());
                spnCinematicPreviewSpeed.setValue((double) data.getPreviewSpeed());
                if (data.getSelectedStartAnchor() >= 0 && data.getSelectedEndAnchor() >= 0) {
                    lblCinematicSelection.setText("Selected Range: "
                            + data.getSelectedStartAnchor() + " - " + data.getSelectedEndAnchor());
                } else if (data.getSelectedStartAnchor() >= 0) {
                    lblCinematicSelection.setText("Selected Range: start " + data.getSelectedStartAnchor());
                } else {
                    lblCinematicSelection.setText("Selected Range: none");
                }
                cinematicTrackPanel.setVisible(true);
            } else {
                cinematicTrackPanel.setVisible(false);
            }

            if (entity.getType() == DesignerEntityType.CINEMATIC_RIG) {
                txtCinematicRuntimeId.setText(entity.getCinematicRuntimeId());
                refreshCinematicTargetChoices(entity.getCinematicTargetEntityId(), entity.getCinematicTargetEntityName());
                Vector3f targetOffset = entity.getCinematicTargetOffset();
                spnCinematicTargetOffsetX.setValue((double) targetOffset.x);
                spnCinematicTargetOffsetY.setValue((double) targetOffset.y);
                spnCinematicTargetOffsetZ.setValue((double) targetOffset.z);
                spnCinematicPreviewDuration.setValue((double) entity.getCinematicPreviewDuration());
                selectCinematicEasingOption(cboCinematicEaseIn, entity.getCinematicEaseIn());
                selectCinematicEasingOption(cboCinematicEaseOut, entity.getCinematicEaseOut());
                cinematicStackTableModel.fireTableDataChanged();
                if (!entity.getCinematicSegments().isEmpty()) {
                    if (cinematicStackTable.getSelectedRow() < 0
                            || cinematicStackTable.getSelectedRow() >= entity.getCinematicSegments().size()) {
                        cinematicStackTable.setRowSelectionInterval(0, 0);
                    }
                } else {
                    cinematicStackTable.clearSelection();
                }
                if (txtCinematicRuntimePosHint != null && (entity.getCinematicTargetEntityId() == null
                        || entity.getCinematicTargetEntityId().isBlank())) {
                    txtCinematicRuntimePosHint.setText("");
                }
                cinematicRigPanel.setVisible(true);
            } else {
                if (txtCinematicRuntimePosHint != null) {
                    txtCinematicRuntimePosHint.setText("");
                }
                cinematicRigPanel.setVisible(false);
            }

            // Camera entities don't need scale
            if (entity.getType() == DesignerEntityType.CAMERA
                    || entity.getType() == DesignerEntityType.CINEMATIC_RIG) {
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

    private void updateScenePropertiesPanel() {
        updatingProperties = true;
        try {
            txtName.setText("Scene");
            lblType.setText("Type: Scene");
            clearSpinners();

            sizeFieldsPanel.setVisible(false);
            wedgePanel.setVisible(false);
            radiusPanel.setVisible(false);
            cylinderPanel.setVisible(false);
            hollowCylinderPanel.setVisible(false);
            stairsPanel.setVisible(false);
            archPanel.setVisible(false);
            staticColliderPanel.setVisible(false);
            materialPanel.setVisible(false);
            hiddenPanel.setVisible(false);
            shaderPanel.setVisible(false);
            shadowModePanel.setVisible(false);
            jointMappingPanel.setVisible(false);
            pathPropertiesPanel.setVisible(false);
            cinematicTrackPanel.setVisible(false);
            if (txtCinematicRuntimeId != null) {
                txtCinematicRuntimeId.setText("");
            }
            if (txtCinematicRuntimePosHint != null) {
                txtCinematicRuntimePosHint.setText("");
            }
            cinematicRigPanel.setVisible(false);

            DesignerDocument doc = app != null ? app.getDocument() : null;
            refreshSceneShaderChoices(doc != null ? doc.getSceneEnvironmentShader() : "");
            sceneShaderPanel.setVisible(true);

            spnScaleX.setEnabled(false);
            spnScaleY.setEnabled(false);
            spnScaleZ.setEnabled(false);
        } finally {
            updatingProperties = false;
        }
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
            Quaternion q = new Quaternion();
            q.fromAngles(rx, ry, rz);
            app.applyEntityTransform(sel, new Vector3f(px, py, pz), q, new Vector3f(sx, sy, sz));
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

    private void applyWedgeChange() {
        if (updatingProperties || app == null) return;
        DesignerEntity sel = app.getSelectionManager().getSelected();
        if (sel == null || sel.getType() != DesignerEntityType.WEDGE) return;

        float width = ((Number) spnWedgeWidth.getValue()).floatValue();
        float height = ((Number) spnWedgeHeight.getValue()).floatValue();
        float depth = ((Number) spnWedgeDepth.getValue()).floatValue();

        app.enqueue(() -> {
            sel.setWedgeWidth(width);
            sel.setWedgeHeight(height);
            sel.setWedgeDepth(depth);
            app.recreateEntity(sel, sel.isStaticEntity(), sel.isColliderEntity());
            app.markDocumentDirty();
            return null;
        });
    }

    private void applyCylinderChange() {
        if (updatingProperties || app == null) return;
        DesignerEntity sel = app.getSelectionManager().getSelected();
        if (sel == null || (sel.getType() != DesignerEntityType.CYLINDER && sel.getType() != DesignerEntityType.CONE)) return;

        float radiusTop = ((Number) spnRadiusTop.getValue()).floatValue();
        float radiusBottom = ((Number) spnRadiusBottom.getValue()).floatValue();
        float height = ((Number) spnCylinderHeight.getValue()).floatValue();

        app.enqueue(() -> {
            sel.setRadiusTop(radiusTop);
            sel.setRadiusBottom(radiusBottom);
            sel.setHeight(height);
            if (sel.getType() == DesignerEntityType.CONE) {
                app.recreateEntity(sel, sel.isStaticEntity(), sel.isColliderEntity());
            } else {
                app.updateCylinderMesh(sel);
            }
            app.markDocumentDirty();
            return null;
        });
    }

    private void applyHollowCylinderChange() {
        if (updatingProperties || app == null) return;
        DesignerEntity sel = app.getSelectionManager().getSelected();
        if (sel == null || sel.getType() != DesignerEntityType.HOLLOW_CYLINDER) return;

        float rTop = ((Number) spnHcRadiusTop.getValue()).floatValue();
        float rBottom = ((Number) spnHcRadiusBottom.getValue()).floatValue();
        float irTop = ((Number) spnHcInnerRadiusTop.getValue()).floatValue();
        float irBottom = ((Number) spnHcInnerRadiusBottom.getValue()).floatValue();
        float h = ((Number) spnHcHeight.getValue()).floatValue();

        app.enqueue(() -> {
            sel.setRadiusTop(rTop);
            sel.setRadiusBottom(rBottom);
            sel.setInnerRadiusTop(irTop);
            sel.setInnerRadiusBottom(irBottom);
            sel.setHeight(h);
            app.updateHollowCylinderMesh(sel);
            app.markDocumentDirty();
            return null;
        });
    }

    private void applyStairsChange() {
        if (updatingProperties || app == null) return;
        DesignerEntity sel = app.getSelectionManager().getSelected();
        if (sel == null || sel.getType() != DesignerEntityType.STAIRS) return;

        float width = ((Number) spnStairsWidth.getValue()).floatValue();
        float stepHeight = ((Number) spnStairsStepHeight.getValue()).floatValue();
        float stepDepth = ((Number) spnStairsStepDepth.getValue()).floatValue();
        int stepCount = ((Number) spnStairsStepCount.getValue()).intValue();

        app.enqueue(() -> {
            sel.setStairsWidth(width);
            sel.setStairsStepHeight(stepHeight);
            sel.setStairsStepDepth(stepDepth);
            sel.setStairsStepCount(stepCount);
            app.recreateEntity(sel, sel.isStaticEntity(), sel.isColliderEntity());
            app.markDocumentDirty();
            return null;
        });
    }

    private void applyArchChange() {
        if (updatingProperties || app == null) return;
        DesignerEntity sel = app.getSelectionManager().getSelected();
        if (sel == null || sel.getType() != DesignerEntityType.ARCH) return;

        float width = ((Number) spnArchWidth.getValue()).floatValue();
        float height = ((Number) spnArchHeight.getValue()).floatValue();
        float depth = ((Number) spnArchDepth.getValue()).floatValue();
        float thickness = ((Number) spnArchThickness.getValue()).floatValue();
        int segments = ((Number) spnArchSegments.getValue()).intValue();

        app.enqueue(() -> {
            sel.setArchWidth(width);
            sel.setArchHeight(height);
            sel.setArchDepth(depth);
            sel.setArchThickness(thickness);
            sel.setArchSegments(segments);
            app.recreateEntity(sel, sel.isStaticEntity(), sel.isColliderEntity());
            app.markDocumentDirty();
            return null;
        });
    }

    private void applyStaticColliderChange() {
        if (updatingProperties || app == null) return;
        DesignerEntity sel = app.getSelectionManager().getSelected();
        if (sel == null) return;
        if (sel.getType() != DesignerEntityType.BOX && sel.getType() != DesignerEntityType.SPHERE
                && sel.getType() != DesignerEntityType.WEDGE
                && sel.getType() != DesignerEntityType.CYLINDER && sel.getType() != DesignerEntityType.CONE
                && sel.getType() != DesignerEntityType.HOLLOW_CYLINDER
                && sel.getType() != DesignerEntityType.QUAD
                && sel.getType() != DesignerEntityType.STAIRS
                && sel.getType() != DesignerEntityType.ARCH) return;

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

    private void applyShaderChange() {
        if (updatingProperties || app == null) return;
        DesignerEntity sel = app.getSelectionManager().getSelected();
        if (sel == null) return;
        if (sel.getType() != DesignerEntityType.BOX && sel.getType() != DesignerEntityType.SPHERE
                && sel.getType() != DesignerEntityType.WEDGE
                && sel.getType() != DesignerEntityType.CYLINDER && sel.getType() != DesignerEntityType.CONE
                && sel.getType() != DesignerEntityType.HOLLOW_CYLINDER
                && sel.getType() != DesignerEntityType.QUAD
                && sel.getType() != DesignerEntityType.STAIRS
                && sel.getType() != DesignerEntityType.ARCH
                && sel.getType() != DesignerEntityType.MODEL) return;

        String selected = (String) cboShader.getSelectedItem();
        String shader = (selected == null || "None".equals(selected)) ? "" : selected;

        app.enqueue(() -> {
            app.applyShader(sel, shader);
            return null;
        });
    }

    private void applySceneShaderChange() {
        if (updatingProperties || app == null || app.getDocument() == null) return;

        TreePath selectionPath = sceneTree.getSelectionPath();
        if (selectionPath == null || selectionPath.getLastPathComponent() != sceneTreeRoot) {
            return;
        }

        String selected = (String) cboSceneShader.getSelectedItem();
        String shader = (selected == null || "None".equals(selected)) ? "" : selected;

        app.enqueue(() -> {
            app.applySceneEnvironmentShader(shader);
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
            if (sel.getType() == DesignerEntityType.WEDGE
                    || sel.getType() == DesignerEntityType.CONE
                    || sel.getType() == DesignerEntityType.STAIRS
                    || sel.getType() == DesignerEntityType.ARCH) {
                app.recreateEntity(sel, sel.isStaticEntity(), sel.isColliderEntity());
            }
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

    // --- Path property handlers ---

    private void applyPathSubdivisionsChange() {
        if (updatingProperties || app == null) return;
        DesignerEntity sel = app.getSelectionManager().getSelected();
        if (sel == null || sel.getType() != DesignerEntityType.PATH || sel.getBezierPath() == null) return;
        int subdivs = (int) spnPathSubdivisions.getValue();
        app.enqueue(() -> {
            sel.getBezierPath().setSubdivisions(subdivs);
            app.rebuildPathVisual(sel);
            app.markDocumentDirty();
            return null;
        });
    }

    private void applyPathClosedChange() {
        if (updatingProperties || app == null) return;
        DesignerEntity sel = app.getSelectionManager().getSelected();
        if (sel == null || sel.getType() != DesignerEntityType.PATH || sel.getBezierPath() == null) return;
        boolean closed = chkPathClosed.isSelected();
        app.enqueue(() -> {
            sel.getBezierPath().setClosed(closed);
            app.rebuildPathVisual(sel);
            app.markDocumentDirty();
            return null;
        });
    }

    private void autoSmoothPath() {
        if (app == null) return;
        DesignerEntity sel = app.getSelectionManager().getSelected();
        if (sel == null || sel.getType() != DesignerEntityType.PATH || sel.getBezierPath() == null) return;
        app.enqueue(() -> {
            sel.getBezierPath().autoSmoothAll();
            app.rebuildPathVisual(sel);
            app.markDocumentDirty();
            return null;
        });
    }

    private void applyCinematicTrackChange() {
        if (updatingProperties || app == null) return;
        DesignerEntity sel = app.getSelectionManager().getSelected();
        if (sel == null || sel.getType() != DesignerEntityType.CINEMATIC_TRACK || sel.getCinematicTrackData() == null) return;

        float radiusX = ((Number) spnCinematicRadiusX.getValue()).floatValue();
        float radiusZ = ((Number) spnCinematicRadiusZ.getValue()).floatValue();
        float previewSpeed = ((Number) spnCinematicPreviewSpeed.getValue()).floatValue();

        app.enqueue(() -> {
            sel.getCinematicTrackData().setRadiusX(radiusX);
            sel.getCinematicTrackData().setRadiusZ(radiusZ);
            sel.getCinematicTrackData().setPreviewSpeed(previewSpeed);
            app.rebuildCinematicTrackVisual(sel);
            app.markDocumentDirty();
            return null;
        });
    }

    private void addSelectedCinematicRange() {
        if (app == null) return;
        DesignerEntity sel = app.getSelectionManager().getSelected();
        if (sel == null || sel.getType() != DesignerEntityType.CINEMATIC_TRACK) return;
        app.enqueue(() -> {
            app.addSelectedRangeToCinematicStack(sel);
            return null;
        });
    }

    private void clearSelectedCinematicRange() {
        if (app == null) return;
        DesignerEntity sel = app.getSelectionManager().getSelected();
        if (sel == null || sel.getType() != DesignerEntityType.CINEMATIC_TRACK) return;
        app.enqueue(() -> {
            app.clearCinematicRangeSelection(sel);
            return null;
        });
    }

    private void removeSelectedCinematicSegment() {
        if (app == null) return;
        DesignerEntity sel = getSelectedTreeEntity();
        if (sel == null || sel.getType() != DesignerEntityType.CINEMATIC_RIG) return;
        int selectedIndex = cinematicStackTable.getSelectedRow();
        if (selectedIndex < 0) return;
        app.enqueue(() -> {
            app.removeCinematicSegment(sel, selectedIndex);
            return null;
        });
    }

    private void onCinematicSegmentSelectionChanged() {
        // Row selection should not rebuild the whole table model, otherwise
        // Swing tears down the active editor and the grid appears non-editable.
    }

    private void applyCinematicRigPreviewDurationChange() {
        if (updatingProperties || app == null) return;
        DesignerEntity sel = getSelectedTreeEntity();
        if (sel == null || sel.getType() != DesignerEntityType.CINEMATIC_RIG) return;
        float duration = ((Number) spnCinematicPreviewDuration.getValue()).floatValue();
        app.enqueue(() -> {
            sel.setCinematicPreviewDuration(duration);
            app.markDocumentDirty();
            return null;
        });
    }

    private void playSelectedCinematicRig() {
        if (app == null) return;
        DesignerEntity sel = getSelectedTreeEntity();
        if (sel == null || sel.getType() != DesignerEntityType.CINEMATIC_RIG) return;
        app.enqueue(() -> {
            app.playCinematicRig(sel);
            return null;
        });
    }

    private void playSelectedCinematicTrack() {
        if (app == null) return;
        DesignerEntity sel = getSelectedTreeEntity();
        if (sel == null || sel.getType() != DesignerEntityType.CINEMATIC_TRACK) return;
        app.enqueue(() -> {
            app.playCinematicTrack(sel);
            return null;
        });
    }

    private void playSelectedCinematicSegment() {
        if (app == null) return;
        DesignerEntity rig = getSelectedTreeEntity();
        if (rig == null || rig.getType() != DesignerEntityType.CINEMATIC_RIG) return;
        int idx = cinematicStackTable.getSelectedRow();
        if (idx < 0) return;
        app.enqueue(() -> {
            app.playCinematicSegment(rig, idx);
            return null;
        });
    }

    private void stopCinematicPlayback() {
        if (app == null) return;
        app.enqueue(() -> {
            app.stopCinematicPlayback();
            return null;
        });
    }

    private void addTrackToSelectedRig() {
        if (app == null) return;
        DesignerEntity sel = getSelectedTreeEntity();
        if (sel == null || sel.getType() != DesignerEntityType.CINEMATIC_RIG) return;
        app.enqueue(() -> {
            app.addDefaultCinematicTrack(sel);
            return null;
        });
    }


    private void refreshCinematicTargetChoices(String selectedId, String selectedName) {
        if (app == null || cboCinematicTarget == null) return;
        cinematicTargetChoices = app.getCinematicTargetCandidates();
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addElement("None");
        int selectedIndex = 0;
        for (int i = 0; i < cinematicTargetChoices.size(); i++) {
            DesignerEntity entity = cinematicTargetChoices.get(i);
            String label = entity.getName();
            model.addElement(label);
            if (selectedId != null && !selectedId.isBlank() && selectedId.equals(entity.getId())) {
                selectedIndex = i + 1;
            }
        }
        if (selectedIndex == 0 && selectedId != null && !selectedId.isBlank()
                && selectedName != null && !selectedName.isBlank()) {
            model.addElement(selectedName + " (missing)");
            selectedIndex = model.getSize() - 1;
        }
        cboCinematicTarget.setModel(model);
        cboCinematicTarget.setSelectedIndex(selectedIndex);
    }

    private void applyCinematicRigTargetChange() {
        if (updatingProperties || app == null || cboCinematicTarget == null) return;
        DesignerEntity sel = getSelectedTreeEntity();
        if (sel == null || sel.getType() != DesignerEntityType.CINEMATIC_RIG) return;
        int idx = cboCinematicTarget.getSelectedIndex();
        if (idx <= 0) {
            app.enqueue(() -> {
                app.setCinematicRigTarget(sel, "", "");
                return null;
            });
            return;
        }
        int entityIdx = idx - 1;
        if (entityIdx < 0 || entityIdx >= cinematicTargetChoices.size()) return;
        DesignerEntity target = cinematicTargetChoices.get(entityIdx);
        app.enqueue(() -> {
            app.setCinematicRigTarget(sel, target.getId(), target.getName());
            return null;
        });
    }

    private void applyCinematicRigRuntimeIdChange() {
        if (updatingProperties || app == null || txtCinematicRuntimeId == null) return;
        DesignerEntity sel = getSelectedTreeEntity();
        if (sel == null || sel.getType() != DesignerEntityType.CINEMATIC_RIG) return;
        String runtimeId = txtCinematicRuntimeId.getText();
        app.enqueue(() -> {
            app.setCinematicRigRuntimeId(sel, runtimeId);
            return null;
        });
    }

    private void applyCinematicRigTargetOffsetChange() {
        if (updatingProperties || app == null) return;
        DesignerEntity sel = getSelectedTreeEntity();
        if (sel == null || sel.getType() != DesignerEntityType.CINEMATIC_RIG) return;
        float x = ((Number) spnCinematicTargetOffsetX.getValue()).floatValue();
        float y = ((Number) spnCinematicTargetOffsetY.getValue()).floatValue();
        float z = ((Number) spnCinematicTargetOffsetZ.getValue()).floatValue();
        app.enqueue(() -> {
            app.setCinematicRigTargetOffset(sel, new Vector3f(x, y, z));
            return null;
        });
    }

    private void applyCinematicRigEasingChange() {
        if (updatingProperties || app == null || cboCinematicEaseIn == null || cboCinematicEaseOut == null) return;
        DesignerEntity sel = getSelectedTreeEntity();
        if (sel == null || sel.getType() != DesignerEntityType.CINEMATIC_RIG) return;
        EasingOption easeIn = (EasingOption) cboCinematicEaseIn.getSelectedItem();
        EasingOption easeOut = (EasingOption) cboCinematicEaseOut.getSelectedItem();
        String easeInId = easeIn != null ? easeIn.id : "linear";
        String easeOutId = easeOut != null ? easeOut.id : "linear";
        app.enqueue(() -> {
            app.setCinematicRigEasing(sel, easeInId, easeOutId);
            return null;
        });
    }

    private ComboBoxModel<EasingOption> buildCinematicEasingOptions() {
        DefaultComboBoxModel<EasingOption> model = new DefaultComboBoxModel<>();
        model.addElement(new EasingOption("linear", "Linear - constant speed, no easing"));
        model.addElement(new EasingOption("ease_in_quad", "Ease In Quad - simple acceleration"));
        model.addElement(new EasingOption("ease_out_quad", "Ease Out Quad - natural stopping"));
        model.addElement(new EasingOption("ease_in_cubic", "Ease In Cubic - stronger acceleration"));
        model.addElement(new EasingOption("ease_out_cubic", "Ease Out Cubic - smoother dramatic stop"));
        model.addElement(new EasingOption("ease_in_expo", "Ease In Expo - very fast ramp-up after a gentle start"));
        model.addElement(new EasingOption("ease_out_expo", "Ease Out Expo - dramatic deceleration into the stop"));
        model.addElement(new EasingOption("ease_in_sine", "Ease In Sine - soft cinematic acceleration"));
        model.addElement(new EasingOption("ease_out_sine", "Ease Out Sine - soft cinematic settling"));
        return model;
    }

    private void selectCinematicEasingOption(JComboBox<EasingOption> comboBox, String easingId) {
        if (comboBox == null) return;
        String targetId = easingId != null ? easingId : "linear";
        ComboBoxModel<EasingOption> model = comboBox.getModel();
        for (int i = 0; i < model.getSize(); i++) {
            EasingOption option = model.getElementAt(i);
            if (option != null && option.id.equals(targetId)) {
                comboBox.setSelectedIndex(i);
                return;
            }
        }
        comboBox.setSelectedIndex(0);
    }

    private DesignerEntity getSelectedTreeEntity() {
        DefaultMutableTreeNode selected = (DefaultMutableTreeNode) sceneTree.getLastSelectedPathComponent();
        if (selected != null && selected.getUserObject() instanceof EntityTreeNode) {
            return ((EntityTreeNode) selected.getUserObject()).entity;
        }
        return null;
    }

    private static final class EasingOption {
        final String id;
        final String label;

        EasingOption(String id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class CinematicPresetOption {
        final String id;
        final String label;

        CinematicPresetOption(String id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private void applyMaterialChange() {
        if (updatingProperties || app == null) return;
        DesignerEntity sel = app.getSelectionManager().getSelected();
        if (sel == null) return;
        if (sel.getType() != DesignerEntityType.BOX && sel.getType() != DesignerEntityType.SPHERE
                && sel.getType() != DesignerEntityType.WEDGE
                && sel.getType() != DesignerEntityType.CYLINDER && sel.getType() != DesignerEntityType.CONE
                && sel.getType() != DesignerEntityType.HOLLOW_CYLINDER
                && sel.getType() != DesignerEntityType.QUAD
                && sel.getType() != DesignerEntityType.STAIRS
                && sel.getType() != DesignerEntityType.ARCH) return;

        String material = (String) cboMaterial.getSelectedItem();
        if (material == null) material = "";
        String mat = material;

        app.enqueue(() -> {
            app.applyMaterial(sel, mat);
            return null;
        });
    }

    private void refreshMaterialChoices(String selectedMaterial) {
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addElement("");

        if (app != null) {
            for (String materialName : app.getAvailableProjectMaterialNames()) {
                model.addElement(materialName);
            }
        }

        if (selectedMaterial != null && !selectedMaterial.isBlank()) {
            boolean exists = false;
            for (int i = 0; i < model.getSize(); i++) {
                if (selectedMaterial.equals(model.getElementAt(i))) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                model.addElement(selectedMaterial);
            }
        }

        cboMaterial.setModel(model);
        cboMaterial.setSelectedItem(selectedMaterial != null ? selectedMaterial : "");
    }

    private void refreshShaderChoices(String selectedShader) {
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addElement("None");

        if (app != null) {
            for (String shaderName : app.getAvailableProjectShaderNames()) {
                model.addElement(shaderName);
            }
        }

        if (selectedShader != null && !selectedShader.isBlank()) {
            boolean exists = false;
            for (int i = 0; i < model.getSize(); i++) {
                if (selectedShader.equals(model.getElementAt(i))) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                model.addElement(selectedShader);
            }
        }

        cboShader.setModel(model);
        cboShader.setSelectedItem(selectedShader != null && !selectedShader.isBlank() ? selectedShader : "None");
    }

    private void refreshSceneShaderChoices(String selectedShader) {
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addElement("None");

        if (app != null) {
            for (String shaderName : app.getAvailableProjectEnvironmentShaderNames()) {
                model.addElement(shaderName);
            }
        }

        if (selectedShader != null && !selectedShader.isBlank()) {
            boolean exists = false;
            for (int i = 0; i < model.getSize(); i++) {
                if (selectedShader.equals(model.getElementAt(i))) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                model.addElement(selectedShader);
            }
        }

        cboSceneShader.setModel(model);
        cboSceneShader.setSelectedItem(selectedShader != null && !selectedShader.isBlank() ? selectedShader : "None");
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

    public Dimension getCanvasSizeForAutomation() {
        Component target = canvas != null ? canvas : canvasContainer;
        if (target == null) {
            return new Dimension(0, 0);
        }
        return new Dimension(target.getWidth(), target.getHeight());
    }

    public Component getCanvasComponentForAutomation() {
        return canvas != null ? canvas : canvasContainer;
    }

    public void selectEntityForAutomation(DesignerEntity entity) {
        if (entity == null) {
            SwingUtilities.invokeLater(() -> selectEntityInTree(null));
            if (app != null) {
                app.enqueue(() -> {
                    app.getSelectionManager().deselect();
                    app.setCinematicAuthoringTarget(null);
                    return null;
                });
            }
            return;
        }

        SwingUtilities.invokeLater(() -> selectEntityInTree(entity));
        if (app != null) {
            app.enqueue(() -> {
                app.getSelectionManager().select(entity);
                app.setCinematicAuthoringTarget(
                        (entity.getType() == DesignerEntityType.CINEMATIC_TRACK
                                || entity.getType() == DesignerEntityType.CINEMATIC_RIG) ? entity : null);
                return null;
            });
        }
    }

    public JSONObject showReferenceOverlayForAutomation(File imageFile, float opacity, double scale,
                                                        String fitMode, int offsetX, int offsetY) throws IOException {
        if (imageFile == null || !imageFile.isFile()) {
            throw new IOException("Reference image file does not exist: " + imageFile);
        }
        BufferedImage image = ImageIO.read(imageFile);
        if (image == null) {
            throw new IOException("Unable to read reference image: " + imageFile.getName());
        }

        if (referenceImageOverlay == null) {
            referenceImageOverlay = new ReferenceImageOverlay();
        }

        JLayeredPane layered = getLayeredPaneForCanvas();
        if (layered == null) {
            throw new IOException("The designer canvas layered pane is not available.");
        }
        if (referenceImageOverlay.getParent() == null) {
            layered.add(referenceImageOverlay, JLayeredPane.DRAG_LAYER);
        }

        referenceImageOverlay.configure(image, opacity, scale, fitMode, offsetX, offsetY);
        repositionReferenceOverlay(layered);
        referenceImageOverlay.setVisible(true);
        layered.revalidate();
        layered.repaint();

        return new JSONObject()
                .put("visible", true)
                .put("imagePath", imageFile.getAbsolutePath())
                .put("opacity", opacity)
                .put("scale", scale)
                .put("fitMode", fitMode == null || fitMode.isBlank() ? "contain" : fitMode.trim().toLowerCase())
                .put("offsetX", offsetX)
                .put("offsetY", offsetY);
    }

    public JSONObject clearReferenceOverlayForAutomation() {
        if (referenceImageOverlay != null) {
            referenceImageOverlay.setVisible(false);
            if (referenceImageOverlay.getParent() != null) {
                referenceImageOverlay.getParent().repaint();
            }
        }
        return new JSONObject().put("visible", false);
    }

    public File captureCanvasSnapshot(File outputFile, int width, int height) throws IOException {
        return captureCanvasSnapshot(outputFile, width, height, false);
    }

    public File captureCanvasSnapshot(File outputFile, int width, int height, boolean clean) throws IOException {
        if (outputFile == null) {
            throw new IOException("Output file is required.");
        }

        Component target = canvas != null ? canvas : canvasContainer;
        if (target == null || !target.isShowing() || target.getWidth() <= 0 || target.getHeight() <= 0) {
            throw new IOException("The designer canvas is not visible, so no snapshot could be captured.");
        }

        BufferedImage finalImage;
        if (app != null) {
            finalImage = app.captureCurrentViewSnapshot(width, height, clean);
        } else {
            BufferedImage capture = new BufferedImage(target.getWidth(), target.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D captureGraphics = capture.createGraphics();
            captureGraphics.setColor(target.getBackground() != null ? target.getBackground() : Color.BLACK);
            captureGraphics.fillRect(0, 0, capture.getWidth(), capture.getHeight());
            target.printAll(captureGraphics);
            captureGraphics.dispose();

            finalImage = capture;
            if (width > 0 && height > 0 && (capture.getWidth() != width || capture.getHeight() != height)) {
                BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = scaled.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.drawImage(capture, 0, 0, width, height, null);
                g2.dispose();
                finalImage = scaled;
            }
        }

        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        ImageIO.write(finalImage, "png", outputFile);
        return outputFile;
    }

    private static final class ReferenceImageOverlay extends JComponent {
        private BufferedImage image;
        private float opacity = 0.35f;
        private double scale = 1.0;
        private String fitMode = "contain";
        private int offsetX;
        private int offsetY;

        ReferenceImageOverlay() {
            setOpaque(false);
            setVisible(false);
            setEnabled(false);
        }

        void configure(BufferedImage image, float opacity, double scale, String fitMode, int offsetX, int offsetY) {
            this.image = image;
            this.opacity = Math.max(0f, Math.min(1f, opacity));
            this.scale = Math.max(0.05, scale);
            this.fitMode = fitMode == null || fitMode.isBlank() ? "contain" : fitMode.trim().toLowerCase();
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image == null || opacity <= 0f || getWidth() <= 0 || getHeight() <= 0) {
                return;
            }

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));

            double baseScaleX = getWidth() / (double) image.getWidth();
            double baseScaleY = getHeight() / (double) image.getHeight();
            double fitScale;
            switch (fitMode) {
                case "cover":
                    fitScale = Math.max(baseScaleX, baseScaleY);
                    break;
                case "stretch":
                    fitScale = Double.NaN;
                    break;
                case "original":
                    fitScale = 1.0;
                    break;
                case "contain":
                default:
                    fitScale = Math.min(baseScaleX, baseScaleY);
                    break;
            }

            int drawWidth;
            int drawHeight;
            if (Double.isNaN(fitScale)) {
                drawWidth = Math.max(1, (int) Math.round(getWidth() * scale));
                drawHeight = Math.max(1, (int) Math.round(getHeight() * scale));
            } else {
                drawWidth = Math.max(1, (int) Math.round(image.getWidth() * fitScale * scale));
                drawHeight = Math.max(1, (int) Math.round(image.getHeight() * fitScale * scale));
            }

            int x = ((getWidth() - drawWidth) / 2) + offsetX;
            int y = ((getHeight() - drawHeight) / 2) + offsetY;
            g2.drawImage(image, x, y, drawWidth, drawHeight, null);
            g2.dispose();
        }
    }

    public File getDesignerFile() {
        return designerFile;
    }

    public String readGeneratedCode() throws IOException {
        File codeFile = DesignerDocument.getCodeFile(designerFile);
        return FileUtils.readFileToString(codeFile, StandardCharsets.UTF_8);
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
            case "wedge":     drawToolbarWedge(g);     break;
            case "cylinder":        drawToolbarCylinder(g);        break;
            case "cone":            drawToolbarCone(g);            break;
            case "hollowcylinder":  drawToolbarHollowCylinder(g);  break;
            case "quad":            drawToolbarQuad(g);            break;
            case "stairs":          drawToolbarStairs(g);          break;
            case "arch":            drawToolbarArch(g);            break;
            case "model":     drawToolbarModel(g);     break;
            case "delete":    drawToolbarDelete(g);    break;
            case "translate": drawToolbarTranslate(g); break;
            case "rotate":    drawToolbarRotate(g);    break;
            case "orbit":     drawToolbarOrbit(g);     break;
            case "pan":       drawToolbarPan(g);       break;
            case "path":      drawToolbarPath(g);      break;
            case "cinematic": drawToolbarCinematic(g); break;
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

    private static void drawToolbarWedge(Graphics2D g) {
        GeneralPath face = new GeneralPath();
        face.moveTo(4, 15);
        face.lineTo(15, 15);
        face.lineTo(15, 5);
        face.closePath();
        g.draw(face);
        g.draw(new Line2D.Float(4, 15, 6, 11));
        g.draw(new Line2D.Float(15, 15, 17, 11));
        g.draw(new Line2D.Float(15, 5, 17, 1));
        g.draw(new Line2D.Float(6, 11, 17, 11));
        g.draw(new Line2D.Float(17, 11, 17, 1));
    }

    /** Cylinder: two ellipses connected by lines */
    private static void drawToolbarCylinder(Graphics2D g) {
        // top ellipse
        g.draw(new Ellipse2D.Float(3, 2, 14, 5));
        // side lines
        g.draw(new Line2D.Float(3, 4.5f, 3, 15.5f));
        g.draw(new Line2D.Float(17, 4.5f, 17, 15.5f));
        // bottom ellipse (arc only - back half hidden)
        g.draw(new java.awt.geom.Arc2D.Float(3, 13, 14, 5, 180, 180, java.awt.geom.Arc2D.OPEN));
        g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                1.0f, new float[]{2f, 2f}, 0));
        g.draw(new java.awt.geom.Arc2D.Float(3, 13, 14, 5, 0, 180, java.awt.geom.Arc2D.OPEN));
    }

    private static void drawToolbarCone(Graphics2D g) {
        g.draw(new Ellipse2D.Float(4, 13, 12, 4));
        g.draw(new Line2D.Float(10, 3, 4, 15));
        g.draw(new Line2D.Float(10, 3, 16, 15));
    }

    /** Hollow Cylinder: cylinder with inner hole visible on top cap */
    private static void drawToolbarHollowCylinder(Graphics2D g) {
        // outer top ellipse
        g.draw(new Ellipse2D.Float(3, 2, 14, 5));
        // side lines
        g.draw(new Line2D.Float(3, 4.5f, 3, 15.5f));
        g.draw(new Line2D.Float(17, 4.5f, 17, 15.5f));
        // bottom ellipse (front arc only)
        g.draw(new java.awt.geom.Arc2D.Float(3, 13, 14, 5, 180, 180, java.awt.geom.Arc2D.OPEN));
        // bottom ellipse (back arc dashed)
        g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                1.0f, new float[]{2f, 2f}, 0));
        g.draw(new java.awt.geom.Arc2D.Float(3, 13, 14, 5, 0, 180, java.awt.geom.Arc2D.OPEN));
        // inner hole on top cap
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(DT_COLOR);
        g.draw(new Ellipse2D.Float(6, 3, 8, 3));
    }

    private static void drawToolbarTube(Graphics2D g) {
        drawToolbarHollowCylinder(g);
    }

    /** Quad: flat rectangle with slight perspective */
    private static void drawToolbarQuad(Graphics2D g) {
        GeneralPath p = new GeneralPath();
        p.moveTo(4, 5);
        p.lineTo(16, 3);
        p.lineTo(18, 15);
        p.lineTo(6, 17);
        p.closePath();
        g.draw(p);
        // diagonal to suggest flatness
        g.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(DT_HIGHLIGHT);
        g.draw(new Line2D.Float(4, 5, 18, 15));
    }

    private static void drawToolbarStairs(Graphics2D g) {
        g.draw(new Line2D.Float(4, 16, 16, 16));
        g.draw(new Line2D.Float(16, 16, 16, 4));
        g.draw(new Line2D.Float(4, 16, 4, 12));
        g.draw(new Line2D.Float(4, 12, 8, 12));
        g.draw(new Line2D.Float(8, 12, 8, 8));
        g.draw(new Line2D.Float(8, 8, 12, 8));
        g.draw(new Line2D.Float(12, 8, 12, 4));
        g.draw(new Line2D.Float(12, 4, 16, 4));
    }

    private static void drawToolbarArch(Graphics2D g) {
        g.draw(new Line2D.Float(4, 16, 4, 10));
        g.draw(new Line2D.Float(16, 16, 16, 10));
        g.draw(new java.awt.geom.Arc2D.Float(4, 4, 12, 12, 0, 180, java.awt.geom.Arc2D.OPEN));
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

    /** Path: S-curve with two dots at endpoints */
    private static void drawToolbarPath(Graphics2D g) {
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // S-curve Bezier path
        GeneralPath curve = new GeneralPath();
        curve.moveTo(3, 16);
        curve.curveTo(3, 8, 17, 12, 17, 4);
        g.draw(curve);
        // Endpoint dots
        g.fill(new Ellipse2D.Float(1.5f, 14.5f, 3, 3));
        g.fill(new Ellipse2D.Float(15.5f, 2.5f, 3, 3));
    }

    private static void drawToolbarCinematic(Graphics2D g) {
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Ellipse2D.Float(2, 5, 16, 10));
        g.fill(new Ellipse2D.Float(3, 9, 2.5f, 2.5f));
        g.fill(new Ellipse2D.Float(14.5f, 9, 2.5f, 2.5f));
        g.setColor(DT_HIGHLIGHT);
        g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.Arc2D.Float(2, 5, 16, 10, 200, 80, java.awt.geom.Arc2D.OPEN));
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
                case WEDGE:
                    drawWedge(g);
                    break;
                case CYLINDER:
                    drawCylinder(g);
                    break;
                case CONE:
                    drawCone(g);
                    break;
                case HOLLOW_CYLINDER:
                    drawHollowCylinder(g);
                    break;
                case QUAD:
                    drawQuad(g);
                    break;
                case STAIRS:
                    drawStairs(g);
                    break;
                case ARCH:
                    drawArch(g);
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
                case PATH:
                    drawPath(g);
                    break;
                case CINEMATIC_RIG:
                    drawSection(g);
                    break;
                case CINEMATIC_TRACK:
                    drawCinematic(g);
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

        private static void drawWedge(Graphics2D g) {
            GeneralPath p = new GeneralPath();
            p.moveTo(3, 13);
            p.lineTo(12, 13);
            p.lineTo(12, 5);
            p.closePath();
            g.draw(p);
            g.draw(new Line2D.Float(3, 13, 5, 10));
            g.draw(new Line2D.Float(12, 13, 14, 10));
            g.draw(new Line2D.Float(12, 5, 14, 2));
            g.draw(new Line2D.Float(5, 10, 14, 10));
            g.draw(new Line2D.Float(14, 10, 14, 2));
        }

        /** Cylinder: two ellipses with side lines */
        private static void drawCylinder(Graphics2D g) {
            g.draw(new Ellipse2D.Float(2, 2, 12, 4));
            g.draw(new Line2D.Float(2, 4, 2, 12));
            g.draw(new Line2D.Float(14, 4, 14, 12));
            g.draw(new java.awt.geom.Arc2D.Float(2, 10, 12, 4, 180, 180, java.awt.geom.Arc2D.OPEN));
        }

        private static void drawCone(Graphics2D g) {
            g.draw(new Ellipse2D.Float(3, 11, 10, 3));
            g.draw(new Line2D.Float(8, 3, 3, 12));
            g.draw(new Line2D.Float(8, 3, 13, 12));
        }

        /** Hollow Cylinder: cylinder with inner hole on top */
        private static void drawHollowCylinder(Graphics2D g) {
            // outer top ellipse
            g.draw(new Ellipse2D.Float(2, 2, 12, 4));
            // side lines
            g.draw(new Line2D.Float(2, 4, 2, 12));
            g.draw(new Line2D.Float(14, 4, 14, 12));
            // bottom arc (front half)
            g.draw(new java.awt.geom.Arc2D.Float(2, 10, 12, 4, 180, 180, java.awt.geom.Arc2D.OPEN));
            // inner hole on top cap
            g.draw(new Ellipse2D.Float(5, 2.5f, 6, 3));
        }

        private static void drawTube(Graphics2D g) {
            drawHollowCylinder(g);
        }

        /** Quad: flat rectangle */
        private static void drawQuad(Graphics2D g) {
            GeneralPath p = new GeneralPath();
            p.moveTo(3, 4);
            p.lineTo(13, 2);
            p.lineTo(14, 12);
            p.lineTo(4, 14);
            p.closePath();
            g.draw(p);
        }

        private static void drawStairs(Graphics2D g) {
            g.draw(new Line2D.Float(3, 13, 13, 13));
            g.draw(new Line2D.Float(13, 13, 13, 4));
            g.draw(new Line2D.Float(3, 13, 3, 10));
            g.draw(new Line2D.Float(3, 10, 6, 10));
            g.draw(new Line2D.Float(6, 10, 6, 7));
            g.draw(new Line2D.Float(6, 7, 9, 7));
            g.draw(new Line2D.Float(9, 7, 9, 4));
            g.draw(new Line2D.Float(9, 4, 13, 4));
        }

        private static void drawArch(Graphics2D g) {
            g.draw(new Line2D.Float(3, 13, 3, 8));
            g.draw(new Line2D.Float(13, 13, 13, 8));
            g.draw(new java.awt.geom.Arc2D.Float(3, 3, 10, 10, 0, 180, java.awt.geom.Arc2D.OPEN));
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

        /** Path: small S-curve with endpoint dots */
        private static void drawPath(Graphics2D g) {
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            GeneralPath curve = new GeneralPath();
            curve.moveTo(2, 13);
            curve.curveTo(2, 7, 14, 9, 14, 3);
            g.draw(curve);
            g.fill(new Ellipse2D.Float(1, 12, 2.5f, 2.5f));
            g.fill(new Ellipse2D.Float(12.5f, 1.5f, 2.5f, 2.5f));
        }

        private static void drawCinematic(Graphics2D g) {
            g.draw(new Ellipse2D.Float(1.5f, 4.5f, 13, 7));
            g.fill(new Ellipse2D.Float(2, 7, 2, 2));
            g.fill(new Ellipse2D.Float(12, 7, 2, 2));
            g.setColor(new Color(255, 255, 255, 100));
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new java.awt.geom.Arc2D.Float(1.5f, 4.5f, 13, 7, 200, 80, java.awt.geom.Arc2D.OPEN));
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
