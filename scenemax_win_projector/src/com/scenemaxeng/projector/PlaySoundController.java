package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.PlayStopSoundCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class PlaySoundController extends SceneMaxBaseController {

    private final PlayStopSoundCommand cmd;

    public PlaySoundController(SceneMaxApp app, ProgramDef prg, SceneMaxScope thread, PlayStopSoundCommand cmd) {
        super(app,prg,thread,cmd);
        this.cmd=cmd;
    }

    public boolean run(float tpf) {
        if (forceStop) return true;

        String sound = null;
        if(cmd.soundExpr!=null) {
            sound = new ActionLogicalExpressionVm(cmd.soundExpr,this.scope).evaluate().toString();
        } else {
            sound = cmd.sound;
        }

        if(cmd.stop) {
            app.stopSound(sound);
        } else {
            app.playSound(sound,cmd);
        }

        return true;
    }

}
