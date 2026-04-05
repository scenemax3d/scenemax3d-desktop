package com.scenemax.designer.effekseer;

import com.jme3.math.Matrix4f;
import com.jme3.math.Vector3f;
import com.scenemax.effekseer.runtime.EffekseerNativeBridge;

import java.io.File;
import java.nio.ByteBuffer;

final class EffekseerNativePreview {

    private long contextHandle;
    private File loadedEffectFile;
    private volatile String lastStatus = "native preview not attempted";
    private File pendingEffectFile;
    private boolean pendingLoop = true;
    private float pendingPlaybackSpeed = 1.0f;
    private float pendingTargetX = 0.0f;
    private float pendingTargetY = 0.0f;
    private float pendingTargetZ = 0.0f;
    private boolean compositeEnabled;
    private boolean pendingUnload;
    private boolean pendingContextReset;

    public boolean isAvailable() {
        return EffekseerNativeBridge.isAvailable();
    }

    public String getAvailabilityMessage() {
        return EffekseerNativeBridge.getLoadMessage();
    }

    public boolean loadEffect(File effectFile, boolean loop, double playbackSpeed) {
        if (!isAvailable() || effectFile == null || !effectFile.isFile()) {
            lastStatus = !isAvailable()
                    ? "native bridge unavailable: " + getAvailabilityMessage()
                    : "runtime effect file missing";
            return false;
        }
        pendingEffectFile = effectFile;
        pendingLoop = loop;
        pendingPlaybackSpeed = (float) playbackSpeed;
        pendingUnload = false;
        pendingContextReset = true;
        lastStatus = "native effect queued for render-thread load: " + effectFile.getName();
        return true;
    }

    public void applyPreviewSettings(boolean loop, double playbackSpeed, Vector3f targetLocation) {
        pendingLoop = loop;
        pendingPlaybackSpeed = (float) playbackSpeed;
        if (targetLocation != null) {
            pendingTargetX = targetLocation.x;
            pendingTargetY = targetLocation.y;
            pendingTargetZ = targetLocation.z;
        } else {
            pendingTargetX = 0.0f;
            pendingTargetY = 0.0f;
            pendingTargetZ = 0.0f;
        }
        if (contextHandle != 0L) {
            EffekseerNativeBridge.setLooping(contextHandle, loop);
            EffekseerNativeBridge.setPlaybackSpeed(contextHandle, (float) playbackSpeed);
            EffekseerNativeBridge.setTargetLocation(contextHandle, pendingTargetX, pendingTargetY, pendingTargetZ);
        }
    }

    public void setCompositeEnabled(boolean enabled) {
        compositeEnabled = enabled;
        if (contextHandle != 0L) {
            EffekseerNativeBridge.setCompositeEnabled(contextHandle, enabled);
        }
    }

    public void updateCamera(Matrix4f view, Matrix4f projection) {
        if (contextHandle != 0L) {
            EffekseerNativeBridge.setCamera(contextHandle, view, projection);
        }
    }

    public void updateCamera(Matrix4f view, Matrix4f projection, Vector3f cameraPosition) {
        if (contextHandle != 0L) {
            float[] position = cameraPosition != null
                    ? new float[]{cameraPosition.x, cameraPosition.y, cameraPosition.z}
                    : null;
            EffekseerNativeBridge.setCamera(contextHandle, view, projection, position);
        }
    }

    public void update(float deltaSeconds) {
        if (contextHandle != 0L) {
            EffekseerNativeBridge.update(contextHandle, deltaSeconds);
        }
    }

    public void render(int viewportWidth, int viewportHeight) {
        syncPendingLoad();
        if (contextHandle != 0L) {
            EffekseerNativeBridge.render(contextHandle, viewportWidth, viewportHeight);
        }
    }

    public boolean readbackFrame(ByteBuffer targetBuffer, int viewportWidth, int viewportHeight) {
        return contextHandle != 0L
                && EffekseerNativeBridge.readbackFrame(contextHandle, targetBuffer, viewportWidth, viewportHeight);
    }

    public String getStatus() {
        return contextHandle != 0L
                ? EffekseerNativeBridge.getStatus(contextHandle)
                : lastStatus;
    }

    public boolean isLoaded(File effectFile) {
        return loadedEffectFile != null && effectFile != null && loadedEffectFile.equals(effectFile);
    }

    public void unload() {
        pendingUnload = true;
        pendingEffectFile = null;
        loadedEffectFile = null;
        lastStatus = contextHandle != 0L
                ? "native effect queued for unload"
                : "native effect unloaded";
    }

    public void dispose() {
        unload();
        if (contextHandle != 0L) {
            EffekseerNativeBridge.destroyPreviewContext(contextHandle);
            contextHandle = 0L;
        }
    }

    private void syncPendingLoad() {
        if (pendingUnload && contextHandle != 0L) {
            EffekseerNativeBridge.unloadEffect(contextHandle);
            pendingUnload = false;
            lastStatus = "native effect unloaded";
        }
        if (pendingContextReset && contextHandle != 0L) {
            EffekseerNativeBridge.destroyPreviewContext(contextHandle);
            contextHandle = 0L;
            loadedEffectFile = null;
            lastStatus = "native context reset on render thread";
        }
        if (pendingEffectFile == null) {
            return;
        }

        File effectFile = pendingEffectFile;
        if (contextHandle == 0L) {
            contextHandle = EffekseerNativeBridge.createPreviewContext(8000);
        }
        if (contextHandle == 0L) {
            lastStatus = "native context creation failed";
            return;
        }
        EffekseerNativeBridge.setCompositeEnabled(contextHandle, compositeEnabled);

        File assetRoot = effectFile.getParentFile();
        boolean loaded = EffekseerNativeBridge.loadEffect(
                contextHandle,
                effectFile.getAbsolutePath(),
                assetRoot != null ? assetRoot.getAbsolutePath() : effectFile.getParent());
        if (loaded) {
            loadedEffectFile = effectFile;
            EffekseerNativeBridge.setLooping(contextHandle, pendingLoop);
            EffekseerNativeBridge.setPlaybackSpeed(contextHandle, pendingPlaybackSpeed);
            EffekseerNativeBridge.setTargetLocation(contextHandle, pendingTargetX, pendingTargetY, pendingTargetZ);
            lastStatus = "native effect loaded on render thread: " + effectFile.getName();
            pendingEffectFile = null;
            pendingContextReset = false;
        } else {
            lastStatus = "native effect load failed on render thread: " + effectFile.getName();
        }
    }
}
