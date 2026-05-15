package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class WeaponCommand extends ActionStatementBase {
    public static final int ACTION_EQUIP = 1;
    public static final int ACTION_UNEQUIP = 2;
    public static final int ACTION_SET_POSTURE = 3;
    public static final int ACTION_DETACH = 4;
    public static final int ACTION_ATTACH = 5;

    public int action;
    public String ownerVarName;
    public SceneMaxParser.Logical_expressionContext weaponNameExpr;
    public SceneMaxParser.Logical_expressionContext postureNameExpr;
}
