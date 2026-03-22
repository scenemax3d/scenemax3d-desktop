package com.scenemaxeng.projector;
import com.jme3.bullet.animation.PhysicsLink;
import com.jme3.bullet.collision.PhysicsCollisionEvent;
import com.jme3.bullet.collision.PhysicsCollisionListener;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;

public class CollisionListener extends RigidBodyControl implements PhysicsCollisionListener {

    //private static GenericDataTransformer genericData = new GenericDataTransformer();
    private final SceneMaxApp app;
    private Vector3f ptA = new Vector3f();
    private Vector3f ptB = new Vector3f();
    private String jointName;

    public CollisionListener(SceneMaxApp app) {
        this.app=app;
    }

    @Override
    public void collision(PhysicsCollisionEvent e) {

        PhysicsCollisionObject pcoA = e.getObjectA();
        PhysicsCollisionObject pcoB = e.getObjectB();
        String a,b;

        Object uoA = pcoA.getUserObject();
        Object uoB = pcoB.getUserObject();

        Spatial sa=getSpatialFromUserObject(uoA);
        String jointNameA = jointName;
        Spatial sb=getSpatialFromUserObject(uoB);
        String jointNameB = jointName;

        if(sa==sb) {
            // inner collision between 2 bones
            return;
        }

        if(sa!=null && sb!=null) {

            a = sa.getUserData("key");
            b = sb.getUserData("key");

            if(!app.scenePaused) {
                app.checkCollision(a, b, jointNameA, jointNameB);
            }
        }

    }

    private Spatial getSpatialFromUserObject(Object userObject) {

        jointName="";
        try {
            if (userObject instanceof Spatial) {
                return (Spatial) userObject;
            } else if (userObject instanceof PhysicsLink) {
                PhysicsLink l = (PhysicsLink) userObject;
                if(l.getArmatureJoint()!=null) {
                    jointName = l.getArmatureJoint().getName();
                    return findKeyParent(l.getControl().getSpatial());

                    //return l.getControl().getSpatial().getParent();
                } else {
                    jointName = l.getBone().getName();
                    return findKeyParent(l.getControl().getSpatial());
                    //return l.getControl().getSpatial().getParent();
                }


            }
        }catch(Exception e) {

        }
        return null;

    }

    private Spatial findKeyParent(Spatial sp) {
        Spatial s1 = sp.getParent();
        if(s1==null) {
            return null;
        }

        if(s1.getUserData("key")!=null) {
            return s1;
        }

        return findKeyParent(s1);

    }

}
