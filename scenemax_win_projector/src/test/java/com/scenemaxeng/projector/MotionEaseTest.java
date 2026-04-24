package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.MotionEaseType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MotionEaseTest {

    @Test
    public void appliesExpectedEaseCurves() {
        assertEquals(0.5f, MotionEase.apply(MotionEaseType.LINEAR, 0.5f), 0.0001f);
        assertEquals(0.25f, MotionEase.apply(MotionEaseType.EASE_IN, 0.5f), 0.0001f);
        assertEquals(0.75f, MotionEase.apply(MotionEaseType.EASE_OUT, 0.5f), 0.0001f);
        assertEquals(0.5f, MotionEase.apply(MotionEaseType.EASE_IN_OUT, 0.5f), 0.0001f);
    }

    @Test
    public void easedDeltasStillCoverTheFullMotion() {
        float total = MotionEase.delta(MotionEaseType.EASE_IN_OUT, 0f, 0.25f)
                + MotionEase.delta(MotionEaseType.EASE_IN_OUT, 0.25f, 0.75f)
                + MotionEase.delta(MotionEaseType.EASE_IN_OUT, 0.75f, 1f);

        assertEquals(1f, total, 0.0001f);
    }
}
