package com.scenemaxeng.projector;

import com.abware.scenemaxlang.parser.SceneMaxParser;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.WhenStateCommand;

import java.util.ArrayList;
import java.util.List;

public class WhenStateEventController extends SceneMaxBaseController {

    private List<ActionLogicalExpression> expr = new ArrayList<>();
    private int exprIndex = 0;
    private WhenStateCommand cmd = null;
    private DoBlockController handler = null;

    public WhenStateEventController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, WhenStateCommand cmd) {
        super(app, prg, scope, cmd);
        this.isEventHandler = true;
        this.cmd = cmd;
    }

    public boolean run(float tpf) {

        if (forceStop) return true;

        if (!targetCalculated) {
            this.targetCalculated = true;
            for (SceneMaxParser.Logical_expressionContext logicalExpressionContext : this.cmd.whenExpr) {
                this.expr.add(new ActionLogicalExpression(logicalExpressionContext, this.scope));
            }
            this.exprIndex = this.expr.size()-1;
            this.handler = new DoBlockController(app,scope, this.cmd.doBlock);
            this.handler.async = cmd.isAsync;
            this.handler.setUIProxy(this.app);
        }

        if (!this.handler.isRunning) {
            ActionLogicalExpression expr = this.expr.get(this.exprIndex);
            Boolean res = (Boolean)expr.evaluate();
            if (res) {
                if (this.exprIndex == 0) {
                    this.handler.init();
                    this.handler.isRunning = true;
                } else {
                    this.exprIndex--;
                }
            } else {
                // check the previous expr before resetting the index
                // - if the prev expr is still valid, no need to reset the index
                if (this.exprIndex + 1 < this.expr.size()) {
                    expr = this.expr.get(this.exprIndex + 1);
                    res = (Boolean)expr.evaluate();
                    if (!res) {
                        // now it's safe to reset the index
                        this.exprIndex = this.expr.size() - 1;
                    }
                }

            }
        } else {
            this.handler.isRunning = !this.handler.run(tpf);
            if (!this.handler.isRunning) {
                this.exprIndex = this.expr.size()-1;
            }
        }

        return false;
    }

}
