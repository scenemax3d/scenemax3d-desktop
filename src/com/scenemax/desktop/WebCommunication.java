package com.scenemax.desktop;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class WebCommunication {

    List<IServerEvents> _observers = new ArrayList<>();
    Socket socket = null;
    private boolean joinedRoom = false;
    static WebCommunication _instance = null;
    private JSONArray members;
    private RoomMember thisMember;
    private JSONObject lastJoinRoomRequestPayload;
    private boolean joinOnReconnect;


    public WebCommunication() {

    }

    public void connect() {

        if (socket != null && socket.connected()) {
            return;
        }

        try {
            String server = AppDB.getInstance().getParam("classrooms_server");
            if (server == null || server.length() == 0 || server.toLowerCase().equals("default")) {
                server = LicenseService.getClassroomServer();//  defaultHost;
            } else {
                if (!server.startsWith("http")) {
                    server = "http://" + server + ":3060";
                }
            }

            socket = IO.socket(server);// "http://45.55.61.171:3060"
            socket.connect();

        } catch (URISyntaxException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(final Object... args) {

                System.out.println(Socket.EVENT_CONNECT + ":" + socket.id());
                broadcast("connected", null);

                // re-join the last room the user joined before he was disconnected
                if(joinOnReconnect && lastJoinRoomRequestPayload!=null) {
                    String room = lastJoinRoomRequestPayload.getString("room");
                    String name = lastJoinRoomRequestPayload.getString("name");
                    String pwd = lastJoinRoomRequestPayload.getString("pwd");
                    joinRoom(room,name,pwd);
                }

            }
        });

        socket.on(Socket.EVENT_CONNECTING, new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                System.out.println(Socket.EVENT_CONNECTING);
            }
        });

        socket.on(Socket.EVENT_CONNECT_TIMEOUT, new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                System.out.println(Socket.EVENT_CONNECT_TIMEOUT);
            }
        });

        socket.on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                System.out.println(Socket.EVENT_DISCONNECT);

                if(thisMember!=null) {

                    String userId = thisMember.getUserId();
                    removeThisMemberFromRoomList();

                    // Notify application that this member has left the room
                    joinOnReconnect=true;
                    JSONObject data = new JSONObject();
                    data.put("members", members);
                    data.put("ownSocket", true);
                    broadcast("user-left", data);
                }

            }
        });

        socket.on(Socket.EVENT_CONNECT_ERROR, new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                System.out.println(Socket.EVENT_CONNECT_ERROR);
                broadcast("error-connect", null);
            }
        });


        /////////////////////////////////////////////////////////////////

        socket.on("pull-request", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                try {
                    JSONObject obj = (JSONObject) args[0];
                    broadcast("pull-request", obj);
                } catch (Exception e) {

                }
            }

        });

        socket.on("push-request", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                try {
                    JSONObject obj = (JSONObject) args[0];
                    broadcast("push-request", obj);
                } catch (Exception e) {

                }
            }

        });

        socket.on("create-room-ok", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                try {
                    JSONObject obj = (JSONObject) args[0];

                } catch (Exception e) {

                }
            }

        });


        socket.on("delete-room-ok", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                try {
                    JSONObject obj = (JSONObject) args[0];

                } catch (Exception e) {

                }
            }

        });

        socket.on("create-room-error", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                try {
                    JSONObject obj = (JSONObject) args[0];

                } catch (Exception e) {

                }
            }

        });

        socket.on("delete-room-error", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                try {
                    JSONObject obj = (JSONObject) args[0];

                } catch (Exception e) {

                }
            }

        });

        socket.on("answer-accepted", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                try {
                    JSONObject obj = (JSONObject) args[0];
                    broadcast("answer-accepted", obj);
                } catch (Exception e) {

                }
            }

        });

        socket.on("join-room-error", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                try {
                    JSONObject obj = (JSONObject) args[0];
                    broadcast("join-room-error", obj);
                } catch (Exception e) {

                }
            }

        });


        socket.on("leave-room-error", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                try {
                    JSONObject obj = (JSONObject) args[0];
                    broadcast("leave-room-error", obj);
                } catch (Exception e) {

                }
            }

        });

        socket.on("answered", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                try {
                    JSONObject obj = (JSONObject) args[0];
                    broadcast("answered", obj);
                } catch (Exception e) {

                }
            }

        });

        socket.on("reviewing", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                try {
                    JSONObject obj = (JSONObject) args[0];
                    broadcast("reviewing", obj);
                } catch (Exception e) {

                }
            }

        });

        socket.on("question", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                try {
                    JSONObject obj = (JSONObject) args[0];
                    broadcast("question", obj);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        });

        socket.on("ask-error", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                try {
                    JSONObject obj = (JSONObject) args[0];

                } catch (Exception e) {

                }
            }

        });

        socket.on("user-left", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                try {

                    JSONObject data = (JSONObject) args[0];
                    boolean isOwnSocket = data != null && data.has("sourceId") && data.getString("sourceId").endsWith(socket.id());
                    if (isOwnSocket) {
                        joinedRoom = false;
                        thisMember = null;
                    }

                    members = data.getJSONArray("members");
                    broadcast("user-left", data);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        });


        socket.on("user-reconnected", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                try {

                    JSONObject data = (JSONObject) args[0];
                    boolean isOwnSocket = data != null && data.has("sourceId") && data.getString("sourceId").endsWith(socket.id());
                    members = data.getJSONArray("members");


                } catch (Exception e) {
                    e.printStackTrace();
                }

            }

        });

        socket.on("user-joined", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                try {

                    JSONObject data = (JSONObject) args[0];
                    boolean isOwnSocket = data != null && data.has("sourceId") && data.getString("sourceId").endsWith(socket.id());
                    members = data.getJSONArray("members");

                    if (isOwnSocket) {
                        joinedRoom = true;
                        thisMember = findThisMember();
                    }

                    broadcast("user-joined", data);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        });


    }

    protected void removeThisMemberFromRoomList() {
        if(members!=null && thisMember!=null) {
            for(int i=0;i<members.length();++i) {
                JSONObject m = members.getJSONObject(i);
                if(m==thisMember.getRawData()) {
                    members.remove(i);
                    break;
                }
            }
        }

        joinedRoom = false;
        thisMember = null;
    }

    private void broadcast(String event, JSONObject data) {

        boolean isOwnSocket = false;
        boolean targettingMe = false;

        try {

            isOwnSocket = data != null && (data.has("ownSocket") || (data.has("sourceId") && data.getString("sourceId").endsWith(socket.id())));
            targettingMe = data != null && data.has("userId") && data.getString("userId").endsWith(socket.id());

        } catch (JSONException e) {
            e.printStackTrace();
        }

        for (int i = 0; i < _observers.size(); ++i) {
            _observers.get(i).onServerResponse(event, data, isOwnSocket, targettingMe);
        }

    }

    public static WebCommunication getInstance() {
        if (_instance == null) {
            _instance = new WebCommunication();
        }

        return _instance;
    }

    public void createRoom() {

    }

    public void joinRoom(String room, String nickName, String pwd) {
        JSONObject js = createJSON();
        try {
            js.put("room", room);
            js.put("name", nickName);
            js.put("pwd", pwd);
            js.put("station",Util.getStationId());
            lastJoinRoomRequestPayload = js;

            socket.emit("join-room", js);

        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void deleteRoom() {

    }

    public void leaveRoom(String room) {

        JSONObject js = createJSON();
        try {
            js.put("room", room);
            js.put("station",Util.getStationId());
            socket.emit("leave-room", js);
            lastJoinRoomRequestPayload=null;

        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    public void askQuestion(String room, String question, JSONArray script, String folderName, JSONObject files) {

        JSONObject js = createJSON();
        try {
            js.put("room", room);
            js.put("question", question);
            js.put("script", script);
            js.put("files", files);
            js.put("folder", folderName);
            js.put("station",Util.getStationId());
            socket.emit("ask", js);

        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    public void reviewQuestion() {

    }

    public void sendScriptFolders(String room, String userId, String userName, JSONObject folders) {

        JSONObject js = createJSON();
        try {
            js.put("room", room);
            js.put("userId", userId); // which user to send this message
            js.put("request", "view_user_folders");
            js.put("sendingUserName", userName);
            js.put("folders", folders);
            js.put("station",Util.getStationId());
            socket.emit("push-request", js);

        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }


    public void sendSingleFile(String room, String userId, String folderName, JSONObject files) {
        JSONObject js = createJSON();
        try {
            js.put("room", room);
            js.put("userId", userId); // which user to send this message
            js.put("request", "override_file");
            js.put("folderName", folderName);
            js.put("files", files);
            js.put("station",Util.getStationId());

            socket.emit("push-request", js);

        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }


    public void sendFolderContent(String room, String userId, String folderName, JSONObject files) {
        JSONObject js = createJSON();
        try {
            js.put("room", room);
            js.put("userId", userId); // which user to send this message
            js.put("request", "view_folder");
            js.put("folderName", folderName);
            js.put("files", files);
            js.put("station",Util.getStationId());

            socket.emit("push-request", js);

        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void sendRoomCommand(String room, JSONObject rc) {
        JSONObject js = createJSON();
        try {
            js.put("room", room);
            js.put("request", "room-command");
            js.put("command", rc);
            js.put("station",Util.getStationId());

            socket.emit("push-request", js);

        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void askForUserFolders(String room, RoomMember rm) {
        JSONObject js = createJSON();
        try {
            js.put("room", room);
            js.put("userId", rm.getUserId()); // which user to send this message
            js.put("request", "view_user_folders");
            js.put("station",Util.getStationId());

            socket.emit("pull-request", js);

        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void askForUserFolderContent(String room, RoomMember rm, String folderName) {
        JSONObject js = createJSON();
        try {
            js.put("room", room);
            js.put("userId", rm.getUserId()); // which user to send this message
            //js.put("userName",rm.toString());
            js.put("request", "view_folder");
            js.put("folderName", folderName);
            js.put("station",Util.getStationId());

            socket.emit("pull-request", js);

        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void answerQuestion(String room, String userId, String answer, String script, String folderName, String fileName) {
        JSONObject js = createJSON();
        try {
            js.put("room", room);
            js.put("userId", userId); // which user to send this message
            js.put("answer", answer);
            js.put("answerCode", script);

            js.put("folder", folderName);
            js.put("file", fileName);
            js.put("station",Util.getStationId());

            socket.emit("answer-question", js);

        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void acceptAnswer(String room) {
        JSONObject js = createJSON();
        try {
            js.put("room", room);
            js.put("station",Util.getStationId());
            //js.put("userId", userId);
            socket.emit("accept-answer", js);

        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private JSONObject createJSON() {
        return createJSON("{}");

    }

    private JSONObject createJSON(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            return obj;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void subscribe(IServerEvents observer) {
        if (!_observers.contains(observer)) {
            _observers.add(observer);
        }
    }

    public void unsubscribe(IServerEvents observer) {
        if (_observers.contains(observer)) {
            _observers.remove(observer);
        }
    }

    public boolean isJoinedToRoom() {
        return joinedRoom;
    }

    public RoomMember getRoomMemberById(String id) {
        if (members != null && members.length() > 0) {

            for (int i = 0; i < members.length(); ++i) {
                RoomMember m = new RoomMember(members.getJSONObject(i));
                if(m.getUserId().equals(id)) {
                    return m;
                }
            }

        }

        return null;
    }

    public List<RoomMember> getRoomMembers() {

        List<RoomMember> rms = new ArrayList();

        if (members != null && members.length() > 0) {

            for (int i = 0; i < members.length(); ++i) {
                RoomMember m = new RoomMember(members.getJSONObject(i));
                rms.add(m);
            }

        }

        return rms;

    }

    private RoomMember findThisMember() {

        RoomMember rm = null;

        if (members != null && members.length() > 0) {

            // new way to find - by station id
            for (int i = 0; i < members.length(); ++i) {

                RoomMember m = new RoomMember(members.getJSONObject(i));
                String stationId = m.getStationId();
                if (stationId!=null && stationId.equals(Util.getStationId())) {
                    rm = m;
                    return rm;
                }

            }

            // old way to find - by socket id
            for (int i = 0; i < members.length(); ++i) {

                RoomMember m = new RoomMember(members.getJSONObject(i));
                if (m.getUserId().equals(socket.id())) {
                    rm = m;
                    return rm;
                }

            }
        }

        return rm;
    }


    public void clearRoomMembers() {
        members = null;
    }

    public RoomMember getThisMember() {
        return thisMember;
    }


}
