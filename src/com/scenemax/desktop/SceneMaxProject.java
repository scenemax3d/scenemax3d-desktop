package com.scenemax.desktop;

import org.json.JSONObject;

public class SceneMaxProject {

    public String selectedParent;
    public String selectedNode;
    public String itchGamePage;
    public String itchButlerPath;
    public String itchWindowsChannel;
    public String itchLinuxChannel;
    public String itchMacChannel;
    String name;
    String path;

    public String getResourcesPath() {
        return path + "/resources";
    }

    public String getScriptsPath() {
        return path + "/scripts";
    }

    public JSONObject toJSON() {

        JSONObject obj = new JSONObject();
        obj.put("name",this.name);
        obj.put("selected_parent",this.selectedParent);
        obj.put("selected_node",this.selectedNode);
        obj.put("itch_game_page", this.itchGamePage == null ? "" : this.itchGamePage);
        obj.put("itch_butler_path", this.itchButlerPath == null ? "" : this.itchButlerPath);
        obj.put("itch_windows_channel", this.itchWindowsChannel == null ? "" : this.itchWindowsChannel);
        obj.put("itch_linux_channel", this.itchLinuxChannel == null ? "" : this.itchLinuxChannel);
        obj.put("itch_mac_channel", this.itchMacChannel == null ? "" : this.itchMacChannel);

        return obj;
    }
}
