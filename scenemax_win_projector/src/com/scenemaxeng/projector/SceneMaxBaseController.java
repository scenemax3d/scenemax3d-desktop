package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ActionStatementBase;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.VariableDef;

public class SceneMaxBaseController implements ISceneMaxController {

    public boolean adhereToPauseStatus = true;
    protected boolean targetCalculated = false;
    public boolean isInitiated = false;
    public boolean isEventHandler = false;
    public boolean forceStop = false;
    public boolean async=false;
    public boolean isRunning;

    public SceneMaxApp app;
    public ProgramDef prg;
    public SceneMaxScope scope;
    public ActionStatementBase cmd;

    protected String targetVar;
    protected VariableDef targetVarDef;
    public SceneMaxBaseController parentController;

    public SceneMaxBaseController() {

    }

    public SceneMaxBaseController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, ActionStatementBase cmd) {
        this.app=app;
        this.prg=prg;
        this.scope=scope;
        this.cmd=cmd;

        if(cmd!=null) {
            this.targetVarDef = cmd.varDef;
        }

    }

    public void enableEntity(String targetVar) {
        StopModelController.forceStopCommands.remove(targetVar);
    }

    public void setUIProxy(SceneMaxApp app) {
        this.app = app;
    }

    @Override
    public boolean run(float tpf) {
        return false;
    }

    @Override
    public void init() {

    }

    @Override
    public void init(int startIndex) {

    }

    @Override
    public void dispose() {

    }

    protected RunTimeVarDef findTargetVar(String varName) {

        EntityInstBase vi=null;
        vi = scope.getEntityInst(varName);

        if(vi==null) {
            return null;
        }

        VariableDef vardef = vi.varDef;//   prg.getVar(varName);
        if(vardef==null) {
            return null;
        }
        RunTimeVarDef rtVarDef = new RunTimeVarDef(vardef);

        if (vardef.varType == VariableDef.VAR_TYPE_SPHERE || vardef.varType == VariableDef.VAR_TYPE_BOX) {
            int scopeId = app.getEntityScopeId(scope, vardef.varName, vardef.varType);
            rtVarDef.varName = vardef.varName + "@" + scopeId;
        } else if (vardef.varType == VariableDef.VAR_TYPE_OBJECT) {
            EntityInstBase obj = (EntityInstBase) scope.getFuncScopeParam(vardef.varName);

            if (obj == null) {
                app.handleRuntimeError("Function argument '" + vardef.varName + "' is undefined");
                return null;
            }

            rtVarDef.varName = obj.varDef.varName + "@" + obj.scope.scopeId;
            rtVarDef.varDef = new VariableDef();// in order to avoid overriding varType
            rtVarDef.varDef.varType = obj.varDef.varType;
        } else if (vardef.varType != VariableDef.VAR_TYPE_CAMERA) {
            //int threadId = app.getEntityThreadId(thread, vardef.varName);
            rtVarDef.varName = vardef.varName + "@" + vi.scope.scopeId;// scopeId;
        }

        return rtVarDef; // 0 = OK

    }

    protected int findTargetVar() {

        VariableDef varDef = cmd.varDef;
        VarInst vi = scope.getVar(cmd.varDef.varName);
        if (vi != null && vi.value instanceof EntityInstBase) {
            varDef = ((EntityInstBase) vi.value).varDef;
            targetVarDef = varDef;
            this.targetVar = varDef.varName + "@" + ((EntityInstBase) vi.value).scope.scopeId;
        }

        if (varDef.varType == VariableDef.VAR_TYPE_SPHERE || varDef.varType == VariableDef.VAR_TYPE_BOX) {
            if (this.targetVar == null) {
                int scopeId = app.getEntityScopeId(scope, varDef.varName, varDef.varType);
                this.targetVar = varDef.varName + "@" + scopeId;
            }
        } else if (varDef.varType == VariableDef.VAR_TYPE_OBJECT) {
            EntityInstBase obj = (EntityInstBase) scope.getFuncScopeParam(varDef.varName);

            if (obj == null) {
                app.handleRuntimeError("Function argument '" + cmd.varDef.varName + "' is undefined");
                return 1;
            }

            this.targetVar = obj.varDef.varName + "@" + obj.scope.scopeId;
            targetVarDef = new VariableDef();// in order to avoid overriding varType
            targetVarDef.varType = obj.varDef.varType;
        } else if (cmd.varDef.varType != VariableDef.VAR_TYPE_CAMERA) {
            if (this.targetVar == null) {
                if (cmd.varDef.varType == 0) {
                    //VarInst vi = thread.getVar(cmd.varDef.varName);
                    if (vi.varReference != null) {
                        int scopeId = app.getEntityScopeId(scope, vi.varReference.varName);
                        this.targetVar = vi.varReference.varName + "@" + scopeId;
                        targetVarDef = new VariableDef();// in order to avoid overriding varType
                        targetVarDef.varType = vi.varReference.varType;
                    }
                } else {
                    int scopeId = app.getEntityScopeId(scope, cmd.varDef.varName);
                    this.targetVar = cmd.varDef.varName + "@" + scopeId;
                }
            }
        }

        return 0; // 0 = OK

    }

    protected void setScope(SceneMaxScope scope) {
        this.scope = scope;
    }
}
