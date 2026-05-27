package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class PhysicsMotionCommand extends ActionStatementBase {

    public static final int ACTION_THROW = 1;
    public static final int ACTION_IMPULSE = 2;
    public static final int ACTION_FORCE = 3;
    public static final int ACTION_VELOCITY = 4;
    public static final int ACTION_ANGULAR_VELOCITY = 5;
    public static final int ACTION_TORQUE = 6;
    public static final int ACTION_STOP = 7;

    public static final int TARGET_DIRECTION = 1;
    public static final int TARGET_TOWARD = 2;
    public static final int TARGET_AT = 3;
    public static final int TARGET_VECTOR = 4;

    public int action;
    public int targetMode;
    public int direction = -1;

    public String targetEntity;
    public PositionStatement targetPositionStatement;

    public SceneMaxParser.Logical_expressionContext xExpr;
    public SceneMaxParser.Logical_expressionContext yExpr;
    public SceneMaxParser.Logical_expressionContext zExpr;
    public SceneMaxParser.Logical_expressionContext powerExpr;
    public SceneMaxParser.Logical_expressionContext angleExpr;
    public SceneMaxParser.Logical_expressionContext durationExpr;

    public SceneMaxParser.Logical_expressionContext spinXExpr;
    public SceneMaxParser.Logical_expressionContext spinYExpr;
    public SceneMaxParser.Logical_expressionContext spinZExpr;

    public String arcMode;
    public SceneMaxParser.Logical_expressionContext arcExpr;
    public boolean impulseMode;

    @Override
    public boolean validate(ProgramDef prg) {
        this.varDef = prg.getVar(targetVar);
        if (this.varDef == null) {
            return false;
        }

        if (targetEntity != null && prg.getVar(targetEntity) == null) {
            lastError = "physics target '" + targetEntity + "' not exists";
            return false;
        }

        if (targetPositionStatement != null && targetPositionStatement.startEntity != null
                && prg.getVar(targetPositionStatement.startEntity) == null) {
            lastError = "physics target '" + targetPositionStatement.startEntity + "' not exists";
            return false;
        }

        return true;
    }
}
