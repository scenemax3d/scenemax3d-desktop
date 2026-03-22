package com.scenemax.desktop;

import org.json.JSONObject;

public class RoomMember {

    private final JSONObject data;

    public RoomMember(JSONObject data) {
        this.data=data;
    }

    public JSONObject getRawData() {
        return data;
    }

    public String getStationId() {
        if(data.has("station")) {
            return data.getString("station");
        }

        return null;
    }


    public String getUserId() {
        return data.getString("id");
    }

    public boolean isModerator() {
        return data.getBoolean("isModerator");
    }

    public String toString() {
        return data.getString("name");
    }

}
