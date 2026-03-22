package com.scenemax.desktop;

import javax.swing.*;
import java.nio.file.*;

public class FileWatcherTask extends SwingWorker<Integer, String> {

    private String dir;
    private String file;
    private long lastTimestamp=0;

    public FileWatcherTask(String dir, String fileName) {
        this.dir=dir;
        this.file=fileName;
    }


    @Override
    protected Integer doInBackground() throws Exception {

        WatchService watchService
                = FileSystems.getDefault().newWatchService();

        Path path = Paths.get(this.dir);

        path.register(
                watchService,
                StandardWatchEventKinds.ENTRY_MODIFY);

        WatchKey key;
        while ((key = watchService.take()) != null) {
            for (WatchEvent<?> event : key.pollEvents()) {
                String eventName = event.kind().name();
                String ctx = event.context().toString();

                if(ctx.equals(this.file)) {
                    long delta = System.currentTimeMillis()-lastTimestamp;
                    if(delta>500) {
                        this.firePropertyChange(eventName, ctx, null);
                        lastTimestamp=System.currentTimeMillis();
                    }
                }
            }
            key.reset();
        }

        return 0;
    }


}
