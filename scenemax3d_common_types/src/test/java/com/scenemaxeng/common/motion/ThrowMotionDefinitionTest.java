package com.scenemaxeng.common.motion;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ThrowMotionDefinitionTest {
    @Test
    public void roundTripsTargetArcDefinition() {
        ThrowMotionDefinition original = ThrowMotionDefinition.createTemplate("Returning Axe", ThrowMotionDefinition.TYPE_RETURNING);
        original.getDesignerMetadata().put("previewModelAssetId", "axe");
        original.getParameters().outboundDistance = 18.0;
        original.getParameters().returnSpeed = 24.0;

        ThrowMotionDefinition loaded = ThrowMotionDefinition.fromJSON(original.toJSON());

        assertEquals(original.getId(), loaded.getId());
        assertEquals(ThrowMotionDefinition.TYPE_RETURNING, loaded.getMotionType());
        assertEquals("axe", loaded.getDesignerMetadata().optString("previewModelAssetId"));
        assertEquals(18.0, loaded.getParameters().outboundDistance, 0.0001);
        assertEquals(24.0, loaded.getParameters().returnSpeed, 0.0001);
    }

    @Test
    public void validatesInvalidSpeed() {
        ThrowMotionDefinition definition = ThrowMotionDefinition.createTemplate("Broken Shot", ThrowMotionDefinition.TYPE_STRAIGHT);
        definition.getParameters().speed = 0.0;

        ThrowMotionValidationResult result = definition.validate();

        assertFalse(result.isValid());
    }

    @Test
    public void samplerProducesMotionSamples() {
        ThrowMotionDefinition definition = ThrowMotionDefinition.createTemplate("Arc", ThrowMotionDefinition.TYPE_TARGET_ARC);
        definition.getParameters().duration = 1.0;
        definition.getParameters().arcHeight = 2.0;

        List<ThrowMotionSample> samples = ThrowMotionSampler.sample(definition,
                new ThrowMotionSampler.PreviewScenario(), 1f / 30f);

        assertTrue(samples.size() > 10);
        assertEquals(0.0f, samples.get(0).getTime(), 0.0001f);
        assertTrue(samples.get(samples.size() / 2).getPosition().y > samples.get(0).getPosition().y);
    }
}
