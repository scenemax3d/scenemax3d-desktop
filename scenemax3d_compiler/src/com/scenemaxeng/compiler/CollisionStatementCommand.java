package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

import java.util.ArrayList;
import java.util.List;

public class CollisionStatementCommand extends ActionStatementBase {

    public DoBlockCommand doBlock;
    public List<VariableDef> sourceEntities = new ArrayList<>();
    public List<String> sourceJoints = new ArrayList<>();
    public VariableDef destEntity;
    public String destJoint ="";
    public SceneMaxParser.Logical_expressionContext goExpr;
}
