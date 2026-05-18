package com.scenemax.desktop;

import com.scenemax.designer.weapon.WeaponRuntimeResourceExporter;
import com.scenemaxeng.common.weapons.WeaponDefinition;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WeaponRuntimeResourceExporterTest {

    @Test
    public void exportsWeaponDesignerDocumentToProjectResources() throws Exception {
        Path tempDir = Files.createTempDirectory("weapon-runtime-export");
        Path projectRoot = tempDir.resolve("project");
        Path scriptFolder = projectRoot.resolve("scripts/Game/weapons");
        Path resourcesFolder = projectRoot.resolve("resources");
        Files.createDirectories(scriptFolder);
        Files.createDirectories(resourcesFolder.resolve("weapons"));

        File designerFile = scriptFolder.resolve("player_weapon.smweapon").toFile();
        WeaponDefinition weapon = WeaponDefinition.createTemplate("Player Weapon", "sword");
        weapon.setId("weapon_player_weapon");
        weapon.setModelAssetId("meshy_axe");
        weapon.save(designerFile);

        File exported = WeaponRuntimeResourceExporter.export(designerFile, weapon);

        assertEquals(resourcesFolder.resolve("weapons/weapon_player_weapon.smweapon").toFile().getCanonicalFile(),
                exported.getCanonicalFile());
        assertTrue(exported.isFile());
        assertEquals("weapon_player_weapon", WeaponDefinition.load(exported).getId());

        deleteDirectory(tempDir.toFile());
    }

    @Test
    public void removesOlderResourceCopyWithSameWeaponId() throws Exception {
        Path tempDir = Files.createTempDirectory("weapon-runtime-export-duplicates");
        Path projectRoot = tempDir.resolve("project");
        Path scriptFolder = projectRoot.resolve("scripts/Game/weapons");
        Path resourcesFolder = projectRoot.resolve("resources/weapons");
        Files.createDirectories(scriptFolder);
        Files.createDirectories(resourcesFolder);

        WeaponDefinition older = WeaponDefinition.createTemplate("Old Player Weapon", "sword");
        older.setId("weapon_player_weapon");
        older.save(resourcesFolder.resolve("player_weapon.smweapon").toFile());

        File designerFile = scriptFolder.resolve("player_weapon.smweapon").toFile();
        WeaponDefinition weapon = WeaponDefinition.createTemplate("Player Weapon", "sword");
        weapon.setId("weapon_player_weapon");
        weapon.setModelAssetId("meshy_axe");
        weapon.save(designerFile);

        File exported = WeaponRuntimeResourceExporter.export(designerFile, weapon);

        assertTrue(exported.isFile());
        assertTrue(!Files.exists(resourcesFolder.resolve("player_weapon.smweapon")));

        deleteDirectory(tempDir.toFile());
    }

    private void deleteDirectory(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteDirectory(child);
                }
            }
        }
        file.delete();
    }
}
