package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class ChaseCameraCommand extends VariableActionStatement {

    public static final int CHASE = 10;
    public static final int STOP = 20;

    public int command;
    public boolean trailing=true;
    public SceneMaxParser.Logical_expressionContext rotationSpeedExpr;
    public SceneMaxParser.Logical_expressionContext verticalRotationExpr;
    public SceneMaxParser.Logical_expressionContext horizontalRotationExpr;
    public SceneMaxParser.Logical_expressionContext minDistanceExpr;
    public SceneMaxParser.Logical_expressionContext maxDistanceExpr;
    public boolean havingAttributesExists;
    public Double rotationSpeedVal;
    public Double verticalRotationVal;
    public Double horizontalRotationVal;
    public Double minDistanceVal;
    public Double maxDistanceVal;
}
