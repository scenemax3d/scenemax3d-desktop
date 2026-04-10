package com.scenemaxeng.projector;

class RuntimeCameraSystemValue {
    static final String TYPE_FIGHTING = "fighting";
    static final String TYPE_THIRD_PERSON = "third_person";
    static final String TYPE_FIRST_PERSON = "first_person";
    static final String TYPE_RACING = "racing";
    static final String TYPE_PLATFORMER = "platformer";
    static final String TYPE_RTS = "rts";

    String systemType;
    String primaryTargetVar;
    String secondaryTargetVar;

    float distance = 12f;
    float depth = 14f;
    float height = 4f;
    float side = 0f;
    float minDistance = 10f;
    float maxDistance = 24f;
    float zoomFactor = 1.15f;
    float damping = 7.5f;
    float lookAhead = 0f;
    float deadZone = 1.5f;
    float verticalBias = 1.5f;
    float angle = 55f;
    float fov = 50f;
    float maxFov = 62f;

    Float minX;
    Float maxX;
    Float minY;
    Float maxY;
    Float minZ;
    Float maxZ;

    Float arenaMinX;
    Float arenaMaxX;
    Float arenaMinZ;
    Float arenaMaxZ;
}
