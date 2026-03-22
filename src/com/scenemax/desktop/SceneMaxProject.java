package com.scenemax.desktop;

import org.json.JSONObject;

public class SceneMaxProject {

    public String selectedParent;
    public String selectedNode;
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

        return obj;
    }
}
