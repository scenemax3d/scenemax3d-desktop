package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class FunctionBlockDef extends ActionStatementBase {

    public ProgramDef prg = null;
    public DoBlockCommand doBlock;
    public String name;

    public SceneMaxParser.Logical_expressionContext goExpr;
}

