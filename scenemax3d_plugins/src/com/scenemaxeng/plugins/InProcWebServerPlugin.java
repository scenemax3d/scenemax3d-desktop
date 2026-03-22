package com.scenemaxeng.plugins;

import com.scenemaxeng.common.types.PluginBase;

import java.io.IOException;

public class InProcWebServerPlugin extends PluginBase {

    private static InProcWebServer server;

    @Override
    public int start(Object... args) {
        if(server != null) {
            System.out.println("in-proc web server already exists. aborting server.start operation");
            return 0;
        }

        int port = args.length > 0 ? (int) args[0] : 8080;
        String hostingProcessDesc = args.length > 0 ? (String) args[1] : "SceneMax3D";
        try {
            server = new InProcWebServer(this.observer, port, hostingProcessDesc);
        } catch (IOException e) {
            e.printStackTrace();
            return 1; // fail
        }

        return 0;
    }

    @Override
    public int stop(Object... args) {
        // when scenemax app is destroyed, it will send a stop message to this plugin
        if(server != null) {
            server.stop();
            server = null;
        }

        return 0;
    }

}
