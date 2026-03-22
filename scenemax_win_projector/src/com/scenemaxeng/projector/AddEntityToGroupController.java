package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.AddEntityToGroupCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class AddEntityToGroupController extends SceneMaxBaseController {

    public AddEntityToGroupController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, AddEntityToGroupCommand cmd) {
        super(app, prg, scope, cmd);
    }

    public boolean run(float tpf) {
        if (forceStop) return true;

        if (!targetCalculated) {

            AddEntityToGroupCommand cmd = (AddEntityToGroupCommand) this.cmd;

            targetCalculated = true;
            findTargetVar();

            this.app.AddEntityToGroup(cmd.varDef.varType,this.targetVar,cmd.targetGroup,this.prg,this.scope);
        }

        return true;

    }

}
