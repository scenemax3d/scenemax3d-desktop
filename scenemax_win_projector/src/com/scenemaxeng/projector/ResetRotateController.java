package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.RotateResetCommand;
import com.scenemaxeng.compiler.VariableDef;

public class ResetRotateController extends SceneMaxBaseController {


    public ResetRotateController(SceneMaxApp app, ProgramDef prg, SceneMaxScope thread, RotateResetCommand cmd) {
        super(app, prg, thread, cmd);
    }


    public boolean run(float tpf) {
        if (forceStop) return true;

        if (!targetCalculated) {

            targetCalculated = true;

            RotateResetCommand cmd = (RotateResetCommand) this.cmd;
            findTargetVar();

            float x = ((Double) new ActionLogicalExpression(cmd.xExpr, this.scope).evaluate()).floatValue();
            float y = ((Double) new ActionLogicalExpression(cmd.yExpr, this.scope).evaluate()).floatValue();
            float z = ((Double) new ActionLogicalExpression(cmd.zExpr, this.scope).evaluate()).floatValue();

            if (targetVarDef.varType == VariableDef.VAR_TYPE_CAMERA) {
                this.app.rotateResetCamera(x,y,z);
            } else if (targetVarDef.varType == VariableDef.VAR_TYPE_SPHERE) {
                this.app.rotateResetSphere(targetVar, x,y,z);
            } else if (targetVarDef.varType == VariableDef.VAR_TYPE_BOX) {
                this.app.rotateResetBox(targetVar, x,y,z);
            } else {
                this.app.rotateResetModel(targetVar, x,y,z);
            }


        }

        return true;
    }

}
