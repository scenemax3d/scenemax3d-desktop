package com.scenemax.desktop;

import com.jcraft.jsch.SftpProgressMonitor;

public class SFTPProgressMonitor implements SftpProgressMonitor{
    IMonitor monitor;
    long count=0;
    long max=0;

    public SFTPProgressMonitor(IMonitor m) {
        this.monitor=m;
    }

    public void init(int op, String src, String dest, long max){
        this.max=max;

        count=0;
        percent=-1;
        monitor.setProgress((int)this.count);

    }
    private long percent=-1;

    public boolean count(long count){
        this.count+=count;

        if(percent>=this.count*100/max){ return true; }
        percent=this.count*100/max;

        monitor.setNote("Completed "+this.count+"("+percent+"%) out of "+max+".");
        monitor.setProgress((int)percent);

        return true;
    }
    public void end(){
        monitor.onEnd();
    }
}
