package com.scenemaxeng.compiler;

public class PluginActionCommand extends ActionStatementBase {

    public enum Actions {
        Start,
        Stop
    }

    public String pluginName;
    public Actions action;

}
