package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class ForCommand extends ActionStatementBase {

    public VariableAssignmentCommand declareIndexCommand;
    public SceneMaxParser.Stop_conditionContext stopConditionExpr;
    public VariableAssignmentCommand incrementIndexCommand;
    public DoBlockCommand doBlock;
}
