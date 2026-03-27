package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ActionScaleCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.VariableDef;

public class ChangeScaleController extends SceneMaxBaseController {

    public ChangeScaleController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, ActionScaleCommand cmd) {
        super(app, prg, scope, cmd);
    }

    public boolean run(float tpf) {

        if (forceStop) return true;

        if (!targetCalculated) {

            ActionScaleCommand cmd = (ActionScaleCommand) this.cmd;

            targetCalculated = true;
            findTargetVar();

            Double scale = (Double)new ActionLogicalExpressionVm(cmd.scaleExpr,this.scope).evaluate();

            if (targetVarDef.varType == VariableDef.VAR_TYPE_3D) {
                this.app.changeModelScale(this.targetVar, scale);
            } else if (targetVarDef.varType == VariableDef.VAR_TYPE_SPHERE) {
                this.app.changeSphereScale(this.targetVar, scale);
            } else if (targetVarDef.varType == VariableDef.VAR_TYPE_BOX) {
                this.app.changeBoxScale(this.targetVar, scale);
            }

        }

        return true;
    }

}
