package com.scenemaxeng.common.types;

public class ResourceSetup2D {

    public String name;
    public int rows=0;
    public int cols=0;
    public String path = "";
    public float scaleX=0.03f;
    public float scaleY=0.03f;

    public float localTranslationX=0;
    public float localTranslationY=0;
    public float localTranslationZ=0;



    public ResourceSetup2D(String name, String path, int rows, int cols) {
        this.name=name;
        this.path=path;
        this.rows=rows;
        this.cols=cols;
    }

    public ResourceSetup2D(String path,
                           int rows,
                           int cols,
                           float scaleX,
                           float scaleY,
                           float localTranslationX,
                           float localTranslationY,
                           float localTranslationZ) {

        this.path=path;
        this.rows=rows;
        this.cols=cols;
        this.scaleX=scaleX;
        this.scaleY=scaleY;

        this.localTranslationX=localTranslationX;
        this.localTranslationY=localTranslationY;
        this.localTranslationZ=localTranslationZ;

    }
}
