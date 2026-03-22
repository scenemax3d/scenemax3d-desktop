package com.scenemax.desktop;

import com.jcraft.jsch.*;

import java.io.File;
import java.util.Vector;

public class SFTP {


    public static Boolean sftpUpload(String server, int portNumber,
                                     String user, String password,
                                     String filename, SftpProgressMonitor monitor)
    {

        try {
            JSch ssh = new JSch();
            Session session = ssh.getSession(user, server, 22);
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.setPassword(password);

            session.connect();
            Channel channel = session.openChannel("sftp");
            channel.connect();

            ChannelSftp sftp = (ChannelSftp) channel;
            sftp.cd("/root/game_hub/downloads");
            Vector files = sftp.ls("/root/game_hub/downloads");

            int mode=ChannelSftp.OVERWRITE;

            File f = new File(filename);
            sftp.put(filename, "/root/game_hub/downloads/"+f.getName(), monitor, mode);

            Boolean success = true;

            if(success){
                // The file has been succesfully downloaded
            }

            channel.disconnect();
            session.disconnect();
        } catch (JSchException e) {
            System.out.println(e.getMessage().toString());
            e.printStackTrace();
            return false;
        } catch (SftpException e) {
            System.out.println(e.getMessage().toString());
            e.printStackTrace();
            return false;
        }

        return true;

    }

    private static Boolean sftpDownload(String server, int portNumber,
                                        String user, String password, String filename, File localFile)
    {

        try {
            JSch ssh = new JSch();
            Session session = ssh.getSession(user, server, 22);
            // Remember that this is just for testing and we need a quick access, you can add an identity and known_hosts file to prevent
            // Man In the Middle attacks
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.setPassword(password);

            session.connect();
            Channel channel = session.openChannel("sftp");
            channel.connect();

            ChannelSftp sftp = (ChannelSftp) channel;

            sftp.cd("/root/downloads");
            // If you need to display the progress of the upload, read how to do it in the end of the article

            // use the get method , if you are using android remember to remove "file://" and use only the relative path
            sftp.get(filename,localFile.getAbsolutePath());

            Boolean success = true;

            if(success){
                // The file has been succesfully downloaded
            }

            channel.disconnect();
            session.disconnect();
        } catch (JSchException e) {
            System.out.println(e.getMessage().toString());
            e.printStackTrace();
        } catch (SftpException e) {
            System.out.println(e.getMessage().toString());
            e.printStackTrace();
        }

        return false;

    }

}
