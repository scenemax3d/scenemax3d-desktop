package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ChangeAngularVelocityCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.VariableDef;

public class ChangeAngularVelocityController extends SceneMaxBaseController {

    public ChangeAngularVelocityController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, ChangeAngularVelocityCommand cmd) {
        super(app, prg, scope, cmd);
    }

    public boolean run(float tpf) {
        if (forceStop) return true;
        findTargetVar();

        ChangeAngularVelocityCommand cmd = (ChangeAngularVelocityCommand) this.cmd;
        Double angularVelocity = (Double) new ActionLogicalExpressionVm(cmd.angularVelocityExpr, this.scope).evaluate();

        if (this.targetVarDef.varType == VariableDef.VAR_TYPE_3D) {
            this.app.applyModelAngularVelocity(this.targetVar, angularVelocity);
        }

        return true;
    }
}
