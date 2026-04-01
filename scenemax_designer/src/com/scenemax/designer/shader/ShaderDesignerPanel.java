package com.scenemax.designer.shader;

import com.jme3.math.ColorRGBA;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeCanvasContext;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

public class ShaderDesignerPanel extends JPanel {

    private final String projectPath;
    private final File shaderFile;
    private final String resourcesFolder;

    private ShaderDocument document;
    private boolean dirty = false;
    private boolean updatingUi = false;
    private Runnable onDirtyCallback;

    private ShaderPreviewApp previewApp;
    private Canvas previewCanvas;
    private JPanel previewContainer;

    private final Map<ShaderBlockType, JCheckBox> blockChecks = new EnumMap<>(ShaderBlockType.class);
    private JList<ShaderTemplatePreset> templateList;
    private JComboBox<ShaderPreviewTarget> cboPreviewTarget;
    private JButton btnColor;
    private JSpinner spnGlowStrength;
    private JSpinner spnPulseSpeed;
    private JSpinner spnTransparency;
    private JSpinner spnEdgeWidth;
    private JSpinner spnScrollSpeed;
    private JTextField txtTexture;
    private JLabel lblExportPath;

    public ShaderDesignerPanel(String projectPath, File shaderFile) {
        super(new BorderLayout());
        this.projectPath = projectPath;
        this.shaderFile = shaderFile;
        this.resourcesFolder = resolveResourcesFolder(projectPath, shaderFile);

        loadDocument();
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
        try {
            document.save(shaderFile);
            document.exportRuntimeAssets(shaderFile, resourcesFolder);
            dirty = false;
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
        if (dirty) {
            saveDocument();
        }
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
        JLabel title = new JLabel("Friendly Shader Effect Editor");
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

        cboPreviewTarget = new JComboBox<>(ShaderPreviewTarget.values());
        cboPreviewTarget.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ShaderPreviewTarget) {
                    setText(((ShaderPreviewTarget) value).getDisplayName());
                }
                return this;
            }
        });
        cboPreviewTarget.addActionListener(e -> {
            if (!updatingUi) {
                document.setPreviewTarget((ShaderPreviewTarget) cboPreviewTarget.getSelectedItem());
                markDirtyAndRefresh();
            }
        });
        previewHeader.add(cboPreviewTarget, BorderLayout.EAST);

        center.add(previewHeader, BorderLayout.NORTH);

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
        center.add(footer, BorderLayout.SOUTH);

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
        btnSave.addActionListener(e -> saveDocument());
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
            updateColorButton(document.getMainColor());
            spnGlowStrength.setValue((double) document.getGlowStrength());
            spnPulseSpeed.setValue((double) document.getPulseSpeed());
            spnTransparency.setValue((double) document.getTransparency());
            spnEdgeWidth.setValue((double) document.getEdgeWidth());
            spnScrollSpeed.setValue((double) document.getScrollSpeed());
            txtTexture.setText(document.getTexturePath());
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
