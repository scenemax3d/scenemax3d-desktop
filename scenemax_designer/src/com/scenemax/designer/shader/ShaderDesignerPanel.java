package com.scenemax.designer.shader;

import com.jme3.math.ColorRGBA;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeCanvasContext;
import com.scenemaxeng.common.types.AssetsMapping;
import com.scenemaxeng.common.types.ResourceSetup;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

public class ShaderDesignerPanel extends JPanel {

    private final String projectPath;
    private final File shaderFile;
    private final String resourcesFolder;
    private final List<String> availableModelNames = new ArrayList<>();
    private final Map<String, ResourceSetup> modelResources = new HashMap<>();

    private ShaderDocument document;
    private boolean dirty = false;
    private boolean discardEditorStateOnDeactivate = false;
    private boolean updatingUi = false;
    private Runnable onDirtyCallback;

    private ShaderPreviewApp previewApp;
    private Canvas previewCanvas;
    private JPanel previewContainer;

    private final Map<ShaderBlockType, JCheckBox> blockChecks = new EnumMap<>(ShaderBlockType.class);
    private JList<ShaderTemplatePreset> templateList;
    private JComboBox<ShaderPreviewTarget> cboPreviewTarget;
    private JComboBox<String> cboPreviewModel;
    private JLabel lblPreviewHint;
    private JPanel modelCardPanel;
    private JLabel lblModelThumb;
    private JLabel lblModelTitle;
    private JLabel lblModelMeta;
    private JButton btnColor;
    private JSpinner spnGlowStrength;
    private JSpinner spnPulseSpeed;
    private JSpinner spnTransparency;
    private JSpinner spnEdgeWidth;
    private JSpinner spnScrollSpeed;
    private JSpinner spnPreviewScale;
    private JTextField txtTexture;
    private JCheckBox chkUseOriginalTexture;
    private JLabel lblExportPath;
    private String thumbnailGenerationModel = null;

    public ShaderDesignerPanel(String projectPath, File shaderFile) {
        super(new BorderLayout());
        this.projectPath = projectPath;
        this.shaderFile = shaderFile;
        this.resourcesFolder = resolveResourcesFolder(projectPath, shaderFile);

        loadDocument();
        loadAvailableModels();
        buildUi();
        initPreview();
        refreshFromDocument();
        refreshPreview();
    }

