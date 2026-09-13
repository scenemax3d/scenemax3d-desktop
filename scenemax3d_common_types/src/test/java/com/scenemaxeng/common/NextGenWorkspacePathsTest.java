package com.scenemaxeng.common;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertEquals;

public class NextGenWorkspacePathsTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void relocatesMissingWorkspaceAndExecutablePaths() throws Exception {
        Path repo = temporary.getRoot().toPath();
        Path workspace = repo.resolve("scenemax3d_nextgen");
        Files.createDirectories(workspace.resolve("Projector/app"));
        Files.write(workspace.resolve("Projector/app/Cargo.toml"), new byte[0]);
        Path executable = workspace.resolve("target/debug/scenemax_projector_nextgen.exe");
        Files.createDirectories(executable.getParent());
        Files.write(executable, new byte[0]);
        assertEquals(workspace.toFile(), NextGenWorkspacePaths.relocated(repo.resolve("scenemax_projector_nextgen").toFile()));
        assertEquals(executable.toFile(), NextGenWorkspacePaths.relocated(repo.resolve("scenemax_projector_nextgen/target/debug/scenemax_projector_nextgen.exe").toFile()));
    }

    @Test public void preservesExistingCustomPaths() throws Exception {
        File custom = temporary.newFolder("custom-runtime");
        assertEquals(custom, NextGenWorkspacePaths.relocated(custom));
    }

    @Test public void doesNotRedirectUnrelatedMissingPaths() {
        File missing = new File(temporary.getRoot(), "another-workspace");
        assertEquals(missing, NextGenWorkspacePaths.relocated(missing));
    }
}
