package com.scenemax.desktop.ai.gemma.install;

public enum GemmaModelVariant {
    E2B(
            "gemma-4-e2b-it",
            "Gemma 4 E2B (Recommended)",
            "gemma-4-E2B-it.litertlm",
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true",
            "Smaller, faster local model. Best default for most Windows laptops."
    ),
    E4B(
            "gemma-4-e4b-it",
            "Gemma 4 E4B",
            "gemma-4-E4B-it.litertlm",
            "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm?download=true",
            "Higher quality and heavier memory use. Better on stronger PCs."
    );

    private final String modelId;
    private final String displayName;
    private final String fileName;
    private final String downloadUrl;
    private final String description;

    GemmaModelVariant(String modelId, String displayName, String fileName, String downloadUrl, String description) {
        this.modelId = modelId;
        this.displayName = displayName;
        this.fileName = fileName;
        this.downloadUrl = downloadUrl;
        this.description = description;
    }

    public String getModelId() {
        return modelId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getFileName() {
        return fileName;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
