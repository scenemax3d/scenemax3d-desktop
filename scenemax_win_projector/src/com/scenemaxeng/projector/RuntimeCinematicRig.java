package com.scenemaxeng.projector;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class RuntimeCinematicRig {
    public String id;
    public String name;
    public Vector3f position = new Vector3f();
    public Quaternion rotation = new Quaternion();
    public Vector3f scale = new Vector3f(1f, 1f, 1f);
    public String targetEntityName = "";
    public Vector3f targetOffset = new Vector3f(0f, 1.5f, 0f);
    public String easeIn = "linear";
    public String easeOut = "linear";
    public Vector3f relativeRigPositionToTarget = new Vector3f();
    public Quaternion relativeRigRotationToTarget = new Quaternion();
    public boolean hasRelativeTargetPlacement = false;
    public final Map<String, RuntimeCinematicTrack> tracksById = new HashMap<>();
    public final List<RuntimeCinematicSegment> segments = new ArrayList<>();
}
