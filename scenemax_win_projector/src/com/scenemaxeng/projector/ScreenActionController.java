package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.ScreenActionCommand;

public class ScreenActionController extends SceneMaxBaseController{

    public ScreenActionController(SceneMaxApp app, ProgramDef prg, SceneMaxScope thread, ScreenActionCommand cmd) {
        super(app, prg, thread, cmd);
    }

    public boolean run(float tpf) {
        if (forceStop) return true;

        app.runScreenCommand((ScreenActionCommand)cmd);
        return true;
    }

}
