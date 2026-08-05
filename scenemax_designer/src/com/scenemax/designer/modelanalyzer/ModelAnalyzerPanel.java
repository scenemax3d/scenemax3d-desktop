package com.scenemax.designer.modelanalyzer;

import com.jme3.system.AppSettings;
import com.jme3.system.JmeCanvasContext;
import com.scenemax.designer.animation.ModelJ3oClipExporter;
import com.scenemax.designer.gizmo.GizmoMode;
import com.scenemaxeng.common.types.AssetsMapping;
import com.scenemaxeng.common.types.ResourceSetup;
import org.lwjgl.input.Mouse;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EventObject;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ModelAnalyzerPanel extends JPanel implements AutoCloseable {
    private static final int RANGE_NAME_COLUMN = 0;
    private static final int RANGE_SOURCE_ANIMATION_COLUMN = 1;
    private static final int RANGE_START_COLUMN = 2;
    private static final int RANGE_END_COLUMN = 3;

    private final File resourcesRoot;
    private final ModelAnalyzerPreviewApp app;
    private final Canvas canvas;

    private final JComboBox<String> modelCombo = new JComboBox<>();
    private final JComboBox<String> animationCombo = new JComboBox<>();
    private final RangeSlider frameRangeSlider = new RangeSlider(0, 1);
    private final Timer rangeSliderPlaybackTimer;
    private final JSlider speedSlider = new JSlider(1, 200, 100);
    private final JLabel speedValue = new JLabel("100%");
    private final JSpinner startFrameSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 999999, 1));
    private final JSpinner endFrameSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 999999, 1));
    private final JLabel animationPercentValue = new JLabel("--%");
    private final JLabel currentFrameValue = new JLabel("--");
    private final JLabel frameRangeValue = new JLabel("Frames: --");
    private final JLabel statusLabel = new JLabel("Choose a model to analyze.");
    private final JButton pauseResumeButton = new JButton("Pause");
    private final JButton saveAsNativeModelButton = new JButton("Save As Native Model");
    private final DefaultTableModel rangeTableModel = new DefaultTableModel(new Object[]{"Name", "Source Animation", "Start", "End"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return true;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return String.class;
        }
    };
    private final JTable rangeTable = new EditableRangeTable(rangeTableModel);

    private boolean updatingModels;
    private boolean filteringModels;
    private boolean updatingAnimations;
    private boolean updatingFrameControls;
    private boolean updatingRangeSlider;
    private boolean updatingRangeTable;
    private boolean playbackRunning;
    private boolean playbackPaused;
    private String currentRangeModel = "";
    private List<String> availableModels = new ArrayList<>();

    public ModelAnalyzerPanel(File resourcesRoot) {
        super(new BorderLayout(0, 8));
        this.resourcesRoot = resourcesRoot;
        setBorder(new EmptyBorder(12, 12, 12, 12));
        rangeSliderPlaybackTimer = new Timer(100, e -> playCurrentFrameRange());
        rangeSliderPlaybackTimer.setRepeats(false);

        app = new ModelAnalyzerPreviewApp(resourcesRoot);
        AppSettings settings = new AppSettings(true);
        settings.setWidth(900);
        settings.setHeight(620);
        settings.setSamples(4);
        settings.setVSync(true);
        settings.setFrameRate(60);
        settings.setGammaCorrection(false);

        app.setSettings(settings);
        app.setPauseOnLostFocus(false);
        app.setShowSettings(false);
        app.createCanvas();

        JmeCanvasContext ctx = (JmeCanvasContext) app.getContext();
        ctx.setSystemListener(app);
        canvas = ctx.getCanvas();
        canvas.setMinimumSize(new Dimension(260, 220));
        canvas.setPreferredSize(new Dimension(900, 580));
        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                releaseMouseCapture();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                releaseMouseCapture();
            }
        });
        canvas.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                releaseMouseCapture();
            }
        });
        canvas.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (canvas.getWidth() > 0 && canvas.getHeight() > 0) {
                    app.enqueue(() -> {
                        app.reshape(canvas.getWidth(), canvas.getHeight());
                        return null;
                    });
                }
            }
        });

        app.setAnimationNamesChangedCallback(names -> SwingUtilities.invokeLater(() -> updateAnimationOptions(names)));
        app.setPlaybackInfoChangedCallback(info -> SwingUtilities.invokeLater(() -> updatePlaybackInfo(info)));
        app.setStatusChangedCallback(status -> SwingUtilities.invokeLater(() -> statusLabel.setText(status)));

        add(buildToolbar(), BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildRangeTablePanel(), buildPreviewPanel());
        split.setContinuousLayout(true);
        split.setResizeWeight(0.24);
        split.setDividerLocation(300);
        add(split, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        loadModelOptions();
        app.startCanvas();
        SwingUtilities.invokeLater(this::loadSelectedModel);
    }

    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel();
        toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.Y_AXIS));

        JPanel modelRow = new JPanel(new BorderLayout(8, 3));
        modelRow.add(new JLabel("Model"), BorderLayout.WEST);
        modelCombo.setPrototypeDisplayValue("MMMMMMMMMMMMMMMMMMMMMMMMMMMM");
        modelCombo.setEditable(true);
        modelCombo.addActionListener(e -> {
            if (!updatingModels && !filteringModels && isKnownModel(selectedModel())) {
                loadSelectedModel();
            }
        });
        installModelFilter();
        modelRow.add(modelCombo, BorderLayout.CENTER);
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> loadModelOptions());
        modelRow.add(refresh, BorderLayout.EAST);
        toolbar.add(modelRow);

        JPanel transformRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 3));
        transformRow.add(modeButton("Move", GizmoMode.TRANSLATE));
        transformRow.add(modeButton("Rotate", GizmoMode.ROTATE));
        transformRow.add(modeButton("Scale", GizmoMode.SCALE));
        JButton resetTransform = new JButton("Reset Transform");
        resetTransform.addActionListener(e -> app.resetModelTransform());
        transformRow.add(resetTransform);
        JButton resetView = new JButton("Reset View");
        resetView.addActionListener(e -> app.resetCamera());
        transformRow.add(resetView);
        toolbar.add(transformRow);

        JPanel animationRow = new JPanel(new BorderLayout(8, 3));
        animationRow.add(new JLabel("Animation"), BorderLayout.WEST);
        animationCombo.setPrototypeDisplayValue("MMMMMMMMMMMMMMMMMMMMMMMM");
        animationCombo.addActionListener(e -> {
            if (!updatingAnimations) {
                app.selectAnimation(selectedAnimation());
            }
        });
        animationRow.add(animationCombo, BorderLayout.CENTER);
        JPanel animationButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        JButton playFull = new JButton("Play Full");
        playFull.addActionListener(e -> app.playAnimation(false, frameValue(startFrameSpinner), frameValue(endFrameSpinner)));
        JButton playRange = new JButton("Play Range");
        playRange.addActionListener(e -> app.playAnimation(true, frameValue(startFrameSpinner), frameValue(endFrameSpinner)));
        pauseResumeButton.addActionListener(e -> togglePauseResume());
        pauseResumeButton.setEnabled(false);
        JButton stop = new JButton("Stop");
        stop.addActionListener(e -> app.stopAnimation());
        animationButtons.add(playFull);
        animationButtons.add(playRange);
        animationButtons.add(pauseResumeButton);
        animationButtons.add(stop);
        animationRow.add(animationButtons, BorderLayout.EAST);
        toolbar.add(animationRow);

        JPanel rangeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        rangeRow.add(new JLabel("Start Frame"));
        startFrameSpinner.setPreferredSize(new Dimension(78, startFrameSpinner.getPreferredSize().height));
        startFrameSpinner.addChangeListener(e -> onFrameSpinnerChanged());
        rangeRow.add(startFrameSpinner);
        rangeRow.add(new JLabel("End Frame"));
        endFrameSpinner.setPreferredSize(new Dimension(78, endFrameSpinner.getPreferredSize().height));
        endFrameSpinner.addChangeListener(e -> onFrameSpinnerChanged());
        rangeRow.add(endFrameSpinner);
        JButton setFullRange = new JButton("Use Full Range");
        setFullRange.addActionListener(e -> setFullFrameRange());
        rangeRow.add(setFullRange);
        rangeRow.add(frameRangeValue);
        toolbar.add(rangeRow);

        JPanel frameSliderRow = new JPanel(new BorderLayout(8, 3));
        frameSliderRow.add(new JLabel("Frame Range"), BorderLayout.WEST);
        frameRangeSlider.setPreferredSize(new Dimension(220, 34));
        frameRangeSlider.addChangeListener(e -> onFrameRangeSliderChanged());
        frameSliderRow.add(frameRangeSlider, BorderLayout.CENTER);
        toolbar.add(frameSliderRow);

        JPanel playbackRow = new JPanel(new BorderLayout(8, 3));
        playbackRow.add(new JLabel("Speed"), BorderLayout.WEST);
        speedSlider.setMajorTickSpacing(50);
        speedSlider.setMinorTickSpacing(10);
        speedSlider.setPaintTicks(true);
        speedSlider.addChangeListener(e -> {
            int value = speedSlider.getValue();
            speedValue.setText(value + "%");
            app.setAnimationSpeedPercent(value);
        });
        playbackRow.add(speedSlider, BorderLayout.CENTER);
        speedValue.setHorizontalAlignment(SwingConstants.RIGHT);
        speedValue.setPreferredSize(new Dimension(48, speedValue.getPreferredSize().height));
        playbackRow.add(speedValue, BorderLayout.EAST);
        toolbar.add(playbackRow);

        JPanel infoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
        infoRow.add(new JLabel("Animation Index"));
        animationPercentValue.setPreferredSize(new Dimension(46, animationPercentValue.getPreferredSize().height));
        infoRow.add(animationPercentValue);
        infoRow.add(new JLabel("Current Frame"));
        currentFrameValue.setPreferredSize(new Dimension(56, currentFrameValue.getPreferredSize().height));
        infoRow.add(currentFrameValue);
        toolbar.add(infoRow);

        return toolbar;
    }

    private JPanel buildPreviewPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(canvas, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        saveAsNativeModelButton.addActionListener(e -> saveSelectedModelAsNativeModel());
        actions.add(saveAsNativeModelButton);
        panel.add(actions, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildRangeTablePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(new EmptyBorder(0, 0, 0, 10));
        panel.setMinimumSize(new Dimension(220, 180));
        panel.setPreferredSize(new Dimension(300, 520));

        JLabel title = new JLabel("Animation Frame Ranges");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        panel.add(title, BorderLayout.NORTH);

        rangeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        rangeTable.setFillsViewportHeight(true);
        rangeTable.setSurrendersFocusOnKeystroke(true);
        rangeTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        JTextField tableEditorField = new JTextField();
        DefaultCellEditor tableEditor = new DefaultCellEditor(tableEditorField);
        tableEditor.setClickCountToStart(2);
        rangeTable.setDefaultEditor(Object.class, tableEditor);
        rangeTable.setDefaultEditor(String.class, tableEditor);
        rangeTable.getColumnModel().getColumn(RANGE_NAME_COLUMN).setPreferredWidth(110);
        rangeTable.getColumnModel().getColumn(RANGE_SOURCE_ANIMATION_COLUMN).setPreferredWidth(115);
        rangeTable.getColumnModel().getColumn(RANGE_START_COLUMN).setPreferredWidth(52);
        rangeTable.getColumnModel().getColumn(RANGE_END_COLUMN).setPreferredWidth(52);
        rangeTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                applySelectedRangeToFrameControls();
            }
        });
        rangeTable.addPropertyChangeListener("tableCellEditor", e -> {
            if (rangeTable.isEditing()) {
                rangeSliderPlaybackTimer.stop();
            }
        });
        panel.add(new JScrollPane(rangeTable), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton add = new JButton("Add");
        add.addActionListener(e -> addRangeRow());
        JButton delete = new JButton("Delete");
        delete.addActionListener(e -> deleteSelectedRangeRow());
        JButton save = new JButton("Save");
        save.addActionListener(e -> saveRangesForSelectedModel());
        actions.add(add);
        actions.add(delete);
        actions.add(save);
        panel.add(actions, BorderLayout.SOUTH);

        return panel;
    }

    private JButton modeButton(String label, GizmoMode mode) {
        JButton button = new JButton(label);
        button.addActionListener(e -> app.setGizmoMode(mode));
        return button;
    }

    private void loadModelOptions() {
        String current = selectedModel();
        availableModels = listProjectModels();
        updatingModels = true;
        modelCombo.removeAllItems();
        for (String model : availableModels) {
            modelCombo.addItem(model);
        }
        if (!current.isEmpty() && isKnownModel(current)) {
            modelCombo.setSelectedItem(current);
        } else if (modelCombo.getItemCount() > 0) {
            modelCombo.setSelectedIndex(0);
        }
        updatingModels = false;
        loadSelectedModel();
    }

    private void installModelFilter() {
        Component editor = modelCombo.getEditor().getEditorComponent();
        if (!(editor instanceof JTextField)) {
            return;
        }
        JTextField editorField = (JTextField) editor;
        editorField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                scheduleModelFilter(editorField);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                scheduleModelFilter(editorField);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                scheduleModelFilter(editorField);
            }
        });
    }

    private void scheduleModelFilter(JTextField editorField) {
        if (updatingModels || filteringModels) {
            return;
        }
        SwingUtilities.invokeLater(() -> filterModelCombo(editorField.getText()));
    }

    private void filterModelCombo(String filterText) {
        if (updatingModels) {
            return;
        }
        filteringModels = true;
        try {
            String text = filterText == null ? "" : filterText;
            String needle = text.trim().toLowerCase(Locale.ROOT);
            modelCombo.removeAllItems();
            for (String model : availableModels) {
                if (needle.isEmpty() || model.toLowerCase(Locale.ROOT).contains(needle)) {
                    modelCombo.addItem(model);
                }
            }
            modelCombo.getEditor().setItem(text);
            Component editor = modelCombo.getEditor().getEditorComponent();
            if (editor instanceof JTextField) {
                JTextField field = (JTextField) editor;
                field.setCaretPosition(Math.min(text.length(), field.getText().length()));
            }
            boolean editorFocused = editor != null && editor.isFocusOwner();
            if (modelCombo.isDisplayable() && editorFocused && modelCombo.getItemCount() > 0) {
                modelCombo.showPopup();
            }
        } catch (Exception ignored) {
        } finally {
            filteringModels = false;
        }
    }

    private boolean isKnownModel(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return false;
        }
        for (String model : availableModels) {
            if (modelName.equalsIgnoreCase(model)) {
                return true;
            }
        }
        return false;
    }

    private List<String> listProjectModels() {
        List<String> names = new ArrayList<>();
        if (resourcesRoot == null || !resourcesRoot.isDirectory()) {
            return names;
        }
        try {
            AssetsMapping assets = new AssetsMapping(resourcesRoot.getCanonicalPath());
            for (ResourceSetup resource : assets.get3DModelsIndex().values()) {
                if (resource != null && resource.name != null && !resource.name.isBlank()) {
                    names.add(resource.name);
                }
            }
            names.sort(Comparator.comparing(String::toLowerCase));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return names;
    }

    private void loadSelectedModel() {
        commitRangeTableEdit();
        String model = selectedModel();
        loadRangesForModel(model);
        updateSaveAsNativeModelButtonVisibility();
        app.loadModel(model);
        app.setAnimationSpeedPercent(speedSlider.getValue());
    }

    private String selectedModel() {
        Object selected = modelCombo.getSelectedItem();
        return selected == null ? "" : String.valueOf(selected).trim();
    }

    private String selectedAnimation() {
        Object selected = animationCombo.getSelectedItem();
        return selected == null ? "" : String.valueOf(selected).trim();
    }

    private int frameValue(JSpinner spinner) {
        Object value = spinner.getValue();
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private void updateAnimationOptions(List<String> animations) {
        String current = selectedAnimation();
        updatingAnimations = true;
        animationCombo.removeAllItems();
        animationCombo.addItem("");
        boolean hasCurrent = current.isEmpty();
        if (animations != null) {
            for (String animation : animations) {
                if (animation == null || animation.trim().isEmpty()) {
                    continue;
                }
                animationCombo.addItem(animation);
                if (animation.equals(current)) {
                    hasCurrent = true;
                }
            }
        }
        animationCombo.setSelectedItem(hasCurrent ? current : "");
        updatingAnimations = false;
        app.selectAnimation(selectedAnimation());
    }

    private void updatePlaybackInfo(ModelAnalyzerPreviewApp.PlaybackInfo info) {
        if (info == null) {
            animationPercentValue.setText("--%");
            currentFrameValue.setText("--");
            frameRangeValue.setText("Frames: --");
            return;
        }
        animationPercentValue.setText(info.percent < 0 ? "--%" : info.percent + "%");
        currentFrameValue.setText(info.currentFrame < 0 ? "--" : String.valueOf(info.currentFrame));
        frameRangeValue.setText(info.maxFrame < 0 ? "Frames: --" : "Frames: 0-" + info.maxFrame);
        playbackRunning = info.running;
        playbackPaused = info.paused;
        pauseResumeButton.setEnabled(info.running);
        pauseResumeButton.setText(info.paused ? "Resume" : "Pause");

        if (!updatingFrameControls && info.maxFrame >= 0 && !info.running) {
            updatingFrameControls = true;
            updatingRangeSlider = true;
            frameRangeSlider.setMaximum(Math.max(1, info.maxFrame));
            startFrameSpinner.setValue(Math.max(0, Math.min(frameValue(startFrameSpinner), info.maxFrame)));
            int endValue = frameValue(endFrameSpinner);
            if (endValue <= 0 || endValue > info.maxFrame) {
                endFrameSpinner.setValue(info.maxFrame);
            }
            frameRangeSlider.setRange(frameValue(startFrameSpinner), frameValue(endFrameSpinner));
            frameRangeSlider.setCursorValue(frameValue(startFrameSpinner));
            updatingRangeSlider = false;
            updatingFrameControls = false;
        } else if (!updatingRangeSlider && info.maxFrame >= 0 && info.running && !frameRangeSlider.isCursorDragging()) {
            updatingRangeSlider = true;
            frameRangeSlider.setMaximum(Math.max(1, info.maxFrame));
            frameRangeSlider.setCursorValue(info.currentFrame);
            updatingRangeSlider = false;
        }
    }

    private void setFullFrameRange() {
        ModelAnalyzerPreviewApp.PlaybackInfo info = app.getLastPlaybackInfo();
        int max = info == null || info.maxFrame < 0 ? 0 : info.maxFrame;
        updatingFrameControls = true;
        updatingRangeSlider = true;
        startFrameSpinner.setValue(0);
        endFrameSpinner.setValue(max);
        frameRangeSlider.setMaximum(Math.max(1, max));
        frameRangeSlider.setRange(0, max);
        frameRangeSlider.setCursorValue(0);
        updatingRangeSlider = false;
        updatingFrameControls = false;
    }

    private void onFrameSpinnerChanged() {
        if (updatingFrameControls) {
            return;
        }
        int max = Math.max(1, frameRangeSlider.getMaximum());
        int start = Math.max(0, Math.min(frameValue(startFrameSpinner), max));
        int end = Math.max(0, Math.min(frameValue(endFrameSpinner), max));
        if (end < start) {
            end = start;
        }
        updatingFrameControls = true;
        updatingRangeSlider = true;
        startFrameSpinner.setValue(start);
        endFrameSpinner.setValue(end);
        frameRangeSlider.setRange(start, end);
        frameRangeSlider.setCursorValue(Math.max(start, Math.min(frameRangeSlider.getCursorValue(), end)));
        updatingRangeSlider = false;
        updatingFrameControls = false;
        updateSelectedRangeRow(start, end);
        if (playbackRunning) {
            app.updateRunningRange(start, end);
        }
    }

    private void onFrameRangeSliderChanged() {
        if (updatingRangeSlider) {
            return;
        }
        RangeSlider.DragHandle changedHandle = frameRangeSlider.getLastChangedHandle();
        if (changedHandle == RangeSlider.DragHandle.CURSOR) {
            rangeSliderPlaybackTimer.stop();
            app.seekToFrame(
                    frameRangeSlider.getCursorValue(),
                    frameRangeSlider.getLowerValue(),
                    frameRangeSlider.getUpperValue());
            return;
        }
        updatingFrameControls = true;
        startFrameSpinner.setValue(frameRangeSlider.getLowerValue());
        endFrameSpinner.setValue(frameRangeSlider.getUpperValue());
        updatingFrameControls = false;
        updateSelectedRangeRow(frameRangeSlider.getLowerValue(), frameRangeSlider.getUpperValue());
        rangeSliderPlaybackTimer.restart();
    }

    private void playCurrentFrameRange() {
        if (rangeTable.isEditing()) {
            return;
        }
        if (selectedAnimation().isEmpty()) {
            return;
        }
        app.playAnimation(true, frameValue(startFrameSpinner), frameValue(endFrameSpinner));
    }

    private void togglePauseResume() {
        if (!playbackRunning) {
            return;
        }
        app.setAnimationPaused(!playbackPaused);
    }

    private void addRangeRow() {
        commitRangeTableEdit();
        String sourceAnimation = selectedAnimation();
        String name = sourceAnimation;
        if (name.isEmpty()) {
            name = "range_" + (rangeTableModel.getRowCount() + 1);
        }
        int start = frameValue(startFrameSpinner);
        int end = frameValue(endFrameSpinner);
        updatingRangeTable = true;
        rangeTableModel.addRow(new Object[]{name, sourceAnimation, String.valueOf(start), String.valueOf(end)});
        int row = rangeTableModel.getRowCount() - 1;
        updatingRangeTable = false;
        rangeTable.getSelectionModel().setSelectionInterval(row, row);
    }

    private void deleteSelectedRangeRow() {
        commitRangeTableEdit();
        int row = rangeTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        int modelRow = rangeTable.convertRowIndexToModel(row);
        updatingRangeTable = true;
        rangeTableModel.removeRow(modelRow);
        updatingRangeTable = false;
    }

    private void applySelectedRangeToFrameControls() {
        if (updatingRangeTable) {
            return;
        }
        int row = rangeTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        int modelRow = rangeTable.convertRowIndexToModel(row);
        String sourceAnimation = rangeCell(modelRow, RANGE_SOURCE_ANIMATION_COLUMN);
        if (!sourceAnimation.isEmpty() && !sourceAnimation.equals(selectedAnimation())) {
            animationCombo.setSelectedItem(sourceAnimation);
        }
        updatingFrameControls = true;
        updatingRangeSlider = true;
        startFrameSpinner.setValue(parseInt(rangeTableModel.getValueAt(modelRow, RANGE_START_COLUMN), frameValue(startFrameSpinner)));
        endFrameSpinner.setValue(parseInt(rangeTableModel.getValueAt(modelRow, RANGE_END_COLUMN), frameValue(endFrameSpinner)));
        frameRangeSlider.setRange(frameValue(startFrameSpinner), frameValue(endFrameSpinner));
        frameRangeSlider.setCursorValue(frameValue(startFrameSpinner));
        updatingRangeSlider = false;
        updatingFrameControls = false;
        rangeSliderPlaybackTimer.restart();
    }

    private void updateSelectedRangeRow(int start, int end) {
        if (updatingRangeTable) {
            return;
        }
        int row = rangeTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        int modelRow = rangeTable.convertRowIndexToModel(row);
        updatingRangeTable = true;
        try {
            rangeTableModel.setValueAt(String.valueOf(start), modelRow, RANGE_START_COLUMN);
            rangeTableModel.setValueAt(String.valueOf(end), modelRow, RANGE_END_COLUMN);
        } finally {
            updatingRangeTable = false;
        }
    }

    private void loadRangesForModel(String modelName) {
        updatingRangeTable = true;
        rangeTableModel.setRowCount(0);
        currentRangeModel = modelName == null ? "" : modelName.trim();
        JSONObject model = findEditableModelJson(modelName);
        if (model != null) {
            String legacySourceAnimation = model.optString("animationFrameRangesSourceAnimation", "").trim();
            if (legacySourceAnimation.isEmpty()) {
                legacySourceAnimation = model.optString("sourceAnimation", "").trim();
            }
            JSONArray ranges = model.optJSONArray("animationFrameRanges");
            if (ranges != null) {
                for (int i = 0; i < ranges.length(); i++) {
                    JSONObject range = ranges.optJSONObject(i);
                    if (range == null) {
                        continue;
                    }
                    String sourceAnimation = range.optString("sourceAnimation", legacySourceAnimation).trim();
                    rangeTableModel.addRow(new Object[]{
                            range.optString("name", ""),
                            sourceAnimation,
                            String.valueOf(range.optInt("start", 0)),
                            String.valueOf(range.optInt("end", 0))
                    });
                }
            }
        }
        updatingRangeTable = false;
    }

    private void saveRangesForSelectedModel() {
        commitRangeTableEdit();
        saveRangesForModel(currentRangeModel.isEmpty() ? selectedModel() : currentRangeModel);
    }

    private void saveRangesForModel(String modelName) {
        if (updatingRangeTable) {
            return;
        }
        File indexFile = findModelsExtJson();
        if (modelName.isEmpty() || indexFile == null || !indexFile.isFile()) {
            statusLabel.setText("Frame ranges can be saved only for project models in models-ext.json.");
            return;
        }
        try {
            JSONObject root = readJsonObject(indexFile);
            JSONArray models = root.optJSONArray("models");
            JSONObject target = findModelObject(models, modelName);
            if (target == null) {
                statusLabel.setText("Frame ranges can be saved only for editable project model entries.");
                return;
            }
            JSONArray rangeMetadata = rangesToJson();
            target.put("animationFrameRanges", rangeMetadata);
            applyRangeSourceAnimationMetadata(target, rangeMetadata);
            Files.write(indexFile.toPath(), root.toString(2).getBytes(StandardCharsets.UTF_8));
            statusLabel.setText("Saved frame ranges for " + modelName + ".");
        } catch (Exception ex) {
            ex.printStackTrace();
            statusLabel.setText("Could not save frame ranges: " + rootMessage(ex));
        }
    }

    private void saveSelectedModelAsNativeModel() {
        commitRangeTableEdit();
        String modelName = currentRangeModel.isEmpty() ? selectedModel() : currentRangeModel;
        File indexFile = findModelsExtJson();
        if (modelName.isEmpty() || indexFile == null || !indexFile.isFile()) {
            statusLabel.setText("Native model export can be saved only for project models in models-ext.json.");
            return;
        }

        JSONArray rangeMetadata = rangesToJson();
        ModelJ3oClipExporter.TextureOptimizationOptions textureOptions = chooseTextureOptimizationOptions();
        if (textureOptions == null) {
            return;
        }

        saveAsNativeModelButton.setEnabled(false);
        statusLabel.setText("Saving native model...");
        SwingWorker<J3oExportResult, Void> worker = new SwingWorker<>() {
            @Override
            protected J3oExportResult doInBackground() throws Exception {
                return exportSelectedModelAsNativeModel(indexFile, modelName, rangeMetadata, textureOptions);
            }

            @Override
            protected void done() {
                saveAsNativeModelButton.setEnabled(true);
                try {
                    J3oExportResult result = get();
                    app.reloadProjectAssets();
                    loadModelOptions();
                    modelCombo.setSelectedItem(result.modelName);
                    loadSelectedModel();
                    String splitNote = result.splitClips ? " with split clips" : "";
                    statusLabel.setText("Saved native model" + splitNote + ": "
                            + result.modelName + " (" + result.relativePath + ").");
                    showJ3oOptimizationTips(result);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    statusLabel.setText("Could not save native model: " + rootMessage(ex));
                }
            }
        };
        worker.execute();
    }

    private J3oExportResult exportSelectedModelAsNativeModel(File indexFile, String modelName, JSONArray rangeMetadata,
                                                             ModelJ3oClipExporter.TextureOptimizationOptions textureOptions) throws Exception {
        JSONObject root = readJsonObject(indexFile);
        JSONArray models = root.optJSONArray("models");
        if (models == null) {
            models = new JSONArray();
            root.put("models", models);
        }
        JSONObject sourceModel = findModelObject(models, modelName);
        if (sourceModel == null) {
            throw new IOException("Model entry was not found in models-ext.json: " + modelName);
        }
        String sourcePath = sourceModel.optString("path", "").trim();
        if (sourcePath.isEmpty()) {
            throw new IOException("Model entry has no path: " + modelName);
        }
        if (!isNativeSaveSupportedPath(sourcePath)) {
            throw new IOException("Save As Native Model supports GLB, GLTF, FBX, and J3O source models.");
        }
        List<ModelJ3oClipExporter.AnimationFrameRange> frameRanges = rangesForJ3oExport();

        File sourceFile = resolveResourceFile(sourcePath);
        if (!sourceFile.isFile()) {
            throw new IOException("Model file was not found: " + sourceFile.getAbsolutePath());
        }

        String nativeModelName = nativeModelName(modelName);
        String nativeRelativePath = nativeRelativePath(sourcePath);
        File outputFile = resolveResourceFile(nativeRelativePath);
        ModelJ3oClipExporter.export(sourceFile, outputFile, "", frameRanges, textureOptions);
        File textureFolder = nativeTextureFolder(outputFile);
        long textureBytes = folderSize(textureFolder);
        int textureCount = fileCount(textureFolder);

        sourceModel.put("animationFrameRanges", new JSONArray(rangeMetadata.toString()));
        applyRangeSourceAnimationMetadata(sourceModel, rangeMetadata);

        JSONObject j3oModel = new JSONObject(sourceModel.toString());
        j3oModel.put("name", nativeModelName);
        j3oModel.put("path", nativeRelativePath);
        j3oModel.put("sourceModel", modelName);
        j3oModel.put("animationFrameRanges", new JSONArray(rangeMetadata.toString()));
        String commonSourceAnimation = commonRangeSourceAnimation(rangeMetadata);
        if (commonSourceAnimation.isEmpty()) {
            j3oModel.remove("sourceAnimation");
            j3oModel.remove("animationFrameRangesSourceAnimation");
        } else {
            j3oModel.put("sourceAnimation", commonSourceAnimation);
            j3oModel.put("animationFrameRangesSourceAnimation", commonSourceAnimation);
        }

        for (int i = 0; i < models.length(); i++) {
            JSONObject existing = models.optJSONObject(i);
            if (existing != null && nativeModelName.equalsIgnoreCase(existing.optString("name", ""))) {
                models.remove(i);
                break;
            }
        }
        models.put(j3oModel);
        Files.write(indexFile.toPath(), root.toString(2).getBytes(StandardCharsets.UTF_8));
        return new J3oExportResult(nativeModelName, nativeRelativePath, !frameRanges.isEmpty(),
                sourceFile.length(), outputFile.length(), textureBytes, textureCount,
                textureOptions == null ? "off" : textureOptions.summary());
    }

    private String nativeModelName(String modelName) {
        String trimmed = modelName == null ? "" : modelName.trim();
        return trimmed.toLowerCase(Locale.ROOT).endsWith("_native") ? trimmed : trimmed + "_native";
    }

    private ModelJ3oClipExporter.TextureOptimizationOptions chooseTextureOptimizationOptions() {
        JCheckBox enable = new JCheckBox("Optimize exported textures");
        enable.setSelected(false);

        JComboBox<String> maxSize = new JComboBox<>(new String[]{
                "Keep dimensions", "4096 px", "2048 px", "1024 px", "512 px"
        });
        maxSize.setSelectedItem("2048 px");

        JSlider quality = new JSlider(50, 95, 82);
        quality.setMajorTickSpacing(15);
        quality.setPaintTicks(true);
        quality.setPaintLabels(true);

        JCheckBox convertColorPng = new JCheckBox("Convert color/gloss/roughness PNG textures to JPEG");
        convertColorPng.setSelected(true);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 8, 0);
        panel.add(enable, gbc);

        gbc.gridy++;
        gbc.gridwidth = 1;
        gbc.insets = new Insets(0, 0, 6, 10);
        panel.add(new JLabel("Max texture size:"), gbc);
        gbc.gridx = 1;
        gbc.insets = new Insets(0, 0, 6, 0);
        panel.add(maxSize, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        panel.add(new JLabel("JPEG quality:"), gbc);
        gbc.gridy++;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(quality, gbc);

        gbc.gridy++;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(6, 0, 0, 0);
        panel.add(convertColorPng, gbc);

        JLabel note = new JLabel("<html>Normal, bump, height, alpha, opacity, and mask maps stay lossless.</html>");
        gbc.gridy++;
        gbc.insets = new Insets(8, 0, 0, 0);
        panel.add(note, gbc);

        int answer = JOptionPane.showConfirmDialog(this, panel, "Native Model Texture Optimization",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return null;
        }
        if (!enable.isSelected()) {
            return ModelJ3oClipExporter.TextureOptimizationOptions.disabled();
        }
        return ModelJ3oClipExporter.TextureOptimizationOptions.enabled(
                selectedMaxTextureSize(String.valueOf(maxSize.getSelectedItem())),
                quality.getValue(),
                convertColorPng.isSelected());
    }

    private int selectedMaxTextureSize(String value) {
        if (value == null || value.startsWith("Keep")) {
            return 0;
        }
        String digits = value.replaceAll("[^0-9]", "");
        return parseInt(digits, 0);
    }

    private List<ModelJ3oClipExporter.AnimationFrameRange> rangesForJ3oExport() throws IOException {
        List<ModelJ3oClipExporter.AnimationFrameRange> ranges = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int row = 0; row < rangeTableModel.getRowCount(); row++) {
            String name = rangeCell(row, RANGE_NAME_COLUMN);
            if (name.isEmpty()) {
                throw new IOException("Every J3O clip row must have a name.");
            }
            if (!names.add(name.toLowerCase(Locale.ROOT))) {
                throw new IOException("Duplicate J3O clip name: " + name);
            }
            String sourceAnimation = rangeCell(row, RANGE_SOURCE_ANIMATION_COLUMN);
            if (sourceAnimation.isEmpty()) {
                throw new IOException("J3O clip '" + name + "' must have a source animation.");
            }
            int start = Math.max(0, parseInt(rangeTableModel.getValueAt(row, RANGE_START_COLUMN), 0));
            int end = Math.max(0, parseInt(rangeTableModel.getValueAt(row, RANGE_END_COLUMN), start));
            if (end <= start) {
                throw new IOException("J3O clip '" + name + "' must have an end frame after its start frame.");
            }
            ranges.add(new ModelJ3oClipExporter.AnimationFrameRange(name, sourceAnimation, start, end));
        }
        return ranges;
    }

    private void updateSaveAsNativeModelButtonVisibility() {
        JSONObject model = findEditableModelJson(currentRangeModel.isEmpty() ? selectedModel() : currentRangeModel);
        boolean visible = model != null && isNativeSaveSupportedPath(model.optString("path", ""));
        saveAsNativeModelButton.setVisible(visible);
        saveAsNativeModelButton.setEnabled(visible);
        Container parent = saveAsNativeModelButton.getParent();
        if (parent != null) {
            parent.revalidate();
            parent.repaint();
        }
    }

    private File resolveResourceFile(String resourcePath) {
        File file = new File(resourcePath);
        if (file.isAbsolute()) {
            return file;
        }
        return new File(resourcesRoot, resourcePath.replace('/', File.separatorChar));
    }

    private String nativeRelativePath(String sourcePath) {
        String normalized = sourcePath.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String folder = slash >= 0 ? normalized.substring(0, slash + 1) : "";
        String fileName = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return folder + nativeModelFileBase(fileName) + ".j3o";
    }

    private String nativeModelFileBase(String fileName) {
        String base = stripModelExtension(fileName);
        return base.toLowerCase(Locale.ROOT).endsWith("_native") ? base : base + "_native";
    }

    private File nativeTextureFolder(File outputFile) {
        return new File(new File(outputFile.getParentFile(), "textures"), stripModelExtension(outputFile.getName()));
    }

    private String stripModelExtension(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mesh.xml")) {
            return fileName.substring(0, fileName.length() - ".mesh.xml".length());
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private boolean isNativeSaveSupportedPath(String path) {
        if (path == null) {
            return false;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".glb") || lower.endsWith(".gltf") || lower.endsWith(".fbx") || lower.endsWith(".j3o");
    }

    private void showJ3oOptimizationTips(J3oExportResult result) {
        if (result == null) {
            return;
        }
        long exportedBytes = result.j3oBytes + result.externalTextureBytes;
        if (exportedBytes < 10L * 1024L * 1024L && exportedBytes <= result.sourceBytes * 1.25) {
            return;
        }
        String message = "Native model export summary:\n"
                + "Source model: " + formatBytes(result.sourceBytes) + "\n"
                + "J3O file: " + formatBytes(result.j3oBytes) + "\n"
                + "External textures: " + result.externalTextureCount + " file(s), "
                + formatBytes(result.externalTextureBytes) + "\n"
                + "Total exported package: " + formatBytes(exportedBytes) + "\n\n"
                + "Texture optimization: " + result.textureOptimizationSummary + "\n\n"
                + "The J3O references external texture files so it stays small and loads quickly.\n\n"
                + "To make the exported model smaller:\n"
                + "- Downscale large texture maps before export.\n"
                + "- Use JPEG for color/gloss/roughness maps that do not need alpha.\n"
                + "- Keep normal maps higher quality when artifacts are visible.\n"
                + "- Remove unused material maps from the source model.";
        JOptionPane.showMessageDialog(this, message, "Native Model Optimization Tips", JOptionPane.INFORMATION_MESSAGE);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private long folderSize(File folder) {
        File[] files = folder == null ? null : folder.listFiles();
        if (files == null) {
            return 0;
        }
        long total = 0;
        for (File file : files) {
            total += file.isDirectory() ? folderSize(file) : file.length();
        }
        return total;
    }

    private int fileCount(File folder) {
        File[] files = folder == null ? null : folder.listFiles();
        if (files == null) {
            return 0;
        }
        int total = 0;
        for (File file : files) {
            total += file.isDirectory() ? fileCount(file) : 1;
        }
        return total;
    }

    private JSONArray rangesToJson() {
        JSONArray ranges = new JSONArray();
        for (int row = 0; row < rangeTableModel.getRowCount(); row++) {
            String name = rangeCell(row, RANGE_NAME_COLUMN);
            String sourceAnimation = rangeCell(row, RANGE_SOURCE_ANIMATION_COLUMN);
            int start = Math.max(0, parseInt(rangeTableModel.getValueAt(row, RANGE_START_COLUMN), 0));
            int end = Math.max(0, parseInt(rangeTableModel.getValueAt(row, RANGE_END_COLUMN), start));
            JSONObject range = new JSONObject();
            range.put("name", name);
            if (!sourceAnimation.isEmpty()) {
                range.put("sourceAnimation", sourceAnimation);
            }
            range.put("start", start);
            range.put("end", end);
            ranges.put(range);
        }
        return ranges;
    }

    private void applyRangeSourceAnimationMetadata(JSONObject model, JSONArray ranges) {
        String commonSourceAnimation = commonRangeSourceAnimation(ranges);
        if (commonSourceAnimation.isEmpty()) {
            model.remove("animationFrameRangesSourceAnimation");
        } else {
            model.put("animationFrameRangesSourceAnimation", commonSourceAnimation);
        }
    }

    private String commonRangeSourceAnimation(JSONArray ranges) {
        if (ranges == null || ranges.length() == 0) {
            return "";
        }
        String common = "";
        for (int i = 0; i < ranges.length(); i++) {
            JSONObject range = ranges.optJSONObject(i);
            if (range == null) {
                continue;
            }
            String sourceAnimation = range.optString("sourceAnimation", "").trim();
            if (sourceAnimation.isEmpty()) {
                return "";
            }
            if (common.isEmpty()) {
                common = sourceAnimation;
            } else if (!common.equals(sourceAnimation)) {
                return "";
            }
        }
        return common;
    }

    private String rangeCell(int row, int column) {
        Object value = rangeTableModel.getValueAt(row, column);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private JSONObject findEditableModelJson(String modelName) {
        File indexFile = findModelsExtJson();
        if (indexFile == null || !indexFile.isFile()) {
            return null;
        }
        try {
            JSONObject root = readJsonObject(indexFile);
            return findModelObject(root.optJSONArray("models"), modelName);
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private JSONObject findModelObject(JSONArray models, String modelName) {
        if (models == null || modelName == null) {
            return null;
        }
        for (int i = 0; i < models.length(); i++) {
            JSONObject model = models.optJSONObject(i);
            if (model != null && modelName.equalsIgnoreCase(model.optString("name", ""))) {
                return model;
            }
        }
        return null;
    }

    private File findModelsExtJson() {
        if (resourcesRoot == null) {
            return null;
        }
        File upper = new File(new File(resourcesRoot, "Models"), "models-ext.json");
        if (upper.isFile()) {
            return upper;
        }
        File lower = new File(new File(resourcesRoot, "models"), "models-ext.json");
        if (lower.isFile()) {
            return lower;
        }
        return upper;
    }

    private JSONObject readJsonObject(File file) throws Exception {
        if (file == null || !file.isFile()) {
            return new JSONObject().put("models", new JSONArray());
        }
        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        return text.trim().isEmpty() ? new JSONObject().put("models", new JSONArray()) : new JSONObject(text);
    }

    private void commitRangeTableEdit() {
        if (rangeTable.isEditing()) {
            rangeTable.getCellEditor().stopCellEditing();
        }
    }

    private int parseInt(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.toString() : current.getMessage();
    }

    private static class J3oExportResult {
        final String modelName;
        final String relativePath;
        final boolean splitClips;
        final long sourceBytes;
        final long j3oBytes;
        final long externalTextureBytes;
        final int externalTextureCount;
        final String textureOptimizationSummary;

        J3oExportResult(String modelName, String relativePath, boolean splitClips,
                        long sourceBytes, long j3oBytes, long externalTextureBytes, int externalTextureCount,
                        String textureOptimizationSummary) {
            this.modelName = modelName;
            this.relativePath = relativePath;
            this.splitClips = splitClips;
            this.sourceBytes = sourceBytes;
            this.j3oBytes = j3oBytes;
            this.externalTextureBytes = externalTextureBytes;
            this.externalTextureCount = externalTextureCount;
            this.textureOptimizationSummary = textureOptimizationSummary;
        }
    }

    private static class EditableRangeTable extends JTable {
        EditableRangeTable(TableModel model) {
            super(model);
        }

        @Override
        public boolean editCellAt(int row, int column, EventObject event) {
            boolean editing = super.editCellAt(row, column, event);
            if (editing) {
                Component editor = getEditorComponent();
                if (editor != null) {
                    SwingUtilities.invokeLater(() -> {
                        editor.requestFocusInWindow();
                        if (editor instanceof JTextField) {
                            ((JTextField) editor).selectAll();
                        }
                    });
                }
            }
            return editing;
        }
    }

    private void releaseMouseCapture() {
        canvas.setCursor(Cursor.getDefaultCursor());
        try {
            if (Mouse.isCreated() && Mouse.isGrabbed()) {
                Mouse.setGrabbed(false);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void close() {
        rangeSliderPlaybackTimer.stop();
        releaseMouseCapture();
        app.stop();
    }
}
