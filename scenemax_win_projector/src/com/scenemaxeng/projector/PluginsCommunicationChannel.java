package com.scenemaxeng.projector;

import com.scenemaxeng.common.types.ISceneMaxPlugin;
import com.scenemaxeng.common.types.PluginBase;

import java.util.ArrayList;
import java.util.List;

public class PluginsCommunicationChannel extends PluginBase {

    private final SceneMaxApp app;
    private List<ISceneMaxPlugin> subscribers = new ArrayList<>();

    public PluginsCommunicationChannel(SceneMaxApp app) {
        this.app = app;
    }

    @Override
    public int run(Object... args) {
        String command = (String)args[0];
        this.app.runPartialCode(command, null, false);
        return 0;
    }

    @Override
    public int stop(Object... args)
    {
        for (ISceneMaxPlugin client : this.subscribers) {
            client.stop();
        }
        return 0;
    }

    @Override
    public int registerObserver(ISceneMaxPlugin observer) {
        this.subscribers.add(observer);
        return 0;
    }

}
