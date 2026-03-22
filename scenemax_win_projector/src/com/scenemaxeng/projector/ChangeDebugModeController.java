package com.scenemaxeng.projector;


import com.scenemaxeng.compiler.ChangeDebugMode;
import com.scenemaxeng.compiler.ProgramDef;

public class ChangeDebugModeController extends SceneMaxBaseController{

    public ChangeDebugModeController(SceneMaxApp app, ProgramDef prg, SceneMaxScope thread, ChangeDebugMode cmd) {
        super(app, prg, thread, cmd);
    }

    public boolean run(float tpf) {
        this.app.setDebugMode(((ChangeDebugMode)this.cmd).debugOn);
        return true;
    }

}
