package com.scenemax.desktop.ai;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class ToolPaths {

    private ToolPaths() {
    }

    public static Path resolvePath(SceneMaxToolContext context, String rawPath, String base) throws IOException {
        if (rawPath == null || rawPath.trim().isEmpty()) {
            throw new IOException("Path is required");
        }

        Path candidate = Paths.get(rawPath.trim());
        Path resolved;
        if (candidate.isAbsolute()) {
            resolved = candidate.normalize().toAbsolutePath();
        } else {
            Path basePath = resolveBase(context, base);
            resolved = basePath.resolve(candidate).normalize().toAbsolutePath();
        }

        ensureAllowed(context, resolved);
        return resolved;
    }

    public static Path resolveBase(SceneMaxToolContext context, String base) throws IOException {
        if (base == null || base.isBlank() || "workspace".equalsIgnoreCase(base)) {
            return context.getWorkspaceRoot();
        }
        if ("scripts".equalsIgnoreCase(base)) {
            Path scriptsRoot = context.getScriptsRoot();
            if (scriptsRoot == null) {
                throw new IOException("No active scripts folder is available");
            }
            return scriptsRoot;
        }
        if ("resources".equalsIgnoreCase(base)) {
            Path resourcesRoot = context.getResourcesRoot();
            if (resourcesRoot == null) {
                throw new IOException("No active resources folder is available");
            }
            return resourcesRoot;
        }
        if ("project".equalsIgnoreCase(base)) {
            Path projectRoot = context.getActiveProjectRoot();
            if (projectRoot == null) {
                throw new IOException("No active project is available");
            }
            return projectRoot;
        }
        throw new IOException("Unsupported base path: " + base);
    }

    public static void ensureAllowed(SceneMaxToolContext context, Path path) throws IOException {
        for (Path root : context.getAllowedRoots()) {
            if (root != null && path.startsWith(root)) {
                return;
            }
        }
        throw new IOException("Path is outside the allowed workspace/project roots: " + path);
    }
}
