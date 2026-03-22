package com.scenemaxeng.common.types;

import com.jme3.audio.AudioData;

public class ResourceAudio {

    public String name;
    public String path;
    public AudioData.DataType dataType;

    public ResourceAudio(String name, String path,AudioData.DataType dataType) {
        this.name=name;
        this.path=path;
        this.dataType=dataType;
    }
}
