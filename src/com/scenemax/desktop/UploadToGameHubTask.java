package com.scenemax.desktop;

import com.jcraft.jsch.SftpProgressMonitor;

import javax.swing.*;


public class UploadToGameHubTask extends SwingWorker<Boolean, String> {

    private final String path;
    private final Callback done;
    private IMonitor monitor;

    public UploadToGameHubTask(String path, IMonitor monitor, Callback done) {
        this.path=path;
        this.done=done;
        this.monitor=monitor;
    }

    @Override
    protected Boolean doInBackground() throws Exception {

        return new BackBlaze(monitor).upload(this.path);

//
//        return SFTP.sftpUpload(LicenseService.getSftpServer(),22,LicenseService.getSftpServerServerUser(),
//                LicenseService.getSftpServerServerPwd(),this.path,monitor);

    }

    @Override
    protected void done() {
        if(monitor!=null) {
            monitor.onEnd();
        }

        done.run(null);
    }

}