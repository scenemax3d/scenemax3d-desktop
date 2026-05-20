package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.EffekseerPlayCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class EffekseerSyntaxParsingTest {

    @Test
    public void effectPlayPosAcceptsEntityJointTargets() {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(
                "player1 => dragon\n"
                        + "effect => effects.effekseer.A_Salamander1\n"
                        + "effect.play pos (player1.\"mixamorig:RightHand\")");

        assertTrue(String.join("\n", prg.syntaxErrors), prg.syntaxErrors.isEmpty());
        assertEquals(3, prg.actions.size());
        assertTrue(prg.actions.get(2) instanceof EffekseerPlayCommand);

        EffekseerPlayCommand cmd = (EffekseerPlayCommand) prg.actions.get(2);
        assertNotNull(cmd.entityPos);
        assertEquals("player1", cmd.entityPos.entityName);
        assertEquals("mixamorig:RightHand", cmd.entityPos.entityJointName);
    }

    @Test
    public void effectPlayPosAcceptsEquippedWeaponTargets() {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(
                "player1 => dragon\n"
                        + "effect => effects.effekseer.A_Salamander1\n"
                        + "effect.play pos (player1.weapon)");

        assertTrue(String.join("\n", prg.syntaxErrors), prg.syntaxErrors.isEmpty());
        EffekseerPlayCommand cmd = (EffekseerPlayCommand) prg.actions.get(2);
        assertNotNull(cmd.entityPos);
        assertEquals("player1", cmd.entityPos.entityName);
        assertTrue(cmd.entityPos.equippedWeapon);
    }

    @Test
    public void effectPlayPosAcceptsEquippedWeaponColliderTargets() {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(
                "player1 => dragon\n"
                        + "effect => effects.effekseer.A_Salamander1\n"
                        + "effect.play pos (player1.weapon.colliders[\"weapon_sphere_collider_1\"])");

        assertTrue(String.join("\n", prg.syntaxErrors), prg.syntaxErrors.isEmpty());
        EffekseerPlayCommand cmd = (EffekseerPlayCommand) prg.actions.get(2);
        assertNotNull(cmd.entityPos);
        assertEquals("player1", cmd.entityPos.entityName);
        assertTrue(cmd.entityPos.equippedWeapon);
        assertTrue(cmd.entityPos.equippedWeaponCollider);
        assertEquals("weapon_sphere_collider_1", cmd.entityPos.weaponColliderName);
    }

    @Test
    public void effectPlayAcceptsLoopOption() {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(
                "player1 => dragon\n"
                        + "effect => effects.effekseer.A_Salamander1\n"
                        + "effect.play pos (player1), loop");

        assertTrue(String.join("\n", prg.syntaxErrors), prg.syntaxErrors.isEmpty());
        EffekseerPlayCommand cmd = (EffekseerPlayCommand) prg.actions.get(2);
        assertTrue(cmd.loop);
    }

    @Test
    public void effectPlayAcceptsRuntimeLoopAttribute() {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(
                "player1 => dragon\n"
                        + "effect => effects.effekseer.A_Salamander1\n"
                        + "effect.play pos (player1), attr = [\"loop\" true]");

        assertTrue(String.join("\n", prg.syntaxErrors), prg.syntaxErrors.isEmpty());
        EffekseerPlayCommand cmd = (EffekseerPlayCommand) prg.actions.get(2);
        assertNotNull(cmd.attrExprs.get("loop"));
    }

    @Test
    public void effectScaleSyntaxParses() {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(
                "effect => effects.effekseer.A_Salamander1 : pos (0,0,0), scale 8\n"
                        + "effect.scale = 10\n"
                        + "effect.play pos (0,0,0), loop");

        assertTrue(String.join("\n", prg.syntaxErrors), prg.syntaxErrors.isEmpty());
    }
}
