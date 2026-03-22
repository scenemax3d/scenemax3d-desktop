package com.scenemaxeng.projector;

import com.jme3.math.Vector3f;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.FpsCameraCommand;

public class FpsCameraController extends SceneMaxBaseController{

    private boolean fpsStart;
    Vector3f offsetPos;

    public FpsCameraController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, FpsCameraCommand cmd) {
        super(app, prg, scope, cmd);
        //fpsStart = cmd.command==FpsCameraCommand.START;
    }

    public boolean run(float tpf) {

        if (forceStop) return true;

        FpsCameraCommand cmd = (FpsCameraCommand) this.cmd;

        if (!targetCalculated) {
            targetCalculated = true;
            if(this.cmd.varDef!=null) {
                findTargetVar();
            }

        }

        if(cmd.cameraType!=null) {
            if (cmd.cameraType.equalsIgnoreCase("dungeon")) {
                if (this.targetVar == null) {
                    findTargetVar();
                }
                app.setDungeonCameraOn(this.scope, this.targetVar, this.targetVarDef, cmd);

                return true;
            }

            if (cmd.cameraType.equalsIgnoreCase("follow")) {
                if (this.targetVar == null) {
                    findTargetVar();
                }
                app.setFollowCameraOn(this.scope, this.targetVar, this.targetVarDef, cmd);

                return true;
            }
        }

        if (fpsStart) {

            //app.setFpsCameraOn(this.targetVar,this.targetVarDef, offsetPos);
        } else {
            app.setAttachCameraOff();
        }

        return true;

    }

}
