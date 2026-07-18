package com.scenemax.desktop;

import org.json.JSONObject;

public class SceneMaxProject {

    public static final int DEFAULT_MULTIPLAYER_PORT = 9001;

    public String selectedParent;
    public String selectedNode;
    public String itchGamePage;
    public String itchButlerPath;
    public String itchWindowsChannel;
    public String itchLinuxChannel;
    public String itchMacChannel;
    public String multiplayerServerIp = "127.0.0.1";
    public int multiplayerServerPort = DEFAULT_MULTIPLAYER_PORT;
    public String multiplayerDeployOs = "Windows";
    public String multiplayerPassword = "";
    public String projectGuid = "";
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
        obj.put("itch_windows_channel", this.itchWindowsChannel == null ? "" : this.itchWindowsChannel);
        obj.put("itch_linux_channel", this.itchLinuxChannel == null ? "" : this.itchLinuxChannel);
        obj.put("itch_mac_channel", this.itchMacChannel == null ? "" : this.itchMacChannel);
        obj.put("multiplayer_server_ip", this.multiplayerServerIp == null ? "" : this.multiplayerServerIp);
        obj.put("multiplayer_server_port", this.multiplayerServerPort <= 0 ? DEFAULT_MULTIPLAYER_PORT : this.multiplayerServerPort);
        obj.put("multiplayer_deploy_os", this.multiplayerDeployOs == null ? "Windows" : this.multiplayerDeployOs);
        obj.put("project_guid", this.projectGuid == null ? "" : this.projectGuid);
        obj.put("projectGuid", this.projectGuid == null ? "" : this.projectGuid);

        return obj;
    }
}
