package com.scenemaxeng.projector;

import com.abware.scenemaxlang.parser.SceneMaxParser;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.WaitForCommand;

public class WaitForController extends SceneMaxBaseController {

    private SceneMaxParser.Logical_expressionContext waitForExpr;
    private boolean waitFinished = false;
    private String mappingName;

    public WaitForController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, WaitForCommand cmd) {
        super(app,prg,scope,cmd);
    }

    public boolean run(float tpf) {

        if (forceStop) return true;
        if(!this.targetCalculated) {
            WaitForCommand cmd = (WaitForCommand) this.cmd;

            if(cmd.waitForExpr!=null) {
                this.waitForExpr = cmd.waitForExpr;
            } else {

                this.mappingName = cmd.inputKey+"@"+scope.scopeId;
                app.getInputManager().addMapping(this.mappingName , new KeyTrigger(app.getKeyMapping().get(cmd.inputKey)));

                final ActionListener actionListener = new ActionListener() {
                    @Override
                    public void onAction(String name, boolean keyPressed, float tpf) {

                        if(keyPressed && name.equals(WaitForController.this.mappingName)) {
                            waitFinished=true;
                            app.getInputManager().deleteMapping(mappingName);
                        }

                    }
                };
                app.getInputManager().addListener(actionListener, this.mappingName);
            }

            this.targetCalculated=true;
        }

        if(this.waitForExpr!=null) {
            Object obj = new ActionLogicalExpressionVm(this.waitForExpr,this.scope).evaluate();
            if (obj instanceof Boolean) {
                return (Boolean) obj;
            }
        }

        return waitFinished;

    }

}