    private void loadDocument() {
        try {
            if (shaderFile.exists() && shaderFile.length() > 0) {
                document = ShaderDocument.load(shaderFile);
            } else {
                document = ShaderDocument.createFromTemplate(shaderFile.getAbsolutePath(), ShaderTemplatePreset.TEXTURE_TINT);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            document = ShaderDocument.createFromTemplate(shaderFile.getAbsolutePath(), ShaderTemplatePreset.TEXTURE_TINT);
        }
    }

    public void saveDocument() {
        saveDocument(false);
    }

    private void saveDocument(boolean showSuccessMessage) {
        try {
            document.save(shaderFile);
            document.exportRuntimeAssets(shaderFile, resourcesFolder);
            dirty = false;
            discardEditorStateOnDeactivate = false;
            if (showSuccessMessage) {
                String exportTarget = resourcesFolder != null
                        ? new File(resourcesFolder, "shaders/" + ShaderDocument.getRuntimeName(shaderFile)).getPath()
                        : "project resources/shaders";
                JOptionPane.showMessageDialog(this,
                        "Shader saved successfully.\nExported runtime files to:\n" + exportTarget,
                        "Shader Saved", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Error saving shader document: " + ex.getMessage(),
                    "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    public void discardEditorState() {
        dirty = false;
        discardEditorStateOnDeactivate = true;
    }

    public void setOnDirtyCallback(Runnable onDirtyCallback) {
        this.onDirtyCallback = onDirtyCallback;
    }

    public void activatePanel() {
        if (previewCanvas != null && previewCanvas.getParent() != previewContainer) {
            previewContainer.add(previewCanvas, BorderLayout.CENTER);
            previewContainer.revalidate();
            previewContainer.repaint();
        }
        refreshPreview();
    }

    public void deactivatePanel() {
        if (discardEditorStateOnDeactivate) {
            discardEditorStateOnDeactivate = false;
            return;
        }
        if (dirty) {
            saveDocument();
        }
    }

    public void reloadFromDisk() {
        loadDocument();
        dirty = false;
        discardEditorStateOnDeactivate = false;
        refreshFromDocument();
        refreshPreview();
    }

    public void clearAndDeactivatePanel() {
        deactivatePanel();
        if (previewApp != null) {
            previewApp.stop();
            previewApp = null;
        }
        if (previewCanvas != null) {
            previewContainer.remove(previewCanvas);
            previewCanvas = null;
        }
    }

    private void buildUi() {
        setBackground(new Color(22, 26, 33));

        JPanel shell = new JPanel(new BorderLayout(14, 14));
        shell.setBorder(new EmptyBorder(14, 14, 14, 14));
        shell.setBackground(new Color(22, 26, 33));
        add(shell, BorderLayout.CENTER);

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        JLabel title = new JLabel("Shader Effect Editor");
        title.setForeground(new Color(245, 247, 250));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        topBar.add(title, BorderLayout.WEST);

        JLabel subtitle = new JLabel("Choose a starter, toggle blocks, tweak the feel, and keep a live preview on screen.");
        subtitle.setForeground(new Color(157, 170, 187));
        topBar.add(subtitle, BorderLayout.SOUTH);
        shell.add(topBar, BorderLayout.NORTH);

        JPanel main = new JPanel(new BorderLayout(14, 0));
        main.setOpaque(false);
        shell.add(main, BorderLayout.CENTER);

        main.add(buildLeftPanel(), BorderLayout.WEST);
        main.add(buildCenterPanel(), BorderLayout.CENTER);
        main.add(buildRightPanel(), BorderLayout.EAST);
    }

    private JPanel buildLeftPanel() {
        JPanel left = createCardPanel();
        left.setPreferredSize(new Dimension(230, 0));
        left.setLayout(new BorderLayout(0, 12));

        JPanel templatesPanel = createSectionPanel("Templates");
        DefaultListModel<ShaderTemplatePreset> model = new DefaultListModel<>();
        for (ShaderTemplatePreset preset : ShaderTemplatePreset.values()) {
            model.addElement(preset);
        }
        templateList = new JList<>(model);
        templateList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        templateList.setBackground(new Color(26, 33, 42));
        templateList.setForeground(new Color(236, 239, 244));
        templateList.setSelectionBackground(new Color(58, 104, 138));
        templateList.setSelectionForeground(Color.WHITE);
        templateList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !updatingUi) {
                applyTemplate(templateList.getSelectedValue());
            }
        });
        templatesPanel.add(new JScrollPane(templateList), BorderLayout.CENTER);
        left.add(templatesPanel, BorderLayout.NORTH);

        JPanel blocksPanel = createSectionPanel("Effect Blocks");
        JPanel blocksList = new JPanel();
        blocksList.setOpaque(false);
        blocksList.setLayout(new BoxLayout(blocksList, BoxLayout.Y_AXIS));
        for (ShaderBlockType block : ShaderBlockType.values()) {
            JCheckBox check = new JCheckBox(block.getDisplayName());
            check.setOpaque(false);
            check.setForeground(new Color(226, 232, 240));
            check.addActionListener(e -> {
                if (!updatingUi) {
                    setBlockEnabled(block, check.isSelected());
                }
            });
            blockChecks.put(block, check);
            blocksList.add(check);
        }
        blocksPanel.add(blocksList, BorderLayout.CENTER);
        left.add(blocksPanel, BorderLayout.CENTER);

        return left;
    }

    private JPanel buildCenterPanel() {
        JPanel center = createCardPanel();
        center.setLayout(new BorderLayout(0, 12));

        JPanel previewHeader = new JPanel(new BorderLayout());
        previewHeader.setOpaque(false);

        JLabel lbl = new JLabel("Live Preview");
        lbl.setForeground(new Color(245, 247, 250));
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 18f));
        previewHeader.add(lbl, BorderLayout.WEST);

