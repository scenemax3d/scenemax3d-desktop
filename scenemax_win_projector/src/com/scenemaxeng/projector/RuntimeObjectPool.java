package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ObjectPoolCreateCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.VariableDef;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class RuntimeObjectPool {
    private final SceneMaxApp app;
    private final ProgramDef prg;
    private final SceneMaxScope scope;
    private final ObjectPoolCreateCommand cmd;
    private final ArrayDeque<EntityInstBase> available = new ArrayDeque<>();
    private final Set<EntityInstBase> active =
            Collections.newSetFromMap(new IdentityHashMap<EntityInstBase, Boolean>());
    private final Set<EntityInstBase> all =
            Collections.newSetFromMap(new IdentityHashMap<EntityInstBase, Boolean>());
    private int createdCount;

    public RuntimeObjectPool(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope,
                             ObjectPoolCreateCommand cmd, int initialSize) {
        this.app = app;
        this.prg = prg;
        this.scope = scope;
        this.cmd = cmd;
        for (int i = 0; i < Math.max(0, initialSize); i++) {
            EntityInstBase inst = createPooledEntity();
            if (inst != null) {
                releaseFresh(inst);
            }
        }
    }

    public EntityInstBase acquire() {
        EntityInstBase inst = available.pollFirst();
        if (inst == null) {
            inst = createPooledEntity();
        }
        if (inst == null) {
            return null;
        }
        active.add(inst);
        app.setPooledEntityActive(inst, true);
        return inst;
    }

    public void release(EntityInstBase inst) {
        if (inst == null) {
            return;
        }
        if (!all.contains(inst)) {
            app.handleRuntimeError("Object '" + inst.varDef.varName + "' does not belong to pool '" + cmd.poolVarName + "'");
            return;
        }
        if (!active.remove(inst)) {
            app.handleRuntimeError("Object '" + inst.varDef.varName + "' is already released to pool '" + cmd.poolVarName + "'");
            return;
        }
        releaseFresh(inst);
    }

    private void releaseFresh(EntityInstBase inst) {
        app.setPooledEntityActive(inst, false);
        available.addLast(inst);
    }

    private EntityInstBase createPooledEntity() {
        if (cmd.sourceIsFunction) {
            Object value = app.invokeFunctionValueNow(cmd.sourceName, Collections.emptyList(), scope);
            if (value instanceof EntityInstBase) {
                EntityInstBase inst = (EntityInstBase) value;
                all.add(inst);
                return inst;
            }
            app.handleRuntimeError("Object pool factory '" + cmd.sourceName + "' must return an object");
            return null;
        }

        VariableDef var = copyVariableDef(cmd.sourceVarDef);
        var.varName = cmd.poolVarName + "_" + (++createdCount);
        var.visible = false;
        var.isAsync = false;

        app.instantiateVariable(prg, var, scope);
        EntityInstBase inst = scope.getEntityInst(var.varName);
        if (inst == null) {
            app.handleRuntimeError("Failed to create pooled object '" + var.varName + "' from '" + cmd.sourceName + "'");
            return null;
        }
        all.add(inst);
        return inst;
    }

    private VariableDef copyVariableDef(VariableDef source) {
        VariableDef copy = new VariableDef();
        copy.resName = source.resName;
        copy.varType = source.varType;
        copy.xExpr = source.xExpr;
        copy.yExpr = source.yExpr;
        copy.zExpr = source.zExpr;
        copy.rxExpr = source.rxExpr;
        copy.ryExpr = source.ryExpr;
        copy.rzExpr = source.rzExpr;
        copy.scaleExpr = source.scaleExpr;
        copy.scaleXExpr = source.scaleXExpr;
        copy.scaleYExpr = source.scaleYExpr;
        copy.scaleZExpr = source.scaleZExpr;
        copy.massExpr = source.massExpr;
        copy.entityPos = source.entityPos;
        copy.entityRot = source.entityRot;
        copy.isStatic = source.isStatic;
        copy.isVehicle = source.isVehicle;
        copy.useVerbalTurn = source.useVerbalTurn;
        copy.rotDir = source.rotDir;
        copy.isDynamic = source.isDynamic;
        copy.joints = source.joints;
        copy.visible = source.visible;
        copy.shadowMode = source.shadowMode;
        copy.resNameExpr = source.resNameExpr;
        copy.entityPosJoint = source.entityPosJoint;
        copy.calibration = source.calibration;
        copy.collisionShape = source.collisionShape;
        return copy;
    }
}
