package com.scenemax.designer.video;

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
        if (file == null || !file.isFile()) {
            publishStatus("Choose a video file to import.");
            return;
        }

        publishStatus("Video playback preview runs in the runtime projector.");

        while (running) {
            sleep(1000L);
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

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
