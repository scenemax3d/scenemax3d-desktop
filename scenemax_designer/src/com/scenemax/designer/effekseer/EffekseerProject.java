package com.scenemax.designer.effekseer;

import com.jme3.math.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class EffekseerProject {

    private String name = "Effect";
    private boolean loop = true;
    private float endFrame = 120f;
    private final Vector3f targetLocation = new Vector3f();
    private final List<EffekseerSpriteEmitter> emitters = new ArrayList<>();
    private final List<String> diagnostics = new ArrayList<>();
    private int parsedNodeCount;
    private int previewableEmitterCount;
    private int skippedNodeCount;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name : "Effect";
    }

    public boolean isLoop() {
        return loop;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    public float getEndFrame() {
        return endFrame;
    }

    public void setEndFrame(float endFrame) {
        this.endFrame = endFrame;
    }

    public Vector3f getTargetLocation() {
        return targetLocation;
    }

    public List<EffekseerSpriteEmitter> getEmitters() {
        return emitters;
    }

    public List<String> getDiagnostics() {
        return diagnostics;
    }

    public void addDiagnostic(String diagnostic) {
        if (diagnostic != null && !diagnostic.isBlank()) {
            diagnostics.add(diagnostic);
        }
    }

    public int getParsedNodeCount() {
        return parsedNodeCount;
    }

    public void incrementParsedNodeCount() {
        parsedNodeCount++;
    }

    public int getPreviewableEmitterCount() {
        return previewableEmitterCount;
    }

    public void incrementPreviewableEmitterCount() {
        previewableEmitterCount++;
    }

    public int getSkippedNodeCount() {
        return skippedNodeCount;
    }

    public void incrementSkippedNodeCount() {
        skippedNodeCount++;
    }
}
