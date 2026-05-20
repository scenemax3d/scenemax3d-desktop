package com.scenemax.designer.weapon;

import com.scenemaxeng.common.weapons.WeaponDefinition;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Locale;

public final class WeaponRuntimeResourceExporter {
    private WeaponRuntimeResourceExporter() {
    }

    public static File export(File sourceFile, WeaponDefinition definition) throws IOException {
        File resourcesRoot = findResourcesRoot(sourceFile);
        if (resourcesRoot == null) {
            return null;
        }

        String resourceId = definition == null || definition.getId() == null ? "" : definition.getId().trim();
        if (resourceId.isEmpty()) {
            return null;
        }

        File runtimeFolder = new File(resourcesRoot, "weapons");
        if (!runtimeFolder.exists() && !runtimeFolder.mkdirs()) {
            throw new IOException("Unable to create " + runtimeFolder.getAbsolutePath());
        }

        File target = new File(runtimeFolder, runtimeFileName(sourceFile, resourceId));
        definition.save(target);
        deleteDuplicateRuntimeResources(resourcesRoot, target, resourceId);
        return target;
    }

    public static File findResourcesRoot(File sourceFile) {
        if (sourceFile == null) {
            return null;
        }
        File current = sourceFile.getAbsoluteFile().getParentFile();
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

    private static void deleteDuplicateRuntimeResources(File resourcesRoot, File keepFile, String resourceId) {
        String normalizedId = resourceId.trim().toLowerCase(Locale.ROOT);
        deleteDuplicateRuntimeResourcesInRoot(new File(resourcesRoot, "weapons"), keepFile, normalizedId);
        deleteDuplicateRuntimeResourcesInRoot(new File(resourcesRoot, "Weapons"), keepFile, normalizedId);
    }

    private static void deleteDuplicateRuntimeResourcesInRoot(File root, File keepFile, String normalizedId) {
        if (!root.isDirectory()) {
            return;
        }
        Collection<File> files = FileUtils.listFiles(root, new String[]{"smweapon"}, true);
        for (File file : files) {
            if (sameFile(file, keepFile)) {
                continue;
            }
            try {
                WeaponDefinition existing = WeaponDefinition.load(file);
                String existingId = existing.getId() == null ? "" : existing.getId().trim().toLowerCase(Locale.ROOT);
                if (normalizedId.equals(existingId)) {
                    java.nio.file.Files.deleteIfExists(file.toPath());
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static boolean sameFile(File a, File b) {
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (IOException ignored) {
            return a.getAbsoluteFile().equals(b.getAbsoluteFile());
        }
    }

    private static String runtimeFileName(File sourceFile, String resourceId) {
        String safe = resourceId.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[\\\\/:*?\"<>|]+", "_")
                .replaceAll("\\s+", "_");
        if (safe.isEmpty() && sourceFile != null) {
            safe = stripExtension(sourceFile.getName()).toLowerCase(Locale.ROOT);
        }
        return safe + WeaponDefinition.FILE_EXTENSION;
    }

    private static String stripExtension(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : "";
    }
}
