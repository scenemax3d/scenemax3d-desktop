package com.scenemaxeng.plugins;

import com.scenemaxeng.common.types.ISceneMaxPlugin;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class EngineWebSocketController implements ISceneMaxPlugin {

    private Socket socket;
    private ISceneMaxPlugin observer;

    @Override
    public int start(Object... args) {
        String uri = (String) args[0];
        try {
            socket = IO.socket(uri);
            initializeSocket();
        } catch (Exception e) {
            e.printStackTrace();
        }

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

    public int registerObserver(ISceneMaxPlugin observer) {
        this.observer = observer;
        return 0;
    }

    private void initializeSocket() {
        socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                System.out.println("Connected to the server");
            }
        }).on("command", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String command = (String) args[0];
                System.out.println("Received command: " + command);

                // Send the command to the engine for execution
                EngineWebSocketController.this.observer.run(command);
            }
        }).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                System.out.println("Disconnected from the server");
            }
        });

        socket.connect();
    }
}
