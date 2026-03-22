package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;
import org.antlr.v4.runtime.ParserRuleContext;

import java.util.ArrayList;
import java.util.List;

public class DoBlockCommand extends ActionStatementBase {

    public List<String> inParams = new ArrayList<>();
    public ProgramDef prg = null;
    public String amount;
    public String loopType;
    public ParserRuleContext amountExpr;
    public boolean isReturnPoint; // mark this block as return point for "return" command
    public boolean isSecondLevelReturnPoint;
    public SceneMaxParser.Logical_expressionContext goExpr;
    public SceneMaxParser.Logical_expressionContext loopExpr;
    public boolean useGoExprEveryIteration = false;
    public Object creatorScope;

}
