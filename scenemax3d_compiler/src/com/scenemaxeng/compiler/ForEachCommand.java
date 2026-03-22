package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class ForEachCommand extends ActionStatementBase {

    public int entityType;
    public String name;
    public String nameComparator;
    public FunctionBlockDef funcDef;
    public SceneMaxParser.Logical_expressionContext targetCollectionExpr;
}
