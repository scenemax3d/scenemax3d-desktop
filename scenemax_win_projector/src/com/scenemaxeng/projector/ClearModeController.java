package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ClearModeCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SwitchModeCommand;

public class ClearModeController extends SceneMaxBaseController {

    public ClearModeController(SceneMaxApp app, ProgramDef prg, SceneMaxScope thread, ClearModeCommand cmd) {
        super(app, prg, thread, cmd);
    }

    public boolean run(float tpf) {

        if (forceStop) return true;

        if (!targetCalculated) {
            targetCalculated = true;
            findTargetVar();

            ClearModeCommand cmd = (ClearModeCommand) this.cmd;
            if(cmd.modeToClear== SwitchModeCommand.CHARACTER) {
                app.clearCharacterControl(targetVar, this.targetVarDef);
                dispatchMultiplayerCharacterModeClearCommand();
            }

        }

        return true;

    }

    private void dispatchMultiplayerCharacterModeClearCommand() {
        String commandText = "{network_entity}.clear character mode";
        dispatchMultiplayerCommand(commandText);
        startPersistentMultiplayerCommand(targetVar,
                SwitchModeController.MULTIPLAYER_ACTION_SLOT_CHARACTER_MODE,
                commandText);
    }


}
