package com.scenemax.desktop;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.scenemax.designer.DesignerDocument;
import com.scenemax.designer.DesignerPanel;
import com.scenemax.designer.Import3DModelPanel;
import com.scenemax.designer.animation.ImportAnimationPanel;
import com.scenemax.designer.effekseer.EffekseerEffectDocument;
import com.scenemax.designer.effekseer.EffekseerEffectDesignerPanel;
import com.scenemax.designer.effekseer.EffekseerImportResult;
import com.scenemax.designer.effekseer.EffekseerImporter;
import com.scenemax.designer.shader.EnvironmentShaderDesignerPanel;
import com.scenemax.designer.shader.EnvironmentShaderDocument;
import com.scenemax.designer.shader.EnvironmentShaderTemplatePreset;
import com.scenemax.designer.shader.ShaderDesignerPanel;
import com.scenemax.designer.shader.ShaderDocument;
import com.scenemax.designer.shader.ShaderTemplatePreset;
import com.scenemax.designer.ui.designer.UIDesignerPanel;
import com.scenemaxeng.common.ui.model.UIDocument;
import com.scenemaxeng.compiler.ApplyMacroResults;
import com.scenemaxeng.compiler.MacroFilter;
import com.scenemaxeng.common.types.*;
import com.scenemaxeng.compiler.Utils;
import org.apache.commons.io.FileUtils;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.formdev.flatlaf.FlatDarkLaf;
import javax.net.ssl.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.List;
import java.util.Timer;

import static javax.swing.JOptionPane.YES_OPTION;

public class MainApp extends JFrame implements IAppObserver, ActionListener, IServerEvents, KeyListener, PropertyChangeListener {

    private static final int MSG_UPDATE_CODE_NEW_MODELS = 100;

    private static int WINDOWS_APP = 100;
    private final JButton packageBrogramButton;
    private final JButton btnRunScript;
    private final JButton btnRecordScene;
    public final boolean licenseExists;

    private JPanel mainPanel;
    private JTree tree1;
    private JToolBar mainToolbar;
    private JSplitPane mainSplitPane;
    private JPanel editorHostPanel;
    private JButton btnAskQuestion;
    private JButton btnCancelQuestion;
    private JButton btnActiveQuestions;
    private JButton btnAnswers;
    private JPanel leftPanel;
    private JPanel classroomPane;
    private JPanel bottomPanel;
    private JPanel topPanel;
    private JTextField txtNavUrl;
    private JPanel gitStatusPanel;
    private RSyntaxTextArea textArea;
    private JScrollPane textAreaSP;
    private JTextArea textAreaRTL;
    private JScrollPane textAreaRtlSP;
    private JLayeredPane layeredPane;
    private List<JSONObject> roomQuestions = null;
    private JSONArray _members = null;
    private GroupManagementDialog groupConnectDialog;

    //private static SceneMaxApp smApp;
    private List<JSONObject> _answers = new ArrayList<>();
    private float DEFAULT_FONT_SIZE;
    private float EXTRA_FONT_SIZE = 12;
    private PackageProgramDialog packageProgramDialog;
    private ActionListener menuActionListener;
    private Integer originalTreeFontSize;
    private String lastSelectedFilePath;
    private boolean isDocumentChanged;
    private boolean lastSelectedNodeIsFile;
    private JMenuItem menuItemSendFolderTo;
    private JMenuItem menuItemUploadFolderToCloud;
    private JMenuItem menuItemSendFileTo;
    private boolean sendToFolderFlag = false;
    private boolean saveLastSelectedFileEnabled = true;
    private JMenu projectsSubMenu;
    private JMenu assetsMenu;
    private EditorTabPanel editorTabPanel;
    private SceneMaxAutoComplete autoComplete;
    // (Designer panels are managed per-tab inside EditorTabPanel)

