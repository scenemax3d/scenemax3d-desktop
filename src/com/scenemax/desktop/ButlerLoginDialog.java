package com.scenemax.desktop;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class ButlerLoginDialog extends JDialog {

    private final String butlerExecutable;
    private final JTextArea txtOutput = new JTextArea();
    private final JLabel lblStatus = new JLabel("Starting butler login...");
    private final JButton btnClose = new JButton("Cancel");
    private volatile Process loginProcess;
    private boolean loginSucceeded = false;

    public ButlerLoginDialog(Window owner, String butlerExecutable) {
        super(owner, "Butler Login", ModalityType.APPLICATION_MODAL);
        this.butlerExecutable = butlerExecutable;

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setContentPane(createContentPane());
        setPreferredSize(new Dimension(720, 430));
        pack();
        setLocationRelativeTo(owner);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                startLogin();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                handleClose();
            }
        });
    }

    private JPanel createContentPane() {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JTextArea intro = new JTextArea(
                "SceneMax is starting `butler login` for you.\n" +
                "Your browser should open to the itch.io sign-in page. After you approve access, come back here and SceneMax will continue once butler finishes."
        );
        intro.setEditable(false);
        intro.setLineWrap(true);
        intro.setWrapStyleWord(true);
        intro.setOpaque(false);
        intro.setFocusable(false);
        intro.setBorder(null);

        txtOutput.setEditable(false);
        txtOutput.setLineWrap(true);
        txtOutput.setWrapStyleWord(true);
        txtOutput.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(txtOutput);

        JPanel header = new JPanel(new BorderLayout(0, 6));
        header.add(intro, BorderLayout.NORTH);
        header.add(lblStatus, BorderLayout.SOUTH);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(btnClose);
        btnClose.addActionListener(e -> handleClose());

        root.add(header, BorderLayout.NORTH);
        root.add(scrollPane, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        return root;
    }

    private void startLogin() {
        appendLine("Running: " + butlerExecutable + " login");
        SwingWorker<Integer, String> worker = new SwingWorker<Integer, String>() {
            @Override
            protected Integer doInBackground() throws Exception {
                List<String> command = Arrays.asList(butlerExecutable, "login");
                ProcessBuilder processBuilder = new ProcessBuilder(command);
                processBuilder.directory(new File(Util.getWorkingDir()));
                processBuilder.redirectErrorStream(true);
                loginProcess = processBuilder.start();

                publish("If your browser did not open automatically, wait a few seconds and check the lines below for any URL or prompt from butler.");
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(loginProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        publish(line);
                    }
                }

                return loginProcess.waitFor();
            }

            @Override
            protected void process(List<String> chunks) {
                for (String chunk : chunks) {
                    appendLine(chunk);
                }
            }

            @Override
            protected void done() {
                loginProcess = null;
                btnClose.setText("Close");
                try {
                    int exitCode = get();
                    if (exitCode == 0) {
                        loginSucceeded = true;
                        lblStatus.setText("Butler login completed successfully.");
                        appendLine("Login completed. SceneMax can now use your local butler session.");
                    } else {
                        lblStatus.setText("Butler login did not complete.");
                        appendLine("Butler exited with code " + exitCode + ".");
                    }
                } catch (Exception ex) {
                    lblStatus.setText("Butler login failed to start or was interrupted.");
                    appendLine(ex.getMessage() == null ? ex.toString() : ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void handleClose() {
        if (loginProcess != null && loginProcess.isAlive()) {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "Butler login is still running.\nDo you want to stop it and close this window?",
                    "Cancel Butler Login",
                    JOptionPane.YES_NO_OPTION
            );
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
            loginProcess.destroy();
        }
        dispose();
    }

    private void appendLine(String line) {
        txtOutput.append(line + System.lineSeparator());
        txtOutput.setCaretPosition(txtOutput.getDocument().getLength());
    }

    public boolean isLoginSucceeded() {
        return loginSucceeded;
    }
}
