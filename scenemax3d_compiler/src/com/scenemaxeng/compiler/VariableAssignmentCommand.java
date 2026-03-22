package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class VariableAssignmentCommand extends ActionStatementBase {
    public List<VariableDef> vars = new ArrayList<>();
    public HashMap<VariableDef, SceneMaxParser.Logical_expressionContext> arrayIndexes = new HashMap<>();
    public List<SceneMaxParser.Logical_expressionContext> values = new ArrayList<>();
    public List<SceneMaxParser.Logical_expressionContext> array;
    public boolean triggeredByDeclaration;
}
