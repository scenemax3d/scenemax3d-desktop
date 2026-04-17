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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class EnvironmentShaderDesignerPanel extends JPanel {

    private final String projectPath;
    private final File documentFile;
    private final String resourcesFolder;

    private EnvironmentShaderDocument document;
    private boolean dirty = false;
    private boolean discardEditorStateOnDeactivate = false;
    private boolean updatingUi = false;
    private Runnable onDirtyCallback;
    private EnvironmentShaderPreviewApp previewApp;
    private Canvas previewCanvas;
    private JPanel previewContainer;

    private final Map<EnvironmentShaderLayerType, JCheckBox> layerChecks =
            new EnumMap<>(EnvironmentShaderLayerType.class);
    private final Map<EnvironmentShaderLayerType, List<JComponent>> layerRows =
            new EnumMap<>(EnvironmentShaderLayerType.class);
    private JList<EnvironmentShaderTemplatePreset> templateList;
    private JButton btnFogColor;
    private JButton btnRainColor;
    private JButton btnSnowColor;
    private JButton btnSkyTint;
    private JButton btnAmbientColor;
    private JButton btnLightColor;
    private JSpinner spnFogDensity;
    private JSpinner spnFogNear;
    private JSpinner spnFogFar;
    private JSpinner spnRainIntensity;
    private JSpinner spnRainSpeed;
    private JSpinner spnRainAngle;
    private JSpinner spnOverlayOpacity;
    private JSpinner spnSnowIntensity;
    private JSpinner spnSnowSpeed;
    private JSpinner spnSnowFlakeSize;
    private JSpinner spnWindDirection;
    private JSpinner spnWindStrength;
    private JSpinner spnWindGustiness;
    private JSpinner spnSkyBrightness;
    private JSpinner spnSkyHorizonBlend;
    private JSpinner spnAmbientIntensity;
    private JSpinner spnLightIntensity;
    private JSpinner spnLightPitch;
    private JSpinner spnLightYaw;
    private JLabel lblExportPath;
    private JTextArea txtSummary;

    public EnvironmentShaderDesignerPanel(String projectPath, File documentFile) {
        super(new BorderLayout());
        this.projectPath = projectPath;
        this.documentFile = documentFile;
        this.resourcesFolder = resolveResourcesFolder(projectPath, documentFile);

        loadDocument();
        buildUi();
        initPreview();
        refreshFromDocument();
        refreshPreview();
    }

    private void loadDocument() {
        try {
            if (documentFile.exists() && documentFile.length() > 0) {
                document = EnvironmentShaderDocument.load(documentFile);
            } else {
                document = EnvironmentShaderDocument.createFromTemplate(
                        documentFile.getAbsolutePath(), EnvironmentShaderTemplatePreset.FOG);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            document = EnvironmentShaderDocument.createFromTemplate(
                    documentFile.getAbsolutePath(), EnvironmentShaderTemplatePreset.FOG);
        }
    }

    public void saveDocument() {
        try {
            document.save(documentFile);
            document.exportRuntimeAssets(documentFile, resourcesFolder);
            dirty = false;
            discardEditorStateOnDeactivate = false;
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Error saving environment shader document: " + ex.getMessage(),
                    "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void setOnDirtyCallback(Runnable onDirtyCallback) {
        this.onDirtyCallback = onDirtyCallback;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void discardEditorState() {
        dirty = false;
        discardEditorStateOnDeactivate = true;
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
        JLabel title = new JLabel("Environment Shader Editor");
        title.setForeground(new Color(245, 247, 250));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        topBar.add(title, BorderLayout.WEST);

        JLabel subtitle = new JLabel("Build scene-wide fog and rain overlays separately from object materials.");
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
        DefaultListModel<EnvironmentShaderTemplatePreset> model = new DefaultListModel<>();
        for (EnvironmentShaderTemplatePreset preset : EnvironmentShaderTemplatePreset.values()) {
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

        JPanel layersPanel = createSectionPanel("Environment Layers");
        JPanel layerList = new JPanel();
        layerList.setOpaque(false);
        layerList.setLayout(new BoxLayout(layerList, BoxLayout.Y_AXIS));
        for (EnvironmentShaderLayerType layer : EnvironmentShaderLayerType.values()) {
            JCheckBox check = new JCheckBox(layer.getDisplayName());
            check.setOpaque(false);
            check.setForeground(new Color(226, 232, 240));
            check.addActionListener(e -> {
                if (!updatingUi) {
                    setLayerEnabled(layer, check.isSelected());
                }
            });
            layerChecks.put(layer, check);
            layerList.add(check);
        }
        layersPanel.add(layerList, BorderLayout.CENTER);
        left.add(layersPanel, BorderLayout.CENTER);
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
        center.add(previewHeader, BorderLayout.NORTH);

        previewContainer = new JPanel(new BorderLayout());
        previewContainer.setOpaque(true);
        previewContainer.setBackground(new Color(10, 14, 18));
        previewContainer.setBorder(BorderFactory.createLineBorder(new Color(52, 65, 82), 1));
        center.add(previewContainer, BorderLayout.CENTER);

        txtSummary = new JTextArea();
        txtSummary.setEditable(false);
        txtSummary.setOpaque(true);
        txtSummary.setBackground(new Color(14, 18, 24));
        txtSummary.setForeground(new Color(221, 232, 240));
        txtSummary.setMargin(new Insets(10, 10, 10, 10));
        txtSummary.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        txtSummary.setRows(8);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(new JScrollPane(txtSummary), BorderLayout.CENTER);

        lblExportPath = new JLabel();
        lblExportPath.setForeground(new Color(149, 164, 182));
        lblExportPath.setBorder(new EmptyBorder(6, 0, 0, 0));
        footer.add(lblExportPath, BorderLayout.SOUTH);
        center.add(footer, BorderLayout.SOUTH);

        return center;
    }

    private JPanel buildRightPanel() {
        JPanel right = createCardPanel();
        right.setPreferredSize(new Dimension(320, 0));
        right.setLayout(new BorderLayout());

        JPanel params = createSectionPanel("Parameters");
        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        btnFogColor = new JButton("Pick Fog Color");
        btnFogColor.setHorizontalAlignment(SwingConstants.LEFT);
        btnFogColor.addActionListener(e -> chooseColor(EnvironmentShaderLayerType.FOG));
        addLayerRow(form, EnvironmentShaderLayerType.FOG, "Fog Color", btnFogColor);

        spnFogDensity = createSpinner(0.35, 0.0, 2.0, 0.02);
        addLayerRow(form, EnvironmentShaderLayerType.FOG, "Fog Density", spnFogDensity);
        spnFogDensity.addChangeListener(e -> {
            if (!updatingUi) {
                document.setFogDensity(((Double) spnFogDensity.getValue()).floatValue());
                markDirtyAndRefresh();
            }
        });

        spnFogNear = createSpinner(12.0, 0.0, 500.0, 1.0);
        addLayerRow(form, EnvironmentShaderLayerType.FOG, "Fog Near", spnFogNear);
        spnFogNear.addChangeListener(e -> {
            if (!updatingUi) {
                document.setFogNearDistance(((Double) spnFogNear.getValue()).floatValue());
                markDirtyAndRefresh();
            }
        });

        spnFogFar = createSpinner(80.0, 1.0, 1000.0, 1.0);
        addLayerRow(form, EnvironmentShaderLayerType.FOG, "Fog Far", spnFogFar);
        spnFogFar.addChangeListener(e -> {
            if (!updatingUi) {
                document.setFogFarDistance(((Double) spnFogFar.getValue()).floatValue());
                markDirtyAndRefresh();
            }
        });

        btnRainColor = new JButton("Pick Rain Color");
        btnRainColor.setHorizontalAlignment(SwingConstants.LEFT);
        btnRainColor.addActionListener(e -> chooseColor(EnvironmentShaderLayerType.RAIN));
        addLayerRow(form, EnvironmentShaderLayerType.RAIN, "Rain Color", btnRainColor);

        spnRainIntensity = createSpinner(0.55, 0.0, 1.5, 0.05);
        addLayerRow(form, EnvironmentShaderLayerType.RAIN, "Rain Intensity", spnRainIntensity);
        spnRainIntensity.addChangeListener(e -> {
            if (!updatingUi) {
                document.setRainIntensity(((Double) spnRainIntensity.getValue()).floatValue());
                markDirtyAndRefresh();
            }
        });

        spnRainSpeed = createSpinner(1.2, 0.0, 5.0, 0.05);
        addLayerRow(form, EnvironmentShaderLayerType.RAIN, "Rain Speed", spnRainSpeed);
        spnRainSpeed.addChangeListener(e -> {
            if (!updatingUi) {
                document.setRainSpeed(((Double) spnRainSpeed.getValue()).floatValue());
                markDirtyAndRefresh();
            }
        });

        spnRainAngle = createSpinner(-18.0, -90.0, 90.0, 1.0);
        addLayerRow(form, EnvironmentShaderLayerType.RAIN, "Rain Angle", spnRainAngle);
        spnRainAngle.addChangeListener(e -> {
            if (!updatingUi) {
                document.setRainAngle(((Double) spnRainAngle.getValue()).floatValue());
                markDirtyAndRefresh();
            }
        });

        spnOverlayOpacity = createSpinner(0.32, 0.0, 1.0, 0.02);
        addLayerRow(form, EnvironmentShaderLayerType.RAIN, "Overlay Opacity", spnOverlayOpacity);
        spnOverlayOpacity.addChangeListener(e -> {
            if (!updatingUi) {
                document.setOverlayOpacity(((Double) spnOverlayOpacity.getValue()).floatValue());
                markDirtyAndRefresh();
            }
        });

        btnSnowColor = new JButton("Pick Snow Color");
        btnSnowColor.setHorizontalAlignment(SwingConstants.LEFT);
        btnSnowColor.addActionListener(e -> chooseColor(EnvironmentShaderLayerType.SNOW));
        addLayerRow(form, EnvironmentShaderLayerType.SNOW, "Snow Color", btnSnowColor);

        spnSnowIntensity = createSpinner(0.45, 0.0, 1.5, 0.05);
        addLayerRow(form, EnvironmentShaderLayerType.SNOW, "Snow Intensity", spnSnowIntensity);
        spnSnowIntensity.addChangeListener(e -> {
            if (!updatingUi) {
                document.setSnowIntensity(((Double) spnSnowIntensity.getValue()).floatValue());
                markDirtyAndRefresh();
            }
        });

        spnSnowSpeed = createSpinner(0.65, 0.0, 5.0, 0.05);
        addLayerRow(form, EnvironmentShaderLayerType.SNOW, "Snow Speed", spnSnowSpeed);
        spnSnowSpeed.addChangeListener(e -> {
            if (!updatingUi) {
                document.setSnowSpeed(((Double) spnSnowSpeed.getValue()).floatValue());
                markDirtyAndRefresh();
            }
        });

        spnSnowFlakeSize = createSpinner(0.55, 0.05, 1.5, 0.05);
        addLayerRow(form, EnvironmentShaderLayerType.SNOW, "Snow Flake Size", spnSnowFlakeSize);
        spnSnowFlakeSize.addChangeListener(e -> {
            if (!updatingUi) {
                document.setSnowFlakeSize(((Double) spnSnowFlakeSize.getValue()).floatValue());
                markDirtyAndRefresh();
            }
        });

        spnWindDirection = createSpinner(24.0, -180.0, 180.0, 1.0);
        addLayerRow(form, EnvironmentShaderLayerType.WIND, "Wind Direction", spnWindDirection);
        spnWindDirection.addChangeListener(e -> {
            if (!updatingUi) {
                document.setWindDirection(((Double) spnWindDirection.getValue()).floatValue());
                markDirtyAndRefresh();
            }
        });

        spnWindStrength = createSpinner(0.45, 0.0, 2.0, 0.05);
        addLayerRow(form, EnvironmentShaderLayerType.WIND, "Wind Strength", spnWindStrength);
        spnWindStrength.addChangeListener(e -> {
            if (!updatingUi) {
                document.setWindStrength(((Double) spnWindStrength.getValue()).floatValue());
                markDirtyAndRefresh();
            }
        });

        spnWindGustiness = createSpinner(0.35, 0.0, 2.0, 0.05);
        addLayerRow(form, EnvironmentShaderLayerType.WIND, "Wind Gustiness", spnWindGustiness);
        spnWindGustiness.addChangeListener(e -> {
            if (!updatingUi) {
                document.setWindGustiness(((Double) spnWindGustiness.getValue()).floatValue());
                markDirtyAndRefresh();
            }
        });

        btnSkyTint = new JButton("Pick Sky Tint");
        btnSkyTint.setHorizontalAlignment(SwingConstants.LEFT);
        btnSkyTint.addActionListener(e -> chooseColor(EnvironmentShaderLayerType.SKY_TWEAKS));
        addLayerRow(form, EnvironmentShaderLayerType.SKY_TWEAKS, "Sky Tint", btnSkyTint);

        spnSkyBrightness = createSpinner(1.0, 0.0, 2.5, 0.05);
        addLayerRow(form, EnvironmentShaderLayerType.SKY_TWEAKS, "Sky Brightness", spnSkyBrightness);
        spnSkyBrightness.addChangeListener(e -> {
            if (!updatingUi) {
                document.setSkyBrightness(((Double) spnSkyBrightness.getValue()).floatValue());
                markDirtyAndRefresh();
            }
        });

        spnSkyHorizonBlend = createSpinner(0.45, 0.0, 2.0, 0.05);
        addLayerRow(form, EnvironmentShaderLayerType.SKY_TWEAKS, "Sky Horizon Blend", spnSkyHorizonBlend);
        spnSkyHorizonBlend.addChangeListener(e -> {
            if (!updatingUi) {
                document.setSkyHorizonBlend(((Double) spnSkyHorizonBlend.getValue()).floatValue());
                markDirtyAndRefresh();
            }
        });

        btnAmbientColor = new JButton("Pick Ambient Color");
        btnAmbientColor.setHorizontalAlignment(SwingConstants.LEFT);
        btnAmbientColor.addActionListener(e -> chooseColor(EnvironmentShaderLayerType.AMBIENT_COLOR));
        addLayerRow(form, EnvironmentShaderLayerType.AMBIENT_COLOR, "Ambient Color", btnAmbientColor);

        spnAmbientIntensity = createSpinner(0.65, 0.0, 2.0, 0.05);
        addLayerRow(form, EnvironmentShaderLayerType.AMBIENT_COLOR, "Ambient Intensity", spnAmbientIntensity);
        spnAmbientIntensity.addChangeListener(e -> {
            if (!updatingUi) {
                document.setAmbientIntensity(((Double) spnAmbientIntensity.getValue()).floatValue());
                markDirtyAndRefresh();
            }
        });

        btnLightColor = new JButton("Pick Light Color");
        btnLightColor.setHorizontalAlignment(SwingConstants.LEFT);
        btnLightColor.addActionListener(e -> chooseColor(EnvironmentShaderLayerType.LIGHTING));
        addLayerRow(form, EnvironmentShaderLayerType.LIGHTING, "Light Color", btnLightColor);

        spnLightIntensity = createSpinner(1.1, 0.0, 3.0, 0.05);
        addLayerRow(form, EnvironmentShaderLayerType.LIGHTING, "Light Intensity", spnLightIntensity);
        spnLightIntensity.addChangeListener(e -> {
            if (!updatingUi) {
                document.setLightIntensity(((Double) spnLightIntensity.getValue()).floatValue());
                markDirtyAndRefresh();
            }
        });

        spnLightPitch = createSpinner(-38.0, -89.0, 89.0, 1.0);
        addLayerRow(form, EnvironmentShaderLayerType.LIGHTING, "Light Pitch", spnLightPitch);
        spnLightPitch.addChangeListener(e -> {
            if (!updatingUi) {
                document.setLightPitch(((Double) spnLightPitch.getValue()).floatValue());
                markDirtyAndRefresh();
            }
        });

        spnLightYaw = createSpinner(-32.0, -180.0, 180.0, 1.0);
        addLayerRow(form, EnvironmentShaderLayerType.LIGHTING, "Light Yaw", spnLightYaw);
        spnLightYaw.addChangeListener(e -> {
            if (!updatingUi) {
                document.setLightYaw(((Double) spnLightYaw.getValue()).floatValue());
                markDirtyAndRefresh();
            }
        });

        JTextArea tip = new JTextArea(
                "This first version exports environment shader assets and metadata as a scene-level package. Hooking the runtime to consume it can come next without mixing it into object shaders.");
        tip.setOpaque(false);
        tip.setLineWrap(true);
        tip.setWrapStyleWord(true);
        tip.setEditable(false);
        tip.setForeground(new Color(155, 169, 186));
        tip.setBorder(new EmptyBorder(12, 0, 0, 0));
        form.add(tip);

        params.add(form, BorderLayout.NORTH);
        JScrollPane scrollPane = new JScrollPane(params);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        right.add(scrollPane, BorderLayout.CENTER);

        JButton btnSave = new JButton("Save Environment Shader");
        btnSave.addActionListener(e -> saveDocument());
        right.add(btnSave, BorderLayout.SOUTH);

        return right;
    }

    private void initPreview() {
        previewApp = new EnvironmentShaderPreviewApp();
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
            updateColorButton(btnFogColor, document.getFogColor());
            updateColorButton(btnRainColor, document.getRainColor());
            updateColorButton(btnSnowColor, document.getSnowColor());
            updateColorButton(btnSkyTint, document.getSkyTint());
            updateColorButton(btnAmbientColor, document.getAmbientColor());
            updateColorButton(btnLightColor, document.getLightColor());
            spnFogDensity.setValue((double) document.getFogDensity());
            spnFogNear.setValue((double) document.getFogNearDistance());
            spnFogFar.setValue((double) document.getFogFarDistance());
            spnRainIntensity.setValue((double) document.getRainIntensity());
            spnRainSpeed.setValue((double) document.getRainSpeed());
            spnRainAngle.setValue((double) document.getRainAngle());
            spnOverlayOpacity.setValue((double) document.getOverlayOpacity());
            spnSnowIntensity.setValue((double) document.getSnowIntensity());
            spnSnowSpeed.setValue((double) document.getSnowSpeed());
            spnSnowFlakeSize.setValue((double) document.getSnowFlakeSize());
            spnWindDirection.setValue((double) document.getWindDirection());
            spnWindStrength.setValue((double) document.getWindStrength());
            spnWindGustiness.setValue((double) document.getWindGustiness());
            spnSkyBrightness.setValue((double) document.getSkyBrightness());
            spnSkyHorizonBlend.setValue((double) document.getSkyHorizonBlend());
            spnAmbientIntensity.setValue((double) document.getAmbientIntensity());
            spnLightIntensity.setValue((double) document.getLightIntensity());
            spnLightPitch.setValue((double) document.getLightPitch());
            spnLightYaw.setValue((double) document.getLightYaw());
            for (Map.Entry<EnvironmentShaderLayerType, JCheckBox> entry : layerChecks.entrySet()) {
                entry.getValue().setSelected(document.getLayers().contains(entry.getKey()));
            }
            updateParameterVisibility();
            if (resourcesFolder != null) {
                lblExportPath.setText("Exports to: " + EnvironmentShaderDocument.getRuntimeFolder(documentFile, resourcesFolder).getPath());
            } else {
                lblExportPath.setText("Exports to project resources/environment_shaders when a project is active.");
            }
        } finally {
            updatingUi = false;
        }
    }

    private void applyTemplate(EnvironmentShaderTemplatePreset preset) {
        if (preset == null) {
            return;
        }
        preset.applyTo(document);
        refreshFromDocument();
        markDirtyAndRefresh();
    }

    private void setLayerEnabled(EnvironmentShaderLayerType layer, boolean enabled) {
        if (enabled) {
            document.getLayers().add(layer);
        } else {
            document.getLayers().remove(layer);
        }
        updateParameterVisibility();
        markDirtyAndRefresh();
    }

    private void chooseColor(EnvironmentShaderLayerType target) {
        ColorRGBA current;
        switch (target) {
            case FOG:
                current = document.getFogColor();
                break;
            case RAIN:
                current = document.getRainColor();
                break;
            case SNOW:
                current = document.getSnowColor();
                break;
            case SKY_TWEAKS:
                current = document.getSkyTint();
                break;
            case AMBIENT_COLOR:
                current = document.getAmbientColor();
                break;
            case LIGHTING:
                current = document.getLightColor();
                break;
            default:
                current = document.getFogColor();
                break;
        }
        Color chosen = JColorChooser.showDialog(
                this,
                "Pick " + target.getDisplayName() + " Color",
                new Color(clampColor(current.r), clampColor(current.g), clampColor(current.b), clampColor(current.a))
        );
        if (chosen == null) {
            return;
        }
        ColorRGBA updated = new ColorRGBA(
                chosen.getRed() / 255f,
                chosen.getGreen() / 255f,
                chosen.getBlue() / 255f,
                chosen.getAlpha() / 255f
        );
        switch (target) {
            case FOG:
                document.setFogColor(updated);
                updateColorButton(btnFogColor, updated);
                break;
            case RAIN:
                document.setRainColor(updated);
                updateColorButton(btnRainColor, updated);
                break;
            case SNOW:
                document.setSnowColor(updated);
                updateColorButton(btnSnowColor, updated);
                break;
            case SKY_TWEAKS:
                document.setSkyTint(updated);
                updateColorButton(btnSkyTint, updated);
                break;
            case AMBIENT_COLOR:
                document.setAmbientColor(updated);
                updateColorButton(btnAmbientColor, updated);
                break;
            case LIGHTING:
                document.setLightColor(updated);
                updateColorButton(btnLightColor, updated);
                break;
        }
        markDirtyAndRefresh();
    }

    private void markDirtyAndRefresh() {
        dirty = true;
        if (onDirtyCallback != null) {
            onDirtyCallback.run();
        }
        refreshPreview();
    }

    private void refreshPreview() {
        if (txtSummary != null) {
            txtSummary.setText(document.buildPreviewSummary(documentFile));
        }
        if (previewApp != null) {
            previewApp.updatePreview(documentFile, document);
        }
    }

    private JPanel createCardPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(new Color(17, 22, 29));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(45, 57, 72), 1),
                new EmptyBorder(12, 12, 12, 12)
        ));
        return panel;
    }

    private JPanel createSectionPanel(String title) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        JLabel lbl = new JLabel(title);
        lbl.setForeground(new Color(239, 244, 248));
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 14f));
        lbl.setBorder(new EmptyBorder(0, 0, 8, 0));
        panel.add(lbl, BorderLayout.NORTH);
        return panel;
    }

    private void addRow(JPanel parent, String label, JComponent field) {
        JLabel lbl = new JLabel(label);
        lbl.setForeground(new Color(205, 214, 224));
        lbl.setBorder(new EmptyBorder(8, 0, 4, 0));
        parent.add(lbl);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        parent.add(field);
    }

    private void addLayerRow(JPanel parent, EnvironmentShaderLayerType layer, String label, JComponent field) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));

        JLabel lbl = new JLabel(label);
        lbl.setForeground(new Color(205, 214, 224));
        lbl.setBorder(new EmptyBorder(8, 0, 4, 0));
        row.add(lbl);

        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.add(field);
        parent.add(row);

        layerRows.computeIfAbsent(layer, key -> new ArrayList<>()).add(row);
    }

    private void updateParameterVisibility() {
        for (Map.Entry<EnvironmentShaderLayerType, List<JComponent>> entry : layerRows.entrySet()) {
            boolean visible = document != null && document.getLayers().contains(entry.getKey());
            for (JComponent row : entry.getValue()) {
                row.setVisible(visible);
            }
        }
        revalidate();
        repaint();
    }

    private JSpinner createSpinner(double value, double min, double max, double step) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
        spinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        return spinner;
    }

    private void updateColorButton(JButton button, ColorRGBA color) {
        button.setBackground(new Color(clampColor(color.r), clampColor(color.g), clampColor(color.b), clampColor(color.a)));
        button.setForeground(color.r + color.g + color.b > 1.7f ? Color.BLACK : Color.WHITE);
        button.setText(String.format("RGBA %.2f %.2f %.2f %.2f", color.r, color.g, color.b, color.a));
        button.setOpaque(true);
        button.setBorderPainted(false);
    }

    private int clampColor(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255f)));
    }

    private String resolveResourcesFolder(String projectPath, File file) {
        if (projectPath != null && !projectPath.isBlank()) {
            File resources = new File(projectPath, "resources");
            if (resources.exists() || resources.mkdirs()) {
                return resources.getAbsolutePath();
            }
        }

        File parent = file != null ? file.getParentFile() : null;
        while (parent != null) {
            File maybeResources = new File(parent, "resources");
            if (maybeResources.exists() || maybeResources.mkdirs()) {
                return maybeResources.getAbsolutePath();
            }
            parent = parent.getParentFile();
        }
        return null;
    }
}
