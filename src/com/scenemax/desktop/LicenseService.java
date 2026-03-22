package com.scenemax.desktop;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.URL;

public class LicenseService {

    private static int whiteLabelCode = 0;
    private static String classroomServer = "";
    private static String sftpServer ="";
    private static String sftpServerUser ="";
    private static String sftpServerPwd ="";
    private static String storageAppKeyId;
    private static String storageAppKey;
    private static String gamebox3dHost;

    public static int getWhiteLabelCode() {
        return whiteLabelCode;
    }

    public static boolean licenseExists() {
        return checkLicense()!=1;
    }

    public static long checkLicense() {

        URL licenseURL = MainApp.class.getResource("/license/lic_terms_encrypted");
        try {
            InputStream is = licenseURL.openStream();
            byte[] terms = Util.toByteArray(is);
            String termsAsText = new StrongAES().decrypt(terms);

            JSONObject conf = new JSONObject(termsAsText);
            boolean hasLicense = conf.getBoolean("license");
            classroomServer = conf.getString("default_classroom_server");
            sftpServer = conf.getString("default_sftp_server");
            sftpServerUser = conf.getString("default_sftp_server_user");
            sftpServerPwd = conf.getString("default_sftp_server_pwd");
            storageAppKeyId = conf.getString("storage_app_key_id");
            storageAppKey = conf.getString("storage_app_key");
            gamebox3dHost = conf.getString("gamebox3d_host");


            long exp = 0 ;
            if(hasLicense) {

                exp = conf.getLong("exp");

                if(conf.has("classroom_server")) {
                    classroomServer = conf.getString("classroom_server");
                }

                if(conf.has("sftp_server")) {
                    sftpServer = conf.getString("sftp_server");
                }

                if(conf.has("sftp_server_user")) {
                    sftpServerUser = conf.getString("sftp_server_user");
                }

                if(conf.has("sftp_server_pwd")) {
                    sftpServerPwd = conf.getString("sftp_server_pwd");
                }

                if(conf.has("storage_app_key_id")) {
                    storageAppKeyId = conf.getString("storage_app_key_id");
                }

                if(conf.has("storage_app_key")) {
                    storageAppKey = conf.getString("storage_app_key");
                }

                //
                return exp-System.currentTimeMillis();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return 1;
    }

    public static String getClassroomServer() {
        return classroomServer;
    }
    public static String getSftpServer() {
        return sftpServer;
    }
    public static String getSftpServerServerUser() {
        return sftpServerUser;
    }
    public static String getSftpServerServerPwd() {
        return sftpServerPwd;
    }

    public static String getGameHubServer() {
        return "http://"+gamebox3dHost+":3080";
    }

    public static String getStorageAppKey() {
        return storageAppKey;
    }

    public static String getStorageAppKeyId() {
        return storageAppKeyId;
    }

}


