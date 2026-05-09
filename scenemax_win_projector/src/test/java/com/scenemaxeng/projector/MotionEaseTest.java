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

    @Test
    public void appliesNamedCurvesAndWindowedEaseTime() {
        MotionEase.MotionEaseSpec cubic = new MotionEase.MotionEaseSpec(
                MotionEaseType.EASE_IN_OUT, "Cubic", new float[0]);
        assertEquals(0.5f, MotionEase.apply(cubic, 0.5f), 0.0001f);

        MotionEase.MotionEaseSpec windowed = new MotionEase.MotionEaseSpec(
                MotionEaseType.EASE_IN_OUT, "Cubic", new float[]{0.25f});
        assertEquals(0.25f, MotionEase.apply(windowed, 0.25f), 0.0001f);
        assertEquals(0.5f, MotionEase.apply(windowed, 0.5f), 0.0001f);
        assertEquals(0.75f, MotionEase.apply(windowed, 0.75f), 0.0001f);
    }

    @Test
    public void customCurveDeltasStillCoverFullMotion() {
        MotionEase.MotionEaseSpec elastic = new MotionEase.MotionEaseSpec(
                MotionEaseType.EASE_OUT, "Elastic", new float[]{1.2f, 0.4f});

        float total = MotionEase.delta(elastic, 0f, 0.2f)
                + MotionEase.delta(elastic, 0.2f, 0.6f)
                + MotionEase.delta(elastic, 0.6f, 1f);

        assertEquals(1f, total, 0.0001f);
    }
}
