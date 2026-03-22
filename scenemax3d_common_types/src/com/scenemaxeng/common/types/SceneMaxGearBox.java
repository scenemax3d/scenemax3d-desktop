package com.scenemaxeng.common.types;

import java.util.ArrayList;
import java.util.List;

public class SceneMaxGearBox {

    public static class SceneMaxGear {
        public SceneMaxGear(float start, float end) {
            this.start=start;
            this.end=end;
        }
        public float start;
        public float end;
    }

    public List<SceneMaxGear> gears = new ArrayList<>();

}
