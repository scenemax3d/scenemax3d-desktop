package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class ChannelDrawCommand extends ActionStatementBase {

    public SceneMaxParser.Logical_expressionContext posXExpr;
    public SceneMaxParser.Logical_expressionContext posYExpr;

    public float posXVal;
    public float posYVal;

    public String channelName;
    public String resourceName;
    public SceneMaxParser.Logical_expressionContext frameNumExpr;
    public SceneMaxParser.Logical_expressionContext widthExpr;
    public SceneMaxParser.Logical_expressionContext heightExpr;
    public int heightVal;
    public int widthVal;
    public int frameNumVal;
}
