package com.scenemaxeng.common.types;

import org.json.JSONObject;

public class SkyBoxResource {

    public final String name;
    public final String up;
    public final String down;
    public final String left;
    public final String right;
    public final String front;
    public final String back;

    public String buff;

    public SkyBoxResource(String name, String up, String down, String left, String right, String front, String back) {

        this.name = name;
        this.up=up;
        this.down=down;
        this.left=left;
        this.right=right;
        this.front=front;
        this.back=back;
    }

}
