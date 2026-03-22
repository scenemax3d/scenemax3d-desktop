package com.scenemaxeng.common.types;

import java.util.List;

public interface IAppObserver {
    void init();
    void showScriptEditor();
    void onEndCode(List<String> errors);
    void onStartCode();
    void message(int msgType);
    void message(int msgType, Object content);
}
