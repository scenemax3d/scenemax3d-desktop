package com.scenemax.desktop;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class BitmapFontGeneratorTest {

    @Test
    public void generatesBitmapFontPair() throws Exception {
        System.setProperty("java.awt.headless", "true");
        BitmapFontGenerator.FontGeneratorOptions options = new BitmapFontGenerator.FontGeneratorOptions();
        options.setFontFamily("Dialog");
        options.setCharacters("ABC 123");

        Path tempDir = Files.createTempDirectory("font-generator-test");
        BitmapFontGenerator.GeneratedFont generated =
                BitmapFontGenerator.generate(options, tempDir.toFile(), "hud_font");

        assertTrue(generated.getFntFile().isFile());
        assertTrue(generated.getPngFile().isFile());

        String fntContent = Files.readString(generated.getFntFile().toPath());
        assertTrue(fntContent.contains("page id=0 file=\"hud_font.png\""));
        assertTrue(fntContent.contains("char id=65"));

        deleteDirectory(tempDir.toFile());
    }

    private void deleteDirectory(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteDirectory(child);
                }
            }
        }
        file.delete();
    }
}
