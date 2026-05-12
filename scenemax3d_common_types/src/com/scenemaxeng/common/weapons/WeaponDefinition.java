package com.scenemaxeng.common.weapons;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class WeaponDefinition {
    public static final String FILE_EXTENSION = ".smweapon";
    public static final String SCHEMA_VERSION = "1.0";

    private String id = "weapon_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private String name = "New Weapon";
    private String description = "";
    private String category = "melee";
    private String iconAssetId = "";
    private String modelAssetId = "";
    private String defaultAttachmentPoint = "RightHandSocket";
    private WeaponAttachmentTransform attachmentTransform = new WeaponAttachmentTransform();
    private List<String> allowedEquipmentSlots = new ArrayList<>();
    private String handMode = "oneHanded";
    private List<String> weaponTags = new ArrayList<>();
    private List<AttackProfile> attackProfiles = new ArrayList<>();
    private DamageProfile damageProfile = new DamageProfile();
    private WeaponAnimationSet animationSet = new WeaponAnimationSet();
    private WeaponEffectSet effectSet = new WeaponEffectSet();
    private List<ProjectileDefinition> projectileDefinitions = new ArrayList<>();
    private AmmoDefinition ammoDefinition = new AmmoDefinition();
    private ReloadSettings reloadSettings = new ReloadSettings();
    private WeaponRuntimeRules runtimeRules = new WeaponRuntimeRules();
    private JSONObject designerMetadata = new JSONObject();

    public WeaponDefinition() {
        allowedEquipmentSlots.add("rightHand");
        attackProfiles.add(new AttackProfile());
    }

    public static WeaponDefinition createTemplate(String displayName, String template) {
        WeaponDefinition definition = new WeaponDefinition();
        definition.setName(displayName == null || displayName.trim().isEmpty() ? "New Weapon" : displayName.trim());
        definition.setId(slugId(definition.getName()));
        String normalized = template == null ? "sword" : template.trim().toLowerCase(Locale.ROOT);
        if ("gun".equals(normalized) || "pistol".equals(normalized)) {
            definition.setCategory("ranged");
            definition.setHandMode("oneHanded");
            definition.setDefaultAttachmentPoint("RightHandSocket");
            definition.getDamageProfile().setBaseDamage(12);
            AttackProfile attack = definition.getAttackProfiles().get(0);
            attack.setId("primary_shot");
            attack.setName("Shot");
            attack.setAttackType("projectile");
            attack.setCooldown(0.25);
            attack.setStartupTime(0.03);
            attack.setActiveTime(0.05);
            attack.setRecoveryTime(0.17);
            attack.setRange(60);
            attack.setAmmoCost(1);
            attack.setProjectileDefinitionId("bullet");
            ProjectileDefinition projectile = new ProjectileDefinition();
            definition.projectileDefinitions.add(projectile);
            definition.ammoDefinition.setUsesAmmo(true);
            definition.ammoDefinition.setMagazineSize(12);
            definition.ammoDefinition.setDefaultMagazineAmmo(12);
            definition.ammoDefinition.setDefaultReserveAmmo(36);
            definition.reloadSettings.setReloadTime(1.2);
        } else if ("bow".equals(normalized)) {
            definition.setCategory("ranged");
            definition.setHandMode("twoHanded");
            definition.setDefaultAttachmentPoint("LeftHandSocket");
            definition.getAllowedEquipmentSlots().clear();
            definition.getAllowedEquipmentSlots().add("bothHands");
            definition.getDamageProfile().setBaseDamage(18);
            AttackProfile attack = definition.getAttackProfiles().get(0);
            attack.setId("primary_arrow");
            attack.setName("Arrow Shot");
            attack.setAttackType("projectile");
            attack.setCooldown(0.8);
            attack.setRange(45);
            attack.setAmmoCost(1);
            attack.setProjectileDefinitionId("arrow");
            definition.projectileDefinitions.add(new ProjectileDefinition());
            definition.ammoDefinition.setUsesAmmo(true);
            definition.ammoDefinition.setMagazineSize(1);
            definition.ammoDefinition.setDefaultMagazineAmmo(1);
            definition.ammoDefinition.setDefaultReserveAmmo(24);
            definition.reloadSettings.setReloadTime(0.7);
        } else if ("staff".equals(normalized) || "magic".equals(normalized)) {
            definition.setCategory("magic");
            definition.setHandMode("twoHanded");
            definition.getDamageProfile().setBaseDamage(22);
            definition.getDamageProfile().setDamageType("magic");
            AttackProfile attack = definition.getAttackProfiles().get(0);
            attack.setId("primary_cast");
            attack.setName("Cast");
            attack.setAttackType("projectile");
            attack.setCooldown(0.75);
            attack.setRange(35);
            attack.setProjectileDefinitionId("magic_bolt");
            definition.projectileDefinitions.add(new ProjectileDefinition());
        }
        return definition;
    }

    public JSONObject toJSON() {
        JSONArray attacks = new JSONArray();
        for (AttackProfile attack : attackProfiles) {
            attacks.put(attack.toJSON());
        }
        JSONArray projectiles = new JSONArray();
        for (ProjectileDefinition projectile : projectileDefinitions) {
            projectiles.put(projectile.toJSON());
        }
        return new JSONObject()
                .put("type", "SceneMaxWeaponDefinition")
                .put("schemaVersion", SCHEMA_VERSION)
                .put("id", id)
                .put("name", name)
                .put("description", description)
                .put("category", category)
                .put("iconAssetId", iconAssetId)
                .put("modelAssetId", modelAssetId)
                .put("defaultAttachmentPoint", defaultAttachmentPoint)
                .put("attachmentTransform", attachmentTransform.toJSON())
                .put("allowedEquipmentSlots", WeaponJsonUtil.toArray(allowedEquipmentSlots))
                .put("handMode", handMode)
                .put("weaponTags", WeaponJsonUtil.toArray(weaponTags))
                .put("attackProfiles", attacks)
                .put("damageProfile", damageProfile.toJSON())
                .put("animationSet", animationSet.toJSON())
                .put("effectSet", effectSet.toJSON())
                .put("projectileDefinitions", projectiles)
                .put("ammoDefinition", ammoDefinition.toJSON())
                .put("reloadSettings", reloadSettings.toJSON())
                .put("runtimeRules", runtimeRules.toJSON())
                .put("designerMetadata", designerMetadata == null ? new JSONObject() : designerMetadata);
    }

    public static WeaponDefinition fromJSON(JSONObject json) {
        WeaponDefinition definition = new WeaponDefinition();
        definition.attackProfiles.clear();
        definition.projectileDefinitions.clear();
        if (json == null) {
            return definition;
        }
        definition.id = json.optString("id", definition.id);
        definition.name = json.optString("name", definition.name);
        definition.description = json.optString("description", definition.description);
        definition.category = json.optString("category", definition.category);
        definition.iconAssetId = json.optString("iconAssetId", definition.iconAssetId);
        definition.modelAssetId = json.optString("modelAssetId", definition.modelAssetId);
        definition.defaultAttachmentPoint = json.optString("defaultAttachmentPoint", definition.defaultAttachmentPoint);
        definition.attachmentTransform = WeaponAttachmentTransform.fromJSON(json.optJSONObject("attachmentTransform"));
        definition.allowedEquipmentSlots = WeaponJsonUtil.stringList(json.optJSONArray("allowedEquipmentSlots"));
        definition.handMode = json.optString("handMode", definition.handMode);
        definition.weaponTags = WeaponJsonUtil.stringList(json.optJSONArray("weaponTags"));
        JSONArray attacks = json.optJSONArray("attackProfiles");
        if (attacks != null) {
            for (int i = 0; i < attacks.length(); i++) {
                definition.attackProfiles.add(AttackProfile.fromJSON(attacks.optJSONObject(i)));
            }
        }
        definition.damageProfile = DamageProfile.fromJSON(json.optJSONObject("damageProfile"));
        definition.animationSet = WeaponAnimationSet.fromJSON(json.optJSONObject("animationSet"));
        definition.effectSet = WeaponEffectSet.fromJSON(json.optJSONObject("effectSet"));
        JSONArray projectiles = json.optJSONArray("projectileDefinitions");
        if (projectiles != null) {
            for (int i = 0; i < projectiles.length(); i++) {
                definition.projectileDefinitions.add(ProjectileDefinition.fromJSON(projectiles.optJSONObject(i)));
            }
        }
        definition.ammoDefinition = AmmoDefinition.fromJSON(json.optJSONObject("ammoDefinition"));
        definition.reloadSettings = ReloadSettings.fromJSON(json.optJSONObject("reloadSettings"));
        definition.runtimeRules = WeaponRuntimeRules.fromJSON(json.optJSONObject("runtimeRules"));
        definition.designerMetadata = json.optJSONObject("designerMetadata");
        if (definition.designerMetadata == null) {
            definition.designerMetadata = new JSONObject();
        }
        return definition;
    }

    public static WeaponDefinition load(File file) throws IOException {
        return fromJSON(new JSONObject(FileUtils.readFileToString(file, StandardCharsets.UTF_8)));
    }

    public void save(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        FileUtils.writeStringToFile(file, toJSON().toString(2), StandardCharsets.UTF_8);
    }

    public static void writeTemplateFile(File file, String displayName, String template) throws IOException {
        createTemplate(displayName, template).save(file);
    }

    public WeaponValidationResult validate() {
        WeaponValidationResult result = new WeaponValidationResult();
        if (id == null || id.trim().isEmpty()) {
            result.addError("id", "Weapon id is required.");
        }
        if (name == null || name.trim().isEmpty()) {
            result.addError("name", "Weapon name is required.");
        }
        if (category == null || category.trim().isEmpty()) {
            result.addError("category", "Weapon category is required.");
        }
        if (allowedEquipmentSlots == null || allowedEquipmentSlots.isEmpty()) {
            result.addError("allowedEquipmentSlots", "At least one allowed equipment slot is required.");
        }
        if (modelAssetId == null || modelAssetId.trim().isEmpty()) {
            result.addWarning("modelAssetId", "Weapon model is not assigned. Invisible weapons are allowed, but most weapons should have a model.");
        }
        if (defaultAttachmentPoint == null || defaultAttachmentPoint.trim().isEmpty()) {
            result.addWarning("defaultAttachmentPoint", "Default attachment point is not assigned.");
        }
        if (attachmentTransform != null) {
            attachmentTransform.validate(result);
        }
        if (attackProfiles == null || attackProfiles.isEmpty()) {
            result.addError("attackProfiles", "At least one attack profile is required.");
        } else {
            for (AttackProfile attack : attackProfiles) {
                attack.validate(result, this);
            }
        }
        if (damageProfile == null) {
            result.addError("damageProfile", "Damage profile is required.");
        } else {
            damageProfile.validate(result);
        }
        for (ProjectileDefinition projectile : projectileDefinitions) {
            projectile.validate(result);
        }
        if (ammoDefinition != null) {
            ammoDefinition.validate(result);
        }
        if (reloadSettings != null) {
            reloadSettings.validate(result, ammoDefinition);
        }
        if (animationSet != null) {
            animationSet.validate(result);
        }
        return result;
    }

    public ProjectileDefinition findProjectileDefinition(String attackId) {
        if (projectileDefinitions.isEmpty()) {
            return null;
        }
        for (ProjectileDefinition projectile : projectileDefinitions) {
            if (projectile.getId().equalsIgnoreCase(attackId)) {
                return projectile;
            }
        }
        return projectileDefinitions.get(0);
    }

    private static String slugId(String value) {
        String slug = value == null ? "weapon" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        slug = slug.replaceAll("^_+|_+$", "");
        if (slug.isEmpty()) {
            slug = "weapon";
        }
        return "weapon_" + slug;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getModelAssetId() {
        return modelAssetId;
    }

    public void setModelAssetId(String modelAssetId) {
        this.modelAssetId = modelAssetId;
    }

    public String getDefaultAttachmentPoint() {
        return defaultAttachmentPoint;
    }

    public void setDefaultAttachmentPoint(String defaultAttachmentPoint) {
        this.defaultAttachmentPoint = defaultAttachmentPoint;
    }

    public WeaponAttachmentTransform getAttachmentTransform() {
        return attachmentTransform;
    }

    public List<String> getAllowedEquipmentSlots() {
        return allowedEquipmentSlots;
    }

    public String getHandMode() {
        return handMode;
    }

    public void setHandMode(String handMode) {
        this.handMode = handMode;
    }

    public List<String> getWeaponTags() {
        return weaponTags;
    }

    public List<AttackProfile> getAttackProfiles() {
        return attackProfiles;
    }

    public DamageProfile getDamageProfile() {
        return damageProfile;
    }

    public WeaponAnimationSet getAnimationSet() {
        return animationSet;
    }

    public WeaponEffectSet getEffectSet() {
        return effectSet;
    }

    public List<ProjectileDefinition> getProjectileDefinitions() {
        return projectileDefinitions;
    }

    public AmmoDefinition getAmmoDefinition() {
        return ammoDefinition;
    }

    public ReloadSettings getReloadSettings() {
        return reloadSettings;
    }
}
