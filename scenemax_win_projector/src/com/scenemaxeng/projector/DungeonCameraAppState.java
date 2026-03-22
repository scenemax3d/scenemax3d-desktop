package com.scenemaxeng.projector;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Spatial;
import com.scenemaxeng.compiler.FpsCameraCommand;

public class DungeonCameraAppState extends BaseAppState {

    private final SceneMaxScope scope;
    private final FpsCameraCommand cmd;
    private SceneMaxApp app;
    private Camera cam;

    public Spatial target;
    public Vector3f offset;

    public float damping = 1;

    public DungeonCameraAppState(SceneMaxScope scope, FpsCameraCommand cmd, Spatial target) {
        this.target=target;
        this.scope=scope;
        this.cmd=cmd;
        //this.offset=offset;
    }

    @Override
    public void update(float tpf) {


        if(cmd.offsetYExpr!=null) {
            Double offsetY = (Double)new ActionLogicalExpression(cmd.offsetYExpr,this.scope).evaluate();
            this.offset.setY(offsetY.floatValue());
        }

        if(cmd.offsetXExpr!=null) {
            Double offsetX = (Double)new ActionLogicalExpression(cmd.offsetXExpr,this.scope).evaluate();
            this.offset.setX(offsetX.floatValue());
        }

        if(cmd.offsetZExpr!=null) {
            Double offsetZ = (Double)new ActionLogicalExpression(cmd.offsetZExpr,this.scope).evaluate();
            this.offset.setZ(offsetZ.floatValue());
        }

        Vector3f desiredPosition = target.getWorldTranslation().add(offset);
        Vector3f position = new Vector3f();
        position.interpolateLocal(cam.getLocation(), desiredPosition, tpf * damping);

        cam.setLocation(position);
        cam.lookAt(target.getWorldTranslation(), Vector3f.UNIT_Y);

    }


    @Override
    protected void initialize(Application app) {
        this.app=(SceneMaxApp)app;
        this.cam = app.getCamera();

        this.offset = new Vector3f(0,1,-8);   //cam.getLocation().subtract(this.target.getWorldTranslation()) ;
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
