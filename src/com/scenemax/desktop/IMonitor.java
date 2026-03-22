package com.scenemax.desktop;

public interface IMonitor {
    void setNote(String note);
    void setProgress(int progress);
    void onEnd();
}
