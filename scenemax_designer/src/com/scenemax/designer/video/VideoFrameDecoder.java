package com.scenemax.designer.video;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

class VideoFrameDecoder implements AutoCloseable, Runnable {
    private final File file;
    private final AtomicReference<BufferedImage> latestFrame = new AtomicReference<>();
    private final Consumer<String> statusCallback;
    private volatile boolean running = true;
    private Thread thread;

    VideoFrameDecoder(File file, Consumer<String> statusCallback) {
        this.file = file;
        this.statusCallback = statusCallback;
    }

    void start() {
        thread = new Thread(this, "SceneMax Video Preview Decoder");
        thread.setDaemon(true);
        thread.start();
    }

    BufferedImage pollFrame() {
        return latestFrame.getAndSet(null);
    }

    @Override
    public void run() {
        while (running) {
            try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(file);
                 Java2DFrameConverter converter = new Java2DFrameConverter()) {
                grabber.start();
                double fps = grabber.getFrameRate();
                long frameDelayMs = fps > 1d ? Math.max(8L, Math.round(1000d / fps)) : 33L;
                publishStatus("Previewing " + file.getName());

                while (running) {
                    Frame frame = grabber.grabImage();
                    if (frame == null) {
                        break;
                    }
                    BufferedImage image = converter.convert(frame);
                    if (image != null) {
                        latestFrame.set(copy(image));
                    }
                    sleep(frameDelayMs);
                }
                grabber.stop();
            } catch (Exception ex) {
                publishStatus("Video preview failed: " + ex.getMessage());
                sleep(1000L);
            }
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

    private void publishStatus(String status) {
        if (statusCallback != null) {
            statusCallback.accept(status);
        }
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
