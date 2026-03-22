package com.scenemaxeng.common.types;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class Util {

    public static String readFile(File f) {

        String text = "";
        try {
            text = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return text;

    }

    public static byte[] toByteArray(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[0x1000];
        while (true) {
            int r = in.read(buf);
            if (r == -1) {
                break;
            }
            out.write(buf, 0, r);
        }
        return out.toByteArray();
    }
}
