package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.StopBlockCommand;

class StopBlockController extends SceneMaxBaseController{

    private final SceneMaxApp app;
    private final SceneMaxScope scope;
    private final ProgramDef prg;
    private final StopBlockCommand cmd;

    public StopBlockController(SceneMaxApp app, ProgramDef prg, StopBlockCommand cmd, SceneMaxScope scope) {
        this.app=app;
        this.scope=scope;
        this.prg=prg;
        this.cmd=cmd;
    }

    @Override
    public boolean run(float tpf) {

        // return action just return from the parent do-end do scope
        if(cmd.returnAction) {
            SceneMaxScope t = scope.getFirstReturnPointScope();
            if(t!=null) {
                t.forceStop();
            } else {
                // didn't find any hosting procedure so stop this one
                t = scope.getSecondLevelReturnPointScope();
                if(t!=null) {
                    t.forceStop();
                } else {
                    scope.forceStop();
                }
            }
            return true;
        }


        // stop - kills the repetition timer
        SceneMaxScope t = scope.getFirstLooperScope();
        SceneMaxBaseController ctl = t.getCreatorController();
        if(ctl instanceof CompositeController) {
            ((CompositeController)ctl).forceStop();
        } else {
            ctl.forceStop = true;
        }



//        SceneMaxThread t = thread.getFirstLooperThread();
//        if(t!=null) {
//
//            // return action just return from the parent do-end do scope
//            if(cmd.returnAction) {
//                t.forceStop();
//                return true;
//            }
//
//            // stop - kills the repetition timer
//            SceneMaxBaseController ctl = t.getCreatorController();
//            if(ctl instanceof CompositeController) {
//                ((CompositeController)ctl).forceStop();
//            } else {
//                ctl.forceStop = true;
//            }
//
//        }
        return true;
    }

}
