package com.scenemaxeng.projector;

import com.jme3.scene.Spatial;
import com.scenemaxeng.compiler.ActionCommandRecord;
import com.scenemaxeng.compiler.ProgramDef;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class SaveRecordingController extends SceneMaxBaseController{

    public SaveRecordingController(SceneMaxApp app, ProgramDef prg, SceneMaxScope thread, ActionCommandRecord cmd) {
        super(app, prg, thread, cmd);
    }


    public boolean run(float tpf) {

        if (forceStop) return true;

        if (!targetCalculated) {

            findTargetVar();

            Spatial sp = app.getEntitySpatial(this.targetVar, this.targetVarDef.varType);
            if (sp == null) {
                return true;
            }

            ActionCommandRecord cmd = (ActionCommandRecord)this.cmd;
            String buff = sp.getUserData("__record_transitions");
            try {
                FileUtils.write(new File(cmd.savePath),buff, StandardCharsets.UTF_8);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return true;
    }
}
