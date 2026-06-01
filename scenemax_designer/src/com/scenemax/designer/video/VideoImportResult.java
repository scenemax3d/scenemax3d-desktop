package com.scenemax.designer.video;

import java.io.File;

public class VideoImportResult {
    private final String assetId;
    private final File assetFolder;
    private final File videoFile;

    VideoImportResult(String assetId, File assetFolder, File videoFile) {
        this.assetId = assetId;
        this.assetFolder = assetFolder;
        this.videoFile = videoFile;
    }

    public String getAssetId() {
        return assetId;
    }

    public File getAssetFolder() {
        return assetFolder;
    }

    public File getVideoFile() {
        return videoFile;
    }
}
