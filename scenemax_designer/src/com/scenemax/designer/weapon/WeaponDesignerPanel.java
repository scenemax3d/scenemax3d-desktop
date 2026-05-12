package com.scenemax.designer.weapon;

import com.scenemaxeng.common.types.AssetsMapping;
import com.scenemaxeng.common.weapons.WeaponAttachmentTransform;
import com.scenemaxeng.common.weapons.WeaponDefinition;
import com.scenemaxeng.common.weapons.WeaponPostureDefinition;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class WeaponDesignerPanel extends JPanel {
    private final File weaponFile;
    private WeaponDefinition document;
    private boolean dirty;
    private boolean updatingUi;
    private boolean updatingPreviewControls;
    private Runnable onDirtyCallback;
    private Runnable onSavedCallback;
    private WeaponPreviewPanel previewPanel;
    private Timer previewRefreshTimer;
    private String lastPreviewSnapshot = "";
    private List<String> latestAttachmentPoints = Collections.emptyList();

    private final DefaultListModel<String> postureListModel = new DefaultListModel<>();
    private final JList<String> postureList = new JList<>(postureListModel);
    private int selectedPostureIndex = 0;
    private boolean updatingPostureSelection;

    private final JTextField txtId = new JTextField();
    private final JTextField txtModel = new JTextField();
    private final JComboBox<String> cboPreviewPlayerModel = new JComboBox<>();

    private final JTextField txtPostureId = new JTextField();
    private final JTextField txtPostureName = new JTextField();
    private final JComboBox<String> cboAttachTo = new JComboBox<>();
    private final SliderField spnOffsetX = slider(0, -999, 999, 0.01);
    private final SliderField spnOffsetY = slider(0, -999, 999, 0.01);
    private final SliderField spnOffsetZ = slider(0, -999, 999, 0.01);
    private final SliderField spnRotX = slider(0, -360, 360, 1);
    private final SliderField spnRotY = slider(0, -360, 360, 1);
    private final SliderField spnRotZ = slider(0, -360, 360, 1);
    private final SliderField spnScaleX = slider(1, 0.01, 999, 0.01);
    private final SliderField spnScaleY = slider(1, 0.01, 999, 0.01);
    private final SliderField spnScaleZ = slider(1, 0.01, 999, 0.01);

    public WeaponDesignerPanel(File weaponFile) {
        super(new BorderLayout());
        this.weaponFile = weaponFile;
        setMinimumSize(new Dimension(0, 0));
        loadDocument();
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
            document.save(weaponFile);
            dirty = false;
            if (onSavedCallback != null) {
                onSavedCallback.run();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error saving weapon: " + ex.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
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
        if (previewRefreshTimer != null) {
            previewRefreshTimer.stop();
        }
        if (previewPanel != null) {
            previewPanel.disposePreview();
        }
    }

    private void loadDocument() {
        try {
            if (weaponFile.exists() && weaponFile.length() > 0) {
                document = WeaponDefinition.load(weaponFile);
            } else {
                document = WeaponDefinition.createTemplate(stripExtension(weaponFile.getName()), "sword");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            document = WeaponDefinition.createTemplate(stripExtension(weaponFile.getName()), "sword");
        }
        document.ensurePostureExists();
    }

    private void buildUi() {
        setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel header = new JPanel(new BorderLayout(8, 2));
        JLabel title = new JLabel("Weapon Designer");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        header.add(title, BorderLayout.WEST);
        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> saveDocument());
        header.add(saveButton, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Overview", buildOverviewTab());
        tabs.setMinimumSize(new Dimension(260, 180));

        previewPanel = new WeaponPreviewPanel(findResourcesRoot());
        previewPanel.setTransformChangedCallback(this::onPreviewTransformChanged);
        previewPanel.setAttachmentPointChangedCallback(this::onPreviewAttachmentPointChanged);
        previewPanel.setAttachmentPointsChangedCallback(this::updateAttachmentPointOptions);
        bindPreviewControls();
        reloadPreviewPlayerModels();

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tabs, previewPanel);
        split.setContinuousLayout(true);
        split.setOneTouchExpandable(true);
        split.setMinimumSize(new Dimension(0, 0));
        split.setResizeWeight(0.58);
        split.setDividerLocation(640);
        add(split, BorderLayout.CENTER);

        previewRefreshTimer = new Timer(180, e -> refreshPreviewFromUi());
        previewRefreshTimer.setRepeats(false);
        bindDirtyTracking(tabs);
    }

    private Component buildOverviewTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(form(
                row("Weapon ID", txtId),
                row("Model Asset", assetPicker(txtModel, "model")),
                row("Player Model", previewPlayerModelControl())
        ), BorderLayout.NORTH);

        JPanel postures = new JPanel(new BorderLayout(0, 6));
        postures.setBorder(BorderFactory.createTitledBorder("Postures"));
        postures.add(buildPosturesSection(), BorderLayout.CENTER);
        panel.add(postures, BorderLayout.CENTER);
        return panel;
    }

    private Component buildPosturesSection() {
        document.ensurePostureExists();
        postureList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        postureList.setVisibleRowCount(10);
        postureList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || updatingUi || updatingPostureSelection) {
                return;
            }
            int newIndex = postureList.getSelectedIndex();
            if (newIndex < 0 || newIndex == selectedPostureIndex) {
                return;
            }
            applySelectedPostureFromUi();
            selectedPostureIndex = newIndex;
            refreshSelectedPostureFields();
            refreshPreviewFromUi();
        });

        JPanel actions = new JPanel(new GridLayout(0, 1, 4, 4));
        JButton addButton = new JButton("Add");
        addButton.addActionListener(e -> addPosture());
        JButton duplicateButton = new JButton("Duplicate");
        duplicateButton.addActionListener(e -> duplicatePosture());
        JButton deleteButton = new JButton("Delete");
        deleteButton.addActionListener(e -> deletePosture());
        JButton defaultButton = new JButton("Set Default");
        defaultButton.addActionListener(e -> setSelectedPostureDefault());
        actions.add(addButton);
        actions.add(duplicateButton);
        actions.add(deleteButton);
        actions.add(defaultButton);

        JPanel left = new JPanel(new BorderLayout(6, 6));
        left.setBorder(new EmptyBorder(10, 10, 10, 4));
        left.add(new JScrollPane(postureList), BorderLayout.CENTER);
        left.add(actions, BorderLayout.SOUTH);
        left.setMinimumSize(new Dimension(120, 160));

        JPanel right = form(
                row("Posture ID", txtPostureId),
                row("Name", txtPostureName),
                row("Attach To", attachToControl()),
                row("Position Offset X", spnOffsetX),
                row("Position Offset Y", spnOffsetY),
                row("Position Offset Z", spnOffsetZ),
                row("Rotation Offset X", spnRotX),
                row("Rotation Offset Y", spnRotY),
                row("Rotation Offset Z", spnRotZ),
                row("Scale X", spnScaleX),
                row("Scale Y", spnScaleY),
                row("Scale Z", spnScaleZ)
        );

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, scroll(right));
        split.setContinuousLayout(true);
        split.setOneTouchExpandable(true);
        split.setMinimumSize(new Dimension(0, 0));
        split.setResizeWeight(0.0);
        split.setDividerLocation(220);
        refreshPostureList();
        return split;
    }

    private Component previewPlayerModelControl() {
        cboPreviewPlayerModel.setEditable(false);
        JPanel panel = new JPanel(new BorderLayout(6, 0));
        panel.add(cboPreviewPlayerModel, BorderLayout.CENTER);
        JButton refresh = new JButton("Refresh");
        refresh.setMargin(new Insets(2, 8, 2, 8));
        refresh.addActionListener(e -> reloadPreviewPlayerModels());
        panel.add(refresh, BorderLayout.EAST);
        return panel;
    }

    private Component attachToControl() {
        cboAttachTo.setEditable(true);
        JPanel panel = new JPanel(new BorderLayout(6, 0));
        panel.add(cboAttachTo, BorderLayout.CENTER);
        JButton clear = new JButton("Clear");
        clear.setMargin(new Insets(2, 8, 2, 8));
        clear.addActionListener(e -> {
            selectAttachmentPoint("");
            onAttachToChangedFromUi();
        });
        panel.add(clear, BorderLayout.EAST);
        return panel;
    }

    private void bindPreviewControls() {
        cboPreviewPlayerModel.addActionListener(e -> {
            if (updatingPreviewControls || updatingUi) {
                return;
            }
            onPreviewPlayerModelChangedFromUi();
        });
        cboAttachTo.addActionListener(e -> {
            if (updatingPreviewControls || updatingUi) {
                return;
            }
            onAttachToChangedFromUi();
        });
        Component editorComponent = cboAttachTo.getEditor().getEditorComponent();
        if (editorComponent instanceof JTextField) {
            ((JTextField) editorComponent).getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    onAttachToEditorChanged();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    onAttachToEditorChanged();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    onAttachToEditorChanged();
                }
            });
        }
    }

    private void refreshFromDocument() {
        updatingUi = true;
        txtId.setText(document.getId());
        txtModel.setText(document.getModelAssetId());
        selectPreviewPlayerModel(previewPlayerModelFromDocument());
        document.ensurePostureExists();
        selectedPostureIndex = Math.max(0, Math.min(selectedPostureIndex, document.getPostures().size() - 1));
        refreshPostureList();
        refreshSelectedPostureFields();
        updatingUi = false;
        refreshPreviewFromUi();
    }

    private void applyUiToDocument() {
        document.setId(txtId.getText().trim());
        document.setModelAssetId(txtModel.getText().trim());
        document.getDesignerMetadata().put("previewPlayerModelAssetId", selectedComboValue(cboPreviewPlayerModel));
        applySelectedPostureFromUi();
    }

    private void addPosture() {
        applySelectedPostureFromUi();
        WeaponPostureDefinition posture = WeaponPostureDefinition.fromJSON(document.getDefaultPosture().toJSON());
        int count = document.getPostures().size() + 1;
        posture.setId(uniquePostureId(count == 1 ? "default" : "posture_" + count));
        posture.setName("Posture " + count);
        document.getPostures().add(posture);
        selectedPostureIndex = document.getPostures().size() - 1;
        refreshPostureList();
        refreshSelectedPostureFields();
        markDirty();
    }

    private void duplicatePosture() {
        applySelectedPostureFromUi();
        WeaponPostureDefinition source = selectedPosture();
        WeaponPostureDefinition copy = WeaponPostureDefinition.fromJSON(source.toJSON());
        copy.setId(uniquePostureId(source.getId() + "_copy"));
        copy.setName((source.getName() == null || source.getName().trim().isEmpty()
                ? "Posture"
                : source.getName().trim()) + " Copy");
        document.getPostures().add(selectedPostureIndex + 1, copy);
        selectedPostureIndex++;
        refreshPostureList();
        refreshSelectedPostureFields();
        markDirty();
    }

    private void deletePosture() {
        if (document.getPostures().size() <= 1) {
            JOptionPane.showMessageDialog(this,
                    "A weapon must keep at least one posture.",
                    "Delete Posture",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int index = Math.max(0, Math.min(selectedPostureIndex, document.getPostures().size() - 1));
        applySelectedPostureFromUi();
        WeaponPostureDefinition removed = document.getPostures().remove(index);
        if (removed != null && removed.getId() != null
                && removed.getId().equalsIgnoreCase(document.getDefaultPostureId())) {
            document.setDefaultPostureId(document.getPostures().get(0).getId());
        }
        selectedPostureIndex = Math.max(0, Math.min(index, document.getPostures().size() - 1));
        refreshPostureList();
        refreshSelectedPostureFields();
        markDirty();
    }

    private void setSelectedPostureDefault() {
        applySelectedPostureFromUi();
        document.setDefaultPostureId(selectedPosture().getId());
        refreshPostureList();
        markDirty();
    }

    private void refreshPostureList() {
        document.ensurePostureExists();
        int safeIndex = Math.max(0, Math.min(selectedPostureIndex, document.getPostures().size() - 1));
        updatingPostureSelection = true;
        postureListModel.clear();
        for (int i = 0; i < document.getPostures().size(); i++) {
            postureListModel.addElement(postureLabel(document.getPostures().get(i)));
        }
        selectedPostureIndex = safeIndex;
        postureList.setSelectedIndex(safeIndex);
        updatingPostureSelection = false;
    }

    private String postureLabel(WeaponPostureDefinition posture) {
        String id = posture.getId() == null || posture.getId().trim().isEmpty() ? "posture" : posture.getId().trim();
        String name = posture.getName() == null || posture.getName().trim().isEmpty() ? id : posture.getName().trim();
        String suffix = id.equalsIgnoreCase(document.getDefaultPostureId()) ? " (default)" : "";
        return id + " - " + name + suffix;
    }

    private void refreshSelectedPostureFields() {
        WeaponPostureDefinition posture = selectedPosture();
        boolean wasUpdating = updatingUi;
        updatingUi = true;
        txtPostureId.setText(posture.getId());
        txtPostureName.setText(posture.getName());
        selectAttachmentPoint(posture.getAttachmentPoint());
        WeaponAttachmentTransform transform = posture.getTransform();
        spnOffsetX.setValue(transform.getOffsetX());
        spnOffsetY.setValue(transform.getOffsetY());
        spnOffsetZ.setValue(transform.getOffsetZ());
        spnRotX.setValue(transform.getRotationX());
        spnRotY.setValue(transform.getRotationY());
        spnRotZ.setValue(transform.getRotationZ());
        spnScaleX.setValue(transform.getScaleX());
        spnScaleY.setValue(transform.getScaleY());
        spnScaleZ.setValue(transform.getScaleZ());
        updatingUi = wasUpdating;
    }

    private void applySelectedPostureFromUi() {
        if (document == null) {
            return;
        }
        WeaponPostureDefinition posture = selectedPosture();
        posture.setId(txtPostureId.getText().trim());
        posture.setName(txtPostureName.getText().trim());
        posture.setAttachmentPoint(selectedComboValue(cboAttachTo));
        WeaponAttachmentTransform transform = posture.getTransform();
        transform.setOffsetX(number(spnOffsetX));
        transform.setOffsetY(number(spnOffsetY));
        transform.setOffsetZ(number(spnOffsetZ));
        transform.setRotationX(number(spnRotX));
        transform.setRotationY(number(spnRotY));
        transform.setRotationZ(number(spnRotZ));
        transform.setScaleX(number(spnScaleX));
        transform.setScaleY(number(spnScaleY));
        transform.setScaleZ(number(spnScaleZ));
        if (document.getDefaultPostureId() == null || document.getDefaultPostureId().trim().isEmpty()) {
            document.setDefaultPostureId(posture.getId());
        }
        refreshPostureList();
    }

    private WeaponPostureDefinition selectedPosture() {
        document.ensurePostureExists();
        selectedPostureIndex = Math.max(0, Math.min(selectedPostureIndex, document.getPostures().size() - 1));
        return document.getPostures().get(selectedPostureIndex);
    }

    private String uniquePostureId(String baseId) {
        String normalizedBase = baseId == null || baseId.trim().isEmpty() ? "posture" : baseId.trim();
        Set<String> existing = document.getPostures().stream()
                .map(WeaponPostureDefinition::getId)
                .filter(id -> id != null)
                .map(id -> id.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        String candidate = normalizedBase;
        int suffix = 2;
        while (existing.contains(candidate.toLowerCase(Locale.ROOT))) {
            candidate = normalizedBase + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private void refreshPreviewFromUi() {
        if (previewPanel == null || updatingUi) {
            return;
        }
        applyUiToDocument();
        String previewSnapshot = previewSnapshot();
        if (previewSnapshot.equals(lastPreviewSnapshot)) {
            previewPanel.setSelectedPostureIndex(selectedPostureIndex);
            return;
        }
        lastPreviewSnapshot = previewSnapshot;
        previewPanel.setSelectedPostureIndex(selectedPostureIndex);
        previewPanel.setWeaponDefinition(document);
    }

    private String previewSnapshot() {
        String model = selectedComboValue(cboPreviewPlayerModel);
        String postureIndex = String.valueOf(selectedPostureIndex);
        String weaponJson = document == null ? "" : document.toJSON().toString();
        return model + "\n" + postureIndex + "\n" + weaponJson;
    }

    private void flushPreviewRefresh() {
        if (previewRefreshTimer != null) {
            previewRefreshTimer.stop();
        }
        refreshPreviewFromUi();
    }

    private void schedulePreviewRefresh() {
        if (previewRefreshTimer != null && !updatingUi) {
            previewRefreshTimer.restart();
        }
    }

    private void onPreviewTransformChanged(WeaponAttachmentTransform transform) {
        if (transform == null || updatingUi) {
            return;
        }
        updatingUi = true;
        spnOffsetX.setValue(transform.getOffsetX());
        spnOffsetY.setValue(transform.getOffsetY());
        spnOffsetZ.setValue(transform.getOffsetZ());
        spnRotX.setValue(transform.getRotationX());
        spnRotY.setValue(transform.getRotationY());
        spnRotZ.setValue(transform.getRotationZ());
        spnScaleX.setValue(transform.getScaleX());
        spnScaleY.setValue(transform.getScaleY());
        spnScaleZ.setValue(transform.getScaleZ());
        selectedPosture().setTransform(transform);
        updatingUi = false;
        markDirtyDirect();
    }

    private void onPreviewAttachmentPointChanged(String attachmentPoint) {
        if (updatingUi) {
            return;
        }
        updatingUi = true;
        selectAttachmentPoint(attachmentPoint);
        selectedPosture().setAttachmentPoint(selectedComboValue(cboAttachTo));
        updatingUi = false;
        markDirtyDirect();
    }

    private void onAttachToEditorChanged() {
        if (updatingPreviewControls || updatingUi) {
            return;
        }
        markDirty();
    }

    private void onAttachToChangedFromUi() {
        selectedPosture().setAttachmentPoint(selectedComboValue(cboAttachTo));
        if (previewPanel != null) {
            previewPanel.setAttachmentPoint(selectedComboValue(cboAttachTo));
        }
        markDirty();
    }

    private void onPreviewPlayerModelChangedFromUi() {
        String modelId = selectedComboValue(cboPreviewPlayerModel);
        if (previewPanel != null) {
            previewPanel.setHolderModelId(modelId);
        }
        if (document != null) {
            document.getDesignerMetadata().put("previewPlayerModelAssetId", modelId);
        }
    }

    private void reloadPreviewPlayerModels() {
        List<String> models = listAssetReferences("model");
        String previous = selectedComboValue(cboPreviewPlayerModel);
        String desired = previous.isEmpty() ? previewPlayerModelFromDocument() : previous;
        updatingPreviewControls = true;
        cboPreviewPlayerModel.removeAllItems();
        for (String model : models) {
            cboPreviewPlayerModel.addItem(model);
        }
        if (!desired.isEmpty() && models.contains(desired)) {
            cboPreviewPlayerModel.setSelectedItem(desired);
        } else if (!models.isEmpty()) {
            cboPreviewPlayerModel.setSelectedIndex(0);
        }
        updatingPreviewControls = false;
        onPreviewPlayerModelChangedFromUi();
    }

    private void updateAttachmentPointOptions(List<String> points) {
        latestAttachmentPoints = points == null ? Collections.emptyList() : new ArrayList<>(points);
        String selected = selectedComboValue(cboAttachTo);
        if (selected.isEmpty() && document != null) {
            selected = selectedPosture().getAttachmentPoint();
        }

        updatingPreviewControls = true;
        cboAttachTo.removeAllItems();
        cboAttachTo.addItem("");
        boolean hasSelected = selected == null || selected.trim().isEmpty();
        for (String point : latestAttachmentPoints) {
            if (point == null || point.trim().isEmpty()) {
                continue;
            }
            cboAttachTo.addItem(point);
            if (point.equals(selected)) {
                hasSelected = true;
            }
        }
        if (!hasSelected) {
            cboAttachTo.addItem(selected);
        }
        cboAttachTo.setSelectedItem(selected == null ? "" : selected.trim());
        updatingPreviewControls = false;
    }

    private void selectPreviewPlayerModel(String modelId) {
        String normalized = modelId == null ? "" : modelId.trim();
        updatingPreviewControls = true;
        if (!normalized.isEmpty()) {
            boolean exists = false;
            for (int i = 0; i < cboPreviewPlayerModel.getItemCount(); i++) {
                if (normalized.equals(cboPreviewPlayerModel.getItemAt(i))) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                cboPreviewPlayerModel.addItem(normalized);
            }
            cboPreviewPlayerModel.setSelectedItem(normalized);
        } else if (cboPreviewPlayerModel.getItemCount() > 0) {
            cboPreviewPlayerModel.setSelectedIndex(0);
        }
        updatingPreviewControls = false;
        onPreviewPlayerModelChangedFromUi();
    }

    private void selectAttachmentPoint(String attachmentPoint) {
        String normalized = attachmentPoint == null ? "" : attachmentPoint.trim();
        updatingPreviewControls = true;
        if (!normalized.isEmpty()) {
            boolean exists = false;
            for (int i = 0; i < cboAttachTo.getItemCount(); i++) {
                if (normalized.equals(cboAttachTo.getItemAt(i))) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                cboAttachTo.addItem(normalized);
            }
        }
        cboAttachTo.setSelectedItem(normalized);
        updatingPreviewControls = false;
    }

    private String previewPlayerModelFromDocument() {
        if (document == null || document.getDesignerMetadata() == null) {
            return "";
        }
        JSONObject metadata = document.getDesignerMetadata();
        return metadata.optString("previewPlayerModelAssetId", "").trim();
    }

    private String selectedComboValue(JComboBox<String> combo) {
        Object selected = combo.getEditor() != null && combo.isEditable()
                ? combo.getEditor().getItem()
                : combo.getSelectedItem();
        return selected == null ? "" : String.valueOf(selected).trim();
    }

    private File findResourcesRoot() {
        File current = weaponFile.getAbsoluteFile().getParentFile();
        while (current != null) {
            if ("resources".equalsIgnoreCase(current.getName())) {
                return current;
            }
            File resources = new File(current, "resources");
            if (resources.isDirectory()) {
                return resources;
            }
            current = current.getParentFile();
        }
        return null;
    }

    private void markDirty() {
        if (updatingUi || updatingPreviewControls) {
            return;
        }
        markDirtyDirect();
    }

    private void markDirtyDirect() {
        dirty = true;
        if (onDirtyCallback != null) {
            onDirtyCallback.run();
        }
        schedulePreviewRefresh();
    }

    private void bindDirtyTracking(Container root) {
        for (Component c : root.getComponents()) {
            if (c instanceof JTextField) {
                ((JTextField) c).getDocument().addDocumentListener(dirtyListener());
            } else if (c instanceof JTextArea) {
                ((JTextArea) c).getDocument().addDocumentListener(dirtyListener());
            } else if (c instanceof JComboBox) {
                ((JComboBox<?>) c).addActionListener(e -> markDirty());
            } else if (c instanceof JSpinner) {
                ((JSpinner) c).addChangeListener(e -> markDirty());
            }
            if (c instanceof Container) {
                bindDirtyTracking((Container) c);
            }
        }
    }

    private DocumentListener dirtyListener() {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                markDirty();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                markDirty();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                markDirty();
            }
        };
    }

    private JPanel assetPicker(JTextField field, String kind) {
        JPanel panel = new JPanel(new BorderLayout(6, 0));
        panel.add(field, BorderLayout.CENTER);
        JButton button = new JButton("...");
        button.setToolTipText("Select " + kind + " asset");
        button.setMargin(new Insets(2, 8, 2, 8));
        button.addActionListener(e -> selectAssetReference(field, kind));
        panel.add(button, BorderLayout.EAST);
        return panel;
    }

    private void selectAssetReference(JTextField field, String kind) {
        List<String> values = listAssetReferences(kind);
        if (values.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No " + kind + " assets were found in this project's resources.",
                    "Select Asset",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Object selected = JOptionPane.showInputDialog(this,
                "Select " + kind + " asset:",
                "Select Asset",
                JOptionPane.PLAIN_MESSAGE,
                null,
                values.toArray(new String[0]),
                field.getText().trim().isEmpty() ? values.get(0) : field.getText().trim());
        if (selected != null) {
            field.setText(String.valueOf(selected));
            markDirty();
        }
    }

    private List<String> listAssetReferences(String kind) {
        File resourcesRoot = findResourcesRoot();
        if (resourcesRoot == null || !resourcesRoot.isDirectory()) {
            return Collections.emptyList();
        }
        AssetsMapping assets = new AssetsMapping(resourcesRoot.getAbsolutePath());
        List<String> values = new ArrayList<>();
        if ("model".equals(kind)) {
            assets.get3DModelsIndex().values().forEach(resource -> values.add(resource.name));
        }
        return values.stream()
                .filter(value -> value != null && !value.trim().isEmpty())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    private static JPanel form(Object... rows) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 4, 4, 8);
        for (Object row : rows) {
            Object[] parts = (Object[]) row;
            JLabel label = new JLabel(String.valueOf(parts[0]));
            panel.add(label, gbc);
            gbc.gridx = 1;
            gbc.weightx = 1;
            panel.add((Component) parts[1], gbc);
            gbc.gridx = 0;
            gbc.weightx = 0;
            gbc.gridy++;
        }
        gbc.weighty = 1;
        panel.add(Box.createVerticalGlue(), gbc);
        return panel;
    }

    private static Object[] row(String label, Component component) {
        return new Object[]{label, component};
    }

    private static JScrollPane scroll(Component component) {
        return new JScrollPane(component);
    }

    private static SliderField slider(double value, double min, double max, double step) {
        return new SliderField(value, min, max, step);
    }

    private static double number(SliderField field) {
        Object value = field.getValue();
        return value instanceof Number ? ((Number) value).doubleValue() : 0;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static class SliderField extends JPanel {
        private static final int MAX_SLIDER_STEPS = 10000;

        private final double min;
        private final double max;
        private final int sliderSteps;
        private final JSlider slider;
        private final JSpinner spinner;
        private boolean syncing;

        SliderField(double value, double min, double max, double step) {
            super(new BorderLayout(8, 0));
            this.min = min;
            this.max = max;
            int preciseSteps = (int) Math.max(1, Math.round((max - min) / step));
            this.sliderSteps = Math.min(MAX_SLIDER_STEPS, preciseSteps);
            this.slider = new JSlider(0, sliderSteps, toSlider(value));
            this.spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
            spinner.setEditor(new JSpinner.NumberEditor(spinner, step >= 1 ? "0" : "0.###"));
            spinner.setPreferredSize(new Dimension(86, spinner.getPreferredSize().height));

            slider.addChangeListener(e -> {
                if (syncing) {
                    return;
                }
                syncing = true;
                spinner.setValue(toValue(slider.getValue()));
                syncing = false;
            });
            spinner.addChangeListener(e -> {
                if (syncing) {
                    return;
                }
                syncing = true;
                slider.setValue(toSlider(getDoubleValue()));
                syncing = false;
            });

            add(slider, BorderLayout.CENTER);
            add(spinner, BorderLayout.EAST);
        }

        Object getValue() {
            return spinner.getValue();
        }

        double getDoubleValue() {
            Object value = spinner.getValue();
            return value instanceof Number ? ((Number) value).doubleValue() : min;
        }

        void setValue(double value) {
            double clamped = Math.max(min, Math.min(max, value));
            spinner.setValue(clamped);
            slider.setValue(toSlider(clamped));
        }

        void addChangeListener(ChangeListener listener) {
            spinner.addChangeListener(listener);
        }

        private int toSlider(double value) {
            if (max <= min) {
                return 0;
            }
            double ratio = (value - min) / (max - min);
            return (int) Math.round(Math.max(0, Math.min(1, ratio)) * sliderSteps);
        }

        private double toValue(int sliderValue) {
            if (sliderSteps <= 0) {
                return min;
            }
            double ratio = sliderValue / (double) sliderSteps;
            return min + (max - min) * ratio;
        }
    }
}
