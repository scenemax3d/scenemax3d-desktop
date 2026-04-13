package com.scenemaxeng.common.types;

public class ResourceMaterialAsset {

    public final String name;
    public final String path;
    public final boolean transparent;
    public final boolean doubleSided;

    public ResourceMaterialAsset(String name, String path, boolean transparent, boolean doubleSided) {
        this.name = name;
        this.path = path;
        this.transparent = transparent;
        this.doubleSided = doubleSided;
    }
}
