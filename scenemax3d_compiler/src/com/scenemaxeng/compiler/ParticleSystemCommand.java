package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class ParticleSystemCommand extends ActionStatementBase{

    public static int FLASH = 10, SHOCK_WAVE = 20, DEBRIS = 30, SMOKE_TRAIL = 40, SPARK = 50, ROUND_SPARK = 60,
            EXPLOSION = 70,
            // New particle system starts at 100
            TIME_ORBIT = 100, FLAME = 110;

    public SceneMaxParser.Print_pos_attrContext pos;
    public SceneMaxParser.Psys_attr_gravityContext gravity;
    public SceneMaxParser.Psys_attr_start_sizeContext startSize;
    public SceneMaxParser.Psys_attr_end_sizeContext endSize;
    public SceneMaxParser.Psys_attr_durationContext time;

    public int type = 0;

    public float posX=0f;
    public float posY=0f;
    public float posZ=0f;

    public float startSizeVal;
    public float endSizeVal;

    public float gravityX=0f;
    public float gravityY=0f;
    public float gravityZ=0f;
    public float timeVal=3.0f;

    public float radiusVal = 0f;

    public SceneMaxParser.Logical_expressionContext radiusValExpr;
    public SceneMaxParser.Logical_expressionContext emissionsPerSecExpr;
    public float emissionsPerSecondVal;
    public SceneMaxParser.Logical_expressionContext particlesPerEmissionExpr;
    public float particlesPerEmissionVal;
    public String attachToEntity;
    public Object attachToEntityVarDef; // RunTimeVarDef

    public EntityPos entityPos;
}
