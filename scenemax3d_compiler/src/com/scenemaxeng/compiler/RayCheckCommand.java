package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class RayCheckCommand extends ActionStatementBase {


    public String targetGroup;
    public DoBlockCommand doBlock;
    public SceneMaxParser.Logical_expressionContext posX;
    public SceneMaxParser.Logical_expressionContext posY;
    public SceneMaxParser.Logical_expressionContext posZ;
    public String entityPos;
}
