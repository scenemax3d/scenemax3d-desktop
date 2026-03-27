package com.scenemaxeng.projector;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Spatial;
import com.scenemaxeng.compiler.FpsCameraCommand;

public class FollowCameraAppState extends BaseAppState {

    private final SceneMaxScope scope;
    private final FpsCameraCommand cmd;
    private SceneMaxApp app;
    private Camera cam;

    public Spatial target;
    public Vector3f offset;

    public float damping = 1;

    public FollowCameraAppState(SceneMaxApp app, SceneMaxScope scope, FpsCameraCommand cmd, Spatial target) {
        this.target=target;
        this.scope=scope;
        this.cmd=cmd;
        this.app=app;
        this.cam = app.getCamera();
        this.offset = new Vector3f(0,1,-5);
        //this.offset=offset;
    }

    @Override
    public void update(float tpf) {

        Double offsetX=0d, offsetY = 0d, offsetZ=0d;
        if(cmd.offsetYExpr!=null) {
            offsetY = (Double)new ActionLogicalExpressionVm(cmd.offsetYExpr,this.scope).evaluate();
        }

        if(cmd.offsetXExpr!=null) {
            offsetX = (Double)new ActionLogicalExpressionVm(cmd.offsetXExpr,this.scope).evaluate();
        }

        if(cmd.offsetZExpr!=null) {
            offsetZ = (Double)new ActionLogicalExpressionVm(cmd.offsetZExpr,this.scope).evaluate();
        }

        offset.set(offsetX.floatValue(),offsetY.floatValue(),offsetZ.floatValue());

        if(cmd.dampingExpr!=null) {
            damping = ((Double)new ActionLogicalExpressionVm(cmd.dampingExpr,this.scope).evaluate()).floatValue();
        }

        float[] camAngles = new float[3];
        cam.getRotation().toAngles(camAngles);

        float[] targetAngles = new float[3];
        target.getWorldRotation().toAngles(targetAngles);

        float angle = FastMath.interpolateLinear(tpf*damping,camAngles[1],targetAngles[1]);

        Quaternion rotation = new Quaternion().fromAngles(0,angle,0);

        //cam.setLocation(target.getWorldTranslation().subtract(offset)); //target.transform.position - (rotation * offset);
        cam.setLocation(target.getWorldTranslation().subtract(rotation.mult(offset)));
        cam.lookAt(target.getWorldTranslation(), Vector3f.UNIT_Y);


    }

    @Override
    protected void initialize(Application app) {

        Double offsetX=null, offsetY = null, offsetZ=null;
        if(cmd.offsetYExpr!=null) {
            offsetY = (Double)new ActionLogicalExpressionVm(cmd.offsetYExpr,this.scope).evaluate();
        }

        if(cmd.offsetXExpr!=null) {
            offsetX = (Double)new ActionLogicalExpressionVm(cmd.offsetXExpr,this.scope).evaluate();
        }

        if(cmd.offsetZExpr!=null) {
            offsetZ = (Double)new ActionLogicalExpressionVm(cmd.offsetZExpr,this.scope).evaluate();
        }

        Vector3f desiredPosition = target.getWorldTranslation();
        if(offsetZ!=null) {
            Vector3f forward = target.getWorldRotation().mult(Vector3f.UNIT_Z);
            desiredPosition = desiredPosition.add(forward.mult(offsetZ.floatValue()));
        }

        if(offsetY!=null) {
            Vector3f vert = target.getWorldRotation().mult(Vector3f.UNIT_Y);
            desiredPosition = desiredPosition.add(vert.mult(offsetY.floatValue()));
        }

        if(offsetX!=null) {
            Vector3f horz = target.getWorldRotation().mult(Vector3f.UNIT_X);
            desiredPosition = desiredPosition.add(horz.mult(offsetX.floatValue()));
        }

        cam.setLocation(desiredPosition);

    }

    @Override
    protected void cleanup(Application app) {

    }

    @Override
    protected void onEnable() {

    }

    @Override
    protected void onDisable() {

    }
}
