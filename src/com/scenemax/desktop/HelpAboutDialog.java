package com.scenemax.desktop;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Locale;

public class HelpAboutDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextArea txt3rdPartySoftware;
    private JLabel wrritenBy;
    private JLabel webSite;
    private JLabel copyright;
    private JLabel title;
    private JPanel logoPanel;
    private JPanel topPanel;

    public HelpAboutDialog(boolean licenseExists) {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

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

        installCreditsArea();
        txt3rdPartySoftware.setText(buildThirdPartyCredits());
        txt3rdPartySoftware.setCaretPosition(0);

        if (licenseExists) {

            topPanel.remove(title);
            wrritenBy.setText("");
            webSite.setText(Util.getAppWebsite());
            copyright.setText("");
            BufferedImage wPic = null;
            try {
                //logoPanel.setPreferredSize(new Dimension(384,75));
                wPic = ImageIO.read(this.getClass().getResource(Util.getAppLogo()));
                JLabel wIcon = new JLabel(new ImageIcon(wPic));

                //wIcon.setPreferredSize(new Dimension(384,75));
                logoPanel.add(wIcon);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        String osVer = Util.is64Bit() ? "64" : "32";
        txt3rdPartySoftware.append("\n===============================\nThis OS Architecture Is: " + osVer + " Bits");

        String stationId = Util.getStationId();
        txt3rdPartySoftware.append("\nStation ID: " + stationId);
    }

    private void installCreditsArea() {
        txt3rdPartySoftware.setEditable(false);
        txt3rdPartySoftware.setLineWrap(true);
        txt3rdPartySoftware.setWrapStyleWord(true);
        txt3rdPartySoftware.setCaretPosition(0);

        topPanel.remove(txt3rdPartySoftware);
        JScrollPane scrollPane = new JScrollPane(txt3rdPartySoftware);
        topPanel.add(scrollPane, new GridConstraints(
                4, 0, 1, 2,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_BOTH,
                GridConstraints.SIZEPOLICY_WANT_GROW,
                GridConstraints.SIZEPOLICY_WANT_GROW,
                null,
                new Dimension(250, 180),
                null,
                0,
                false
        ));
    }

    private String buildThirdPartyCredits() {
        return String.join("\n",
                "The following third-party software, libraries, tools, and assets are used by SceneMax3D:",
                "",
                "Core IDE and Java libraries:",
                "- Java / OpenJDK runtime - https://openjdk.org/",
                "- Gradle build system and Gradle Wrapper - https://gradle.org/",
                "- Shadow Gradle plugin - https://github.com/johnrengelman/shadow",
                "- IntelliJ IDEA UI Designer runtime (forms_rt) - https://www.jetbrains.com/idea/",
                "- FlatLaf look and feel - https://www.formdev.com/flatlaf/",
                "- RSyntaxTextArea - https://github.com/bobbylight/RSyntaxTextArea",
                "- JGoodies Forms - https://www.jgoodies.com/freeware/libraries/forms/",
                "- JDOM and JDOM2 - http://www.jdom.org/",
                "- ASM bytecode libraries - https://asm.ow2.io/",
                "",
                "Parsing, data, networking, and persistence:",
                "- ANTLR 4 - https://www.antlr.org/",
                "- JSON-java (org.json) - https://github.com/stleary/JSON-java",
                "- Apache Commons IO - https://commons.apache.org/proper/commons-io/",
                "- Apache Commons Lang - https://commons.apache.org/proper/commons-lang/",
                "- SQLite JDBC - https://github.com/xerial/sqlite-jdbc",
                "- Zip4j - https://github.com/srikanth-lingala/zip4j",
                "- ftp4j - http://www.sauronsoftware.it/projects/ftp4j/",
                "- JSch - http://www.jcraft.com/jsch/",
                "- OkHttp - https://square.github.io/okhttp/",
                "- Okio - https://square.github.io/okio/",
                "- Socket.IO Java client and Engine.IO client - https://github.com/socketio/socket.io-client-java",
                "- NanoHTTPD - https://github.com/NanoHttpd/nanohttpd",
                "- SLF4J - https://www.slf4j.org/",
                "- Kotlin standard library - https://kotlinlang.org/",
                "- JAXB API - https://projects.eclipse.org/projects/ee4j.jaxb",
                "- Guava - https://github.com/google/guava",
                "- Gson - https://github.com/google/gson",
                "",
                "3D engine, rendering, input, UI, and game runtime:",
                "- jMonkeyEngine 3 - https://github.com/jMonkeyEngine/jmonkeyengine",
                "- LWJGL and LWJGL Assimp - https://www.lwjgl.org/",
                "- JInput and JUtils - https://github.com/jinput/jinput",
                "- OpenAL - https://openal.org/",
                "- Nifty GUI - https://github.com/nifty-gui/nifty-gui",
                "- Lemur GUI library - https://github.com/jMonkeyEngine-Contributions/Lemur",
                "- XPP3 XML pull parser - http://www.extreme.indiana.edu/xgws/xsoap/xpp/",
                "- Particle Monkey - https://jmonkeystore.com/189b56af-a1be-4036-8ac7-2b62a94935ff",
                "- Customizable Minimap - https://jmonkeystore.com/32ac86d0-3857-442f-853e-f78ce90f3b36",
                "- Select Object Outliner - https://jmonkeystore.com/5246c9ac-3f4c-4a5d-9fb0-470eb4026246",
                "",
                "Physics, vehicles, animation, and scene utilities:",
                "- Minie physics library by Stephen Gold - https://github.com/stephengold/Minie",
                "- Libbulletjme / Bullet Physics native runtime - https://github.com/stephengold/Libbulletjme",
                "- Heart library by Stephen Gold - https://github.com/stephengold/Heart",
                "- MaVehicles / JME vehicle libraries by Stephen Gold - https://github.com/stephengold/MaVehicles",
                "- Garrett by Stephen Gold - https://github.com/stephengold/Garrett",
                "- Wes by Stephen Gold - https://github.com/stephengold/Wes",
                "- SkyControl by Stephen Gold - https://github.com/stephengold/SkyControl",
                "- jme-ttf and sfntly font tooling - https://github.com/stephengold/jme-ttf",
                "- Sim-math - https://github.com/Simsilica/SimMath",
                "- j-ogg-vorbis - https://github.com/stephengold/j-ogg-vorbis",
                "",
                "Model, animation, video, and audio tooling:",
                "- JavaCV - https://github.com/bytedeco/javacv",
                "- JavaCPP - https://github.com/bytedeco/javacpp",
                "- FFmpeg - https://ffmpeg.org/",
                "- MonkeyWrench model importer by Stephen Gold - https://github.com/stephengold/MonkeyWrench",
                "- FBX2glTF - https://github.com/godotengine/FBX2glTF",
                "- Sketchfab asset services - https://sketchfab.com/",
                "- Killarney Raceway model - https://sketchfab.com/3d-models/killarney-raceway-e5e0e679b28b464b90adff7a37d9dfb3",
                "- FreeTTS - https://freetts.sourceforge.io/",
                "- AssemblyAI Java SDK - https://github.com/AssemblyAI/assemblyai-java-sdk",
                "",
                "Effekseer and bundled native/runtime components:",
                "- Effekseer - https://effekseer.github.io/",
                "- LLGI graphics abstraction library - https://github.com/effekseer/LLGI",
                "- ufbx - https://github.com/ufbx/ufbx",
                "- tinygltf - https://github.com/syoyo/tinygltf",
                "- nlohmann/json - https://github.com/nlohmann/json",
                "- Dear ImGui - https://github.com/ocornut/imgui",
                "- imgui-node-editor - https://github.com/thedmd/imgui-node-editor",
                "- GLFW - https://www.glfw.org/",
                "- FlatBuffers - https://github.com/google/flatbuffers",
                "- SPIRV-Cross - https://github.com/KhronosGroup/SPIRV-Cross",
                "- glslang - https://github.com/KhronosGroup/glslang",
                "- libpng - http://www.libpng.org/pub/png/libpng.html",
                "- zlib - https://zlib.net/",
                "- spdlog - https://github.com/gabime/spdlog",
                "- easy_profiler - https://github.com/yse/easy_profiler",
                "- Native File Dialog - https://github.com/mlabbe/nativefiledialog",
                "- OpenSoundMixer - https://github.com/effekseer/OpenSoundMixer",
                "",
                "Packaging, installers, launchers, and platform integration:",
                "- Zig native launcher toolchain - https://ziglang.org/",
                "- Launch4j - https://launch4j.sourceforge.net/",
                "- Inno Setup - https://jrsoftware.org/isinfo.php",
                "- NSIS - https://nsis.sourceforge.io/",
                "- JNI4Net - https://github.com/jni4net/jni4net/",
                "- .NET Framework - https://dotnet.microsoft.com/",
                "- itch.io butler uploader - https://itch.io/docs/butler/",
                "",
                "Testing and supporting build-time libraries:",
                "- JUnit - https://junit.org/",
                "- Hamcrest - http://hamcrest.org/JavaHamcrest/",
                "- Apache Ant - https://ant.apache.org/",
                "- Log4j - https://logging.apache.org/log4j/",
                "- Plexus Utils - https://codehaus-plexus.github.io/plexus-utils/",
                ""
        );
    }

    private void onOK() {
        // add your code here
        dispose();
    }

    private void onCancel() {
        // add your code here if necessary
        dispose();
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
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
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1, true, false));
        panel1.add(panel2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        buttonOK = new JButton();
        buttonOK.setText("OK");
        panel2.add(buttonOK, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonCancel = new JButton();
        buttonCancel.setText("Cancel");
        panel2.add(buttonCancel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        topPanel = new JPanel();
        topPanel.setLayout(new GridLayoutManager(5, 2, new Insets(0, 0, 0, 0), -1, -1));
        topPanel.setBackground(new Color(-1));
        contentPane.add(topPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        title = new JLabel();
        Font titleFont = this.$$$getFont$$$(null, -1, 48, title.getFont());
        if (titleFont != null) title.setFont(titleFont);
        title.setText("SceneMax3D");
        topPanel.add(title, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        wrritenBy = new JLabel();
        Font wrritenByFont = this.$$$getFont$$$(null, -1, 16, wrritenBy.getFont());
        if (wrritenByFont != null) wrritenBy.setFont(wrritenByFont);
        wrritenBy.setText("Written By: Adi Barda");
        topPanel.add(wrritenBy, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        copyright = new JLabel();
        copyright.setText(" (c) 2021 All rights reserved");
        topPanel.add(copyright, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txt3rdPartySoftware = new JTextArea();
        txt3rdPartySoftware.setText("");
        topPanel.add(txt3rdPartySoftware, new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(250, 50), null, 0, false));
        webSite = new JLabel();
        webSite.setText("www.scenemax3d.com ");
        topPanel.add(webSite, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        logoPanel = new JPanel();
        logoPanel.setLayout(new BorderLayout(0, 0));
        logoPanel.setBackground(new Color(-1));
        topPanel.add(logoPanel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
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
