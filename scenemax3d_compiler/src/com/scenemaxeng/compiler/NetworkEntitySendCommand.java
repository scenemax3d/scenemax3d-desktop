package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class NetworkEntitySendCommand extends VariableActionStatement {
    public SceneMaxParser.Logical_expressionContext eventNameExpr;
    public SceneMaxParser.Logical_expressionContext messageExpr;
}
