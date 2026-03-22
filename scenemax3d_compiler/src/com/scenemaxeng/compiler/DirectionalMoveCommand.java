package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class DirectionalMoveCommand extends VariableActionStatement{

    public static final int FORWARD = 10;
    public static final int BACKWARD = 20;
    public static final int LEFT = 30;
    public static final int RIGHT = 40;
    public int direction;
    public SceneMaxParser.Logical_expressionContext distanceExpr;
    public SceneMaxParser.Logical_expressionContext timeExpr;
    public SceneMaxParser.Loop_exprContext loopExpr;
}
