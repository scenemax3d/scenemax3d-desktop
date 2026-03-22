package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

public class HttpCommand extends ActionStatementBase {

    public static final int VERB_TYPE_GET = 0;
    public static final int VERB_TYPE_POST = 1;
    public static final int VERB_TYPE_PUT = 2;

    public int verb;
    public SceneMaxParser.Logical_expressionContext addressExp;
    public SceneMaxParser.Logical_expressionContext bodyExp;

    public String callbackProcName;
}
