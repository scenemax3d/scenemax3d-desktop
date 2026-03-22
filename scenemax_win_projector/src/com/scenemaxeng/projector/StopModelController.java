package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ActionCommandStop;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.VariableDef;

import java.util.HashMap;

class StopModelController extends SceneMaxBaseController{

    private final SceneMaxApp app;
    private final ProgramDef prg;
    private final ActionCommandStop cmd;
    private SceneMaxScope scope;
    private VariableDef targetVarDef;
    public static HashMap<String,Boolean> forceStopCommands = new HashMap<>();

    public StopModelController(SceneMaxApp app, ProgramDef prg, ActionCommandStop cmd, SceneMaxScope scope) {
        this.app=app;
        this.prg=prg;
        this.cmd=cmd;
        this.scope=scope;
        this.targetVarDef=cmd.varDef;
    }

    @Override
    public boolean run(float tpf) {

        int scopeId=-1;
        String targetVar = null;

        if(cmd.varDef.varType== VariableDef.VAR_TYPE_OBJECT) {
            EntityInstBase obj = (EntityInstBase) scope.getFuncScopeParam(cmd.varDef.varName);
            if(obj==null) {
                app.handleRuntimeError("Function argument '"+cmd.varDef.varName+"' is undefined");
                return true;
            }
            targetVar = obj.varDef.varName + "@" + obj.scope.scopeId;
            targetVarDef = new VariableDef();// in order to avoid overriding varType
            targetVarDef.varType = obj.varDef.varType;
        } else if(cmd.varDef.varType==VariableDef.VAR_TYPE_SPHERE) {
            scopeId = app.getEntityScopeId(scope, cmd.targetVar,cmd.varDef.varType);
            targetVar = cmd.varDef.varName + "@" + scopeId;
        } else if(cmd.varDef.varType!= VariableDef.VAR_TYPE_CAMERA) {
            scopeId=app.getEntityScopeId(scope,cmd.targetVar);
            targetVar = cmd.varDef.varName + "@" + scopeId;
        }

        forceStopCommands.put(targetVar,true);

        return true;

    }
}
