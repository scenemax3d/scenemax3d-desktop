package com.scenemax.desktop;

import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PackageProgramTaskAssetPackagingTest {

    @Test
    public void packagingUsesGameRootMainWhenStartedFromNestedLevelFile() throws Exception {
        Path tempDir = Files.createTempDirectory("package-root-main");
        Path gameRoot = tempDir.resolve("project/scripts/Fighting Game");
        Path levelRoot = gameRoot.resolve("game_level1");
        Files.createDirectories(levelRoot);
        Files.writeString(gameRoot.resolve("main"), "root.print \"whole game\"", StandardCharsets.UTF_8);
        Files.writeString(levelRoot.resolve("main"), "level.print \"level only\"", StandardCharsets.UTF_8);
        Path focusedFile = levelRoot.resolve("game_init.code");
        Files.writeString(focusedFile, "focused.print \"editor buffer\"", StandardCharsets.UTF_8);

        PackageProgramTask task = new PackageProgramTask(
                focusedFile.toString(),
                "unsaved focused buffer",
                null,
                null,
                () -> {},
                () -> {}
        );

        Method method = PackageProgramTask.class.getDeclaredMethod("normalizePackageRootAndProgram");
        method.setAccessible(true);
        method.invoke(task);

        Field scriptFolderField = PackageProgramTask.class.getDeclaredField("scriptFolder");
        scriptFolderField.setAccessible(true);
        Field prgField = PackageProgramTask.class.getDeclaredField("prg");
        prgField.setAccessible(true);

        assertEquals(gameRoot.toFile().getCanonicalFile(), ((File) scriptFolderField.get(task)).getCanonicalFile());
        assertEquals("root.print \"whole game\"", prgField.get(task));

        deleteDirectory(tempDir.toFile());
    }

    @Test
    public void copiesOnlyReferencedWeaponAndThrowMotionDocumentsIntoPackagedResources() throws Exception {
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
        Files.writeString(resourceWeapons.resolve("unused_weapon.smweapon"), "{\"id\":\"weapon_unused\"}", StandardCharsets.UTF_8);
        Files.writeString(resourceWeapons.resolve("nested/nested_weapon.smweapon"), "{\"id\":\"weapon_nested\"}", StandardCharsets.UTF_8);
        Files.writeString(resourceMotions.resolve("motion_shared.smmotion"), "{\"id\":\"motion_resource\"}", StandardCharsets.UTF_8);
        Files.writeString(resourceMotions.resolve("motion_unused.smmotion"), "{\"id\":\"motion_unused\"}", StandardCharsets.UTF_8);
        Files.writeString(scriptRoot.resolve("motion_shared.smmotion"), "{\"id\":\"motion_script\"}", StandardCharsets.UTF_8);

        PackageProgramTask task = new PackageProgramTask(scriptRoot.toString(), "", null, null, () -> {}, () -> {});
        addUsedName(task, "weaponAssetNamesUsed", "weapon_resource");
        addUsedName(task, "weaponAssetNamesUsed", "weapon_nested");
        addUsedName(task, "throwMotionAssetNamesUsed", "motion_resource");

        invokeCopyMethod(task, "copyWeaponResourcesToDeploy", deployRoot.toFile());
        invokeCopyMethod(task, "copyThrowMotionResourcesToDeploy", deployRoot.toFile());

        assertTrue(Files.isRegularFile(deployRoot.resolve("resources/weapons/resource_weapon.smweapon")));
        assertTrue(Files.isRegularFile(deployRoot.resolve("resources/weapons/nested/nested_weapon.smweapon")));
        assertTrue(Files.isRegularFile(deployRoot.resolve("resources/weapons/nested_weapon.smweapon")));
        assertFalse(Files.exists(deployRoot.resolve("resources/weapons/unused_weapon.smweapon")));
        assertEquals("{\"id\":\"motion_resource\"}",
                Files.readString(deployRoot.resolve("resources/throw_motions/motion_shared.smmotion"), StandardCharsets.UTF_8));
        assertFalse(Files.exists(deployRoot.resolve("resources/throw_motions/motion_unused.smmotion")));

        deleteDirectory(tempDir.toFile());
    }

    @Test
    public void copiesOnlyReferencedAnimationResourcesIntoPackagedResources() throws Exception {
        Path tempDir = Files.createTempDirectory("package-animations");
        Path projectRoot = tempDir.resolve("project");
        Path scriptRoot = projectRoot.resolve("scripts/Game");
        Path animations = projectRoot.resolve("resources/animations");
        Path deployRoot = tempDir.resolve("deploy");

        Files.createDirectories(scriptRoot);
        Files.createDirectories(animations.resolve("kick"));
        Files.createDirectories(animations.resolve("idle"));
        Files.writeString(animations.resolve("kick/kick.j3o"), "kick-data", StandardCharsets.UTF_8);
        Files.writeString(animations.resolve("idle/idle.j3o"), "idle-data", StandardCharsets.UTF_8);
        Files.writeString(animations.resolve("animations-ext.json"),
                "{ \"animations\": ["
                        + "{ \"name\":\"kick\", \"path\":\"animations/kick/kick.j3o\", \"clipName\":\"mixamo.com\" },"
                        + "{ \"name\":\"idle\", \"path\":\"animations/idle/idle.j3o\", \"clipName\":\"mixamo.com\" }"
                        + "] }",
                StandardCharsets.UTF_8);

        PackageProgramTask task = new PackageProgramTask(scriptRoot.toString(), "", null, null, () -> {}, () -> {});
        addUsedName(task, "animationNamesUsed", "kick");

        org.json.JSONArray packaged = new org.json.JSONArray();
        Method method = PackageProgramTask.class.getDeclaredMethod("copyAnimationResourcesToDeploy", File.class, org.json.JSONArray.class);
        method.setAccessible(true);
        method.invoke(task, deployRoot.toFile(), packaged);

        assertTrue(Files.isRegularFile(deployRoot.resolve("animations/kick/kick.j3o")));
        assertFalse(Files.exists(deployRoot.resolve("animations/idle/idle.j3o")));
        assertEquals(1, packaged.length());
        assertEquals("kick", packaged.getJSONObject(0).getString("name"));

        deleteDirectory(tempDir.toFile());
    }

    private void invokeCopyMethod(PackageProgramTask task, String methodName, File deployRoot) throws Exception {
        Method method = PackageProgramTask.class.getDeclaredMethod(methodName, File.class);
        method.setAccessible(true);
        method.invoke(task, deployRoot);
    }

    @SuppressWarnings("unchecked")
    private void addUsedName(PackageProgramTask task, String fieldName, String value) throws Exception {
        Field field = PackageProgramTask.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        ((Set<String>) field.get(task)).add(value);
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
