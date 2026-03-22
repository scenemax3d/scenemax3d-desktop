package com.scenemaxeng.common.types;

import org.json.JSONObject;

public class ResourceSetup {

    public final String name;
    private final boolean isJ3o;
    public String path = "";
    public float scaleX=0.03f;
    public float scaleY=0.03f;
    public float scaleZ=0.03f;
    public float localTranslationX;
    public float localTranslationY=-5f;
    public float localTranslationZ;
    public float rotateY=0;
    public float stepHeight;
    public float capsuleHeight;
    public float capsuleRadius;
    public float calibrateZ;
    public float calibrateY;
    public float calibrateX;

    public String chassis;
    public float stiffness=120.0f;
    public float compression=0.2f;
    public float damping=0.3f;
    public float mass=400f;

    public String chassisMaterial;
    public String wheelModel;
    public String rearWheelModel;

    public String wheelMaterial;
    public SceneMaxWheel frontWheel;
    public SceneMaxWheel backWheel;
    public SceneMaxGearBox gearBox;
    public SceneMaxEngine engine;
    public boolean isVehicle;
    public String horn;
    public float localScale=1;

    private String jsonBUff;


    public ResourceSetup(String name,String path, float scaleX, float scaleY, float scaleZ,
                         float localTranslationX, float localTranslationY, float localTranslationZ, float rotateY) {

        this.name=name;
        this.path=path;
        this.scaleX=scaleX;
        this.scaleY=scaleY;
        this.scaleZ=scaleZ;
        this.localTranslationX=localTranslationX;
        this.localTranslationY=localTranslationY;
        this.localTranslationZ=localTranslationZ;
        this.rotateY=rotateY;
        this.isJ3o = path.endsWith(".j3o");
    }

    public void setJsonBuffer(String buff) {
        this.jsonBUff = buff;
    }

    public JSONObject toJson() {
        JSONObject j = new JSONObject(jsonBUff);

        return j;
    }

    public boolean isJ3O() {
        return this.isJ3o;
    }
}
