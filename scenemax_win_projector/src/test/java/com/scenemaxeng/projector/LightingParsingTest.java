package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.LightVariableDef;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LightingParsingTest {

    @Test
    public void parsesDesignerFriendlyLightDeclarations() {
        String code = "sun => Lights.directional : color \"#fff3d2\", intensity 3.0, direction (-0.3,-0.8,-0.4), shadow high\n"
                + "lamp => Lights.point : pos (2,4,1), color warm, intensity 900 lumens, range 12, shadow medium\n"
                + "stageSpot => Lights.spot : pos (0,6,-4), look at player1, angle 35, intensity 2500, shadow on\n"
                + "environment => Lights.sky : preset \"Night Neon\", exposure 0.2, ambient \"#223344\"";

        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(code);

        assertTrue(prg.syntaxErrors.isEmpty());
        assertLight(prg, "sun", "directional");
        assertLight(prg, "lamp", "point");
        assertLight(prg, "stageSpot", "spot");
        assertLight(prg, "environment", "sky");

        LightVariableDef lamp = (LightVariableDef) prg.getVar("lamp");
        assertEquals("warm", lamp.color);
        assertEquals("lumens", lamp.intensityUnit);
        assertEquals("medium", lamp.shadowMode);

        LightVariableDef sky = (LightVariableDef) prg.getVar("environment");
        assertEquals("Night Neon", sky.preset);
        assertEquals("#223344", sky.ambientColor);
    }

    private static void assertLight(ProgramDef prg, String name, String type) {
        assertNotNull(prg.getVar(name));
        assertTrue(prg.getVar(name) instanceof LightVariableDef);
        assertEquals(type, ((LightVariableDef) prg.getVar(name)).lightType);
    }
}
