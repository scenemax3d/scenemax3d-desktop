package com.scenemax.desktop;

import javax.swing.*;

public class DownloadFileTask extends SwingWorker<Integer, String> {

    private final Runnable done;
    private String url;
    public String error;
    public String message;

    public DownloadFileTask(String url, Runnable done) {
        this.done=done;
        this.url=url;
    }

    @Override
    protected Integer doInBackground() throws Exception {

        final String path = Util.downloadFile(url);
        if(path!=null && !path.startsWith("error")) {
            new ImportProgramZipFileTask(path, new Callback() {
                @Override
                public void run(Object res) {

                }
            }).execute();
        } else {
            this.error=path;
        }
        return null;
    }


    @Override
    public void done() {

        done.run();
    }

}
