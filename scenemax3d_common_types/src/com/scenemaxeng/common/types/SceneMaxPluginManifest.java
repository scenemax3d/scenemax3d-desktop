package com.scenemaxeng.common.types;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SceneMaxPluginManifest {
    private final String id;
    private final String name;
    private final String version;
    private final String description;
    private final List<String> capabilities;

    public SceneMaxPluginManifest(String id, String name, String version, String description) {
        this(id, name, version, description, new ArrayList<>());
    }

    public SceneMaxPluginManifest(String id, String name, String version, String description, List<String> capabilities) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.description = description;
        this.capabilities = capabilities == null ? new ArrayList<>() : new ArrayList<>(capabilities);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getCapabilities() {
        return Collections.unmodifiableList(capabilities);
    }
}
