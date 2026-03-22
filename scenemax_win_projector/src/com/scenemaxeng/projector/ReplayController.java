package com.scenemaxeng.projector;

import com.abware.scenemaxlang.parser.SceneMaxParser;
import com.jme3.math.FastMath;
import com.jme3.scene.Spatial;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.ReplayCommand;

import java.util.HashMap;

public class ReplayController extends SceneMaxBaseController {

    private VarInst data = null;
    private int startAt;
    private double speedUnit;
    private float timePassed=0;
    private Spatial targetSpatial;
    private int initialStart;
    private ReplayCommand command;
    private boolean pause = false;

    public static HashMap<String, ReplayController> controllers = new HashMap<>();


    public ReplayController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, ReplayCommand cmd) {
        super(app,prg,scope,cmd);
    }


    public boolean run(float tpf) {

        if (forceStop) return true;
        if (!this.targetCalculated) {
            ReplayCommand cmd = (ReplayCommand) this.cmd;
            this.command = cmd;
            findTargetVar();

            if(this.command.option==ReplayCommand.STOP) {
                stopPreviousController();
                return true;
            }

            if(this.command.option==ReplayCommand.PAUSE) {

                ReplayController ctl = controllers.get(targetVar);
                if(ctl!=null) {
                    ctl.pause=true;
                 }

                return true;
            }

            if(this.command.option==ReplayCommand.RESUME) {

                ReplayController ctl = controllers.get(targetVar);
                if(ctl!=null) {
                    ctl.pause=false;
                }

                return true;
            }


            if(this.command.option==ReplayCommand.CHANGE_SPEED) {

                ReplayController ctl = controllers.get(targetVar);
                if (ctl != null) {
                    ctl.setSpeed(this.command.speedExpr);

                }

                return true;

            }


            if(this.command.option==ReplayCommand.SWITCH_TO) {

                ReplayController ctl = controllers.get(targetVar);
                if(ctl!=null) {
                    data = scope.getVar(cmd.dataArrayName);
                    ctl.setData(data);
                    ctl.setScope(scope);
                    ctl.command = cmd;

                }

                return true;
            }

            stopPreviousController();
            controllers.put(targetVar,this);

            data = scope.getVar(cmd.dataArrayName);
            if(cmd.startAtExpr!=null) {
                startAt = ((Double)new ActionLogicalExpression(cmd.startAtExpr,scope).evaluate()).intValue();
                int mod = startAt%6;
                startAt-=mod;
                initialStart = startAt;
            }

            Double speed = (Double)new ActionLogicalExpression(cmd.speedExpr,scope).evaluate();
            speedUnit = speed/(data.values.size()/6);

            targetSpatial = app.getEntitySpatial(this.targetVar, this.targetVarDef.varType);

            setModelInitialPosition();
            setModelInitialRotation();

            this.targetCalculated=true;
        }

        if(pause) {
            return false;
        }

        timePassed+=tpf;
        boolean shouldStop = false;
        if(timePassed>=speedUnit) {
            shouldStop=moveNext();
            timePassed=0;
        }

        return shouldStop;
    }

    private void stopPreviousController() {
        ReplayController ctl = controllers.get(targetVar);
        if(ctl!=null) {
            ctl.forceStop=true;
            controllers.remove(targetVar);
        }
    }

    public void setSpeed(SceneMaxParser.Logical_expressionContext speedExpr) {

        Double speed = (Double)new ActionLogicalExpression(speedExpr,scope).evaluate();
        speedUnit = speed/(data.values.size()/6);
    }

    public void setData(VarInst data) {

        if(data.values.size()<=startAt || data.values.size()<=initialStart) {
            this.forceStop=true;
        } else {
            this.data=data;
        }

    }

    private boolean moveNext() {
        startAt += 6;

        if (startAt >= data.values.size()) {
            startAt = 0;
        }

        if(startAt==initialStart) {
            if(command.loopExpr!=null) {
                Object cond = new ActionLogicalExpression(command.loopExpr,this.scope).evaluate();
                if(!(cond instanceof Boolean && ((Boolean)cond))) {
                    return true;
                }
            } else {
                return true;
            }
        }

        double x = (double) data.values.get(startAt);
        double y = (double) data.values.get(startAt + 1);
        double z = (double) data.values.get(startAt + 2);

        double rx = (double) data.values.get(startAt + 3);
        double ry = (double) data.values.get(startAt + 4);
        double rz = (double) data.values.get(startAt + 5);


        if(this.command.offsetXExpr!=null) {
            Double offset = (Double)new ActionLogicalExpression(command.offsetXExpr,this.scope).evaluate();
            x=x+offset;
        }

        if(this.command.offsetYExpr!=null) {
            Double offset = (Double)new ActionLogicalExpression(command.offsetYExpr,this.scope).evaluate();
            y=y+offset;
        }

        if(this.command.offsetZExpr!=null) {
            Double offset = (Double)new ActionLogicalExpression(command.offsetZExpr,this.scope).evaluate();
            z=z+offset;
        }

        if(this.command.offsetRXExpr!=null) {
            Double offset = (Double)new ActionLogicalExpression(command.offsetRXExpr,this.scope).evaluate();
            rx=rx+offset;
        }

        if(this.command.offsetRYExpr!=null) {
            Double offset = (Double)new ActionLogicalExpression(command.offsetRYExpr,this.scope).evaluate();
            ry=ry+offset;
        }

        if(this.command.offsetRZExpr!=null) {
            Double offset = (Double)new ActionLogicalExpression(command.offsetRZExpr,this.scope).evaluate();
            rz=rz+offset;
        }


        MoveToControllerLight ctl = new MoveToControllerLight(app, prg, scope, null);
        ctl.setTargetDetails(targetVar, targetSpatial, (float) x, (float) y, (float) z, (float) speedUnit);
        ctl.async = true;
        app.registerController(ctl);

        RotateToControllerLight rc = new RotateToControllerLight(app, prg, scope, null);
        rc.setTargetDetails(targetVar,targetVarDef, 1, (float) rx, (float) speedUnit);
        rc.async = true;
        app.registerController(rc);

        rc = new RotateToControllerLight(app, prg, scope, null);
        rc.setTargetDetails(targetVar,targetVarDef, 2, (float) ry, (float) speedUnit);
        rc.async = true;
        app.registerController(rc);

        rc = new RotateToControllerLight(app, prg, scope, null);
        rc.setTargetDetails(targetVar, targetVarDef, 3, (float) rz, (float) speedUnit);
        rc.async = true;
        app.registerController(rc);

        targetSpatial.setUserData("replay_index",startAt);

        return false;
    }

    private void setModelInitialRotation() {

        double rx = (double) data.values.get(startAt + 3);
        double ry = (double) data.values.get(startAt + 4);
        double rz = (double) data.values.get(startAt + 5);

        targetSpatial.rotate((float)rx * FastMath.DEG_TO_RAD, (float)ry*FastMath.DEG_TO_RAD, (float)rz*FastMath.DEG_TO_RAD);

    }

    private void setModelInitialPosition() {

        double x = (double) data.values.get(startAt);
        double y = (double) data.values.get(startAt + 1);
        double z = (double) data.values.get(startAt + 2);

        targetSpatial.setLocalTranslation((float)x,(float)y,(float)z);
    }

}
