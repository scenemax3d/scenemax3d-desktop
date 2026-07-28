package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.NetworkEntitySendCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class NetworkEntitySendController extends SceneMaxBaseController {

    public NetworkEntitySendController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope,
                                       NetworkEntitySendCommand cmd) {
        super(app, prg, scope, cmd);
    }

    @Override
    public boolean run(float tpf) {
        NetworkEntitySendCommand send = (NetworkEntitySendCommand) cmd;
        Object eventValue = send.eventNameExpr == null
                ? null
                : new ActionLogicalExpressionVm(send.eventNameExpr, scope).evaluate();
        String eventName = eventValue == null ? "" : String.valueOf(eventValue).trim();
        if (!eventName.isEmpty()) {
            Object messageValue = send.messageExpr == null
                    ? null
                    : new ActionLogicalExpressionVm(send.messageExpr, scope).evaluate();
            app.sendNetworkEventToEntity(scope, send.targetVar, eventName, messageValue);
        }
        return true;
    }
}
