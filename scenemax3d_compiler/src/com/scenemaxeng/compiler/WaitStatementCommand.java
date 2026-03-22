package com.scenemaxeng.compiler;

import org.antlr.v4.runtime.ParserRuleContext;

public class WaitStatementCommand extends ActionStatementBase {

    public ParserRuleContext waitExpr;
    public float explicitWaitTime;

}
