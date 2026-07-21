package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.CollisionStatementCommand;
import com.scenemaxeng.compiler.DoBlockCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.VariableDef;

public class CollisionController extends CompositeController {

    private String targetVar1;
    private String targetVar2;

    public CollisionController(SceneMaxApp app, ProgramDef prg, SceneMaxScope thread, CollisionStatementCommand cmd) {
        super(app,prg,thread,cmd);
    }

    public boolean run(float tpf)
    {
        if(forceStop) return true;

        CollisionStatementCommand collCmd = (CollisionStatementCommand)this.cmd;
        CollisionTarget rt2 = resolveCollisionTarget(collCmd.destEndpoint, collCmd.destEntity);
        if (rt2 == null) {
            return true;
        }
        this.targetVar2 = rt2.runtimeName;

        DoBlockCommand cmd = collCmd.doBlock;
        DoBlockController collisionController = new DoBlockController(app, this.scope, cmd);

        collisionController.app = app;
        collisionController.async = cmd.isAsync;

        int counter = 0;
        for (CollisionStatementCommand.CollisionEndpoint endpoint : sourceEndpoints(collCmd)) {
            CollisionTarget rt1 = resolveCollisionTarget(endpoint,
                    counter < collCmd.sourceEntities.size() ? collCmd.sourceEntities.get(counter) : null);
            if (rt1 == null) {
                counter++;
                continue;
            }

            if (collCmd.destEndpoint != null && collCmd.destEndpoint.networkEntity) {
                app.addNetworkCollisionHandler(
                        rt1.runtimeName,
                        collCmd.destEndpoint.networkObjectName,
                        collisionController,
                        rt1.entityInst,
                        rt1.joint,
                        collCmd.destEndpoint.joint,
                        collCmd.goExpr);
                counter++;
                continue;
            }

            if (endpoint != null && endpoint.networkEntity) {
                app.addNetworkCollisionHandler(
                        rt2.runtimeName,
                        endpoint.networkObjectName,
                        collisionController,
                        rt2.entityInst,
                        rt2.joint,
                        endpoint.joint,
                        collCmd.goExpr);
                counter++;
                continue;
            }

            app.addCollisionHandler(
                    rt1.runtimeName,
                    rt2.runtimeName,
                    collisionController,
                    rt1.entityInst,
                    rt2.entityInst,
                    rt1.joint,
                    rt2.joint,
                    collCmd.goExpr);
            counter++;
        }

//        app.addCollisionHandler(this.targetVar1,this.targetVar2,
//                collisionController,
//                thread.getEntityInst(collCmd.varDef1.varName),
//                thread.getEntityInst(collCmd.varDef2.varName),
//                collCmd.part1,
//                collCmd.part2,
//                collCmd.goExpr);

        return true;

    }

    private java.util.List<CollisionStatementCommand.CollisionEndpoint> sourceEndpoints(CollisionStatementCommand cmd) {
        if (cmd.sourceEndpoints != null && !cmd.sourceEndpoints.isEmpty()) {
            return cmd.sourceEndpoints;
        }
        java.util.List<CollisionStatementCommand.CollisionEndpoint> endpoints = new java.util.ArrayList<>();
        for (int i = 0; i < cmd.sourceEntities.size(); i++) {
            CollisionStatementCommand.CollisionEndpoint endpoint = new CollisionStatementCommand.CollisionEndpoint();
            endpoint.entity = cmd.sourceEntities.get(i);
            endpoint.joint = i < cmd.sourceJoints.size() ? cmd.sourceJoints.get(i) : null;
            endpoints.add(endpoint);
        }
        return endpoints;
    }

    private CollisionTarget resolveCollisionTarget(CollisionStatementCommand.CollisionEndpoint endpoint,
                                                   VariableDef fallbackEntity) {
        if (endpoint != null && endpoint.networkEntity) {
            return new CollisionTarget(endpoint.networkObjectName, null, endpoint.joint);
        }

        if (endpoint != null && endpoint.equippedWeaponCollider) {
            RunTimeVarDef owner = app.findVarRuntime(prg, scope, endpoint.ownerVarName);
            if (owner == null) {
                app.handleRuntimeError(app.formatUndefinedVariableError(
                        endpoint.ownerVarLine,
                        endpoint.ownerVarName,
                        endpoint.ownerVarDef,
                        getClass().getSimpleName()));
                return null;
            }
            String runtimeName = endpoint.colliderName + "@" + runtimeScopeId(owner.varName);
            return new CollisionTarget(runtimeName, null, endpoint.joint);
        }

        VariableDef entity = endpoint != null && endpoint.entity != null ? endpoint.entity : fallbackEntity;
        RunTimeVarDef runtime = findTargetVar(entity == null ? null : entity.varName);
        if (runtime == null) {
            runtime = runtimeVarForPendingEntity(entity);
        }
        if (runtime == null) {
            return null;
        }
        String entityName = entity == null ? "" : entity.varName;
        String joint = endpoint == null ? null : endpoint.joint;
        return new CollisionTarget(runtime.varName, scope.getEntityInst(entityName), joint);
    }

    private String runtimeScopeId(String runtimeVarName) {
        if (runtimeVarName == null) {
            return String.valueOf(scope.scopeId);
        }
        int at = runtimeVarName.lastIndexOf('@');
        return at >= 0 && at < runtimeVarName.length() - 1
                ? runtimeVarName.substring(at + 1)
                : String.valueOf(scope.scopeId);
    }

    private RunTimeVarDef runtimeVarForPendingEntity(VariableDef varDef) {
        if (varDef == null) {
            return null;
        }
        RunTimeVarDef runtime = new RunTimeVarDef(varDef);
        runtime.varName = varDef.varName + "@" + scope.scopeId;
        return runtime;
    }

    private static class CollisionTarget {
        private final String runtimeName;
        private final EntityInstBase entityInst;
        private final String joint;

        private CollisionTarget(String runtimeName, EntityInstBase entityInst, String joint) {
            this.runtimeName = runtimeName;
            this.entityInst = entityInst;
            this.joint = joint;
        }
    }




}
