package com.scenemaxeng.plugins.ide;

import okhttp3.*;

import java.io.IOException;

public class HttpClient {

    public static void post(String url, String bodyJson, Callback callback) {

        OkHttpClient client = new OkHttpClient();

        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(bodyJson, JSON);

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        client.newCall(request).enqueue(callback);
    }
}
