package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.ArrayCommand;

public class ArrayCommandController extends SceneMaxBaseController {

    private ArrayCommand cmd;

    public ArrayCommandController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, ArrayCommand cmd) {
        super(app, prg, scope, cmd);
        this.cmd = cmd;
    }

    public boolean run(float tpf) {
        this.findTargetVar();
        VarInst var = scope.getVar(this.cmd.varName);
        if (var == null) {
            return false;
        }

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
        }

        return true;
    }

}
