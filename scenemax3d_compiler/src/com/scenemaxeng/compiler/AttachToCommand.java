package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class AttachToCommand extends VariableActionStatement {


    public String entityNameToAttach;
    public SceneMaxParser.Logical_expressionContext xExpr;
    public SceneMaxParser.Logical_expressionContext yExpr;
    public SceneMaxParser.Logical_expressionContext zExpr;
    public SceneMaxParser.Logical_expressionContext rxExpr;
    public SceneMaxParser.Logical_expressionContext ryExpr;
    public SceneMaxParser.Logical_expressionContext rzExpr;

    public String entityRot;
    public String jointName;
    public String sourceJointName;
}
