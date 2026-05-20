package com.scenemaxeng.common.types;

import com.scenemaxeng.common.motion.ThrowMotionDefinition;
import com.scenemaxeng.common.weapons.WeaponDefinition;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AssetsMappingThrowMotionTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void projectResourceMotionIsRuntimeSourceWhenScriptDesignerDocHasSameId() throws Exception {
        File project = temporaryFolder.newFolder("project");
        File resourcesThrowMotions = new File(project, "resources/throw_motions");
        File scriptLevel = new File(project, "scripts/Fighting Game/game_level1");
        assertTrue(resourcesThrowMotions.mkdirs());
        assertTrue(scriptLevel.mkdirs());

        ThrowMotionDefinition resourcesMotion = ThrowMotionDefinition.createTemplate(
                "Axe Throw", ThrowMotionDefinition.TYPE_TARGET_ARC);
        resourcesMotion.setId("motion_axe_throw");
        resourcesMotion.getParameters().duration = 1.2;
        resourcesMotion.getParameters().arcHeight = 3.0;
        resourcesMotion.save(new File(resourcesThrowMotions, "motion_axe_throw.smmotion"));

        ThrowMotionDefinition scriptMotion = ThrowMotionDefinition.createTemplate(
                "Axe Throw", ThrowMotionDefinition.TYPE_TARGET_ARC);
        scriptMotion.setId("motion_axe_throw");
        scriptMotion.getParameters().duration = 0.8;
        scriptMotion.getParameters().arcHeight = 2.0;
        scriptMotion.save(new File(scriptLevel, "motion_axe_throw.smmotion"));

        AssetsMapping mapping = new AssetsMapping(new File(project, "resources").getAbsolutePath());
        mapping.loadWeaponsFromProject(project.getAbsolutePath());

        ThrowMotionDefinition resolved = mapping.getThrowMotionDefinition("motion_axe_throw");

        assertEquals(1.2, resolved.getParameters().duration, 0.0001);
        assertEquals(3.0, resolved.getParameters().arcHeight, 0.0001);
    }

    @Test
    public void packagedThrowMotionCanLoadFromClasspathResource() {
        AssetsMapping mapping = new AssetsMapping(new File(temporaryFolder.getRoot(), "missing_resources").getAbsolutePath());

        ThrowMotionDefinition resolved = mapping.getThrowMotionDefinition("motion_packaged_throw");

        assertEquals(1.6, resolved.getParameters().duration, 0.0001);
        assertEquals(4.5, resolved.getParameters().arcHeight, 0.0001);
    }

    @Test
    public void packagedWeaponCanLoadFromClasspathResource() throws Exception {
        File classpathRoot = new File(AssetsMappingThrowMotionTest.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        WeaponDefinition weapon = WeaponDefinition.createTemplate("Packaged Blade", "sword");
        weapon.setId("weapon_packaged_blade");
        weapon.setModelAssetId("packaged_blade_model");
        weapon.save(new File(classpathRoot, "resources/weapons/packaged_blade.smweapon"));

        AssetsMapping mapping = new AssetsMapping(new File(temporaryFolder.getRoot(), "missing_resources").getAbsolutePath());

        WeaponDefinition resolved = mapping.getWeaponDefinition("weapon_packaged_blade");

        assertEquals("weapon_packaged_blade", resolved.getId());
        assertEquals("packaged_blade_model", resolved.getModelAssetId());
    }

    @Test
    public void doublePrefixedWeaponIdCanResolvePackagedFileName() throws Exception {
        File classpathRoot = new File(AssetsMappingThrowMotionTest.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        WeaponDefinition weapon = WeaponDefinition.createTemplate("Player Weapon", "sword");
        weapon.setId("weapon_player_weapon");
        weapon.setModelAssetId("player_weapon_model");
        weapon.save(new File(classpathRoot, "resources/weapons/player_weapon.smweapon"));

        AssetsMapping mapping = new AssetsMapping(new File(temporaryFolder.getRoot(), "missing_resources").getAbsolutePath());

        WeaponDefinition resolved = mapping.getWeaponDefinition("weapon_weapon_player_weapon");

        assertEquals("weapon_player_weapon", resolved.getId());
        assertEquals("player_weapon_model", resolved.getModelAssetId());
    }

    @Test
    public void projectWeaponRuntimeLookupUsesResourcesRegistration() throws Exception {
        File project = temporaryFolder.newFolder("weapon_project");
        File resourcesWeapons = new File(project, "resources/weapons");
        File scriptLevel = new File(project, "scripts/Fighting Game/game_level1");
        assertTrue(resourcesWeapons.mkdirs());
        assertTrue(scriptLevel.mkdirs());

        WeaponDefinition runtimeWeapon = WeaponDefinition.createTemplate("Player Weapon", "sword");
        runtimeWeapon.setId("weapon_player_weapon");
        runtimeWeapon.setModelAssetId("runtime_model");
        runtimeWeapon.save(new File(resourcesWeapons, "weapon_player_weapon.smweapon"));

        WeaponDefinition designerSource = WeaponDefinition.createTemplate("Player Weapon", "sword");
        designerSource.setId("weapon_player_weapon");
        designerSource.setModelAssetId("designer_source_model");
        designerSource.save(new File(scriptLevel, "player_weapon.smweapon"));

        AssetsMapping mapping = new AssetsMapping(new File(project, "resources").getAbsolutePath());
        mapping.loadWeaponsFromProject(project.getAbsolutePath());

        WeaponDefinition resolved = mapping.getWeaponDefinition("weapon_player_weapon");

        assertEquals("runtime_model", resolved.getModelAssetId());
    }
}
