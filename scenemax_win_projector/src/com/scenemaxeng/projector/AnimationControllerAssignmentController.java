package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.AnimationControllerAssignmentCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.VariableDef;

public class AnimationControllerAssignmentController extends SceneMaxBaseController {

    public AnimationControllerAssignmentController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope,
                                                   AnimationControllerAssignmentCommand cmd) {
        super(app, prg, scope, cmd);
    }

    @Override
    public boolean run(float tpf) {
        AnimationControllerAssignmentCommand assignment = (AnimationControllerAssignmentCommand) cmd;
        VarInst var = scope.getVar(assignment.targetVar);
        if (var == null) {
            var = new VarInst(assignment.varDef, scope);
            scope.vars_index.put(assignment.targetVar, var);
        }

        var.value = new AnimationRuntimeController(
                app,
                prg,
                scope,
                assignment.sourceVar,
                assignment.sourceVarDef,
                assignment.animationName,
                assignment.varLineNum,
                assignment.statements);
        var.varType = VariableDef.VAR_TYPE_ANIMATION_CONTROLLER;
        return true;
    }
}
