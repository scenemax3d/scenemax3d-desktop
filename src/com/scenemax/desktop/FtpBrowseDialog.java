package com.scenemax.desktop;

import it.sauronsoftware.ftp4j.FTPClient;
import it.sauronsoftware.ftp4j.FTPFile;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

public class FtpBrowseDialog extends JDialog {

    private final JComboBox<String> cboProtocol = new JComboBox<>(new String[]{"FTP", "SFTP"});
    private final JTextField txtHost = new JTextField();
    private final JTextField txtPort = new JTextField();
    private final JTextField txtUser = new JTextField();
    private final JPasswordField txtPassword = new JPasswordField();
    private final JButton btnConnect = new JButton("Connect");
    private final JButton btnUp = new JButton("Up");
    private final JButton btnNewFolder = new JButton("New Folder...");
    private final JButton btnSelect = new JButton("Select Folder");
    private final JButton btnCancel = new JButton("Cancel");
    private final JLabel lblCurrentPath = new JLabel("/");
    private final JProgressBar progressBar = new JProgressBar();
    private final DefaultListModel<String> directoriesModel = new DefaultListModel<>();
    private final JList<String> lstDirectories = new JList<>(directoriesModel);
    private final JLabel lblStatus = new JLabel("Enter FTP details and connect.");

    private FTPClient client;
    private String selectedFolder;
    private String currentPath = "/";
    private boolean connected;
    private boolean busy;

