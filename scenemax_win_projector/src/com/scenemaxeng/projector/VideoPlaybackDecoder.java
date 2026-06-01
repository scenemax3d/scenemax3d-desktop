package com.scenemaxeng.projector;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

class VideoPlaybackDecoder implements AutoCloseable, Runnable {

    private final File file;
    private final double startSeconds;
    private final double endSeconds;
    private final boolean reverse;
    private final boolean loop;
    private final AtomicReference<BufferedImage> latestFrame = new AtomicReference<>();
    private volatile boolean running = true;
    private volatile boolean finished;
    private volatile String errorMessage;
    private Thread thread;

    VideoPlaybackDecoder(File file, double startSeconds, double endSeconds, boolean reverse, boolean loop) {
        this.file = file;
        this.startSeconds = Math.max(0d, startSeconds);
        this.endSeconds = endSeconds;
        this.reverse = reverse;
        this.loop = loop;
    }

    void start() {
        thread = new Thread(this, "SceneMax Video Runtime Decoder");
        thread.setDaemon(true);
        thread.start();
    }

    BufferedImage pollFrame() {
        return latestFrame.getAndSet(null);
    }

    boolean isFinished() {
        return finished;
    }

    String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public void run() {
        try {
            if (reverse) {
                runReverse();
            } else {
                runForward();
            }
        } catch (Exception ex) {
            errorMessage = ex.getMessage();
        } finally {
            finished = true;
        }
    }

    private void runForward() throws Exception {
        do {
            try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(file);
                 Java2DFrameConverter converter = new Java2DFrameConverter()) {
                grabber.start();
                double fps = normalizeFps(grabber.getFrameRate());
                long frameDelayMs = frameDelayMs(fps);
                long endUs = resolveEndUs(grabber);
                if (startSeconds > 0d) {
                    grabber.setTimestamp(toMicros(startSeconds));
                }

                while (running) {
                    Frame frame = grabber.grabImage();
                    if (frame == null) {
                        break;
                    }
                    long timestamp = grabber.getTimestamp();
                    if (endUs > 0L && timestamp > endUs) {
                        break;
                    }
                    publishFrame(converter.convert(frame));
                    sleep(frameDelayMs);
                }
                grabber.stop();
            }
        } while (running && loop);
    }

    private void runReverse() throws Exception {
        do {
            try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(file);
                 Java2DFrameConverter converter = new Java2DFrameConverter()) {
                grabber.start();
                double fps = normalizeFps(grabber.getFrameRate());
                long frameDelayMs = frameDelayMs(fps);
                long stepUs = Math.max(1L, Math.round(1_000_000d / fps));
                long startUs = toMicros(startSeconds);
                long endUs = resolveEndUs(grabber);
                if (endUs <= startUs) {
                    endUs = Math.max(startUs, grabber.getLengthInTime());
                }

                for (long ts = endUs; running && ts >= startUs; ts -= stepUs) {
                    grabber.setTimestamp(ts);
                    Frame frame = grabber.grabImage();
                    if (frame != null) {
                        publishFrame(converter.convert(frame));
                    }
                    sleep(frameDelayMs);
                }
                grabber.stop();
            }
        } while (running && loop);
    }

    private long resolveEndUs(FFmpegFrameGrabber grabber) {
        if (endSeconds > 0d) {
            return toMicros(endSeconds);
        }
        long length = grabber.getLengthInTime();
        return length > 0L ? length : -1L;
    }

    private void publishFrame(BufferedImage image) {
        if (image != null) {
            latestFrame.set(copy(image));
        }
    }

    @Override
    public void close() {
        running = false;
        Thread t = thread;
        if (t != null) {
            t.interrupt();
        }
    }

    private static long toMicros(double seconds) {
        return Math.max(0L, Math.round(seconds * 1_000_000d));
    }

    private static double normalizeFps(double fps) {
        return fps > 1d ? fps : 30d;
    }

    private static long frameDelayMs(double fps) {
        return Math.max(1L, Math.round(1000d / normalizeFps(fps)));
    }

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage target = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D g = target.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return target;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
