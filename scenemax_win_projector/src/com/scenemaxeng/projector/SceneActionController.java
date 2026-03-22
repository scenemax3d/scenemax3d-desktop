package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneActionCommand;

public class SceneActionController extends SceneMaxBaseController {

    public SceneActionController(SceneMaxApp app, ProgramDef prg, SceneMaxScope thread, SceneActionCommand cmd) {
        super(app, prg, thread, cmd);
        this.adhereToPauseStatus=false; // allow resume
    }

    public boolean run(float tpf) {

        if (forceStop) return true;

        SceneActionCommand cmd=(SceneActionCommand)this.cmd;
        if(cmd.pause) {
            app.pauseScene();
        } else {
            app.resumeScene();
        }

        return true;

    }
}
