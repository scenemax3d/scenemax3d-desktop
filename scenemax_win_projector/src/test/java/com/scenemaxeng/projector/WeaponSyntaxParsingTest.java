package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.GraphicEntityCreationCommand;
import com.scenemaxeng.compiler.CollisionStatementCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import com.scenemaxeng.compiler.VariableDef;
import com.scenemaxeng.compiler.WeaponCommand;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Field;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WeaponSyntaxParsingTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void parsesApprovedWeaponSyntax() {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse(
                "player=>dragon\n"
                        + "player.weapon = \"Iron Sword\"\n"
                        + "player.weapon.posture = \"fight\"\n"
                        + "player.weapon = empty");

        assertTrue(String.join("\n", prg.syntaxErrors), prg.syntaxErrors.isEmpty());
        assertEquals(4, prg.actions.size());

        WeaponCommand equip = (WeaponCommand) prg.actions.get(1);
        assertEquals(WeaponCommand.ACTION_EQUIP, equip.action);
        assertEquals("player", equip.ownerVarName);

        WeaponCommand posture = (WeaponCommand) prg.actions.get(2);
        assertEquals(WeaponCommand.ACTION_SET_POSTURE, posture.action);
        assertEquals("player", posture.ownerVarName);
        assertNotNull(posture.postureNameExpr);

        WeaponCommand unequip = (WeaponCommand) prg.actions.get(3);
        assertEquals(WeaponCommand.ACTION_UNEQUIP, unequip.action);
    }

    @Test
    public void keepsModelInstantiationSyntaxWorking() {
        ProgramDef prg = new SceneMaxLanguageParser(null, "").parse("m2 => adi2 : pos (4,-2,0)");

        assertTrue(String.join("\n", prg.syntaxErrors), prg.syntaxErrors.isEmpty());
        assertEquals(1, prg.actions.size());
        assertTrue(prg.actions.get(0) instanceof GraphicEntityCreationCommand);
        VariableDef model = prg.getVar("m2");
        assertEquals("adi2", model.resName);
        assertEquals(VariableDef.VAR_TYPE_3D, model.varType);
    }

    @Test
    public void keepsPartialModelInstantiationSyntaxWorking() {
        ProgramDef liveProgram = new ProgramDef();
        liveProgram.addCameraVariableDef();

        ProgramDef prg = new SceneMaxLanguageParser(liveProgram, "").parse("m2 => adi2 : pos (4,-2,0)");

        assertTrue(String.join("\n", prg.syntaxErrors), prg.syntaxErrors.isEmpty());
        assertEquals(1, prg.actions.size());
        assertTrue(prg.actions.get(0) instanceof GraphicEntityCreationCommand);
        VariableDef model = prg.getVar("m2");
        assertEquals("adi2", model.resName);
        assertEquals(VariableDef.VAR_TYPE_3D, model.varType);
    }

    @Test
    public void partialModelInstantiationRunsAfterControllerListWasCleared() throws Exception {
        TrackingSceneMaxApp app = new TrackingSceneMaxApp();
        app.initForTest();
        controllers(app).clear();

        app.runPartialCode("m2 => adi2 : pos (4,-2,0)", null, false);

        ArrayList<SceneMaxBaseController> activeControllers = controllers(app);
        assertTrue(activeControllers.contains(app.mainController()));
        assertNotNull(app.mainController().getActiveController());

        app.mainController().run(0f);

        assertEquals(1, app.loadModelCount);
        assertEquals("m2", app.loadedName);
        assertEquals("adi2", app.loadedResource);
    }

    @Test
    public void weaponEquipResolvesOwnerToRuntimeModelKey() {
        TrackingSceneMaxApp app = new TrackingSceneMaxApp();
        app.initForTest();

        app.runPartialCode("m2 => adi2 : pos (4,-2,0)\n"
                + "m2.weapon = \"weapon_player_weapon\"", null, false);

        CompositeController mainController = app.mainController();
        for (int i = 0; i < 5 && app.equippedOwner == null; i++) {
            mainController.run(0f);
        }

        assertEquals("m2@" + app.getMainScope().scopeId, app.equippedOwner);
        assertEquals("weapon_player_weapon", app.equippedWeapon);
    }

    @Test
    public void weaponCollidersCanBeDeferredUntilWeaponEquip() throws Exception {
        File projectRoot = temporaryFolder.newFolder("project");
        File scripts = new File(projectRoot, "scripts");
        File resources = new File(projectRoot, "resources");
        assertTrue(scripts.mkdirs());
        assertTrue(resources.mkdirs());

        ProgramDef prg = new SceneMaxLanguageParser(null, new File(scripts, "level.sm").getAbsolutePath()).parse(
                "player=>dragon\n"
                        + "when player1_weapon_axe_upper_collider collides with player do\n"
                        + "end do");

        assertTrue(String.join("\n", prg.syntaxErrors), prg.syntaxErrors.isEmpty());
        assertEquals(null, prg.getVar("player1_weapon_axe_upper_collider"));
        CollisionStatementCommand collision = (CollisionStatementCommand) prg.actions.get(1);
        assertEquals("player1_weapon_axe_upper_collider", collision.sourceEntities.get(0).varName);
        assertEquals(VariableDef.VAR_TYPE_OBJECT, collision.sourceEntities.get(0).varType);
    }

    @Test
    public void runningFolderParseDoesNotLoadWeaponFilesForCollisionVariables() throws Exception {
        File repoRoot = temporaryFolder.newFolder("repo");
        assertTrue(new File(repoRoot, "resources").mkdirs());
        File repoScripts = new File(repoRoot, "scripts");
        assertTrue(repoScripts.mkdirs());
        Files.write(new File(repoScripts, "outside.smweapon").toPath(), "{ invalid weapon json".getBytes(StandardCharsets.UTF_8));

        File running = new File(repoRoot, "running");
        File sideTests = new File(running, "side_tests");
        File level = new File(running, "game_level_train");
        assertTrue(sideTests.mkdirs());
        assertTrue(level.mkdirs());
        Files.write(new File(level, "player_weapon.smweapon").toPath(), "{ invalid weapon json".getBytes(StandardCharsets.UTF_8));

        ProgramDef prg = new SceneMaxLanguageParser(null, new File(sideTests, "tests").getAbsolutePath()).parse(
                "player=>dragon\n"
                        + "when staged_collider collides with player do\n"
                        + "end do");

        assertTrue(String.join("\n", prg.syntaxErrors), prg.syntaxErrors.isEmpty());
        assertEquals(null, prg.getVar("staged_collider"));
        assertEquals(null, prg.getVar("outside_collider"));
    }

    @SuppressWarnings("unchecked")
    private ArrayList<SceneMaxBaseController> controllers(SceneMaxApp app) throws Exception {
        Field field = SceneMaxApp.class.getDeclaredField("_controllers");
        field.setAccessible(true);
        return (ArrayList<SceneMaxBaseController>) field.get(app);
    }

    private static class TrackingSceneMaxApp extends SceneMaxApp {
        int loadModelCount;
        String loadedName;
        String loadedResource;
        String equippedOwner;
        String equippedWeapon;
        String postureOwner;
        String postureName;

        void initForTest() {
            initDesignerRuntime("");
        }

        CompositeController mainController() {
            return getMainScope().mainController;
        }

        @Override
        public int loadModel(String name, String resourcePath, ModelInst modelInst) {
            loadModelCount++;
            loadedName = name;
            loadedResource = resourcePath;
            return 0;
        }

        @Override
        public EquippedWeaponRuntime equipWeapon(String ownerVarName, String weaponNameOrId) {
            equippedOwner = ownerVarName;
            equippedWeapon = weaponNameOrId;
            return null;
        }

        @Override
        public boolean setWeaponPosture(String ownerVarName, String postureIdOrName) {
            postureOwner = ownerVarName;
            postureName = postureIdOrName;
            return true;
        }
    }
}
