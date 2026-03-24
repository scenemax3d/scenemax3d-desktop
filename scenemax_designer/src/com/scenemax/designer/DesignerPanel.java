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
import java.io.File;
import java.io.IOException;
import java.util.List;

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
    private JCheckBox chkStaticEntity, chkColliderEntity;
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

        JButton btnAddSphere = new JButton("Sphere");
        btnAddSphere.setToolTipText("Add Sphere");
        btnAddSphere.addActionListener(e -> {
            if (app != null) app.enqueue(() -> { app.addDefaultSphere(); return null; });
        });

        JButton btnAddBox = new JButton("Box");
        btnAddBox.setToolTipText("Add Box");
        btnAddBox.addActionListener(e -> {
            if (app != null) app.enqueue(() -> { app.addDefaultBox(); return null; });
        });

        JButton btnAddModel = new JButton("3D Model");
        btnAddModel.setToolTipText("Add 3D Model");
        btnAddModel.addActionListener(e -> showModelPickerDialog());

        JButton btnDelete = new JButton("Delete");
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
        btnTranslate = new JToggleButton("Translate", true);
        btnRotate = new JToggleButton("Rotate");
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

        // --- Left Panel (Scene Tree + Properties) ---
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(220, 0));

        // Scene tree
        sceneTreeRoot = new DefaultMutableTreeNode("Scene");
        sceneTreeModel = new DefaultTreeModel(sceneTreeRoot);
        sceneTree = new JTree(sceneTreeModel);
        sceneTree.setRootVisible(true);
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
        sceneTree.setDropMode(DropMode.INSERT);
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
                app.enqueue(() -> { app.addModel(selectedModel, isStatic, isDynamic, isVehicle); return null; });
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
        sceneTreeRoot.removeAllChildren();
        if (app != null) {
            for (DesignerEntity entity : app.getEntities()) {
                String icon;
                switch (entity.getType()) {
                    case SPHERE: icon = "[S] "; break;
                    case BOX:    icon = "[B] "; break;
                    case MODEL:  icon = "[M] "; break;
                    case CAMERA: icon = "[C] "; break;
                    case CODE:   icon = "{} "; break;
                    default:     icon = ""; break;
                }
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(new EntityTreeNode(entity, icon + entity.getName()));
                sceneTreeRoot.add(node);
            }
        }
        sceneTreeModel.reload();
        // Expand root
        sceneTree.expandRow(0);
    }

    private void onTreeSelectionChanged(TreeSelectionEvent e) {
        if (updatingTreeSelection) return;
        DefaultMutableTreeNode selected = (DefaultMutableTreeNode) sceneTree.getLastSelectedPathComponent();
        if (selected == null || !(selected.getUserObject() instanceof EntityTreeNode)) {
            hideCodeEditor();
            return;
        }
        EntityTreeNode etn = (EntityTreeNode) selected.getUserObject();

        // CODE nodes have no 3D scene node - show floating code editor instead
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
            for (int i = 0; i < sceneTreeRoot.getChildCount(); i++) {
                DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) sceneTreeRoot.getChildAt(i);
                if (childNode.getUserObject() instanceof EntityTreeNode) {
                    EntityTreeNode etn = (EntityTreeNode) childNode.getUserObject();
                    if (etn.entity == entity) {
                        javax.swing.tree.TreePath path = new javax.swing.tree.TreePath(childNode.getPath());
                        sceneTree.setSelectionPath(path);
                        sceneTree.scrollPathToVisible(path);
                        return;
                    }
                }
            }
            sceneTree.clearSelection();
        } finally {
            updatingTreeSelection = false;
        }
    }

    private void showTreeContextMenu(MouseEvent e) {
        int row = sceneTree.getClosestRowForLocation(e.getX(), e.getY());
        if (row < 0) return;
        sceneTree.setSelectionRow(row);

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) sceneTree.getLastSelectedPathComponent();
        if (node == null || !(node.getUserObject() instanceof EntityTreeNode)) return;
        EntityTreeNode etn = (EntityTreeNode) node.getUserObject();

        JPopupMenu menu = new JPopupMenu();

        // "Add Code Node" - inserts after the selected item
        JMenuItem addCodeItem = new JMenuItem("Add Code Node");
        addCodeItem.addActionListener(ev -> {
            String name = JOptionPane.showInputDialog(this, "Code node name:", "New Code Node",
                    JOptionPane.PLAIN_MESSAGE);
            if (name != null && !name.trim().isEmpty()) {
                String trimmedName = name.trim();
                if (app != null) {
                    int entityIndex = app.getEntities().indexOf(etn.entity);
                    int insertIndex = entityIndex >= 0 ? entityIndex + 1 : -1;
                    app.enqueue(() -> {
                        app.addCodeNode(trimmedName, insertIndex);
                        return null;
                    });
                }
            }
        });
        menu.add(addCodeItem);

        menu.addSeparator();

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

    // --- Code node double-click rename ---

    private void onTreeDoubleClick(MouseEvent e) {
        int row = sceneTree.getClosestRowForLocation(e.getX(), e.getY());
        if (row < 0) return;
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)
                sceneTree.getPathForRow(row).getLastPathComponent();
        if (node == null || !(node.getUserObject() instanceof EntityTreeNode)) return;
        EntityTreeNode etn = (EntityTreeNode) node.getUserObject();
        if (etn.entity.getType() != DesignerEntityType.CODE) return;

        String newName = JOptionPane.showInputDialog(this, "Rename code node:",
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
                staticColliderPanel.setVisible(false);
                materialPanel.setVisible(false);
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
     * within the scene hierarchy tree. Only entity nodes (children of root)
     * can be dragged; dropping onto the root node or between entity nodes
     * reorders the entity list in DesignerApp.
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
            // Only allow drops under the root node
            DefaultMutableTreeNode destNode = (DefaultMutableTreeNode) destPath.getLastPathComponent();
            return destNode == sceneTreeRoot;
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            try {
                EntityTreeNode draggedEtn = (EntityTreeNode) support.getTransferable().getTransferData(entityNodeFlavor);
                JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
                int dropIndex = dl.getChildIndex();

                if (app == null) return false;
                List<DesignerEntity> entities = app.getEntities();

                // Find source index in the entity list
                int sourceIndex = entities.indexOf(draggedEtn.entity);
                if (sourceIndex < 0) return false;

                // dropIndex == -1 means dropped on the root node itself → move to end
                int targetIndex = computeDropTargetIndex(dropIndex, sourceIndex, entities);

                if (sourceIndex == targetIndex) return false;

                final int finalTarget = targetIndex;
                app.enqueue(() -> {
                    app.reorderEntity(sourceIndex, finalTarget);
                    return null;
                });
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        private int computeDropTargetIndex(int dropIndex, int sourceIndex, List<DesignerEntity> entities) {
            if (dropIndex < 0) {
                return entities.size() - 1;
            }
            if (dropIndex >= sceneTreeRoot.getChildCount()) {
                return entities.size() - 1;
            }
            if (dropIndex == 0) {
                return 0;
            }
            // Get the entity at the tree node just before the drop position
            int lookupIndex = dropIndex > sourceIndex ? dropIndex - 1 : dropIndex;
            DefaultMutableTreeNode nodeBefore = (DefaultMutableTreeNode) sceneTreeRoot.getChildAt(lookupIndex);
            if (nodeBefore.getUserObject() instanceof EntityTreeNode) {
                EntityTreeNode etnBefore = (EntityTreeNode) nodeBefore.getUserObject();
                int idx = entities.indexOf(etnBefore.entity);
                return idx >= 0 ? idx : entities.size() - 1;
            }
            return entities.size() - 1;
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
