package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

import java.util.List;

public class JavaNewInstanceDecl extends StatementDef {

    public String scope;
    public String type;
    public String val;
    public boolean isClassType;
    public String varName;
    public SceneMaxParser.Logical_expressionContext valExpr;
    public List<JavaNewInstanceDecl> siblings;
}
