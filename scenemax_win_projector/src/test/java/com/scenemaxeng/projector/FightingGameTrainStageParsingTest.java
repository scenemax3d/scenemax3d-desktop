package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class FightingGameTrainStageParsingTest {

    @Test
    public void trainStageUtilityParsesWithGeneratedTrainParts() throws Exception {
        Path main = Path.of("..", "projects", "fighting_game_project", "scripts", "Fighting Game", "game_level_train", "main")
                .normalize();
        ProgramDef prg = new SceneMaxLanguageParser(null, main.toAbsolutePath().toString()).parse(Files.readString(main));

        assertTrue(String.join(System.lineSeparator(), prg.syntaxErrors), prg.syntaxErrors.isEmpty());
    }
}
