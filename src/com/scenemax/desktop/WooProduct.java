package com.scenemax.desktop;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

public class WooProduct {

    public String downloadFileId="";
    public String visibility;
    public String dateCreated;
    public JSONObject json;
    public String name;
    public String desc;
    public String shortDesc;
    public String sku;
    public String downloadFile;
    public String webId;
    public String downloadFilePic;
    public String localFilePic;

    public WooProduct(JSONObject item) {

        this.json = item;
        if(item!=null) {
            this.visibility = item.getString("catalog_visibility");
            this.webId = String.valueOf(item.getInt("id"));
            this.sku = item.getString("sku");
            this.name = item.getString("name");
            this.desc = item.getString("description");
            this.shortDesc = item.getString("short_description");
            this.dateCreated = item.getString("date_created");
            this.downloadFile = getDownloadFile(item);
            this.downloadFilePic = getDownloadFilePic(item);

            File f = new File(this.downloadFile);
            this.downloadFileId = f.getName().replace(".zip","");
        }

    }

    public void genDownloadFileId() {
        File f = new File(this.downloadFile);
        this.downloadFileId = f.getName().replace(".zip","");
    }

    private String getDownloadFile(JSONObject item) {
        JSONArray downloads = item.getJSONArray("downloads");
        if(downloads.length()>0) {
            JSONObject file = downloads.getJSONObject(0);
            return file.getString("file");
        } else {
            return null;
        }
    }

    private String getDownloadFilePic(JSONObject item) {
        JSONArray images = item.getJSONArray("images");
        if(images.length()>0) {
            JSONObject file = images.getJSONObject(0);
            return file.getString("src");
        } else {
            return null;
        }
    }

}
