package com.scenemaxeng.projector;

class RuntimeCameraSystemValue {
    static final String TYPE_FIGHTING = "fighting";

    String systemType;
    String playerVar;
    String opponentVar;

    float depth = 14f;
    float height = 4f;
    float side = 0f;
    float minDistance = 10f;
    float maxDistance = 24f;
    float zoomFactor = 1.15f;
    float damping = 7.5f;
    float lookAhead = 0f;
    float fov = 50f;
    float maxFov = 62f;

    Float arenaMinX;
    Float arenaMaxX;
    Float arenaMinZ;
    Float arenaMaxZ;
}
