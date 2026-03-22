package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class WaitForCommand extends ActionStatementBase {


    public SceneMaxParser.Logical_expressionContext waitForExpr;
    public String inputType;
    public String inputKey;
}
