package com.scenemaxeng.plugins.ide;

import com.scenemaxeng.common.types.Util;

import java.io.InputStream;
import java.net.URL;

public class Utils {

    public static String readResourceText(String res) {
//    /code_templates/import_model_test_program
        URL menuURL = Utils.class.getResource(res);
        String code = "";
        try {
            InputStream script = menuURL.openStream();
            code = new String(Util.toByteArray(script));
            return code;
        } catch(Exception ex) {
            ex.printStackTrace();
        }

        return null;
    }
}
