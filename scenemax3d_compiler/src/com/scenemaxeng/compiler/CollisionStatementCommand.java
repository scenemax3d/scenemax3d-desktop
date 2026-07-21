package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

import java.util.ArrayList;
import java.util.List;

public class CollisionStatementCommand extends ActionStatementBase {

    public DoBlockCommand doBlock;
    public List<VariableDef> sourceEntities = new ArrayList<>();
    public List<String> sourceJoints = new ArrayList<>();
    public List<CollisionEndpoint> sourceEndpoints = new ArrayList<>();
    public VariableDef destEntity;
    public String destJoint ="";
    public CollisionEndpoint destEndpoint;
    public SceneMaxParser.Logical_expressionContext goExpr;

    public static class CollisionEndpoint {
        public VariableDef entity;
        public String joint = "";
        public boolean equippedWeaponCollider;
        public String ownerVarName = "";
        public VariableDef ownerVarDef;
        public int ownerVarLine;
        public String colliderName = "";
        public boolean networkEntity;
        public String networkObjectName = "";
    }
}
