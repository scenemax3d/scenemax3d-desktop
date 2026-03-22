package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;

import java.util.ArrayList;
import java.util.List;

public class VariableDeclarationCommand extends StatementDef {
    public String varName;
    public SceneMaxParser.Logical_expressionContext valExpr;
    public List<VariableDeclarationCommand> siblings;
    public List<SceneMaxParser.Logical_expressionContext> array;
    public boolean isExprPointer;
    public SceneMaxParser.Logical_expressionContext minExpr;
    public SceneMaxParser.Logical_expressionContext maxExpr;
    public boolean isShared;

    public VariableAssignmentCommand toVarAssignment(ProgramDef prg) {
        VariableAssignmentCommand vac = new VariableAssignmentCommand();
        vac.triggeredByDeclaration = true;

        for (VariableDeclarationCommand var : this.siblings) {
            if(!prg.vars_index.containsKey(var.varName)) {
                VariableDef vd = new VariableDef();
                vd.isShared = var.isShared;
                vd.declaration = var;
                vd.resName = "var";
                vd.varName = var.varName;
                vd.isExprPointer = var.isExprPointer;
                vac.vars.add(vd);
                if(var.array!=null) {
                    vac.array = var.array;
                } else {
                    vac.values.add(var.valExpr);
                }

                prg.vars.add(vd);
                prg.vars_index.put(vd.varName, vd);
            }
        }

        return vac;
    }
}
