package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ObjectPoolReleaseCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class ObjectPoolReleaseController extends SceneMaxBaseController {
    private final ObjectPoolReleaseCommand poolCommand;

    public ObjectPoolReleaseController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope,
                                       ObjectPoolReleaseCommand cmd) {
        super(app, prg, scope, cmd);
        this.poolCommand = cmd;
    }

    @Override
    public boolean run(float tpf) {
        EntityInstBase inst = null;
        VarInst var = scope.getVar(poolCommand.objectVarName);
        if (var != null && var.value instanceof EntityInstBase) {
            inst = (EntityInstBase) var.value;
        }
        if (inst == null) {
            inst = scope.getEntityInst(poolCommand.objectVarName);
        }
        app.releaseObjectToPool(poolCommand.poolVarName, inst, scope, poolCommand.varLineNum);
        return true;
    }
}
