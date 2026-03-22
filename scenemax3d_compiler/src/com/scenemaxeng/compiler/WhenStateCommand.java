package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

import java.util.ArrayList;
import java.util.List;

public class WhenStateCommand extends ActionStatementBase {

    public List<SceneMaxParser.Logical_expressionContext> whenExpr = new ArrayList<>();
    public DoBlockCommand doBlock;
}
