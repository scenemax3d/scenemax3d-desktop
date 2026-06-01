package com.scenemax.designer.video;

import org.bytedeco.javacv.FFmpegFrameGrabber;

import java.io.File;

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
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(file)) {
            grabber.start();
            VideoMetadata metadata = new VideoMetadata(
                    grabber.getImageWidth(),
                    grabber.getImageHeight(),
                    grabber.getFrameRate(),
                    grabber.getLengthInTime() > 0 ? grabber.getLengthInTime() / 1_000_000d : 0d,
                    grabber.getLengthInFrames(),
                    grabber.getAudioChannels(),
                    safe(grabber.getFormat())
            );
            grabber.stop();
            return metadata;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
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
