package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.NetworkEventHandlerCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class NetworkEventHandlerController extends SceneMaxBaseController {

    public NetworkEventHandlerController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope,
                                         NetworkEventHandlerCommand cmd) {
        super(app, prg, scope, cmd);
    }

    @Override
    public boolean run(float tpf) {
        NetworkEventHandlerCommand handler = (NetworkEventHandlerCommand) cmd;
        Object value = handler.eventNameExpr == null
                ? null
                : new ActionLogicalExpressionVm(handler.eventNameExpr, scope).evaluate();
        String eventName = value == null ? "" : String.valueOf(value).trim();
        if (!eventName.isEmpty() && handler.doBlock != null) {
            app.registerNetworkEventHandler(eventName, scope, handler.doBlock, handler.messageParamName);
            float intervalSeconds = evaluateIntervalSeconds(handler);
            if (intervalSeconds > 0f) {
                app.registerServerNetworkEvent(eventName, intervalSeconds);
            }
        }
        return true;
    }

    private float evaluateIntervalSeconds(NetworkEventHandlerCommand handler) {
        if (handler.serverIntervalSecondsExpr == null) {
            return 0f;
        }
        Object value = new ActionLogicalExpressionVm(handler.serverIntervalSecondsExpr, scope).evaluate();
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        if (value != null) {
            try {
                return Float.parseFloat(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return 0f;
    }
}
