package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;
import org.antlr.v4.runtime.ParserRuleContext;

public class ActionCommandAnimate extends VariableActionStatement {
    public String animationName;
    public ParserRuleContext speedExpr;
    public boolean loop;
    public SceneMaxParser.Logical_expressionContext goExpr;
    public boolean isProtected = false;

}