        JPanel previewHeaderLeft = new JPanel();
        previewHeaderLeft.setOpaque(false);
        previewHeaderLeft.setLayout(new BoxLayout(previewHeaderLeft, BoxLayout.Y_AXIS));
        previewHeaderLeft.add(lbl);

        lblPreviewHint = new JLabel("Try the effect on a quick primitive or one of your imported models.");
        lblPreviewHint.setForeground(new Color(149, 164, 182));
        lblPreviewHint.setFont(lblPreviewHint.getFont().deriveFont(Font.PLAIN, 12f));
        previewHeaderLeft.add(lblPreviewHint);

        previewHeader.add(previewHeaderLeft, BorderLayout.WEST);

        cboPreviewTarget = new JComboBox<>(ShaderPreviewTarget.values());
        cboPreviewTarget.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ShaderPreviewTarget) {
                    setText("Target: " + ((ShaderPreviewTarget) value).getDisplayName());
                }
                return this;
            }
        });
        cboPreviewTarget.addActionListener(e -> {
            if (!updatingUi) {
                document.setPreviewTarget((ShaderPreviewTarget) cboPreviewTarget.getSelectedItem());
                if (document.getPreviewTarget() == ShaderPreviewTarget.MODEL
                        && (document.getPreviewModelName() == null || document.getPreviewModelName().isBlank())
                        && !availableModelNames.isEmpty()) {
                    document.setPreviewModelName(availableModelNames.get(0));
                }
                if (cboPreviewModel != null) {
                    cboPreviewModel.setVisible(document.getPreviewTarget() == ShaderPreviewTarget.MODEL);
                }
                if (modelCardPanel != null) {
                    modelCardPanel.setVisible(document.getPreviewTarget() == ShaderPreviewTarget.MODEL);
                }
                markDirtyAndRefresh();
            }
        });
        JPanel previewControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        previewControls.setOpaque(false);
        previewControls.add(cboPreviewTarget);

        cboPreviewModel = new JComboBox<>(availableModelNames.toArray(new String[0]));
        cboPreviewModel.setPreferredSize(new Dimension(170, 28));
        cboPreviewModel.setToolTipText("Project model used for shader preview");
        cboPreviewModel.addActionListener(e -> {
            if (!updatingUi && cboPreviewModel.getSelectedItem() != null) {
                document.setPreviewModelName((String) cboPreviewModel.getSelectedItem());
                if (document.getPreviewTarget() != ShaderPreviewTarget.MODEL) {
                    document.setPreviewTarget(ShaderPreviewTarget.MODEL);
                    cboPreviewTarget.setSelectedItem(ShaderPreviewTarget.MODEL);
                }
                markDirtyAndRefresh();
            }
        });
        previewControls.add(cboPreviewModel);
        JPanel previewHeaderWrap = new JPanel(new BorderLayout());
        previewHeaderWrap.setOpaque(false);
        previewHeaderWrap.add(previewHeaderLeft, BorderLayout.WEST);
        previewHeaderWrap.add(previewControls, BorderLayout.EAST);

        center.add(previewHeaderWrap, BorderLayout.NORTH);

        modelCardPanel = buildModelCard();
        center.add(modelCardPanel, BorderLayout.SOUTH);

        previewContainer = new JPanel(new BorderLayout());
        previewContainer.setOpaque(true);
        previewContainer.setBackground(new Color(10, 14, 18));
        previewContainer.setBorder(BorderFactory.createLineBorder(new Color(52, 65, 82), 1));
        center.add(previewContainer, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(6, 0, 0, 0));
        lblExportPath = new JLabel();
        lblExportPath.setForeground(new Color(149, 164, 182));
        footer.add(lblExportPath, BorderLayout.CENTER);
        previewContainer.add(footer, BorderLayout.SOUTH);

        return center;
    }

    private JPanel buildRightPanel() {
        JPanel right = createCardPanel();
        right.setPreferredSize(new Dimension(290, 0));
        right.setLayout(new BorderLayout());

        JPanel params = createSectionPanel("Parameters");
        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        btnColor = new JButton("Pick Main Color");
        btnColor.setHorizontalAlignment(SwingConstants.LEFT);
        btnColor.addActionListener(e -> chooseMainColor());
        addRow(form, "Main Color", btnColor);

        spnGlowStrength = createSpinner(0.15, 0.0, 5.0, 0.05);
        addRow(form, "Glow Strength", spnGlowStrength);
        spnGlowStrength.addChangeListener(e -> {
            if (!updatingUi) {
                document.setGlowStrength(((Double) spnGlowStrength.getValue()).floatValue());
                markDirtyAndRefresh();
            }
        });

        spnPulseSpeed = createSpinner(0.55, 0.0, 5.0, 0.05);
        addRow(form, "Pulse Speed", spnPulseSpeed);
        spnPulseSpeed.addChangeListener(e -> {
            if (!updatingUi) {
                document.setPulseSpeed(((Double) spnPulseSpeed.getValue()).floatValue());
                markDirtyAndRefresh();
            }
        });

        spnTransparency = createSpinner(0.05, 0.0, 1.0, 0.02);
        addRow(form, "Transparency", spnTransparency);
        spnTransparency.addChangeListener(e -> {
            if (!updatingUi) {
                document.setTransparency(((Double) spnTransparency.getValue()).floatValue());
                markDirtyAndRefresh();
            }
        });

        spnEdgeWidth = createSpinner(0.15, 0.01, 1.0, 0.01);
        addRow(form, "Edge Width", spnEdgeWidth);
        spnEdgeWidth.addChangeListener(e -> {
            if (!updatingUi) {
                document.setEdgeWidth(((Double) spnEdgeWidth.getValue()).floatValue());
                markDirtyAndRefresh();
            }
        });

        spnScrollSpeed = createSpinner(0.35, 0.0, 5.0, 0.05);
        addRow(form, "Scroll Speed", spnScrollSpeed);
        spnScrollSpeed.addChangeListener(e -> {
            if (!updatingUi) {
                document.setScrollSpeed(((Double) spnScrollSpeed.getValue()).floatValue());
                markDirtyAndRefresh();
            }
        });

        spnPreviewScale = createSpinner(1.0, 0.1, 10.0, 0.1);
        addRow(form, "Preview Scale", spnPreviewScale);
        spnPreviewScale.addChangeListener(e -> {
            if (!updatingUi) {
                document.setPreviewScale(((Double) spnPreviewScale.getValue()).floatValue());
                markDirtyAndRefresh();
            }
        });

        txtTexture = new JTextField();
        txtTexture.addActionListener(e -> {
            document.setTexturePath(txtTexture.getText());
            markDirtyAndRefresh();
        });
        JButton btnBrowse = new JButton("Browse...");
        btnBrowse.addActionListener(e -> browseTexture());

        JPanel textureRow = new JPanel(new BorderLayout(6, 0));
        textureRow.setOpaque(false);
        textureRow.add(txtTexture, BorderLayout.CENTER);
        textureRow.add(btnBrowse, BorderLayout.EAST);
        addRow(form, "Texture", textureRow);

        chkUseOriginalTexture = new JCheckBox("Use Original Texture");
        chkUseOriginalTexture.setOpaque(false);
        chkUseOriginalTexture.setSelected(true);
        chkUseOriginalTexture.addActionListener(e -> {
            if (!updatingUi) {
                document.setUseOriginalTexture(chkUseOriginalTexture.isSelected());
                markDirtyAndRefresh();
            }
        });
        form.add(chkUseOriginalTexture);

        JTextArea tip = new JTextArea(
                "Tip: templates are just starting points. You can mix blocks freely, then save to generate runtime-ready .j3md/.j3m/.vert/.frag files.");
        tip.setOpaque(false);
        tip.setLineWrap(true);
        tip.setWrapStyleWord(true);
        tip.setEditable(false);
        tip.setForeground(new Color(155, 169, 186));
        tip.setBorder(new EmptyBorder(12, 0, 0, 0));
        form.add(tip);

        params.add(form, BorderLayout.NORTH);
        right.add(params, BorderLayout.CENTER);

        JButton btnSave = new JButton("Save Shader");
        btnSave.addActionListener(e -> saveDocument(true));
        right.add(btnSave, BorderLayout.SOUTH);

        return right;
    }

    private void initPreview() {
        previewApp = new ShaderPreviewApp();
        previewApp.setResourcesFolder(resourcesFolder);

        AppSettings settings = new AppSettings(true);
        settings.setWidth(900);
        settings.setHeight(640);
        settings.setSamples(4);
        settings.setVSync(true);
        settings.setFrameRate(60);
        settings.setGammaCorrection(false);

        previewApp.setSettings(settings);
        previewApp.setPauseOnLostFocus(false);
        previewApp.setShowSettings(false);
        previewApp.createCanvas();

        JmeCanvasContext ctx = (JmeCanvasContext) previewApp.getContext();
        ctx.setSystemListener(previewApp);
        previewCanvas = ctx.getCanvas();
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
    }

    private void refreshFromDocument() {
        updatingUi = true;
        try {
            templateList.setSelectedValue(document.getTemplate(), true);
            cboPreviewTarget.setSelectedItem(document.getPreviewTarget());
            if (document.getPreviewModelName() != null && !document.getPreviewModelName().isBlank()) {
                cboPreviewModel.setSelectedItem(document.getPreviewModelName());
            } else if (!availableModelNames.isEmpty()) {
                cboPreviewModel.setSelectedIndex(0);
            }
            cboPreviewModel.setEnabled(!availableModelNames.isEmpty());
            cboPreviewModel.setVisible(document.getPreviewTarget() == ShaderPreviewTarget.MODEL);
            modelCardPanel.setVisible(document.getPreviewTarget() == ShaderPreviewTarget.MODEL);
            if (document.getPreviewTarget() == ShaderPreviewTarget.MODEL) {
                lblPreviewHint.setText("Previewing on one of your project models.");
            } else {
                lblPreviewHint.setText("Try the effect on a quick primitive or one of your imported models.");
            }
            refreshModelCard();
            updateColorButton(document.getMainColor());
            spnGlowStrength.setValue((double) document.getGlowStrength());
            spnPulseSpeed.setValue((double) document.getPulseSpeed());
            spnTransparency.setValue((double) document.getTransparency());
            spnEdgeWidth.setValue((double) document.getEdgeWidth());
            spnScrollSpeed.setValue((double) document.getScrollSpeed());
            spnPreviewScale.setValue((double) document.getPreviewScale());
            txtTexture.setText(document.getTexturePath());
            chkUseOriginalTexture.setSelected(document.isUseOriginalTexture());
            for (Map.Entry<ShaderBlockType, JCheckBox> entry : blockChecks.entrySet()) {
                entry.getValue().setSelected(document.getBlocks().contains(entry.getKey()));
            }
            if (resourcesFolder != null) {
                lblExportPath.setText("Exports to: " + new File(resourcesFolder, "shaders/" + ShaderDocument.getRuntimeName(shaderFile)).getPath());
            } else {
                lblExportPath.setText("Exports to project resources/shaders when a project is active.");
            }
        } finally {
            updatingUi = false;
        }
    }

    private void applyTemplate(ShaderTemplatePreset preset) {
        if (preset == null) {
            return;
        }
        preset.applyTo(document);
        refreshFromDocument();
        markDirtyAndRefresh();
    }

    private void setBlockEnabled(ShaderBlockType block, boolean enabled) {
        if (enabled) {
            document.getBlocks().add(block);
        } else {
            document.getBlocks().remove(block);
        }
        markDirtyAndRefresh();
    }

    private void chooseMainColor() {
        ColorRGBA c = document.getMainColor();
        Color awt = new Color(
                clampColor(c.r),
                clampColor(c.g),
                clampColor(c.b),
                clampColor(c.a)
        );
        Color chosen = JColorChooser.showDialog(this, "Pick Main Color", awt);
        if (chosen == null) {
            return;
        }
        document.setMainColor(new ColorRGBA(
                chosen.getRed() / 255f,
                chosen.getGreen() / 255f,
                chosen.getBlue() / 255f,
                chosen.getAlpha() / 255f
        ));
        updateColorButton(document.getMainColor());
        markDirtyAndRefresh();
    }

    private void browseTexture() {
        if (resourcesFolder == null) {
            JOptionPane.showMessageDialog(this,
                    "Open a project first so the editor can browse the project's resources folder.",
                    "No Project Resources", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser(new File(resourcesFolder));
        chooser.setDialogTitle("Choose Texture From Resources");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
            return;
        }
        try {
            File selected = chooser.getSelectedFile().getCanonicalFile();
            File resourcesRoot = new File(resourcesFolder).getCanonicalFile();
            String relative = resourcesRoot.toPath().relativize(selected.toPath()).toString().replace("\\", "/");
            txtTexture.setText(relative);
            document.setTexturePath(relative);
            markDirtyAndRefresh();
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Failed to resolve texture path: " + ex.getMessage(),
                    "Texture Selection Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void markDirtyAndRefresh() {
        dirty = true;
        if (onDirtyCallback != null) {
            onDirtyCallback.run();
        }
        refreshPreview();
    }

    private void refreshPreview() {
        if (previewApp != null) {
            previewApp.updatePreview(shaderFile, document);
        }
        if (document.getPreviewTarget() == ShaderPreviewTarget.MODEL) {
            scheduleModelThumbnailGeneration();
        }
    }

    private void loadAvailableModels() {
        availableModelNames.clear();
        modelResources.clear();
        if (resourcesFolder == null || resourcesFolder.isBlank()) {
            return;
        }
        try {
            AssetsMapping mapping = new AssetsMapping(new File(resourcesFolder).getCanonicalPath());
            List<ResourceSetup> setups = new ArrayList<>(mapping.get3DModelsIndex().values());
            setups.sort(Comparator.comparing(res -> res.name.toLowerCase()));
            for (ResourceSetup setup : setups) {
                availableModelNames.add(setup.name);
                modelResources.put(setup.name, setup);
            }
            if ((document.getPreviewModelName() == null || document.getPreviewModelName().isBlank()) && !availableModelNames.isEmpty()) {
                document.setPreviewModelName(availableModelNames.get(0));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private JPanel buildModelCard() {
        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.setOpaque(true);
        panel.setBackground(new Color(22, 30, 38));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(56, 68, 84), 1),
                new EmptyBorder(10, 10, 10, 10)
        ));

        lblModelThumb = new JLabel(createGenericModelThumbnail());
        lblModelThumb.setPreferredSize(new Dimension(72, 72));
        panel.add(lblModelThumb, BorderLayout.WEST);

        JPanel textWrap = new JPanel();
        textWrap.setOpaque(false);
        textWrap.setLayout(new BoxLayout(textWrap, BoxLayout.Y_AXIS));

        lblModelTitle = new JLabel("No model selected");
        lblModelTitle.setForeground(new Color(239, 244, 248));
        lblModelTitle.setFont(lblModelTitle.getFont().deriveFont(Font.BOLD, 14f));
        textWrap.add(lblModelTitle);

        lblModelMeta = new JLabel("A cached preview thumbnail will appear here.");
        lblModelMeta.setForeground(new Color(149, 164, 182));
        lblModelMeta.setFont(lblModelMeta.getFont().deriveFont(Font.PLAIN, 12f));
        textWrap.add(Box.createVerticalStrut(4));
        textWrap.add(lblModelMeta);

        panel.add(textWrap, BorderLayout.CENTER);
        panel.setVisible(false);
        return panel;
    }

    private void refreshModelCard() {
        if (lblModelTitle == null || lblModelMeta == null || lblModelThumb == null) {
            return;
        }
        String modelName = document.getPreviewModelName();
        if (modelName == null || modelName.isBlank()) {
            lblModelTitle.setText("No model selected");
            lblModelMeta.setText("Choose a project model to preview the shader on it.");
            lblModelThumb.setIcon(createGenericModelThumbnail());
            return;
        }
        lblModelTitle.setText(modelName);
        ResourceSetup setup = modelResources.get(modelName);
        String pathText = setup != null && setup.path != null ? setup.path.replace("\\", "/") : "Project model";
        lblModelMeta.setText(pathText);

        File cacheFile = getCachedThumbnailFile(modelName);
        if (cacheFile.exists()) {
            try {
                BufferedImage img = ImageIO.read(cacheFile);
                if (img != null) {
                    lblModelThumb.setIcon(new ImageIcon(img));
                    return;
                }
            } catch (IOException ignored) {
            }
        }
        lblModelThumb.setIcon(createGenericModelThumbnail());
    }

    private void scheduleModelThumbnailGeneration() {
        if (previewCanvas == null) {
            return;
        }
        String modelName = document.getPreviewModelName();
        if (modelName == null || modelName.isBlank()) {
            return;
        }
        File cacheFile = getCachedThumbnailFile(modelName);
        if (cacheFile.exists() || modelName.equals(thumbnailGenerationModel)) {
            refreshModelCard();
            return;
        }
        thumbnailGenerationModel = modelName;
        Timer timer = new Timer(900, e -> captureAndCacheThumbnail(modelName));
        timer.setRepeats(false);
        timer.start();
    }

    private void captureAndCacheThumbnail(String modelName) {
        if (previewCanvas == null || !modelName.equals(document.getPreviewModelName())) {
            thumbnailGenerationModel = null;
            return;
        }
        int width = Math.max(1, previewCanvas.getWidth());
        int height = Math.max(1, previewCanvas.getHeight());
        if (width < 10 || height < 10) {
            thumbnailGenerationModel = null;
            return;
        }

        BufferedImage snapshot = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = snapshot.createGraphics();
        previewCanvas.paint(g);
        g.dispose();

        SwingWorker<BufferedImage, Void> worker = new SwingWorker<>() {
            @Override
            protected BufferedImage doInBackground() throws Exception {
                int crop = Math.min(snapshot.getWidth(), snapshot.getHeight());
                int x = (snapshot.getWidth() - crop) / 2;
                int y = Math.max(0, (snapshot.getHeight() - crop) / 3);
                y = Math.min(y, snapshot.getHeight() - crop);
                BufferedImage square = snapshot.getSubimage(x, y, crop, crop);

                BufferedImage scaled = new BufferedImage(72, 72, BufferedImage.TYPE_INT_ARGB);
                Graphics2D gg = scaled.createGraphics();
                gg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                gg.drawImage(square, 0, 0, 72, 72, null);
                gg.dispose();

                File cacheFile = getCachedThumbnailFile(modelName);
                File parent = cacheFile.getParentFile();
                if (!parent.exists()) {
                    parent.mkdirs();
                }
                ImageIO.write(scaled, "png", cacheFile);
                return scaled;
            }

            @Override
            protected void done() {
                try {
                    BufferedImage img = get();
                    if (modelName.equals(document.getPreviewModelName())) {
                        lblModelThumb.setIcon(new ImageIcon(img));
                    }
                } catch (Exception ignored) {
                } finally {
                    thumbnailGenerationModel = null;
                    refreshModelCard();
                }
            }
        };
        worker.execute();
    }

    private File getCachedThumbnailFile(String modelName) {
        String key = sanitizeFileName(modelName);
        ResourceSetup setup = modelResources.get(modelName);
        if (setup != null && setup.path != null) {
            key += "_" + Integer.toHexString(setup.path.toLowerCase().hashCode());
        }
        return new File(getThumbnailCacheDir(), key + ".png");
    }

    private File getThumbnailCacheDir() {
        File root = new File("tmp/shader_model_thumbs");
        if (projectPath != null && !projectPath.isBlank()) {
            String projectKey = sanitizeFileName(new File(projectPath).getName());
            return new File(root, projectKey);
        }
        return root;
    }

    private String sanitizeFileName(String value) {
        return value == null ? "model" : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private Icon createGenericModelThumbnail() {
        BufferedImage img = new BufferedImage(72, 72, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(28, 37, 46));
        g.fillRoundRect(0, 0, 72, 72, 12, 12);

        GradientPaint paint = new GradientPaint(12, 14, new Color(80, 209, 195), 58, 58, new Color(255, 170, 83));
        g.setPaint(paint);
        Polygon front = new Polygon(new int[]{22, 42, 42, 22}, new int[]{26, 26, 46, 46}, 4);
        Polygon top = new Polygon(new int[]{22, 32, 52, 42}, new int[]{26, 16, 16, 26}, 4);
        Polygon side = new Polygon(new int[]{42, 52, 52, 42}, new int[]{26, 16, 36, 46}, 4);
        g.fillPolygon(front);
        g.fillPolygon(top);
        g.fillPolygon(side);

        g.setColor(new Color(255, 255, 255, 170));
        g.setStroke(new BasicStroke(1.5f));
        g.drawPolygon(front);
        g.drawPolygon(top);
        g.drawPolygon(side);

        g.dispose();
        return new ImageIcon(img);
    }

    private JPanel createCardPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(new Color(28, 34, 43));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(56, 68, 84), 1),
                new EmptyBorder(12, 12, 12, 12)
        ));
        return panel;
    }

    private JPanel createSectionPanel(String title) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        JLabel header = new JLabel(title);
        header.setForeground(new Color(235, 240, 245));
        header.setFont(header.getFont().deriveFont(Font.BOLD, 15f));
        header.setBorder(new EmptyBorder(0, 0, 10, 0));
        panel.add(header, BorderLayout.NORTH);
        return panel;
    }

    private void addRow(JPanel form, String label, JComponent input) {
        JPanel row = new JPanel(new BorderLayout(0, 6));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(0, 0, 10, 0));

        JLabel lbl = new JLabel(label);
        lbl.setForeground(new Color(196, 204, 214));
        row.add(lbl, BorderLayout.NORTH);
        row.add(input, BorderLayout.CENTER);

        form.add(row);
    }

    private JSpinner createSpinner(double value, double min, double max, double step) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
        spinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        return spinner;
    }

    private void updateColorButton(ColorRGBA color) {
        Color awt = new Color(clampColor(color.r), clampColor(color.g), clampColor(color.b));
        btnColor.setBackground(awt);
        btnColor.setForeground(awt.getRed() + awt.getGreen() + awt.getBlue() > 420 ? Color.BLACK : Color.WHITE);
        btnColor.setText(String.format("R %.0f  G %.0f  B %.0f", color.r * 255f, color.g * 255f, color.b * 255f));
        btnColor.setOpaque(true);
        btnColor.setBorderPainted(false);
    }

    private int clampColor(float val) {
        return Math.max(0, Math.min(255, Math.round(val * 255f)));
    }

    private static String resolveResourcesFolder(String projectPath, File shaderFile) {
        if (projectPath != null && !projectPath.isBlank()) {
            return new File(projectPath, "resources").getPath();
        }
        if (shaderFile != null && shaderFile.getParentFile() != null && shaderFile.getParentFile().getParentFile() != null) {
            return new File(shaderFile.getParentFile().getParentFile(), "resources").getPath();
        }
        return null;
    }
}
