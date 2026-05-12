package com.scenemax.designer.weapon;

import com.scenemaxeng.common.weapons.AttackProfile;
import com.scenemaxeng.common.weapons.ProjectileDefinition;
import com.scenemaxeng.common.weapons.WeaponAttachmentTransform;
import com.scenemaxeng.common.weapons.WeaponDefinition;
import com.scenemaxeng.common.weapons.WeaponValidationIssue;
import com.scenemaxeng.common.weapons.WeaponValidationResult;
import com.scenemaxeng.common.types.AssetsMapping;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class WeaponDesignerPanel extends JPanel {
    private final File weaponFile;
    private WeaponDefinition document;
    private boolean dirty;
    private boolean updatingUi;
    private Runnable onDirtyCallback;
    private Runnable onSavedCallback;
    private WeaponPreviewPanel previewPanel;
    private Timer previewRefreshTimer;
    private final DefaultListModel<String> attackListModel = new DefaultListModel<>();
    private final JList<String> attackList = new JList<>(attackListModel);
    private int selectedAttackIndex = 0;
    private boolean updatingAttackSelection;

    private final JTextField txtId = new JTextField();
    private final JTextField txtName = new JTextField();
    private final JTextArea txtDescription = new JTextArea(4, 20);
    private final JComboBox<String> cboCategory = new JComboBox<>(new String[]{"melee", "ranged", "throwable", "magic", "shield"});
    private final JComboBox<String> cboHandMode = new JComboBox<>(new String[]{"oneHanded", "twoHanded", "dualWield"});
    private final JTextField txtSlots = new JTextField();
    private final JTextField txtTags = new JTextField();
    private final JTextField txtModel = new JTextField();
    private final JTextField txtAttachment = new JTextField();
    private final JSpinner spnOffsetX = spinner(0, -999, 999, 0.01);
    private final JSpinner spnOffsetY = spinner(0, -999, 999, 0.01);
    private final JSpinner spnOffsetZ = spinner(0, -999, 999, 0.01);
    private final JSpinner spnRotX = spinner(0, -360, 360, 1);
    private final JSpinner spnRotY = spinner(0, -360, 360, 1);
    private final JSpinner spnRotZ = spinner(0, -360, 360, 1);
    private final JSpinner spnScaleX = spinner(1, 0.01, 999, 0.01);
    private final JSpinner spnScaleY = spinner(1, 0.01, 999, 0.01);
    private final JSpinner spnScaleZ = spinner(1, 0.01, 999, 0.01);

    private final JTextField txtAttackId = new JTextField();
    private final JTextField txtAttackName = new JTextField();
    private final JComboBox<String> cboInputAction = new JComboBox<>(new String[]{"primary", "secondary", "reload", "block", "special"});
    private final JComboBox<String> cboAttackType = new JComboBox<>(new String[]{"meleeHitbox", "projectile", "hitscan", "area"});
    private final JSpinner spnCooldown = spinner(0.6, 0, 999, 0.05);
    private final JSpinner spnStartup = spinner(0.15, 0, 999, 0.05);
    private final JSpinner spnActive = spinner(0.2, 0, 999, 0.05);
    private final JSpinner spnRecovery = spinner(0.25, 0, 999, 0.05);
    private final JSpinner spnRange = spinner(1.5, 0, 9999, 0.1);
    private final JSpinner spnDamageMultiplier = spinner(1, 0.01, 999, 0.1);
    private final JSpinner spnAmmoCost = spinner(0, 0, 999, 1);
    private final JTextField txtProjectileId = new JTextField();
    private final JSpinner spnProjectileLaunchOffsetX = spinner(0, -999, 999, 0.01);
    private final JSpinner spnProjectileLaunchOffsetY = spinner(0, -999, 999, 0.01);
    private final JSpinner spnProjectileLaunchOffsetZ = spinner(0.35, -999, 999, 0.01);
    private final JTextField txtAttackAnim = new JTextField();
    private final JTextField txtAttackFxSound = new JTextField();
    private final JTextField txtAttackImpactSound = new JTextField();
    private final JTextField txtAttackMuzzleFx = new JTextField();
    private final JTextField txtAttackTrailFx = new JTextField();
    private final JTextField txtAttackImpactFx = new JTextField();
    private final JTextField txtAttackHandler = new JTextField();

    private final JTextField txtProjectileName = new JTextField();
    private final JLabel lblProjectileBinding = new JLabel("No projectile bound to selected attack.");
    private final JTextField txtProjectileModel = new JTextField();
    private final JSpinner spnProjectileScaleX = spinner(1, 0.01, 999, 0.01);
    private final JSpinner spnProjectileScaleY = spinner(1, 0.01, 999, 0.01);
    private final JSpinner spnProjectileScaleZ = spinner(1, 0.01, 999, 0.01);
    private final JSpinner spnProjectileSpeed = spinner(30, 0.01, 9999, 1);
    private final JSpinner spnProjectileGravity = spinner(0, -99, 99, 0.1);
    private final JSpinner spnProjectileLifetime = spinner(5, 0.01, 999, 0.1);
    private final JSpinner spnProjectileRadius = spinner(0.2, 0.01, 999, 0.01);
    private final JSpinner spnProjectilePierce = spinner(0, 0, 999, 1);
    private final JCheckBox chkProjectileExplodes = new JCheckBox("Explodes on impact");
    private final JSpinner spnExplosionRadius = spinner(0, 0, 999, 0.1);
    private final JTextField txtTrailEffect = new JTextField();
    private final JTextField txtImpactEffectProjectile = new JTextField();

    private final JSpinner spnBaseDamage = spinner(10, 0, 99999, 1);
    private final JComboBox<String> cboDamageType = new JComboBox<>(new String[]{"physical", "fire", "ice", "electric", "poison", "magic"});
    private final JSpinner spnCritChance = spinner(0, 0, 1, 0.01);
    private final JSpinner spnCritMultiplier = spinner(2, 1, 99, 0.1);
    private final JSpinner spnKnockback = spinner(0, 0, 999, 0.1);

    private final JCheckBox chkUsesAmmo = new JCheckBox("Uses ammo");
    private final JSpinner spnMagazine = spinner(0, 0, 9999, 1);
    private final JSpinner spnDefaultMagazine = spinner(0, 0, 9999, 1);
    private final JSpinner spnReserve = spinner(0, 0, 99999, 1);
    private final JSpinner spnReloadTime = spinner(1, 0, 999, 0.1);

    private final JTextArea validationText = new JTextArea();
    private final JTextArea scriptText = new JTextArea();

    public WeaponDesignerPanel(File weaponFile) {
        super(new BorderLayout());
        this.weaponFile = weaponFile;
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
            refreshValidation();
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
        refreshValidation();
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
        tabs.addTab("Overview", scroll(form(
                row("Weapon ID", txtId),
                row("Name", txtName),
                row("Description", new JScrollPane(txtDescription)),
                row("Category", cboCategory),
                row("Hand Mode", cboHandMode),
                row("Allowed Slots", txtSlots),
                row("Tags", txtTags),
                row("Model Asset", assetPicker(txtModel, "model")),
                row("Attachment Point", txtAttachment),
                row("Position Offset X", spnOffsetX),
                row("Position Offset Y", spnOffsetY),
                row("Position Offset Z", spnOffsetZ),
                row("Rotation Offset X", spnRotX),
                row("Rotation Offset Y", spnRotY),
                row("Rotation Offset Z", spnRotZ),
                row("Scale X", spnScaleX),
                row("Scale Y", spnScaleY),
                row("Scale Z", spnScaleZ)
        )));
        tabs.addTab("Attacks", buildAttacksTab());
        tabs.addTab("Projectile", scroll(buildProjectileTab()));
        tabs.addTab("Damage", scroll(form(
                row("Base Damage", spnBaseDamage),
                row("Damage Type", cboDamageType),
                row("Critical Chance", spnCritChance),
                row("Critical Multiplier", spnCritMultiplier),
                row("Knockback", spnKnockback)
        )));
        tabs.addTab("Ammo & Reload", scroll(form(
                row("", chkUsesAmmo),
                row("Magazine Size", spnMagazine),
                row("Default Magazine", spnDefaultMagazine),
                row("Reserve Ammo", spnReserve),
                row("Reload Time", spnReloadTime)
        )));
        scriptText.setEditable(false);
        scriptText.setLineWrap(true);
        scriptText.setWrapStyleWord(true);
        scriptText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        tabs.addTab("Script API", new JScrollPane(scriptText));

        validationText.setEditable(false);
        validationText.setLineWrap(true);
        validationText.setWrapStyleWord(true);
        tabs.addTab("Validation", new JScrollPane(validationText));

        previewPanel = new WeaponPreviewPanel(findResourcesRoot());
        previewPanel.setTransformChangedCallback(this::onPreviewTransformChanged);
        previewPanel.setAttachmentPointChangedCallback(this::onPreviewAttachmentPointChanged);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tabs, previewPanel);
        split.setResizeWeight(0.58);
        split.setDividerLocation(640);
        add(split, BorderLayout.CENTER);

        previewRefreshTimer = new Timer(180, e -> refreshPreviewFromUi());
        previewRefreshTimer.setRepeats(false);
        txtProjectileId.getDocument().addDocumentListener(projectileBindingListener());
        bindDirtyTracking(tabs);
    }

    private void refreshFromDocument() {
        updatingUi = true;
        txtId.setText(document.getId());
        txtName.setText(document.getName());
        txtDescription.setText(document.getDescription());
        cboCategory.setSelectedItem(document.getCategory());
        cboHandMode.setSelectedItem(document.getHandMode());
        txtSlots.setText(String.join(", ", document.getAllowedEquipmentSlots()));
        txtTags.setText(String.join(", ", document.getWeaponTags()));
        txtModel.setText(document.getModelAssetId());
        txtAttachment.setText(document.getDefaultAttachmentPoint());
        spnOffsetX.setValue(document.getAttachmentTransform().getOffsetX());
        spnOffsetY.setValue(document.getAttachmentTransform().getOffsetY());
        spnOffsetZ.setValue(document.getAttachmentTransform().getOffsetZ());
        spnRotX.setValue(document.getAttachmentTransform().getRotationX());
        spnRotY.setValue(document.getAttachmentTransform().getRotationY());
        spnRotZ.setValue(document.getAttachmentTransform().getRotationZ());
        spnScaleX.setValue(document.getAttachmentTransform().getScaleX());
        spnScaleY.setValue(document.getAttachmentTransform().getScaleY());
        spnScaleZ.setValue(document.getAttachmentTransform().getScaleZ());

        ensureAttackExists();
        selectedAttackIndex = Math.max(0, Math.min(selectedAttackIndex, document.getAttackProfiles().size() - 1));
        refreshAttackList();
        refreshSelectedAttackFields();

        spnBaseDamage.setValue(document.getDamageProfile().getBaseDamage());
        cboDamageType.setSelectedItem(document.getDamageProfile().getDamageType());
        spnCritChance.setValue(document.getDamageProfile().getCriticalChance());
        spnCritMultiplier.setValue(document.getDamageProfile().getCriticalMultiplier());
        spnKnockback.setValue(document.getDamageProfile().getKnockback());

        chkUsesAmmo.setSelected(document.getAmmoDefinition().isUsesAmmo());
        spnMagazine.setValue((double) document.getAmmoDefinition().getMagazineSize());
        spnDefaultMagazine.setValue((double) document.getAmmoDefinition().getDefaultMagazineAmmo());
        spnReserve.setValue((double) document.getAmmoDefinition().getDefaultReserveAmmo());
        spnReloadTime.setValue(document.getReloadSettings().getReloadTime());

        updatingUi = false;
        refreshScriptExamples();
        refreshValidation();
        refreshPreviewFromUi();
    }

    private void applyUiToDocument() {
        document.setId(txtId.getText().trim());
        document.setName(txtName.getText().trim());
        document.setDescription(txtDescription.getText());
        document.setCategory(String.valueOf(cboCategory.getSelectedItem()));
        document.setHandMode(String.valueOf(cboHandMode.getSelectedItem()));
        document.setModelAssetId(txtModel.getText().trim());
        document.setDefaultAttachmentPoint(txtAttachment.getText().trim());
        document.getAttachmentTransform().setOffsetX(number(spnOffsetX));
        document.getAttachmentTransform().setOffsetY(number(spnOffsetY));
        document.getAttachmentTransform().setOffsetZ(number(spnOffsetZ));
        document.getAttachmentTransform().setRotationX(number(spnRotX));
        document.getAttachmentTransform().setRotationY(number(spnRotY));
        document.getAttachmentTransform().setRotationZ(number(spnRotZ));
        document.getAttachmentTransform().setScaleX(number(spnScaleX));
        document.getAttachmentTransform().setScaleY(number(spnScaleY));
        document.getAttachmentTransform().setScaleZ(number(spnScaleZ));
        document.getAllowedEquipmentSlots().clear();
        document.getAllowedEquipmentSlots().addAll(splitCsv(txtSlots.getText()));
        document.getWeaponTags().clear();
        document.getWeaponTags().addAll(splitCsv(txtTags.getText()));

        applySelectedAttackFromUi();

        applySelectedProjectileFromUi();

        document.getDamageProfile().setBaseDamage(number(spnBaseDamage));
        document.getDamageProfile().setDamageType(String.valueOf(cboDamageType.getSelectedItem()));
        document.getDamageProfile().setCriticalChance(number(spnCritChance));
        document.getDamageProfile().setCriticalMultiplier(number(spnCritMultiplier));
        document.getDamageProfile().setKnockback(number(spnKnockback));

        document.getAmmoDefinition().setUsesAmmo(chkUsesAmmo.isSelected());
        document.getAmmoDefinition().setMagazineSize((int) Math.round(number(spnMagazine)));
        document.getAmmoDefinition().setDefaultMagazineAmmo((int) Math.round(number(spnDefaultMagazine)));
        document.getAmmoDefinition().setDefaultReserveAmmo((int) Math.round(number(spnReserve)));
        document.getReloadSettings().setReloadTime(number(spnReloadTime));

        refreshScriptExamples();
    }

    private void refreshValidation() {
        applyUiIfNotUpdating();
        WeaponValidationResult result = document.validate();
        appendProjectValidation(result);
        StringBuilder text = new StringBuilder();
        text.append(result.isValid() ? "Weapon is usable." : "Weapon has blocking issues.").append("\n\n");
        if (result.getIssues().isEmpty()) {
            text.append("No validation issues.");
        } else {
            for (WeaponValidationIssue issue : result.getIssues()) {
                text.append(issue.getSeverity().name()).append(" - ")
                        .append(issue.getField()).append(": ")
                        .append(issue.getMessage()).append("\n");
            }
        }
        validationText.setText(text.toString());
        validationText.setCaretPosition(0);
    }

    private void refreshScriptExamples() {
        String weaponName = txtName.getText().trim().isEmpty() ? "New Weapon" : txtName.getText().trim();
        String handlerName = txtAttackHandler.getText().trim();
        String handlerBlock = handlerName.isEmpty()
                ? ""
                : "\nAttack handler stub:\n" +
                        "    " + handlerName + " (attack) = {}\n" +
                        "    The handler runs before ammo is consumed and receives a mutable attack instance.\n";
        scriptText.setText(
                "Suggested setup:\n" +
                        "    player.weapon = \"" + weaponName + "\"\n\n" +
                        "Suggested primary attack:\n" +
                        "    player.weapon.use primary\n\n" +
                        "Suggested damage access:\n" +
                        "    player.weapon.damage.number\n" +
                        handlerBlock
        );
        scriptText.setCaretPosition(0);
    }

    private void applyUiIfNotUpdating() {
        if (!updatingUi) {
            applyUiToDocument();
        }
    }

    private Component buildProjectileTab() {
        JButton createButton = new JButton("Create / Bind Projectile");
        createButton.addActionListener(e -> createOrSyncSelectedProjectileDefinition());
        lblProjectileBinding.setFont(lblProjectileBinding.getFont().deriveFont(Font.BOLD));
        return form(
                row("Selected Attack", lblProjectileBinding),
                row("", createButton),
                row("Projectile Name", txtProjectileName),
                row("Projectile Model", assetPicker(txtProjectileModel, "model")),
                row("Scale X", spnProjectileScaleX),
                row("Scale Y", spnProjectileScaleY),
                row("Scale Z", spnProjectileScaleZ),
                row("Speed", spnProjectileSpeed),
                row("Gravity Scale", spnProjectileGravity),
                row("Lifetime", spnProjectileLifetime),
                row("Collision Radius", spnProjectileRadius),
                row("Pierce Count", spnProjectilePierce),
                row("", chkProjectileExplodes),
                row("Explosion Radius", spnExplosionRadius),
                row("Trail Effect", assetPicker(txtTrailEffect, "effect")),
                row("Impact Effect", assetPicker(txtImpactEffectProjectile, "effect"))
        );
    }

    private Component buildAttacksTab() {
        ensureAttackExists();
        attackList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        attackList.setVisibleRowCount(10);
        attackList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || updatingUi || updatingAttackSelection) {
                return;
            }
            int newIndex = attackList.getSelectedIndex();
            if (newIndex < 0 || newIndex == selectedAttackIndex) {
                return;
            }
            applySelectedAttackFromUi();
            applySelectedProjectileFromUi();
            selectedAttackIndex = newIndex;
            refreshSelectedAttackFields();
            refreshAttackList();
            schedulePreviewRefresh();
        });

        JPanel attackActions = new JPanel(new GridLayout(0, 1, 4, 4));
        JButton addButton = new JButton("Add");
        addButton.addActionListener(e -> addAttackProfile());
        JButton duplicateButton = new JButton("Duplicate");
        duplicateButton.addActionListener(e -> duplicateAttackProfile());
        JButton deleteButton = new JButton("Delete");
        deleteButton.addActionListener(e -> deleteAttackProfile());
        attackActions.add(addButton);
        attackActions.add(duplicateButton);
        attackActions.add(deleteButton);

        JPanel left = new JPanel(new BorderLayout(6, 6));
        left.setBorder(new EmptyBorder(10, 10, 10, 4));
        left.add(new JScrollPane(attackList), BorderLayout.CENTER);
        left.add(attackActions, BorderLayout.SOUTH);
        left.setMinimumSize(new Dimension(180, 220));

        JPanel right = form(
                row("Attack ID", txtAttackId),
                row("Attack Name", txtAttackName),
                row("Input Action", cboInputAction),
                row("Attack Type", cboAttackType),
                row("Cooldown", spnCooldown),
                row("Startup Time", spnStartup),
                row("Active Time", spnActive),
                row("Recovery Time", spnRecovery),
                row("Range", spnRange),
                row("Damage Multiplier", spnDamageMultiplier),
                row("Ammo Cost", spnAmmoCost),
                row("Projectile ID", txtProjectileId),
                row("Launch Offset X", spnProjectileLaunchOffsetX),
                row("Launch Offset Y", spnProjectileLaunchOffsetY),
                row("Launch Offset Z", spnProjectileLaunchOffsetZ),
                row("Animation", assetPicker(txtAttackAnim, "animation")),
                row("Attack Sound", assetPicker(txtAttackFxSound, "audio")),
                row("Impact Sound", assetPicker(txtAttackImpactSound, "audio")),
                row("Muzzle Flash Effect", assetPicker(txtAttackMuzzleFx, "effect")),
                row("Melee Trail Effect", assetPicker(txtAttackTrailFx, "effect")),
                row("Impact Effect", assetPicker(txtAttackImpactFx, "effect")),
                row("Attack Handler", txtAttackHandler)
        );

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, scroll(right));
        split.setResizeWeight(0.0);
        split.setDividerLocation(220);
        refreshAttackList();
        return split;
    }

    private void addAttackProfile() {
        applySelectedAttackFromUi();
        applySelectedProjectileFromUi();
        AttackProfile attack = new AttackProfile();
        int count = document.getAttackProfiles().size() + 1;
        attack.setId(uniqueAttackId(defaultAttackId(count)));
        attack.setName("Attack " + count);
        attack.setInputAction(defaultInputAction(count));
        document.getAttackProfiles().add(attack);
        selectedAttackIndex = document.getAttackProfiles().size() - 1;
        refreshAttackList();
        refreshSelectedAttackFields();
        markDirty();
        refreshValidation();
    }

    private void duplicateAttackProfile() {
        applySelectedAttackFromUi();
        applySelectedProjectileFromUi();
        AttackProfile source = selectedAttack();
        AttackProfile copy = AttackProfile.fromJSON(source.toJSON());
        copy.setId(uniqueAttackId(source.getId() + "_copy"));
        copy.setName((source.getName() == null || source.getName().trim().isEmpty() ? "Attack" : source.getName().trim()) + " Copy");
        document.getAttackProfiles().add(selectedAttackIndex + 1, copy);
        selectedAttackIndex++;
        refreshAttackList();
        refreshSelectedAttackFields();
        markDirty();
        refreshValidation();
    }

    private void deleteAttackProfile() {
        if (document.getAttackProfiles().size() <= 1) {
            JOptionPane.showMessageDialog(this,
                    "A weapon must keep at least one attack profile.",
                    "Delete Attack",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int index = Math.max(0, Math.min(selectedAttackIndex, document.getAttackProfiles().size() - 1));
        applySelectedAttackFromUi();
        applySelectedProjectileFromUi();
        document.getAttackProfiles().remove(index);
        selectedAttackIndex = Math.max(0, Math.min(index, document.getAttackProfiles().size() - 1));
        refreshAttackList();
        refreshSelectedAttackFields();
        markDirty();
        refreshValidation();
    }

    private void refreshAttackList() {
        ensureAttackExists();
        int safeIndex = Math.max(0, Math.min(selectedAttackIndex, document.getAttackProfiles().size() - 1));
        updatingAttackSelection = true;
        attackListModel.clear();
        for (int i = 0; i < document.getAttackProfiles().size(); i++) {
            attackListModel.addElement(attackLabel(document.getAttackProfiles().get(i), i));
        }
        selectedAttackIndex = safeIndex;
        attackList.setSelectedIndex(safeIndex);
        updatingAttackSelection = false;
    }

    private String attackLabel(AttackProfile attack, int index) {
        String input = attack.getInputAction() == null || attack.getInputAction().trim().isEmpty()
                ? "attack"
                : attack.getInputAction().trim();
        String name = attack.getName() == null || attack.getName().trim().isEmpty()
                ? attack.getId()
                : attack.getName();
        if (name == null || name.trim().isEmpty()) {
            name = "Attack " + (index + 1);
        }
        return input + " - " + name;
    }

    private void refreshSelectedAttackFields() {
        AttackProfile attack = selectedAttack();
        boolean wasUpdating = updatingUi;
        updatingUi = true;
        txtAttackId.setText(attack.getId());
        txtAttackName.setText(attack.getName());
        cboInputAction.setSelectedItem(attack.getInputAction());
        cboAttackType.setSelectedItem(attack.getAttackType());
        spnCooldown.setValue(attack.getCooldown());
        spnStartup.setValue(attack.getStartupTime());
        spnActive.setValue(attack.getActiveTime());
        spnRecovery.setValue(attack.getRecoveryTime());
        spnRange.setValue(attack.getRange());
        spnDamageMultiplier.setValue(attack.getDamageMultiplier());
        spnAmmoCost.setValue((double) attack.getAmmoCost());
        txtProjectileId.setText(attack.getProjectileDefinitionId());
        spnProjectileLaunchOffsetX.setValue(attack.getProjectileLaunchOffsetX());
        spnProjectileLaunchOffsetY.setValue(attack.getProjectileLaunchOffsetY());
        spnProjectileLaunchOffsetZ.setValue(attack.getProjectileLaunchOffsetZ());
        txtAttackAnim.setText(attack.getAttackAnimation());
        txtAttackFxSound.setText(attack.getAttackSound());
        txtAttackImpactSound.setText(attack.getImpactSound());
        txtAttackMuzzleFx.setText(attack.getMuzzleFlashEffect());
        txtAttackTrailFx.setText(attack.getMeleeTrailEffect());
        txtAttackImpactFx.setText(attack.getImpactEffect());
        txtAttackHandler.setText(attack.getAttackHandlerProcedure());
        refreshSelectedProjectileFields();
        updatingUi = wasUpdating;
    }

    private void applySelectedAttackFromUi() {
        if (document == null) {
            return;
        }
        AttackProfile attack = selectedAttack();
        attack.setId(txtAttackId.getText().trim());
        attack.setName(txtAttackName.getText().trim());
        attack.setInputAction(String.valueOf(cboInputAction.getSelectedItem()));
        attack.setAttackType(String.valueOf(cboAttackType.getSelectedItem()));
        attack.setCooldown(number(spnCooldown));
        attack.setStartupTime(number(spnStartup));
        attack.setActiveTime(number(spnActive));
        attack.setRecoveryTime(number(spnRecovery));
        attack.setRange(number(spnRange));
        attack.setDamageMultiplier(number(spnDamageMultiplier));
        attack.setAmmoCost((int) Math.round(number(spnAmmoCost)));
        attack.setProjectileDefinitionId(txtProjectileId.getText().trim());
        attack.setProjectileLaunchOffsetX(number(spnProjectileLaunchOffsetX));
        attack.setProjectileLaunchOffsetY(number(spnProjectileLaunchOffsetY));
        attack.setProjectileLaunchOffsetZ(number(spnProjectileLaunchOffsetZ));
        attack.setAttackAnimation(txtAttackAnim.getText().trim());
        attack.setAttackSound(txtAttackFxSound.getText().trim());
        attack.setImpactSound(txtAttackImpactSound.getText().trim());
        attack.setMuzzleFlashEffect(txtAttackMuzzleFx.getText().trim());
        attack.setMeleeTrailEffect(txtAttackTrailFx.getText().trim());
        attack.setImpactEffect(txtAttackImpactFx.getText().trim());
        attack.setAttackHandlerProcedure(txtAttackHandler.getText().trim());
        refreshAttackList();
    }

    private void refreshSelectedProjectileFields() {
        ProjectileDefinition projectile = selectedProjectile(false);
        boolean wasUpdating = updatingUi;
        updatingUi = true;
        if (projectile == null) {
            clearProjectileFields();
        } else {
            lblProjectileBinding.setText("Editing projectile '" + projectile.getId() + "' for " + attackLabel(selectedAttack(), selectedAttackIndex));
            txtProjectileName.setText(projectile.getName());
            txtProjectileModel.setText(projectile.getModelAssetId());
            spnProjectileScaleX.setValue(projectile.getScaleX());
            spnProjectileScaleY.setValue(projectile.getScaleY());
            spnProjectileScaleZ.setValue(projectile.getScaleZ());
            spnProjectileSpeed.setValue(projectile.getSpeed());
            spnProjectileGravity.setValue(projectile.getGravityScale());
            spnProjectileLifetime.setValue(projectile.getLifetime());
            spnProjectileRadius.setValue(projectile.getCollisionRadius());
            spnProjectilePierce.setValue((double) projectile.getPierceCount());
            chkProjectileExplodes.setSelected(projectile.isExplodeOnImpact());
            spnExplosionRadius.setValue(projectile.getExplosionRadius());
            txtTrailEffect.setText(projectile.getTrailEffectId());
            txtImpactEffectProjectile.setText(projectile.getImpactEffectId());
        }
        updatingUi = wasUpdating;
    }

    private void clearProjectileFields() {
        lblProjectileBinding.setText("No projectile bound to selected attack.");
        txtProjectileName.setText("");
        txtProjectileModel.setText("");
        ProjectileDefinition defaults = new ProjectileDefinition();
        spnProjectileScaleX.setValue(defaults.getScaleX());
        spnProjectileScaleY.setValue(defaults.getScaleY());
        spnProjectileScaleZ.setValue(defaults.getScaleZ());
        spnProjectileSpeed.setValue(defaults.getSpeed());
        spnProjectileGravity.setValue(defaults.getGravityScale());
        spnProjectileLifetime.setValue(defaults.getLifetime());
        spnProjectileRadius.setValue(defaults.getCollisionRadius());
        spnProjectilePierce.setValue((double) defaults.getPierceCount());
        chkProjectileExplodes.setSelected(defaults.isExplodeOnImpact());
        spnExplosionRadius.setValue(defaults.getExplosionRadius());
        txtTrailEffect.setText("");
        txtImpactEffectProjectile.setText("");
    }

    private void applySelectedProjectileFromUi() {
        if (document == null || updatingUi) {
            return;
        }
        String projectileId = projectileIdForSelectedAttack(true);
        if (projectileId.isEmpty()) {
            return;
        }
        ProjectileDefinition projectile = findProjectileById(projectileId);
        if (projectile == null) {
            projectile = new ProjectileDefinition();
            projectile.setId(projectileId);
            document.getProjectileDefinitions().add(projectile);
        }
        projectile.setId(projectileId);
        projectile.setName(txtProjectileName.getText().trim().isEmpty() ? "Projectile" : txtProjectileName.getText().trim());
        projectile.setModelAssetId(txtProjectileModel.getText().trim());
        projectile.setScaleX(number(spnProjectileScaleX));
        projectile.setScaleY(number(spnProjectileScaleY));
        projectile.setScaleZ(number(spnProjectileScaleZ));
        projectile.setSpeed(number(spnProjectileSpeed));
        projectile.setGravityScale(number(spnProjectileGravity));
        projectile.setLifetime(number(spnProjectileLifetime));
        projectile.setCollisionRadius(number(spnProjectileRadius));
        projectile.setPierceCount((int) Math.round(number(spnProjectilePierce)));
        projectile.setExplodeOnImpact(chkProjectileExplodes.isSelected());
        projectile.setExplosionRadius(number(spnExplosionRadius));
        projectile.setTrailEffectId(txtTrailEffect.getText().trim());
        projectile.setImpactEffectId(txtImpactEffectProjectile.getText().trim());
    }

    private void createOrSyncSelectedProjectileDefinition() {
        applySelectedAttackFromUi();
        AttackProfile attack = selectedAttack();
        String projectileId = projectileIdForSelectedAttack(true);
        if (projectileId.isEmpty()) {
            projectileId = uniqueProjectileId(attack.getId() == null || attack.getId().trim().isEmpty()
                    ? "projectile"
                    : attack.getId().trim());
            txtProjectileId.setText(projectileId);
            attack.setProjectileDefinitionId(projectileId);
        }
        ProjectileDefinition projectile = findProjectileById(projectileId);
        if (projectile == null) {
            projectile = new ProjectileDefinition();
            projectile.setId(projectileId);
            String attackName = attack.getName() == null || attack.getName().trim().isEmpty()
                    ? "Projectile"
                    : attack.getName().trim() + " Projectile";
            projectile.setName(attackName);
            document.getProjectileDefinitions().add(projectile);
        }
        if (!"projectile".equalsIgnoreCase(attack.getAttackType()) && !"hitscan".equalsIgnoreCase(attack.getAttackType())) {
            attack.setAttackType("projectile");
            cboAttackType.setSelectedItem("projectile");
        }
        refreshSelectedProjectileFields();
        markDirty();
        refreshValidation();
    }

    private ProjectileDefinition selectedProjectile(boolean create) {
        String projectileId = projectileIdForSelectedAttack(false);
        if (projectileId.isEmpty()) {
            return null;
        }
        ProjectileDefinition projectile = findProjectileById(projectileId);
        if (projectile == null && create) {
            projectile = new ProjectileDefinition();
            projectile.setId(projectileId);
            document.getProjectileDefinitions().add(projectile);
        }
        return projectile;
    }

    private String projectileIdForSelectedAttack(boolean preferUi) {
        String projectileId = preferUi ? txtProjectileId.getText().trim() : "";
        if (projectileId.isEmpty()) {
            AttackProfile attack = selectedAttack();
            projectileId = attack.getProjectileDefinitionId() == null ? "" : attack.getProjectileDefinitionId().trim();
        }
        return projectileId;
    }

    private ProjectileDefinition findProjectileById(String projectileId) {
        if (projectileId == null || projectileId.trim().isEmpty()) {
            return null;
        }
        for (ProjectileDefinition projectile : document.getProjectileDefinitions()) {
            if (projectile.getId() != null && projectile.getId().trim().equalsIgnoreCase(projectileId.trim())) {
                return projectile;
            }
        }
        return null;
    }

    private AttackProfile selectedAttack() {
        ensureAttackExists();
        selectedAttackIndex = Math.max(0, Math.min(selectedAttackIndex, document.getAttackProfiles().size() - 1));
        return document.getAttackProfiles().get(selectedAttackIndex);
    }

    private void ensureAttackExists() {
        if (document != null && document.getAttackProfiles().isEmpty()) {
            document.getAttackProfiles().add(new AttackProfile());
        }
    }

    private String defaultAttackId(int count) {
        switch (count) {
            case 2:
                return "secondary";
            case 3:
                return "special";
            default:
                return "attack_" + count;
        }
    }

    private String defaultInputAction(int count) {
        switch (count) {
            case 1:
                return "primary";
            case 2:
                return "secondary";
            case 3:
                return "special";
            default:
                return "special";
        }
    }

    private String uniqueAttackId(String baseId) {
        String normalizedBase = baseId == null || baseId.trim().isEmpty() ? "attack" : baseId.trim();
        Set<String> existing = document.getAttackProfiles().stream()
                .map(AttackProfile::getId)
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

    private String uniqueProjectileId(String baseId) {
        String normalizedBase = baseId == null || baseId.trim().isEmpty() ? "projectile" : baseId.trim();
        Set<String> existing = document.getProjectileDefinitions().stream()
                .map(ProjectileDefinition::getId)
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
        previewPanel.setSelectedAttackIndex(selectedAttackIndex);
        previewPanel.setWeaponDefinition(document);
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
        document.getAttachmentTransform().setOffsetX(transform.getOffsetX());
        document.getAttachmentTransform().setOffsetY(transform.getOffsetY());
        document.getAttachmentTransform().setOffsetZ(transform.getOffsetZ());
        document.getAttachmentTransform().setRotationX(transform.getRotationX());
        document.getAttachmentTransform().setRotationY(transform.getRotationY());
        document.getAttachmentTransform().setRotationZ(transform.getRotationZ());
        document.getAttachmentTransform().setScaleX(transform.getScaleX());
        document.getAttachmentTransform().setScaleY(transform.getScaleY());
        document.getAttachmentTransform().setScaleZ(transform.getScaleZ());
        updatingUi = false;
        dirty = true;
        if (onDirtyCallback != null) {
            onDirtyCallback.run();
        }
        refreshValidation();
    }

    private void onPreviewAttachmentPointChanged(String attachmentPoint) {
        if (updatingUi) {
            return;
        }
        updatingUi = true;
        txtAttachment.setText(attachmentPoint == null ? "" : attachmentPoint);
        document.setDefaultAttachmentPoint(txtAttachment.getText().trim());
        updatingUi = false;
        dirty = true;
        if (onDirtyCallback != null) {
            onDirtyCallback.run();
        }
        refreshValidation();
    }

    private void appendProjectValidation(WeaponValidationResult result) {
        File resourcesRoot = findResourcesRoot();
        if (resourcesRoot == null || !resourcesRoot.isDirectory()) {
            result.addWarning("project.resources", "Project resources folder could not be located for asset reference checks.");
            return;
        }

        AssetsMapping assets = new AssetsMapping(resourcesRoot.getAbsolutePath());
        warnMissingModel(result, assets, "modelAssetId", document.getModelAssetId());
        for (ProjectileDefinition projectile : document.getProjectileDefinitions()) {
            warnMissingModel(result, assets, "projectileDefinitions.modelAssetId", projectile.getModelAssetId());
            warnMissingEffect(result, resourcesRoot, "projectileDefinitions.trailEffectId", projectile.getTrailEffectId());
            warnMissingEffect(result, resourcesRoot, "projectileDefinitions.impactEffectId", projectile.getImpactEffectId());
        }

        for (AttackProfile attack : document.getAttackProfiles()) {
            String fieldPrefix = "attackProfiles[" + (attack.getId() == null ? "" : attack.getId()) + "]";
            warnMissingAnimation(result, assets, fieldPrefix + ".attackAnimation", attack.getAttackAnimation());
            warnMissingSound(result, assets, fieldPrefix + ".attackSound", attack.getAttackSound());
            warnMissingSound(result, assets, fieldPrefix + ".impactSound", attack.getImpactSound());
            warnMissingEffect(result, resourcesRoot, fieldPrefix + ".muzzleFlashEffect", attack.getMuzzleFlashEffect());
            warnMissingEffect(result, resourcesRoot, fieldPrefix + ".meleeTrailEffect", attack.getMeleeTrailEffect());
            warnMissingEffect(result, resourcesRoot, fieldPrefix + ".impactEffect", attack.getImpactEffect());
        }

        validateProjectileBindings(result);
    }

    private void validateProjectileBindings(WeaponValidationResult result) {
        Set<String> projectileIds = new LinkedHashSet<>();
        for (ProjectileDefinition projectile : document.getProjectileDefinitions()) {
            if (projectile.getId() != null && !projectile.getId().trim().isEmpty()) {
                projectileIds.add(projectile.getId().trim().toLowerCase(Locale.ROOT));
            }
        }
        for (AttackProfile attack : document.getAttackProfiles()) {
            if (!"projectile".equalsIgnoreCase(attack.getAttackType()) && !"hitscan".equalsIgnoreCase(attack.getAttackType())) {
                continue;
            }
            String projectileId = attack.getProjectileDefinitionId();
            if (projectileId == null || projectileId.trim().isEmpty()) {
                projectileId = attack.getId();
            }
            if (projectileId == null || projectileId.trim().isEmpty() || !projectileIds.contains(projectileId.trim().toLowerCase(Locale.ROOT))) {
                result.addError("attackProfiles.projectileDefinitionId", "Ranged attack '" + attack.getId() + "' must reference an existing projectile definition.");
            }
        }
    }

    private void warnMissingModel(WeaponValidationResult result, AssetsMapping assets, String field, String model) {
        if (model == null || model.trim().isEmpty()) {
            return;
        }
        if (!assets.get3DModelsIndex().containsKey(model.trim().toLowerCase(Locale.ROOT))) {
            result.addWarning(field, "Model asset '" + model + "' was not found in project resources.");
        }
    }

    private void warnMissingSound(WeaponValidationResult result, AssetsMapping assets, String field, String sound) {
        if (sound == null || sound.trim().isEmpty()) {
            return;
        }
        if (!assets.getAudioIndex().containsKey(sound.trim().toLowerCase(Locale.ROOT))) {
            result.addWarning(field, "Audio asset '" + sound + "' was not found in project resources.");
        }
    }

    private void warnMissingAnimation(WeaponValidationResult result, AssetsMapping assets, String field, String animation) {
        if (animation == null || animation.trim().isEmpty()) {
            return;
        }
        if (!assets.getAnimationsIndex().containsKey(animation.trim().toLowerCase(Locale.ROOT))) {
            result.addWarning(field, "Animation asset '" + animation + "' was not found as an external animation resource. It may still exist inside the character model.");
        }
    }

    private void warnMissingEffect(WeaponValidationResult result, File resourcesRoot, String field, String effect) {
        if (effect == null || effect.trim().isEmpty()) {
            return;
        }
        File effectFolder = new File(resourcesRoot, "effects/" + effect.trim());
        File effectDesignerFile = new File(resourcesRoot, "effects/" + effect.trim() + ".smeffect");
        File effectDesignerFile2 = new File(resourcesRoot, "effects/" + effect.trim() + ".smeffectdesign");
        if (!effectFolder.exists() && !effectDesignerFile.exists() && !effectDesignerFile2.exists()) {
            result.addWarning(field, "Effect asset '" + effect + "' was not found under project resources/effects.");
        }
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
        if (updatingUi) {
            return;
        }
        dirty = true;
        if (onDirtyCallback != null) {
            onDirtyCallback.run();
        }
        refreshScriptExamples();
        schedulePreviewRefresh();
    }

    private void bindDirtyTracking(Container root) {
        for (Component c : root.getComponents()) {
            if (c instanceof JTextField) {
                ((JTextField) c).getDocument().addDocumentListener(dirtyListener());
            } else if (c instanceof JTextArea && c != validationText && c != scriptText) {
                ((JTextArea) c).getDocument().addDocumentListener(dirtyListener());
            } else if (c instanceof JComboBox) {
                ((JComboBox<?>) c).addActionListener(e -> markDirty());
            } else if (c instanceof JCheckBox) {
                ((JCheckBox) c).addActionListener(e -> markDirty());
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

    private DocumentListener projectileBindingListener() {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refreshLater();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshLater();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshLater();
            }

            private void refreshLater() {
                if (!updatingUi) {
                    SwingUtilities.invokeLater(() -> {
                        applySelectedAttackFromUi();
                        refreshSelectedProjectileFields();
                    });
                }
            }
        };
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
            Component comp = (Component) parts[1];
            panel.add(comp, gbc);
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
            refreshValidation();
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
        } else if ("audio".equals(kind)) {
            assets.getAudioIndex().values().forEach(resource -> values.add(resource.name));
        } else if ("animation".equals(kind)) {
            assets.getAnimationsIndex().values().forEach(resource -> values.add(resource.name));
        } else if ("effect".equals(kind)) {
            values.addAll(listEffectNames(resourcesRoot));
        }
        return values.stream()
                .filter(value -> value != null && !value.trim().isEmpty())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    private List<String> listEffectNames(File resourcesRoot) {
        List<String> names = new ArrayList<>();
        File effectsRoot = new File(resourcesRoot, "effects");
        File[] files = effectsRoot.listFiles();
        if (files == null) {
            return names;
        }
        for (File file : files) {
            if (file.isDirectory() && containsEffekseerFile(file)) {
                names.add(file.getName());
            } else if (file.isFile()) {
                String lower = file.getName().toLowerCase(Locale.ROOT);
                if (lower.endsWith(".smeffectdesign") || lower.endsWith(".smeffect")) {
                    names.add(stripExtension(file.getName()));
                }
            }
        }
        return names;
    }

    private boolean containsEffekseerFile(File folder) {
        File[] files = folder.listFiles();
        if (files == null) {
            return false;
        }
        for (File file : files) {
            String lower = file.getName().toLowerCase(Locale.ROOT);
            if (file.isFile() && (lower.endsWith(".efk") || lower.endsWith(".efkefc") || lower.endsWith(".efkproj"))) {
                return true;
            }
            if (file.isDirectory() && containsEffekseerFile(file)) {
                return true;
            }
        }
        return false;
    }

    private static JScrollPane scroll(Component component) {
        return new JScrollPane(component);
    }

    private static JSpinner spinner(double value, double min, double max, double step) {
        return new JSpinner(new SpinnerNumberModel(value, min, max, step));
    }

    private static double number(JSpinner spinner) {
        Object value = spinner.getValue();
        return value instanceof Number ? ((Number) value).doubleValue() : 0;
    }

    private static java.util.List<String> splitCsv(String text) {
        return Arrays.stream((text == null ? "" : text).split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
