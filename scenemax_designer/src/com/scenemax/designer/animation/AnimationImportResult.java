package com.scenemax.designer.animation;

import java.io.File;
import java.util.Collections;
import java.util.List;
import com.jme3.scene.Spatial;

public class AnimationImportResult {

    private final File assetFolder;
    private final File animationFile;
    private final List<String> clipNames;
    private final List<String> clipSummaries;
    private final String selectedClipName;
    private final Spatial importedSpatial;

    public AnimationImportResult(File assetFolder, File animationFile, List<String> clipNames) {
        this(assetFolder, animationFile, clipNames, null);
    }

    public AnimationImportResult(File assetFolder, File animationFile, List<String> clipNames, List<String> clipSummaries) {
        this(assetFolder, animationFile, clipNames, clipSummaries, null, null);
    }

    public AnimationImportResult(File assetFolder, File animationFile, List<String> clipNames, List<String> clipSummaries, Spatial importedSpatial) {
        this(assetFolder, animationFile, clipNames, clipSummaries, null, importedSpatial);
    }

    public AnimationImportResult(File assetFolder, File animationFile, List<String> clipNames,
                                 List<String> clipSummaries, String selectedClipName, Spatial importedSpatial) {
        this.assetFolder = assetFolder;
        this.animationFile = animationFile;
        this.clipNames = clipNames == null ? Collections.emptyList() : Collections.unmodifiableList(clipNames);
        this.clipSummaries = clipSummaries == null ? Collections.emptyList() : Collections.unmodifiableList(clipSummaries);
        this.selectedClipName = selectedClipName;
        this.importedSpatial = importedSpatial;
    }

    public File getAssetFolder() {
        return assetFolder;
    }

    public File getAnimationFile() {
        return animationFile;
    }

    public List<String> getClipNames() {
        return clipNames;
    }

    public List<String> getClipSummaries() {
        return clipSummaries;
    }

    public String getSelectedClipName() {
        return selectedClipName;
    }

    public Spatial getImportedSpatial() {
        return importedSpatial;
    }
}
