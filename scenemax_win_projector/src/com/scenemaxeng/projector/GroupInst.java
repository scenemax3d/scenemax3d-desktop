package com.scenemaxeng.projector;

import com.jme3.scene.Node;
import com.scenemaxeng.compiler.GroupDef;

public class GroupInst {

    public final SceneMaxScope scope;
    private final GroupDef def;
    public Node node;
    public EntityInstBase lastClosestRayCheck;

    public GroupInst(GroupDef gd, SceneMaxScope scope, Node n) {
        this.def = gd;
        this.node=n;
        this.scope=scope;
        this.scope.groups.put(gd.name,this);

    }


}
