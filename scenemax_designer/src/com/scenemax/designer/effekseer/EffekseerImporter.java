package com.scenemax.designer.effekseer;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class EffekseerImporter {

    private static final Set<String> EFFECT_EXTENSIONS = new HashSet<>(Arrays.asList(".efkefc", ".efkproj", ".efk"));
    private static final Set<String> DIRECTORIES_TO_COPY = new HashSet<>(Arrays.asList("texture", "model", "sound", "material", "curve"));

    public static EffekseerImportResult importEffect(File sourceEffectFile,
                                                     File resourcesFolder,
                                                     File targetScriptsFolder) throws IOException {
        validateInputs(sourceEffectFile, resourcesFolder, targetScriptsFolder);
        String baseName = baseName(sourceEffectFile.getName());
        String assetId = uniqueAssetId(new File(resourcesFolder, "effects"),
                EffekseerEffectDocument.sanitizeAssetId(baseName));
        return importEffectInternal(sourceEffectFile, resourcesFolder, targetScriptsFolder, assetId, assetId + ".smeffectdesign");
    }

    public static EffekseerImportResult reimport(EffekseerEffectDocument document,
                                                 File documentFile,
                                                 File resourcesFolder) throws IOException {
        if (document == null || documentFile == null) {
            throw new IOException("Effect document is missing.");
        }
        File sourceEffectFile = new File(document.getOriginalImportPath());
        if (!sourceEffectFile.isFile()) {
            throw new IOException("Original Effekseer import path no longer exists: " + document.getOriginalImportPath());
        }
        return importEffectInternal(sourceEffectFile,
                resourcesFolder,
                documentFile.getParentFile(),
                EffekseerEffectDocument.sanitizeAssetId(document.getAssetId()),
                documentFile.getName());
    }

    private static EffekseerImportResult importEffectInternal(File sourceEffectFile,
                                                              File resourcesFolder,
                                                              File targetScriptsFolder,
                                                              String assetId,
                                                              String documentName) throws IOException {
        File effectsRoot = new File(resourcesFolder, "effects");
        if (!effectsRoot.exists() && !effectsRoot.mkdirs()) {
            throw new IOException("Failed to create effects folder: " + effectsRoot.getAbsolutePath());
        }
        File assetFolder = new File(effectsRoot, assetId);
        if (assetFolder.exists()) {
            FileUtils.deleteDirectory(assetFolder);
        }
        if (!assetFolder.mkdirs()) {
            throw new IOException("Failed to create effect asset folder: " + assetFolder.getAbsolutePath());
        }

        List<String> diagnostics = new ArrayList<>();
        File importedEffectFile = new File(assetFolder, sourceEffectFile.getName());
        FileUtils.copyFile(sourceEffectFile, importedEffectFile);

        File sourceParent = sourceEffectFile.getParentFile();
        if (sourceParent != null && sourceParent.isDirectory()) {
            File[] siblings = sourceParent.listFiles();
            if (siblings != null) {
                for (File sibling : siblings) {
                    if (sibling == null || sourceEffectFile.equals(sibling)) {
                        continue;
                    }
                    if (shouldCopySibling(sibling)) {
                        File dest = new File(assetFolder, sibling.getName());
                        if (sibling.isDirectory()) {
                            FileUtils.copyDirectory(sibling, dest);
                        } else {
                            FileUtils.copyFile(sibling, dest);
                        }
                    }
                }
            }
        }

        if (!targetScriptsFolder.exists() && !targetScriptsFolder.mkdirs()) {
            throw new IOException("Failed to create scripts folder: " + targetScriptsFolder.getAbsolutePath());
        }
        File documentFile = new File(targetScriptsFolder, documentName);

        String relativeEffectPath = resourcesFolder.toPath().relativize(importedEffectFile.toPath()).toString().replace("\\", "/");
        String extension = extension(sourceEffectFile.getName());
        EffekseerEffectDocument document = EffekseerEffectDocument.createImported(
                documentFile.getAbsolutePath(),
                baseName(sourceEffectFile.getName()),
                assetId,
                relativeEffectPath,
                extension.startsWith(".") ? extension.substring(1) : extension,
                sourceEffectFile.getAbsolutePath(),
                Instant.now().toString()
        );
        document.save(documentFile);

        if (importedEffectFile.getName().toLowerCase(Locale.ROOT).endsWith(".efkproj")) {
            try {
                File runtimeEffectFile = EffekseerTooling.exportRuntimeEffect(importedEffectFile);
                diagnostics.add("Exported runtime effect for native preview: " + runtimeEffectFile.getName());
            } catch (IOException ex) {
                diagnostics.add("Runtime export skipped: " + ex.getMessage());
            }
        }

        diagnostics.add("Imported " + sourceEffectFile.getName() + " into " + assetFolder.getAbsolutePath());
        return new EffekseerImportResult(documentFile, assetFolder, importedEffectFile, diagnostics);
    }

    private static void validateInputs(File sourceEffectFile, File resourcesFolder, File targetScriptsFolder) throws IOException {
        if (sourceEffectFile == null || !sourceEffectFile.isFile()) {
            throw new IOException("Effekseer source file was not found.");
        }
        String ext = extension(sourceEffectFile.getName()).toLowerCase(Locale.ROOT);
        if (!EFFECT_EXTENSIONS.contains(ext)) {
            throw new IOException("Unsupported Effekseer file type: " + sourceEffectFile.getName());
        }
        if (resourcesFolder == null) {
            throw new IOException("Project resources folder is not available.");
        }
        if (targetScriptsFolder == null) {
            throw new IOException("Target scripts folder is not available.");
        }
    }

    private static boolean shouldCopySibling(File sibling) {
        if (sibling.isDirectory()) {
            return DIRECTORIES_TO_COPY.contains(sibling.getName().toLowerCase(Locale.ROOT));
        }
        String ext = extension(sibling.getName()).toLowerCase(Locale.ROOT);
        if (EFFECT_EXTENSIONS.contains(ext)) {
            return false;
        }
        return ext.equals(".efkmat")
                || ext.equals(".efkmodel")
                || ext.equals(".png")
                || ext.equals(".jpg")
                || ext.equals(".jpeg")
                || ext.equals(".bmp")
                || ext.equals(".tga")
                || ext.equals(".dds")
                || ext.equals(".wav")
                || ext.equals(".ogg")
                || ext.equals(".mp3")
                || ext.equals(".txt")
                || ext.equals(".json");
    }

    private static String uniqueAssetId(File effectsRoot, String baseAssetId) {
        String candidate = baseAssetId;
        int suffix = 2;
        while (new File(effectsRoot, candidate).exists()) {
            candidate = baseAssetId + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot) : "";
    }
}
