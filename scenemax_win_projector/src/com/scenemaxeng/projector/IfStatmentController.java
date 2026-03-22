package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.IfStatementCommand;
import com.scenemaxeng.compiler.DoBlockCommand;

public class IfStatmentController extends CompositeController {

    public IfStatementCommand ifCommand;

    private SceneMaxScope scope;
    private SceneMaxApp app;
    private ActionLogicalExpression ifExpr;


    public IfStatmentController(SceneMaxApp app, SceneMaxScope scope, IfStatementCommand cmd) {
        this.scope=scope;
        this.app=app;
        this.ifCommand=cmd;
        this.adhereToPauseStatus=false; // do block works even when the scene is paused

        ifExpr=new ActionLogicalExpression(ifCommand.expression,scope);

    }

    public boolean evalExpr(ActionLogicalExpression expr) {
        Object obj = expr.evaluate();
        if (obj instanceof Boolean) {
            //System.out.println("expression eval() = "+obj);
            return (Boolean) obj;
        }

        return true;
    }

    @Override
    public boolean run(float tpf) {

        // evaluate expression once
        if(this.size()==0) {
            if(evalExpr(ifExpr)) {
                DoBlockCommand cmd = ifCommand.doBlock;
                DoBlockController c = new DoBlockController((SceneMaxApp)app,scope, cmd);
                c.async = cmd.isAsync;
                this.add(c);
            } else if(ifCommand.elseIfCommands!=null && ifCommand.elseIfCommands.size()>0) {
                boolean elseifBranchExist = false;
                // check the else-if blocks
                for(int i=0;i<ifCommand.elseIfCommands.size();++i) {
                    IfStatementCommand elseifCmd = ifCommand.elseIfCommands.get(i);
                    if(evalExpr(new ActionLogicalExpression(elseifCmd.expression,scope))) {
                        DoBlockCommand cmd = elseifCmd.doBlock;
                        DoBlockController c = new DoBlockController((SceneMaxApp)app,scope,cmd);
                        c.app = (SceneMaxApp)app;
                        c.async = cmd.isAsync;
                        this.add(c);
                        elseifBranchExist=true;
                        break; // do not check other else-if blocks
                    }
                }

                if(!elseifBranchExist) {
                    if(ifCommand.elseCmd!=null) {
                        DoBlockController c = new DoBlockController((SceneMaxApp)app,scope,ifCommand.elseCmd);
                        c.app = (SceneMaxApp)app;
                        c.async = ifCommand.elseCmd.isAsync;
                        this.add(c);
                    }
                }

            } else if(ifCommand.elseCmd!=null) {
                DoBlockController c = new DoBlockController((SceneMaxApp)app,scope,ifCommand.elseCmd);
                c.app = (SceneMaxApp)app;
                c.async = ifCommand.elseCmd.isAsync;
                this.add(c);
            }
        }

        // run until finish
        return super.run(tpf);

    }
    
}
