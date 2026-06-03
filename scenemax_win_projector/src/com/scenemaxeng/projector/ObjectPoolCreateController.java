package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ObjectPoolCreateCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.VariableDef;

public class ObjectPoolCreateController extends SceneMaxBaseController {
    private final ObjectPoolCreateCommand poolCommand;

    public ObjectPoolCreateController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope,
                                      ObjectPoolCreateCommand cmd) {
        super(app, prg, scope, cmd);
        this.poolCommand = cmd;
    }

    @Override
    public boolean run(float tpf) {
        VarInst existing = scope.getVar(poolCommand.poolVarName);
        if (existing != null && existing.value instanceof RuntimeObjectPool) {
            return true;
        }

        int initialSize = 0;
        Object sizeValue = new ActionLogicalExpressionVm(poolCommand.initialSizeExpr, scope).evaluate();
        if (sizeValue instanceof Number) {
            initialSize = Math.max(0, ((Number) sizeValue).intValue());
        }

        VarInst poolVar = existing;
        if (poolVar == null) {
            poolVar = new VarInst(poolCommand.poolVarDef, scope);
            scope.vars_index.put(poolCommand.poolVarName, poolVar);
        }
        poolVar.varType = VariableDef.VAR_TYPE_OBJECT_POOL;
        poolVar.value = new RuntimeObjectPool(app, prg, scope, poolCommand, initialSize);
        return true;
    }
}
