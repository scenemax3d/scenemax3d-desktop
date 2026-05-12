package com.scenemaxeng.common.weapons;

import org.junit.Test;

import static org.junit.Assert.*;

public class WeaponDefinitionTest {
    @Test
    public void weaponDefinitionRoundTripsThroughJson() {
        WeaponDefinition original = WeaponDefinition.createTemplate("Iron Sword", "sword");
        original.setModelAssetId("Models/IronSword/iron_sword.j3o");
        original.getAttackProfiles().get(0).setAttackAnimation("SwordSlash");
        original.getAttackProfiles().get(0).setAttackSound("SwordSwing");
        original.getAttackProfiles().get(0).setMeleeTrailEffect("SwordTrail");
        original.getAttackProfiles().get(0).setProjectileLaunchOffsetZ(0.5);
        original.getAttackProfiles().get(0).setAttackHandlerProcedure("player1_on_primary_attack");
        ProjectileDefinition projectile = new ProjectileDefinition();
        projectile.setId("coin");
        projectile.setScaleX(1.5);
        projectile.setScaleY(1.5);
        projectile.setScaleZ(1.5);
        original.getProjectileDefinitions().add(projectile);

        WeaponDefinition loaded = WeaponDefinition.fromJSON(original.toJSON());

        assertEquals(original.getId(), loaded.getId());
        assertEquals("Iron Sword", loaded.getName());
        assertEquals("Models/IronSword/iron_sword.j3o", loaded.getModelAssetId());
        assertEquals(1, loaded.getAttackProfiles().size());
        assertEquals("SwordSlash", loaded.getAttackProfiles().get(0).getAttackAnimation());
        assertEquals("SwordSwing", loaded.getAttackProfiles().get(0).getAttackSound());
        assertEquals("SwordTrail", loaded.getAttackProfiles().get(0).getMeleeTrailEffect());
        assertEquals(0.5, loaded.getAttackProfiles().get(0).getProjectileLaunchOffsetZ(), 0.001);
        assertEquals("player1_on_primary_attack", loaded.getAttackProfiles().get(0).getAttackHandlerProcedure());
        assertEquals(1.5, loaded.findProjectileDefinition("coin").getScaleX(), 0.001);
        assertTrue(loaded.validate().isValid());
    }

    @Test
    public void validationCatchesMissingRequiredFields() {
        WeaponDefinition definition = WeaponDefinition.createTemplate("Broken", "sword");
        definition.setName("");
        definition.getAttackProfiles().clear();
        definition.getDamageProfile().setBaseDamage(0);

        WeaponValidationResult result = definition.validate();

        assertFalse(result.isValid());
        assertTrue(result.getIssues().stream().anyMatch(issue -> issue.getField().equals("name")));
        assertTrue(result.getIssues().stream().anyMatch(issue -> issue.getField().equals("attackProfiles")));
        assertTrue(result.getIssues().stream().anyMatch(issue -> issue.getField().equals("damageProfile.baseDamage")));
    }

    @Test
    public void rangedTemplateIncludesAmmoAndProjectile() {
        WeaponDefinition definition = WeaponDefinition.createTemplate("Pistol", "pistol");

        assertEquals("ranged", definition.getCategory());
        assertTrue(definition.getAmmoDefinition().isUsesAmmo());
        assertEquals(12, definition.getAmmoDefinition().getMagazineSize());
        assertFalse(definition.getProjectileDefinitions().isEmpty());
        assertTrue(definition.validate().isValid());
    }
}
