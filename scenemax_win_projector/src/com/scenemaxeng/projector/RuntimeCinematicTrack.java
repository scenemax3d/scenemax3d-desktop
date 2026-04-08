package com.scenemaxeng.projector;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

class RuntimeCinematicTrack {
    public String id;
    public String name;
    public Vector3f localPosition = new Vector3f();
    public Quaternion localRotation = new Quaternion();
    public Vector3f localScale = new Vector3f(1f, 1f, 1f);
    public float radiusX = 2.5f;
    public float radiusZ = 2.5f;
    public int anchorCount = 360;
}
