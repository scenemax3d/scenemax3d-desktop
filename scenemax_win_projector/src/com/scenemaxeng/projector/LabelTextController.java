package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.LabelTextCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class LabelTextController extends SceneMaxBaseController {

    public LabelTextController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, LabelTextCommand cmd) {
        super(app, prg, scope, cmd);
    }

    @Override
    public boolean run(float tpf) {
        if (forceStop) return true;

        LabelTextCommand textCommand = (LabelTextCommand) cmd;
        if (!targetCalculated) {
            targetCalculated = true;
            findTargetVar();
        }
        Object value = new ActionLogicalExpressionVm(textCommand.textExpr, scope).evaluate();
        String textValue = value == null ? "" : value.toString();
        app.setLabelText(targetVar, textValue);
        dispatchMultiplayerLabelText(textValue);

        return true;
    }

    private void dispatchMultiplayerLabelText(String textValue) {
        String commandText = "{network_entity}.text = " + quotedSceneMaxString(textValue);
        dispatchMultiplayerCommand(commandText);
        startPersistentMultiplayerCommand(targetVar, MULTIPLAYER_ACTION_SLOT_STRUCTURAL_BASE + 190, commandText);
    }

    private String quotedSceneMaxString(String value) {
        return "\"" + (value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")) + "\"";
    }
}
