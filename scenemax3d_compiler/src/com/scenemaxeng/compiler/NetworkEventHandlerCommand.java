package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class NetworkEventHandlerCommand extends ActionStatementBase {
    public SceneMaxParser.Logical_expressionContext eventNameExpr;
    public SceneMaxParser.Logical_expressionContext serverIntervalSecondsExpr;
    public String messageParamName;
    public SceneMaxParser.Logical_expressionContext goExpr;
    public boolean useGoExprEveryIteration;
    public DoBlockCommand doBlock;
}
