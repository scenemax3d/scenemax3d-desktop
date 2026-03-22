package com.scenemaxeng.common.types;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;

public class PluginBase implements ISceneMaxPlugin {
    protected ISceneMaxPlugin observer;

    @Override
    public int start(Object... args) {
        return 0;
    }

    @Override
    public int stop(Object... args) {
        return 0;
    }

    @Override
    public int run(Object... args) {
        return 0;
    }

    @Override
    public int progress(Object... args) {
        return 0;
    }

    @Override
    public int registerObserver(ISceneMaxPlugin observer) {
        this.observer = observer;
        return 0;
    }

}
