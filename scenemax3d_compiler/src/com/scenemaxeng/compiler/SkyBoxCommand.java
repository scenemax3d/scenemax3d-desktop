package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser.Logical_expressionContext;

public class SkyBoxCommand extends ActionStatementBase{

    public boolean isSetup;
    public boolean isShow=false;
    public String showExpr;

    public boolean isShowSolarSystem;
    public Logical_expressionContext cloudFlatteningExpr;
    public Logical_expressionContext cloudinessExpr;
    public Logical_expressionContext hourOfDayExpr;


    public Double cloudinessVal;
    public Double cloudFlatteningVal;
    public Double hourOfDayVal;
}
