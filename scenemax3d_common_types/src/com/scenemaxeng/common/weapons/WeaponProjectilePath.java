package com.scenemaxeng.common.weapons;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WeaponProjectilePath {
    private boolean relativeToOrigin = true;
    private final List<Point> points = new ArrayList<>();

    public JSONObject toJSON() {
        JSONArray arr = new JSONArray();
        for (Point point : points) {
            arr.put(point.toJSON());
        }
        return new JSONObject()
                .put("relativeToOrigin", relativeToOrigin)
                .put("points", arr);
    }

    public static WeaponProjectilePath fromJSON(JSONObject json) {
        WeaponProjectilePath path = new WeaponProjectilePath();
        if (json == null) {
            return path;
        }
        path.relativeToOrigin = json.optBoolean("relativeToOrigin", path.relativeToOrigin);
        JSONArray arr = json.optJSONArray("points");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item != null) {
                    path.points.add(Point.fromJSON(item));
                }
            }
        }
        return path;
    }

    public boolean isRelativeToOrigin() {
        return relativeToOrigin;
    }

    public void setRelativeToOrigin(boolean relativeToOrigin) {
        this.relativeToOrigin = relativeToOrigin;
    }

    public List<Point> getPoints() {
        return points;
    }

    public List<Point> getReadOnlyPoints() {
        return Collections.unmodifiableList(points);
    }

    public void addPoint(double time, double x, double y, double z) {
        points.add(new Point(time, x, y, z));
    }

    public boolean hasUsablePoints() {
        return points.size() >= 2;
    }

    public static class Point {
        private double time;
        private double x;
        private double y;
        private double z;

        public Point() {
        }

        public Point(double time, double x, double y, double z) {
            this.time = time;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public JSONObject toJSON() {
            return new JSONObject()
                    .put("time", time)
                    .put("x", x)
                    .put("y", y)
                    .put("z", z);
        }

        public static Point fromJSON(JSONObject json) {
            Point point = new Point();
            if (json == null) {
                return point;
            }
            point.time = json.optDouble("time", point.time);
            point.x = json.optDouble("x", point.x);
            point.y = json.optDouble("y", point.y);
            point.z = json.optDouble("z", point.z);
            return point;
        }

        public double getTime() {
            return time;
        }

        public void setTime(double time) {
            this.time = time;
        }

        public double getX() {
            return x;
        }

        public void setX(double x) {
            this.x = x;
        }

        public double getY() {
            return y;
        }

        public void setY(double y) {
            this.y = y;
        }

        public double getZ() {
            return z;
        }

        public void setZ(double z) {
            this.z = z;
        }
    }
}
