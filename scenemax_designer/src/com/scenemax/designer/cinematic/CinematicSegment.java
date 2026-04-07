package com.scenemax.designer.cinematic;

import org.json.JSONObject;

/**
 * A selected arc range on a cinematic track, stored on the owning rig.
 */
public class CinematicSegment {

    private String trackId;
    private String trackName;
    private int startAnchor;
    private int endAnchor;
    private float speed = 30f;

    public CinematicSegment(String trackId, String trackName, int startAnchor, int endAnchor) {
        this.trackId = trackId;
        this.trackName = trackName;
        this.startAnchor = startAnchor;
        this.endAnchor = endAnchor;
    }

    public CinematicSegment(String trackId, String trackName, int startAnchor, int endAnchor, float speed) {
        this(trackId, trackName, startAnchor, endAnchor);
        this.speed = speed;
    }

    public String getTrackId() {
        return trackId;
    }

    public String getTrackName() {
        return trackName;
    }

    public int getStartAnchor() {
        return startAnchor;
    }

    public int getEndAnchor() {
        return endAnchor;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = Math.max(0.1f, speed);
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("trackId", trackId != null ? trackId : "");
        json.put("trackName", trackName != null ? trackName : "");
        json.put("startAnchor", startAnchor);
        json.put("endAnchor", endAnchor);
        json.put("speed", speed);
        return json;
    }

    public static CinematicSegment fromJSON(JSONObject json) {
        return new CinematicSegment(
                json.optString("trackId", ""),
                json.optString("trackName", ""),
                json.optInt("startAnchor", 0),
                json.optInt("endAnchor", 0),
                (float) json.optDouble("speed", 30.0));
    }

    public String toDisplayString() {
        return (trackName != null && !trackName.isBlank() ? trackName : "track")
                + ": " + startAnchor + "-" + endAnchor + " @ " + speed;
    }
}
