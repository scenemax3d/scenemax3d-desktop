package com.scenemax.desktop;

import javax.net.ssl.HttpsURLConnection;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import javax.xml.bind.annotation.adapters.HexBinaryAdapter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.net.HttpURLConnection;
import java.net.URL;


public class BackBlaze {

    private final IMonitor monitor;

    public BackBlaze(IMonitor monitor) {
        this.monitor=monitor;
    }


    static public String myInputStreamReader(InputStream in) throws IOException {
        InputStreamReader reader = new InputStreamReader(in);
        StringBuilder sb = new StringBuilder();
        int c = reader.read();
        while (c != -1) {
            sb.append((char)c);
            c = reader.read();
        }
        reader.close();
        return sb.toString();
    }


    public boolean download(String fileName, File targetFile) {
        try {
            JSONObject json = authorizeAccount();

            String apiUrl = json.getString("apiUrl");
            String authorizationToken = json.getString("authorizationToken");
            String downloadUrl = json.getString("downloadUrl");
            JSONObject allowed = json.getJSONObject("allowed");
            String bucketId = allowed.getString("bucketId");

            String bucketName = AppConfig.get("b2_bucket_name");
            HttpsURLConnection connection = null;
            URL url = new URL(downloadUrl + "/file/" + bucketName + "/" + fileName);

            connection = (HttpsURLConnection)url.openConnection();
            connection.setRequestProperty("Authorization", authorizationToken);
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();

            // always check HTTP response code first
            int contentLength = 0;
            if (responseCode == HttpURLConnection.HTTP_OK) {
                contentLength = connection.getContentLength();
            }

            InputStream inputStream = connection.getInputStream();
            FileOutputStream outputStream = new FileOutputStream(targetFile);

            int bytesRead = -1;
            int length = 0;
            int BUFFER_SIZE=4096;
            byte[] buffer = new byte[BUFFER_SIZE];
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                length+=bytesRead;
                int progress = (int)((float)length/(float)contentLength*100f);

                if(monitor!=null) {
                    monitor.setProgress(progress);
                }
            }

            outputStream.close();
            inputStream.close();


        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }


