package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.CharacterIgnoreCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.VariableDef;

public class CharacterIgnoreController extends SceneMaxBaseController {
    public CharacterIgnoreController(SceneMaxApp app, ProgramDef prg, SceneMaxScope thread, CharacterIgnoreCommand cmd) {
        super(app, prg, thread, cmd);
    }

    public boolean run(float tpf) {

        if (forceStop) return true;

        if (!targetCalculated) {

            CharacterIgnoreCommand cmd = (CharacterIgnoreCommand) this.cmd;

            targetCalculated = true;
            findTargetVar();
            RunTimeVarDef ignoreVarDef = this.findTargetVar(cmd.ignoreVar);

            if (targetVarDef.varType == VariableDef.VAR_TYPE_3D) {
                app.ignoreJoints(this.targetVar, ignoreVarDef);
            }

        }


        return true;

    }

}
