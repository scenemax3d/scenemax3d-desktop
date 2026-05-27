package com.scenemax.designer.physics;

import com.jme3.math.Vector3f;

class PhysicsSimulationSettings {
    enum CommandType {
        THROW,
        IMPULSE,
        FORCE,
        VELOCITY,
        ANGULAR_VELOCITY,
        TORQUE,
        STOP
    }

    enum TargetMode {
        TOWARD,
        AT,
        VECTOR,
        FORWARD,
        BACKWARD,
        LEFT,
        RIGHT,
        UP,
        DOWN
    }

    enum ArcMode {
        NONE,
        LOW,
        MEDIUM,
        HIGH,
        CUSTOM
    }

    CommandType commandType = CommandType.THROW;
    TargetMode targetMode = TargetMode.AT;
    ArcMode arcMode = ArcMode.MEDIUM;

    String objectName = "throwable";
    String targetName = "target";

    float power = 24f;
    boolean useAngle;
    float angleDegrees = 35f;
    float customArc = 0.5f;
    boolean useSpin;
    Vector3f spin = new Vector3f(0f, 8f, 0f);

    Vector3f vector = new Vector3f(0f, 8f, 20f);
    boolean torqueImpulse;
    float forceDuration = 0.5f;
    boolean useForceDuration = true;

    float mass = 1f;
    float drag = 0f;
    float gravity = 9.81f;
    float restitution = 0.35f;
    float floorFriction = 0.88f;
    float simulationDuration = 6f;

    Vector3f objectPosition = new Vector3f(0f, 1.05f, 0f);
    Vector3f targetPosition = new Vector3f(0f, 1.05f, 12f);

    PhysicsSimulationSettings copy() {
        PhysicsSimulationSettings copy = new PhysicsSimulationSettings();
        copy.commandType = commandType;
        copy.targetMode = targetMode;
        copy.arcMode = arcMode;
        copy.objectName = objectName;
        copy.targetName = targetName;
        copy.power = power;
        copy.useAngle = useAngle;
        copy.angleDegrees = angleDegrees;
        copy.customArc = customArc;
        copy.useSpin = useSpin;
        copy.spin = spin.clone();
        copy.vector = vector.clone();
        copy.torqueImpulse = torqueImpulse;
        copy.forceDuration = forceDuration;
        copy.useForceDuration = useForceDuration;
        copy.mass = mass;
        copy.drag = drag;
        copy.gravity = gravity;
        copy.restitution = restitution;
        copy.floorFriction = floorFriction;
        copy.simulationDuration = simulationDuration;
        copy.objectPosition = objectPosition.clone();
        copy.targetPosition = targetPosition.clone();
        return copy;
    }
}
