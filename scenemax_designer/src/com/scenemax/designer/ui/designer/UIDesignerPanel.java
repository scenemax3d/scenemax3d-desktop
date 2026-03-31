package com.scenemax.designer.ui.designer;

import com.scenemaxeng.common.types.AssetsMapping;
import com.scenemaxeng.common.types.ResourceFont;
import com.scenemaxeng.common.types.ResourceSetup2D;
import com.scenemaxeng.common.ui.model.*;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.Map;

/**
 * Swing panel that provides a visual UI designer for .smui documents.
 * Hosted inside the EditorTabPanel as a tab, following the same pattern
 * as the existing DesignerPanel for .smdesign files.
 *
 * Layout:
 * +-------------------------------------------+
 * | Toolbar: [Panel] [Button] [Text] [Image]  |
 * +----------+-------------------+------------+
 * | Widget   |                   | Properties |
 * | Tree     |   2D Canvas       | Panel      |
 * |          |   (Layout Preview) |           |
 * +----------+-------------------+------------+
 */
public class UIDesignerPanel extends JPanel {

    private String projectPath;
    private File uiFile;
    private UIDocument document;
    private boolean dirty = false;

    // Left panel: widget hierarchy tree
    private JTree widgetTree;
    private DefaultTreeModel widgetTreeModel;
    private DefaultMutableTreeNode widgetTreeRoot;

    // Center: 2D layout preview canvas
    private UIDesignerCanvas canvas;

    // Right panel: properties inspector
    private static final int LABEL_WIDTH = 80;
    private JPanel propertiesPanel;

    // Property fields — common
    private JTextField txtWidgetName;
    private JComboBox<String> cboWidthMode;
    private JComboBox<String> cboHeightMode;
    private JSpinner spnWidth, spnHeight;
    private JSpinner spnHBias, spnVBias;
    private JSpinner spnMarginLeft, spnMarginRight, spnMarginTop, spnMarginBottom;
    private JSpinner spnPaddingLeft, spnPaddingRight, spnPaddingTop, spnPaddingBottom;

    // Property fields — constraints
    private JComboBox<String> cboConstraintLeft, cboConstraintRight, cboConstraintTop, cboConstraintBottom;
    private JComboBox<String> cboConstraintLeftSide, cboConstraintRightSide, cboConstraintTopSide, cboConstraintBottomSide;
    private JCheckBox chkCenterHorizontal, chkCenterVertical;

    // Property fields — type-specific
    private JPanel textPropsPanel;
    private JTextField txtText;
    private JSpinner spnFontSize;
    private JComboBox<String> cboTextAlign;
    private JComboBox<String> cboFont;

    private JPanel buttonPropsPanel;
    private JTextField txtButtonText;

    private JPanel imagePropsPanel;
    private JTextField txtImagePath;
    private JComboBox<String> cboSprite;

    // Layer selector
    private JComboBox<String> cboLayer;

    // Flag to prevent feedback loops when updating properties programmatically
    private boolean updatingProperties = false;
    private boolean updatingTreeSelection = false;

    // Callback for notifying the IDE that the file has been modified
    private Runnable onDirtyCallback;

    // Sprite resource mapping for design-time rendering
    private AssetsMapping assetsMapping;

    public UIDesignerPanel(String projectPath, File uiFile) {
        super(new BorderLayout());
        this.projectPath = projectPath;
        this.uiFile = uiFile;

        loadDocument();
        buildUI();
    }

    // ========================================================================
    // Document loading/saving
    // ========================================================================

    private void loadDocument() {
        try {
            if (uiFile.exists()) {
                document = UIDocument.load(uiFile);
            } else {
                document = UIDocument.createEmpty(uiFile.getAbsolutePath());
                UILayerDef defaultLayer = new UILayerDef("layer1");
                document.addLayer(defaultLayer);
            }
        } catch (IOException e) {
            e.printStackTrace();
            document = UIDocument.createEmpty(uiFile.getAbsolutePath());
        }
    }

    public void saveDocument() {
        if (document == null) return;
        try {
            document.save(uiFile);
            document.saveCodeFile(uiFile);
            dirty = false;
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Error saving UI document: " + e.getMessage(),
                    "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean isDirty() { return dirty; }
    public File getUiFile() { return uiFile; }
    public UIDocument getDocument() { return document; }

    public void setOnDirtyCallback(Runnable callback) {
        this.onDirtyCallback = callback;
    }

    private void markDirty() {
        dirty = true;
        if (onDirtyCallback != null) onDirtyCallback.run();
    }

    // ========================================================================
    // UI Construction
    // ========================================================================

    private void buildUI() {
        // --- Toolbar ---
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        // Layer selector
        toolbar.add(new JLabel("  Layer: "));
        cboLayer = new JComboBox<>();
        refreshLayerCombo();
        cboLayer.setPreferredSize(new Dimension(120, 26));
        cboLayer.addActionListener(e -> onLayerChanged());
        toolbar.add(cboLayer);

        JButton btnAddLayer = new JButton(createUIToolbarIcon("addlayer"));
        btnAddLayer.setToolTipText("Add Layer");
        btnAddLayer.addActionListener(e -> addLayer());
        toolbar.add(btnAddLayer);

        JButton btnRemoveLayer = new JButton(createUIToolbarIcon("removelayer"));
        btnRemoveLayer.setToolTipText("Remove Layer");
        btnRemoveLayer.addActionListener(e -> removeLayer());
        toolbar.add(btnRemoveLayer);

        toolbar.addSeparator();
        toolbar.add(new JLabel("  Add: "));

        JButton btnAddPanel = new JButton(createUIToolbarIcon("panel"));
        btnAddPanel.setToolTipText("Add Panel");
        btnAddPanel.addActionListener(e -> addWidget(UIWidgetType.PANEL));
        toolbar.add(btnAddPanel);

        JButton btnAddButton = new JButton(createUIToolbarIcon("button"));
        btnAddButton.setToolTipText("Add Button");
        btnAddButton.addActionListener(e -> addWidget(UIWidgetType.BUTTON));
        toolbar.add(btnAddButton);

        JButton btnAddText = new JButton(createUIToolbarIcon("textview"));
        btnAddText.setToolTipText("Add Text View");
        btnAddText.addActionListener(e -> addWidget(UIWidgetType.TEXT_VIEW));
        toolbar.add(btnAddText);

        JButton btnAddImage = new JButton(createUIToolbarIcon("image"));
        btnAddImage.setToolTipText("Add Image");
        btnAddImage.addActionListener(e -> addWidget(UIWidgetType.IMAGE));
        toolbar.add(btnAddImage);

        JButton btnAddGuideline = new JButton(createUIToolbarIcon("guideline"));
        btnAddGuideline.setToolTipText("Add Guideline");
        btnAddGuideline.addActionListener(e -> addWidget(UIWidgetType.GUIDELINE));
        toolbar.add(btnAddGuideline);

        toolbar.addSeparator();

        JButton btnDelete = new JButton(createUIToolbarIcon("delete"));
        btnDelete.setToolTipText("Delete Selected Widget");
        btnDelete.addActionListener(e -> deleteSelectedWidget());
        toolbar.add(btnDelete);

        toolbar.addSeparator();

        JButton btnSave = new JButton("Save");
        btnSave.setToolTipText("Save UI Document");
        btnSave.addActionListener(e -> saveDocument());
        toolbar.add(btnSave);

        add(toolbar, BorderLayout.NORTH);

        // --- Left panel: Widget tree ---
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(200, 0));

        widgetTreeRoot = new DefaultMutableTreeNode("UI");
        widgetTreeModel = new DefaultTreeModel(widgetTreeRoot);
        widgetTree = new JTree(widgetTreeModel);
        widgetTree.setRootVisible(true);
        widgetTree.setCellRenderer(new UIWidgetTreeCellRenderer());
        widgetTree.addTreeSelectionListener(this::onTreeSelectionChanged);
        widgetTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showTreeContextMenu(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showTreeContextMenu(e);
            }
        });

