package com.scenemaxeng.plugins;

import com.scenemaxeng.common.types.ISceneMaxPlugin;
import fi.iki.elonen.NanoHTTPD;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class InProcWebServer extends NanoHTTPD {

    private final ISceneMaxPlugin host;

    public InProcWebServer(ISceneMaxPlugin host, int port, String hostingProcessDesc) throws IOException {
        super(port);
        start(SOCKET_READ_TIMEOUT, false);
        System.out.println("\nStarting " + hostingProcessDesc + " inproc web server: http://localhost:"+port+"\n");
        this.host = host;
    }

    @Override
    public Response serve(IHTTPSession session) {

        String uri = session.getUri();
        if(uri.startsWith("/run")) {
            try {
                Map<String, String> files = new HashMap<>();
                session.parseBody(files);
                StringBuilder sb = new StringBuilder();
                for (String value : files.values()) {
                    sb.append(value);
                }
                JSONObject obj = new JSONObject(sb.toString());
                new MessageHandler(this.host, obj).run();

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        return newFixedLengthResponse("");

    }
}