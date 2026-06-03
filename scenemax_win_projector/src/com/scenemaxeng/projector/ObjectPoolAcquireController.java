package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ObjectPoolAcquireCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.VariableDef;

public class ObjectPoolAcquireController extends SceneMaxBaseController {
    private final ObjectPoolAcquireCommand poolCommand;

    public ObjectPoolAcquireController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope,
                                       ObjectPoolAcquireCommand cmd) {
        super(app, prg, scope, cmd);
        this.poolCommand = cmd;
    }

    @Override
    public boolean run(float tpf) {
        EntityInstBase inst = app.acquireObjectFromPool(poolCommand.poolVarName, scope, poolCommand.varLineNum);
        if (inst == null) {
            return true;
        }

        VarInst result = scope.getVar(poolCommand.resultVarName);
        if (result == null) {
            result = new VarInst(poolCommand.resultVarDef, scope);
            scope.vars_index.put(poolCommand.resultVarName, result);
        }
        result.value = inst;
        result.varReference = inst.varDef;
        result.varType = VariableDef.VAR_TYPE_OBJECT;
        return true;
    }
}
