package com.scenemaxeng.projector;

import com.jme3.ui.Picture;

public class PictureExt extends Picture {
    public boolean clear = false;
    public String resPath;
    public int width;
    public int height;
    private int widthEx;
    private int heightEx;

    public PictureExt(String name) {
        super(name);
    }

    public void setWidthEx(int width) {
        this.widthEx=width;
        this.setWidth(width);
    }

    public void setHeightEx(int height) {
        this.heightEx = height;
        this.setHeight(height);
    }

    public int getHeightEx() {
        return this.heightEx;
    }

    public int getWidthEx() {
        return this.widthEx;
    }


}
