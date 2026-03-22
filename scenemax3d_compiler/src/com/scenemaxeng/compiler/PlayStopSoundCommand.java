package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class PlayStopSoundCommand extends ActionStatementBase{

    public boolean stop = false;
    public String sound;
    public boolean loop;
    public SceneMaxParser.Logical_expressionContext volumeExpr;
    public SceneMaxParser.Logical_expressionContext soundExpr;
}

