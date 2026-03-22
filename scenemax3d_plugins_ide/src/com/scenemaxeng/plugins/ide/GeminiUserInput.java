package com.scenemaxeng.plugins.ide;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.scenemaxeng.common.types.ISceneMaxPlugin;
import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.awt.event.*;
import java.net.URL;
import java.util.Locale;
import java.util.Objects;

public class GeminiUserInput extends JDialog implements ISceneMaxPlugin {
    private final ISceneMaxPlugin host;
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    protected JTextArea txtUserText;
    private Timer executionTimer;
    private Timer paritalTextExecutionTimer;

    public GeminiUserInput(ISceneMaxPlugin host) {
        setContentPane(contentPane);
        setAlwaysOnTop(true);
        getRootPane().setDefaultButton(buttonOK);
        this.host = host;
        setMicButtonIcon();
        setWindowIcon();

        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });

        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    private void setWindowIcon() {

        URL iconURL = getClass().getResource("/google-gemini-icon-96x96.png");
        ImageIcon icon = new ImageIcon(iconURL);
        this.setIconImage(icon.getImage());
        this.setUndecorated(true);
    }

    private void setMicButtonIcon() {
        ImageIcon icon = new ImageIcon(Objects.requireNonNull(GeminiUserInput.class.getResource("/microphone-icon.png")));
        Image originalImage = icon.getImage();
        int newWidth = 48; // desired width
        int newHeight = 48; // desired height
        Image resizedImage = originalImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);

        ImageIcon resizedIcon = new ImageIcon(resizedImage);
        buttonOK.setIcon(resizedIcon);
        alignButtonsSize();
    }

    @Override
    public int start(Object... args) {
        return 0;
    }

    @Override
    public int stop(Object... args) {
        txtUserText.setText("Session stopped :)\nHit the 'Start Talking' button to continue...");
        return 0;
    }

    @Override
    public int run(Object... args) {
        return 0;
    }

    @Override
    public int progress(Object... args) {
        String text = (String) args[0];

        txtUserText.setText(text);
        if (args.length > 1) {
            String type = (String) args[1];
            if (type.equals("final")) {

                String lowerText = text.toLowerCase();
                PartialSentenceReaction react = PartialSentenceReactionManager.react(lowerText);
                if (react != null) {
                    runPartialExecutionTimer(react);
                }

                this.runExecutionTimer(text);
            } else if (type.equals("partial")) {

            }
        }
        return 0;
    }

    private void runPartialExecutionTimer(PartialSentenceReaction reaction) {

        if (paritalTextExecutionTimer == null) {
            paritalTextExecutionTimer = new Timer(500, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    paritalTextExecutionTimer = null;
                    if (reaction.code != null) {
                        GeminiUserInput.this.host.run("run_partial_text_code", reaction.code);
                    }
                    if (reaction.speak != null) {
                        TextToSpeech.speak(reaction.speak);
                    }
                }
            });
            paritalTextExecutionTimer.setRepeats(false);
            paritalTextExecutionTimer.start();
        } else {
            if (paritalTextExecutionTimer.isRunning()) {
                paritalTextExecutionTimer.restart();
            }
        }

    }

    private void runExecutionTimer(String finalInput) {

        if (executionTimer == null) {
            executionTimer = new Timer(500, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    executionTimer = null;
                    if (finalInput.length() > 0) {
                        GeminiUserInput.this.host.run("on_user_input", finalInput);
                        SpeechToText.clearUserInputText();
                    }
                }
            });
            executionTimer.setRepeats(false);
            executionTimer.start();
        } else {
            if (executionTimer.isRunning()) {
                executionTimer.restart();
            }
        }

    }

    @Override
    public int registerObserver(ISceneMaxPlugin observer) {
        return 0;
    }

    private void onOK() {

        // temp code for testing only
        String userInput = txtUserText.getText();
        if (userInput.startsWith(":")) {
            userInput = userInput.substring(1);
            runExecutionTimer(userInput);
            return;
        }

        if (SpeechToText.isInProgress()) {
            buttonOK.setText("Start Talking");
            System.out.println("Recording stopped.");
            SpeechToText.endTranscribeRealTime();
            return;
        }

        // start recording
        SpeechToText.startTranscribeRealTime(this);
        buttonOK.setText("Stop the session...");
    }

    private void onCancel() {
        // add your code here if necessary
        if (SpeechToText.isInProgress()) {
            SpeechToText.endTranscribeRealTime();
        }
        dispose();
    }

    public void terminate() {
        this.onCancel();
    }

    private void alignButtonsSize() {

        Dimension buttonOkSize = buttonOK.getPreferredSize();
        Dimension buttonCancelSize = new Dimension(buttonCancel.getPreferredSize().width, buttonOkSize.height);
        buttonCancel.setPreferredSize(buttonCancelSize);
        buttonCancel.revalidate(); // Revalidate to apply the new size
        this.repaint();
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        contentPane = new JPanel();
        contentPane.setLayout(new GridLayoutManager(2, 1, new Insets(10, 10, 10, 10), -1, -1));
        contentPane.setBackground(new Color(-16777216));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel1.setBackground(new Color(-16777216));
        contentPane.add(panel1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1, true, false));
        panel2.setBackground(new Color(-16777216));
        panel1.add(panel2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        buttonOK = new JButton();
        buttonOK.setBackground(new Color(-16776961));
        buttonOK.setForeground(new Color(-1));
        buttonOK.setText("Start Talking");
        panel2.add(buttonOK, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonCancel = new JButton();
        buttonCancel.setBackground(new Color(-917504));
        buttonCancel.setText("Close");
        panel2.add(buttonCancel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, 1, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        txtUserText = new JTextArea();
        txtUserText.setBackground(new Color(-16777216));
        Font txtUserTextFont = this.$$$getFont$$$("Courier New", -1, 16, txtUserText.getFont());
        if (txtUserTextFont != null) txtUserText.setFont(txtUserTextFont);
        txtUserText.setForeground(new Color(-16711936));
        txtUserText.setLineWrap(true);
        txtUserText.setWrapStyleWord(true);
        panel3.add(txtUserText, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(350, 100), null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    private Font $$$getFont$$$(String fontName, int style, int size, Font currentFont) {
        if (currentFont == null) return null;
        String resultName;
        if (fontName == null) {
            resultName = currentFont.getName();
        } else {
            Font testFont = new Font(fontName, Font.PLAIN, 10);
            if (testFont.canDisplay('a') && testFont.canDisplay('1')) {
                resultName = fontName;
            } else {
                resultName = currentFont.getName();
            }
        }
        Font font = new Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());
        boolean isMac = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).startsWith("mac");
        Font fontWithFallback = isMac ? new Font(font.getFamily(), font.getStyle(), font.getSize()) : new StyleContext().getFont(font.getFamily(), font.getStyle(), font.getSize());
        return fontWithFallback instanceof FontUIResource ? fontWithFallback : new FontUIResource(fontWithFallback);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }


}
