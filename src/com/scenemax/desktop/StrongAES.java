package com.scenemax.desktop;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class StrongAES {

    private String key = AppConfig.get("aes_key"); // 128 bit key, loaded from config.properties

    public String decrypt(byte[] text) {

        try {
        Cipher cipher = Cipher.getInstance("AES");
        Key aesKey = new SecretKeySpec(key.getBytes(), "AES");

        // decrypt the text
        cipher.init(Cipher.DECRYPT_MODE, aesKey);
        String decrypted = new String(cipher.doFinal(text));
        return decrypted;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public void encrypt() {
        try {
            File f = new File("lic_terms_decrypted");
            if(!f.exists()) {
                // in production, do nothing
                return;
            }

            String text = Util.readFile(f);

            // Create key and cipher
            Key aesKey = new SecretKeySpec(key.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES");

            // encrypt the text
            cipher.init(Cipher.ENCRYPT_MODE, aesKey);
            byte[] encrypted = cipher.doFinal(text.getBytes());

            FileUtils.writeByteArrayToFile(new File("lic_terms_encrypted"), encrypted);

        } catch (Exception e) {
            e.printStackTrace();
        }


    }

}
