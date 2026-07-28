package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.ArrayCommand;
import com.scenemaxeng.compiler.VariableDef;

import java.util.ArrayList;

public class ArrayCommandController extends SceneMaxBaseController {

    private ArrayCommand cmd;

    public ArrayCommandController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, ArrayCommand cmd) {
        super(app, prg, scope, cmd);
        this.cmd = cmd;
    }

    public boolean run(float tpf) {
        VarInst var = scope.getVar(this.cmd.varName);
        if (var == null) {
            return false;
        }
        if (var.values == null) {
            var.values = new ArrayList<>();
        }
        var.varType = VariableDef.VAR_TYPE_ARRAY;

        switch (this.cmd.action) {
            case Push:
                Object obj = new ActionLogicalExpressionVm(this.cmd.expr, this.scope).evaluate();
                var.values.add(obj);
                break;
            case Pop:
                if(var.values.size() > 0) {
                    var.values.remove(var.values.size() - 1);
                }
                break;
            case Clear:
                var.values.clear();
                break;
            case Reset:
                Object resetValue = new ActionLogicalExpressionVm(this.cmd.expr, this.scope).evaluate();
                for (int i = 0; i < var.values.size(); i++) {
                    var.values.set(i, resetValue);
                }
                break;
        }

        if (app != null && var.varDef != null && var.varDef.isNetwork && !this.cmd.fromMultiplayerNetwork) {
            app.syncNetworkVariable(var.varDef.varName, var.values, false);
            app.applyPendingNetworkVariableValues(this.scope);
        }

        return true;
    }

}
