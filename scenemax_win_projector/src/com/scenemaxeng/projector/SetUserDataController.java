package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SetUserDataCommand;
import com.scenemaxeng.compiler.VariableDef;

public class SetUserDataController extends SceneMaxBaseController {

    public SetUserDataController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, SetUserDataCommand cmd) {
        super(app, prg, scope, cmd);
    }

    public boolean run(float tpf) {
        if (forceStop) return true;

        if (!targetCalculated) {

            SetUserDataCommand cmd = (SetUserDataCommand) this.cmd;

            targetCalculated = true;
            findTargetVar();

            java.lang.Object data = new ActionLogicalExpressionVm(cmd.dataExpr,this.scope).evaluate();

            if (targetVarDef.varType == VariableDef.VAR_TYPE_3D) {
                this.app.setModelUserData(this.targetVar, cmd.fieldName, data);
            } else if (targetVarDef.varType == VariableDef.VAR_TYPE_SPHERE) {
                this.app.setSphereUserData(this.targetVar, cmd.fieldName, data);
            } else if (targetVarDef.varType == VariableDef.VAR_TYPE_BOX) {
                this.app.setBoxUserData(this.targetVar, cmd.fieldName, data);
            } else if (targetVarDef.varType == VariableDef.VAR_TYPE_2D) {
                this.app.setSpriteUserData(this.targetVar, cmd.fieldName, data);
            }

        }


        return true;
    }
}
