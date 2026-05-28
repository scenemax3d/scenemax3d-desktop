package com.scenemax.desktop;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SceneMaxScriptFormatterTest {

    @Test
    public void formatsBracedBlocksWithTwoSpaces() {
        String source = "level1_spawn_dragon_gate_rock = {\n"
                + "if(level1_dragon_rocks_started==0) {\n"
                + "return\n"
                + "}\n"
                + "for (var index=0;index<5;index=index+1) {\n"
                + "if (level1_dragon_rock_slot[index]==0) {\n"
                + "run level1_launch_dragon_gate_rock(index) Async\n"
                + "}\n"
                + "}\n"
                + "}\n";

        String expected = "level1_spawn_dragon_gate_rock = {\n"
                + "  if(level1_dragon_rocks_started==0) {\n"
                + "    return\n"
                + "  }\n"
                + "  for (var index=0;index<5;index=index+1) {\n"
                + "    if (level1_dragon_rock_slot[index]==0) {\n"
                + "      run level1_launch_dragon_gate_rock(index) Async\n"
                + "    }\n"
                + "  }\n"
                + "}\n";

        assertEquals(expected, SceneMaxScriptFormatter.format(source));
    }

    @Test
    public void formatsDoWhileBlocksWithTwoSpaces() {
        String source = "level1_dragon_rocks_start = {\n"
                + "do async\n"
                + "wait 1.15 seconds\n"
                + "do\n"
                + "run level1_spawn_dragon_gate_rock\n"
                + "wait 1.15 seconds\n"
                + "while level1_dragon_rocks_started==1\n"
                + "end do\n"
                + "}\n";

        String expected = "level1_dragon_rocks_start = {\n"
                + "  do async\n"
                + "    wait 1.15 seconds\n"
                + "    do\n"
                + "      run level1_spawn_dragon_gate_rock\n"
                + "      wait 1.15 seconds\n"
                + "    while level1_dragon_rocks_started==1\n"
                + "  end do\n"
                + "}\n";

        assertEquals(expected, SceneMaxScriptFormatter.format(source));
    }

    @Test
    public void ignoresBracesInsideStringsAndComments() {
        String source = "msg = {\n"
                + "sys.print \"{\" // }\n"
                + "}\n";

        String expected = "msg = {\n"
                + "  sys.print \"{\" // }\n"
                + "}\n";

        assertEquals(expected, SceneMaxScriptFormatter.format(source));
    }
}