    public boolean upload(String path) {

        try {
            JSONObject json = authorizeAccount();

            String apiUrl = json.getString("apiUrl");
            String authorizationToken = json.getString("authorizationToken");
            String downloadUrl = json.getString("downloadUrl");
            JSONObject allowed = json.getJSONObject("allowed");
            String bucketId = allowed.getString("bucketId");

            JSONObject uploadTokens = getUploadUrl(apiUrl,authorizationToken,bucketId);
            uploadFile(uploadTokens,new File(path));

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private JSONObject authorizeAccount() {

        JSONObject json = null;
        HttpURLConnection connection = null;

        try {
            String applicationKeyId = LicenseService.getStorageAppKeyId();
            String applicationKey = LicenseService.getStorageAppKey();

            String headerForAuthorizeAccount = "Basic " + Base64.getEncoder().encodeToString((applicationKeyId + ":" + applicationKey).getBytes());

            URL url = new URL(AppConfig.get("b2_api_url"));
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", headerForAuthorizeAccount);
            InputStream in = new BufferedInputStream(connection.getInputStream());
            String jsonResponse = myInputStreamReader(in);
            System.out.println(jsonResponse);
            json = new JSONObject(jsonResponse);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            connection.disconnect();
        }

        return json;

    }

    private boolean uploadFile(JSONObject uploadTokens, File file) {

        String fileName = file.getName();

        String contentType = "application/zip";//"text/plain"; // The content type of the file
        if(file.getName().endsWith(".png")) {
            contentType="image/png";
        } else if(file.getName().endsWith(".jpg") || file.getName().endsWith(".jpeg")) {
            contentType="image/jpeg";
        } else if(file.getName().endsWith(".txt")) {
            contentType="text/plain";
        }


        HttpsURLConnection connection = null;
        String json = null;
        try {
            byte[] fileData = FileUtils.readFileToByteArray(file);
            String sha1 = calcSHA1(file).toLowerCase();
            //String sha1 = calcSHA1(fileData);

            URL url = new URL(uploadTokens.getString("uploadUrl"));
            connection = (HttpsURLConnection)url.openConnection();
            connection.setRequestMethod("POST");
            //connection.setRequestProperty("X-Bz-Server-Side-Encryption", "AES256");

            connection.setRequestProperty("Authorization", uploadTokens.getString("authorizationToken"));
            connection.setRequestProperty("Content-Type", contentType);
            connection.setRequestProperty("Content-Length", String.valueOf(fileData.length));//Content-Length
            connection.setRequestProperty("X-Bz-File-Name", fileName);
            connection.setRequestProperty("X-Bz-Content-Sha1", sha1);
            connection.setRequestProperty("X-Bz-Info-Author", "SceneMax3D");

            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(true);

            connection.setFixedLengthStreamingMode(fileData.length);

            DataOutputStream output = new DataOutputStream(connection.getOutputStream());
            //writer.write(fileData);

            InputStream is = null;
            try {
                long max = file.length();
                long cur = 0;
                is = new FileInputStream(file);
                int read = 0;
                byte buff[] = new byte[4096];
                while ((read = is.read(buff, 0, buff.length)) > 0) {
                    output.write(buff, 0, read);
                    output.flush();
                    cur += read;
                    if (monitor != null) {
                        int progress = (int)((float)cur/(float)max*100f);
                        monitor.setProgress(progress);
                        monitor.setNote("Completed "+cur+" ("+progress+"%) out of "+max+".");
                    }
                }

            } catch (Exception ex) {
                ex.printStackTrace();
                return false;
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        return false;
                    }
                }
            }


//            DataOutputStream writer = new DataOutputStream(connection.getOutputStream());
//            writer.write(fileData);


            String jsonResponse = myInputStreamReader(connection.getInputStream());
            System.out.println(jsonResponse);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            connection.disconnect();
        }

        return true;
    }

    public static JSONObject getUploadUrl(String apiUrl, String token, String bucketId ) {

        String accountAuthorizationToken = token; // Provided by b2_authorize_account
        HttpURLConnection connection = null;
        String postParams = "{\"bucketId\":\"" + bucketId + "\"}";
        byte postData[] = postParams.getBytes(StandardCharsets.UTF_8);
        try {
            URL url = new URL(apiUrl + "/b2api/v2/b2_get_upload_url");
            connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", accountAuthorizationToken);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("charset", "utf-8");
            connection.setRequestProperty("Content-Length", Integer.toString(postData.length));
            connection.setDoOutput(true);
            DataOutputStream writer = new DataOutputStream(connection.getOutputStream());
            writer.write(postData);
            String jsonResponse = myInputStreamReader(connection.getInputStream());
            JSONObject obj = new JSONObject(jsonResponse);

            return obj;

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            connection.disconnect();
        }

        return null;
    }

//    public static String calcSHA1(byte[] data) {
//        MessageDigest md = null;
//        try {
//            md = MessageDigest.getInstance("SHA-1");
//        } catch (NoSuchAlgorithmException e) {
//            e.printStackTrace();
//        }
//        return byteArray2Hex(md.digest(data));
//    }

//    private static String byteArray2Hex(final byte[] hash) {
//        Formatter formatter = new Formatter();
//        for (byte b : hash) {
//            formatter.format("%02x", b);
//        }
//        return formatter.toString();
//    }

    private static String calcSHA1(File file) {

        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            try (InputStream input = new FileInputStream(file)) {

                byte[] buffer = new byte[8192];
                int len = input.read(buffer);

                while (len != -1) {
                    sha1.update(buffer, 0, len);
                    len = input.read(buffer);
                }

                return new HexBinaryAdapter().marshal(sha1.digest());
            }
        } catch(Exception e) {
            e.printStackTrace();
        }

        return null;
    }

}
