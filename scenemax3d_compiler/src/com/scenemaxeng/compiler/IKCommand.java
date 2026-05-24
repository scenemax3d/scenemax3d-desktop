package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class IKCommand extends ActionStatementBase {
    public static final int ACTION_APPLY = 1;
    public static final int ACTION_REMOVE = 2;
    public static final int ACTION_SET_TARGET = 3;
    public static final int ACTION_SET_WEIGHT = 4;
    public static final int ACTION_SET_BLEND = 5;
    public static final int ACTION_PLAY = 6;
    public static final int ACTION_STOP = 7;

    public int action;
    public String ownerVarName;
    public String layerId;
    public SceneMaxParser.Logical_expressionContext ikNameExpr;
    public EntityPos targetEntityPos;
    public SceneMaxParser.Logical_expressionContext weightExpr;
    public SceneMaxParser.Logical_expressionContext blendExpr;
}
