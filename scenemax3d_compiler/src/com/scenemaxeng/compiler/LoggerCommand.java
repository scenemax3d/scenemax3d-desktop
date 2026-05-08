package com.scenemaxeng.compiler;

import org.antlr.v4.runtime.ParserRuleContext;

public class LoggerCommand extends ActionStatementBase {

    public static final String INFO = "info";
    public static final String DEBUG = "debug";
    public static final String ERROR = "error";

    public String level = INFO;
    public ParserRuleContext message;
}
