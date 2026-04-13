package com.scenemax.desktop.ai;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.Util;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class SceneMaxToolContext {

    private final MainApp host;
    private final Path workspaceRoot;

    public SceneMaxToolContext(MainApp host) {
        this(host, Paths.get("").toAbsolutePath().normalize());
    }

    public SceneMaxToolContext(MainApp host, Path workspaceRoot) {
        this.host = host;
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
    }

    public MainApp getHost() {
        return host;
    }

    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    public Path getScriptsRoot() {
        try {
            String scripts = Util.getScriptsFolder();
            return scripts == null ? null : Paths.get(scripts).toAbsolutePath().normalize();
        } catch (Exception ignored) {
            return null;
        }
    }

    public Path getResourcesRoot() {
        try {
            String resources = Util.getResourcesFolder();
            return resources == null ? null : Paths.get(resources).toAbsolutePath().normalize();
        } catch (Exception ignored) {
            return null;
        }
    }

    public Path getActiveProjectRoot() {
        Path scriptsRoot = getScriptsRoot();
        if (scriptsRoot != null && scriptsRoot.getParent() != null) {
            return scriptsRoot.getParent().normalize();
        }
        Path resourcesRoot = getResourcesRoot();
        if (resourcesRoot != null && resourcesRoot.getParent() != null) {
            return resourcesRoot.getParent().normalize();
        }
        return null;
    }

    public List<Path> getAllowedRoots() {
        List<Path> roots = new ArrayList<>();
        roots.add(workspaceRoot);
        addIfMissing(roots, getActiveProjectRoot());
        addIfMissing(roots, getScriptsRoot());
        addIfMissing(roots, getResourcesRoot());
        return roots;
    }

    private void addIfMissing(List<Path> roots, Path root) {
        if (root == null) {
            return;
        }
        for (Path existing : roots) {
            if (existing.equals(root)) {
                return;
            }
        }
        roots.add(root);
    }
}
