package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;
import org.antlr.v4.runtime.ParserRuleContext;

public class LightVariableDef extends VariableDef {

    public static final String TYPE_DIRECTIONAL = "directional";
    public static final String TYPE_POINT = "point";
    public static final String TYPE_SPOT = "spot";
    public static final String TYPE_SKY = "sky";
    public static final String TYPE_AMBIENT = "ambient";
    public static final String TYPE_PROBE = "probe";

    public String lightType;
    public String color;
    public ParserRuleContext intensityExpr;
    public String intensityUnit;
    public SceneMaxParser.Logical_expressionContext directionXExpr;
    public SceneMaxParser.Logical_expressionContext directionYExpr;
    public SceneMaxParser.Logical_expressionContext directionZExpr;
    public String shadowMode = "off";
    public ParserRuleContext rangeExpr;
    public String lookAtTarget;
    public ParserRuleContext angleExpr;
    public String preset;
    public ParserRuleContext exposureExpr;
    public String ambientColor;

    public LightVariableDef() {
        this.varType = VariableDef.VAR_TYPE_LIGHT;
        this.resName = "light";
    }

    @Override
    public boolean validate(ProgramDef prg) {
        return true;
    }
}
