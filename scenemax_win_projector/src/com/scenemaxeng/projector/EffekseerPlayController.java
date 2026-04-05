package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.EffekseerPlayCommand;
import com.scenemaxeng.compiler.ProgramDef;

import java.util.Map;

public class EffekseerPlayController extends SceneMaxBaseController {

    public EffekseerPlayController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, EffekseerPlayCommand cmd) {
        super(app, prg, scope, cmd);
    }

    @Override
    public boolean run(float tpf) {
        if (forceStop) return true;

        EffekseerPlayCommand cmd = (EffekseerPlayCommand) this.cmd;
        if (!targetCalculated) {
            targetCalculated = true;
            findTargetVar();
        }

        Double x = null;
        Double y = null;
        Double z = null;
        RunTimeVarDef posEntity = null;

        if (cmd.xExpr != null) {
            x = (Double) new ActionLogicalExpressionVm(cmd.xExpr, scope).evaluate();
            y = (Double) new ActionLogicalExpressionVm(cmd.yExpr, scope).evaluate();
            z = (Double) new ActionLogicalExpressionVm(cmd.zExpr, scope).evaluate();
        } else if (cmd.entityPos != null) {
            posEntity = app.findVarRuntime(prg, scope, cmd.entityPos.entityName);
        }

        float playbackSpeed = 1.0f;
        float[] inputs = new float[] {0f, 0f, 0f, 0f};
        for (Map.Entry<String, org.antlr.v4.runtime.ParserRuleContext> entry : cmd.attrExprs.entrySet()) {
            float value = Float.parseFloat(new ActionLogicalExpressionVm(entry.getValue(), scope).evaluate().toString());
            String key = entry.getKey();
            if ("play_back_speed".equals(key) || "playback_speed".equals(key) || "speed".equals(key)) {
                playbackSpeed = value;
            } else if ("input0".equals(key) || "homing force".equals(key)) {
                inputs[0] = value;
            } else if ("input1".equals(key) || "orbit bias".equals(key)) {
                inputs[1] = value;
            } else if ("input2".equals(key) || "velocity damping".equals(key)) {
                inputs[2] = value;
            } else if ("input3".equals(key)) {
                inputs[3] = value;
            }
        }

        app.playEffekseerEffect(targetVar, x, y, z, posEntity, playbackSpeed, inputs);
        return true;
    }
}
