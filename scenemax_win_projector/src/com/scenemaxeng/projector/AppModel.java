package com.scenemaxeng.projector;

import com.scenemaxeng.common.types.*;

import com.jme3.anim.AnimClip;
import com.jme3.anim.AnimComposer;
import com.jme3.anim.Joint;
import com.jme3.anim.SkinningControl;
import com.jme3.animation.*;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

import java.util.Collection;
import java.util.List;

public class AppModel {

    public float accelerate=0;
    public float steer=0;
    public Node model;
    public Object physicalControl;
    public ResourceSetup resource;
    public Spatial skinningControlNode;
    public CharacterAction currentAction;
    public boolean isStatic;
    public EntityInstBase entityInst;
    private AnimChannel channel;
    private AnimControl control;

    private AnimComposer composer;
    public Transform resetTransform;


    public AppModel(Node m) {
        model=m;
    }

    public AnimChannel getChannel() {
        if(channel==null) {
            channel = control.createChannel();
        }

        return channel;
    }

    public AnimControl getAnimControl() {
        if(control==null) {
            Spatial sp = model.getChild(0);
            control= findAnimationControl(sp);
        }

        return control;
    }

    private AnimControl findAnimationControl(Spatial sp) {

        AnimControl ctl = sp.getControl(AnimControl.class);
        if(ctl!=null) {
            return ctl;
        }

        if(sp instanceof Node) {
            Node nd=(Node)sp;
            for(Spatial spChild:nd.getChildren()) {
                AnimControl ctlChild = findAnimationControl(spChild);
                if(ctlChild!=null) {
                    return ctlChild;
                }
            }
        }

        return null;

    }

    public AnimComposer getAnimComposer() {
        if(composer==null) {
            Spatial sp = model.getChild(0);
            composer= findAnimationComposer(sp);
        }

        return composer;
    }

    private AnimComposer findAnimationComposer(Spatial sp) {

        AnimComposer ctl = sp.getControl(AnimComposer.class);
        if(ctl!=null) {
            return ctl;
        }

        if(sp instanceof Node) {
            Node nd=(Node)sp;
            for(Spatial spChild:nd.getChildren()) {
                AnimComposer ctlChild = findAnimationComposer(spChild);
                if(ctlChild!=null) {
                    return ctlChild;
                }
            }
        }

        return null;

    }

    public SkinningControl getSkinningControl() {
        return this.skinningControlNode.getControl(SkinningControl.class);
    }

    public Node getJointAttachementNode(String jointName) {

        if (this.resource.isJ3O()) {
            SkeletonControl sc = findSkeletonControl(model);
            if(sc!=null) {
                Node n= sc.getAttachmentsNode(jointName);
                return n;
            }

        } else {

            SkinningControl skinningControl = this.skinningControlNode.getControl(SkinningControl.class);
            if (skinningControl != null) {
                Node n= skinningControl.getAttachmentsNode(jointName);
                return n;

            }

        }

        return null;
    }


    public Vector3f getJointPosition(String jointName) {

        if(this.resource.isJ3O()) {

            Skeleton sk = findSkeleton(model);
            if(sk!=null) {

                Bone b = sk.getBone(jointName);
                if(b!=null) {
                    return b.getLocalPosition();
                }
            }

        } else {

            Spatial sp = model.getChild(0);
            SkinningControl skinningControl = sp.getControl(SkinningControl.class);
            if(skinningControl!=null) {
                Joint j = skinningControl.getArmature().getJoint(jointName);
                if(j!=null) {
                    return j.getLocalTranslation();
                }
            }

        }

        return null;

    }

    public String getJointsList() {

        String joints = "";

        if(this.resource.isJ3O()) {

            Skeleton sk = findSkeleton(model);
            if(sk!=null) {
                int cnt = sk.getBoneCount();
                for(int i=0;i<cnt;++i) {
                    joints += sk.getBone(i).getName() +",";

                }
            }

        } else {

            if(this.skinningControlNode!=null) {
                SkinningControl skinningControl = this.skinningControlNode.getControl(SkinningControl.class);
                if (skinningControl != null) {
                    List<Joint> jts = skinningControl.getArmature().getJointList();
                    for (Joint j : jts) {
                        joints += j.getName() + ",";
                    }
                }
            }

        }

        return joints;

    }


    public String getAnimationsList() {

        String animations = "";

        if(this.resource.isJ3O()) {

            AnimControl ctl = this.getAnimControl();
            if(ctl!=null) {
                Collection<String> anims = ctl.getAnimationNames();
                for (String s : anims) {
                    animations += s + ", ";
                }
            }

        } else {

            AnimComposer ctl = this.getAnimComposer();
            if (ctl != null) {

                for (AnimClip ac : ctl.getAnimClips()) {
                    animations += ac.getName() + ", ";
                }

            }

        }

        if(animations.length()==0) {
            animations = "No animations found for this model";
        }

        return animations;

    }

    private SkeletonControl findSkeletonControl (Spatial spatial) {

        SkeletonControl control = spatial.getControl(SkeletonControl.class);
        if (control != null) {
            return control;
        }

        if (spatial instanceof Node) {
            Node node = (Node) spatial;
            for(Spatial s:node.getChildren()) {
                control = findSkeletonControl(s);
                if(control!=null) {
                    return control;
                }
            }

        }

        return null;
    }

    public Skeleton findSkeleton(Spatial spatial) {
        Skeleton r = null;
        final SkeletonControl control = spatial.getControl(SkeletonControl.class);
        if (control != null) {
            r = control.getSkeleton();
        }
        if (r == null && spatial instanceof Node) {
            Node node = (Node) spatial;
            for (int i = 0; r == null && i < node.getQuantity(); i++) {
                Spatial child = node.getChild(i);
                r = findSkeleton(child);
            }
        }
        return r;
    }


}
