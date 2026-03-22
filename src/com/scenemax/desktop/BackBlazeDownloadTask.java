package com.scenemax.desktop;



import javax.swing.*;
import java.io.File;

public class BackBlazeDownloadTask extends SwingWorker<Object, Void> {

    private File target=null;
    private String src=null;
    private Runnable done=null;
    private IMonitor monitor=null;

    public BackBlazeDownloadTask(String src, File target, IMonitor monitor, Runnable done) {
        this.target=target;
        this.src=src;
        this.done=done;
        this.monitor=monitor;
    }

    @Override
    public void done() {
        if(monitor!=null) {
            monitor.onEnd();
        }
        done.run();
    }

    @Override
    protected Object doInBackground() throws Exception {
        new BackBlaze(monitor).download(src,target);
        return null;
    }
}
