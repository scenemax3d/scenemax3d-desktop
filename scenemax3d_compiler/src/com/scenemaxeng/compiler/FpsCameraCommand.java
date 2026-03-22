package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class FpsCameraCommand extends ActionStatementBase {

    public static final int START = 10;
    public static final int STOP = 20;

    public int command;
    public String cameraType;
    public SceneMaxParser.Logical_expressionContext offsetXExpr;
    public SceneMaxParser.Logical_expressionContext offsetYExpr;
    public SceneMaxParser.Logical_expressionContext offsetZExpr;
    public SceneMaxParser.Logical_expressionContext offsetRXExpr;
    public SceneMaxParser.Logical_expressionContext offsetRYExpr;
    public SceneMaxParser.Logical_expressionContext offsetRZExpr;
    public SceneMaxParser.Logical_expressionContext dampingExpr;



}
