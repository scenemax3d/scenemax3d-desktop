package com.scenemax.designer.ik;

import com.scenemaxeng.common.ik.IKDefinition;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Locale;

public final class IKRuntimeResourceExporter {
    private IKRuntimeResourceExporter() {
    }

    public static File export(File sourceFile, IKDefinition definition) throws IOException {
        File resourcesRoot = findResourcesRoot(sourceFile);
        if (resourcesRoot == null || definition == null || definition.getId() == null || definition.getId().trim().isEmpty()) {
            return null;
        }
        File runtimeFolder = new File(resourcesRoot, "ik");
        if (!runtimeFolder.exists() && !runtimeFolder.mkdirs()) {
            throw new IOException("Unable to create " + runtimeFolder.getAbsolutePath());
        }
        File target = new File(runtimeFolder, runtimeFileName(sourceFile, definition.getId()));
        definition.save(target);
        deleteDuplicateRuntimeResources(resourcesRoot, target, definition.getId());
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
        deleteDuplicateRuntimeResourcesInRoot(new File(resourcesRoot, "ik"), keepFile, normalizedId);
        deleteDuplicateRuntimeResourcesInRoot(new File(resourcesRoot, "IK"), keepFile, normalizedId);
    }

    private static void deleteDuplicateRuntimeResourcesInRoot(File root, File keepFile, String normalizedId) {
        if (!root.isDirectory()) {
            return;
        }
        Collection<File> files = FileUtils.listFiles(root, new String[]{"smik", "json"}, true);
        for (File file : files) {
            if (!isIKFile(file) || sameFile(file, keepFile)) {
                continue;
            }
            try {
                IKDefinition existing = IKDefinition.load(file);
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
        return safe + IKDefinition.FILE_EXTENSION;
    }

    private static String stripExtension(String name) {
        if (name == null) {
            return "";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(IKDefinition.FILE_EXTENSION)) {
            return name.substring(0, name.length() - IKDefinition.FILE_EXTENSION.length());
        }
        if (lower.endsWith(IKDefinition.LEGACY_FILE_EXTENSION)) {
            return name.substring(0, name.length() - IKDefinition.LEGACY_FILE_EXTENSION.length());
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static boolean isIKFile(File file) {
        if (file == null) {
            return false;
        }
        String lower = file.getName().toLowerCase(Locale.ROOT);
        return lower.endsWith(IKDefinition.FILE_EXTENSION)
                || lower.endsWith(IKDefinition.LEGACY_FILE_EXTENSION);
    }
}
