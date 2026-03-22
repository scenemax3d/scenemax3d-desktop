package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class TerrainCommand extends ActionStatementBase{

    public static final int ACTION_SHOW = 10;
    public static final int ACTION_HIDE = 20;

    public int action;
    public SceneMaxParser.Logical_expressionContext terrainNameExprCtx;

}
