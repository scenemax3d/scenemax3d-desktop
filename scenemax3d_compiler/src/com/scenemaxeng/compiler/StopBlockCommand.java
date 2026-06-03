package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class StopBlockCommand extends ActionStatementBase {

    public boolean returnAction = false;
    public SceneMaxParser.Logical_expressionContext returnExpr;
}