        JScrollPane treeScroll = new JScrollPane(widgetTree);
        treeScroll.setBorder(BorderFactory.createTitledBorder("Widget Hierarchy"));
        leftPanel.add(treeScroll, BorderLayout.CENTER);

        // --- Center: 2D canvas ---
        canvas = new UIDesignerCanvas();
        canvas.setDocument(document);
        canvas.setSelectionListener(widget -> {
            selectWidgetInTree(widget);
            showPropertiesForWidget(widget);
        });

        // --- Right panel: Properties ---
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setPreferredSize(new Dimension(290, 0));

        propertiesPanel = new JPanel();
        propertiesPanel.setLayout(new BoxLayout(propertiesPanel, BoxLayout.Y_AXIS));
        propertiesPanel.setBorder(BorderFactory.createTitledBorder("Properties"));
        buildPropertiesForm();

        // Pass sprite resources to canvas for design-time rendering
        if (assetsMapping != null) {
            canvas.setSpriteResources(assetsMapping, projectPath);
        }

        JScrollPane propsScroll = new JScrollPane(propertiesPanel);
        rightPanel.add(propsScroll, BorderLayout.CENTER);

        // --- Main layout ---
        JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvas, rightPanel);
        rightSplit.setDividerLocation(800);
        rightSplit.setResizeWeight(1.0);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightSplit);
        mainSplit.setDividerLocation(200);
        mainSplit.setResizeWeight(0.0);

        add(mainSplit, BorderLayout.CENTER);

        // Initial tree refresh
        refreshWidgetTree();
    }

    // ========================================================================
    // Properties Panel
    // ========================================================================

    private void buildPropertiesForm() {
        propertiesPanel.removeAll();

        // Widget name
        txtWidgetName = new JTextField(15);
        txtWidgetName.addActionListener(e -> applyWidgetNameChange());
        addFormRow("Name:", txtWidgetName);

        propertiesPanel.add(Box.createVerticalStrut(8));
        propertiesPanel.add(createBoldLabel("Size:"));

        // Width mode + value
        cboWidthMode = new JComboBox<>(new String[]{"Fixed", "Wrap Content", "Match Constraint"});
        cboWidthMode.addActionListener(e -> applySizeModeChange());
        addFormRow("Width Mode:", cboWidthMode);

        spnWidth = new JSpinner(new SpinnerNumberModel(100.0, 0.0, 9999.0, 1.0));
        spnWidth.addChangeListener(e -> applySizeChange());
        addFormRow("Width:", spnWidth);

        cboHeightMode = new JComboBox<>(new String[]{"Fixed", "Wrap Content", "Match Constraint"});
        cboHeightMode.addActionListener(e -> applySizeModeChange());
        addFormRow("Height Mode:", cboHeightMode);

        spnHeight = new JSpinner(new SpinnerNumberModel(50.0, 0.0, 9999.0, 1.0));
        spnHeight.addChangeListener(e -> applySizeChange());
        addFormRow("Height:", spnHeight);

        // --- Constraints ---
        propertiesPanel.add(Box.createVerticalStrut(8));
        propertiesPanel.add(createBoldLabel("Constraints:"));

        String[] sides = {"left", "right"};
        String[] vSides = {"top", "bottom"};
        String[] targets = {"(none)", "parent"};  // will be populated dynamically

        // Left constraint
        cboConstraintLeft = new JComboBox<>(targets);
        cboConstraintLeftSide = new JComboBox<>(sides);
        addConstraintRow("Left \u2192", cboConstraintLeft, cboConstraintLeftSide);

        // Right constraint
        cboConstraintRight = new JComboBox<>(targets);
        cboConstraintRightSide = new JComboBox<>(sides);
        addConstraintRow("Right \u2192", cboConstraintRight, cboConstraintRightSide);

        // Top constraint
        cboConstraintTop = new JComboBox<>(targets);
        cboConstraintTopSide = new JComboBox<>(vSides);
        addConstraintRow("Top \u2192", cboConstraintTop, cboConstraintTopSide);

        // Bottom constraint
        cboConstraintBottom = new JComboBox<>(targets);
        cboConstraintBottomSide = new JComboBox<>(vSides);
        addConstraintRow("Bottom \u2192", cboConstraintBottom, cboConstraintBottomSide);

        // Center constraints
        propertiesPanel.add(Box.createVerticalStrut(4));
        chkCenterHorizontal = new JCheckBox("Center Horizontal");
        chkCenterVertical = new JCheckBox("Center Vertical");
        chkCenterHorizontal.addActionListener(e -> applyCenterChange());
        chkCenterVertical.addActionListener(e -> applyCenterChange());

        JPanel centerRow = new JPanel(new GridBagLayout());
        centerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        centerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        centerRow.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        GridBagConstraints cgbc = new GridBagConstraints();
        cgbc.fill = GridBagConstraints.HORIZONTAL;
        cgbc.gridy = 0; cgbc.insets = new Insets(0, 0, 0, 4);
        cgbc.gridx = 0; cgbc.weightx = 1.0;
        centerRow.add(chkCenterHorizontal, cgbc);
        cgbc.gridx = 1;
        centerRow.add(chkCenterVertical, cgbc);
        propertiesPanel.add(centerRow);

        // Margins
        propertiesPanel.add(Box.createVerticalStrut(8));
        propertiesPanel.add(createBoldLabel("Margins:"));

        spnMarginLeft = new JSpinner(new SpinnerNumberModel(0.0, -9999.0, 9999.0, 1.0));
        spnMarginRight = new JSpinner(new SpinnerNumberModel(0.0, -9999.0, 9999.0, 1.0));
        spnMarginTop = new JSpinner(new SpinnerNumberModel(0.0, -9999.0, 9999.0, 1.0));
        spnMarginBottom = new JSpinner(new SpinnerNumberModel(0.0, -9999.0, 9999.0, 1.0));

        addFormRow("L:", spnMarginLeft, "R:", spnMarginRight);
        addFormRow("T:", spnMarginTop, "B:", spnMarginBottom);

        spnMarginLeft.addChangeListener(e -> applyConstraintChange());
        spnMarginRight.addChangeListener(e -> applyConstraintChange());
        spnMarginTop.addChangeListener(e -> applyConstraintChange());
        spnMarginBottom.addChangeListener(e -> applyConstraintChange());

        // Bias
        propertiesPanel.add(Box.createVerticalStrut(8));
        propertiesPanel.add(createBoldLabel("Bias:"));

        spnHBias = new JSpinner(new SpinnerNumberModel(0.5, 0.0, 1.0, 0.05));
        spnVBias = new JSpinner(new SpinnerNumberModel(0.5, 0.0, 1.0, 0.05));
        addFormRow("H:", spnHBias, "V:", spnVBias);

        spnHBias.addChangeListener(e -> applyBiasChange());
        spnVBias.addChangeListener(e -> applyBiasChange());

        // Padding
        propertiesPanel.add(Box.createVerticalStrut(8));
        propertiesPanel.add(createBoldLabel("Padding:"));

        spnPaddingLeft = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 9999.0, 1.0));
        spnPaddingRight = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 9999.0, 1.0));
        spnPaddingTop = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 9999.0, 1.0));
        spnPaddingBottom = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 9999.0, 1.0));

        addFormRow("L:", spnPaddingLeft, "R:", spnPaddingRight);
        addFormRow("T:", spnPaddingTop, "B:", spnPaddingBottom);

        spnPaddingLeft.addChangeListener(e -> applyPaddingChange());
        spnPaddingRight.addChangeListener(e -> applyPaddingChange());
        spnPaddingTop.addChangeListener(e -> applyPaddingChange());
        spnPaddingBottom.addChangeListener(e -> applyPaddingChange());

        // --- Type-specific: Text View ---
        textPropsPanel = new JPanel();
        textPropsPanel.setLayout(new BoxLayout(textPropsPanel, BoxLayout.Y_AXIS));
        textPropsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textPropsPanel.setBorder(BorderFactory.createTitledBorder("Text Properties"));

        txtText = new JTextField(15);
        txtText.addActionListener(e -> applyTextChange());
        addFormRowTo(textPropsPanel, "Text:", txtText);

        spnFontSize = new JSpinner(new SpinnerNumberModel(16.0, 1.0, 200.0, 1.0));
        spnFontSize.addChangeListener(e -> applyTextChange());
        addFormRowTo(textPropsPanel, "Font Size:", spnFontSize);

        cboTextAlign = new JComboBox<>(new String[]{"left", "center", "right"});
        cboTextAlign.addActionListener(e -> applyTextChange());
        addFormRowTo(textPropsPanel, "Align:", cboTextAlign);

        cboFont = new JComboBox<>();
        cboFont.addItem("(default)");
        loadFontNames();
        cboFont.addActionListener(e -> applyTextChange());
        addFormRowTo(textPropsPanel, "Font:", cboFont);

        textPropsPanel.setVisible(false);
        propertiesPanel.add(textPropsPanel);

        // --- Type-specific: Button ---
        buttonPropsPanel = new JPanel();
        buttonPropsPanel.setLayout(new BoxLayout(buttonPropsPanel, BoxLayout.Y_AXIS));
        buttonPropsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonPropsPanel.setBorder(BorderFactory.createTitledBorder("Button Properties"));

        txtButtonText = new JTextField(15);
        txtButtonText.addActionListener(e -> applyButtonTextChange());
        addFormRowTo(buttonPropsPanel, "Button Text:", txtButtonText);

        buttonPropsPanel.setVisible(false);
        propertiesPanel.add(buttonPropsPanel);

        // --- Type-specific: Image ---
        imagePropsPanel = new JPanel();
        imagePropsPanel.setLayout(new BoxLayout(imagePropsPanel, BoxLayout.Y_AXIS));
        imagePropsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        imagePropsPanel.setBorder(BorderFactory.createTitledBorder("Image Properties"));

        cboSprite = new JComboBox<>();
        cboSprite.addItem("(none)");
        loadSpriteNames();
        cboSprite.addActionListener(e -> applySpriteChange());
        addFormRowTo(imagePropsPanel, "Sprite:", cboSprite);

        txtImagePath = new JTextField(15);
        txtImagePath.addActionListener(e -> applyImagePathChange());
        addFormRowTo(imagePropsPanel, "Image Path:", txtImagePath);

        imagePropsPanel.setVisible(false);
        propertiesPanel.add(imagePropsPanel);

        propertiesPanel.add(Box.createVerticalGlue());
        propertiesPanel.revalidate();
    }

    // ========================================================================
    // Widget operations
    // ========================================================================

    private UILayerDef getActiveLayer() {
        if (document == null || document.getLayers().isEmpty()) return null;
        int idx = cboLayer.getSelectedIndex();
        if (idx < 0 || idx >= document.getLayers().size()) return null;
        return document.getLayers().get(idx);
    }

    private void addWidget(UIWidgetType type) {
        UILayerDef layer = getActiveLayer();
        if (layer == null) return;

        String baseName;
        switch (type) {
            case PANEL: baseName = "panel"; break;
            case BUTTON: baseName = "button"; break;
            case TEXT_VIEW: baseName = "text"; break;
            case IMAGE: baseName = "image"; break;
            case GUIDELINE: baseName = "guideline"; break;
            default: baseName = "widget"; break;
        }

        // Generate unique name
        String name = generateUniqueName(baseName);

        UIWidgetDef widget = new UIWidgetDef(name, type);

        // Set sensible defaults
        widget.setWidthMode(UISizeMode.FIXED);
        widget.setHeightMode(UISizeMode.FIXED);

        switch (type) {
            case PANEL:
                widget.setWidth(200);
                widget.setHeight(150);
                break;
            case BUTTON:
                widget.setWidth(120);
                widget.setHeight(40);
                break;
            case TEXT_VIEW:
                widget.setWidth(150);
                widget.setHeight(30);
                break;
            case IMAGE:
                widget.setWidth(100);
                widget.setHeight(100);
                break;
            case GUIDELINE:
                widget.setGuidelineIsHorizontal(true);
                widget.setGuidelineIsPercent(true);
                widget.setGuidelinePosition(0.5f);
                break;
        }

        // Check if selected widget is a PANEL — add as child if so
        UIWidgetDef selected = canvas.getSelectedWidget();
        if (selected != null && selected.getType() == UIWidgetType.PANEL) {
            selected.addChild(widget);
        } else {
            layer.addWidget(widget);
        }

        markDirty();
        refreshWidgetTree();
        canvas.refreshLayout();
        canvas.setSelectedWidget(widget);
        showPropertiesForWidget(widget);
    }

    private void deleteSelectedWidget() {
        UIWidgetDef selected = canvas.getSelectedWidget();
        if (selected == null) return;

        UILayerDef layer = getActiveLayer();
        if (layer == null) return;

        // Remove from layer or from parent
        if (!layer.getWidgets().remove(selected)) {
            // Try removing from a parent widget
            removeWidgetRecursive(layer.getWidgets(), selected);
        }

        markDirty();
        canvas.setSelectedWidget(null);
        refreshWidgetTree();
        canvas.refreshLayout();
        clearProperties();
    }

    private boolean removeWidgetRecursive(List<UIWidgetDef> widgets, UIWidgetDef target) {
        for (UIWidgetDef w : widgets) {
            if (w.getChildren().remove(target)) return true;
            if (removeWidgetRecursive(w.getChildren(), target)) return true;
        }
        return false;
    }

    private void addLayer() {
        String name = JOptionPane.showInputDialog(this, "Layer name:", "New Layer",
                JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;
        name = name.trim();

        // Check uniqueness
        if (document.findLayerByName(name) != null) {
            JOptionPane.showMessageDialog(this, "Layer '" + name + "' already exists.",
                    "Duplicate Name", JOptionPane.WARNING_MESSAGE);
            return;
        }

        UILayerDef layer = new UILayerDef(name);
        document.addLayer(layer);
        markDirty();

        refreshLayerCombo();
        cboLayer.setSelectedIndex(document.getLayers().size() - 1);
    }

    private void removeLayer() {
        UILayerDef layer = getActiveLayer();
        if (layer == null) return;

        if (document.getLayers().size() <= 1) {
            JOptionPane.showMessageDialog(this, "Cannot remove the last layer.",
                    "Remove Layer", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Remove layer '" + layer.getName() + "' and all its widgets?",
                "Remove Layer", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        document.removeLayer(layer);
        markDirty();

        refreshLayerCombo();
        canvas.setDocument(document);
        refreshWidgetTree();
    }

    private String generateUniqueName(String baseName) {
        List<String> duplicates = document.validateUniqueNames();
        // Just increment until we find a unique name
        for (int i = 1; i < 10000; i++) {
            String candidate = baseName + i;
            if (document.findLayerByName(candidate) == null) {
                boolean found = false;
                for (UILayerDef layer : document.getLayers()) {
                    if (layer.findWidgetByName(candidate) != null) {
                        found = true;
                        break;
                    }
                }
                if (!found) return candidate;
            }
        }
        return baseName + "_" + System.currentTimeMillis();
    }

    // ========================================================================
    // Layer combo
    // ========================================================================

    private void refreshLayerCombo() {
        if (cboLayer == null) return;
        updatingProperties = true;
        cboLayer.removeAllItems();
        if (document != null) {
            for (UILayerDef layer : document.getLayers()) {
                cboLayer.addItem(layer.getName());
            }
        }
        updatingProperties = false;
    }

    private void onLayerChanged() {
        if (updatingProperties) return;
        UILayerDef layer = getActiveLayer();
        if (layer != null) {
            canvas.setActiveLayer(layer);
            refreshWidgetTree();
        }
    }

    // ========================================================================
    // Widget tree
    // ========================================================================

    private void refreshWidgetTree() {
        widgetTreeRoot.removeAllChildren();

        UILayerDef layer = getActiveLayer();
        if (layer != null) {
            DefaultMutableTreeNode layerNode = new DefaultMutableTreeNode(
                    new WidgetTreeNodeData(layer.getName(), "LAYER", null));
            widgetTreeRoot.add(layerNode);

            for (UIWidgetDef widget : layer.getWidgets()) {
                addWidgetToTree(layerNode, widget);
            }
        }

        widgetTreeModel.reload();
        expandAllTreeNodes(widgetTree);
    }

    private void addWidgetToTree(DefaultMutableTreeNode parent, UIWidgetDef widget) {
        String typeTag = widget.getType().name();
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(
                new WidgetTreeNodeData(widget.getName(), typeTag, widget));
        parent.add(node);

        for (UIWidgetDef child : widget.getChildren()) {
            addWidgetToTree(node, child);
        }
    }

    private void expandAllTreeNodes(JTree tree) {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    private void selectWidgetInTree(UIWidgetDef widget) {
        if (updatingTreeSelection) return;
        updatingTreeSelection = true;

        if (widget == null) {
            widgetTree.clearSelection();
        } else {
            DefaultMutableTreeNode node = findTreeNode(widgetTreeRoot, widget);
            if (node != null) {
                TreePath path = new TreePath(node.getPath());
                widgetTree.setSelectionPath(path);
                widgetTree.scrollPathToVisible(path);
            }
        }

        updatingTreeSelection = false;
    }

    private DefaultMutableTreeNode findTreeNode(DefaultMutableTreeNode parent, UIWidgetDef target) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            Object userObj = child.getUserObject();
            if (userObj instanceof WidgetTreeNodeData) {
                if (((WidgetTreeNodeData) userObj).widget == target) return child;
            }
            DefaultMutableTreeNode found = findTreeNode(child, target);
            if (found != null) return found;
        }
        return null;
    }

    private void onTreeSelectionChanged(TreeSelectionEvent e) {
        if (updatingTreeSelection) return;

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) widgetTree.getLastSelectedPathComponent();
        if (node == null) return;

        Object userObj = node.getUserObject();
        if (userObj instanceof WidgetTreeNodeData) {
            UIWidgetDef widget = ((WidgetTreeNodeData) userObj).widget;
            canvas.setSelectedWidget(widget);
            showPropertiesForWidget(widget);
        }
    }

    private void showTreeContextMenu(MouseEvent e) {
        TreePath path = widgetTree.getPathForLocation(e.getX(), e.getY());
        if (path == null) return;
        widgetTree.setSelectionPath(path);

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObj = node.getUserObject();
        if (!(userObj instanceof WidgetTreeNodeData)) return;

        UIWidgetDef widget = ((WidgetTreeNodeData) userObj).widget;

        JPopupMenu menu = new JPopupMenu();

        if (widget != null && widget.getType() == UIWidgetType.PANEL) {
            JMenuItem miAddPanel = new JMenuItem("Add Panel");
            miAddPanel.addActionListener(ev -> {
                canvas.setSelectedWidget(widget);
                addWidget(UIWidgetType.PANEL);
            });
            menu.add(miAddPanel);

            JMenuItem miAddButton = new JMenuItem("Add Button");
            miAddButton.addActionListener(ev -> {
                canvas.setSelectedWidget(widget);
                addWidget(UIWidgetType.BUTTON);
            });
            menu.add(miAddButton);

            JMenuItem miAddText = new JMenuItem("Add Text View");
            miAddText.addActionListener(ev -> {
                canvas.setSelectedWidget(widget);
                addWidget(UIWidgetType.TEXT_VIEW);
            });
            menu.add(miAddText);

            JMenuItem miAddImage = new JMenuItem("Add Image");
            miAddImage.addActionListener(ev -> {
                canvas.setSelectedWidget(widget);
                addWidget(UIWidgetType.IMAGE);
            });
            menu.add(miAddImage);

            menu.addSeparator();
        }

        if (widget != null) {
            JMenuItem miDelete = new JMenuItem("Delete");
            miDelete.addActionListener(ev -> {
                canvas.setSelectedWidget(widget);
                deleteSelectedWidget();
            });
            menu.add(miDelete);
        }

        menu.show(widgetTree, e.getX(), e.getY());
    }

    // ========================================================================
    // Properties display/apply
    // ========================================================================

    private void showPropertiesForWidget(UIWidgetDef widget) {
        updatingProperties = true;

        // Hide all type-specific panels
        textPropsPanel.setVisible(false);
        buttonPropsPanel.setVisible(false);
        imagePropsPanel.setVisible(false);

        if (widget == null) {
            clearProperties();
            updatingProperties = false;
            return;
        }

        txtWidgetName.setText(widget.getName());
        txtWidgetName.setEnabled(true);

        // Size
        cboWidthMode.setSelectedIndex(widget.getWidthMode().ordinal());
        cboHeightMode.setSelectedIndex(widget.getHeightMode().ordinal());
        spnWidth.setValue((double) widget.getWidth());
        spnHeight.setValue((double) widget.getHeight());

        // Bias
        spnHBias.setValue((double) widget.getHorizontalBias());
        spnVBias.setValue((double) widget.getVerticalBias());

        // Padding
        spnPaddingLeft.setValue((double) widget.getPaddingLeft());
        spnPaddingRight.setValue((double) widget.getPaddingRight());
        spnPaddingTop.setValue((double) widget.getPaddingTop());
        spnPaddingBottom.setValue((double) widget.getPaddingBottom());

        // Update constraint target dropdowns
        refreshConstraintTargets(widget);

        // Load current constraints
        loadConstraint(widget, UIConstraintSide.LEFT, cboConstraintLeft, cboConstraintLeftSide, spnMarginLeft);
        loadConstraint(widget, UIConstraintSide.RIGHT, cboConstraintRight, cboConstraintRightSide, spnMarginRight);
        loadConstraint(widget, UIConstraintSide.TOP, cboConstraintTop, cboConstraintTopSide, spnMarginTop);
        loadConstraint(widget, UIConstraintSide.BOTTOM, cboConstraintBottom, cboConstraintBottomSide, spnMarginBottom);

        // Center constraints
        chkCenterHorizontal.setSelected(widget.isCenterHorizontal());
        chkCenterVertical.setSelected(widget.isCenterVertical());

        // Type-specific
        switch (widget.getType()) {
            case TEXT_VIEW:
                textPropsPanel.setVisible(true);
                txtText.setText(widget.getText());
                spnFontSize.setValue((double) widget.getFontSize());
                cboTextAlign.setSelectedItem(widget.getTextAlignment());
                if (widget.getFontName() != null && !widget.getFontName().isEmpty()) {
                    cboFont.setSelectedItem(widget.getFontName());
                } else {
                    cboFont.setSelectedIndex(0);
                }
                break;
            case BUTTON:
                buttonPropsPanel.setVisible(true);
                txtButtonText.setText(widget.getButtonText());
                break;
            case IMAGE:
                imagePropsPanel.setVisible(true);
                txtImagePath.setText(widget.getImagePath() != null ? widget.getImagePath() : "");
                if (widget.getSpriteName() != null && !widget.getSpriteName().isEmpty()) {
                    cboSprite.setSelectedItem(widget.getSpriteName());
                } else {
                    cboSprite.setSelectedIndex(0);
                }
                break;
        }

        propertiesPanel.revalidate();
        propertiesPanel.repaint();

        updatingProperties = false;
    }

    private void clearProperties() {
        updatingProperties = true;
        txtWidgetName.setText("");
        txtWidgetName.setEnabled(false);
        cboWidthMode.setSelectedIndex(0);
        cboHeightMode.setSelectedIndex(0);
        spnWidth.setValue(0.0);
        spnHeight.setValue(0.0);
        spnHBias.setValue(0.5);
        spnVBias.setValue(0.5);
        textPropsPanel.setVisible(false);
        buttonPropsPanel.setVisible(false);
        imagePropsPanel.setVisible(false);
        updatingProperties = false;
    }

    private void refreshConstraintTargets(UIWidgetDef widget) {
        UILayerDef layer = getActiveLayer();
        if (layer == null) return;

        // Build target list: (none), parent, + all sibling widget names
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addElement("(none)");
        model.addElement("parent");

        collectSiblingNames(layer.getWidgets(), widget, model);

        cboConstraintLeft.setModel(new DefaultComboBoxModel<>(comboModelToArray(model)));
        cboConstraintRight.setModel(new DefaultComboBoxModel<>(comboModelToArray(model)));
        cboConstraintTop.setModel(new DefaultComboBoxModel<>(comboModelToArray(model)));
        cboConstraintBottom.setModel(new DefaultComboBoxModel<>(comboModelToArray(model)));
    }

    private void collectSiblingNames(List<UIWidgetDef> widgets, UIWidgetDef exclude,
                                      DefaultComboBoxModel<String> model) {
        for (UIWidgetDef w : widgets) {
            if (w != exclude) {
                model.addElement(w.getName());
            }
            collectSiblingNames(w.getChildren(), exclude, model);
        }
    }

    private String[] comboModelToArray(DefaultComboBoxModel<String> model) {
        String[] arr = new String[model.getSize()];
        for (int i = 0; i < model.getSize(); i++) {
            arr[i] = model.getElementAt(i);
        }
        return arr;
    }

    private void loadConstraint(UIWidgetDef widget, UIConstraintSide side,
                                 JComboBox<String> targetCombo, JComboBox<String> sideCombo,
                                 JSpinner marginSpinner) {
        UIConstraint constraint = widget.getConstraintForSide(side);
        if (constraint == null) {
            targetCombo.setSelectedItem("(none)");
            marginSpinner.setValue(0.0);
        } else {
            targetCombo.setSelectedItem(constraint.getTargetName());
            sideCombo.setSelectedItem(constraint.getTargetSide().name().toLowerCase());
            marginSpinner.setValue((double) constraint.getMargin());
        }
    }

    // --- Apply changes from properties to the model ---

    private void applyWidgetNameChange() {
        if (updatingProperties) return;
        UIWidgetDef widget = canvas.getSelectedWidget();
        if (widget == null) return;

        String newName = txtWidgetName.getText().trim();
        if (newName.isEmpty() || newName.equals(widget.getName())) return;

        // Check uniqueness
        for (UILayerDef layer : document.getLayers()) {
            if (layer.findWidgetByName(newName) != null) {
                JOptionPane.showMessageDialog(this, "Name '" + newName + "' already exists.",
                        "Duplicate Name", JOptionPane.WARNING_MESSAGE);
                txtWidgetName.setText(widget.getName());
                return;
            }
        }

        widget.setName(newName);
        markDirty();
        refreshWidgetTree();
    }

    private void applySizeModeChange() {
        if (updatingProperties) return;
        UIWidgetDef widget = canvas.getSelectedWidget();
        if (widget == null) return;

        widget.setWidthMode(UISizeMode.values()[cboWidthMode.getSelectedIndex()]);
        widget.setHeightMode(UISizeMode.values()[cboHeightMode.getSelectedIndex()]);
        markDirty();
        canvas.refreshLayout();
    }

    private void applySizeChange() {
        if (updatingProperties) return;
        UIWidgetDef widget = canvas.getSelectedWidget();
        if (widget == null) return;

        widget.setWidth(((Number) spnWidth.getValue()).floatValue());
        widget.setHeight(((Number) spnHeight.getValue()).floatValue());
        markDirty();
        canvas.refreshLayout();
    }

    private void applyConstraintChange() {
        if (updatingProperties) return;
        UIWidgetDef widget = canvas.getSelectedWidget();
        if (widget == null) return;

        widget.clearConstraints();

        applyOneConstraint(widget, UIConstraintSide.LEFT, cboConstraintLeft, cboConstraintLeftSide, spnMarginLeft);
        applyOneConstraint(widget, UIConstraintSide.RIGHT, cboConstraintRight, cboConstraintRightSide, spnMarginRight);
        applyOneConstraint(widget, UIConstraintSide.TOP, cboConstraintTop, cboConstraintTopSide, spnMarginTop);
        applyOneConstraint(widget, UIConstraintSide.BOTTOM, cboConstraintBottom, cboConstraintBottomSide, spnMarginBottom);

        markDirty();
        canvas.refreshLayout();
    }

    private void applyOneConstraint(UIWidgetDef widget, UIConstraintSide side,
                                     JComboBox<String> targetCombo, JComboBox<String> sideCombo,
                                     JSpinner marginSpinner) {
        String target = (String) targetCombo.getSelectedItem();
        if (target == null || "(none)".equals(target)) return;

        String targetSideStr = (String) sideCombo.getSelectedItem();
        UIConstraintSide targetSide;
        try {
            targetSide = UIConstraintSide.valueOf(targetSideStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            targetSide = side; // fallback to same side
        }

        float margin = ((Number) marginSpinner.getValue()).floatValue();
        widget.addConstraint(new UIConstraint(side, target, targetSide, margin));
    }

    private void applyBiasChange() {
        if (updatingProperties) return;
        UIWidgetDef widget = canvas.getSelectedWidget();
        if (widget == null) return;

        widget.setHorizontalBias(((Number) spnHBias.getValue()).floatValue());
        widget.setVerticalBias(((Number) spnVBias.getValue()).floatValue());
        markDirty();
        canvas.refreshLayout();
    }

    private void applyCenterChange() {
        if (updatingProperties) return;
        UIWidgetDef widget = canvas.getSelectedWidget();
        if (widget == null) return;

        widget.setCenterHorizontal(chkCenterHorizontal.isSelected());
        widget.setCenterVertical(chkCenterVertical.isSelected());
        markDirty();
        canvas.refreshLayout();
    }

    private void applyPaddingChange() {
        if (updatingProperties) return;
        UIWidgetDef widget = canvas.getSelectedWidget();
        if (widget == null) return;

        widget.setPaddingLeft(((Number) spnPaddingLeft.getValue()).floatValue());
        widget.setPaddingRight(((Number) spnPaddingRight.getValue()).floatValue());
        widget.setPaddingTop(((Number) spnPaddingTop.getValue()).floatValue());
        widget.setPaddingBottom(((Number) spnPaddingBottom.getValue()).floatValue());
        markDirty();
        canvas.refreshLayout();
    }

    private void applyTextChange() {
        if (updatingProperties) return;
        UIWidgetDef widget = canvas.getSelectedWidget();
        if (widget == null || widget.getType() != UIWidgetType.TEXT_VIEW) return;

        widget.setText(txtText.getText());
        widget.setFontSize(((Number) spnFontSize.getValue()).floatValue());
        widget.setTextAlignment((String) cboTextAlign.getSelectedItem());

        String selectedFont = (String) cboFont.getSelectedItem();
        if ("(default)".equals(selectedFont)) {
            widget.setFontName(null);
        } else {
            widget.setFontName(selectedFont);
        }

        markDirty();
        canvas.refreshLayout();
    }

    private void applyButtonTextChange() {
        if (updatingProperties) return;
        UIWidgetDef widget = canvas.getSelectedWidget();
        if (widget == null || widget.getType() != UIWidgetType.BUTTON) return;

        widget.setButtonText(txtButtonText.getText());
        markDirty();
        canvas.refreshLayout();
    }

    private void applyImagePathChange() {
        if (updatingProperties) return;
        UIWidgetDef widget = canvas.getSelectedWidget();
        if (widget == null || widget.getType() != UIWidgetType.IMAGE) return;

        widget.setImagePath(txtImagePath.getText());
        markDirty();
        canvas.refreshLayout();
    }

    private void applySpriteChange() {
        if (updatingProperties) return;
        UIWidgetDef widget = canvas.getSelectedWidget();
        if (widget == null || widget.getType() != UIWidgetType.IMAGE) return;

        String selected = (String) cboSprite.getSelectedItem();
        if ("(none)".equals(selected)) {
            widget.setSpriteName(null);
        } else {
            widget.setSpriteName(selected);
        }
        markDirty();
        canvas.refreshLayout();
    }

    private void loadSpriteNames() {
        try {
            if (projectPath != null) {
                String resourcesFolder = projectPath + "/resources";
                File resDir = new File(resourcesFolder);
                if (resDir.exists()) {
                    assetsMapping = new AssetsMapping(resourcesFolder);
                } else {
                    assetsMapping = new AssetsMapping();
                }
            } else {
                assetsMapping = new AssetsMapping();
            }

            HashMap<String, ResourceSetup2D> sprites = assetsMapping.getSpriteSheetsIndex();
            List<String> names = new ArrayList<>(sprites.keySet());
            Collections.sort(names);
            for (String name : names) {
                ResourceSetup2D res = sprites.get(name);
                cboSprite.addItem(res.name);
            }
        } catch (Exception e) {
            System.err.println("[UIDesigner] Failed to load sprite names: " + e.getMessage());
        }
    }

    private void loadFontNames() {
        try {
            if (assetsMapping == null) {
                if (projectPath != null) {
                    String resourcesFolder = projectPath + "/resources";
                    File resDir = new File(resourcesFolder);
                    if (resDir.exists()) {
                        assetsMapping = new AssetsMapping(resourcesFolder);
                    } else {
                        assetsMapping = new AssetsMapping();
                    }
                } else {
                    assetsMapping = new AssetsMapping();
                }
            }

            HashMap<String, ResourceFont> fonts = assetsMapping.getFontsIndex();
            List<String> names = new ArrayList<>(fonts.keySet());
            Collections.sort(names);
            for (String name : names) {
                ResourceFont res = fonts.get(name);
                cboFont.addItem(res.name);
            }
        } catch (Exception e) {
            System.err.println("[UIDesigner] Failed to load font names: " + e.getMessage());
        }
    }

    // ========================================================================
    // Form building helpers
    // ========================================================================

    private JLabel createBoldLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
        lbl.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
        return lbl;
    }

    private void addFormRow(String label, JComponent field) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        JLabel lbl = new JLabel(label);
        lbl.setPreferredSize(new Dimension(LABEL_WIDTH, 24));
        row.add(lbl, BorderLayout.WEST);
        field.setPreferredSize(new Dimension(0, 24));
        row.add(field, BorderLayout.CENTER);
        row.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        propertiesPanel.add(row);
    }

    private void addFormRow(String l1, JComponent f1, String l2, JComponent f2) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 0, 2);
        gbc.gridy = 0;

        gbc.gridx = 0; gbc.weightx = 0;
        JLabel lbl1 = new JLabel(l1);
        lbl1.setPreferredSize(new Dimension(24, 24));
        row.add(lbl1, gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        f1.setPreferredSize(new Dimension(0, 24));
        row.add(f1, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        gbc.insets = new Insets(0, 6, 0, 2);
        JLabel lbl2 = new JLabel(l2);
        lbl2.setPreferredSize(new Dimension(24, 24));
        row.add(lbl2, gbc);

        gbc.gridx = 3; gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 0, 0);
        f2.setPreferredSize(new Dimension(0, 24));
        row.add(f2, gbc);

        propertiesPanel.add(row);
    }

    private void addConstraintRow(String label, JComboBox<String> targetCombo, JComboBox<String> sideCombo) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 0, 4);
        gbc.gridy = 0;

        // Label (fixed width)
        gbc.gridx = 0; gbc.weightx = 0;
        JLabel lbl = new JLabel(label);
        lbl.setPreferredSize(new Dimension(56, 24));
        row.add(lbl, gbc);

        // Target combo (fills remaining space)
        gbc.gridx = 1; gbc.weightx = 1.0;
        targetCombo.setPreferredSize(new Dimension(0, 24));
        row.add(targetCombo, gbc);

        // Side combo (fixed width, enough for "bottom")
        gbc.gridx = 2; gbc.weightx = 0;
        gbc.insets = new Insets(0, 0, 0, 0);
        sideCombo.setPreferredSize(new Dimension(72, 24));
        row.add(sideCombo, gbc);

        propertiesPanel.add(row);

        targetCombo.addActionListener(e -> applyConstraintChange());
        sideCombo.addActionListener(e -> applyConstraintChange());
    }

    private void addFormRowTo(JPanel panel, String label, JComponent field) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        JLabel lbl = new JLabel(label);
        lbl.setPreferredSize(new Dimension(LABEL_WIDTH, 24));
        row.add(lbl, BorderLayout.WEST);
        field.setPreferredSize(new Dimension(0, 24));
        row.add(field, BorderLayout.CENTER);
        row.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        panel.add(row);
    }

    // ========================================================================
    // Activation/deactivation (for EditorTabPanel integration)
    // ========================================================================

    /**
     * Called when this panel becomes the active tab.
     */
    public void activatePanel() {
        // Refresh the canvas in case the document was modified externally
        if (document != null) {
            canvas.setDocument(document);
            refreshWidgetTree();
        }
    }

    /**
     * Called when switching away from this tab.
     */
    public void deactivatePanel() {
        if (dirty) {
            saveDocument();
        }
    }

    /**
     * Called when the file is being deleted.
     */
    public void clearAndDeactivatePanel() {
        deactivatePanel();
        document = null;
    }

    // ========================================================================
    // Toolbar icons (programmatic, matching DesignerPanel style)
    // ========================================================================

    private static final int ICON_SIZE = 20;
    private static final Color ICON_COLOR = new Color(220, 220, 220);

    private static Icon createUIToolbarIcon(String key) {
        BufferedImage img = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(ICON_COLOR);
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        switch (key) {
            case "panel":
                g.drawRoundRect(2, 2, 16, 16, 3, 3);
                break;
            case "button":
                g.fillRoundRect(2, 5, 16, 10, 4, 4);
                g.setColor(new Color(45, 45, 48));
                g.setFont(g.getFont().deriveFont(Font.BOLD, 8f));
                g.drawString("BTN", 4, 13);
                break;
            case "textview":
                g.setFont(g.getFont().deriveFont(Font.BOLD, 14f));
                g.drawString("T", 5, 16);
                break;
            case "image":
                g.drawRect(2, 2, 16, 16);
                // Small mountain/sun icon
                g.drawLine(2, 14, 8, 8);
                g.drawLine(8, 8, 12, 12);
                g.drawLine(12, 12, 18, 6);
                g.fillOval(12, 3, 4, 4);
                break;
            case "guideline":
                float[] dash = {3f, 2f};
                g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
                g.drawLine(10, 1, 10, 19);
                break;
            case "delete":
                g.setColor(new Color(255, 100, 100));
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(4, 4, 16, 16);
                g.drawLine(16, 4, 4, 16);
                break;
            case "addlayer":
                g.drawRoundRect(2, 2, 12, 12, 2, 2);
                g.drawRoundRect(6, 6, 12, 12, 2, 2);
                g.setColor(new Color(100, 255, 100));
                g.setStroke(new BasicStroke(2f));
                g.drawLine(15, 1, 15, 7);
                g.drawLine(12, 4, 18, 4);
                break;
            case "removelayer":
                g.drawRoundRect(2, 2, 12, 12, 2, 2);
                g.drawRoundRect(6, 6, 12, 12, 2, 2);
                g.setColor(new Color(255, 100, 100));
                g.setStroke(new BasicStroke(2f));
                g.drawLine(12, 4, 18, 4);
                break;
        }

        g.dispose();
        return new ImageIcon(img);
    }

    // ========================================================================
    // Widget tree cell renderer
    // ========================================================================

    /**
     * Data object for tree nodes — holds the widget reference.
     */
    static class WidgetTreeNodeData {
        String displayName;
        String typeTag;
        UIWidgetDef widget; // null for layer/root nodes

        WidgetTreeNodeData(String displayName, String typeTag, UIWidgetDef widget) {
            this.displayName = displayName;
            this.typeTag = typeTag;
            this.widget = widget;
        }

        @Override
        public String toString() {
            return displayName + " [" + typeTag + "]";
        }
    }

    /**
     * Custom tree cell renderer with type-specific icons.
     */
    private static class UIWidgetTreeCellRenderer extends DefaultTreeCellRenderer {
        private static final int IC_SIZE = 16;
        private static final Map<UIWidgetType, Icon> ICON_CACHE = new EnumMap<>(UIWidgetType.class);
        private static final Icon LAYER_ICON;

        static {
            for (UIWidgetType type : UIWidgetType.values()) {
                ICON_CACHE.put(type, createWidgetIcon(type));
            }
            LAYER_ICON = createLayerIcon();
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                       boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            if (value instanceof DefaultMutableTreeNode) {
                Object userObj = ((DefaultMutableTreeNode) value).getUserObject();
                if (userObj instanceof WidgetTreeNodeData) {
                    WidgetTreeNodeData data = (WidgetTreeNodeData) userObj;
                    setText(data.displayName);
                    if (data.widget != null) {
                        Icon icon = ICON_CACHE.get(data.widget.getType());
                        if (icon != null) setIcon(icon);
                    } else if ("LAYER".equals(data.typeTag)) {
                        setIcon(LAYER_ICON);
                    }
                }
            }

            return this;
        }

        private static Icon createWidgetIcon(UIWidgetType type) {
            BufferedImage img = new BufferedImage(IC_SIZE, IC_SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(200, 200, 200));
            g.setStroke(new BasicStroke(1.2f));

            switch (type) {
                case PANEL:
                    g.drawRoundRect(1, 1, 13, 13, 2, 2);
                    break;
                case BUTTON:
                    g.fillRoundRect(1, 3, 13, 9, 3, 3);
                    break;
                case TEXT_VIEW:
                    g.setFont(g.getFont().deriveFont(Font.BOLD, 12f));
                    g.drawString("T", 3, 13);
                    break;
                case IMAGE:
                    g.drawRect(1, 1, 13, 13);
                    g.drawLine(1, 11, 5, 7);
                    g.drawLine(5, 7, 9, 10);
                    g.drawLine(9, 10, 14, 5);
                    break;
                case GUIDELINE:
                    g.setColor(new Color(255, 200, 50));
                    float[] dash = {2f, 2f};
                    g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
                    g.drawLine(8, 0, 8, 15);
                    break;
            }

            g.dispose();
            return new ImageIcon(img);
        }

        private static Icon createLayerIcon() {
            BufferedImage img = new BufferedImage(IC_SIZE, IC_SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(100, 180, 255));
            g.setStroke(new BasicStroke(1.2f));
            g.drawRoundRect(1, 1, 10, 10, 2, 2);
            g.drawRoundRect(4, 4, 10, 10, 2, 2);
            g.dispose();
            return new ImageIcon(img);
        }
    }
}
