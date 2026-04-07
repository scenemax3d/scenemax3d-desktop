package com.scenemax.designer.cinematic;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import org.json.JSONObject;

/**
 * Serializable authoring data for a cinematic ellipse track.
 */
public class CinematicTrackData {

    public static final int DEFAULT_ANCHOR_COUNT = 360;

    private float radiusX = 2.5f;
    private float radiusZ = 2.5f;
    private int anchorCount = DEFAULT_ANCHOR_COUNT;
    private int selectedStartAnchor = -1;
    private int selectedEndAnchor = -1;
    private float previewSpeed = 30f;
    private int playbackStartAnchor = -1;
    private float playbackCursorAnchor = -1f;

    public float getRadiusX() {
        return radiusX;
    }

    public void setRadiusX(float radiusX) {
        this.radiusX = Math.max(0.1f, radiusX);
    }

    public float getRadiusZ() {
        return radiusZ;
    }

    public void setRadiusZ(float radiusZ) {
        this.radiusZ = Math.max(0.1f, radiusZ);
    }

    public int getAnchorCount() {
        return Math.max(8, anchorCount);
    }

    public void setAnchorCount(int anchorCount) {
        this.anchorCount = Math.max(8, anchorCount);
    }

    public int getSelectedStartAnchor() {
        return selectedStartAnchor;
    }

    public void setSelectedStartAnchor(int selectedStartAnchor) {
        this.selectedStartAnchor = normalizeAnchor(selectedStartAnchor);
    }

    public int getSelectedEndAnchor() {
        return selectedEndAnchor;
    }

    public void setSelectedEndAnchor(int selectedEndAnchor) {
        this.selectedEndAnchor = normalizeAnchor(selectedEndAnchor);
    }

    public void clearSelection() {
        selectedStartAnchor = -1;
        selectedEndAnchor = -1;
    }

    public float getPreviewSpeed() {
        return Math.max(0.1f, previewSpeed);
    }

    public void setPreviewSpeed(float previewSpeed) {
        this.previewSpeed = Math.max(0.1f, previewSpeed);
    }

    public int getPlaybackStartAnchor() {
        return normalizeAnchor(playbackStartAnchor);
    }

    public void setPlaybackStartAnchor(int playbackStartAnchor) {
        this.playbackStartAnchor = normalizeAnchor(playbackStartAnchor);
    }

    public float getPlaybackCursorAnchor() {
        return playbackCursorAnchor;
    }

    public void setPlaybackCursorAnchor(float playbackCursorAnchor) {
        this.playbackCursorAnchor = playbackCursorAnchor;
    }

    public boolean hasPlaybackPreview() {
        return playbackStartAnchor >= 0 && playbackCursorAnchor >= 0f;
    }

    public void clearPlaybackPreview() {
        playbackStartAnchor = -1;
        playbackCursorAnchor = -1f;
    }

    public int normalizeAnchor(int index) {
        if (index < 0) {
            return -1;
        }
        int count = getAnchorCount();
        int normalized = index % count;
        return normalized < 0 ? normalized + count : normalized;
    }

    public Vector3f getAnchorLocalPoint(int index) {
        int normalized = normalizeAnchor(index);
        float angle = FastMath.TWO_PI * normalized / getAnchorCount();
        return new Vector3f(FastMath.cos(angle) * radiusX, 0f, FastMath.sin(angle) * radiusZ);
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("radiusX", radiusX);
        json.put("radiusZ", radiusZ);
        json.put("anchorCount", getAnchorCount());
        json.put("selectedStartAnchor", selectedStartAnchor);
        json.put("selectedEndAnchor", selectedEndAnchor);
        json.put("previewSpeed", getPreviewSpeed());
        return json;
    }

    public static CinematicTrackData fromJSON(JSONObject json) {
        CinematicTrackData data = new CinematicTrackData();
        data.setRadiusX((float) json.optDouble("radiusX", 2.5));
        data.setRadiusZ((float) json.optDouble("radiusZ", 2.5));
        data.setAnchorCount(json.optInt("anchorCount", DEFAULT_ANCHOR_COUNT));
        data.selectedStartAnchor = json.optInt("selectedStartAnchor", -1);
        data.selectedEndAnchor = json.optInt("selectedEndAnchor", -1);
        data.setPreviewSpeed((float) json.optDouble("previewSpeed", 30.0));
        return data;
    }
}
