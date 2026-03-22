package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

import java.util.HashMap;

public class VehicleSetupCommand extends ActionStatementBase {

    public boolean setupInput;
    public boolean setupEngine;
    public boolean setupFront;
    public SceneMaxParser.Logical_expressionContext frictionExpr;
    public SceneMaxParser.Logical_expressionContext compressionExpr;
    public SceneMaxParser.Logical_expressionContext dampingExpr;
    public SceneMaxParser.Logical_expressionContext stiffnessExpr;
    public SceneMaxParser.Logical_expressionContext lengthExpr;
    public Double compressionVal;
    public Double dampingVal;
    public Double frictionVal;
    public Double stiffnessVal;
    public Double lengthVal;

    public HashMap<String,Integer> inputSource = new HashMap<>();
    public String inputOnOffCommand;
    public SceneMaxParser.Logical_expressionContext enginePowerExp;
    public SceneMaxParser.Logical_expressionContext engineBreakingExp;
    public Double enginePowerVal;
    public Double engineBreakingVal;
    public String engineOnOff;

    public void addInputSource(String action, Integer key) {
        inputSource.put(action,key);
    }

}
