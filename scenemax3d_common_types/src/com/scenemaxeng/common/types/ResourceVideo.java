package com.scenemaxeng.common.types;

public class ResourceVideo {
    public final String name;
    public final String path;
    public final int width;
    public final int height;
    public final double frameRate;
    public final double durationSeconds;
    public final String format;

    public ResourceVideo(String name, String path, int width, int height,
                         double frameRate, double durationSeconds, String format) {
        this.name = name;
        this.path = path;
        this.width = width;
        this.height = height;
        this.frameRate = frameRate;
        this.durationSeconds = durationSeconds;
        this.format = format;
    }
}
