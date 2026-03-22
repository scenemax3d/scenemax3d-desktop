package com.scenemax.desktop;

import org.json.JSONObject;

public class Question {

    public String asker;
    public String question;
    public String sourceId;
    public String script;
    public JSONObject files;
    public String folder;

    @Override
    public String toString() {
        return asker;
    }
}
