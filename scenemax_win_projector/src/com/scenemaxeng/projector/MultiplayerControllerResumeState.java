package com.scenemaxeng.projector;

class MultiplayerControllerResumeState {
    final int slot;
    final float elapsedSeconds;
    final float remainingSeconds;
    final float durationSeconds;

    MultiplayerControllerResumeState(int slot, float elapsedSeconds, float remainingSeconds, float durationSeconds) {
        this.slot = slot;
        this.elapsedSeconds = Math.max(0f, elapsedSeconds);
        this.remainingSeconds = Math.max(0f, remainingSeconds);
        this.durationSeconds = Math.max(0f, durationSeconds);
    }
}
