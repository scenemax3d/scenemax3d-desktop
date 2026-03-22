package com.scenemaxeng.plugins;

import com.scenemaxeng.common.types.ISceneMaxPlugin;
import org.json.JSONObject;

public class MessageHandler {
    private final ISceneMaxPlugin host;
    private final JSONObject command;

    public MessageHandler(ISceneMaxPlugin host, JSONObject command) {
        this.host = host;
        this.command = command;
    }

    public int run() {
        String code = command.getString("code");
        this.host.run(code);
        return 0;
    }
}
