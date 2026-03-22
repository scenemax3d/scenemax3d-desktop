package com.scenemaxeng.projector;

import com.scenemaxeng.common.types.*;

import com.jayfella.jme.vehicle.part.Engine;
import com.jme3.math.Spline;
import com.jme3.math.Vector3f;

public class SceneMax3DGenericEngine extends Engine {

    final private Spline spline;

    final private static Vector3f[] points = new Vector3f[]{
            new Vector3f(0f, 0.25f, 0f),
            new Vector3f(1_000f, 0.35f, 0f),
            new Vector3f(2_000f, 0.4f, 0f),
            new Vector3f(3_000f, 0.5f, 0f),
            new Vector3f(4_000f, 0.7f, 0f),
            new Vector3f(5_000f, 0.95f, 0f),
            new Vector3f(6_000f, 0.99f, 0f),
            new Vector3f(7_000f, 0.85f, 0f),
            new Vector3f(9_000f, 0.75f, 0f)
    };

    public SceneMax3DGenericEngine(SceneMaxEngine engine) {
        super(engine.name,engine.power*HP_TO_W,1000f, engine.maxRevs);
        spline = new Spline(Spline.SplineType.Linear, points, 0.1f, false);
    }

    @Override
    public float powerFraction(float rpm) {
        float result = evaluateSpline(spline, rpm);
        return result;
    }
}
