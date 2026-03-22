package com.scenemaxeng.plugins.ide;

import okhttp3.Callback;
import org.json.JSONObject;

public class SceneMaxEngRestApi {
    public static void post(String code, Callback callback) {

        JSONObject command = new JSONObject();
        command.put("code", code);
        HttpClient.post("http://127.0.0.1:8080/run", command.toString(), callback);
    }
}
