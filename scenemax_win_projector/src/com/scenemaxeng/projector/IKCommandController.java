package com.scenemaxeng.projector;

import com.jme3.scene.Spatial;
import com.scenemaxeng.compiler.EntityPos;
import com.scenemaxeng.compiler.IKCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.projector.ik.IKControlComponent;
import com.scenemaxeng.projector.ik.IKLayer;

public class IKCommandController extends SceneMaxBaseController {
    public IKCommandController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, IKCommand cmd) {
        super(app, prg, scope, cmd);
    }

    @Override
    public boolean run(float tpf) {
        if (forceStop) {
            return true;
        }
        IKCommand ikCommand = (IKCommand) cmd;
        if (!targetCalculated) {
            if (findTargetVar() != 0) {
                return true;
            }
            targetCalculated = true;
        }

        if (ikCommand.action == IKCommand.ACTION_APPLY) {
            Object value = new ActionLogicalExpressionVm(ikCommand.ikNameExpr, scope).evaluate();
            if (value != null) {
                app.applyIK(targetVar, value.toString());
            }
            return true;
        }
        if (ikCommand.action == IKCommand.ACTION_REMOVE) {
            app.getIKRuntimeSystem().remove(targetVar);
            return true;
        }

        IKControlComponent control = app.ik(targetVar);
        if (control == null) {
            app.handleRuntimeError("IK is not applied to '" + ikCommand.ownerVarName + "'. Use "
                    + ikCommand.ownerVarName + ".ik = \"resource_id\" first.");
            return true;
        }
        IKLayer layer = control.findLayer(ikCommand.layerId);
        if (layer == null) {
            app.handleRuntimeError("IK layer '" + ikCommand.layerId + "' was not found on '" + ikCommand.ownerVarName + "'.");
            return true;
        }

        if (ikCommand.targetEntityPos != null) {
            applyTarget(control, ikCommand.layerId, ikCommand.targetEntityPos);
        }
        Float blend = evalFloat(ikCommand.blendExpr);
        Float weight = evalFloat(ikCommand.weightExpr);
        if (ikCommand.action == IKCommand.ACTION_SET_TARGET) {
            return true;
        } else if (ikCommand.action == IKCommand.ACTION_SET_WEIGHT) {
            if (weight != null) {
                control.setLayerWeight(ikCommand.layerId, weight);
            }
        } else if (ikCommand.action == IKCommand.ACTION_SET_BLEND) {
            if (blend != null) {
                control.setLayerBlend(ikCommand.layerId, blend);
            }
        } else if (ikCommand.action == IKCommand.ACTION_PLAY) {
            control.playLayer(ikCommand.layerId, weight, blend);
        } else if (ikCommand.action == IKCommand.ACTION_STOP) {
            control.stopLayer(ikCommand.layerId, blend);
        }
        return true;
    }

    private void applyTarget(IKControlComponent control, String layerId, EntityPos entityPos) {
        Spatial targetSpatial = app.resolveEntityPosSpatial(prg, scope, entityPos);
        String targetName = runtimeEntityName(entityPos);
        control.setLayerTarget(layerId, targetName, targetSpatial);
    }

    private String runtimeEntityName(EntityPos entityPos) {
        if (entityPos == null || entityPos.entityName == null || entityPos.entityName.isBlank()) {
            return "";
        }
        RunTimeVarDef runtime = app.findVarRuntime(prg, scope, entityPos.entityName);
        return runtime == null ? entityPos.entityName : runtime.varName;
    }

    private Float evalFloat(com.abware.scenemaxlang.parser.SceneMaxParser.Logical_expressionContext expr) {
        if (expr == null) {
            return null;
        }
        Object value = new ActionLogicalExpressionVm(expr, scope).evaluate();
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        if (value != null) {
            try {
                return Float.parseFloat(value.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
