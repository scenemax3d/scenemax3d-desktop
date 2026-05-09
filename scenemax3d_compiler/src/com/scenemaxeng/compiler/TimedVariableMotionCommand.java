package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

import java.util.Collections;
import java.util.List;

public class TimedVariableMotionCommand extends VariableActionStatement {
    public int motionEaseType = MotionEaseType.LINEAR;
    public String motionEaseFunction;
    public List<SceneMaxParser.Logical_expressionContext> motionEaseParamExprs = Collections.emptyList();
}
