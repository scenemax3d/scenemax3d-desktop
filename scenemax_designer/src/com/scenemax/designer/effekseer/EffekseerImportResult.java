package com.scenemax.designer.effekseer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EffekseerImportResult {

    private final File documentFile;
    private final File assetFolder;
    private final File importedEffectFile;
    private final List<String> diagnostics;

    public EffekseerImportResult(File documentFile, File assetFolder, File importedEffectFile, List<String> diagnostics) {
        this.documentFile = documentFile;
        this.assetFolder = assetFolder;
        this.importedEffectFile = importedEffectFile;
        this.diagnostics = diagnostics != null ? new ArrayList<>(diagnostics) : new ArrayList<>();
    }

    public File getDocumentFile() {
        return documentFile;
    }

    public File getAssetFolder() {
        return assetFolder;
    }

    public File getImportedEffectFile() {
        return importedEffectFile;
    }

    public List<String> getDiagnostics() {
        return Collections.unmodifiableList(diagnostics);
    }
}
