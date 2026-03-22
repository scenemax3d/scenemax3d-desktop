package com.scenemaxeng.projector;

class ResourceMaterial {

    public String diffuseMap;
    public String normalMap;

    public ResourceMaterial(String diffuseMapResPath, String normalMapResPath ) {
        this.diffuseMap=diffuseMapResPath;
        this.normalMap=normalMapResPath;
    }
}
