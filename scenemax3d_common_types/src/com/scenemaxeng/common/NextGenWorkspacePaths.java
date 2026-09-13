package com.scenemaxeng.common;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/** Temporary Swing migration adapter for persisted Rust workspace paths. */
public final class NextGenWorkspacePaths {
    private NextGenWorkspacePaths() { }

    /** Preserve valid custom paths; relocate only missing paths under the old workspace. */
    public static File relocated(File candidate) {
        if (candidate == null || candidate.exists()) {
            return candidate;
        }
        Path original = candidate.toPath().toAbsolutePath().normalize();
        for (Path current = original; current != null; current = current.getParent()) {
            if (current.getFileName() != null
                    && "scenemax_projector_nextgen".equals(current.getFileName().toString())
                    && current.getParent() != null) {
                Path workspace = current.getParent().resolve("scenemax3d_nextgen");
                Path relocated = workspace.resolve(current.relativize(original));
                if (Files.isRegularFile(workspace.resolve("Projector/app/Cargo.toml"))
                        && Files.exists(relocated)) {
                    return relocated.toFile();
                }
            }
        }
        return candidate;
    }
}
