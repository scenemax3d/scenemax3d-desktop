package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ActionStatementBase;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SwitchModeCommand;

public class SwitchModeController extends SceneMaxBaseController {

    static final int MULTIPLAYER_ACTION_SLOT_CHARACTER_MODE = MULTIPLAYER_ACTION_SLOT_STRUCTURAL_BASE + 189;

    private boolean targetCalculated = false;

    public SwitchModeController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, ActionStatementBase cmd) {
        super(app, prg, scope, cmd);
    }

    public boolean run(float tpf) {
        if (forceStop) return true;

        SwitchModeCommand cmd = (SwitchModeCommand)this.cmd;

        if (!targetCalculated) {
            targetCalculated = true;
            findTargetVar();
        }

        if(cmd.switchTo==SwitchModeCommand.CHARACTER) {
            if(cmd.gravityExpr!=null) {
                cmd.gravityVal = (Double) new ActionLogicalExpressionVm(cmd.gravityExpr,this.scope).evaluate();
            }
            this.app.switchModelToCharacterMode(this.targetVar,cmd);
            dispatchMultiplayerCharacterModeCommand(cmd);
        } else if(cmd.switchTo==SwitchModeCommand.RAGDOLL) {
            this.app.switchModelToRagdollMode(this.targetVar);
        } else if(cmd.switchTo==SwitchModeCommand.KINEMATIC) {
            this.app.switchModelToKinematicMode(this.targetVar);
        } else if(cmd.switchTo==SwitchModeCommand.FLOATING) {
            this.app.switchModelToFloatingMode(this.targetVar);
        } else if(cmd.switchTo==SwitchModeCommand.RIGID_BODY) {

        }

        return true;

    }

    private void dispatchMultiplayerCharacterModeCommand(SwitchModeCommand cmd) {
        String commandText = "{network_entity}.switch to character mode : gravity "
                + networkNumber(cmd.gravityVal == null ? 9.8 : cmd.gravityVal);
        dispatchMultiplayerCommand(commandText);
        startPersistentMultiplayerCommand(targetVar, MULTIPLAYER_ACTION_SLOT_CHARACTER_MODE, commandText);
    }


}
