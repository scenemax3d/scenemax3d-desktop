package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.PrintStatementCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class PrintStatementContoller extends SceneMaxBaseController {

      public PrintStatementContoller(SceneMaxApp app, ProgramDef prg, PrintStatementCommand cmd, SceneMaxScope thread) {
        super(app,prg,thread,cmd);

    }

    public boolean run(float tpf)
    {

        PrintStatementCommand cmd = (PrintStatementCommand)this.cmd;
        String txt = new ActionLogicalExpression(cmd.text,this.scope).evaluate().toString();
        double x=-1,y=0,z=0,fontSize=0;

        if(cmd.x!=null) {
            x=Double.parseDouble(new ActionLogicalExpression(cmd.x,this.scope).evaluate().toString());
            y=Double.parseDouble(new ActionLogicalExpression(cmd.y,this.scope).evaluate().toString());
            z=Double.parseDouble(new ActionLogicalExpression(cmd.z,this.scope).evaluate().toString());
        }

        if(cmd.fontSize!=null) {
            fontSize = Double.parseDouble(new ActionLogicalExpression(cmd.fontSize,this.scope).evaluate().toString());
        }

        app.print(cmd.printChannel,txt,cmd.color,x,y,z,cmd.font, fontSize,cmd.append);
        return true;
    }


}
