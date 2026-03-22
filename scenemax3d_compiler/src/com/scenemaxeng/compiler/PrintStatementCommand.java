package com.scenemaxeng.compiler;

import org.antlr.v4.runtime.ParserRuleContext;

public class PrintStatementCommand extends ActionStatementBase {

    public String printChannel;
    public ParserRuleContext text;

    public ParserRuleContext x;
    public ParserRuleContext y;
    public ParserRuleContext z;
    public String color;
    public ParserRuleContext fontSize;
    public boolean append;
    public String font;
}
