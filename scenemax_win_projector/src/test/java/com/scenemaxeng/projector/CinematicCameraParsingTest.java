package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.CinematicCameraPlayCommand;
import com.scenemaxeng.compiler.CinematicCameraVariableDef;
import com.scenemaxeng.compiler.GraphicEntityCreationCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CinematicCameraParsingTest {

    @Test
    public void parsesCinematicCameraDeclarationAndPlayCommand() throws Exception {
        File tempDir = Files.createTempDirectory("cinematic-parse-test").toFile();
        tempDir.deleteOnExit();
        File designerFile = new File(tempDir, "stadium.smdesign");
        designerFile.deleteOnExit();
        String smdesign = "{ \"entities\": [ { \"id\": \"internal-rig-id\", \"name\": \"Cinematic Rig\", \"type\": \"CINEMATIC_RIG\", "
                + "\"position\": [0,0,0], \"rotation\": [0,0,0,1], \"scale\": [1,1,1], "
                + "\"cinematicRuntimeId\": \"cine_rig_1\", \"children\": [], \"cinematicSegments\": [] } ] }";
        Files.writeString(designerFile.toPath(), smdesign, StandardCharsets.UTF_8);

        String code = "my_camera=>cinematic.camera.cine_rig_1\n"
                + "my_camera.play : target player1, duration 30";

        ProgramDef prg = new SceneMaxLanguageParser(null, tempDir.getAbsolutePath()).parse(code);

        assertTrue(prg.syntaxErrors.isEmpty());
        assertNotNull(prg.getVar("my_camera"));
        assertTrue(prg.getVar("my_camera") instanceof CinematicCameraVariableDef);
        CinematicCameraVariableDef varDef = (CinematicCameraVariableDef) prg.getVar("my_camera");
        assertEquals("cine_rig_1", varDef.cinematicCameraId);
        assertEquals(designerFile.getAbsolutePath(), varDef.cinematicSourceFile);
        assertTrue(prg.actions.get(0) instanceof GraphicEntityCreationCommand);
        assertTrue(prg.actions.get(1) instanceof CinematicCameraPlayCommand);

        CinematicCameraPlayCommand play = (CinematicCameraPlayCommand) prg.actions.get(1);
        assertEquals("my_camera", play.targetVar);
        assertEquals("player1", play.lookAtTargetVar);
        assertNotNull(play.speedExpr);
    }

    @Test
    public void resolvesCinematicRigFromProjectAncestorFolder() throws Exception {
        File projectDir = Files.createTempDirectory("cinematic-project-test").toFile();
        projectDir.deleteOnExit();
        File scriptDir = new File(projectDir, "scripts\\ui");
        assertTrue(scriptDir.mkdirs() || scriptDir.exists());
        File rigDir = new File(projectDir, "scripts\\game_init");
        assertTrue(rigDir.mkdirs() || rigDir.exists());

        File designerFile = new File(rigDir, "game_init.smdesign");
        designerFile.deleteOnExit();
        String smdesign = "{ \"entities\": [ { \"id\": \"internal-rig-id\", \"name\": \"Cinematic Rig\", \"type\": \"CINEMATIC_RIG\", "
                + "\"position\": [0,0,0], \"rotation\": [0,0,0,1], \"scale\": [1,1,1], "
                + "\"cinematicRuntimeId\": \"cinematic1\", \"children\": [], \"cinematicSegments\": [] } ] }";
        Files.writeString(designerFile.toPath(), smdesign, StandardCharsets.UTF_8);

        File codeFile = new File(scriptDir, "test_ui_ui.code");
        codeFile.deleteOnExit();
        String code = "cincam1=>cinematic.camera.cinematic1\n"
                + "cincam1.play : target player1, duration 30";
        Files.writeString(codeFile.toPath(), code, StandardCharsets.UTF_8);

        ProgramDef prg = new SceneMaxLanguageParser(null, codeFile.getAbsolutePath()).parse(code);

        assertTrue(prg.syntaxErrors.isEmpty());
        assertNotNull(prg.getVar("cincam1"));
        CinematicCameraVariableDef varDef = (CinematicCameraVariableDef) prg.getVar("cincam1");
        assertEquals("cinematic1", varDef.cinematicCameraId);
        assertEquals(designerFile.getAbsolutePath(), varDef.cinematicSourceFile);
    }

    @Test
    public void parsesCinematicPlayPositionStatements() throws Exception {
        File tempDir = Files.createTempDirectory("cinematic-pos-statement-test").toFile();
        tempDir.deleteOnExit();
        File designerFile = new File(tempDir, "stadium.smdesign");
        designerFile.deleteOnExit();
        String smdesign = "{ \"entities\": [ { \"id\": \"internal-rig-id\", \"name\": \"Cinematic Rig\", \"type\": \"CINEMATIC_RIG\", "
                + "\"position\": [0,0,0], \"rotation\": [0,0,0,1], \"scale\": [1,1,1], "
                + "\"cinematicRuntimeId\": \"cine_rig_1\", \"children\": [], \"cinematicSegments\": [] } ] }";
        Files.writeString(designerFile.toPath(), smdesign, StandardCharsets.UTF_8);

        String code = "my_camera=>cinematic.camera.cine_rig_1\n"
                + "my_camera.play : target (player1 forward 1 up 2), duration 5";

        ProgramDef prg = new SceneMaxLanguageParser(null, tempDir.getAbsolutePath()).parse(code);

        assertTrue(prg.syntaxErrors.isEmpty());
        assertTrue(prg.actions.get(1) instanceof CinematicCameraPlayCommand);

        CinematicCameraPlayCommand play = (CinematicCameraPlayCommand) prg.actions.get(1);
        assertNotNull(play.lookAtPosStatement);
        assertEquals("player1", play.lookAtPosStatement.startEntity);
        assertNull(play.lookAtTargetVar);
    }
}
