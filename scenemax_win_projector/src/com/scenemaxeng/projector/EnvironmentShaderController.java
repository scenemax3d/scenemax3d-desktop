package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.EnvironmentShaderCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class EnvironmentShaderController extends SceneMaxBaseController {

    public EnvironmentShaderController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, EnvironmentShaderCommand cmd) {
        super(app, prg, scope, cmd);
    }

    @Override
    public boolean run(float tpf) {
        if (forceStop) return true;

        EnvironmentShaderCommand cmd = (EnvironmentShaderCommand) this.cmd;
        Object shaderValue = new ActionLogicalExpressionVm(cmd.shaderNameExpr, this.scope).evaluate();
        String shaderName = shaderValue == null ? "" : shaderValue.toString();
        app.setEnvironmentShader(shaderName);
        return true;
    }
}
