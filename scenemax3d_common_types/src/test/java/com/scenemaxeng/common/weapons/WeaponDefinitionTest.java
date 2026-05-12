package com.scenemaxeng.common.weapons;

import org.junit.Test;

import static org.junit.Assert.*;

public class WeaponDefinitionTest {
    @Test
    public void weaponDefinitionRoundTripsMinimalPostureDataThroughJson() {
        WeaponDefinition original = WeaponDefinition.createTemplate("Iron Sword", "sword");
        original.setModelAssetId("Models/IronSword/iron_sword.j3o");
        original.getDefaultPosture().setId("fight");
        original.getDefaultPosture().setName("Fight");
        original.getDefaultPosture().setAttachmentPoint("mixamorig:RightHand");
        original.getDefaultPosture().getTransform().setScaleX(0.35);
        original.setDefaultPostureId("fight");

        WeaponPostureDefinition ready = new WeaponPostureDefinition();
        ready.setId("ready");
        ready.setName("Ready");
        ready.setAttachmentPoint("mixamorig:Spine2");
        ready.getTransform().setRotationZ(90);
        original.getPostures().add(ready);

        WeaponDefinition loaded = WeaponDefinition.fromJSON(original.toJSON());

        assertEquals(original.getId(), loaded.getId());
        assertEquals("Models/IronSword/iron_sword.j3o", loaded.getModelAssetId());
        assertEquals(2, loaded.getPostures().size());
        assertEquals("fight", loaded.getDefaultPosture().getId());
        assertEquals("mixamorig:RightHand", loaded.getDefaultPosture().getAttachmentPoint());
        assertEquals(0.35, loaded.getDefaultPosture().getTransform().getScaleX(), 0.0001);
        assertEquals("mixamorig:Spine2", loaded.findPosture("ready").getAttachmentPoint());
        assertFalse(loaded.toJSON().has("category"));
        assertFalse(loaded.toJSON().has("handMode"));
        assertTrue(loaded.validate().isValid());
    }

    @Test
    public void validationCatchesMissingRequiredFields() {
        WeaponDefinition definition = WeaponDefinition.createTemplate("Broken", "sword");
        definition.setId("");
        definition.getPostures().clear();

        WeaponValidationResult result = definition.validate();

        assertFalse(result.isValid());
        assertTrue(result.getIssues().stream().anyMatch(issue -> issue.getField().equals("id")));
        assertTrue(result.getIssues().stream().anyMatch(issue -> issue.getField().equals("postures")));
    }

    @Test
    public void legacyAttachmentFieldsLoadAsDefaultPosture() {
        WeaponDefinition definition = WeaponDefinition.fromJSON(new org.json.JSONObject()
                .put("id", "weapon_legacy")
                .put("name", "Legacy Weapon")
                .put("category", "melee")
                .put("allowedEquipmentSlots", new org.json.JSONArray().put("rightHand"))
                .put("defaultAttachmentPoint", "mixamorig:RightHand")
                .put("attachmentTransform", new org.json.JSONObject().put("scaleX", 0.5)));

        assertEquals("weapon_legacy", definition.getId());
        assertEquals("mixamorig:RightHand", definition.getDefaultPosture().getAttachmentPoint());
        assertEquals(0.5, definition.getDefaultPosture().getTransform().getScaleX(), 0.0001);
        assertFalse(definition.toJSON().has("allowedEquipmentSlots"));
    }
}
