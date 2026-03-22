package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class EntityPos {
    public SceneMaxParser.Logical_expressionContext posX=null;
    public SceneMaxParser.Logical_expressionContext posY=null;
    public SceneMaxParser.Logical_expressionContext posZ=null;

    public String entityName;
    public String entityJointName;

    public EntityPos() {}
    public EntityPos(SceneMaxParser.Logical_expressionContext posX, SceneMaxParser.Logical_expressionContext posY, SceneMaxParser.Logical_expressionContext posZ) {
        this.posX=posX;
        this.posY=posY;
        this.posZ=posZ;
    }
}
