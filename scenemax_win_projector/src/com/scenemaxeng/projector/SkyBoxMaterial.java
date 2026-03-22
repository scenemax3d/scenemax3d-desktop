package com.scenemaxeng.projector;

public class SkyBoxMaterial {

    String ddsRes;

    String downRes;
    String upRes;
    String northRes;
    String southRes;
    String eastRes;
    String westRes;

    public SkyBoxMaterial(String ddsRes) {
        this.ddsRes=ddsRes;
    }

    public SkyBoxMaterial(String downRes, String upRes, String northRes, String southRes, String eastRes, String westRes) {
        this.downRes=downRes;
        this.upRes=upRes;
        this.northRes=northRes;
        this.southRes=southRes;
        this.eastRes=eastRes;
        this.westRes=westRes;
    }


}
