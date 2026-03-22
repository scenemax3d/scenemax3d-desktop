package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class DirectionVerb {
    public static final int FORWARD = 0;
    public static final int BACKWARD = 1;
    public static final int LEFT = 2;
    public static final int RIGHT = 3;
    public static final int UP = 4;
    public static final int DOWN = 5;

    public int verb;
    public SceneMaxParser.Logical_expressionContext valExp;

}
