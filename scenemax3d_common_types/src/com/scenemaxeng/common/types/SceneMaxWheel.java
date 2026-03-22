package com.scenemaxeng.common.types;

import com.jme3.math.Vector3f;

public class SceneMaxWheel {

    public float diameter;
    public float scale;
    public float length;
    public float maxForce;
    public Vector3f offset;
    public boolean steering;
    public float brake;
    public float friction;
    public float accelerationForce;

    public class Suspension {
        public float stiffness;
        public float compression;
        public float damping;
        public float length;
        public float maxForce;
    }

    public Suspension suspension = new Suspension();

}
