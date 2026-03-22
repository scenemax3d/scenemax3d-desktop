package com.scenemaxeng.projector;

import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.scenemaxeng.compiler.*;

public class MoveToControllerLight extends SceneMaxBaseController {

    private Vector3f dir;
    private Spatial targetSpatial;
    private Vector3f targetVector;
    private Vector3f startPos;
    private float timePassed=0;
    private float targetTime;
    private float totalDist;
    private Vector3f lastTimePos;

    public MoveToControllerLight(SceneMaxApp app, ProgramDef prg, SceneMaxScope thread, MoveToCommand cmd) {
        super(app, prg, thread, cmd);
    }

    public void setTargetDetails(String targetVar, Spatial targetSpatial, float x, float y, float z, float targetTime) {
        targetVector = new Vector3f(x,y,z);
        this.targetTime=targetTime;
        this.targetSpatial=targetSpatial;

        this.targetVar=targetVar;

    }

    public boolean run(float tpf) {

        if (forceStop) return true;

        if (!targetCalculated) {

            targetCalculated = true;
            Vector3f targetWorldTrans = targetSpatial.getWorldTranslation();
            dir = targetVector.subtract(targetWorldTrans);
            dir = dir.normalize();

            startPos = targetWorldTrans.clone();
            Vector3f targetPos = targetVector.clone();

            totalDist = targetPos.distance(startPos);

        }

        timePassed+=tpf;
        if(timePassed>targetTime) {
            timePassed=targetTime;
        }

        Vector3f step = dir.mult(timePassed/targetTime*totalDist);
        Vector3f timePos = startPos.add(step);
        Vector3f offset=lastTimePos==null?step:timePos.subtract(lastTimePos);
        lastTimePos=timePos;
        targetSpatial.move(offset);

        return  timePassed==targetTime;

    }


}
