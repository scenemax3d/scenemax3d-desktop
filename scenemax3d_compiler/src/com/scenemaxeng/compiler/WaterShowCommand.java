package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class WaterShowCommand extends ActionStatementBase{

    public SceneMaxParser.Print_pos_attrContext pos;
    public SceneMaxParser.Water_depth_attrContext depth;
    public SceneMaxParser.Water_plane_size_attrContext size;
    public SceneMaxParser.Water_strength_attrContext strength;
    public SceneMaxParser.Water_wave_speed_attrContext speed;

    // default water attributes
    public float posX=0;
    public float posY=-2.0f;
    public float posZ=0;
    public float depthVal=40;
    public float strengthVal = 0.5f;
    public float speedVal = 0.05f;
    public float sizeWidth = 400;
    public float sizeHeight = 400;


    public String entityPos;
}