    public MainApp() {

        new StrongAES().encrypt(); // encrypt config file if it exists
        long time = LicenseService.checkLicense();
        this.licenseExists = time != 1;

        disableSslVerification();

        initDataFolder();
        initUtils();

        // Override hardcoded background set by the UI Designer generated code
        leftPanel.setBackground(UIManager.getColor("Panel.background"));

        mainToolbar.setFloatable(false);

        btnRunScript = addToolbarButton(createToolbarIcon("run"), "run", "Run Program");
        addToolbarButton(createToolbarIcon("create_folder"), "create_folder", "Create Folder");
        addToolbarButton(createToolbarIcon("save"), "save", "Save Changes");

        JButton jb = addToolbarButton(createToolbarIcon("connect"), "connect", "Connect To Classroom");
        if (LicenseService.getClassroomServer().equals("NA")) {
            jb.setEnabled(false);
        }

        packageBrogramButton = addToolbarButton(createToolbarIcon("deploy"), "deploy", "Package Program");
        btnRecordScene = addToolbarButton(createToolbarIcon("run_record"), "run_record", "Run & Record Scene Clip");
        btnRecordScene.setEnabled(false); // until we find a way to get the running window rect

        textArea = new RSyntaxTextArea(20, 60);
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        Util.applyDarkTheme(textArea);

        textArea.setCodeFoldingEnabled(true);
        textArea.setTabSize(2);
        textArea.addKeyListener(this);
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            private void markDirty() {
                if (editorTabPanel != null && editorTabPanel.isSuppressingDocumentEvents()) {
                    return;
                }
                isDocumentChanged = true;
                if (editorTabPanel != null) {
                    editorTabPanel.markActiveTabDirty();
                }
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                markDirty();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                markDirty();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                markDirty();
            }
        });

        String fontSizeParam = AppDB.getInstance().getParam("DEFAULT_FONT_SIZE");
        DEFAULT_FONT_SIZE = textArea.getFont().getSize() + EXTRA_FONT_SIZE;
        float fontSize = DEFAULT_FONT_SIZE;
        if (fontSizeParam != null) {
            fontSize = Float.parseFloat(fontSizeParam);
        } else {
            AppDB.getInstance().setParam("DEFAULT_FONT_SIZE", fontSize + "");
        }

        textArea.setFont(textArea.getFont().deriveFont(fontSize));
        textAreaSP = new RTextScrollPane(textArea);

        textAreaRTL = new JTextArea(20, 60);
        textAreaRTL.setMargin(new Insets(10, 10, 10, 10));
        textAreaRTL.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        textAreaRTL.setFont(textAreaRTL.getFont().deriveFont(DEFAULT_FONT_SIZE * 2));
        textAreaRTL.addKeyListener(this);
        textAreaRtlSP = new JScrollPane(textAreaRTL);
        textAreaRtlSP.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

        layeredPane = new JLayeredPane();
        layeredPane.add(textAreaRtlSP, new Integer(10));
        layeredPane.add(textAreaSP, new Integer(20));

        layeredPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                SwingUtilities.invokeLater(new Runnable() {

                    @Override
                    public void run() {
                        Insets insets = layeredPane.getInsets();
                        int w = layeredPane.getWidth() - insets.left - insets.right;
                        int h = layeredPane.getHeight() - insets.top - insets.bottom;
                        textAreaSP.setBounds(0, 0, w, h);
                        textAreaRtlSP.setBounds(0, 0, w, h);


                        textAreaSP.setVisible(true);
                        textAreaRtlSP.setVisible(false);
                        textAreaSP.updateUI();

                    }
                });
            }

        });

        editorTabPanel = new EditorTabPanel(layeredPane, textArea, textAreaSP, textAreaRTL, textAreaRtlSP);
        autoComplete = new SceneMaxAutoComplete(textArea, () -> editorTabPanel != null && editorTabPanel.getActiveTab() != null
                ? editorTabPanel.getActiveFilePath() : null);
        editorTabPanel.setOnTabChangedCallback(() -> {
            EditorTabPanel.TabData active = editorTabPanel.getActiveTab();
            if (active != null) {
                lastSelectedFilePath = active.filePath;
                isDocumentChanged = active.dirty;
                lastSelectedNodeIsFile = true;
                boolean visualDesignerTab = active.isDesignerTab || active.isUIDesignerTab
                        || active.isEffekseerDesignerTab
                        || active.isShaderDesignerTab || active.isEnvironmentShaderDesignerTab
                        || active.isAnimationImportTab;
                textArea.setEnabled(!visualDesignerTab);
                textAreaRTL.setEnabled(!visualDesignerTab);
                btnRunScript.setEnabled(!visualDesignerTab && !active.filePath.endsWith(".cs"));

                // Select and focus the corresponding tree node
                openTreeNodeByFile(new File(active.filePath));

                // Invalidate autocomplete cache on tab switch
                autoComplete.invalidateCache();
            } else {
                lastSelectedFilePath = null;
                isDocumentChanged = false;
                lastSelectedNodeIsFile = false;
            }
        });
        editorHostPanel.add(editorTabPanel, BorderLayout.CENTER);

        btnActiveQuestions.setVisible(false);
        btnAnswers.setVisible(false);

        initMacroFolder();
        //initResourcesFolder();

        tree1.setCellRenderer(new ScriptsTreeCellRenderer());
        initScriptsFolder("scripts");

        // Clean up any leftover files from a previous interrupted model import
        Import3DModelPanel.cleanupLeftovers(Util.getScriptsFolder(), Util.getResourcesFolder());

        initTreePopupMenu();
        //initDb();
        initButtonHandlers();
        WebCommunication.getInstance().subscribe(this);
        AppDB.getInstance().setParam("joined_room", "");


        Util.setLicenseExists(this.licenseExists, LicenseService.getWhiteLabelCode());
        if (time <= 0) {
            String email = Util.getAppEmail();
            String msg = "This is a commercial version\r\n" +
                    "Your license has expired. \r\nPlease contact: " + email + " to extend your license";
            JOptionPane.showMessageDialog(null, msg, "License Expired", JOptionPane.INFORMATION_MESSAGE);

            System.exit(0);
        }

        txtNavUrl.setBorder(null);
        txtNavUrl.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                super.keyPressed(e);
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    String url = txtNavUrl.getText().trim();
                    runCommandLineUrl(url);

                }
            }
        });

        this.mainSplitPane.setResizeWeight(0.065);
        initGitStatusLabel();
        loadPlugins();
    }

    private void loadPlugins() {
        ISceneMaxPlugin plugin = PluginsManager.loadPlugin("gemini_integration", new GeminiAiIntegrationObserver(this), true);
        if (plugin != null) {
            plugin.start();
        }
    }

    private void runCommandLineUrl(String url) {

        if (url.startsWith("room install:")) {
            sendRoomCommand(url);
        } else if (url.startsWith("http")) {

            DownloadProgramFromWebDialog dlg = new DownloadProgramFromWebDialog(url, new Runnable() {

                @Override
                public void run() {
                    loadScriptsFolder();
                    openLastTreeNode();
                }
            });

            dlg.pack();
            dlg.setLocationRelativeTo(null);
            dlg.setModal(true);
            dlg.setVisible(true);

        } else {
            // free text is treated as a product SKU
            GetProgramDetailsFromWebStoreDialog dlg = new GetProgramDetailsFromWebStoreDialog(url, new Runnable() {

                @Override
                public void run() {
                    loadScriptsFolder();
                    openLastTreeNode();
                }
            });

            dlg.pack();
            dlg.setLocationRelativeTo(null);
            dlg.setModal(true);
            dlg.setVisible(true);

        }

    }

    private void sendRoomCommand(String url) {

        JSONObject cmd = createRoomCommand(url);
        if (cmd != null) {
            String room = AppDB.getInstance().getParam("joined_room");
            WebCommunication.getInstance().sendRoomCommand(room, cmd);

        }
    }

    private JSONObject createRoomCommand(String url) {

        JSONObject cmd = new JSONObject();

        if (url.startsWith("room install:")) {
            url = url.replace("room install:", "");
            cmd.put("command", "install");
            cmd.put("url", url.trim());
        }

        return cmd;
    }

    private void initUtils() {

        Util.GALLERY_HOST = getParam("gallery_host", AppConfig.get("gallery_host", "https://he.scenemax3d.com"));

        Util.CONSUMER_KEY = getParam("gallery_api_key", AppConfig.get("gallery_api_key"));
        Util.CONSUMER_SECRET = getParam("gallery_api_secret", AppConfig.get("gallery_api_secret"));

        Util.WRITE_CONSUMER_KEY = getParam("write_gallery_api_key", AppConfig.get("write_gallery_api_key"));
        Util.WRITE_CONSUMER_SECRET = getParam("write_gallery_api_secret", AppConfig.get("write_gallery_api_secret"));

        Util.FTP_UPLOAD_URL = getParam("ftp_upload_folder", AppConfig.get("ftp_upload_folder", "http://he.scenemax3d.com/wp-content/downloads/"));
        Util.FTP_HOST_NAME = getParam("ftp_host_name", AppConfig.get("ftp_host_name", "scenemax3d.com"));
        Util.FTP_USER_NAME = getParam("ftp_user", AppConfig.get("ftp_user"));
        Util.FTP_PASSWORD = getParam("ftp_password", AppConfig.get("ftp_password"));
        Util.FILE_TRANSFER_PROTOCOL = getParam("file_transfer_protocol", AppConfig.get("file_transfer_protocol", "FTP"));
        try {
            Util.FTP_PORT = Integer.parseInt(getParam("ftp_port", AppConfig.get("ftp_port", "21")));
        } catch (Exception e) {
            Util.FTP_PORT = "SFTP".equalsIgnoreCase(Util.FILE_TRANSFER_PROTOCOL) ? 22 : 21;
        }
    }

    private String getParam(String key, String defaultVal) {
        String val = AppDB.getInstance().getParam(key);
        if (val == null || val.length() == 0) {
            val = defaultVal;
        }

        return val;
    }

    private JMenuBar addMenuBar() {

        menuActionListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                String cmd = e.getActionCommand();

                if (cmd == null) return;

                if (cmd.equals("load_from_cloud")) {
                    UploadToCloudStorageDialog dlg = new UploadToCloudStorageDialog("");
                    dlg.pack();
                    dlg.setLocationRelativeTo(null);
                    dlg.show();
                    loadScriptsFolder();
                    openLastTreeNode();

                } else if (cmd.equals("load_program")) {

                    ImportProgramDialog dlg = new ImportProgramDialog();
                    dlg.setSize(500, 150);
                    dlg.setLocationRelativeTo(null);
                    dlg.setModal(true);
                    dlg.setVisible(true);

                    loadScriptsFolder();
                    openLastTreeNode();

                } else if (cmd.equals("new_project_scripts_folder")) {
                    createNewProjectScriptsFolder();
                } else if (cmd.equals("new_folder")) {
                    createNewScriptsFolder();
                } else if (cmd.equals("exit")) {
                    System.exit(0);
                } else if (cmd.equals("add_skybox")) {
                    ImportSkyBoxZipFileDialog dlg = new ImportSkyBoxZipFileDialog();
                    dlg.setSize(500, 150);
                    dlg.setLocationRelativeTo(null);
                    dlg.setModal(true);
                    dlg.setVisible(true);
                    refreshAssetsMenu();
                } else if (cmd.equals("import_audio")) {
                    ImportAudioFileDialog dlg = new ImportAudioFileDialog();
                    dlg.setSize(500, 150);
                    dlg.setLocationRelativeTo(null);
                    dlg.setModal(true);
                    dlg.setVisible(true);
                    refreshAssetsMenu();
                } else if (cmd.equals("add_sprite")) {
                    ImportSpriteSheetDialog dlg = new ImportSpriteSheetDialog();
                    dlg.setSize(500, 450);
                    dlg.setLocationRelativeTo(null);
                    dlg.setModal(true);
                    dlg.setVisible(true);
                    refreshAssetsMenu();
                } else if (cmd.equals("add_model")) {
                    openImport3DModelDocument();
                } else if (cmd.equals("import_animation")) {
                    openImportAnimationDocument();
                } else if (cmd.equals("import_effekseer")) {
                    importEffekseerEffect();
                } else if (cmd.equals("font_generator")) {
                    FontGeneratorDialog dlg = new FontGeneratorDialog(MainApp.this);
                    dlg.setLocationRelativeTo(MainApp.this);
                    dlg.setVisible(true);

                } else if (cmd.equals("open_assets_folder")) {
                    try {
                        Desktop.getDesktop().open(new File(Util.getResourcesFolder()));
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                } else if (cmd.equals("open_install_folder")) {
                    try {
                        Desktop.getDesktop().open(new File(Util.getWorkingDir()));
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                } else if (cmd.equals("online_help")) {
                    try {
                        String url = Util.getOnlineHelpUrl();
                        Desktop.getDesktop().browse(new URL(url).toURI());

                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                } else if (cmd.equals("about")) {
                    HelpAboutDialog dlg = new HelpAboutDialog(MainApp.this.licenseExists);

                    //dlg.pack();
                    dlg.setSize(800, 700);
//                    if(Util.getLicenseExists()) {
//                        dlg.setSize(800, 700);
//                    } else {
//                        dlg.setSize(500, 450);
//                    }
                    dlg.setLocationRelativeTo(null);
                    dlg.setModal(true);
                    dlg.setVisible(true);

                } else if (cmd.equals("macro_builder")) {
                    MacroBuilder dlg = new MacroBuilder();
                    dlg.setSize(800, 600);
                    dlg.setLocationRelativeTo(null);
                    dlg.setModal(true);
                    dlg.setVisible(true);
                } else if (cmd.equals("settings")) {
                    SettingsDialog dlg = new SettingsDialog();
                    dlg.setSize(800, 600);
                    dlg.setLocationRelativeTo(null);
                    dlg.setModal(true);
                    dlg.setVisible(true);

                    initUtils();

                // ─── Git commands ───
                } else if (cmd.startsWith("git_")) {
                    handleGitCommand(cmd);
                }
            }
        };

        JMenuBar mb = new JMenuBar();

        URL menuURL = MainApp.class.getResource("/menu/main_menu");
        try {
            InputStream script = menuURL.openStream();

            String menuJson = new String(Util.toByteArray(script));
            JSONObject menus = new JSONObject(menuJson);

            JSONArray items = menus.getJSONArray("items");
            for (int i = 0; i < items.length(); ++i) {
                JSONObject menuItem = items.getJSONObject(i);
                String name = menuItem.getString("name");
                JMenu menu = new JMenu(name);
                mb.add(menu);

                if (name.equals("Assets")) {
                    this.assetsMenu = menu;
                }

                // add sub items
                addMenuItems(menu, menuItem.getJSONArray("items"));

            }

        } catch (IOException e) {
            e.printStackTrace();
        }


        return mb;
    }

    private void addProjectsToMenu(JMenu menu) {
        menu.addSeparator();
        List<SceneMaxProject> projects = Util.getProjects_New();
        for (SceneMaxProject p : projects) {
            addProjectMenuItem(menu, p);
        }
    }

    private void addProjectMenuItem(JMenu menu, SceneMaxProject p) {
        JMenuItem item = new JMenuItem(p.name);
        ProjectMenuAction ma = new ProjectMenuAction(this, p);
        item.setAction(ma);
        menu.add(item);
    }

    private JSONObject getMenuJSON() {

        JSONObject menus = null;
        URL menuURL = MainApp.class.getResource("/menu/main_menu");
        try {
            InputStream script = menuURL.openStream();
            String menuJson = new String(Util.toByteArray(script));
            menus = new JSONObject(menuJson);

        } catch (IOException e) {
            e.printStackTrace();
        }

        return menus;
    }

    public void refreshAssetsMenu() {

        if (this.assetsMenu == null) {
            return;
        }

        this.assetsMenu.removeAll();

        JSONObject menus = getMenuJSON();
        JSONArray items = menus.getJSONArray("items");
        for (int i = 0; i < items.length(); ++i) {
            JSONObject menuItem = items.getJSONObject(i);
            String name = menuItem.getString("name");
            if (name.equals("Assets")) {
                // add sub items
                addMenuItems(this.assetsMenu, menuItem.getJSONArray("items"));
                break;
            }

        }

        this.assetsMenu.updateUI();

    }


    private void addMenuItems(JMenu menu, JSONArray items) {
        if (items == null) return;
        for (int i = 0; i < items.length(); ++i) {
            JSONObject item = items.getJSONObject(i);
            String name = item.getString("name");

            // Separator support
            if (name.equals("-")) {
                menu.addSeparator();
                continue;
            }

            if (item.has("items")) {
                JSONArray subItems = item.getJSONArray("items");
                JMenu subMenu = new JMenu(name);
                menu.add(subMenu);
                addMenuItems(subMenu, subItems);

                if (name.equals("Projects")) {
                    addProjectsToMenu(subMenu);
                    projectsSubMenu = subMenu;
                }

            } else {
                JMenuItem subItem = new JMenuItem(name);
                menu.add(subItem);
                subItem.addActionListener(menuActionListener);
                String command = item.has("command") ? item.getString("command") : "";
                subItem.setActionCommand(command);
                if (item.has("enabled")) {
                    subItem.setEnabled(item.getBoolean("enabled"));
                }

            }

        }
    }

    // ─── Git Integration ───

    private String getActiveProjectGitPath() {
        SceneMaxProject project = Util.getActiveProject();
        if (project == null) {
            JOptionPane.showMessageDialog(this, "No active project. Please create or select a project first.",
                    "Git", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return new File(project.path).getAbsolutePath();
    }

    private boolean checkGitInstalled() {
        if (!GitManager.isGitInstalled()) {
            String instructions = GitManager.getGitInstallInstructions();
            JTextArea textArea = new JTextArea(instructions);
            textArea.setEditable(false);
            textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setPreferredSize(new Dimension(500, 300));
            JOptionPane.showMessageDialog(this, scrollPane, "Git Not Found", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    private boolean checkGitRepo(String projectPath) {
        if (!GitManager.isGitRepository(projectPath)) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "This project is not a Git repository.\nWould you like to initialize one now?",
                    "Not a Git Repository", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                GitManager.GitResult result = GitManager.initRepository(projectPath);
                if (result.isSuccess()) {
                    JOptionPane.showMessageDialog(this, "Git repository initialized successfully!",
                            "Git Init", JOptionPane.INFORMATION_MESSAGE);
                    updateGitStatusLabel();

                    // Check if user name/email are configured
                    if (GitManager.getUserName().isEmpty() || GitManager.getUserEmail().isEmpty()) {
                        JOptionPane.showMessageDialog(this,
                                "Git user name and email are not configured.\nOpening configuration...",
                                "Git Setup", JOptionPane.INFORMATION_MESSAGE);
                        GitConfigDialog cfgDlg = new GitConfigDialog(this);
                        cfgDlg.setVisible(true);
                    }
                    return true;
                } else {
                    JOptionPane.showMessageDialog(this, "Failed to initialize repository:\n" + result.error,
                            "Git Init Error", JOptionPane.ERROR_MESSAGE);
                    return false;
                }
            }
            return false;
        }
        return true;
    }

    private void handleGitCommand(String cmd) {
        if (!checkGitInstalled()) return;

        if (cmd.equals("git_config")) {
            GitConfigDialog dlg = new GitConfigDialog(this);
            dlg.setVisible(true);
            updateGitStatusLabel();
            return;
        }

        if (cmd.equals("git_clone")) {
            GitCloneDialog dlg = new GitCloneDialog(this);
            dlg.setVisible(true);
            return;
        }

        String projectPath = getActiveProjectGitPath();
        if (projectPath == null) return;

        if (cmd.equals("git_init")) {
            if (GitManager.isGitRepository(projectPath)) {
                JOptionPane.showMessageDialog(this, "This project is already a Git repository.",
                        "Git Init", JOptionPane.INFORMATION_MESSAGE);
            } else {
                GitManager.GitResult result = GitManager.initRepository(projectPath);
                if (result.isSuccess()) {
                    JOptionPane.showMessageDialog(this, "Git repository initialized!\n" + result.output,
                            "Git Init", JOptionPane.INFORMATION_MESSAGE);
                    updateGitStatusLabel();

                    if (GitManager.getUserName().isEmpty() || GitManager.getUserEmail().isEmpty()) {
                        JOptionPane.showMessageDialog(this,
                                "Git user name and email are not configured.\nOpening configuration...",
                                "Git Setup", JOptionPane.INFORMATION_MESSAGE);
                        GitConfigDialog cfgDlg = new GitConfigDialog(this);
                        cfgDlg.setVisible(true);
                    }
                } else {
                    JOptionPane.showMessageDialog(this, "Failed to initialize:\n" + result.error,
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
            return;
        }

        // All remaining commands require an existing repo
        if (!checkGitRepo(projectPath)) return;

        switch (cmd) {
            case "git_commit": {
                GitCommitDialog dlg = new GitCommitDialog(this, projectPath);
                dlg.setVisible(true);
                updateGitStatusLabel();
                break;
            }
            case "git_push": {
                GitPushPullDialog dlg = new GitPushPullDialog(this, projectPath, GitPushPullDialog.Operation.PUSH);
                dlg.setVisible(true);
                updateGitStatusLabel();
                break;
            }
            case "git_pull": {
                GitPushPullDialog dlg = new GitPushPullDialog(this, projectPath, GitPushPullDialog.Operation.PULL);
                dlg.setVisible(true);
                loadScriptsFolder();
                openLastTreeNode();
                updateGitStatusLabel();
                break;
            }
            case "git_pull_rebase": {
                GitPushPullDialog dlg = new GitPushPullDialog(this, projectPath, GitPushPullDialog.Operation.PULL_REBASE);
                dlg.setVisible(true);
                loadScriptsFolder();
                openLastTreeNode();
                updateGitStatusLabel();
                break;
            }
            case "git_fetch": {
                GitPushPullDialog dlg = new GitPushPullDialog(this, projectPath, GitPushPullDialog.Operation.FETCH);
                dlg.setVisible(true);
                break;
            }
            case "git_branches": {
                GitBranchDialog dlg = new GitBranchDialog(this, projectPath);
                dlg.setVisible(true);
                loadScriptsFolder();
                openLastTreeNode();
                updateGitStatusLabel();
                break;
            }
            case "git_log": {
                GitLogDialog dlg = new GitLogDialog(this, projectPath);
                dlg.setVisible(true);
                break;
            }
            case "git_stash": {
                GitManager.GitResult result = GitManager.stash(projectPath);
                if (result.isSuccess()) {
                    JOptionPane.showMessageDialog(this, "Changes stashed.\n" + result.output,
                            "Git Stash", JOptionPane.INFORMATION_MESSAGE);
                    loadScriptsFolder();
                    openLastTreeNode();
                } else {
                    JOptionPane.showMessageDialog(this, "Stash failed:\n" + result.getFullOutput(),
                            "Git Stash", JOptionPane.ERROR_MESSAGE);
                }
                updateGitStatusLabel();
                break;
            }
            case "git_stash_pop": {
                GitManager.GitResult result = GitManager.stashPop(projectPath);
                if (result.isSuccess()) {
                    JOptionPane.showMessageDialog(this, "Stash applied.\n" + result.output,
                            "Git Stash Pop", JOptionPane.INFORMATION_MESSAGE);
                    loadScriptsFolder();
                    openLastTreeNode();
                } else {
                    JOptionPane.showMessageDialog(this, "Stash pop failed:\n" + result.getFullOutput(),
                            "Git Stash Pop", JOptionPane.ERROR_MESSAGE);
                }
                updateGitStatusLabel();
                break;
            }
            case "git_create_gitignore": {
                if (GitManager.hasGitignore(projectPath)) {
                    int choice = JOptionPane.showConfirmDialog(this,
                            ".gitignore already exists. Overwrite with default?",
                            "Create .gitignore", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (choice != JOptionPane.YES_OPTION) return;
                }
                try {
                    GitManager.createDefaultGitignore(projectPath);
                    JOptionPane.showMessageDialog(this, ".gitignore created successfully.",
                            "Git", JOptionPane.INFORMATION_MESSAGE);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Failed to create .gitignore:\n" + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
                break;
            }
        }
    }

    private JLabel lblGitBranch;

    private void initGitStatusLabel() {
        if (lblGitBranch == null) {
            lblGitBranch = new JLabel();
            lblGitBranch.setFont(lblGitBranch.getFont().deriveFont(Font.PLAIN, 11f));
            lblGitBranch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            lblGitBranch.setToolTipText("Click to open Git branches");
            lblGitBranch.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (GitManager.isGitInstalled()) {
                        String path = getActiveProjectGitPath();
                        if (path != null && GitManager.isGitRepository(path)) {
                            GitBranchDialog dlg = new GitBranchDialog(MainApp.this, path);
                            dlg.setVisible(true);
                            updateGitStatusLabel();
                            loadScriptsFolder();
                            openLastTreeNode();
                        }
                    }
                }
            });
            gitStatusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
            gitStatusPanel.setOpaque(false);
            gitStatusPanel.add(lblGitBranch);
            topPanel.add(gitStatusPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST,
                    GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW,
                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        }
        updateGitStatusLabel();
    }

    private void updateGitStatusLabel() {
        if (lblGitBranch == null) return;

        SwingUtilities.invokeLater(() -> {
            if (!GitManager.isGitInstalled()) {
                lblGitBranch.setText("  Git: not installed");
                lblGitBranch.setForeground(new Color(150, 150, 150));
                return;
            }

            String projectPath = null;
            SceneMaxProject project = Util.getActiveProject();
            if (project != null) {
                projectPath = new File(project.path).getAbsolutePath();
            }

            if (projectPath == null || !GitManager.isGitRepository(projectPath)) {
                lblGitBranch.setText("  Git: no repo");
                lblGitBranch.setForeground(new Color(150, 150, 150));
                return;
            }

            String branch = GitManager.getCurrentBranch(projectPath);
            boolean hasChanges = GitManager.hasUncommittedChanges(projectPath);
            String changedIndicator = hasChanges ? " *" : "";

            lblGitBranch.setText("  \u2387 " + branch + changedIndicator);
            lblGitBranch.setForeground(hasChanges ? new Color(255, 200, 100) : new Color(100, 200, 100));
        });
    }

    private void initButtonHandlers() {
        btnAskQuestion.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                askQuestion();
            }
        });

        btnCancelQuestion.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cancelQuestion();
            }
        });

        btnActiveQuestions.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showActiveQuestions();
            }
        });

        btnAnswers.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showActiveAnsqwers();
            }
        });

    }

    private void showActiveQuestions() {

        ActiveQuestionsDialog dlg = new ActiveQuestionsDialog();
        dlg.setQuestions(roomQuestions);
        //dlg.pack();
        dlg.setSize(1000, 600);
        dlg.setLocationRelativeTo(null);
        dlg.setVisible(true);

    }

    private void showActiveAnsqwers() {

        ActiveAnswersDialog dlg = new ActiveAnswersDialog();
        dlg.setAnswers(_members, _answers);
        dlg.setSize(1000, 600);
        dlg.setLocationRelativeTo(null);

        Answer ans = dlg.showDialog();
        if (ans != null) {
            applyAnswer(ans);
        }

        showAnswersCountInfo();
    }

    private void applyAnswer(Answer ans) {

        File script = new File(Util.getScriptsFolder() + "/" + ans.folderName + "/" + ans.fileName);
        if (script.exists()) {
            try {
                FileUtils.write(script, ans.answerCode, StandardCharsets.UTF_8);
            } catch (IOException e) {
                e.printStackTrace();
            }
            saveSelectedTreeNodePosition(script.getParentFile().getPath(), ans.fileName);
            openLastTreeNode();
            loadFileToTextEditor(script);
        }
    }

    private void cancelQuestion() {
        String room = AppDB.getInstance().getParam("joined_room");
        WebCommunication.getInstance().acceptAnswer(room);
    }

    private void askQuestion() {
        String question = JOptionPane.showInputDialog(
                null,
                "Type your question (optional)",
                "Classroom Question",
                JOptionPane.PLAIN_MESSAGE
        );

        if (question == null) {
            return;
        }

        question = question.trim();
        String room = AppDB.getInstance().getParam("joined_room");
        JSONArray script = new JSONArray();
        script.put(textArea.getText().trim());
        File f = new File(lastSelectedFilePath);
        JSONObject files = new JSONObject();
        if (f.isFile()) {
            files = getFolderFiles(f.getParentFile());
        }

        WebCommunication.getInstance().askQuestion(room, question, script, f.getParentFile().getName(), files);

    }

    // private void initDb() {
    //     _db = AppDB.getInstance();
    // }

    private void initDataFolder() {

        File f = new File("data");

        if (!f.exists()) {
            f.mkdir();
        }

        f = new File("deploy");

        if (!f.exists()) {
            f.mkdir();
        }

        f = new File("out");

        if (!f.exists()) {
            f.mkdir();
        }

        f = new File("tmp");

        if (!f.exists()) {
            f.mkdir();
        }

        f = new File("plugins");

        if (!f.exists()) {
            f.mkdir();
        }
    }

    private void initTreePopupMenu() {

        // add MouseListener to tree
        MouseAdapter ma = new MouseAdapter() {
            private void myPopupEvent(MouseEvent e) {
                int x = e.getX();
                int y = e.getY();

                TreePath path = tree1.getPathForLocation(x, y);
                if (path == null)
                    return;

                JPopupMenu popup = getNodePopupMenu(path);
                popup.show(tree1, x, y);
            }

            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) myPopupEvent(e);
            }

            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) myPopupEvent(e);
            }
        };


        tree1.addMouseListener(ma);

    }

    private void addScene(String parentPath) {
        String sceneName = (String) JOptionPane.showInputDialog(
                null,
                "Type new scene name",
                "Add Scene",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                "");

        if (sceneName == null || sceneName.trim().length() == 0) {
            return;
        }

        sceneName = sceneName.trim();

        // Validate as a valid folder name (no path separators or invalid chars)
        if (sceneName.matches(".*[\\\\/:*?\"<>|].*")) {
            JOptionPane.showMessageDialog(null,
                    "Invalid scene name. Avoid these characters: \\ / : * ? \" < > |",
                    "Invalid Name", JOptionPane.ERROR_MESSAGE);
            return;
        }

        File sceneFolder = new File(parentPath + "/" + sceneName);
        if (sceneFolder.exists()) {
            JOptionPane.showMessageDialog(null,
                    "A folder named '" + sceneName + "' already exists.",
                    "Scene Already Exists", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (!sceneFolder.mkdir()) {
            JOptionPane.showMessageDialog(null,
                    "Folder creation failed. Try again with a different name.",
                    "Create Scene Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Create the designer document (.smdesign and companion .code files)
        String docName = sceneName + ".smdesign";
        File designerFile = new File(sceneFolder, docName);
        try {
            DesignerDocument.writeEmptyFile(designerFile);
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null,
                    "Error creating designer document: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Create the main file with a reference to the designer's generated code
        File mainFile = new File(sceneFolder, "main");
        try {
            String mainContent = "add \"" + sceneName + ".code\" code\n";
            Files.write(mainFile.toPath(), mainContent.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        // Refresh tree and open the designer document
        saveSelectedTreeNodePosition(sceneFolder.getPath(), docName);
        loadScriptsFolder();
        openLastTreeNode();
    }

    private JPopupMenu getNodePopupMenu(TreePath path) {

        ActionListener popupActionListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                String cmd = actionEvent.getActionCommand();

                DefaultMutableTreeNode obj = (DefaultMutableTreeNode) tree1.getLastSelectedPathComponent();
                if (obj == null) {
                    return;
                }

                String filePath = ((ScriptPathNode) obj.getUserObject()).getPath();
                if (cmd.equals("add_scene")) {
                    addScene(filePath);
                } else if (cmd.equals("create_sub_folder")) {
                    createSubFolder(filePath);
                } else if (cmd.equals("export_native_android")) {
                    exportNativeAndroidApp(filePath);
                } else if (cmd.equals("save")) {
                    if (editorTabPanel != null && editorTabPanel.isFileOpen(filePath)) {
                        editorTabPanel.saveActiveTab();
                    } else {
                        saveLastEditedScript(filePath);
                    }
                } else if (cmd.equals("new")) {
                    createNewScript(filePath);
                } else if (cmd.equals("new_designer")) {
                    createNewDesignerDocument(filePath);
                } else if (cmd.equals("new_ui_document")) {
                    createNewUIDocument(filePath);
                } else if (cmd.equals("new_shader_document")) {
                    createNewShaderDocument(filePath);
                } else if (cmd.equals("new_environment_shader_document")) {
                    createNewEnvironmentShaderDocument(filePath);
                } else if (cmd.equals("clean_backup_files")) {
                    cleanBackupFiles(new File(filePath));
                } else if (cmd.equals("rename_folder")) {
                    renameFolder(new File(filePath));
                } else if (cmd.equals("delete_folder")) {
                    File f = new File(filePath);
                    int n = JOptionPane.showConfirmDialog(
                            null,
                            "Are you sure you want to delete '" + f.getName() + "'?",
                            "Delete Folder",
                            JOptionPane.YES_NO_OPTION);
                    if (n == YES_OPTION) {
                        try {
                            editorTabPanel.closeTabsUnderFolder(filePath);
                            FileUtils.deleteDirectory(f);
                            obj.removeFromParent();
                            tree1.updateUI();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    }

                } else if (cmd.equals("send_folder_to")) {
                    sendFolderToRoomMember(filePath);
                } else if (cmd.equals("upload_to_cloud")) {
                    uploadProgramToCloudStorage(filePath);
                } else if (cmd.equals("send_file_to")) {
                    sendFileToRoomMember(filePath);
                } else if (cmd.equals("move_file_to")) {
                    moveFileToFolder(filePath);
                } else if (cmd.equals("upload_to_hub")) {
                    uploadToHub(filePath);
                } else if (cmd.equals("delete")) {

                    File f = new File(filePath);
                    int n = JOptionPane.showConfirmDialog(
                            null,
                            "Are you sure you want to delete '" + f.getName() + "'?",
                            "Delete File",
                            JOptionPane.YES_NO_OPTION);
                    if (n == YES_OPTION) {
                        // Pass deleting=true for .smdesign files so the shared
                        // JME app's in-memory state is cleared and stale entities
                        // are not accidentally written to a future file
                        boolean isDesigner = f.getName().toLowerCase().endsWith(".smdesign");
                        boolean isUIDesigner = f.getName().toLowerCase().endsWith(".smui");
                        boolean isEffekseerDesigner = f.getName().toLowerCase().endsWith(".smeffectdesign");
                        boolean isShaderDesigner = f.getName().toLowerCase().endsWith(".smshader");
                        boolean isEnvironmentShaderDesigner = f.getName().toLowerCase().endsWith(".smenvshader");
                        editorTabPanel.closeTabByPath(filePath,
                                isDesigner || isUIDesigner || isEffekseerDesigner || isShaderDesigner || isEnvironmentShaderDesigner);

                        // If this is a .smdesign file, also delete its companion .code, _init.code
                        // and _end.code files and clean up any DB references (open_tabs)
                        if (isDesigner) {
                            File codeFile = DesignerDocument.getCodeFile(f);
                            if (codeFile.exists()) {
                                editorTabPanel.closeTabByPath(codeFile.getAbsolutePath());
                                codeFile.delete();
                            }
                            File initFile = DesignerDocument.getInitCodeFile(f);
                            if (initFile.exists()) {
                                editorTabPanel.closeTabByPath(initFile.getAbsolutePath());
                                initFile.delete();
                            }
                            File endFile = DesignerDocument.getEndCodeFile(f);
                            if (endFile.exists()) {
                                editorTabPanel.closeTabByPath(endFile.getAbsolutePath());
                                endFile.delete();
                            }
                            // Remove all companion file paths from the persisted open_tabs in DB
                            SceneMaxProject currentProject = Util.getActiveProject();
                            if (currentProject != null) {
                                String savedTabs = AppDB.getInstance().getParam("open_tabs~" + currentProject.name);
                                if (savedTabs != null && !savedTabs.isEmpty()) {
                                    try {
                                        JSONArray pathsArray = new JSONArray(savedTabs);
                                        JSONArray cleaned = new JSONArray();
                                        String codeFilePath = codeFile.getAbsolutePath();
                                        String initFilePath = initFile.getAbsolutePath();
                                        String endFilePath = endFile.getAbsolutePath();
                                        for (int i = 0; i < pathsArray.length(); i++) {
                                            String p = pathsArray.getString(i);
                                            if (!p.equals(filePath) && !p.equals(codeFilePath)
                                                    && !p.equals(initFilePath) && !p.equals(endFilePath)) {
                                                cleaned.put(p);
                                            }
                                        }
                                        AppDB.getInstance().setParam("open_tabs~" + currentProject.name, cleaned.toString());
                                    } catch (JSONException ignored) {}
                                }
                            }
                        }

                        // If this is a .smui file, also delete its companion _ui.code file
                        if (isUIDesigner) {
                            File uiCodeFile = UIDocument.getCodeFile(f);
                            if (uiCodeFile.exists()) {
                                editorTabPanel.closeTabByPath(uiCodeFile.getAbsolutePath());
                                uiCodeFile.delete();
                            }
                        }

                        if (isShaderDesigner) {
                            File shaderOutputFolder = ShaderDocument.getRuntimeFolder(f, Util.getResourcesFolder());
                            if (shaderOutputFolder.exists()) {
                                try {
                                    FileUtils.deleteDirectory(shaderOutputFolder);
                                } catch (IOException ex) {
                                    ex.printStackTrace();
                                }
                            }
                        }

                        if (isEnvironmentShaderDesigner) {
                            File shaderOutputFolder = EnvironmentShaderDocument.getRuntimeFolder(f, Util.getResourcesFolder());
                            if (shaderOutputFolder.exists()) {
                                try {
                                    FileUtils.deleteDirectory(shaderOutputFolder);
                                } catch (IOException ex) {
                                    ex.printStackTrace();
                                }
                            }
                        }

                        if (isEffekseerDesigner) {
                            try {
                                EffekseerEffectDocument doc = EffekseerEffectDocument.load(f);
                                String resourcesFolder = Util.getResourcesFolder();
                                if (resourcesFolder != null && !resourcesFolder.isBlank()) {
                                    EffekseerImporter.deleteDocumentAsset(doc, new File(resourcesFolder));
                                }
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }
                        }

                        f.delete();
                        obj.removeFromParent();
                        tree1.updateUI();
                        if (isEffekseerDesigner) {
                            refreshAssetsMenu();
                        }
                    }

                } else if (cmd.equals("upload_to_web")) {//

                    String prg = MainApp.this.getMainFileContent(filePath);
                    filePath = MainApp.this.getMainFilePath(filePath);
                    //String prg = textAreaSP.isVisible() ? textArea.getText() : textAreaRTL.getText();
                    ApplyMacroResults mr = applyMacro(prg);
                    ExportProgramDialog dlg = new ExportProgramDialog(filePath, mr.finalPrg, true);
                    dlg.setSize(550, 600);
                    dlg.setLocationRelativeTo(null);
                    dlg.setModal(true);
                    dlg.setVisible(true);

                } else if (cmd.equals("export_to_zip")) {
                    exportProgramToLocalZipFile(filePath);
                } else if (cmd.equals("import_from_zip")) {
                    ImportProgramDialog dlg = new ImportProgramDialog();
                    dlg.setSize(500, 150);
                    dlg.setLocationRelativeTo(null);
                    dlg.setModal(true);
                    dlg.setVisible(true);

                    loadScriptsFolder();
                    openLastTreeNode();
                } else if (cmd.equals("rename")) {

                    Path source = Paths.get(filePath);
                    String newName = JOptionPane.showInputDialog(
                            "Rename File",
                            obj.getUserObject().toString());
                    if (newName != null && newName.trim().length() > 0) {
                        try {
                            String oldPath = source.toString();
                            Path dest = source.resolveSibling(newName.trim());
                            Files.move(source, dest);
                            if (oldPath.toLowerCase().endsWith(".smshader")) {
                                File oldFile = new File(oldPath);
                                File newFile = dest.toFile();
                                File oldOutput = ShaderDocument.getRuntimeFolder(oldFile, Util.getResourcesFolder());
                                File newOutput = ShaderDocument.getRuntimeFolder(newFile, Util.getResourcesFolder());
                                if (oldOutput.exists() && !newOutput.exists()) {
                                    FileUtils.moveDirectory(oldOutput, newOutput);
                                }
                            }
                            if (oldPath.toLowerCase().endsWith(".smenvshader")) {
                                File oldFile = new File(oldPath);
                                File newFile = dest.toFile();
                                File oldOutput = EnvironmentShaderDocument.getRuntimeFolder(oldFile, Util.getResourcesFolder());
                                File newOutput = EnvironmentShaderDocument.getRuntimeFolder(newFile, Util.getResourcesFolder());
                                if (oldOutput.exists() && !newOutput.exists()) {
                                    FileUtils.moveDirectory(oldOutput, newOutput);
                                }
                            }
                            obj.setUserObject(new ScriptPathNode(dest.toString(), newName.trim()));
                            tree1.updateUI();
                            editorTabPanel.updateTabForRename(oldPath, dest.toString());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    }

                } else if (cmd.equals("connect_to_doc")) {
                    //connectToGoogleDoc(filePath);
                } else if (cmd.equals("copy_absolute_path")) {
                    String absolutePath = new File(filePath).getAbsolutePath();
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(absolutePath), null);
                }

            }
        };

        tree1.setSelectionPath(path);
        DefaultMutableTreeNode obj = (DefaultMutableTreeNode) path.getLastPathComponent();
        String filePath = ((ScriptPathNode) obj.getUserObject()).getPath();
        File file = new File(filePath);
        JPopupMenu popup = new JPopupMenu();

        addScriptsTreePopupMenuItem("Add Scene...", "add_scene", popup, popupActionListener, true, false, file);
        if (file.isDirectory()) {
            popup.addSeparator();
        }
        addScriptsTreePopupMenuItem("Save", "save", popup, popupActionListener, false, true, file);
        addScriptsTreePopupMenuItem("Copy Absolute Path", "copy_absolute_path", popup, popupActionListener, false, true, file);
        JMenuItem item = addScriptsTreePopupMenuItem("Delete...", "delete", popup, popupActionListener, false, true, file);
        if (item != null) {
            item.setEnabled(!file.getName().equals("main"));
        }
        addScriptsTreePopupMenuItem("Rename...", "rename", popup, popupActionListener, false, true, file);
        addScriptsTreePopupMenuItem("Create New Script", "new", popup, popupActionListener, true, false, file);
        addScriptsTreePopupMenuItem("Create Designer Document", "new_designer", popup, popupActionListener, true, false, file);
        addScriptsTreePopupMenuItem("Create UI Document", "new_ui_document", popup, popupActionListener, true, false, file);
        addScriptsTreePopupMenuItem("Create Shader Document", "new_shader_document", popup, popupActionListener, true, false, file);
        addScriptsTreePopupMenuItem("Create Environment Shader Document", "new_environment_shader_document", popup, popupActionListener, true, false, file);
        addScriptsTreePopupMenuItem("Create Sub Folder...", "create_sub_folder", popup, popupActionListener, true, false, file);
        addScriptsTreePopupMenuItem("Delete Folder...", "delete_folder", popup, popupActionListener, true, false, file);
        addScriptsTreePopupMenuItem("Rename Folder...", "rename_folder", popup, popupActionListener, true, false, file);
        addScriptsTreePopupMenuItem("Clean Backup Files...", "clean_backup_files", popup, popupActionListener, true, false, file);

        menuItemSendFolderTo = addScriptsTreePopupMenuItem("Send To...", "send_folder_to", popup, popupActionListener, true, false, file);
        if (menuItemSendFolderTo != null) {
            menuItemSendFolderTo.setEnabled(sendToFolderFlag);
        }

        menuItemUploadFolderToCloud = addScriptsTreePopupMenuItem("Upload To Cloud...", "upload_to_cloud", popup, popupActionListener, true, false, file);
        if (menuItemUploadFolderToCloud != null) {
            menuItemUploadFolderToCloud.setEnabled(sendToFolderFlag);
        }

        addScriptsTreePopupMenuItem("Export Program To Zip File...", "export_to_zip", popup, popupActionListener, true, false, file);
        addScriptsTreePopupMenuItem("Import Program From Zip File...", "import_from_zip", popup, popupActionListener, true, false, file);
        item = addScriptsTreePopupMenuItem("Upload Program To Web...", "upload_to_web", popup, popupActionListener, true, false, file);

        if (item != null) {
            if (Util.WRITE_CONSUMER_KEY == null || Util.WRITE_CONSUMER_KEY.length() == 0
                    || Util.WRITE_CONSUMER_SECRET == null || Util.WRITE_CONSUMER_SECRET.length() == 0) {
                item.setEnabled(false);
            }
        }

        menuItemSendFileTo = addScriptsTreePopupMenuItem("Send To...", "send_file_to", popup, popupActionListener, false, true, file);
        if (menuItemSendFileTo != null) {
            menuItemSendFileTo.setEnabled(sendToFolderFlag);
        }

        addScriptsTreePopupMenuItem("Move To...", "move_file_to", popup, popupActionListener, false, true, file);

        if (!"NA".equals(LicenseService.getStorageAppKey())) {
            addScriptsTreePopupMenuItem("Deploy To GameBox 3D...", "upload_to_hub", popup, popupActionListener, true, false, file);
        }

        addScriptsTreePopupMenuItem("Export to native Android...", "export_native_android", popup, popupActionListener, true, false, file);


        return popup;
    }

    private void exportProgramToLocalZipFile(String filePath) {
        String prg = this.getMainFileContent(filePath);
        filePath = this.getMainFilePath(filePath);
        ExportProgramDialog dlg = new ExportProgramDialog(filePath, prg, false);
        dlg.setSize(500, 200);
        dlg.setLocationRelativeTo(null);
        dlg.setModal(true);
        dlg.setVisible(true);
    }

    public String exportNativeAndroidApp(String filePath) {
        String prg = "";

        File f = new File(filePath);
        if (f.isDirectory()) {
            f = new File(f, "main");
            if (!f.exists()) {
                return null;
            }
            filePath = f.getAbsolutePath();
            prg = Util.readFile(f);
        } else {
            prg = textAreaSP.isVisible() ? textArea.getText() : textAreaRTL.getText();
        }

        ExportToAndroidNativeDialog dlg = new ExportToAndroidNativeDialog(filePath, prg);
        dlg.setSize(900, 400);
        dlg.setLocationRelativeTo(null);
        dlg.setModal(true);
        dlg.setVisible(true);

        return dlg.getProjectFolder();
    }

    private void cleanBackupFiles(File folder) {

        int n = JOptionPane.showConfirmDialog(
                null,
                "Are you sure you want to clean '" + folder.getName() + "' backup files?",
                "Clean Backup Files",
                JOptionPane.YES_NO_OPTION);
        if (n == YES_OPTION) {
            File[] files = folder.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".bkup");
                }
            });

            for (int i = 0; i < files.length; ++i) {
                files[i].delete();
            }

            saveSelectedTreeNodePosition(folder.getPath(), "main");
            loadScriptsFolder();
            openLastTreeNode();
        }
    }

    private void renameFolder(File folder) {
        String folderName = (String) JOptionPane.showInputDialog(
                null,
                "Type new folder name",
                "Folder Name",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                "");

        if (folderName == null || folderName.trim().length() == 0) {
            return;
        }

        folderName = folderName.trim();

        try {

            editorTabPanel.closeTabsUnderFolder(folder.getAbsolutePath());

            File parent = folder.getParentFile();
            File newFolder = new File(parent, folderName);
            FileUtils.moveDirectory(folder, newFolder);

            saveSelectedTreeNodePosition(newFolder.getPath(), "main");

            loadScriptsFolder();
            openLastTreeNode();

        } catch (IOException e) {
            e.printStackTrace();

            JOptionPane.showMessageDialog(null, "Folder rename failed. Try again with a different folder name", "Rename Folder Error", JOptionPane.INFORMATION_MESSAGE);

        }


    }

    private String getMainFilePath(String filePath) {

        File f = new File(filePath);
        if (f.isDirectory()) {
            f = new File(f, "main");
            if (!f.exists()) {
                return "";
            }
            filePath = f.getAbsolutePath();
        }

        return filePath;
    }

    private String getMainFileContent(String ScriptFolder) {
        String prg = "";
        File f = new File(ScriptFolder);
        if (f.isDirectory()) {
            f = new File(f, "main");
            if (!f.exists()) {
                return "";
            }
            //filePath = f.getAbsolutePath();
            prg = Util.readFile(f);
        } else {
            prg = textAreaSP.isVisible() ? textArea.getText() : textAreaRTL.getText();
        }

        return prg;
    }

    private void uploadToHub(String filePath) {

        String prg = this.getMainFileContent(filePath);
        filePath = this.getMainFilePath(filePath);

        ApplyMacroResults mr = applyMacro(prg);
        UploadToGameHubDialog dlg = new UploadToGameHubDialog(filePath, mr.finalPrg);
        //dlg.setSize(550,300);
        dlg.pack();
        dlg.setLocationRelativeTo(null);
        dlg.setModal(true);
        dlg.setVisible(true);

    }

    private void moveFileToFolder(String path) {

        File selectedFile = new File(path);
        SelectFolderDialog dlg = new SelectFolderDialog();
        dlg.pack();
        dlg.setLocationRelativeTo(null);
        File selectedFolder = dlg.showDialog();
        try {
            FileUtils.moveFileToDirectory(selectedFile, selectedFolder, false);
            saveSelectedTreeNodePosition(selectedFolder.getPath(), selectedFile.getName());
            loadScriptsFolder();
            openLastTreeNode();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null,
                    "Cannot move selected file", "Move File Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void sendFileToRoomMember(String path) {
        SelectRoomMemberDialog dlg = new SelectRoomMemberDialog();
        dlg.pack();
        dlg.setLocationRelativeTo(null);

        List<RoomMember> res = dlg.showDialog();
        if (res == null) {
            return;
        }

        File file = new File(path);
        File folder = file.getParentFile();
        String folderName = folder.getName();
        int atPos = folderName.indexOf("@");
        if (atPos != -1) {
            folderName = folderName.substring(0, atPos);
        }

        if (res.size() > 0) {
            String room = AppDB.getInstance().getParam("joined_room");
            JSONObject files = getSingleFile(file);

            for (RoomMember rm : res) {
                WebCommunication.getInstance().sendSingleFile(room, rm.getUserId(), folderName, files);
            }

        }
    }

    private void uploadProgramToCloudStorage(String path) {

        File folder = new File(path);
        File main = new File(folder, "main");
        if (!main.exists()) {
            JOptionPane.showMessageDialog(null, "Cannot upload a program without \"main\" file", "Upload Error", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String prg = "";
        try {
            prg = FileUtils.readFileToString(main, StandardCharsets.UTF_8);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Cannot upload program. Problem reading \"main\" file", "Upload Error", JOptionPane.INFORMATION_MESSAGE);
            e.printStackTrace();
            return;
        }


        ApplyMacroResults mr = applyMacro(prg);

        UploadToCloudStorageDialog dlg = new UploadToCloudStorageDialog(main.getAbsolutePath(), prg);
        dlg.pack();
        dlg.setLocationRelativeTo(null);
        dlg.show();
    }


    private void sendFolderToRoomMember(String path) {
        SelectRoomMemberDialog dlg = new SelectRoomMemberDialog();
        dlg.pack();
        dlg.setLocationRelativeTo(null);
        //dlg.setModal(true);
        List<RoomMember> res = dlg.showDialog();
        if (res == null) {
            return;
        }

        File folder = new File(path);
        String folderName = folder.getName();
        if (res.size() > 0) {
            String room = AppDB.getInstance().getParam("joined_room");
            JSONObject files = getFolderFiles(folder);

            String userName = AppDB.getInstance().getParam("last_nick_name");
            for (RoomMember rm : res) {
                WebCommunication.getInstance().sendFolderContent(room, rm.getUserId(), folderName + "@" + userName, files);
            }

        }
    }


    private JSONObject getSingleFile(File file) {

        JSONObject files = new JSONObject();

        if (file.exists() && file.isFile()) {
            try {
                String code = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
                files.put(file.getName(), code);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return files;

    }

    private void searchFolderFilesRecursive(File folder, List<String> files) {

        if (folder.exists() && folder.isDirectory()) {
            for (File f : folder.listFiles()) {
                if (f.isFile()) {
                    files.add(f.getAbsolutePath());
                } else if (f.isDirectory()) {
                    this.searchFolderFilesRecursive(f, files);
                }

            }
        }

    }

    private JSONObject getFolderFiles(File folder) {

        JSONObject files = new JSONObject();

        if (folder.exists() && folder.isDirectory()) {

            for (File f : folder.listFiles()) {
                if (!f.getName().endsWith(".dll")) {
                    try {
                        String code = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
                        files.put(f.getName(), code);

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

        }

        return files;

    }

    private void createNewReplayTransitionsFile(String folderPath) {

        ImportTransitionsFileDialog dlg = new ImportTransitionsFileDialog(folderPath);

        dlg.pack();
        //dlg.setSize(500,500);
        dlg.setLocationRelativeTo(null);
        dlg.setModal(true);
        dlg.setVisible(true);

        if (dlg.retval) {
            loadScriptsFolder();
            openLastTreeNode();
        } else {
            if (dlg.retvalErr != null) {
                JOptionPane.showMessageDialog(null, dlg.retvalErr);
            }
        }

    }

    private void createSubFolder(String path) {
        String subFolderName = (String) JOptionPane.showInputDialog(
                null,
                "Type new sub folder name",
                "Folder Name",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                "");

        if (subFolderName == null || subFolderName.trim().length() == 0) {
            return;
        }

        subFolderName = subFolderName.trim();

        File f = new File(path);
        File subFolder = new File(f, subFolderName);
        try {
            FileUtils.forceMkdir(subFolder);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Error occurred while creating a folder\r\n" + e.getMessage());
            return;
        }

        loadScriptsFolder();
        openLastTreeNode();
    }

    private void createNewScript(String path) {
        String scriptName = (String) JOptionPane.showInputDialog(
                null,
                "Type new scripts name",
                "Script Name",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                "");

        if (scriptName == null || scriptName.trim().length() == 0) {
            return;
        }

        scriptName = scriptName.trim();
        createNewScriptFile(path, scriptName);

        File f = new File(path);
        saveSelectedTreeNodePosition(f.getPath(), scriptName);
        loadScriptsFolder();
        openLastTreeNode();
    }

    private void createNewScriptFile(String dirPath, String fileName) {

        File f = new File(dirPath + "/" + fileName);
        try {
            if (f.createNewFile()) {

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createNewDesignerDocument(String path) {
        String docName = (String) JOptionPane.showInputDialog(
                null,
                "Type new designer document name",
                "Designer Document Name",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                "");

        if (docName == null || docName.trim().length() == 0) {
            return;
        }

        docName = docName.trim();
        if (!docName.endsWith(".smdesign")) {
            docName = docName + ".smdesign";
        }

        File f = new File(path + "/" + docName);
        try {
            DesignerDocument.writeEmptyFile(f);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        File parentDir = new File(path);
        saveSelectedTreeNodePosition(parentDir.getPath(), docName);
        loadScriptsFolder();
        openLastTreeNode();
    }

    private void createNewUIDocument(String path) {
        String docName = (String) JOptionPane.showInputDialog(
                null,
                "Type new UI document name",
                "UI Document Name",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                "");

        if (docName == null || docName.trim().length() == 0) {
            return;
        }

        docName = docName.trim();
        if (!docName.endsWith(".smui")) {
            docName = docName + ".smui";
        }

        File f = new File(path + "/" + docName);
        try {
            UIDocument.writeEmptyFile(f);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        File parentDir = new File(path);
        saveSelectedTreeNodePosition(parentDir.getPath(), docName);
        loadScriptsFolder();
        openLastTreeNode();
    }

    private void createNewShaderDocument(String path) {
        String docName = (String) JOptionPane.showInputDialog(
                null,
                "Type new shader document name",
                "Shader Document Name",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                "");

        if (docName == null || docName.trim().length() == 0) {
            return;
        }

        ShaderTemplatePreset preset = (ShaderTemplatePreset) JOptionPane.showInputDialog(
                null,
                "Choose a starter template",
                "Starter Template",
                JOptionPane.PLAIN_MESSAGE,
                null,
                ShaderTemplatePreset.values(),
                ShaderTemplatePreset.TEXTURE_TINT
        );
        if (preset == null) {
            preset = ShaderTemplatePreset.TEXTURE_TINT;
        }

        docName = docName.trim();
        if (!docName.endsWith(".smshader")) {
            docName = docName + ".smshader";
        }

        File f = new File(path + "/" + docName);
        try {
            ShaderDocument.writeEmptyFile(f, preset);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        File parentDir = new File(path);
        saveSelectedTreeNodePosition(parentDir.getPath(), docName);
        loadScriptsFolder();
        openLastTreeNode();
    }

    private void createNewEnvironmentShaderDocument(String path) {
        String docName = (String) JOptionPane.showInputDialog(
                null,
                "Type new environment shader document name",
                "Environment Shader Document Name",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                "");

        if (docName == null || docName.trim().length() == 0) {
            return;
        }

        EnvironmentShaderTemplatePreset preset = (EnvironmentShaderTemplatePreset) JOptionPane.showInputDialog(
                null,
                "Choose a starter template",
                "Starter Template",
                JOptionPane.PLAIN_MESSAGE,
                null,
                EnvironmentShaderTemplatePreset.values(),
                EnvironmentShaderTemplatePreset.FOG
        );
        if (preset == null) {
            preset = EnvironmentShaderTemplatePreset.FOG;
        }

        docName = docName.trim();
        if (!docName.endsWith(".smenvshader")) {
            docName = docName + ".smenvshader";
        }

        File f = new File(path + "/" + docName);
        try {
            EnvironmentShaderDocument.writeEmptyFile(f, preset);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        File parentDir = new File(path);
        saveSelectedTreeNodePosition(parentDir.getPath(), docName);
        loadScriptsFolder();
        openLastTreeNode();
    }

    private void openUIDesignerDocument(File f) {
        // If this UI designer file is already open, just switch to its tab
        if (editorTabPanel.isFileOpen(f.getAbsolutePath())) {
            editorTabPanel.openUIDesignerFile(f.getAbsolutePath(), null);
            return;
        }

        // Determine project path for resource loading
        String projectPath = null;
        SceneMaxProject activeProject = Util.getActiveProject();
        if (activeProject != null) {
            projectPath = activeProject.path;
        }

        UIDesignerPanel uiPanel = new UIDesignerPanel(projectPath, f);

        // Notify the tab when the document becomes dirty
        uiPanel.setOnDirtyCallback(() -> {
            editorTabPanel.markActiveTabDirty();
        });
        uiPanel.setOnSavedCallback(() -> {
            editorTabPanel.markTabClean(f.getAbsolutePath());
        });

        // Open UI designer tab
        editorTabPanel.openUIDesignerFile(f.getAbsolutePath(), uiPanel);

        lastSelectedFilePath = f.getAbsolutePath();
        lastSelectedNodeIsFile = true;
        btnRunScript.setEnabled(false);
    }

    private void openShaderDesignerDocument(File f) {
        if (editorTabPanel.isFileOpen(f.getAbsolutePath())) {
            editorTabPanel.openShaderDesignerFile(f.getAbsolutePath(), null);
            return;
        }

        String projectPath = null;
        SceneMaxProject activeProject = Util.getActiveProject();
        if (activeProject != null) {
            projectPath = activeProject.path;
        }

        ShaderDesignerPanel shaderPanel = new ShaderDesignerPanel(projectPath, f);
        shaderPanel.setOnDirtyCallback(() -> editorTabPanel.markActiveTabDirty());

        editorTabPanel.openShaderDesignerFile(f.getAbsolutePath(), shaderPanel);

        lastSelectedFilePath = f.getAbsolutePath();
        lastSelectedNodeIsFile = true;
        btnRunScript.setEnabled(false);

        saveSelectedTreeNodePosition(f.getParentFile().getPath(), f.getName());
    }

    private void openEffekseerDesignerDocument(File f) {
        if (editorTabPanel.isFileOpen(f.getAbsolutePath())) {
            editorTabPanel.openEffekseerDesignerFile(f.getAbsolutePath(), null);
            return;
        }

        String projectPath = null;
        SceneMaxProject activeProject = Util.getActiveProject();
        if (activeProject != null) {
            projectPath = activeProject.path;
        }

        EffekseerEffectDesignerPanel effectPanel = new EffekseerEffectDesignerPanel(projectPath, f);
        effectPanel.setOnDirtyCallback(() -> editorTabPanel.markActiveTabDirty());
        effectPanel.setOnSavedCallback(() -> {
            loadScriptsFolder();
            refreshAssetsMenu();
        });
        editorTabPanel.openEffekseerDesignerFile(f.getAbsolutePath(), effectPanel);

        lastSelectedFilePath = f.getAbsolutePath();
        lastSelectedNodeIsFile = true;
        btnRunScript.setEnabled(false);

        saveSelectedTreeNodePosition(f.getParentFile().getPath(), f.getName());
    }

    private void openEnvironmentShaderDesignerDocument(File f) {
        if (editorTabPanel.isFileOpen(f.getAbsolutePath())) {
            editorTabPanel.openEnvironmentShaderDesignerFile(f.getAbsolutePath(), null);
            return;
        }

        String projectPath = null;
        SceneMaxProject activeProject = Util.getActiveProject();
        if (activeProject != null) {
            projectPath = activeProject.path;
        }

        EnvironmentShaderDesignerPanel environmentPanel = new EnvironmentShaderDesignerPanel(projectPath, f);
        environmentPanel.setOnDirtyCallback(() -> editorTabPanel.markActiveTabDirty());

        editorTabPanel.openEnvironmentShaderDesignerFile(f.getAbsolutePath(), environmentPanel);

        lastSelectedFilePath = f.getAbsolutePath();
        lastSelectedNodeIsFile = true;
        btnRunScript.setEnabled(false);

        saveSelectedTreeNodePosition(f.getParentFile().getPath(), f.getName());
    }

    private void openDesignerDocument(File f) {
        // If this designer file is already open, just switch to its tab
        if (editorTabPanel.isFileOpen(f.getAbsolutePath())) {
            editorTabPanel.openDesignerFile(f.getAbsolutePath(), null);
            return;
        }

        // Determine project path for resource loading
        String projectPath = null;
        SceneMaxProject activeProject = Util.getActiveProject();
        if (activeProject != null) {
            projectPath = activeProject.path;
        }

        // Create a new DesignerPanel.  All designer panels share a single
        // JME3 canvas and DesignerApp — switching between designer tabs
        // triggers a document switch (similar to switchState for game levels).
        DesignerPanel newPanel = new DesignerPanel(projectPath, f);

        // Refresh the scripts tree when a .code file is first created
        newPanel.setScriptsTreeRefreshCallback(this::loadScriptsFolder);

        // Refresh an open .code editor tab whenever the designer updates it on disk
        newPanel.setCodeFileUpdatedCallback(codeFilePath -> {
            File codeFile = new File(codeFilePath);
            if (codeFile.isFile()) {
                try {
                    String content = FileUtils.readFileToString(codeFile, StandardCharsets.UTF_8);
                    editorTabPanel.refreshTabContent(codeFilePath, content);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });

        // Open designer tab with the embedded panel
        editorTabPanel.openDesignerFile(f.getAbsolutePath(), newPanel);

        lastSelectedFilePath = f.getAbsolutePath();
        lastSelectedNodeIsFile = true;
        btnRunScript.setEnabled(false);

        saveSelectedTreeNodePosition(f.getParentFile().getPath(), f.getName());
    }

    private void openImport3DModelDocument() {
        // Determine project path for resource loading
        String projectPath = null;
        SceneMaxProject activeProject = Util.getActiveProject();
        if (activeProject != null) {
            projectPath = activeProject.path;
        }

        String resourcesFolder = Util.getResourcesFolder();
        String scriptsFolder = Util.getScriptsFolder();

        // Create a temporary designer file path so the DesignerApp can resolve
        // the project resources correctly (it navigates 2 levels up from the
        // working folder to find the project root).
        File tmpDir = new File(scriptsFolder + "/tmp");
        if (!tmpDir.exists()) {
            tmpDir.mkdirs();
        }
        File tmpDesignerFile = new File(tmpDir, "_import_preview.smdesign");

        // Use a synthetic tab path so EditorTabPanel identifies this tab uniquely
        String tabPath = tmpDesignerFile.getAbsolutePath();

        // If the import tab is already open, switch to it
        if (editorTabPanel.isFileOpen(tabPath)) {
            editorTabPanel.openDesignerFile(tabPath, null);
            return;
        }

        String initialSketchfabToken = getParam("sketchfab_api_token", AppConfig.get("sketchfab_api_token"));
        Import3DModelPanel importPanel = new Import3DModelPanel(
                projectPath,
                tmpDesignerFile,
                resourcesFolder,
                initialSketchfabToken,
                token -> AppDB.getInstance().setParam("sketchfab_api_token", token)
        );

        importPanel.setOnCloseCallback(imported -> {
            // Close the import tab
            editorTabPanel.closeTabByPath(tabPath);
            if (imported) {
                refreshAssetsMenu();
            }
        });

        editorTabPanel.openDesignerFile(tabPath, importPanel);
        btnRunScript.setEnabled(false);
    }

    private void openImportAnimationDocument() {
        SceneMaxProject activeProject = Util.getActiveProject();
        if (activeProject == null) {
            JOptionPane.showMessageDialog(this,
                    "No active project. Please create or select a project first.",
                    "Animation Import",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        File tmpDir = new File(Util.getScriptsFolder() + "/tmp");
        if (!tmpDir.exists()) {
            tmpDir.mkdirs();
        }
        File tabFile = new File(tmpDir, "_import_animation.smanimimport");
        String tabPath = tabFile.getAbsolutePath();

        if (editorTabPanel.isFileOpen(tabPath)) {
            editorTabPanel.openAnimationImportFile(tabPath, null);
            return;
        }

        ImportAnimationPanel panel = new ImportAnimationPanel(new File(activeProject.getResourcesPath()));
        panel.setOnCloseCallback(imported -> {
            editorTabPanel.closeTabByPath(tabPath);
            if (imported) {
                refreshAssetsMenu();
            }
        });

        editorTabPanel.openAnimationImportFile(tabPath, panel);
        btnRunScript.setEnabled(false);
    }

    private void importEffekseerEffect() {
        SceneMaxProject activeProject = Util.getActiveProject();
        if (activeProject == null) {
            JOptionPane.showMessageDialog(this,
                    "No active project. Please create or select a project first.",
                    "Effekseer Import",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import Effekseer Effect");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Effekseer effects (*.efkefc, *.efkproj, *.efk)",
                "efkefc", "efkproj", "efk"));
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File sourceFile = chooser.getSelectedFile();
        File targetScriptsFolder = getSelectedScriptsFolder();
        File resourcesFolder = new File(activeProject.getResourcesPath());

        try {
            EffekseerImportResult importResult = EffekseerImporter.importEffect(
                    sourceFile,
                    resourcesFolder,
                    targetScriptsFolder
            );
            loadScriptsFolder();
            openEffekseerDesignerDocument(importResult.getDocumentFile());
            refreshAssetsMenu();

            StringBuilder msg = new StringBuilder();
            msg.append("Imported ").append(sourceFile.getName()).append("\n");
            msg.append("Document: ").append(importResult.getDocumentFile().getAbsolutePath()).append("\n");
            msg.append("Assets: ").append(importResult.getAssetFolder().getAbsolutePath());
            JOptionPane.showMessageDialog(this, msg.toString(), "Effekseer Import", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    ex.getMessage(),
                    "Effekseer Import",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private File getSelectedScriptsFolder() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree1.getLastSelectedPathComponent();
        if (selectedNode != null && selectedNode.getUserObject() instanceof ScriptPathNode) {
            File selectedFile = new File(((ScriptPathNode) selectedNode.getUserObject()).getPath());
            if (selectedFile.isDirectory()) {
                return selectedFile;
            }
            File parent = selectedFile.getParentFile();
            if (parent != null) {
                return parent;
            }
        }
        return new File(Util.getScriptsFolder());
    }

    private JMenuItem addScriptsTreePopupMenuItem(String label, String action, JPopupMenu popup,
                                                  ActionListener popupActionListener, boolean addToFolderNode, boolean addToFileNode, File file) {
        if (!addToFolderNode && file.isDirectory()) {
            return null;// directory nodes doesn't need that menu item
        }

        if (!addToFileNode && file.isFile()) {
            return null;
        }

        JMenuItem item = new JMenuItem(label);
        item.setActionCommand(action);
        item.addActionListener(popupActionListener);
        popup.add(item);

        return item;
    }

    public static void main(String[] args) {

        // Fix HiDPI gap in JME3/LWJGL2 canvas: Java 9+ declares the process
        // as DPI-aware, so the GL framebuffer is at physical pixel resolution
        // but Canvas.getWidth()/getHeight() report logical pixels.  This causes
        // glViewport to cover only (1/scale) of the framebuffer, leaving gaps
        // on the right and bottom edges.  Disabling Java's DPI scaling makes
        // logical == physical so the viewport fills the entire framebuffer.
        System.setProperty("sun.java2d.uiScale", "1");

        // Compensate by letting FlatLaf scale the Swing UI independently.
        // Toolkit.getScreenResolution() returns the OS DPI (e.g. 120 for 125%)
        // via the native GetDeviceCaps API, unaffected by sun.java2d.uiScale.
        try {
            int dpi = Toolkit.getDefaultToolkit().getScreenResolution();
            float scale = dpi / 96f;
            if (scale > 1f) {
                System.setProperty("flatlaf.uiScale", String.valueOf(scale));
            }
        } catch (Exception ignored) {
        }

        // Embed the window title bar into the menu bar row to save vertical space.
        // FlatLaf renders its own title bar with icon, title, and window buttons
        // on the same line as the menu, replacing the native OS title bar.
        System.setProperty("flatlaf.useWindowDecorations", "true");
        System.setProperty("flatlaf.menuBarEmbedded", "true");

        setupLookAndFeel();

        EventQueue.invokeLater(new Runnable() {
            public void run() {

                // add the JME canvas\
                final MainApp app = new MainApp();

                String title = Util.getAppTitle(app.licenseExists);

                app.setTitle(title);
                app.setContentPane(app.mainPanel);
                app.setJMenuBar(app.addMenuBar());
                app.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                app.pack();
                app.setExtendedState(JFrame.MAXIMIZED_BOTH);
                app.setVisible(true);

                URL imgURL = MainApp.class.getResource("/images/scenemax_icon.png");
                app.setIconImage(new ImageIcon(imgURL).getImage());

            }
        });


    }

    private static void setupLookAndFeel() {
        try {
            FlatDarkLaf.class.getMethod("setup").invoke(null);
            return;
        } catch (Exception ignored) {
        }

        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (UnsupportedLookAndFeelException e) {
            throw new RuntimeException("Failed to initialize FlatDarkLaf", e);
        }
    }

    private void initMacroFolder() {
        File f = new File("macro");
        f.mkdirs();
    }

    private boolean initScriptsFolder(String folderName) {

        SceneMaxProject p = Util.getActiveProject();

        if (p == null) {
            Util.createProject("default project", null);
            migrateToDefaultProject();
        }

        loadScriptsFolder();
        setFileTreeListener();
        openLastTreeNode();

        return true;
    }

    private void migrateToDefaultProject() {
        try {
            FileUtils.copyDirectoryToDirectory(new File("scripts"), new File("./projects/default_project/"));

            migrateResources("Models", "models-ext.json", "models", "path", true);
            migrateResources("sprites", "sprites-ext.json", "sprites", "path", false);
            migrateResources("audio", "audio-ext.json", "sounds", "path", false);
            migrateResources("skyboxes", "skyboxes-ext.json", "skyboxes", "back", true);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void migrateResources(String srcFolderName, String extFileName, String collectionName, String filePathKey, boolean copyResFolder) {

        try {

            File ext = new File(Util.getDefaultResourcesFolder() + "/" + srcFolderName + "/" + extFileName);
            if (ext.exists()) {
                FileUtils.copyFile(ext, new File(Util.getResourcesFolder() + "/" + srcFolderName + "/" + extFileName));
                File destFolder = new File(Util.getResourcesFolder() + "/" + srcFolderName);
                JSONObject obj = getResourcesFolderIndex(ext.getAbsolutePath());
                JSONArray arr = obj.getJSONArray(collectionName);
                for (int i = 0; i < arr.length(); ++i) {
                    JSONObject item = arr.getJSONObject(i);
                    String path = Util.getDefaultResourcesFolder() + "/" + item.getString(filePathKey);

                    if (copyResFolder) {
                        FileUtils.copyDirectoryToDirectory(new File(path).getParentFile(), destFolder);
                    } else {
                        FileUtils.copyFile(new File(path), new File(Util.getResourcesFolder() + "/" + srcFolderName + "/" + new File(path).getName()));
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private JSONObject getResourcesFolderIndex(String path) {
        File f = new File(path);
        if (!f.exists()) return null;

        String s = Util.readFile(f);
        if (s == null || s.length() == 0) return null;
        return new JSONObject(s);
    }

    private void setFileTreeListener() {

        tree1.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = tree1.getPathForLocation(e.getX(), e.getY());
                    if (path == null) return;
                    DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) path.getLastPathComponent();
                    if (selectedNode == null || selectedNode.getUserObject() == null) return;

                    String filePath = ((ScriptPathNode) selectedNode.getUserObject()).getPath();
                    File f = new File(filePath);

                    if (f.isFile()) {
                        if (f.getName().endsWith(".smdesign")) {
                            openDesignerDocument(f);
                        } else if (f.getName().endsWith(".smeffectdesign")) {
                            openEffekseerDesignerDocument(f);
                        } else if (f.getName().endsWith(".smui")) {
                            openUIDesignerDocument(f);
                        } else if (f.getName().endsWith(".smshader")) {
                            openShaderDesignerDocument(f);
                        } else if (f.getName().endsWith(".smenvshader")) {
                            openEnvironmentShaderDesignerDocument(f);
                        } else {
                            openFileInTab(f);
                        }
                    }
                }
            }
        });

    }

    private void openFileInTab(File f) {
        String content = "";
        try {
            content = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }

        textArea.setEnabled(true);
        textAreaRTL.setEnabled(true);
        editorTabPanel.openFile(f.getAbsolutePath(), content);
        saveSelectedTreeNodePosition(f.getParentFile().getPath(), f.getName());

        lastSelectedFilePath = f.getAbsolutePath();
        isDocumentChanged = false;
        lastSelectedNodeIsFile = true;
        btnRunScript.setEnabled(!f.getName().endsWith(".cs"));
    }

    protected void loadFileToTextEditor(File f) {
        if (f.getName().endsWith(".smdesign")) {
            openDesignerDocument(f);
        } else if (f.getName().endsWith(".smeffectdesign")) {
            openEffekseerDesignerDocument(f);
        } else if (f.getName().endsWith(".smui")) {
            openUIDesignerDocument(f);
        } else if (f.getName().endsWith(".smshader")) {
            openShaderDesignerDocument(f);
        } else if (f.getName().endsWith(".smenvshader")) {
            openEnvironmentShaderDesignerDocument(f);
        } else {
            openFileInTab(f);
        }
    }

    private void loadScriptsFolder() {

        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree1.getModel().getRoot();
        root.removeAllChildren();
        listFilesForFolder(new File(Util.getScriptsFolder()), root, false);
        tree1.setRootVisible(false);
        tree1.setShowsRootHandles(true);

        if (originalTreeFontSize == null) {
            final Font currentFont = tree1.getFont();
            originalTreeFontSize = currentFont.getSize() + 6;
            final Font bigFont = new Font(currentFont.getName(), currentFont.getStyle(), originalTreeFontSize);
            tree1.setFont(bigFont);
        }

        tree1.updateUI();
    }

    private void saveLastSelectedFile() {
        if (editorTabPanel != null && editorTabPanel.getActiveTab() != null && editorTabPanel.getActiveTab().dirty) {
            editorTabPanel.saveActiveTab();
        }
    }

    private TreeNode findFolderNode(TreeNode parent, String nodeName) {

        for (int i = 0; i < parent.getChildCount(); ++i) {
            TreeNode n = parent.getChildAt(i);
            ScriptPathNode sp = (ScriptPathNode) ((DefaultMutableTreeNode) n).getUserObject();
            String path = sp.getPath();
            File f = new File(path);
            if (f.isDirectory() && path.equals(nodeName)) {
                return n;
            }

//            if (n.toString().equals(nodeName)) {
//                return n;
//            }
            if (n.getChildCount() > 0) {
                TreeNode child = findFolderNode(n, nodeName);
                if (child != null) {
                    return child;
                }
            }
        }

        return null;
    }


    private TreePath find(DefaultMutableTreeNode root, String s) {

        if (root == null) {
            return null;
        }

        Enumeration<TreeNode> e = root.depthFirstEnumeration();
        while (e.hasMoreElements()) {
            TreeNode node = e.nextElement();
            if (node.toString().equalsIgnoreCase(s)) {
                return Utils.getPath(node);
            }
        }
        return null;
    }

    private TreePath findDirectChild(DefaultMutableTreeNode parent, String name) {

        if (parent == null) {
            return null;
        }

        for (int i = 0; i < parent.getChildCount(); i++) {
            TreeNode child = parent.getChildAt(i);
            if (child.toString().equalsIgnoreCase(name)) {
                return Utils.getPath(child);
            }
        }
        return null;
    }

    private void openTreeNodeByFile(File path) {
        saveLastSelectedFileEnabled = false;
        String parentPath = path.getParentFile().getAbsolutePath();
        String nodeText = path.getName();
        TreeNode rootNode = (TreeNode) tree1.getModel().getRoot();
        TreeNode root = parentPath == null || parentPath.length() == 0 ? (TreeNode) tree1.getModel().getRoot() : findFolderNode(rootNode, parentPath);

        TreePath tp = findDirectChild((DefaultMutableTreeNode) root, nodeText);

        if (tp != null) {
            tree1.setSelectionPath(tp);
            tree1.scrollPathToVisible(tp);
        }

        saveLastSelectedFileEnabled = true;

    }

    public void openLastTreeNode() {

        saveLastSelectedFileEnabled = false;
        String parentPath = AppDB.getInstance().getParam("selected_tree_node_parent");
        String nodeText = AppDB.getInstance().getParam("selected_tree_node");
        TreeNode rootNode = (TreeNode) tree1.getModel().getRoot();
        TreeNode root = parentPath == null || parentPath.length() == 0 ? (TreeNode) tree1.getModel().getRoot() : findFolderNode(rootNode, parentPath);

        TreePath tp = findDirectChild((DefaultMutableTreeNode) root, nodeText);

        if (tp != null) {
            tree1.setSelectionPath(tp);
            tree1.scrollPathToVisible(tp);

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) tp.getLastPathComponent();
            String filePath = ((ScriptPathNode) node.getUserObject()).getPath();
            File f = new File(filePath);
            if (f.isFile()) {
                if (f.getName().endsWith(".smdesign")) {
                    openDesignerDocument(f);
                } else if (f.getName().endsWith(".smeffectdesign")) {
                    openEffekseerDesignerDocument(f);
                } else if (f.getName().endsWith(".smui")) {
                    openUIDesignerDocument(f);
                } else if (f.getName().endsWith(".smshader")) {
                    openShaderDesignerDocument(f);
                } else if (f.getName().endsWith(".smenvshader")) {
                    openEnvironmentShaderDesignerDocument(f);
                } else {
                    openFileInTab(f);
                }
            }
        }

        saveLastSelectedFileEnabled = true;
    }

    public void saveSelectedTreeNodePosition(String parentNodeText, String nodeText) {

        AppDB.getInstance().setParam("selected_tree_node_parent", parentNodeText);
        AppDB.getInstance().setParam("selected_tree_node", nodeText);
    }

    public static void listFilesForFolder(final File folder, DefaultMutableTreeNode treeNode, boolean showFoldersOnly) {
        File[] files = folder.listFiles();
        if (files == null) return;

        // Collect .code files that have a matching .smdesign or .smui sibling so we can
        // skip them during the normal pass and nest them under the parent node instead.
        Set<String> pairedCodeFiles = new HashSet<>();
        for (final File fileEntry : files) {
            if (fileEntry.isFile() && fileEntry.getName().toLowerCase().endsWith(".smdesign")) {
                String baseName = fileEntry.getName().substring(0, fileEntry.getName().length() - ".smdesign".length());
                File codeFile = new File(folder, baseName + ".code");
                if (codeFile.exists() && codeFile.isFile()) {
                    pairedCodeFiles.add(codeFile.getAbsolutePath());
                }
                File initFile = new File(folder, baseName + "_init.code");
                if (initFile.exists() && initFile.isFile()) {
                    pairedCodeFiles.add(initFile.getAbsolutePath());
                }
                File endFile = new File(folder, baseName + "_end.code");
                if (endFile.exists() && endFile.isFile()) {
                    pairedCodeFiles.add(endFile.getAbsolutePath());
                }
            }
            // Collect _ui.code files paired with .smui files
            if (fileEntry.isFile() && fileEntry.getName().toLowerCase().endsWith(".smui")) {
                String baseName = fileEntry.getName().substring(0, fileEntry.getName().length() - ".smui".length());
                File uiCodeFile = new File(folder, baseName + "_ui.code");
                if (uiCodeFile.exists() && uiCodeFile.isFile()) {
                    pairedCodeFiles.add(uiCodeFile.getAbsolutePath());
                }
            }
        }

        for (final File fileEntry : files) {

            if (treeNode.getRoot() == treeNode && fileEntry.isFile()) {
                continue;
            }

            String name = fileEntry.getName().toLowerCase();
            if (name.endsWith(".dll")) continue;

            // Skip .code files that are paired with a .smdesign file;
            // they will be added as children of the .smdesign node below.
            if (pairedCodeFiles.contains(fileEntry.getAbsolutePath())) continue;

            ScriptPathNode sp = new ScriptPathNode(fileEntry.getAbsolutePath(), fileEntry.getName());

            sp.isFolder = fileEntry.isDirectory() &&
                    !fileEntry.getParentFile().getName().equals("scripts");
            DefaultMutableTreeNode child = new DefaultMutableTreeNode(sp);
            if (!showFoldersOnly || (showFoldersOnly && fileEntry.isDirectory())) {
                treeNode.add(child);
            }

            // Nest the matching .code, _init.code and _end.code files as children of the .smdesign node
            if (fileEntry.isFile() && name.endsWith(".smdesign")) {
                String baseName = fileEntry.getName().substring(0, fileEntry.getName().length() - ".smdesign".length());
                File codeFile = new File(folder, baseName + ".code");
                if (codeFile.exists() && codeFile.isFile()) {
                    ScriptPathNode codeSp = new ScriptPathNode(codeFile.getAbsolutePath(), codeFile.getName());
                    DefaultMutableTreeNode codeChild = new DefaultMutableTreeNode(codeSp);
                    child.add(codeChild);
                }
                File initFile = new File(folder, baseName + "_init.code");
                if (initFile.exists() && initFile.isFile()) {
                    ScriptPathNode initSp = new ScriptPathNode(initFile.getAbsolutePath(), initFile.getName());
                    DefaultMutableTreeNode initChild = new DefaultMutableTreeNode(initSp);
                    child.add(initChild);
                }
                File endFile = new File(folder, baseName + "_end.code");
                if (endFile.exists() && endFile.isFile()) {
                    ScriptPathNode endSp = new ScriptPathNode(endFile.getAbsolutePath(), endFile.getName());
                    DefaultMutableTreeNode endChild = new DefaultMutableTreeNode(endSp);
                    child.add(endChild);
                }
            }

            // Nest the matching _ui.code file as a child of the .smui node
            if (fileEntry.isFile() && name.endsWith(".smui")) {
                String baseName = fileEntry.getName().substring(0, fileEntry.getName().length() - ".smui".length());
                File uiCodeFile = new File(folder, baseName + "_ui.code");
                if (uiCodeFile.exists() && uiCodeFile.isFile()) {
                    ScriptPathNode uiCodeSp = new ScriptPathNode(uiCodeFile.getAbsolutePath(), uiCodeFile.getName());
                    DefaultMutableTreeNode uiCodeChild = new DefaultMutableTreeNode(uiCodeSp);
                    child.add(uiCodeChild);
                }
            }

            if (fileEntry.isDirectory()) {
                listFilesForFolder(fileEntry, child, showFoldersOnly);
            }
        }
    }

    public JButton addToolbarButton(String img, String actionCommand, String toolTipText, ActionListener listener) {

        String imgLocation = "/images/" + img + ".png";
        URL imageURL = MainApp.class.getResource(imgLocation);

        //Create and initialize the button.
        JButton button = new JButton();
        button.setActionCommand(actionCommand);
        button.setToolTipText(toolTipText);
        button.addActionListener(listener);
        button.setIcon(new ImageIcon(imageURL));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);

        mainToolbar.add(button);
        return button;
    }

    private JButton addToolbarButton(String img, String actionCommand, String toolTipText) {
        return this.addToolbarButton(img, actionCommand, toolTipText, this);
    }

    private JButton addToolbarButton(Icon icon, String actionCommand, String toolTipText) {
        JButton button = new JButton();
        button.setActionCommand(actionCommand);
        button.setToolTipText(toolTipText);
        button.addActionListener(this);
        button.setIcon(icon);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        mainToolbar.add(button);
        return button;
    }

    // ── Programmatic toolbar icons (white on transparent, 24×24) ──────────

    private static final int TB_ICON_SIZE = 24;
    private static final Color TB_COLOR = new Color(220, 220, 220);
    private static final Color TB_HIGHLIGHT = new Color(255, 255, 255, 100);

    private static Icon createToolbarIcon(String key) {
        BufferedImage img = new BufferedImage(TB_ICON_SIZE, TB_ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(TB_COLOR);
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        switch (key) {
            case "run":          drawRunIcon(g);        break;
            case "create_folder":drawFolderIcon(g);     break;
            case "save":         drawSaveIcon(g);       break;
            case "connect":      drawConnectIcon(g);    break;
            case "deploy":       drawDeployIcon(g);     break;
            case "run_record":   drawRecordIcon(g);     break;
        }
        g.dispose();
        return new ImageIcon(img);
    }

    /** Play triangle */
    private static void drawRunIcon(Graphics2D g) {
        GeneralPath p = new GeneralPath();
        p.moveTo(7, 4);
        p.lineTo(20, 12);
        p.lineTo(7, 20);
        p.closePath();
        g.fill(p);
    }

    /** Folder with a + sign */
    private static void drawFolderIcon(Graphics2D g) {
        // folder body
        GeneralPath folder = new GeneralPath();
        folder.moveTo(2, 7);
        folder.lineTo(2, 20);
        folder.lineTo(22, 20);
        folder.lineTo(22, 9);
        folder.lineTo(13, 9);
        folder.lineTo(11, 7);
        folder.closePath();
        g.draw(folder);
        // folder tab
        g.draw(new Line2D.Float(2, 7, 11, 7));
        // plus sign
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(12, 12, 12, 18));
        g.draw(new Line2D.Float(9, 15, 15, 15));
    }

    /** Floppy disk */
    private static void drawSaveIcon(Graphics2D g) {
        // outer body
        GeneralPath body = new GeneralPath();
        body.moveTo(3, 3);
        body.lineTo(3, 21);
        body.lineTo(21, 21);
        body.lineTo(21, 7);
        body.lineTo(17, 3);
        body.closePath();
        g.draw(body);
        // top slot (disk shutter)
        g.draw(new RoundRectangle2D.Float(8, 3, 8, 7, 1, 1));
        // metal slide
        g.draw(new Line2D.Float(14, 3, 14, 9));
        // label area
        g.draw(new RoundRectangle2D.Float(6, 14, 12, 6, 1, 1));
    }

    /** Connected people / classroom */
    private static void drawConnectIcon(Graphics2D g) {
        // left person - head
        g.draw(new Ellipse2D.Float(3, 4, 5, 5));
        // left person - body
        GeneralPath lb = new GeneralPath();
        lb.moveTo(2, 19);
        lb.curveTo(2, 13, 3, 11, 5.5f, 11);
        lb.curveTo(8, 11, 9, 13, 9, 19);
        g.draw(lb);

        // right person - head
        g.draw(new Ellipse2D.Float(15, 4, 5, 5));
        // right person - body
        GeneralPath rb = new GeneralPath();
        rb.moveTo(14, 19);
        rb.curveTo(14, 13, 15, 11, 17.5f, 11);
        rb.curveTo(20, 11, 21, 13, 21, 19);
        g.draw(rb);

        // connection line
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(TB_HIGHLIGHT);
        g.draw(new Line2D.Float(9, 14, 14, 14));
        // connection dots
        g.setColor(TB_COLOR);
        g.fillOval(9, 13, 2, 2);
        g.fillOval(13, 13, 2, 2);
    }

    /** Package / build box */
    private static void drawDeployIcon(Graphics2D g) {
        int cx = 12, top = 3, bot = 21, mid = 12;
        int left = 2, right = 22;
        // top face (diamond)
        GeneralPath topFace = new GeneralPath();
        topFace.moveTo(cx, top);
        topFace.lineTo(right, top + 4);
        topFace.lineTo(cx, mid);
        topFace.lineTo(left, top + 4);
        topFace.closePath();
        g.draw(topFace);
        // left face
        g.draw(new Line2D.Float(left, top + 4, left, bot - 4));
        g.draw(new Line2D.Float(left, bot - 4, cx, bot));
        // right face
        g.draw(new Line2D.Float(right, top + 4, right, bot - 4));
        g.draw(new Line2D.Float(right, bot - 4, cx, bot));
        // center vertical
        g.draw(new Line2D.Float(cx, mid, cx, bot));
        // ribbon on top face
        g.setColor(TB_HIGHLIGHT);
        g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(cx, top, cx, mid));
        g.draw(new Line2D.Float(left, top + 4, right, top + 4));
    }

    /** Record: circle + small play triangle */
    private static void drawRecordIcon(Graphics2D g) {
        // record circle
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Ellipse2D.Float(2, 2, 20, 20));
        // inner filled circle (record dot)
        g.fill(new Ellipse2D.Float(7, 7, 10, 10));
    }

    @Override
    public void init() {

    }

    @Override
    public void showScriptEditor() {

    }

    @Override
    public void onEndCode(final List<String> errors) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    onEndCode(errors);
                }
            });
            return;
        }

        if (errors != null && errors.size() > 0) {
            String msg = "";
            for (String s : errors) {
                msg += s + "\r\n";
            }

            JOptionPane.showMessageDialog(null, msg, "Program Errors", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    @Override
    public void onStartCode() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    onStartCode();
                }
            });
            return;
        }

//        if (isRecordMode) {
//            screenRec = new ScreenRecorder();
//            CanvasRect rc = theApp.getRect();
//            screenRec.start(rc);
//        }

    }


    public void message(int msgType) {

        if (msgType == SceneMax3dEvents.EVENT_DESTROY) {
            btnRunScript.setEnabled(true);
            btnRecordScene.setEnabled(true);

        }
    }

    @Override
    public void message(int msgType, Object content) {
        if (msgType == MSG_UPDATE_CODE_NEW_MODELS) {
            String code = textArea.getText();
            code = content.toString() + "\r\n" + code;
            textArea.setText(code);
        }
    }


    @Override
    public void actionPerformed(ActionEvent actionEvent) {

        if (actionEvent.getActionCommand().equalsIgnoreCase("run_record")) {

            if (!prepareAndRunLauncher()) return;

        } else if (actionEvent.getActionCommand().equals("run")) {

            //if (!prepareAndRunScript()) return;//use it for debugging the engine
            if (!prepareAndRunLauncher()) {
                return;
            }

        } else if (actionEvent.getActionCommand().equals("deploy")) {

            if (lastSelectedFilePath == null || lastSelectedFilePath.length() == 0) {
                JOptionPane.showMessageDialog(null, "Select your game's folder or any of its files", "Package Error", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            File f = new File(lastSelectedFilePath);
            if (f.isFile()) {
                f = f.getParentFile();
            }

            if (!new File(f, "main").exists()) {
                JOptionPane.showMessageDialog(null, "Main script file not exists", "Package Error", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            String prg = this.getMainFileContent(f.getAbsolutePath());// textAreaSP.isVisible() ? textArea.getText() : textAreaRTL.getText();
            ApplyMacroResults mr = applyMacro(prg);

            if (mr.finalPrg.trim().length() == 0) {
                JOptionPane.showMessageDialog(null, "Cannot package an empty program", "Package Error", JOptionPane.INFORMATION_MESSAGE);
            } else {

                packageBrogramButton.setEnabled(false);
                packageProgramDialog = new PackageProgramDialog();
                packageProgramDialog.pack();
                packageProgramDialog.setLocationRelativeTo(null);
                packageProgramDialog.run(lastSelectedFilePath, mr.finalPrg, new Runnable() {
                    @Override
                    public void run() {
                        packageBrogramButton.setEnabled(true);
                    }
                });

                packageProgramDialog.setVisible(true);
            }

        } else if (actionEvent.getActionCommand().equals("save")) {
            if (editorTabPanel != null && editorTabPanel.getActiveTab() != null) {
                editorTabPanel.saveActiveTab();
                isDocumentChanged = false;
            }

        } else if (actionEvent.getActionCommand().equals("create_folder")) {
            createNewScriptsFolder();
        } else if (actionEvent.getActionCommand().equals("connect")) {

            groupConnectDialog = new GroupManagementDialog();
            groupConnectDialog.pack();
            groupConnectDialog.setLocationRelativeTo(null);

            groupConnectDialog.refreshDialogState();
            groupConnectDialog.setVisible(true);
        }
    }

    protected boolean prepareAndRunLauncher() {

        btnRunScript.setEnabled(false);
        btnRecordScene.setEnabled(false);

        File scriptFile = prepareCurrScriptFile();
        if (scriptFile == null) {
            btnRunScript.setEnabled(true);
            return false;
        }

        String prg = textAreaSP.isVisible() ? textArea.getText() : textAreaRTL.getText();
        //ApplyMacroResults mr = applyMacro(prg);
        new RunLauncherTask(scriptFile.getAbsolutePath(), prg, new Runnable() {
            @Override
            public void run() {
                btnRunScript.setEnabled(true);
                //btnRecordScene.setEnabled(true);
            }
        }).execute();

        return true;
    }

    private File prepareCurrScriptFile() {
        if (editorTabPanel != null && editorTabPanel.getActiveTab() != null) {
            EditorTabPanel.TabData active = editorTabPanel.getActiveTab();
            if (active.dirty) {
                editorTabPanel.saveActiveTab();
            }
            return new File(active.filePath);
        }
        return null;
    }

    private void deleteCurrentScriptFile() {

        String path = "script.txt";
        File f = new File(path);

        if (f.exists() && f.isFile()) {
            f.delete();
        }
    }

    private void createNewProjectScriptsFolder() {
        //List<File> folders = Util.getScriptFolders();
        String folderName = (String) JOptionPane.showInputDialog(
                null,
                "Type new project name",
                "Project Name",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                "");

        if (folderName == null || folderName.trim().length() == 0) {
            return;
        }

        folderName = folderName.trim();

        Util.createProject(folderName, folderName);
        refreshScriptsFolder();
        refreshAppTitle();
        refreshAssetsMenu();

        SceneMaxProject p = Util.getActiveProject();
        addProjectMenuItem(projectsSubMenu, p);

    }

    public EditorTabPanel getEditorTabPanel() {
        return editorTabPanel;
    }

    public void refreshAppTitle() {
        String title = Util.getAppTitle(this.licenseExists);
        this.setTitle(title);
    }

    public void refreshScriptsFolder() {
        loadScriptsFolder();
        openLastTreeNode();
    }

    private void createNewScriptsFolder() {

        String folderName = (String) JOptionPane.showInputDialog(
                null,
                "Type new folder name",
                "Folder Name",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                "");

        this.createNewScriptsFolder(folderName);
    }

    public File createNewScriptsFolder(String folderName) {

        if (folderName == null || folderName.trim().length() == 0) {
            return null;
        }

        folderName = folderName.trim();
        File f = new File(Util.getScriptsFolder() + "/" + folderName);
        if (f.exists()) {
            return f;
        }

        if (!f.mkdir()) {
            JOptionPane.showMessageDialog(null, "Folder creation failed. Try again with a different folder name", "Create Folder Error", JOptionPane.INFORMATION_MESSAGE);
            return null;
        }

        createNewScriptFile(f.getAbsolutePath(), "main");
        createNewScriptFile(f.getAbsolutePath(), "tests");
        saveSelectedTreeNodePosition(f.getPath(), "main");
        loadScriptsFolder();
        openLastTreeNode();

        return f;
    }

    private ApplyMacroResults applyMacro(String prg) {

        MacroFilter mf = new MacroFilter();
        mf.loadMacroRulesFromMacroFolder(new File("macro"));
        return mf.apply(prg);

    }

    public void saveFile(String path, String prg) {

        prg = prg.replaceAll("\r", "");
        try {
            Files.write(Paths.get(path), prg.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    private void saveLastEditedScript(String path, String prg) {

        prg = prg.replaceAll("\r", "");

        File f = new File(path);

        if (f.isFile()) {

            BufferedWriter writer = null;
            try {

                FileUtils.writeStringToFile(f, prg, StandardCharsets.UTF_8);

//                writer = new BufferedWriter(new OutputStreamWriter(
//                        new FileOutputStream(f), "UTF-8"));
//
//                //writer = new BufferedWriter(new FileWriter(path));
//                writer.write(prg);
//                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private void saveLastEditedScript(String path) {

        String script = textAreaSP.isVisible() ? textArea.getText() : textAreaRTL.getText();
        saveLastEditedScript(path, script);
    }

    @Override
    public void onServerResponse(final String event, final JSONObject data, final boolean ownSocket, final boolean targettingMe) {

        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    onServerResponse(event, data, ownSocket, targettingMe);
                }
            });
            return;
        }


        if (event.equals("user-joined")) {

            if (ownSocket) {
                btnAskQuestion.setEnabled(true);

                RoomMember m = WebCommunication.getInstance().getThisMember();
                sendToFolderFlag = (m != null && m.isModerator());

            }

            showQuestionsCountInfo(data);
            showMembersCountInfo(data);

        } else if (event.equals("user-left")) {

            showQuestionsCountInfo(data);
            showMembersCountInfo(data);

            if (!ownSocket) {
                return;
            }

            sendToFolderFlag = false;

            btnAskQuestion.setEnabled(false);
            btnAskQuestion.setText("Not Connected");
            //AppDB.getInstance().setParam("last_nick_name", "");

        } else if (event.equals("question")) {
//            if (ownSocket) {
//                btnCancelQuestion.setEnabled(true);
//            }
            showQuestionsCountInfo(data);
        } else if (event.equals("answer-accepted")) {
//            if (ownSocket) {
//                btnCancelQuestion.setEnabled(false);
//            }
            showQuestionsCountInfo(data);
        } else if (event.equals("answered") && targettingMe) {
            onSomeoneAnsweredMyQuestion(data);
        } else if (event.equals("pull-request") && targettingMe) {
            onPullRequest(data);
        } else if (event.equals("push-request") && targettingMe) {
            onPushRequest(data);
        } else if (event.equals("push-request")) { // general push request
            String request = data.getString("request");
            if (request.equals("room-command")) {
                JSONObject cmd = data.getJSONObject("command");
                if (ownSocket) {
                    String command = cmd.getString("command");
                    JOptionPane.showMessageDialog(null, "Room command: " + command + " was sent successfully", "Room Command Confirmation", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }

                onGotRoomCommand(data);

            }
        }

    }

    private void onGotRoomCommand(JSONObject data) {

        JSONObject cmd = data.getJSONObject("command");
        String command = cmd.getString("command");
        if (command.equals("install")) {
            String url = cmd.getString("url");
            runCommandLineUrl(url);
        }
    }

    private void onPushRequest(JSONObject data) {

        String request = data.getString("request");

        if (request.equals("override_file")) {

            String folderName = data.getString("folderName");
            JSONObject files = data.getJSONObject("files");

            File folder = new File(Util.getScriptsFolder() + "/" + folderName);
            if (!folder.exists()) {
                folder.mkdir();
            }

            saveFilesInFolder(files, folder);

        } else if (request.equals("view_folder")) {
            String folderName = data.getString("folderName");
            JSONObject files = data.getJSONObject("files");

            File folder = new File(Util.getScriptsFolder() + "/" + folderName);
            if (folder.exists()) {
                try {
                    FileUtils.cleanDirectory(folder);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                folder.mkdir();
            }

            saveFilesInFolder(files, folder);
        }

    }

    private void saveFilesInFolder(JSONObject files, File folder) {

        String folderPath = folder.getAbsolutePath();
        for (String key : files.keySet()) {
            File f = new File(folderPath + "/" + key);
            try {
                FileUtils.write(f, files.getString(key), StandardCharsets.UTF_8);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        saveSelectedTreeNodePosition(folder.getPath(), "main");

        loadScriptsFolder();
        openLastTreeNode();

        JOptionPane.showMessageDialog(null, "Got files in: " + folder.getName(), "Files Arrived From User", JOptionPane.INFORMATION_MESSAGE);
    }

    private void onPullRequest(JSONObject data) {

        String request = data.getString("request");

        if (request.equals("view_user_folders")) {
            File folder = new File(Util.getScriptsFolder());
            JSONObject folders = new JSONObject();
            for (final File f : folder.listFiles()) {
                if (f.isDirectory()) {
                    File[] scripts = f.listFiles();
                    if (scripts != null) {
                        folders.put(f.getName(), scripts.length);
                    }
                }
            }

            String room = AppDB.getInstance().getParam("joined_room");
            String userId = data.getString("sourceId"); // send data to the source that asked this pull request
            String userName = AppDB.getInstance().getParam("last_nick_name");
            WebCommunication.getInstance().sendScriptFolders(room, userId, userName, folders);

        } else if (request.equals("view_folder")) {
            String folderName = data.getString("folderName");
            File folder = new File(Util.getScriptsFolder() + "/" + folderName);
            if (folder.exists() && folder.isDirectory()) {
                JSONObject files = new JSONObject();
                for (File f : folder.listFiles()) {
                    if (!f.getName().endsWith(".dll")) {
                        try {
                            String code = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
                            files.put(f.getName(), code);

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }

                String room = AppDB.getInstance().getParam("joined_room");
                String userId = data.getString("sourceId"); // send data to the source that asked this pull request
                String userName = AppDB.getInstance().getParam("last_nick_name");
                WebCommunication.getInstance().sendFolderContent(room, userId, folderName + "@" + userName, files);
            }
        }

    }

    private void onSomeoneAnsweredMyQuestion(JSONObject data) {
        RoomMember m = findRoomMember(data);
        if (m != null && m.isModerator()) {
            String room = AppDB.getInstance().getParam("joined_room");
            WebCommunication.getInstance().acceptAnswer(room);
            Answer an = createAnswerFromJson(data);
            applyAnswer(an);
            _answers.clear();
            showAnswersCountInfo();
        } else {
            _answers.add(data);
            showAnswersCountInfo();
        }

    }

    private Answer createAnswerFromJson(JSONObject item) {

        Answer a = new Answer();
        a.text = item.getString("answer");
        a.answerCode = item.getString("answerCode");
        a.userId = item.getString("userId");
        a.sourceId = item.getString("sourceId");
        a.folderName = item.getString("folder");
        a.fileName = item.getString("file");
//        a.userName = userMapping.get(a.sourceId);
//        if(a.userName==null || a.userName.length()==0) {
//            a.userName="Unknown";
//        }

        return a;
    }

    private RoomMember findRoomMember(JSONObject data) {
        String id = data.getString("sourceId");
        return WebCommunication.getInstance().getRoomMemberById(id);

    }

    private void showAnswersCountInfo() {
        if (_answers != null && _answers.size() > 0) {
            btnAnswers.setVisible(true);
            btnAnswers.setText(_answers.size() + " Replies");
        } else {
            btnAnswers.setVisible(false);
        }
    }

    private void showMembersCountInfo(final JSONObject data) {

        try {
            _members = data.getJSONArray("members");
            RoomMember m = WebCommunication.getInstance().getThisMember();

            if (m != null) {
                String text = m.toString();
                btnAskQuestion.setText(text + " | " + _members.length() + " members");
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void showQuestionsCountInfo(final JSONObject room) {

        roomQuestions = new ArrayList<>();
        boolean thisUserHasActiveQuestion = false;

        try {
            JSONArray members = room.getJSONArray("members");

            for (int i = 0; i < members.length(); ++i) {
                JSONObject member = members.getJSONObject(i);

                if (member.has("question") && !member.isNull("question")) {
                    JSONObject question = member.getJSONObject("question");
                    if (question != null) {
                        question.put("asker", member.get("name"));
                        roomQuestions.add(question);


                        if (member.has("station") && member.getString("station").equals(Util.getStationId())) {
                            thisUserHasActiveQuestion = true;
                        }
                    }
                }
            }

            btnCancelQuestion.setEnabled(thisUserHasActiveQuestion);

            if (roomQuestions.size() > 0) {
                btnActiveQuestions.setVisible(true);
                btnActiveQuestions.setText(roomQuestions.size() + " messages");
            } else {
                btnActiveQuestions.setVisible(false);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }


    }

    public void keyTyped(KeyEvent e) {

    }

    public void keyPressed(KeyEvent e) {

        if ((e.getSource() == textArea || e.getSource() == textAreaRTL) && e.getKeyCode() == KeyEvent.VK_F && e.isControlDown()) {

            String selectedText = textAreaSP.isVisible() ? textArea.getSelectedText() : textAreaRTL.getSelectedText();
            String activeFilePath = editorTabPanel != null ? editorTabPanel.getActiveFilePath() : null;
            if (activeFilePath == null || !new File(activeFilePath).isFile()) {
                return;
            }
            List<String> files = new ArrayList<>();
            searchFolderFilesRecursive(new File(Util.getScriptsFolder()), files);

            FindDialog dlg = new FindDialog(this, activeFilePath, files, selectedText);

            dlg.setSize(550, 200);
            dlg.setLocationRelativeTo(null);
            dlg.setAlwaysOnTop(true);
            dlg.setVisible(true);

        } else if (e.getKeyCode() == KeyEvent.VK_S && e.isControlDown()) {

            if (editorTabPanel != null && editorTabPanel.getActiveTab() != null) {
                editorTabPanel.saveActiveTab();
                autoComplete.invalidateCache();

                final JTextArea txt = e.getSource() == textArea ? textArea : textAreaRTL;
                final Color c = txt.getBackground();
                txt.setBackground(Color.CYAN);
                Timer timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        txt.setBackground(c);
                    }
                }, 200);

                isDocumentChanged = false;
            }

        } else if (e.getKeyCode() == KeyEvent.VK_EQUALS && e.isControlDown()) {

            JTextArea txt = e.getSource() == textArea ? textArea : textAreaRTL;
            Font font = txt.getFont();
            float size = font.getSize() + 1.0f;
            if (size < 72) {
                txt.setFont(font.deriveFont(size));
                AppDB.getInstance().setParam("DEFAULT_FONT_SIZE", size + "");
            }
        } else if (e.getKeyCode() == KeyEvent.VK_MINUS && e.isControlDown()) {

            JTextArea txt = e.getSource() == textArea ? textArea : textAreaRTL;
            Font font = txt.getFont();
            float size = font.getSize() - 1.0f;
            if (size >= DEFAULT_FONT_SIZE - EXTRA_FONT_SIZE) {
                txt.setFont(font.deriveFont(size));
                AppDB.getInstance().setParam("DEFAULT_FONT_SIZE", size + "");
            }
        } else if (e.getKeyCode() == KeyEvent.VK_0 && e.isControlDown()) {
            JTextArea txt = e.getSource() == textArea ? textArea : textAreaRTL;
            Font font = txt.getFont();
            float size = font.getSize();
            if (size != DEFAULT_FONT_SIZE) {
                size = DEFAULT_FONT_SIZE;
                txt.setFont(font.deriveFont(size));
                AppDB.getInstance().setParam("DEFAULT_FONT_SIZE", DEFAULT_FONT_SIZE + "");
            }
        } else if (e.getKeyCode() == KeyEvent.VK_R && e.isControlDown()) {

            if (e.getSource() == textArea) {

                textAreaRTL.setText(textArea.getText());
                textAreaSP.setVisible(false);
                textAreaRtlSP.setVisible(true);
                textAreaRTL.grabFocus();
                textAreaRTL.setCaretPosition(0);
                textAreaRTL.updateUI();
            } else {
                textArea.setText(textAreaRTL.getText());
                textAreaRtlSP.setVisible(false);
                textAreaSP.setVisible(true);
                textArea.grabFocus();
                textArea.setCaretPosition(0);
                textArea.updateUI();
            }

            layeredPane.updateUI();

            if (editorTabPanel != null && editorTabPanel.getActiveTab() != null) {
                editorTabPanel.getActiveTab().isRtlMode = textAreaRtlSP.isVisible();
            }
        }
    }

    public void keyReleased(KeyEvent e) {

    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {

    }

    public void addCodeSnippet(String code) {
        textArea.append(code);
    }

    public void showTextInFile(String filePath, int index, int length) {
        File f = new File(filePath);
        openTreeNodeByFile(f);
        openFileInTab(f);
        JTextArea activeEditor = textAreaSP.isVisible() ? textArea : textAreaRTL;
        activeEditor.setCaretPosition(index);
        activeEditor.setSelectionStart(index);
        activeEditor.setSelectionEnd(index + length);
    }

    public void showTextInFile(String folder, String file, int index, int length) {

        saveLastSelectedFileEnabled = false;
        String parentPath = folder;
        String nodeText = file;
        TreeNode rootNode = (TreeNode) tree1.getModel().getRoot();
        TreeNode root = parentPath == null || parentPath.length() == 0 ? (TreeNode) tree1.getModel().getRoot() : findFolderNode(rootNode, parentPath);

        TreePath tp = find((DefaultMutableTreeNode) root, nodeText);

        if (tp != null) {
            tree1.setSelectionPath(tp);
            tree1.scrollPathToVisible(tp);

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) tp.getLastPathComponent();
            String path = ((ScriptPathNode) node.getUserObject()).getPath();
            openFileInTab(new File(path));

            textArea.setCaretPosition(index);
            textArea.setSelectionStart(index);
            textArea.setSelectionEnd(index + length);
        }

        saveLastSelectedFileEnabled = true;

    }

    public String getActiveFilePath() {
        return editorTabPanel != null ? editorTabPanel.getActiveFilePath() : null;
    }

    public String getActiveEditorText() {
        if (editorTabPanel == null || editorTabPanel.getActiveTab() == null) return null;
        return editorTabPanel.getCurrentEditorText();
    }

    public int getActiveSelectionStart() {
        JTextArea activeEditor = textAreaSP.isVisible() ? textArea : textAreaRTL;
        return activeEditor.getSelectionStart();
    }

    public int getActiveSelectionEnd() {
        JTextArea activeEditor = textAreaSP.isVisible() ? textArea : textAreaRTL;
        return activeEditor.getSelectionEnd();
    }

    public void replaceActiveSelection(String replacement) {
        JTextArea activeEditor = textAreaSP.isVisible() ? textArea : textAreaRTL;
        activeEditor.replaceSelection(replacement);
    }

    public void setActiveEditorText(String text) {
        if (editorTabPanel == null) return;
        textArea.setText(text);
        textAreaRTL.setText(text);
    }

    ////////////////////////////////////////////////////////////////////////////////////

    private void disableSslVerification() {
        try {
            // Create a trust manager that does not validate certificate chains
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        public void checkClientTrusted(X509Certificate[] certs,
                                                       String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] certs,
                                                       String authType) {
                        }
                    }};

            // Install the all-trusting trust manager
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // Create all-trusting host name verifier
            HostnameVerifier allHostsValid = new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };

            // Install the all-trusting host verifier
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
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
        mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout(0, 0));
        bottomPanel = new JPanel();
        bottomPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        topPanel = new JPanel();
        topPanel.setLayout(new GridLayoutManager(1, 4, new Insets(0, 0, 0, 0), -1, -1));
        mainPanel.add(topPanel, BorderLayout.NORTH);
        txtNavUrl = new JTextField();
        txtNavUrl.setText("");
        topPanel.add(txtNavUrl, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final Spacer spacer1 = new Spacer();
        topPanel.add(spacer1, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("command:");
        topPanel.add(label1, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        mainSplitPane = new JSplitPane();
        mainSplitPane.setDividerLocation(532);
        mainSplitPane.setOneTouchExpandable(true);
        mainSplitPane.setOrientation(1);
        mainSplitPane.setResizeWeight(0.0);
        mainPanel.add(mainSplitPane, BorderLayout.CENTER);
        editorHostPanel = new JPanel();
        editorHostPanel.setLayout(new BorderLayout(0, 0));
        mainSplitPane.setRightComponent(editorHostPanel);
        leftPanel = new JPanel();
        leftPanel.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
        leftPanel.setBackground(new Color(-855310));
        leftPanel.setEnabled(true);
        mainSplitPane.setLeftComponent(leftPanel);
        classroomPane = new JPanel();
        classroomPane.setLayout(new BorderLayout(0, 0));
        leftPanel.add(classroomPane, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        btnAskQuestion = new JButton();
        btnAskQuestion.setEnabled(false);
        btnAskQuestion.setHorizontalTextPosition(0);
        btnAskQuestion.setIcon(new ImageIcon(getClass().getResource("/images/raise_hand_72x72.png")));
        btnAskQuestion.setText("Not Connected");
        btnAskQuestion.setVerticalAlignment(0);
        btnAskQuestion.setVerticalTextPosition(3);
        classroomPane.add(btnAskQuestion, BorderLayout.CENTER);
        btnCancelQuestion = new JButton();
        btnCancelQuestion.setEnabled(false);
        btnCancelQuestion.setText("Cancel Message");
        classroomPane.add(btnCancelQuestion, BorderLayout.SOUTH);
        btnActiveQuestions = new JButton();
        btnActiveQuestions.setEnabled(true);
        btnActiveQuestions.setIcon(new ImageIcon(getClass().getResource("/images/question1_32x32.png")));
        btnActiveQuestions.setText("");
        classroomPane.add(btnActiveQuestions, BorderLayout.NORTH);
        btnAnswers = new JButton();
        btnAnswers.setIcon(new ImageIcon(getClass().getResource("/images/answer1_32x32.png")));
        btnAnswers.setText("");
        leftPanel.add(btnAnswers, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JScrollPane scrollPane1 = new JScrollPane();
        leftPanel.add(scrollPane1, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        tree1 = new JTree();
        scrollPane1.setViewportView(tree1);
        mainToolbar = new JToolBar();
        mainToolbar.setOrientation(1);
        leftPanel.add(mainToolbar, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(-1, 20), null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return mainPanel;
    }

}
