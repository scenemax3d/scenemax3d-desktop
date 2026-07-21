package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class NetworkEventHandlerCommand extends ActionStatementBase {
    public SceneMaxParser.Logical_expressionContext eventNameExpr;
    public DoBlockCommand doBlock;
}
