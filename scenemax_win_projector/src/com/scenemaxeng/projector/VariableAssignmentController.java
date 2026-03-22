package com.scenemaxeng.projector;

import com.abware.scenemaxlang.parser.SceneMaxParser;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.VariableAssignmentCommand;
import com.scenemaxeng.compiler.VariableDef;

import java.util.ArrayList;
import java.util.List;

public class VariableAssignmentController extends CompositeController {

    private final VariableAssignmentCommand varAssignmentCommand;

    public VariableAssignmentController(SceneMaxApp app, SceneMaxScope thread, ProgramDef prg, VariableAssignmentCommand cmd) {
        super(app, prg, thread, cmd);
        this.varAssignmentCommand = cmd;

    }

    @Override
    public boolean run(float tpf) {

        // evaluate expression once
        if (this.size() == 0) {
            if (varAssignmentCommand.array != null) {
                return assignArray();
            } else {
                return assignSingleVar();
            }
        }

        // run until finish
        return super.run(tpf);

    }

    private boolean assignArray() {

        VarInst var = addVarIndex(varAssignmentCommand.vars.get(0));
        if (var == null) {
            return true;
        }

        var.values = new ArrayList<>();
        for (SceneMaxParser.Logical_expressionContext logExp : varAssignmentCommand.array) {
            Object retval = new ActionLogicalExpression(logExp, this.scope).evaluate();
            var.values.add(retval);
        }

        var.varType = VariableDef.VAR_TYPE_ARRAY;

        return true;

    }

    protected boolean assignSingleVar() {

        int index = 0;
        for (VariableDef varDef : varAssignmentCommand.vars) {
            VarInst var = addVarIndex(varDef);
            if (var == null) {
                return true;
            }

            if (varDef.isExprPointer) {
                var.value = varAssignmentCommand.values.get(index);
                var.varType = VariableDef.VAR_TYPE_EXPR_POINTER;
                index++;
                continue;
            }

            Object retval = new ActionLogicalExpression(varAssignmentCommand.values.get(index), this.scope).evaluate();

            if (retval instanceof List) {
                var.values = (List<Object>) retval;
                var.varType = VariableDef.VAR_TYPE_ARRAY;
                return true;
            } else {

                if (var.varType == VariableDef.VAR_TYPE_ARRAY) {
                    Object arrIndex = new ActionLogicalExpression(varAssignmentCommand.arrayIndexes.get(varDef), this.scope).evaluate();
                    var.values.set( ((Double)arrIndex).intValue(), retval);
                    index++;
                    continue;
                }

                var.value = retval;
                boolean minSet = false;
                if (var.varDef.declaration.minExpr != null) {
                    Object min = new ActionLogicalExpression(var.varDef.declaration.minExpr, this.scope).evaluate();
                    if ((Double) var.value < (Double) min) {
                        var.value = min;
                        minSet = true;
                    }
                }

                if (!minSet && var.varDef.declaration.maxExpr != null) {
                    Object max = new ActionLogicalExpression(var.varDef.declaration.maxExpr, this.scope).evaluate();
                    if ((Double) var.value > (Double) max) {
                        var.value = max;
                    }
                }

            }

            if (var.varDef.varType == 0 || var.varDef.varType == VariableDef.VAR_TYPE_OBJECT) {
                if (retval instanceof String) {
                    var.varType = VariableDef.VAR_TYPE_STRING;
                } else if (retval instanceof Double) {
                    var.varType = VariableDef.VAR_TYPE_NUMBER;
                } else if (retval instanceof EntityInstBase) {
                    var.varReference = ((EntityInstBase) retval).varDef;
                }
            }

            index++;
        }

        return true;
    }

    private VarInst addVarIndex(VariableDef varDef) {
        VarInst var = this.scope.getVar(varDef.varName);
        if (var == null) {
            VariableDef vd = prg.getVar(varDef.varName);
            if (vd == null) {
                app.handleRuntimeError("Variable '" + varDef.varName + "' is not defined");
                return null;
            }
            var = new VarInst(vd, this.scope);
            this.scope.vars_index.put(varDef.varName, var);
        } else {
            if (varDef.isShared && this.varAssignmentCommand.triggeredByDeclaration) {
                return null; // don't update existing shared variables in the declaration phase
            }
        }

        return var;
    }

}
