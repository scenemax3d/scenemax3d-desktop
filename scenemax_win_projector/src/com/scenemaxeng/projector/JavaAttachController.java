package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.JavaAttachCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class JavaAttachController extends SceneMaxBaseController {

    public JavaAttachController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, JavaAttachCommand cmd) {
        super(app, prg, scope, cmd);
    }

    @Override
    public boolean run(float tpf) {
        JavaAttachCommand javaCommand = (JavaAttachCommand) this.cmd;
        boolean attached = JavaExtensionRuntimeLoader.attachExtension(
                app,
                scope,
                javaCommand.appStateName,
                null);
        if (!attached) {
            app.handleRuntimeError("Could not attach Java app state '" + javaCommand.appStateName + "'.");
        }
        return true;
    }
}
