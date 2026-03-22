package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

import java.util.ArrayList;
import java.util.List;

public class CSharpInvokeCommand extends ActionStatementBase{

    public String funcName;
    public String className;
    public List<SceneMaxParser.Logical_expressionContext> params;
    public List<String> targetRegister = new ArrayList<>();
}
