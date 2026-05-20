package com.scenemax.desktop;

import org.junit.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PackageProgramTaskAssetPackagingTest {

    @Test
    public void copiesResourceWeaponAndThrowMotionDocumentsIntoPackagedResources() throws Exception {
        Path tempDir = Files.createTempDirectory("package-assets");
        Path projectRoot = tempDir.resolve("project");
        Path scriptRoot = projectRoot.resolve("scripts/Game");
        Path resourceWeapons = projectRoot.resolve("resources/weapons");
        Path resourceMotions = projectRoot.resolve("resources/throw_motions");
        Path deployRoot = tempDir.resolve("deploy");

        Files.createDirectories(scriptRoot);
        Files.createDirectories(resourceWeapons);
        Files.createDirectories(resourceMotions);
        Files.createDirectories(resourceWeapons.resolve("nested"));

        Files.writeString(resourceWeapons.resolve("resource_weapon.smweapon"), "{\"id\":\"weapon_resource\"}", StandardCharsets.UTF_8);
        Files.writeString(resourceWeapons.resolve("nested/nested_weapon.smweapon"), "{\"id\":\"weapon_nested\"}", StandardCharsets.UTF_8);
        Files.writeString(resourceMotions.resolve("motion_shared.smmotion"), "{\"id\":\"motion_resource\"}", StandardCharsets.UTF_8);
        Files.writeString(scriptRoot.resolve("motion_shared.smmotion"), "{\"id\":\"motion_script\"}", StandardCharsets.UTF_8);

        PackageProgramTask task = new PackageProgramTask(scriptRoot.toString(), "", null, null, () -> {}, () -> {});

        invokeCopyMethod(task, "copyWeaponResourcesToDeploy", deployRoot.toFile());
        invokeCopyMethod(task, "copyThrowMotionResourcesToDeploy", deployRoot.toFile());

        assertTrue(Files.isRegularFile(deployRoot.resolve("resources/weapons/resource_weapon.smweapon")));
        assertTrue(Files.isRegularFile(deployRoot.resolve("resources/weapons/nested/nested_weapon.smweapon")));
        assertTrue(Files.isRegularFile(deployRoot.resolve("resources/weapons/nested_weapon.smweapon")));
        assertEquals("{\"id\":\"motion_resource\"}",
                Files.readString(deployRoot.resolve("resources/throw_motions/motion_shared.smmotion"), StandardCharsets.UTF_8));

        deleteDirectory(tempDir.toFile());
    }

    private void invokeCopyMethod(PackageProgramTask task, String methodName, File deployRoot) throws Exception {
        Method method = PackageProgramTask.class.getDeclaredMethod(methodName, File.class);
        method.setAccessible(true);
        method.invoke(task, deployRoot);
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