    public FtpBrowseDialog(Window owner) {
        super(owner, "Browse FTP Folder", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setContentPane(createContentPane());
        setPreferredSize(new Dimension(820, 560));
        setMinimumSize(new Dimension(780, 540));
        setLocationRelativeTo(owner);

        txtHost.setColumns(18);
        txtPort.setColumns(5);
        txtUser.setColumns(12);
        txtPassword.setColumns(12);

        txtHost.setText(Util.FTP_HOST_NAME == null ? "" : Util.FTP_HOST_NAME);
        cboProtocol.setSelectedItem(Util.FILE_TRANSFER_PROTOCOL == null || Util.FILE_TRANSFER_PROTOCOL.trim().length() == 0 ? "FTP" : Util.FILE_TRANSFER_PROTOCOL.toUpperCase());
        txtPort.setText(String.valueOf(Util.FTP_PORT <= 0 ? defaultPortForProtocol(getSelectedProtocol()) : Util.FTP_PORT));
        txtUser.setText(Util.FTP_USER_NAME == null ? "" : Util.FTP_USER_NAME);
        txtPassword.setText(Util.FTP_PASSWORD == null ? "" : Util.FTP_PASSWORD);
        cboProtocol.addActionListener(e -> applyDefaultPortIfNeeded());

        btnConnect.addActionListener(e -> connectAndLoad());
        btnUp.addActionListener(e -> navigateUp());
        btnNewFolder.addActionListener(e -> createRemoteFolder());
        btnSelect.addActionListener(e -> selectCurrentFolder());
        btnCancel.addActionListener(e -> closeDialog());
        lstDirectories.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lstDirectories.addListSelectionListener(e -> updateSelectButtonState());
        lstDirectories.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedDirectory();
                }
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeDialog();
            }
        });

        updateBrowseState();
    }

    public String showDialog(String initialFolder) {
        if (initialFolder != null && initialFolder.trim().length() > 0) {
            currentPath = normalizePath(initialFolder);
            lblCurrentPath.setText(currentPath);
        }
        pack();
        setVisible(true);
        return selectedFolder;
    }

    private JPanel createContentPane() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridy = 0;
        gbc.gridx = 0;
        top.add(new JLabel("Protocol"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 0.18;
        top.add(cboProtocol, gbc);
        gbc.gridx = 2;
        gbc.weightx = 0;
        top.add(new JLabel("Host"), gbc);
        gbc.gridx = 3;
        gbc.weightx = 0.55;
        top.add(txtHost, gbc);
        gbc.gridx = 4;
        gbc.weightx = 0;
        top.add(new JLabel("Port"), gbc);
        gbc.gridx = 5;
        gbc.weightx = 0.08;
        top.add(txtPort, gbc);
        gbc.gridx = 6;
        gbc.weightx = 0;
        top.add(btnConnect, gbc);

        gbc.gridy = 1;
        gbc.gridx = 0;
        gbc.weightx = 0;
        top.add(new JLabel("User"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 3;
        gbc.weightx = 0.42;
        top.add(txtUser, gbc);
        gbc.gridx = 4;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        top.add(new JLabel("Password"), gbc);
        gbc.gridx = 5;
        gbc.gridwidth = 2;
        gbc.weightx = 0.3;
        top.add(txtPassword, gbc);

        gbc.gridy = 2;
        gbc.gridx = 0;
        gbc.gridwidth = 7;
        gbc.weightx = 1;
        lblStatus.setForeground(new Color(92, 92, 92));
        top.add(lblStatus, gbc);

        gbc.gridy = 3;
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        top.add(progressBar, gbc);

        JPanel browserPanel = new JPanel(new BorderLayout(0, 8));
        JPanel browserHeader = new JPanel(new BorderLayout(8, 0));
        JPanel navButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        navButtons.add(btnUp);
        navButtons.add(btnNewFolder);
        browserHeader.add(navButtons, BorderLayout.WEST);
        browserHeader.add(lblCurrentPath, BorderLayout.CENTER);
        browserPanel.add(browserHeader, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(lstDirectories);
        lstDirectories.setVisibleRowCount(18);
        browserPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(btnCancel);
        buttons.add(btnSelect);

        root.add(top, BorderLayout.NORTH);
        root.add(browserPanel, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        return root;
    }

    private void connectAndLoad() {
        String host = txtHost.getText() == null ? "" : txtHost.getText().trim();
        String user = txtUser.getText() == null ? "" : txtUser.getText().trim();
        String password = new String(txtPassword.getPassword());
        String protocol = getSelectedProtocol();
        int port;
        try {
            port = Integer.parseInt(txtPort.getText().trim());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Enter a valid FTP port.", "FTP Connect", JOptionPane.INFORMATION_MESSAGE);
            txtPort.requestFocusInWindow();
            return;
        }

        if (host.length() == 0 || user.length() == 0) {
            JOptionPane.showMessageDialog(this, "Enter FTP host and user.", "FTP Connect", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        disconnectQuietly();
        runBusyOperation("Connecting to " + protocol + " server...", new Runnable() {
            @Override
            public void run() {
                try {
                    if ("SFTP".equalsIgnoreCase(protocol)) {
                        SFTP.testConnection(host, port, user, password);
                    } else {
                        FTPClient newClient = new FTPClient();
                        updateBusyMessage("Logging in to FTP server...");
                        newClient.connect(host, port);
                        newClient.login(user, password);
                        newClient.setType(FTPClient.TYPE_BINARY);
                        client = newClient;
                    }
                    connected = true;

                    Util.FTP_HOST_NAME = host;
                    Util.FTP_USER_NAME = user;
                    Util.FTP_PASSWORD = password;
                    Util.FTP_PORT = port;
                    Util.FILE_TRANSFER_PROTOCOL = protocol;
                    AppDB.getInstance().setParam("file_transfer_protocol", protocol);
                    AppDB.getInstance().setParam("ftp_host_name", host);
                    AppDB.getInstance().setParam("ftp_user", user);
                    AppDB.getInstance().setParam("ftp_password", password);
                    AppDB.getInstance().setParam("ftp_port", String.valueOf(port));

                    updateBusyMessage("Loading folder list...");
                    loadDirectoryInternal(currentPath);
                    SwingUtilities.invokeLater(() -> lblStatus.setText("Connected. Double-click a folder to open it."));
                } catch (Exception e) {
                    disconnectQuietly();
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(FtpBrowseDialog.this, "FTP connection failed:\r\n" + e.getMessage(), "FTP Connect", JOptionPane.ERROR_MESSAGE);
                        lblStatus.setText("Connection failed.");
                    });
                }
            }
        });
    }

    private void loadDirectory(String path) {
        final String normalized = normalizePath(path);
        runBusyOperation("Loading " + normalized + "...", new Runnable() {
            @Override
            public void run() {
                try {
                    loadDirectoryInternal(normalized);
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(FtpBrowseDialog.this, "Could not open folder:\r\n" + e.getMessage(), "FTP Browser", JOptionPane.ERROR_MESSAGE));
                }
            }
        });
    }

    private void openSelectedDirectory() {
        String selected = lstDirectories.getSelectedValue();
        if (selected == null || selected.trim().length() == 0) {
            return;
        }
        String current = currentPath;
        if ("/".equals(current)) {
            loadDirectory("/" + selected);
        } else {
            loadDirectory(current + "/" + selected);
        }
    }

    private void navigateUp() {
        String current = normalizePath(currentPath);
        if ("/".equals(current)) {
            return;
        }
        int idx = current.lastIndexOf('/');
        if (idx <= 0) {
            loadDirectory("/");
        } else {
            loadDirectory(current.substring(0, idx));
        }
    }

    private void selectCurrentFolder() {
        if (!connected) {
            return;
        }
        selectedFolder = normalizePath(currentPath);
        closeDialog();
    }

    private void createRemoteFolder() {
        if (!connected || busy) {
            return;
        }
        String folderName = JOptionPane.showInputDialog(this, "Enter new folder name", "Create Remote Folder", JOptionPane.PLAIN_MESSAGE);
        if (folderName == null) {
            return;
        }
        String trimmed = folderName.trim();
        if (trimmed.length() == 0) {
            JOptionPane.showMessageDialog(this, "Folder name cannot be empty.", "Create Remote Folder", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (trimmed.contains("/") || trimmed.contains("\\")) {
            JOptionPane.showMessageDialog(this, "Folder name cannot contain path separators.", "Create Remote Folder", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        final String targetPath = "/".equals(currentPath) ? "/" + trimmed : currentPath + "/" + trimmed;
        runBusyOperation("Creating folder " + trimmed + "...", new Runnable() {
            @Override
            public void run() {
                try {
                    createRemoteFolderInternal(targetPath);
                    loadDirectoryInternal(currentPath);
                    SwingUtilities.invokeLater(() -> {
                        lstDirectories.setSelectedValue(trimmed, true);
                        lblStatus.setText("Created folder " + trimmed + ".");
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(FtpBrowseDialog.this, "Could not create folder:\r\n" + e.getMessage(), "Create Remote Folder", JOptionPane.ERROR_MESSAGE));
                }
            }
        });
    }

    private void updateBrowseState() {
        boolean enabled = connected && !busy;
        btnUp.setEnabled(enabled && !"/".equals(normalizePath(currentPath)));
        btnNewFolder.setEnabled(enabled);
        lstDirectories.setEnabled(enabled);
        btnConnect.setEnabled(!busy);
        cboProtocol.setEnabled(!busy);
        txtHost.setEnabled(!busy);
        txtPort.setEnabled(!busy);
        txtUser.setEnabled(!busy);
        txtPassword.setEnabled(!busy);
        btnCancel.setEnabled(!busy);
        updateSelectButtonState();
    }

    private void updateSelectButtonState() {
        btnSelect.setEnabled(connected && !busy);
    }

    private String normalizePath(String path) {
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

    private void closeDialog() {
        disconnectQuietly();
        dispose();
    }

    private void disconnectQuietly() {
        connected = false;
        if (client != null) {
            try {
                client.disconnect(true);
            } catch (Exception ignored) {
            }
            client = null;
        }
        currentPath = "/";
        lblCurrentPath.setText(currentPath);
    }

    private String getSelectedProtocol() {
        Object value = cboProtocol.getSelectedItem();
        return value == null ? "FTP" : value.toString().trim().toUpperCase();
    }

    private int defaultPortForProtocol(String protocol) {
        return "SFTP".equalsIgnoreCase(protocol) ? 22 : 21;
    }

    private void applyDefaultPortIfNeeded() {
        String current = txtPort.getText() == null ? "" : txtPort.getText().trim();
        if (current.length() == 0 || "21".equals(current) || "22".equals(current)) {
            txtPort.setText(String.valueOf(defaultPortForProtocol(getSelectedProtocol())));
        }
    }

    private void changeFtpDirectory(FTPClient ftpClient, String path) throws Exception {
        String normalized = normalizePath(path);
        ftpClient.changeDirectory("/");
        if ("/".equals(normalized)) {
            return;
        }
        String[] parts = normalized.substring(1).split("/");
        for (String part : parts) {
            String segment = part.trim();
            if (segment.length() == 0) {
                continue;
            }
            ftpClient.changeDirectory(segment);
        }
    }

    private void loadDirectoryInternal(String normalized) throws Exception {
        if (client == null) {
            if (!"SFTP".equalsIgnoreCase(getSelectedProtocol())) {
                return;
            }
        }

        List<String> directories = new ArrayList<>();
        if ("SFTP".equalsIgnoreCase(getSelectedProtocol())) {
            updateBusyMessage("Reading SFTP folder " + normalized + "...");
            directories.addAll(SFTP.listDirectories(txtHost.getText().trim(), Integer.parseInt(txtPort.getText().trim()), txtUser.getText().trim(), new String(txtPassword.getPassword()), normalized));
        } else {
            updateBusyMessage("Reading FTP folder " + normalized + "...");
            changeFtpDirectory(client, normalized);
            FTPFile[] files = client.list();
            if (files != null) {
                for (FTPFile file : files) {
                    if (file != null && file.getType() == FTPFile.TYPE_DIRECTORY) {
                        String name = file.getName();
                        if (name != null && name.trim().length() > 0 && !".".equals(name) && !"..".equals(name)) {
                            directories.add(name);
                        }
                    }
                }
            }
        }

        SwingUtilities.invokeLater(() -> {
            currentPath = normalized;
            lblCurrentPath.setText(currentPath);
            directoriesModel.clear();
            for (String directory : directories) {
                directoriesModel.addElement(directory);
            }
            lblStatus.setText(directories.isEmpty() ? "Connected. This folder has no subfolders." : "Connected. Double-click a folder to open it.");
            updateBrowseState();
        });
    }

    private void createRemoteFolderInternal(String fullPath) throws Exception {
        String normalized = normalizePath(fullPath);
        if ("SFTP".equalsIgnoreCase(getSelectedProtocol())) {
            updateBusyMessage("Creating SFTP folder " + normalized + "...");
            SFTP.createDirectory(txtHost.getText().trim(), Integer.parseInt(txtPort.getText().trim()), txtUser.getText().trim(), new String(txtPassword.getPassword()), normalized);
        } else {
            updateBusyMessage("Creating FTP folder " + normalized + "...");
            createFtpDirectory(client, normalized);
        }
    }

    private void createFtpDirectory(FTPClient ftpClient, String path) throws Exception {
        String normalized = normalizePath(path);
        ftpClient.changeDirectory("/");
        if ("/".equals(normalized)) {
            return;
        }
        String[] parts = normalized.substring(1).split("/");
        for (String part : parts) {
            String segment = part.trim();
            if (segment.length() == 0) {
                continue;
            }
            try {
                ftpClient.changeDirectory(segment);
            } catch (Exception e) {
                ftpClient.createDirectory(segment);
                ftpClient.changeDirectory(segment);
            }
        }
    }

    private void runBusyOperation(String startMessage, Runnable action) {
        if (busy) {
            return;
        }
        busy = true;
        lblStatus.setText(startMessage);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        updateBrowseState();

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                action.run();
                return null;
            }

            @Override
            protected void done() {
                busy = false;
                progressBar.setVisible(false);
                updateBrowseState();
            }
        };
        worker.execute();
    }

    private void updateBusyMessage(String message) {
        SwingUtilities.invokeLater(() -> lblStatus.setText(message));
    }
}
