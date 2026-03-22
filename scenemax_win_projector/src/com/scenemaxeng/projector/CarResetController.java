package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.CarResetCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class CarResetController extends SceneMaxBaseController{

    public CarResetController(SceneMaxApp app, ProgramDef prg, SceneMaxScope thread, CarResetCommand cmd) {
        super(app, prg, thread, cmd);

    }

    public boolean run(float tpf) {

        if (forceStop) return true;

        CarResetCommand cmd = (CarResetCommand) this.cmd;
        RunTimeVarDef entity = findTargetVar(cmd.targetVar);

        app.carReset(entity);
        return true;

    }
}
