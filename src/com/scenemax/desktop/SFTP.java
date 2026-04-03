package com.scenemax.desktop;

import com.jcraft.jsch.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;

public class SFTP {

    public static void uploadFiles(String server, int portNumber, String user, String password,
                                   String remoteFolder, List<File> files, IMonitor monitor) throws Exception {
        Session session = null;
        ChannelSftp sftp = null;
        try {
            session = openSession(server, portNumber, user, password);
            sftp = openSftp(session);
            ensureRemoteDirectory(sftp, remoteFolder);
            String targetFolder = normalizePath(remoteFolder);
            long totalBytes = 0;
            int totalFiles = 0;
            for (File file : files) {
                if (file != null && file.exists() && file.isFile()) {
                    totalBytes += Math.max(1L, file.length());
                    totalFiles += 1;
                }
            }
            long uploadedBytes = 0;
            int fileIndex = 0;
            for (File file : files) {
                if (file == null || !file.exists() || !file.isFile()) {
                    continue;
                }
                fileIndex += 1;
                if (monitor != null) {
                    monitor.setNote("Uploading file " + fileIndex + " of " + totalFiles + ": " + file.getName());
                }
                final long baseBytes = uploadedBytes;
                final long fileSize = Math.max(1L, file.length());
                final long totalSize = Math.max(1L, totalBytes);
                sftp.put(file.getAbsolutePath(), buildRemotePath(targetFolder, file.getName()), new SftpProgressMonitor() {
                    private long transferred;

                    @Override
                    public void init(int op, String src, String dest, long max) {
                    }

                    @Override
                    public boolean count(long count) {
                        transferred += count;
                        if (monitor != null) {
                            long aggregate = Math.min(totalSize, baseBytes + transferred);
                            int progress = (int) Math.min(100L, (aggregate * 100L) / totalSize);
                            monitor.setProgress(progress);
                        }
                        return true;
                    }

                    @Override
                    public void end() {
                        if (monitor != null) {
                            long aggregate = Math.min(totalSize, baseBytes + fileSize);
                            int progress = (int) Math.min(100L, (aggregate * 100L) / totalSize);
                            monitor.setProgress(progress);
                        }
                    }
                }, ChannelSftp.OVERWRITE);
                uploadedBytes += fileSize;
            }
            if (monitor != null) {
                monitor.setProgress(100);
                monitor.setNote("Upload completed.");
                monitor.onEnd();
            }
        } finally {
            closeQuietly(sftp, session);
        }
    }

    public static List<String> listDirectories(String server, int portNumber, String user, String password,
                                               String remoteFolder) throws Exception {
        Session session = null;
        ChannelSftp sftp = null;
        try {
            session = openSession(server, portNumber, user, password);
            sftp = openSftp(session);
            String normalized = normalizePath(remoteFolder);
            sftp.cd(normalized);
            Vector entries = sftp.ls(normalized);
            List<String> result = new ArrayList<>();
            if (entries != null) {
                for (Object entry : entries) {
                    if (!(entry instanceof ChannelSftp.LsEntry)) {
                        continue;
                    }
                    ChannelSftp.LsEntry lsEntry = (ChannelSftp.LsEntry) entry;
                    String name = lsEntry.getFilename();
                    if (".".equals(name) || "..".equals(name)) {
                        continue;
                    }
                    if (lsEntry.getAttrs() != null && lsEntry.getAttrs().isDir()) {
                        result.add(name);
                    }
                }
            }
            result.sort(Comparator.comparing(String::toLowerCase));
            return result;
        } finally {
            closeQuietly(sftp, session);
        }
    }

    public static boolean testConnection(String server, int portNumber, String user, String password) throws Exception {
        Session session = null;
        ChannelSftp sftp = null;
        try {
            session = openSession(server, portNumber, user, password);
            sftp = openSftp(session);
            return true;
        } finally {
            closeQuietly(sftp, session);
        }
    }

    public static void createDirectory(String server, int portNumber, String user, String password,
                                       String remoteFolder) throws Exception {
        Session session = null;
        ChannelSftp sftp = null;
        try {
            session = openSession(server, portNumber, user, password);
            sftp = openSftp(session);
            ensureRemoteDirectory(sftp, remoteFolder);
        } finally {
            closeQuietly(sftp, session);
        }
    }

    private static Session openSession(String server, int portNumber, String user, String password) throws JSchException {
        JSch ssh = new JSch();
        Session session = ssh.getSession(user, server, portNumber <= 0 ? 22 : portNumber);
        java.util.Properties config = new java.util.Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.setPassword(password);
        session.connect();
        return session;
    }

    private static ChannelSftp openSftp(Session session) throws JSchException {
        Channel channel = session.openChannel("sftp");
        channel.connect();
        return (ChannelSftp) channel;
    }

    private static void ensureRemoteDirectory(ChannelSftp sftp, String remoteFolder) throws SftpException {
        String normalized = normalizePath(remoteFolder);
        if ("/".equals(normalized)) {
            sftp.cd("/");
            return;
        }
        sftp.cd("/");
        String[] parts = normalized.substring(1).split("/");
        for (String part : parts) {
            if (part.trim().length() == 0) {
                continue;
            }
            try {
                sftp.cd(part);
            } catch (SftpException changeFailed) {
                sftp.mkdir(part);
                sftp.cd(part);
            }
        }
    }

    private static String buildRemotePath(String folder, String fileName) {
        String normalized = normalizePath(folder);
        if ("/".equals(normalized)) {
            return "/" + fileName;
        }
        return normalized + "/" + fileName;
    }

    private static String normalizePath(String path) {
        String normalized = path == null ? "/" : path.trim().replace("\\", "/");
        if (normalized.length() == 0) {
            return "/";
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static void closeQuietly(ChannelSftp sftp, Session session) {
        if (sftp != null) {
            try {
                sftp.disconnect();
            } catch (Exception ignored) {
            }
        }
        if (session != null) {
            try {
                session.disconnect();
            } catch (Exception ignored) {
            }
        }
    }
}
