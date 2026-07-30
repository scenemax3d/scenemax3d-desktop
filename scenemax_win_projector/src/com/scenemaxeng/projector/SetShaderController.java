package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SetShaderCommand;

public class SetShaderController extends SceneMaxBaseController {

    public SetShaderController(SceneMaxApp app, ProgramDef prg, SceneMaxScope thread, SetShaderCommand cmd) {
        super(app, prg, thread, cmd);
    }

    @Override
    public boolean run(float tpf) {
        if (forceStop) return true;

        findTargetVar();

        SetShaderCommand cmd = (SetShaderCommand) this.cmd;
        Object shaderValue = new ActionLogicalExpressionVm(cmd.shaderNameExpr, this.scope).evaluate();
        String shaderName = shaderValue == null ? "" : shaderValue.toString();
        if (app.setEntityShader(this.targetVar, this.targetVarDef.varType, shaderName)
                && !cmd.fromMultiplayerNetwork) {
            app.syncNetworkEntityData(this.targetVar, SceneMaxApp.NETWORK_ENTITY_SHADER_FIELD, shaderName);
        }
        return true;
    }
}
