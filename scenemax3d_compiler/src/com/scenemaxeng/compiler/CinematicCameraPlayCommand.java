package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class CinematicCameraPlayCommand extends VariableActionStatement {

    public SceneMaxParser.Logical_expressionContext xExpr;
    public SceneMaxParser.Logical_expressionContext yExpr;
    public SceneMaxParser.Logical_expressionContext zExpr;
    public EntityPos entityPos;
    public PositionStatement posStatement;
    public String lookAtTargetVar;
    public SceneMaxParser.Logical_expressionContext lookAtXExpr;
    public SceneMaxParser.Logical_expressionContext lookAtYExpr;
    public SceneMaxParser.Logical_expressionContext lookAtZExpr;
    public PositionStatement lookAtPosStatement;
    public SceneMaxParser.Logical_expressionContext speedExpr;

    @Override
    public boolean validate(ProgramDef prg) {
        this.varDef = prg.getVar(targetVar);
        checkVariableExistsError();
        if (this.varDef == null) {
            return false;
        }
        if (this.varDef.varType != VariableDef.VAR_TYPE_CINEMATIC_CAMERA) {
            this.lastError = "Object '" + targetVar + "' is not a cinematic camera";
            return false;
        }
        return true;
    }
}
