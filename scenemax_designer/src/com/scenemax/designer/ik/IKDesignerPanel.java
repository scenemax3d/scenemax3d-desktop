package com.scenemax.designer.ik;

import com.scenemaxeng.common.ik.IKDefinition;
import com.scenemaxeng.common.ik.IKLayerDefinition;
import com.scenemaxeng.common.ik.IKPresetLibrary;
import com.scenemaxeng.common.ik.IKValidationIssue;
import com.scenemaxeng.common.ik.IKValidationResult;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class IKDesignerPanel extends JPanel {
    private final File ikFile;
    private final IKPreviewPanel previewPanel;
    private IKDefinition document;
    private boolean dirty;
    private boolean updatingUi;
    private Runnable onDirtyCallback;
    private Runnable onSavedCallback;

    private final JTextField txtId = new JTextField();
    private final JTextField txtName = new JTextField();
    private final JComboBox<String> cboTargetModel = new JComboBox<>();
    private final DefaultListModel<String> layerListModel = new DefaultListModel<>();
    private final JList<String> layerList = new JList<>(layerListModel);
    private int selectedLayerIndex = 0;

    private final JTextField txtLayerId = new JTextField();
    private final JTextField txtLayerName = new JTextField();
    private final JComboBox<String> cboSolver = new JComboBox<>(new String[]{
            IKLayerDefinition.SOLVER_TWO_BONE,
            IKLayerDefinition.SOLVER_THREE_BONE,
            IKLayerDefinition.SOLVER_LOOK_AT,
            IKLayerDefinition.SOLVER_FOOT,
            IKLayerDefinition.SOLVER_FABRIK,
            IKLayerDefinition.SOLVER_AIM
    });
    private final JComboBox<IKUseCaseTemplate> cboUseCase = new JComboBox<>(IKUseCaseTemplate.defaults());
    private final JCheckBox chkEnabled = new JCheckBox("Enabled");
    private final JSpinner spnWeight = spinner(1.0, 0.0, 1.0, 0.05);
    private final JSpinner spnPriority = spinner(0, -100, 100, 1);
    private final JComboBox<String> cboRoot = editableCombo();
    private final JComboBox<String> cboMiddle = editableCombo();
    private final JComboBox<String> cboSecondMiddle = editableCombo();
    private final JComboBox<String> cboEnd = editableCombo();
    private final JComboBox<String> cboStart = editableCombo();
    private final JComboBox<String> cboAffectedJoint = editableCombo();
    private final JTextField txtAffected = new JTextField();
    private final JComboBox<String> cboTarget = editableCombo();
    private final JTextField txtPole = new JTextField();
    private final JCheckBox chkStretch = new JCheckBox("Allow Stretch");
    private final JSpinner spnMaxStretch = spinner(1.05, 1.0, 5.0, 0.01);
    private final JSpinner spnMaxAngle = spinner(90.0, 0.0, 180.0, 1.0);
    private final JSpinner spnIterations = spinner(8, 1, 64, 1);
    private final JTextArea txtValidation = new JTextArea(5, 30);
    private JPanel rootRow;
    private JPanel middleRow;
    private JPanel secondMiddleRow;
    private JPanel endRow;
    private JPanel startRow;
    private JPanel affectedRow;
    private JPanel targetRow;
    private JPanel poleRow;
    private List<String> availableJointNames = Collections.emptyList();

    public IKDesignerPanel(File ikFile) {
        super(new BorderLayout());
        this.ikFile = ikFile;
        setMinimumSize(new Dimension(0, 0));
        loadDocument();
        previewPanel = new IKPreviewPanel(IKRuntimeResourceExporter.findResourcesRoot(ikFile));
        buildUi();
        refreshFromDocument();
    }

    public void setOnDirtyCallback(Runnable onDirtyCallback) {
        this.onDirtyCallback = onDirtyCallback;
    }

    public void setOnSavedCallback(Runnable onSavedCallback) {
        this.onSavedCallback = onSavedCallback;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void saveDocument() {
        applyUiToDocument();
        try {
            document.save(ikFile);
            IKRuntimeResourceExporter.export(ikFile, document);
            dirty = false;
            if (onSavedCallback != null) {
                onSavedCallback.run();
            }
            refreshValidation();
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error saving IK asset: " + ex.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void reloadFromDisk() {
        loadDocument();
        dirty = false;
        refreshFromDocument();
        if (onSavedCallback != null) {
            onSavedCallback.run();
        }
    }

    public void discardEditorState() {
        dirty = false;
    }

    public void activatePanel() {
    }

    public void deactivatePanel() {
        if (dirty) {
            saveDocument();
        }
    }

    public void clearAndDeactivatePanel() {
        deactivatePanel();
        disposePreview();
    }

    public void disposePreview() {
        previewPanel.disposePreview();
    }

    private void loadDocument() {
        try {
            if (ikFile.exists() && ikFile.length() > 0) {
                document = IKDefinition.load(ikFile);
            } else {
                document = IKDefinition.createTemplate(stripExtension(ikFile.getName()), IKLayerDefinition.SOLVER_TWO_BONE);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            document = IKDefinition.createTemplate(stripExtension(ikFile.getName()), IKLayerDefinition.SOLVER_TWO_BONE);
        }
    }

    private void buildUi() {
        setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel header = new JPanel(new BorderLayout(8, 2));
        JLabel title = new JLabel("IK Designer");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        JButton save = new JButton("Save");
        save.addActionListener(e -> saveDocument());
        cboSolver.addActionListener(e -> updateSolverFieldVisibility());
        header.add(title, BorderLayout.WEST);
        header.add(save, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildEditorPanel(), previewPanel);
        mainSplit.setContinuousLayout(true);
        mainSplit.setResizeWeight(0.58);
        mainSplit.setDividerLocation(720);
        add(mainSplit, BorderLayout.CENTER);

        txtValidation.setEditable(false);
        txtValidation.setLineWrap(true);
        txtValidation.setWrapStyleWord(true);
        add(new JScrollPane(txtValidation), BorderLayout.SOUTH);
        updatingUi = true;
        updateTargetOptions("");
        updatingUi = false;
        bindDirtyTracking(this);
    }

    private JComponent buildEditorPanel() {
        JSplitPane editorSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildLeftPanel(), buildLayerFormPanel());
        editorSplit.setContinuousLayout(true);
        editorSplit.setResizeWeight(0.42);
        editorSplit.setDividerLocation(260);
        editorSplit.setMinimumSize(new Dimension(120, 120));
        return editorSplit;
    }

    private JComponent buildLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(8, 0, 8, 8));
        panel.setMinimumSize(new Dimension(90, 120));
        cboTargetModel.setPrototypeDisplayValue("MMMMMMMMMMMMMMMMMMMMMMMM");
        cboTargetModel.addActionListener(e -> onTargetModelChanged());
        cboTarget.setToolTipText("Runtime scene entity name. Use ik_preview_target for the designer test target.");
        previewPanel.setCompatibleModelsChangedCallback(this::setAvailableTargetModels);
        previewPanel.setJointNamesChangedCallback(this::setAvailableJoints);
        panel.add(form(row("IK ID", txtId), row("Name", txtName), row("Target Model", cboTargetModel)), BorderLayout.NORTH);

        layerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        layerList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || updatingUi) {
                return;
            }
            int index = layerList.getSelectedIndex();
            if (index < 0 || index == selectedLayerIndex) {
                return;
            }
            IKLayerDefinition previousLayer = layerAt(selectedLayerIndex);
            if (previousLayer != null) {
                applyLayerFieldsToLayer(previousLayer);
            }
            selectedLayerIndex = index;
            rebuildLayerListAndSelect(selectedLayerIndex);
            refreshValidation();
            refreshLayerFields();
        });
        panel.add(new JScrollPane(layerList), BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(0, 1, 4, 4));
        JButton add = new JButton("Add");
        add.addActionListener(e -> addLayer());
        JButton duplicate = new JButton("Duplicate");
        duplicate.addActionListener(e -> duplicateLayer());
        JButton delete = new JButton("Delete");
        delete.addActionListener(e -> deleteLayer());
        JButton presets = new JButton("Humanoid");
        presets.addActionListener(e -> addHumanoidPreset());
        actions.add(add);
        actions.add(duplicate);
        actions.add(delete);
        actions.add(presets);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent buildLayerFormPanel() {
        JPanel body = new JPanel(new BorderLayout(8, 8));
        body.setBorder(new EmptyBorder(8, 8, 8, 8));
        JPanel layerForm = buildLayerForm();
        body.add(new JScrollPane(layerForm), BorderLayout.CENTER);
        return body;
    }

    private JPanel buildLayerForm() {
        rootRow = rowWithHelp("Root Joint", cboRoot, "First joint in a bending chain, closest to the body.");
        middleRow = rowWithHelp("Middle Joint", cboMiddle, "The hinge joint, such as an elbow or knee.");
        secondMiddleRow = rowWithHelp("Middle 2 Joint", cboSecondMiddle, "Second bend joint for a four-joint / three-bone chain.");
        endRow = rowWithHelp("End Joint", cboEnd, "The joint that reaches or aims toward the target.");
        startRow = rowWithHelp("Start Joint", cboStart, "First joint in a FABRIK-style chain.");
        affectedRow = rowWithHelp("Affected Joints", affectedJointsEditor(), "LookAt/Aim joints allowed to rotate toward the target.");
        targetRow = rowWithHelp("Target Object", cboTarget, "Scene entity to reach or look at. Use ik_preview_target while testing.");
        poleRow = rowWithHelp("Pole Target", txtPole, "Optional scene entity that controls elbow/knee bend direction.");
        return form(
            row("Layer ID", txtLayerId),
            row("Layer Name", txtLayerName),
            row("Solver", cboSolver),
            row("Use Case", useCaseEditor()),
            row("Preview", previewActionsEditor()),
            row("Enabled", chkEnabled),
            row("Weight", spnWeight),
            row("Priority", spnPriority),
            rootRow,
            middleRow,
            secondMiddleRow,
            endRow,
            startRow,
            affectedRow,
            targetRow,
            poleRow,
            row("Stretch", chkStretch),
            row("Max Stretch", spnMaxStretch),
            row("Max Angle", spnMaxAngle),
            row("Iterations", spnIterations)
        );
    }

    private void refreshFromDocument() {
        updatingUi = true;
        txtId.setText(document.getId());
        txtName.setText(document.getName());
        setModelComboSelection(document.getTargetModelId());
        rebuildLayerListAndSelect(selectedLayerIndex);
        selectedLayerIndex = Math.min(selectedLayerIndex, Math.max(0, document.getLayers().size() - 1));
        if (!document.getLayers().isEmpty()) {
            layerList.setSelectedIndex(selectedLayerIndex);
        }
        refreshLayerFields();
        updatingUi = false;
        previewPanel.setTargetModelId(document.getTargetModelId());
        refreshValidation();
    }

    private void rebuildLayerList() {
        layerListModel.clear();
        for (IKLayerDefinition layer : document.getLayers()) {
            String name = layer.getName() == null || layer.getName().trim().isEmpty() ? layer.getSolverType() : layer.getName();
            layerListModel.addElement(name + "  [" + layer.getSolverType() + "]");
        }
    }

    private void rebuildLayerListAndSelect(int selectionIndex) {
        boolean wasUpdatingUi = updatingUi;
        updatingUi = true;
        rebuildLayerList();
        if (document == null || document.getLayers().isEmpty()) {
            layerList.clearSelection();
        } else {
            layerList.setSelectedIndex(Math.min(Math.max(0, selectionIndex), document.getLayers().size() - 1));
        }
        updatingUi = wasUpdatingUi;
    }

    private void refreshLayerFields() {
        updatingUi = true;
        IKLayerDefinition layer = selectedLayer();
        if (layer == null) {
            txtLayerId.setText("");
            txtLayerName.setText("");
            updatingUi = false;
            return;
        }
        txtLayerId.setText(layer.getId());
        txtLayerName.setText(layer.getName());
        cboSolver.setSelectedItem(layer.getSolverType());
        chkEnabled.setSelected(layer.isEnabled());
        spnWeight.setValue((double) layer.getWeight());
        spnPriority.setValue(layer.getPriority());
        setComboSelection(cboRoot, layer.getRootJoint());
        setComboSelection(cboMiddle, layer.getMiddleJoint());
        setComboSelection(cboSecondMiddle, layer.getSecondMiddleJoint());
        setComboSelection(cboEnd, layer.getEndJoint());
        setComboSelection(cboStart, layer.getStartJoint());
        txtAffected.setText(String.join(", ", layer.getAffectedJoints()));
        setComboSelection(cboTarget, layer.getTarget());
        txtPole.setText(layer.getPoleTarget());
        chkStretch.setSelected(layer.isAllowStretch());
        spnMaxStretch.setValue((double) layer.getMaxStretch());
        spnMaxAngle.setValue((double) layer.getMaxAngle());
        spnIterations.setValue(layer.getIterations());
        updateSolverFieldVisibility();
        updatingUi = false;
        updatePreviewHighlights();
    }

    private void applyUiToDocument() {
        document.setId(txtId.getText().trim());
        document.setName(txtName.getText().trim());
        document.setTargetModelId(selectedTargetModel());
        applySelectedLayerFromUi();
    }

    private void onTargetModelChanged() {
        if (updatingUi) {
            return;
        }
        String modelId = selectedTargetModel();
        document.setTargetModelId(modelId);
        previewPanel.setTargetModelId(modelId);
        refreshValidation();
        markDirty();
    }

    private void setAvailableTargetModels(List<String> modelIds) {
        String current = document == null ? "" : document.getTargetModelId();
        if (current == null || current.isBlank()) {
            current = selectedTargetModel();
        }
        List<String> values = modelIds == null ? Collections.emptyList() : new ArrayList<>(modelIds);
        updatingUi = true;
        cboTargetModel.removeAllItems();
        cboTargetModel.addItem("");
        boolean containsCurrent = current == null || current.isBlank();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            cboTargetModel.addItem(value);
            if (value.equalsIgnoreCase(current)) {
                containsCurrent = true;
            }
        }
        if (!containsCurrent && current != null && !current.isBlank()) {
            cboTargetModel.addItem(current);
        }
        setModelComboSelection(current);
        updatingUi = false;
    }

    private void setAvailableJoints(List<String> jointNames) {
        List<String> values = jointNames == null ? Collections.emptyList() : new ArrayList<>(jointNames);
        availableJointNames = values;
        updatingUi = true;
        updateComboItems(cboRoot, values, selectedComboText(cboRoot));
        updateComboItems(cboMiddle, values, selectedComboText(cboMiddle));
        updateComboItems(cboSecondMiddle, values, selectedComboText(cboSecondMiddle));
        updateComboItems(cboEnd, values, selectedComboText(cboEnd));
        updateComboItems(cboStart, values, selectedComboText(cboStart));
        updateComboItems(cboAffectedJoint, values, selectedComboText(cboAffectedJoint));
        updateTargetOptions(selectedComboText(cboTarget));
        updatingUi = false;
        updatePreviewHighlights();
    }

    private void updateTargetOptions(String current) {
        List<String> values = new ArrayList<>();
        values.add(IKPreviewApp.PREVIEW_TARGET_ID);
        updateComboItems(cboTarget, values, current);
    }

    private void setModelComboSelection(String modelId) {
        String value = modelId == null ? "" : modelId.trim();
        if (!value.isEmpty() && !comboContains(cboTargetModel, value)) {
            cboTargetModel.addItem(value);
        }
        cboTargetModel.setSelectedItem(value);
    }

    private static void updateComboItems(JComboBox<String> combo, List<String> values, String current) {
        String selected = current == null ? "" : current.trim();
        combo.removeAllItems();
        combo.addItem("");
        boolean containsSelected = selected.isEmpty();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            combo.addItem(value);
            if (value.equalsIgnoreCase(selected)) {
                containsSelected = true;
            }
        }
        if (!containsSelected) {
            combo.addItem(selected);
        }
        combo.setSelectedItem(selected);
    }

    private static void setComboSelection(JComboBox<String> combo, String value) {
        String selected = value == null ? "" : value.trim();
        if (!selected.isEmpty() && !comboContains(combo, selected)) {
            combo.addItem(selected);
        }
        combo.setSelectedItem(selected);
    }

    private static boolean comboContains(JComboBox<String> combo, String value) {
        ComboBoxModel<String> model = combo.getModel();
        for (int i = 0; i < model.getSize(); i++) {
            String item = model.getElementAt(i);
            if (value.equalsIgnoreCase(item)) {
                return true;
            }
        }
        return false;
    }

    private String selectedTargetModel() {
        Object selected = cboTargetModel.getSelectedItem();
        return selected == null ? "" : String.valueOf(selected).trim();
    }

    private static String selectedComboText(JComboBox<String> combo) {
        Object selected = combo.getEditor() != null && combo.isEditable()
                ? combo.getEditor().getItem()
                : combo.getSelectedItem();
        return selected == null ? "" : String.valueOf(selected).trim();
    }

    private JComponent affectedJointsEditor() {
        JPanel panel = new JPanel(new BorderLayout(4, 0));
        JButton add = new JButton("Add");
        add.addActionListener(e -> addAffectedJoint());
        cboAffectedJoint.setPrototypeDisplayValue("MMMMMMMMMMMMMMMMMMMMMMMM");
        panel.add(txtAffected, BorderLayout.CENTER);
        panel.add(cboAffectedJoint, BorderLayout.NORTH);
        panel.add(add, BorderLayout.EAST);
        return panel;
    }

    private void addAffectedJoint() {
        String joint = selectedComboText(cboAffectedJoint);
        if (joint.isEmpty()) {
            return;
        }
        List<String> joints = new ArrayList<>();
        for (String value : txtAffected.getText().split(",")) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty() && joints.stream().noneMatch(existing -> existing.equalsIgnoreCase(trimmed))) {
                joints.add(trimmed);
            }
        }
        if (joints.stream().noneMatch(existing -> existing.equalsIgnoreCase(joint))) {
            joints.add(joint);
        }
        txtAffected.setText(String.join(", ", joints));
    }

    private JComponent useCaseEditor() {
        JPanel panel = new JPanel(new BorderLayout(4, 0));
        JButton apply = new JButton("Apply");
        apply.addActionListener(e -> applySelectedUseCase());
        panel.add(cboUseCase, BorderLayout.CENTER);
        panel.add(apply, BorderLayout.EAST);
        return panel;
    }

    private JComponent previewActionsEditor() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton run = new JButton("Run IK");
        run.addActionListener(e -> runSelectedLayerPreview());
        JButton play = new JButton("Play");
        play.addActionListener(e -> playSelectedLayerPreview());
        JButton stop = new JButton("Stop");
        stop.addActionListener(e -> previewPanel.stopLayerPreview());
        JButton reset = new JButton("Reset Pose");
        reset.addActionListener(e -> previewPanel.resetModelPose());
        panel.add(run);
        panel.add(play);
        panel.add(stop);
        panel.add(reset);
        return panel;
    }

    private void runSelectedLayerPreview() {
        if (selectedLayer() == null) {
            return;
        }
        applySelectedLayerFromUi();
        previewPanel.runLayerPreview(IKLayerDefinition.fromJSON(selectedLayer().toJSON()));
    }

    private void playSelectedLayerPreview() {
        if (selectedLayer() == null) {
            return;
        }
        applySelectedLayerFromUi();
        previewPanel.playLayerPreview(IKLayerDefinition.fromJSON(selectedLayer().toJSON()));
    }

    private void applySelectedUseCase() {
        Object selected = cboUseCase.getSelectedItem();
        if (!(selected instanceof IKUseCaseTemplate)) {
            return;
        }
        IKUseCaseTemplate template = (IKUseCaseTemplate) selected;
        IKUseCaseMatch match = template.match(availableJointNames);
        updatingUi = true;
        cboSolver.setSelectedItem(template.solverType);
        setComboSelection(cboRoot, match.root);
        setComboSelection(cboMiddle, match.middle);
        setComboSelection(cboSecondMiddle, match.secondMiddle);
        setComboSelection(cboEnd, match.end);
        setComboSelection(cboStart, match.start);
        txtAffected.setText(String.join(", ", match.affected));
        if (template.needsTarget && selectedComboText(cboTarget).isEmpty()) {
            setComboSelection(cboTarget, IKPreviewApp.PREVIEW_TARGET_ID);
        }
        updatingUi = false;
        updateSolverFieldVisibility();
        applySelectedLayerFromUi();
        updatePreviewHighlights();
        markDirty();
        if (!match.missing.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "I matched what I could. Please choose: " + String.join(", ", match.missing),
                    "Template Needs Help",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void applySelectedLayerFromUi() {
        IKLayerDefinition layer = selectedLayer();
        if (layer == null) {
            return;
        }
        applyLayerFieldsToLayer(layer);
        rebuildLayerListAndSelect(selectedLayerIndex);
        refreshValidation();
        updatePreviewHighlights();
    }

    private void applyLayerFieldsToLayer(IKLayerDefinition layer) {
        layer.setId(txtLayerId.getText().trim());
        layer.setName(txtLayerName.getText().trim());
        layer.setSolverType(String.valueOf(cboSolver.getSelectedItem()));
        layer.setEnabled(chkEnabled.isSelected());
        layer.setWeight(floatValue(spnWeight));
        layer.setPriority(intValue(spnPriority));
        layer.setRootJoint(selectedComboText(cboRoot));
        layer.setMiddleJoint(selectedComboText(cboMiddle));
        layer.setSecondMiddleJoint(selectedComboText(cboSecondMiddle));
        layer.setEndJoint(selectedComboText(cboEnd));
        layer.setStartJoint(selectedComboText(cboStart));
        layer.getAffectedJoints().clear();
        for (String value : txtAffected.getText().split(",")) {
            String joint = value.trim();
            if (!joint.isEmpty()) {
                layer.getAffectedJoints().add(joint);
            }
        }
        layer.setTarget(selectedComboText(cboTarget));
        layer.setPoleTarget(txtPole.getText().trim());
        layer.setAllowStretch(chkStretch.isSelected());
        layer.setMaxStretch(floatValue(spnMaxStretch));
        layer.setMaxAngle(floatValue(spnMaxAngle));
        layer.setIterations(intValue(spnIterations));
    }

    private void addLayer() {
        applyUiToDocument();
        IKLayerDefinition layer = new IKLayerDefinition();
        layer.setId("layer_" + (document.getLayers().size() + 1));
        layer.setName("IK Layer " + (document.getLayers().size() + 1));
        document.getLayers().add(layer);
        selectedLayerIndex = document.getLayers().size() - 1;
        markDirty();
        refreshFromDocument();
    }

    private void duplicateLayer() {
        applyUiToDocument();
        IKLayerDefinition layer = selectedLayer();
        if (layer == null) {
            return;
        }
        IKLayerDefinition copy = IKLayerDefinition.fromJSON(layer.toJSON());
        copy.setId(copy.getId() + "_copy");
        copy.setName(copy.getName() + " Copy");
        document.getLayers().add(copy);
        selectedLayerIndex = document.getLayers().size() - 1;
        markDirty();
        refreshFromDocument();
    }

    private void deleteLayer() {
        if (document.getLayers().size() <= 1 || selectedLayer() == null) {
            return;
        }
        document.getLayers().remove(selectedLayerIndex);
        selectedLayerIndex = Math.max(0, selectedLayerIndex - 1);
        markDirty();
        refreshFromDocument();
    }

    private void addHumanoidPreset() {
        List<IKDefinition> presets = IKPresetLibrary.humanoidPresets();
        String[] names = presets.stream().map(IKDefinition::getName).toArray(String[]::new);
        String choice = (String) JOptionPane.showInputDialog(this, "Preset", "Humanoid IK Presets",
                JOptionPane.PLAIN_MESSAGE, null, names, names.length == 0 ? null : names[0]);
        if (choice == null) {
            return;
        }
        for (IKDefinition preset : presets) {
            if (choice.equals(preset.getName())) {
                applyUiToDocument();
                document.getLayers().addAll(preset.getLayers());
                selectedLayerIndex = document.getLayers().size() - 1;
                markDirty();
                refreshFromDocument();
                return;
            }
        }
    }

    private IKLayerDefinition selectedLayer() {
        return layerAt(selectedLayerIndex);
    }

    private IKLayerDefinition layerAt(int index) {
        if (document == null || document.getLayers().isEmpty()
                || index < 0 || index >= document.getLayers().size()) {
            return null;
        }
        return document.getLayers().get(index);
    }

    private void refreshValidation() {
        if (document == null) {
            txtValidation.setText("");
            return;
        }
        IKValidationResult result = document.validate();
        if (result.getIssues().isEmpty()) {
            txtValidation.setText("IK asset is valid.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (IKValidationIssue issue : result.getIssues()) {
            sb.append(issue.getSeverity().toUpperCase(Locale.ROOT))
                    .append(" ")
                    .append(issue.getField())
                    .append(": ")
                    .append(issue.getMessage())
                    .append("\n");
        }
        txtValidation.setText(sb.toString().trim());
    }

    private void bindDirtyTracking(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof JTextField) {
                ((JTextField) component).getDocument().addDocumentListener(new SimpleDocumentListener(this::onFieldChanged));
            } else if (component instanceof JComboBox) {
                if (component == cboUseCase) {
                    continue;
                }
                ((JComboBox<?>) component).addActionListener(e -> onFieldChanged());
            } else if (component instanceof JCheckBox) {
                ((JCheckBox) component).addActionListener(e -> onFieldChanged());
            } else if (component instanceof JSpinner) {
                ((JSpinner) component).addChangeListener(e -> onFieldChanged());
            } else if (component instanceof Container) {
                bindDirtyTracking((Container) component);
            }
        }
    }

    private void onFieldChanged() {
        if (updatingUi) {
            return;
        }
        applySelectedLayerFromUi();
        markDirty();
    }

    private void updatePreviewHighlights() {
        Map<String, String> joints = new LinkedHashMap<>();
        String solver = String.valueOf(cboSolver.getSelectedItem());
        boolean twoBone = IKLayerDefinition.SOLVER_TWO_BONE.equalsIgnoreCase(solver);
        boolean threeBone = IKLayerDefinition.SOLVER_THREE_BONE.equalsIgnoreCase(solver);
        boolean foot = IKLayerDefinition.SOLVER_FOOT.equalsIgnoreCase(solver);
        boolean fabrik = IKLayerDefinition.SOLVER_FABRIK.equalsIgnoreCase(solver);
        boolean lookOrAim = IKLayerDefinition.SOLVER_LOOK_AT.equalsIgnoreCase(solver)
                || IKLayerDefinition.SOLVER_AIM.equalsIgnoreCase(solver);

        if (twoBone || foot) {
            addIfPresent(joints, selectedComboText(cboRoot), "Root");
            addIfPresent(joints, selectedComboText(cboMiddle), "Middle");
            addIfPresent(joints, selectedComboText(cboEnd), "End");
        } else if (threeBone) {
            addIfPresent(joints, selectedComboText(cboRoot), "Root");
            addIfPresent(joints, selectedComboText(cboMiddle), "Mid 1");
            addIfPresent(joints, selectedComboText(cboSecondMiddle), "Mid 2");
            addIfPresent(joints, selectedComboText(cboEnd), "End");
        } else if (fabrik) {
            addIfPresent(joints, selectedComboText(cboStart), "Start");
            addIfPresent(joints, selectedComboText(cboEnd), "End");
        } else if (lookOrAim) {
            String label = IKLayerDefinition.SOLVER_AIM.equalsIgnoreCase(solver) ? "Aim" : "Look";
            int index = 1;
            for (String value : txtAffected.getText().split(",")) {
                if (addIfPresent(joints, value.trim(), label + " " + index)) {
                    index++;
                }
            }
            if (joints.isEmpty()) {
                addIfPresent(joints, selectedComboText(cboEnd), label);
            }
        }
        previewPanel.setHighlightedJoints(joints);
    }

    private static boolean addIfPresent(Map<String, String> values, String value, String label) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (String existing : values.keySet()) {
            if (existing.equalsIgnoreCase(value)) {
                return false;
            }
        }
        values.put(value, label);
        return true;
    }

    private void markDirty() {
        if (!dirty) {
            dirty = true;
            if (onDirtyCallback != null) {
                onDirtyCallback.run();
            }
        }
    }

    private JPanel form(Component... rows) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        for (Component row : rows) {
            panel.add(row);
        }
        return panel;
    }

    private JPanel row(String label, JComponent input) {
        JPanel row = new JPanel(new BorderLayout(8, 2));
        row.setBorder(new EmptyBorder(4, 0, 4, 0));
        JLabel lbl = new JLabel(label);
        lbl.setPreferredSize(new Dimension(116, 24));
        row.add(lbl, BorderLayout.WEST);
        row.add(input, BorderLayout.CENTER);
        return row;
    }

    private JPanel rowWithHelp(String label, JComponent input, String helpText) {
        JPanel row = new JPanel(new BorderLayout(8, 2));
        row.setBorder(new EmptyBorder(4, 0, 4, 0));
        JLabel lbl = new JLabel(label);
        lbl.setPreferredSize(new Dimension(116, 24));
        row.add(lbl, BorderLayout.WEST);

        JPanel content = new JPanel(new BorderLayout(0, 2));
        content.add(input, BorderLayout.CENTER);
        JLabel help = new JLabel(helpText);
        help.setForeground(new Color(95, 105, 115));
        help.setFont(help.getFont().deriveFont(Font.PLAIN, Math.max(10f, help.getFont().getSize2D() - 1f)));
        content.add(help, BorderLayout.SOUTH);
        row.add(content, BorderLayout.CENTER);
        return row;
    }

    private void updateSolverFieldVisibility() {
        if (rootRow == null) {
            return;
        }
        String solver = String.valueOf(cboSolver.getSelectedItem());
        boolean twoBone = IKLayerDefinition.SOLVER_TWO_BONE.equalsIgnoreCase(solver);
        boolean threeBone = IKLayerDefinition.SOLVER_THREE_BONE.equalsIgnoreCase(solver);
        boolean foot = IKLayerDefinition.SOLVER_FOOT.equalsIgnoreCase(solver);
        boolean fabrik = IKLayerDefinition.SOLVER_FABRIK.equalsIgnoreCase(solver);
        boolean lookOrAim = IKLayerDefinition.SOLVER_LOOK_AT.equalsIgnoreCase(solver)
                || IKLayerDefinition.SOLVER_AIM.equalsIgnoreCase(solver);

        rootRow.setVisible(twoBone || threeBone || foot);
        middleRow.setVisible(twoBone || threeBone || foot);
        secondMiddleRow.setVisible(threeBone);
        endRow.setVisible(twoBone || threeBone || foot || fabrik || lookOrAim);
        startRow.setVisible(fabrik);
        affectedRow.setVisible(lookOrAim);
        targetRow.setVisible(!foot);
        poleRow.setVisible(twoBone || threeBone || foot);

        revalidate();
        repaint();
    }

    private static JSpinner spinner(double value, double min, double max, double step) {
        return new JSpinner(new SpinnerNumberModel(value, min, max, step));
    }

    private static JSpinner spinner(int value, int min, int max, int step) {
        return new JSpinner(new SpinnerNumberModel(value, min, max, step));
    }

    private static JComboBox<String> editableCombo() {
        JComboBox<String> combo = new JComboBox<>();
        combo.setEditable(true);
        combo.setPrototypeDisplayValue("MMMMMMMMMMMMMMMMMMMMMMMM");
        return combo;
    }

    private static float floatValue(JSpinner spinner) {
        return ((Number) spinner.getValue()).floatValue();
    }

    private static int intValue(JSpinner spinner) {
        return ((Number) spinner.getValue()).intValue();
    }

    private static String stripExtension(String name) {
        if (name == null) {
            return "ik";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(IKDefinition.FILE_EXTENSION)) {
            return name.substring(0, name.length() - IKDefinition.FILE_EXTENSION.length());
        }
        if (lower.endsWith(IKDefinition.LEGACY_FILE_EXTENSION)) {
            return name.substring(0, name.length() - IKDefinition.LEGACY_FILE_EXTENSION.length());
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static final class IKUseCaseTemplate {
        final String label;
        final String solverType;
        final boolean needsTarget;
        final String[][] rootPatterns;
        final String[][] middlePatterns;
        final String[][] secondMiddlePatterns;
        final String[][] endPatterns;
        final String[][] startPatterns;
        final String[][] affectedPatterns;

        IKUseCaseTemplate(String label, String solverType, boolean needsTarget,
                          String[][] rootPatterns, String[][] middlePatterns, String[][] secondMiddlePatterns, String[][] endPatterns,
                          String[][] startPatterns, String[][] affectedPatterns) {
            this.label = label;
            this.solverType = solverType;
            this.needsTarget = needsTarget;
            this.rootPatterns = rootPatterns;
            this.middlePatterns = middlePatterns;
            this.secondMiddlePatterns = secondMiddlePatterns;
            this.endPatterns = endPatterns;
            this.startPatterns = startPatterns;
            this.affectedPatterns = affectedPatterns;
        }

        static IKUseCaseTemplate[] defaults() {
            return new IKUseCaseTemplate[]{
                    new IKUseCaseTemplate("Right hand reach", IKLayerDefinition.SOLVER_TWO_BONE, true,
                            groups("rightarm", "rightupperarm", "upperarmr", "rupperarm", "rightshoulder"),
                            groups("rightforearm", "rightlowerarm", "forearmr", "lowerarmr", "rightelbow"),
                            null,
                            groups("righthand", "rightwrist", "handr", "wristr"),
                            null, null),
                    new IKUseCaseTemplate("Left hand reach", IKLayerDefinition.SOLVER_TWO_BONE, true,
                            groups("leftarm", "leftupperarm", "upperarml", "lupperarm", "leftshoulder"),
                            groups("leftforearm", "leftlowerarm", "forearml", "lowerarml", "leftelbow"),
                            null,
                            groups("lefthand", "leftwrist", "handl", "wristl"),
                            null, null),
                    new IKUseCaseTemplate("Right arm with shoulder", IKLayerDefinition.SOLVER_THREE_BONE, true,
                            groups("rightshoulder", "shoulderr", "rshoulder"),
                            groups("rightarm", "rightupperarm", "upperarmr", "rupperarm"),
                            groups("rightforearm", "rightlowerarm", "forearmr", "lowerarmr", "rightelbow"),
                            groups("righthand", "rightwrist", "handr", "wristr"),
                            null, null),
                    new IKUseCaseTemplate("Left arm with shoulder", IKLayerDefinition.SOLVER_THREE_BONE, true,
                            groups("leftshoulder", "shoulderl", "lshoulder"),
                            groups("leftarm", "leftupperarm", "upperarml", "lupperarm"),
                            groups("leftforearm", "leftlowerarm", "forearml", "lowerarml", "leftelbow"),
                            groups("lefthand", "leftwrist", "handl", "wristl"),
                            null, null),
                    new IKUseCaseTemplate("Right foot placement", IKLayerDefinition.SOLVER_FOOT, false,
                            groups("rightupleg", "rightupperleg", "rightthigh", "thighr", "upperlegr"),
                            groups("rightleg", "rightlowerleg", "rightknee", "shinr", "calfr", "lowerlegr"),
                            null,
                            groups("rightfoot", "rightankle", "footr", "ankler"),
                            null, null),
                    new IKUseCaseTemplate("Left foot placement", IKLayerDefinition.SOLVER_FOOT, false,
                            groups("leftupleg", "leftupperleg", "leftthigh", "thighl", "upperlegl"),
                            groups("leftleg", "leftlowerleg", "leftknee", "shinl", "calfl", "lowerlegl"),
                            null,
                            groups("leftfoot", "leftankle", "footl", "anklel"),
                            null, null),
                    new IKUseCaseTemplate("Head look at", IKLayerDefinition.SOLVER_LOOK_AT, true,
                            null, null, null, groups("head"),
                            null, groups("neck", "head")),
                    new IKUseCaseTemplate("Upper body aim", IKLayerDefinition.SOLVER_AIM, true,
                            null, null, null, groups("rightshoulder", "rightarm", "righthand"),
                            null, groups("spine", "spine1", "spine2", "chest", "rightshoulder"))
            };
        }

        IKUseCaseMatch match(List<String> joints) {
            IKUseCaseMatch match = new IKUseCaseMatch();
            match.root = findBest(joints, rootPatterns);
            match.middle = findBest(joints, middlePatterns);
            match.secondMiddle = findBest(joints, secondMiddlePatterns);
            match.end = findBest(joints, endPatterns);
            match.start = findBest(joints, startPatterns);
            if (affectedPatterns != null) {
                for (String[] group : affectedPatterns) {
                    String found = findBest(joints, new String[][]{group});
                    if (found != null && match.affected.stream().noneMatch(existing -> existing.equalsIgnoreCase(found))) {
                        match.affected.add(found);
                    }
                }
            }

            if ((IKLayerDefinition.SOLVER_TWO_BONE.equals(solverType) || IKLayerDefinition.SOLVER_FOOT.equals(solverType))) {
                require(match, "Root Joint", match.root);
                require(match, "Middle Joint", match.middle);
                require(match, "End Joint", match.end);
            } else if (IKLayerDefinition.SOLVER_THREE_BONE.equals(solverType)) {
                require(match, "Root Joint", match.root);
                require(match, "Middle Joint", match.middle);
                require(match, "Middle 2 Joint", match.secondMiddle);
                require(match, "End Joint", match.end);
            } else if (IKLayerDefinition.SOLVER_FABRIK.equals(solverType)) {
                require(match, "Start Joint", match.start);
                require(match, "End Joint", match.end);
            } else if (IKLayerDefinition.SOLVER_LOOK_AT.equals(solverType) || IKLayerDefinition.SOLVER_AIM.equals(solverType)) {
                if (match.affected.isEmpty() && (match.end == null || match.end.isBlank())) {
                    match.missing.add("Affected Joints");
                }
            }
            return match;
        }

        @Override
        public String toString() {
            return label;
        }

        private static String[][] groups(String... values) {
            String[][] groups = new String[values.length][];
            for (int i = 0; i < values.length; i++) {
                groups[i] = new String[]{values[i]};
            }
            return groups;
        }

        private static void require(IKUseCaseMatch match, String label, String value) {
            if (value == null || value.isBlank()) {
                match.missing.add(label);
            }
        }

        private static String findBest(List<String> joints, String[][] patternGroups) {
            if (joints == null || patternGroups == null) {
                return "";
            }
            for (String[] patterns : patternGroups) {
                String found = findBestInGroup(joints, patterns);
                if (found != null && !found.isBlank()) {
                    return found;
                }
            }
            return "";
        }

        private static String findBestInGroup(List<String> joints, String[] patterns) {
            String best = "";
            int bestScore = Integer.MIN_VALUE;
            for (String joint : joints) {
                String normalizedJoint = normalizeJoint(joint);
                for (String pattern : patterns) {
                    String normalizedPattern = normalizeJoint(pattern);
                    int score = score(normalizedJoint, normalizedPattern);
                    if (score > bestScore) {
                        bestScore = score;
                        best = joint;
                    }
                }
            }
            return bestScore > 0 ? best : "";
        }

        private static int score(String joint, String pattern) {
            if (joint.equals(pattern)) {
                return 1000 + pattern.length();
            }
            if (joint.endsWith(pattern)) {
                return 700 + pattern.length();
            }
            if (joint.contains(pattern)) {
                return 400 + pattern.length();
            }
            return -1;
        }

        private static String normalizeJoint(String value) {
            if (value == null) {
                return "";
            }
            String stripped = value;
            int colon = stripped.lastIndexOf(':');
            if (colon >= 0 && colon < stripped.length() - 1) {
                stripped = stripped.substring(colon + 1);
            }
            return stripped.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        }
    }

    private static final class IKUseCaseMatch {
        String root = "";
        String middle = "";
        String secondMiddle = "";
        String end = "";
        String start = "";
        final List<String> affected = new ArrayList<>();
        final List<String> missing = new ArrayList<>();
    }

    private static class SimpleDocumentListener implements DocumentListener {
        private final Runnable callback;

        SimpleDocumentListener(Runnable callback) {
            this.callback = callback;
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            callback.run();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            callback.run();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            callback.run();
        }
    }
}
