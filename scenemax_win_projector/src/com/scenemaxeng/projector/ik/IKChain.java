package com.scenemaxeng.projector.ik;

import com.jme3.anim.Joint;

public class IKChain {
    private final Joint root;
    private final Joint middle;
    private final Joint secondMiddle;
    private final Joint end;

    public IKChain(Joint root, Joint middle, Joint end) {
        this(root, middle, null, end);
    }

    public IKChain(Joint root, Joint middle, Joint secondMiddle, Joint end) {
        this.root = root;
        this.middle = middle;
        this.secondMiddle = secondMiddle;
        this.end = end;
    }

    public Joint getRoot() {
        return root;
    }

    public Joint getMiddle() {
        return middle;
    }

    public Joint getSecondMiddle() {
        return secondMiddle;
    }

    public Joint getEnd() {
        return end;
    }

    public boolean isComplete() {
        return root != null && middle != null && end != null;
    }

    public boolean isThreeBoneComplete() {
        return root != null && middle != null && secondMiddle != null && end != null;
    }
}
