package com.scenemax.desktop;

import org.json.JSONObject;

public interface IServerEvents {
    void onServerResponse(String event, JSONObject data, boolean ownSocket, boolean targettingMe);
}
