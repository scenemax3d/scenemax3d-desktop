package com.scenemax.designer.material;

import com.jme3.math.ColorRGBA;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeCanvasContext;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class MaterialDesignerPanel extends JPanel {

    private final String projectPath;
    private final File materialFile;
    private final String resourcesFolder;

    private MaterialDocument document;
    private boolean dirty = false;
    private boolean discardEditorStateOnDeactivate = false;
    private boolean updatingUi = false;
    private Runnable onDirtyCallback;
    private Runnable onSavedCallback;

    private MaterialPreviewApp previewApp;
    private Canvas previewCanvas;
    private JPanel previewContainer;

    private JList<MaterialTemplatePreset> templateList;
    private JList<String> starterTextureList;
    private JComboBox<MaterialPreviewShape> cboPreviewShape;
    private JButton btnBaseColor;
    private JButton btnGlowColor;
    private JSlider sldAmbient;
    private JSlider sldSpecular;
    private JSlider sldShininess;
    private JSlider sldOpacity;
    private JSlider sldGlow;
    private JSlider sldAlphaDiscard;
    private JSlider sldPreviewScale;
    private JCheckBox chkTransparent;
    private JCheckBox chkDoubleSided;
    private JTextField txtDiffuse;
    private JTextField txtNormal;
    private JTextField txtGlow;
    private JLabel lblExportPath;
    private final Map<String, File> starterTexturePacks = new LinkedHashMap<>();

    public MaterialDesignerPanel(String projectPath, File materialFile) {
        super(new BorderLayout());
        this.projectPath = projectPath;
        this.materialFile = materialFile;
        this.resourcesFolder = resolveResourcesFolder(projectPath, materialFile);

        loadDocument();
        buildUi();
        initPreview();
        refreshFromDocument();
        refreshPreview();
    }

    private void loadDocument() {
        try {
            if (materialFile.exists() && materialFile.length() > 0) {
                document = MaterialDocument.load(materialFile);
            } else {
                document = MaterialDocument.createFromTemplate(materialFile.getAbsolutePath(), MaterialTemplatePreset.MATTE_PAINT);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            document = MaterialDocument.createFromTemplate(materialFile.getAbsolutePath(), MaterialTemplatePreset.MATTE_PAINT);
        }
    }

    public void saveDocument() {
        saveDocument(false);
    }

    private void saveDocument(boolean showSuccessMessage) {
        try {
            document.save(materialFile);
            document.exportRuntimeAssets(materialFile, resourcesFolder);
            dirty = false;
            discardEditorStateOnDeactivate = false;
            if (onSavedCallback != null) {
                onSavedCallback.run();
            }
            if (showSuccessMessage) {
                String exportTarget = resourcesFolder != null
                        ? MaterialDocument.getRuntimeFolder(materialFile, resourcesFolder).getAbsolutePath()
                        : "project resources/material";
                JOptionPane.showMessageDialog(this,
                        "Material saved successfully.\nExported runtime files to:\n" + exportTarget,
                        "Material Saved", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Error saving material document: " + ex.getMessage(),
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

    public void setOnSavedCallback(Runnable onSavedCallback) {
        this.onSavedCallback = onSavedCallback;
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
        } else if (dirty) {
            saveDocument();
        }
    }

    public void reloadFromDisk() {
        loadDocument();
        dirty = false;
        discardEditorStateOnDeactivate = false;
        refreshFromDocument();
        refreshPreview();
        if (onSavedCallback != null) {
            onSavedCallback.run();
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
        setBackground(new Color(24, 28, 34));

        JPanel shell = new JPanel(new BorderLayout(14, 14));
        shell.setBorder(new EmptyBorder(14, 14, 14, 14));
        shell.setBackground(new Color(24, 28, 34));
        add(shell, BorderLayout.CENTER);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel title = new JLabel("Material Builder");
        title.setForeground(new Color(244, 247, 250));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        top.add(title, BorderLayout.WEST);

        JLabel subtitle = new JLabel("Shape the look, preview it on a box or sphere, and export a runtime-ready material.");
        subtitle.setForeground(new Color(154, 167, 185));
        top.add(subtitle, BorderLayout.SOUTH);
        shell.add(top, BorderLayout.NORTH);

        JPanel main = new JPanel(new BorderLayout(14, 0));
        main.setOpaque(false);
        shell.add(main, BorderLayout.CENTER);

        main.add(buildTemplatesPanel(), BorderLayout.WEST);

        JPanel previewPanel = buildPreviewPanel();
        JPanel propertiesPanel = buildPropertiesPanel();

        previewPanel.setMinimumSize(new Dimension(340, 320));
        propertiesPanel.setMinimumSize(new Dimension(280, 320));

        JSplitPane contentSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, previewPanel, propertiesPanel);
        contentSplit.setBorder(BorderFactory.createEmptyBorder());
        contentSplit.setOpaque(false);
        contentSplit.setContinuousLayout(true);
        contentSplit.setOneTouchExpandable(true);
        contentSplit.setResizeWeight(0.7);
        contentSplit.setDividerLocation(760);

        main.add(contentSplit, BorderLayout.CENTER);
    }

    private JPanel buildTemplatesPanel() {
        JPanel left = createCardPanel();
        left.setPreferredSize(new Dimension(230, 0));
        left.setLayout(new BorderLayout(0, 12));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JPanel section = createSectionPanel("Templates");
        DefaultListModel<MaterialTemplatePreset> model = new DefaultListModel<>();
        for (MaterialTemplatePreset preset : MaterialTemplatePreset.values()) {
            model.addElement(preset);
        }
        templateList = new JList<>(model);
        templateList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        templateList.setBackground(new Color(26, 33, 42));
        templateList.setForeground(new Color(236, 239, 244));
        templateList.setSelectionBackground(new Color(68, 108, 136));
        templateList.setSelectionForeground(Color.WHITE);
        templateList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !updatingUi) {
                MaterialTemplatePreset preset = templateList.getSelectedValue();
                if (preset != null) {
                    preset.applyTo(document);
                    refreshFromDocument();
                    markDirtyAndRefresh();
                }
            }
        });
        section.add(new JScrollPane(templateList), BorderLayout.CENTER);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, 250));
        content.add(section);
        content.add(Box.createVerticalStrut(12));
        content.add(buildStarterTexturesPanel());

        left.add(content, BorderLayout.CENTER);
        return left;
    }

    private JPanel buildStarterTexturesPanel() {
        JPanel section = createSectionPanel("Starter Textures");
        loadStarterTexturePacks();

        DefaultListModel<String> model = new DefaultListModel<>();
        for (String name : starterTexturePacks.keySet()) {
            model.addElement(name);
        }

        starterTextureList = new JList<>(model);
        starterTextureList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        starterTextureList.setVisibleRowCount(8);
        starterTextureList.setBackground(new Color(26, 33, 42));
        starterTextureList.setForeground(new Color(236, 239, 244));
        starterTextureList.setSelectionBackground(new Color(74, 118, 87));
        starterTextureList.setSelectionForeground(Color.WHITE);
        starterTextureList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    applySelectedStarterPack();
                }
            }
        });

        section.add(new JScrollPane(starterTextureList), BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0, 6));
        footer.setOpaque(false);
        JLabel hint = new JLabel("Applies diffuse, normal, and glow together.");
        hint.setForeground(new Color(149, 164, 182));
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 11f));
        footer.add(hint, BorderLayout.NORTH);

        JButton applyButton = new JButton("Use Selected Pack");
        applyButton.addActionListener(e -> applySelectedStarterPack());
        footer.add(applyButton, BorderLayout.SOUTH);

        section.add(footer, BorderLayout.SOUTH);
        return section;
    }

    private JPanel buildPreviewPanel() {
        JPanel center = createCardPanel();
        center.setLayout(new BorderLayout(0, 12));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel titleWrap = new JPanel();
        titleWrap.setOpaque(false);
        titleWrap.setLayout(new BoxLayout(titleWrap, BoxLayout.Y_AXIS));
        JLabel lbl = new JLabel("Live Preview");
        lbl.setForeground(new Color(245, 247, 250));
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 18f));
        titleWrap.add(lbl);

        JLabel hint = new JLabel("Drag inside the preview to rotate. Use the wheel or the scale slider to size it.");
        hint.setForeground(new Color(149, 164, 182));
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 12f));
        titleWrap.add(hint);
        header.add(titleWrap, BorderLayout.WEST);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controls.setOpaque(false);
        cboPreviewShape = new JComboBox<>(MaterialPreviewShape.values());
        cboPreviewShape.addActionListener(e -> {
            if (!updatingUi) {
                document.setPreviewShape((MaterialPreviewShape) cboPreviewShape.getSelectedItem());
                markDirtyAndRefresh();
            }
        });
        controls.add(cboPreviewShape);
        header.add(controls, BorderLayout.EAST);
        center.add(header, BorderLayout.NORTH);

        previewContainer = new JPanel(new BorderLayout());
        previewContainer.setOpaque(true);
        previewContainer.setBackground(new Color(10, 14, 18));
        previewContainer.setBorder(BorderFactory.createLineBorder(new Color(52, 65, 82), 1));
        center.add(previewContainer, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(8, 0, 0, 0));
        lblExportPath = new JLabel();
        lblExportPath.setForeground(new Color(149, 164, 182));
        footer.add(lblExportPath, BorderLayout.CENTER);
        center.add(footer, BorderLayout.SOUTH);

        return center;
    }

    private JPanel buildPropertiesPanel() {
        JPanel right = createCardPanel();
        right.setPreferredSize(new Dimension(340, 0));
        right.setLayout(new BorderLayout(0, 12));

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        body.add(createTextureSection());
        body.add(Box.createVerticalStrut(10));
        body.add(createSurfaceSection());
        body.add(Box.createVerticalStrut(10));
        body.add(createPreviewSection());

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        right.add(scroll, BorderLayout.CENTER);

        JButton btnSave = new JButton("Save And Export");
        btnSave.addActionListener(e -> saveDocument(true));
        right.add(btnSave, BorderLayout.SOUTH);
        return right;
    }

    private JPanel createTextureSection() {
        JPanel section = createSectionPanel("Texture Slots");
        Box box = Box.createVerticalBox();
        txtDiffuse = createTextureRow(box, "Diffuse");
        txtNormal = createTextureRow(box, "Normal");
        txtGlow = createTextureRow(box, "Glow");
        section.add(box, BorderLayout.CENTER);
        return section;
    }

    private JPanel createSurfaceSection() {
        JPanel section = createSectionPanel("Surface");
        Box box = Box.createVerticalBox();

        btnBaseColor = createColorButton("Base Color", document.getBaseColor(), color -> {
            document.setBaseColor(color);
            markDirtyAndRefresh();
        });
        box.add(btnBaseColor);
        box.add(Box.createVerticalStrut(8));

        btnGlowColor = createColorButton("Glow Color", document.getGlowColor(), color -> {
            document.setGlowColor(color);
            markDirtyAndRefresh();
        });
        box.add(btnGlowColor);
        box.add(Box.createVerticalStrut(10));

        sldAmbient = createFloatSlider(box, "Ambient Strength", 0, 100, 40, value -> document.setAmbientStrength(value / 100f));
        sldSpecular = createFloatSlider(box, "Specular Strength", 0, 200, 100, value -> document.setSpecularStrength(value / 100f));
        sldShininess = createFloatSlider(box, "Shininess", 0, 128, 32, document::setShininess);
        sldOpacity = createFloatSlider(box, "Opacity", 0, 100, 100, value -> document.setOpacity(value / 100f));
        sldGlow = createFloatSlider(box, "Glow Strength", 0, 200, 0, value -> document.setGlowStrength(value / 100f));
        sldAlphaDiscard = createFloatSlider(box, "Alpha Cutout", 0, 100, 0, value -> document.setAlphaDiscardThreshold(value / 100f));

        JPanel flags = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        flags.setOpaque(false);
        chkTransparent = new JCheckBox("Transparent");
        chkTransparent.setOpaque(false);
        chkTransparent.setForeground(new Color(226, 232, 240));
        chkTransparent.addActionListener(e -> {
            if (!updatingUi) {
                document.setTransparent(chkTransparent.isSelected());
                markDirtyAndRefresh();
            }
        });
        flags.add(chkTransparent);

        chkDoubleSided = new JCheckBox("Double Sided");
        chkDoubleSided.setOpaque(false);
        chkDoubleSided.setForeground(new Color(226, 232, 240));
        chkDoubleSided.addActionListener(e -> {
            if (!updatingUi) {
                document.setDoubleSided(chkDoubleSided.isSelected());
                markDirtyAndRefresh();
            }
        });
        flags.add(chkDoubleSided);
        box.add(flags);

        section.add(box, BorderLayout.CENTER);
        return section;
    }

    private JPanel createPreviewSection() {
        JPanel section = createSectionPanel("Preview");
        Box box = Box.createVerticalBox();
        sldPreviewScale = createFloatSlider(box, "Preview Scale", 25, 300, 100, value -> document.setPreviewScale(value / 100f));
        section.add(box, BorderLayout.CENTER);
        return section;
    }

    private JTextField createTextureRow(Box parent, String label) {
        JLabel lbl = new JLabel(label);
        lbl.setForeground(new Color(226, 232, 240));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        parent.add(lbl);
        parent.add(Box.createVerticalStrut(4));

        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField field = new JTextField();
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { sync(); }
            @Override
            public void removeUpdate(DocumentEvent e) { sync(); }
            @Override
            public void changedUpdate(DocumentEvent e) { sync(); }
            private void sync() {
                if (updatingUi) {
                    return;
                }
                if (field == txtDiffuse) {
                    document.setDiffuseTexture(field.getText());
                } else if (field == txtNormal) {
                    document.setNormalTexture(field.getText());
                } else if (field == txtGlow) {
                    document.setGlowTexture(field.getText());
                }
                markDirtyAndRefresh();
            }
        });
        row.add(field, BorderLayout.CENTER);

        JButton btnClear = new JButton("x");
        btnClear.addActionListener(e -> field.setText(""));
        row.add(btnClear, BorderLayout.WEST);

        JButton btnBrowse = new JButton("...");
        btnBrowse.addActionListener(e -> chooseTexture(field));
        row.add(btnBrowse, BorderLayout.EAST);

        parent.add(row);
        parent.add(Box.createVerticalStrut(10));
        return field;
    }

    private JButton createColorButton(String label, ColorRGBA initialColor, Consumer<ColorRGBA> consumer) {
        JButton button = new JButton(label);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.addActionListener(e -> {
            ColorRGBA current = button == btnBaseColor ? document.getBaseColor() : document.getGlowColor();
            Color chosen = JColorChooser.showDialog(this, label, toAwt(current));
            if (chosen != null) {
                ColorRGBA color = new ColorRGBA(
                        chosen.getRed() / 255f,
                        chosen.getGreen() / 255f,
                        chosen.getBlue() / 255f,
                        current.a
                );
                consumer.accept(color);
                refreshColorButton(button, color, label);
            }
        });
        refreshColorButton(button, initialColor, label);
        return button;
    }

    private JSlider createFloatSlider(Box parent, String label, int min, int max, int initial, IntConsumer consumer) {
        JLabel lbl = new JLabel(label);
        lbl.setForeground(new Color(226, 232, 240));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        parent.add(lbl);

        JSlider slider = new JSlider(min, max, initial);
        slider.setOpaque(false);
        slider.setAlignmentX(Component.LEFT_ALIGNMENT);
        slider.addChangeListener(e -> {
            if (!updatingUi) {
                consumer.accept(slider.getValue());
                markDirtyAndRefresh();
            }
        });
        parent.add(slider);
        parent.add(Box.createVerticalStrut(10));
        return slider;
    }

    private void chooseTexture(JTextField targetField) {
        JFileChooser chooser = new JFileChooser(resolveTextureChooserFolder());
        chooser.setDialogTitle("Choose Texture");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File selected = chooser.getSelectedFile();
        String relative = toResourceRelativePath(selected);
        if (relative == null) {
            JOptionPane.showMessageDialog(this,
                    "Please choose a texture from the project resources folder, the default ./resources folder, or the bundled resources-basic library.",
                    "Texture Path", JOptionPane.WARNING_MESSAGE);
            return;
        }
        targetField.setText(relative);
    }

    private void applySelectedStarterPack() {
        if (starterTextureList == null) {
            return;
        }

        String selected = starterTextureList.getSelectedValue();
        if (selected == null || selected.isBlank()) {
            return;
        }

        File packDir = starterTexturePacks.get(selected);
        if (packDir == null || !packDir.isDirectory()) {
            return;
        }

        File root = resolveStarterTextureLibraryRoot();
        if (root == null) {
            return;
        }

        String base = root.toPath().relativize(packDir.toPath()).toString().replace("\\", "/");
        document.setDiffuseTexture("Textures/StarterMaterials/" + base + "/diffuse.png");
        document.setNormalTexture("Textures/StarterMaterials/" + base + "/normal.png");
        document.setGlowTexture("Textures/StarterMaterials/" + base + "/glow.png");
        refreshFromDocument();
        markDirtyAndRefresh();
    }

    private String toResourceRelativePath(File file) {
        if (file == null) {
            return null;
        }

        try {
            File projectRoot = resourcesFolder == null ? null : new File(resourcesFolder).getCanonicalFile();
            File defaultsRoot = new File("./resources").getCanonicalFile();
            File bundledRoot = new File("./resources-basic/resources").getCanonicalFile();
            File chosen = file.getCanonicalFile();

            if (projectRoot != null && chosen.toPath().startsWith(projectRoot.toPath())) {
                return projectRoot.toPath().relativize(chosen.toPath()).toString().replace("\\", "/");
            }
            if (chosen.toPath().startsWith(defaultsRoot.toPath())) {
                return defaultsRoot.toPath().relativize(chosen.toPath()).toString().replace("\\", "/");
            }
            if (chosen.toPath().startsWith(bundledRoot.toPath())) {
                return bundledRoot.toPath().relativize(chosen.toPath()).toString().replace("\\", "/");
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private File resolveTextureChooserFolder() {
        File starterRoot = resolveStarterTextureLibraryRoot();
        if (starterRoot != null) {
            return starterRoot;
        }

        if (resourcesFolder != null) {
            File resources = new File(resourcesFolder);
            if (resources.isDirectory()) {
                return resources;
            }
        }

        File bundledDefaults = new File("./resources-basic/resources");
        if (bundledDefaults.isDirectory()) {
            return bundledDefaults;
        }

        File defaults = new File("./resources");
        return defaults.isDirectory() ? defaults : new File(".");
    }

    private void loadStarterTexturePacks() {
        starterTexturePacks.clear();
        File root = resolveStarterTextureLibraryRoot();
        if (root == null || !root.isDirectory()) {
            return;
        }

        File[] dirs = root.listFiles(File::isDirectory);
        if (dirs == null) {
            return;
        }

        List<File> validDirs = new ArrayList<>();
        for (File dir : dirs) {
            if (new File(dir, "diffuse.png").isFile()
                    && new File(dir, "normal.png").isFile()
                    && new File(dir, "glow.png").isFile()) {
                validDirs.add(dir);
            }
        }

        validDirs.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File dir : validDirs) {
            starterTexturePacks.put(dir.getName(), dir);
        }
    }

    private File resolveStarterTextureLibraryRoot() {
        File projectStarter = resourcesFolder == null ? null : new File(resourcesFolder, "Textures/StarterMaterials");
        if (projectStarter != null && projectStarter.isDirectory()) {
            return projectStarter;
        }

        File bundledStarter = new File("./resources-basic/resources/Textures/StarterMaterials");
        if (bundledStarter.isDirectory()) {
            return bundledStarter;
        }

        File defaultStarter = new File("./resources/Textures/StarterMaterials");
        if (defaultStarter.isDirectory()) {
            return defaultStarter;
        }

        return null;
    }

    private void initPreview() {
        previewApp = new MaterialPreviewApp();
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
        previewCanvas.setMinimumSize(new Dimension(120, 120));
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
            cboPreviewShape.setSelectedItem(document.getPreviewShape());
            refreshColorButton(btnBaseColor, document.getBaseColor(), "Base Color");
            refreshColorButton(btnGlowColor, document.getGlowColor(), "Glow Color");
            sldAmbient.setValue(Math.round(document.getAmbientStrength() * 100f));
            sldSpecular.setValue(Math.round(document.getSpecularStrength() * 100f));
            sldShininess.setValue(Math.round(document.getShininess()));
            sldOpacity.setValue(Math.round(document.getOpacity() * 100f));
            sldGlow.setValue(Math.round(document.getGlowStrength() * 100f));
            sldAlphaDiscard.setValue(Math.round(document.getAlphaDiscardThreshold() * 100f));
            sldPreviewScale.setValue(Math.round(document.getPreviewScale() * 100f));
            chkTransparent.setSelected(document.isTransparent());
            chkDoubleSided.setSelected(document.isDoubleSided());
            txtDiffuse.setText(document.getDiffuseTexture());
            txtNormal.setText(document.getNormalTexture());
            txtGlow.setText(document.getGlowTexture());
            lblExportPath.setText("Exports to " + (resourcesFolder != null
                    ? MaterialDocument.getRuntimeFolder(materialFile, resourcesFolder).getAbsolutePath()
                    : "project resources/material"));
        } finally {
            updatingUi = false;
        }
    }

    private void refreshPreview() {
        if (previewApp != null) {
            previewApp.updatePreview(document);
        }
    }

    private void markDirtyAndRefresh() {
        dirty = true;
        if (onDirtyCallback != null) {
            onDirtyCallback.run();
        }
        refreshPreview();
    }

    private JPanel createCardPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(true);
        panel.setBackground(new Color(30, 36, 44));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(52, 65, 82), 1),
                new EmptyBorder(12, 12, 12, 12)
        ));
        return panel;
    }

    private JPanel createSectionPanel(String title) {
        JPanel section = new JPanel(new BorderLayout(0, 8));
        section.setOpaque(false);
        JLabel lbl = new JLabel(title);
        lbl.setForeground(new Color(245, 247, 250));
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 14f));
        section.add(lbl, BorderLayout.NORTH);
        return section;
    }

    private void refreshColorButton(JButton button, ColorRGBA color, String label) {
        if (button == null || color == null) {
            return;
        }
        button.setBackground(toAwt(color));
        button.setForeground(color.r + color.g + color.b > 1.5f ? Color.BLACK : Color.WHITE);
        button.setText(label + "  " + colorToHex(color));
    }

    private static Color toAwt(ColorRGBA color) {
        return new Color(
                Math.min(255, Math.max(0, Math.round(color.r * 255f))),
                Math.min(255, Math.max(0, Math.round(color.g * 255f))),
                Math.min(255, Math.max(0, Math.round(color.b * 255f)))
        );
    }

    private static String colorToHex(ColorRGBA color) {
        return String.format("#%02X%02X%02X",
                Math.round(color.r * 255f),
                Math.round(color.g * 255f),
                Math.round(color.b * 255f));
    }

    private static String resolveResourcesFolder(String projectPath, File file) {
        if (projectPath != null && !projectPath.isBlank()) {
            return new File(projectPath, "resources").getAbsolutePath();
        }
        if (file != null) {
            File parent = file.getParentFile();
            while (parent != null) {
                File candidate = new File(parent, "resources");
                if (candidate.isDirectory()) {
                    return candidate.getAbsolutePath();
                }
                parent = parent.getParentFile();
            }
        }
        return null;
    }
}
