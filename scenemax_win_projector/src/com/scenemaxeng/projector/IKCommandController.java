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
                dispatchPersistentIKCommand(ikCommand, "{network_entity}.ik = "
                        + quotedSceneMaxString(value.toString()), ikApplySlot());
            }
            return true;
        }
        if (ikCommand.action == IKCommand.ACTION_REMOVE) {
            app.getIKRuntimeSystem().remove(targetVar);
            dispatchPersistentIKCommand(ikCommand, "{network_entity}.ik = empty", ikApplySlot());
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
            dispatchPersistentIKCommand(ikCommand, buildIKLayerPropertyCommand(ikCommand, "target",
                    targetExpression(ikCommand.targetEntityPos)), ikLayerSlot(ikCommand.layerId));
            return true;
        } else if (ikCommand.action == IKCommand.ACTION_SET_WEIGHT) {
            if (weight != null) {
                control.setLayerWeight(ikCommand.layerId, weight);
                dispatchPersistentIKCommand(ikCommand, buildIKLayerPropertyCommand(ikCommand, "weight",
                        networkNumber(weight)), ikLayerSlot(ikCommand.layerId));
            }
        } else if (ikCommand.action == IKCommand.ACTION_SET_BLEND) {
            if (blend != null) {
                control.setLayerBlend(ikCommand.layerId, blend);
                dispatchPersistentIKCommand(ikCommand, buildIKLayerPropertyCommand(ikCommand, "blend",
                        networkNumber(blend)), ikLayerSlot(ikCommand.layerId));
            }
        } else if (ikCommand.action == IKCommand.ACTION_PLAY) {
            control.playLayer(ikCommand.layerId, weight, blend);
            dispatchPersistentIKCommand(ikCommand, buildIKLayerPlayCommand(ikCommand, weight, blend),
                    ikLayerSlot(ikCommand.layerId));
        } else if (ikCommand.action == IKCommand.ACTION_STOP) {
            control.stopLayer(ikCommand.layerId, blend);
            dispatchPersistentIKCommand(ikCommand, buildIKLayerStopCommand(ikCommand, blend),
                    ikLayerSlot(ikCommand.layerId));
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

    private void dispatchPersistentIKCommand(IKCommand ikCommand, String commandText, int slot) {
        if (commandText == null || commandText.isBlank()) {
            return;
        }
        dispatchMultiplayerCommand(commandText);
        startPersistentMultiplayerCommand(targetVar, slot, commandText);
    }

    private String buildIKLayerPropertyCommand(IKCommand ikCommand, String property, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return "{network_entity}.ik." + ikCommand.layerId + "." + property + " = " + value;
    }

    private String buildIKLayerPlayCommand(IKCommand ikCommand, Float weight, Float blend) {
        StringBuilder command = new StringBuilder();
        command.append("{network_entity}.ik.")
                .append(ikCommand.layerId)
                .append(".play");
        boolean hasOption = false;
        String target = targetExpression(ikCommand.targetEntityPos);
        if (target != null && !target.isBlank()) {
            command.append(": target ").append(target);
            hasOption = true;
        }
        if (blend != null) {
            command.append(hasOption ? ", " : ": ")
                    .append("blend ")
                    .append(networkNumber(blend));
            hasOption = true;
        }
        if (weight != null) {
            command.append(hasOption ? ", " : ": ")
                    .append("weight ")
                    .append(networkNumber(weight));
        }
        return command.toString();
    }

    private String buildIKLayerStopCommand(IKCommand ikCommand, Float blend) {
        StringBuilder command = new StringBuilder();
        command.append("{network_entity}.ik.")
                .append(ikCommand.layerId)
                .append(".stop");
        if (blend != null) {
            command.append(": blend ").append(networkNumber(blend));
        }
        return command.toString();
    }

    private String targetExpression(EntityPos entityPos) {
        String runtimeName = runtimeEntityName(entityPos);
        if (runtimeName == null || runtimeName.isBlank()) {
            return "";
        }
        StringBuilder target = new StringBuilder(multiplayerEntityPlaceholder(runtimeName));
        if (entityPos.entityJointName != null && !entityPos.entityJointName.isBlank()) {
            target.append(".\"").append(quotedJointName(entityPos.entityJointName)).append("\"");
        }
        return target.toString();
    }

    private int ikApplySlot() {
        return MULTIPLAYER_ACTION_SLOT_STRUCTURAL_BASE + 1;
    }

    private int ikLayerSlot(String layerId) {
        int hash = layerId == null ? 0 : layerId.hashCode();
        return MULTIPLAYER_ACTION_SLOT_STRUCTURAL_BASE + 2 + Math.floorMod(hash, 180);
    }

    private String quotedSceneMaxString(String value) {
        return "\"" + (value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")) + "\"";
    }

    private String quotedJointName(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
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
