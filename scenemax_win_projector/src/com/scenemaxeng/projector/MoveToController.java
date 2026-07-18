package com.scenemaxeng.projector;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.scenemaxeng.compiler.*;

public class MoveToController extends SceneMaxBaseController {

    VariableDef moveToVarDef;
    String moveToTargetVar = "";


    float velocity = 0.01f;
    Vector3f dir;
    Spatial targetSpatial;
    Vector3f targetPos;
    Vector3f startPos;
    float timePassed=0;
    float targetTime;
    float totalDist;
    private boolean isCamera;
    private Vector3f lookingAt;
    private Spatial lookingAtEntity;
    private PositionStatement lookingAtPosStatement;
    private final Vector3f frameOffset = new Vector3f();
    private MotionEase.MotionEaseSpec motionEase;
    private boolean multiplayerCommandDispatched = false;
    private int multiplayerActionSequence = 0;

    //private static HashMap<String,MoveToController> activeMoveControllers = new HashMap<>();

    public MoveToController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, MoveToCommand cmd) {
        super(app, prg, scope, cmd);
    }


    public boolean run(float tpf) {
        if (forceStop) return true;

        if (!targetCalculated) {

            targetCalculated = true;

            MoveToCommand cmd = (MoveToCommand) this.cmd;
            findTargetVar();

            Spatial red = null;
            Vector3f redVector = null;

            if(cmd.lookingAtStatement!=null) {
                Spatial sp = calcMoveToTargetVar(cmd.lookingAtStatement.startEntity);

                if(sp==null) {
                    // error - probably user typed wrong object name
                    return true;
                }

                this.lookingAtEntity=sp;
                this.lookingAtPosStatement = cmd.lookingAtStatement;
            }

            if(cmd.posStatement!=null) {

                Spatial sp = calcMoveToTargetVar(cmd.posStatement.startEntity);

                if(sp==null) {
                    // error - probably user typed wrong object name
                    return true;
                }

                redVector = sp.getWorldTranslation();
                Quaternion locRot = sp.getLocalRotation();
                Util.calcPositionStatementVerbs(this.scope, cmd.posStatement,locRot,redVector);

            } else if(cmd.moveToTarget!=null) {

                red = calcMoveToTargetVar(cmd.moveToTarget);
                if(red==null) {
                    // error - probably user typed wrong object name
                    return true;
                }

            }

            targetTime = ((Double)new ActionLogicalExpressionVm(cmd.speedExpr,scope).evaluate()).floatValue();
            Double extraDist = 0.0;
            if(cmd.extraDistanceExpr!=null) {
                extraDist = (Double)new ActionLogicalExpressionVm(cmd.extraDistanceExpr,scope).evaluate();
            }

            //
            isCamera = this.targetVarDef.varType==VariableDef.VAR_TYPE_CAMERA;
            if(!isCamera) {
                targetSpatial = app.getEntitySpatial(this.targetVar, this.targetVarDef.varType);//sinbad
            }

            if(redVector==null) {
                if (red != null) {
                    redVector = red.getWorldTranslation();
                } else {
                    float x = ((Double) new ActionLogicalExpressionVm(cmd.moveToTargetXExpr, scope).evaluate()).floatValue();
                    float y = ((Double) new ActionLogicalExpressionVm(cmd.moveToTargetYExpr, scope).evaluate()).floatValue();
                    float z = ((Double) new ActionLogicalExpressionVm(cmd.moveToTargetZExpr, scope).evaluate()).floatValue();
                    redVector = new Vector3f(x, y, z);
                }
            }

            Vector3f targetWorldTrans = isCamera?app.getCamera().getLocation():targetSpatial.getWorldTranslation();
            dir = redVector.subtract(targetWorldTrans);
            dir = dir.normalize();

            Vector3f extraDistVec = dir.mult(extraDist.floatValue());
            startPos = targetWorldTrans.clone();
            targetPos = redVector.clone().add(extraDistVec);

            totalDist = targetPos.distance(startPos);
            motionEase = MotionEase.fromCommand(cmd, scope);
            dispatchMultiplayerMoveToCommand();

        }

        float previousProgress;
        float currentProgress;
        if (targetTime <= 0f) {
            previousProgress = 0f;
            currentProgress = 1f;
        } else {
            float previousTime = timePassed;
            timePassed += tpf;
            if(timePassed>targetTime) {
                timePassed=targetTime;
            }
            previousProgress = previousTime / targetTime;
            currentProgress = timePassed / targetTime;
        }

        float deltaProgress = MotionEase.delta(motionEase, previousProgress, currentProgress);
        frameOffset.set(dir).multLocal(totalDist * deltaProgress);

        Vector3f currPos = null;
        if(isCamera) {
            if(app.attachCameraNode!=null && app.attachCameraNode.isEnabled()){
                return true;
            }

            currPos = app.getCamera().getLocation().addLocal(frameOffset);
            app.getCamera().setLocation(currPos);
            if(this.lookingAtEntity!=null) {
                this.lookingAt = this.lookingAtEntity.getWorldTranslation().clone();
                Quaternion locRot = this.lookingAtEntity.getLocalRotation();
                Util.calcPositionStatementVerbs(this.scope, this.lookingAtPosStatement,locRot,this.lookingAt);
                app.getCamera().lookAt(this.lookingAt, Vector3f.UNIT_Y);
            }

        } else {
            targetSpatial.move(frameOffset);
            currPos = targetSpatial.getWorldTranslation();
        }

        boolean finished = timePassed == targetTime;
        if (finished) {
            endMultiplayerTimedAction(MULTIPLAYER_ACTION_SLOT_MOVE, multiplayerActionSequence);
            multiplayerActionSequence = 0;
        }
        return finished;//    currPos.distance(targetPos)<0.1f;

    }

    private Spatial calcMoveToTargetVar(String startEntity) {

        moveToVarDef = prg.getVar(startEntity);
        if (moveToVarDef == null) {
            //throw err
            return null;
        }

        moveToTargetVar = "";
        if (moveToVarDef.varType == VariableDef.VAR_TYPE_SPHERE || moveToVarDef.varType == VariableDef.VAR_TYPE_BOX) {
            int threadId = app.getEntityScopeId(scope, moveToVarDef.varName, moveToVarDef.varType);
            moveToTargetVar = moveToVarDef.varName + "@" + threadId;
        } else if (moveToVarDef.varType == VariableDef.VAR_TYPE_OBJECT) {
            EntityInstBase obj = (EntityInstBase) scope.getFuncScopeParam(moveToVarDef.varName);

            if (obj == null) {
                app.handleRuntimeError("Function argument '" + moveToVarDef.varName + "' is undefined");
                return null;
            }

            moveToTargetVar = obj.varDef.varName + "@" + obj.scope.scopeId;
            moveToVarDef = new VariableDef();// in order to avoid overriding varType
            moveToVarDef.varType = obj.varDef.varType;
        } else if (moveToVarDef.varType != VariableDef.VAR_TYPE_CAMERA) {
            int threadId = app.getEntityScopeId(scope, moveToVarDef.varName);
            moveToTargetVar = moveToVarDef.varName + "@" + threadId;
        }

        Spatial red = app.getEntitySpatial(moveToTargetVar,moveToVarDef.varType);//buggy
        if(red==null) {
            // error - probably user typed wrong object name
            return null;
        }

        return red; // OK continue

    }

    private void dispatchMultiplayerMoveToCommand() {
        if (multiplayerCommandDispatched || targetPos == null) {
            return;
        }
        multiplayerCommandDispatched = true;
        String command = "{network_entity}.move to ("
                + networkNumber(targetPos.x) + ","
                + networkNumber(targetPos.y) + ","
                + networkNumber(targetPos.z) + ") in "
                + networkNumber(targetTime) + " seconds";
        dispatchMultiplayerCommand(command);
        if (targetTime > 0f) {
            multiplayerActionSequence = startMultiplayerTimedAction(MULTIPLAYER_ACTION_SLOT_MOVE, targetTime, command);
        }
    }


}
