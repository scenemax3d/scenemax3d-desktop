package com.scenemaxeng.common.types;

public class ResourceCinematicRig {

    public final String name;
    public final String sourcePath;
    public final String jsonBuffer;
    public final String documentBuffer;

    public ResourceCinematicRig(String name, String sourcePath, String jsonBuffer) {
        this(name, sourcePath, jsonBuffer, null);
    }

    public ResourceCinematicRig(String name, String sourcePath, String jsonBuffer, String documentBuffer) {
        this.name = name;
        this.sourcePath = sourcePath;
        this.jsonBuffer = jsonBuffer;
        this.documentBuffer = documentBuffer;
    }
}
