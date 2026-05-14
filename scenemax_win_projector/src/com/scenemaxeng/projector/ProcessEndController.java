package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProcessEndCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class ProcessEndController extends SceneMaxBaseController {

    public ProcessEndController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, ProcessEndCommand cmd) {
        super(app, prg, scope, cmd);
    }

    @Override
    public boolean run(float tpf) {
        app.endRuntimeProcess();
        return true;
    }
}
