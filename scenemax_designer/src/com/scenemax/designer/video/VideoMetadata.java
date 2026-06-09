package com.scenemax.designer.video;

import java.io.File;
import java.util.Locale;

public class VideoMetadata {
    final int width;
    final int height;
    final double frameRate;
    final double durationSeconds;
    final long frames;
    final int audioChannels;
    final String format;

    private VideoMetadata(int width, int height, double frameRate, double durationSeconds,
                          long frames, int audioChannels, String format) {
        this.width = width;
        this.height = height;
        this.frameRate = frameRate;
        this.durationSeconds = durationSeconds;
        this.frames = frames;
        this.audioChannels = audioChannels;
        this.format = format;
    }

    public static VideoMetadata probe(File file) throws Exception {
        return new VideoMetadata(0, 0, 0d, 0d, 0, 0, extension(file));
    }

    private static String extension(File file) {
        String name = file == null ? "" : file.getName();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && dot < name.length() - 1
                ? name.substring(dot + 1).toLowerCase(Locale.ROOT)
                : "";
    }

    String dimensionsText() {
        if (width <= 0 || height <= 0) {
            return "Unknown";
        }
        return width + " x " + height;
    }

    String frameRateText() {
        return frameRate > 0 ? String.format("%.2f fps", frameRate) : "Unknown";
    }

    String durationText() {
        if (durationSeconds <= 0) {
            return "Unknown";
        }
        int seconds = (int) Math.round(durationSeconds);
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
