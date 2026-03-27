package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.CharacterJumpCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.VariableDef;

public class CharacterJumpController extends SceneMaxBaseController {


    public CharacterJumpController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, CharacterJumpCommand cmd) {
        super(app, prg, scope, cmd);
    }

    public boolean run(float tpf) {

        if (forceStop) return true;

        if (!targetCalculated) {

            CharacterJumpCommand cmd = (CharacterJumpCommand) this.cmd;

            targetCalculated = true;
            findTargetVar();

            Double speed = null;
            if(cmd.speedExpr!=null) {
                speed = (Double) new ActionLogicalExpressionVm(cmd.speedExpr, this.scope).evaluate();
            }

            if (targetVarDef.varType == VariableDef.VAR_TYPE_3D) {
                app.characterJump(this.targetVar, speed);
            }

        }


        return true;

    }

}
