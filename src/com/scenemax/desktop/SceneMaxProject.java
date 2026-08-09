package com.scenemax.desktop;

import org.json.JSONObject;

public class SceneMaxProject {

    public static final int DEFAULT_MULTIPLAYER_PORT = 9001;
    public static final String PROJECTOR_CLASSIC = "classic";
    public static final String PROJECTOR_NEXTGEN = "nextgen";
    public static final String PROJECTOR_CLASSIC_LABEL = "Classic - Java / JME3";
    public static final String PROJECTOR_NEXTGEN_LABEL = "NextGen - Rust / Bevy";

    public String selectedParent;
    public String selectedNode;
    public String projectorType = PROJECTOR_CLASSIC;
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
    public long lastActiveAt;
    String name;
    String path;

    public String getResourcesPath() {
        return path + "/resources";
    }

    public String getScriptsPath() {
        return path + "/scripts";
    }

    public boolean isNextGenProjector() {
        return PROJECTOR_NEXTGEN.equals(normalizeProjectorType(projectorType));
    }

    public String getProjectorLabel() {
        return isNextGenProjector() ? PROJECTOR_NEXTGEN_LABEL : PROJECTOR_CLASSIC_LABEL;
    }

    public static String normalizeProjectorType(String value) {
        if (value == null) {
            return PROJECTOR_CLASSIC;
        }
        String normalized = value.trim().toLowerCase();
        if (PROJECTOR_NEXTGEN.equals(normalized)
                || "rust".equals(normalized)
                || "bevy".equals(normalized)
                || "rust_bevy".equals(normalized)
                || "nextgen_rust_bevy".equals(normalized)) {
            return PROJECTOR_NEXTGEN;
        }
        return PROJECTOR_CLASSIC;
    }

    public static String projectorTypeFromLabel(String label) {
        if (label != null && label.toLowerCase().contains("nextgen")) {
            return PROJECTOR_NEXTGEN;
        }
        return PROJECTOR_CLASSIC;
    }

    public JSONObject toJSON() {

        JSONObject obj = new JSONObject();
        obj.put("name",this.name);
        obj.put("selected_parent",this.selectedParent);
        obj.put("selected_node",this.selectedNode);
        obj.put("projector_type", normalizeProjectorType(this.projectorType));
        obj.put("projectorType", normalizeProjectorType(this.projectorType));
        obj.put("itch_game_page", this.itchGamePage == null ? "" : this.itchGamePage);
        obj.put("itch_windows_channel", this.itchWindowsChannel == null ? "" : this.itchWindowsChannel);
        obj.put("itch_linux_channel", this.itchLinuxChannel == null ? "" : this.itchLinuxChannel);
        obj.put("itch_mac_channel", this.itchMacChannel == null ? "" : this.itchMacChannel);
        obj.put("multiplayer_server_ip", this.multiplayerServerIp == null ? "" : this.multiplayerServerIp);
        obj.put("multiplayer_server_port", this.multiplayerServerPort <= 0 ? DEFAULT_MULTIPLAYER_PORT : this.multiplayerServerPort);
        obj.put("multiplayer_deploy_os", this.multiplayerDeployOs == null ? "Windows" : this.multiplayerDeployOs);
        obj.put("project_guid", this.projectGuid == null ? "" : this.projectGuid);
        obj.put("projectGuid", this.projectGuid == null ? "" : this.projectGuid);
        obj.put("lastActiveAt", this.lastActiveAt);

        return obj;
    }
}
