package com.scenemaxeng.common.types;

import org.json.JSONObject;

public class TerrainResource {
    public final String name;
    public final String alphaMap;
    public final String redTex;
    public final String greenTex;
    public final String blueTex;
    public final String heightMap;
    public final JSONObject pos;
    public final JSONObject scale;

    public String buff;

    public TerrainResource(String name, String alphaMap, String redTex, String greenTex, String blueTex, String heightMap, JSONObject pos, JSONObject scale) {
        this.name=name;
        this.alphaMap=alphaMap;
        this.redTex=redTex;
        this.greenTex=greenTex;
        this.blueTex=blueTex;
        this.heightMap=heightMap;
        this.pos=pos;
        this.scale=scale;
    }


}
