package com.scenemax.desktop;

import java.io.File;

public class ScriptPathNode {
    private final String path;
    private final String name;
    public boolean isFolder = false;

    public ScriptPathNode(String path, String name) {
        this.path=path;
        this.name=name;
    }

    public String getPath() {
        return this.path;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
