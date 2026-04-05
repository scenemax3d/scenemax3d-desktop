package com.scenemax.designer.effekseer;

import java.io.File;
import java.util.Locale;

final class EffekseerNativeEffectResolver {

    private EffekseerNativeEffectResolver() {
    }

    public static File resolveRuntimeEffect(File importedEffectFile) {
        if (importedEffectFile == null || !importedEffectFile.isFile()) {
            return null;
        }

        String lowerName = importedEffectFile.getName().toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".efkefc") || lowerName.endsWith(".efk")) {
            return importedEffectFile;
        }

        String baseName = baseName(importedEffectFile.getName());
        File parent = importedEffectFile.getParentFile();
        if (parent == null || !parent.isDirectory()) {
            return null;
        }

        File efkefc = new File(parent, baseName + ".efkefc");
        if (efkefc.isFile()) {
            return efkefc;
        }

        File efk = new File(parent, baseName + ".efk");
        if (efk.isFile()) {
            return efk;
        }

        return null;
    }

    private static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
