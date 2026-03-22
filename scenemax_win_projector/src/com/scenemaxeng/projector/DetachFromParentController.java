package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.DettachFromParentCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.VariableDef;

public class DetachFromParentController extends SceneMaxBaseController {

    public DetachFromParentController(SceneMaxApp app, ProgramDef prg, SceneMaxScope thread, DettachFromParentCommand cmd) {
        super(app, prg, thread, cmd);
    }

    public boolean run(float tpf) {

        if (forceStop) return true;

        if (!targetCalculated) {
            targetCalculated = true;
            findTargetVar();

            if (targetVarDef.varType == VariableDef.VAR_TYPE_3D) {
                app.detachModelFromParent(targetVar);
            } else if (targetVarDef.varType == VariableDef.VAR_TYPE_SPHERE) {
                app.detachSphereFromParent(targetVar);
            } else if (targetVarDef.varType == VariableDef.VAR_TYPE_BOX) {
                app.detachBoxFromParent(targetVar);
            } else if (targetVarDef.varType == VariableDef.VAR_TYPE_2D) {
                app.detachSpriteFromParent(targetVar);
            }

        }

        return true;

    }

}
