package com.scenemax.desktop;

import com.scenemaxeng.compiler.*;
import org.apache.commons.io.FileUtils;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

/**
 * Autocomplete provider for the SceneMax scripting language.
 * Uses the real SceneMaxLanguageParser to parse the project's main file
 * and populate completions from the resulting ProgramDef.
 */
public class SceneMaxAutoComplete {

    private static final int TYPE_KEYWORD = 0;
    private static final int TYPE_VARIABLE = 1;
    private static final int TYPE_OBJECT_3D = 2;
    private static final int TYPE_OBJECT_2D = 3;
    private static final int TYPE_FUNCTION = 4;
    private static final int TYPE_EXPR_POINTER = 5;
    private static final int TYPE_BUILTIN_FUNC = 6;
    private static final int TYPE_COLOR = 7;
    private static final int TYPE_INPUT_KEY = 8;
    private static final int TYPE_EFFECT = 9;
    private static final int TYPE_GROUP = 10;
    private static final int TYPE_MODEL = 11;
    private static final int TYPE_SPRITE = 12;
    private static final int TYPE_PARAMETER = 13;
    private static final int TYPE_JAVA_METHOD = 14;
    private static final int TYPE_JAVA_FIELD = 15;
    private static final int TYPE_JAVA_CLASS = 16;
    private static final int TYPE_JAVA_SNIPPET = 17;

    private final RSyntaxTextArea textArea;
    private JWindow popupWindow;
    private JList<CompletionItem> completionList;
    private DefaultListModel<CompletionItem> listModel;
    private boolean isShowing = false;
    private boolean isInserting = false;
    private long lastUserEditIntentTime = 0;

    // Cached ProgramDef from last parse
    private ProgramDef cachedProgram = null;
    private long lastParseTime = 0;
    private static final long PARSE_COOLDOWN_MS = 2000; // re-parse at most every 2 seconds
    private ProgramDef cachedLocalProgram = null;
    private long lastLocalParseTime = 0;
    private boolean localParseDirty = true;
    private static final long LOCAL_PARSE_COOLDOWN_MS = 750; // avoid parsing incomplete text on every keystroke

    private static final int AUTO_TRIGGER_LENGTH = 2;
    private static final long USER_EDIT_INTENT_WINDOW_MS = 500;

    // Callback to get active file path from EditorTabPanel
    private ActiveFileProvider activeFileProvider;

    @FunctionalInterface
    public interface ActiveFileProvider {
        String getActiveFilePath();
    }

    // SceneMax language keywords
    private static final String[] KEYWORDS = {
        "if", "else", "do", "end", "then", "when", "for", "foreach",
        "while", "return", "stop", "switch",
        "Var", "Shared", "Function",
        "is a", "is an",
        "Sphere", "Box", "Cylinder", "Quad", "Hollow",
        "Sprite", "Model",
        "Dynamic", "Static", "Collider", "Vehicle",
        "Belongs", "Group",
        "Move", "Rotate", "Scale", "Animate", "Play", "Hide", "Show", "Delete",
        "Turn", "Roll", "Look", "Pos", "Stop",
        "Push", "Pop", "Clear", "Print",
        "Accelerate", "Steer", "Brake", "Turbo", "Reset",
        "Attach", "Detach",
        "Record", "Replay",
        "Run", "Call", "Async",
        "Having", "And", "In", "At", "From", "To", "With",
        "Speed", "Of", "Loop", "Once", "Every",
        "Mass", "Velocity", "Angular", "Restitution", "Friction",
        "Material", "Radius", "Height", "Size",
        "Gravity",
        "Shadow", "Mode", "Hidden", "Collision", "Shape",
        "Calibrate", "Joints", "Data",
        "Camera", "Chase", "Follow", "Trailing", "Dungeon", "Default", "Fighting",
        "third_person", "first_person", "racing", "platformer", "rts",
        "Modifiers", "Apply",
        "hit_modifier", "fall_modifier", "shooting_modifier", "accelerating_modifier",
        "decelerating_modifier", "bump_modifier", "landing_modifier", "earthquake_modifier",
        "explosion_modifier", "near_miss_modifier",
        "Vertical", "Horizontal", "Rotation",
        "Max", "Min", "Distance", "Damping", "Type",
        "SkyBox", "Solar", "System", "Terrain", "Water",
        "Cloud", "Flattening", "Cloudiness", "Hour",
        "Depth", "Strength",
        "Audio", "Sound", "Volume",
        "Logger", "info", "debug", "error",
        "Lights", "Light", "Probe", "directional", "point", "spot", "sky", "ambient",
        "Direction", "Intensity", "Lumens", "Range", "Preset", "Exposure",
        "Low", "Medium", "High", "Warm", "Cool",
        "Screen", "Scene", "Pause", "Resume",
        "Full", "Window",
        "Effects",
        "Minimap",
        "Using", "Code", "Add",
        "Wait", "Seconds", "For",
        "Is", "Pressed", "Released",
        "Character", "RagDoll", "Kinematic", "Floating", "Rigid", "Body",
        "Engine", "Power", "Breaking", "Suspension",
        "Compression", "Stiffness", "Length",
        "Front", "Rear", "Input", "Reverse", "Horn",
        "Forward", "Backward", "Left", "Right", "Up", "Down",
        "Billboard", "Wireframe", "Info", "Outline",
        "Offset", "Duration", "Emissions", "Start", "End",
        "Draw", "Frames", "Frame",
        "Append", "Color", "Font",
        "Cast", "Receive",
        "Debug", "On", "Off",
        "Protected", "True", "False",
        "New", "Class", "Save",
        "After", "Collides",
        "Ray", "Check",
        "File", "Name", "Contains",
        "Each", "Where",
        "Http", "Get", "Post", "Put",
        "UI", "Load", "Message", "TextEffect", "Ease",
        "Java", "Attach",
        "Plugins",
        "Animation",
        "Rows", "Cols",
        "Times", "Inner",
        "Transitions", "Commands",
        "Ignore", "Jump",
        "Speedo", "Tacho",
        "Angle",
        "JSON",
        "Looking",
        "Not",
    };

    private static final String[] BUILTIN_FUNCTIONS = {
        "Distance", "Angle", "Jump", "abs", "rnd", "round",
    };

    private static final String[] COLORS = {
        "Red", "Green", "Blue", "White", "Black", "Brown", "Cyan",
        "Gray", "DarkGray", "LightGray", "Magenta", "Orange", "Pink", "Yellow",
        "Warm", "Cool",
    };

    private static final String[] EFFECTS = {
        "Flash", "Explosion", "Debris", "Spark", "Smoketrail",
        "Shockwave", "Fire", "Flame", "Destination", "Gradient",
        "Orbital", "TimeOrbit",
    };

    private static final String[] INPUT_KEYS = {
        "Key A", "Key B", "Key C", "Key D", "Key E", "Key F", "Key G",
        "Key H", "Key I", "Key J", "Key K", "Key L", "Key M", "Key N",
        "Key O", "Key P", "Key Q", "Key R", "Key S", "Key T", "Key U",
        "Key V", "Key W", "Key X", "Key Y", "Key Z",
        "Key Space", "Key Left", "Key Right", "Key Up", "Key Down", "Key Del",
        "Key 0", "Key 1", "Key 2", "Key 3", "Key 4", "Key 5",
        "Key 6", "Key 7", "Key 8", "Key 9",
        "Mouse Left", "Mouse Right",
    };

    private static final String[] JAVA_KEYWORDS = {
            "abstract", "assert", "boolean", "break", "case", "catch", "class", "continue",
            "default", "do", "double", "else", "enum", "extends", "final", "finally",
            "float", "for", "if", "implements", "import", "instanceof", "int", "interface",
            "new", "private", "protected", "public", "return", "static", "super", "switch",
            "this", "throw", "throws", "try", "void", "while", "true", "false", "null"
    };

    private static final String[] JAVA_CORE_COMPLETION_CLASS_NAMES = {
            "com.scenemaxeng.projector.SceneMaxApp",
            "com.scenemaxeng.projector.SceneMaxBaseAppState",
            "com.scenemaxeng.projector.SceneMaxScope",
            "com.scenemaxeng.projector.EntityInstBase",
            "com.scenemaxeng.projector.AppModel",
            "com.jme3.scene.Spatial",
            "com.jme3.scene.Node",
            "com.jme3.scene.Geometry",
            "com.jme3.math.Vector3f",
            "com.jme3.math.Quaternion",
            "com.jme3.material.Material",
            "com.jme3.scene.control.Control",
            "com.jme3.app.state.BaseAppState",
            "com.jme3.app.Application",
            "com.jme3.asset.AssetManager",
            "com.jme3.app.state.AppStateManager"
    };

    private static final Map<String, Class<?>> JAVA_TYPE_CACHE = new HashMap<>();
    private static ClassLoader javaReflectionClassLoader;

    public SceneMaxAutoComplete(RSyntaxTextArea textArea, ActiveFileProvider activeFileProvider) {
        this.textArea = textArea;
        this.activeFileProvider = activeFileProvider;
        setupPopup();
        setupListeners();
    }

    private void setupPopup() {
        listModel = new DefaultListModel<>();
        completionList = new JList<>(listModel);
        completionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        completionList.setCellRenderer(new CompletionCellRenderer());
        completionList.setFixedCellHeight(22);
        completionList.setVisibleRowCount(10);
        completionList.setFocusable(false);

        Font editorFont = textArea.getFont();
        completionList.setFont(editorFont.deriveFont(Font.PLAIN, editorFont.getSize() - 1));

        completionList.setBackground(new Color(43, 43, 43));
        completionList.setForeground(new Color(187, 187, 187));
        completionList.setSelectionBackground(new Color(75, 110, 175));
        completionList.setSelectionForeground(Color.WHITE);

        JScrollPane scrollPane = new JScrollPane(completionList);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(60, 63, 65)));
        scrollPane.setPreferredSize(new Dimension(380, 220));

        completionList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() >= 1) {
                    insertSelectedCompletion();
                }
            }
        });

        completionList.putClientProperty("_scrollPane", scrollPane);
    }

    private JWindow getPopupWindow() {
        if (popupWindow == null) {
            Window ancestor = SwingUtilities.getWindowAncestor(textArea);
            popupWindow = new JWindow(ancestor);
            popupWindow.setType(Window.Type.POPUP);
            popupWindow.setFocusableWindowState(false);
            popupWindow.setAlwaysOnTop(true);
            JScrollPane sp = (JScrollPane) completionList.getClientProperty("_scrollPane");
            popupWindow.getContentPane().add(sp);
            popupWindow.pack();
        }
        return popupWindow;
    }

    private void setupListeners() {
        textArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_SPACE) {
                    e.consume();
                    triggerParse();
                    showCompletions(true);
                    return;
                }

                if (isEditKey(e)) {
                    markUserEditIntent();
                }

                if (!isShowing) return;

                switch (e.getKeyCode()) {
                    case KeyEvent.VK_DOWN:
                        e.consume();
                        moveSelection(1);
                        break;
                    case KeyEvent.VK_UP:
                        e.consume();
                        moveSelection(-1);
                        break;
                    case KeyEvent.VK_ENTER:
                    case KeyEvent.VK_TAB:
                        e.consume();
                        insertSelectedCompletion();
                        break;
                    case KeyEvent.VK_ESCAPE:
                        e.consume();
                        hidePopup();
                        break;
                    case KeyEvent.VK_LEFT:
                    case KeyEvent.VK_RIGHT:
                    case KeyEvent.VK_HOME:
                    case KeyEvent.VK_END:
                        hidePopup();
                        break;
                }
            }

            @Override
            public void keyTyped(KeyEvent e) {
                if (isTextInputKey(e)) {
                    markUserEditIntent();
                }
            }
        });

        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                if (isInserting) return;
                SwingUtilities.invokeLater(() -> onTextChanged());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                if (isInserting) return;
                SwingUtilities.invokeLater(() -> onTextChanged());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {}
        });

        textArea.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (!textArea.isShowing()) {
                    hidePopup();
                }
            }
        });

        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (!isShowing) return;
            if (event instanceof WindowEvent) {
                int id = event.getID();
                if (id == WindowEvent.WINDOW_DEACTIVATED
                        || id == WindowEvent.WINDOW_ICONIFIED
                        || id == WindowEvent.WINDOW_LOST_FOCUS) {
                    Window src = ((WindowEvent) event).getWindow();
                    Window ancestor = SwingUtilities.getWindowAncestor(textArea);
                    if (src == ancestor) {
                        SwingUtilities.invokeLater(() -> hidePopup());
                    }
                }
            }
        }, AWTEvent.WINDOW_EVENT_MASK);
    }

    // ---- Parsing ----

    /**
     * Finds the script folder (the folder containing the "main" file) for the
     * current project. Walks up from the active file's directory until we find a
     * folder that has a "main" file.
     */
    private File findScriptFolder() {
        String activeFilePath = activeFileProvider != null ? activeFileProvider.getActiveFilePath() : null;
        if (activeFilePath == null || activeFilePath.isEmpty()) return null;

        File activeFile = new File(activeFilePath);
        File dir = activeFile.isDirectory() ? activeFile : activeFile.getParentFile();

        // Walk up directory tree to find a folder containing "main"
        while (dir != null) {
            File mainFile = new File(dir, "main");
            if (mainFile.exists() && mainFile.isFile()) {
                return dir;
            }
            dir = dir.getParentFile();
        }

        return null;
    }

    /**
     * Parses the project's main file using SceneMaxLanguageParser and caches the result.
     */
    private void triggerParse() {
        long now = System.currentTimeMillis();
        if (cachedProgram != null && (now - lastParseTime) < PARSE_COOLDOWN_MS) {
            return; // Use cached result
        }

        File scriptFolder = findScriptFolder();
        if (scriptFolder == null) {
            cachedProgram = null;
            return;
        }

        try {
            File mainFile = new File(scriptFolder, "main");
            if (!mainFile.exists()) {
                cachedProgram = null;
                return;
            }

            String code = FileUtils.readFileToString(mainFile, StandardCharsets.UTF_8);
            // Strip project metadata comment
            code = code.replaceAll("//\\$\\[project\\]=(.+?);", "");

            SceneMaxLanguageParser parser = new SceneMaxLanguageParser(null, scriptFolder.getAbsolutePath());
            // Autocomplete only needs symbols, so keep this parse lightweight and
            // avoid expensive project-wide cinematic rig resolution on the EDT.
            parser.enableChildParserMode(true);
            parser.setSuppressParserErrorLogging(true);
            cachedProgram = parser.parse(code);
            lastParseTime = now;
        } catch (Exception e) {
            // Parsing failed; keep old cache if available.
        }
    }

    /**
     * Does a lightweight parse of just the current editor text to capture
     * local scope symbols that may not be in the main file yet.
     */
    private ProgramDef parseCurrentEditor() {
        long now = System.currentTimeMillis();
        if (!localParseDirty) {
            return cachedLocalProgram;
        }
        if (cachedLocalProgram != null && (now - lastLocalParseTime) < LOCAL_PARSE_COOLDOWN_MS) {
            return cachedLocalProgram;
        }

        try {
            String code = textArea.getText();
            if (code == null || code.trim().isEmpty()) {
                cachedLocalProgram = null;
                localParseDirty = false;
                lastLocalParseTime = now;
                return null;
            }

            // Strip project metadata
            code = code.replaceAll("//\\$\\[project\\]=(.+?);", "");

            File scriptFolder = findScriptFolder();
            String codePath = scriptFolder != null ? scriptFolder.getAbsolutePath() : "";

            SceneMaxLanguageParser parser = new SceneMaxLanguageParser(null, codePath);
            parser.enableChildParserMode(true); // don't clear static collections
            parser.setSuppressParserErrorLogging(true);
            cachedLocalProgram = parser.parse(code);
            localParseDirty = false;
            lastLocalParseTime = now;
            return cachedLocalProgram;
        } catch (Exception e) {
            localParseDirty = false;
            lastLocalParseTime = now;
            return cachedLocalProgram;
        }
    }

    // ---- Completion logic ----

    private void onTextChanged() {
        localParseDirty = true;
        if (!wasCurrentChangeUserInitiated()) {
            hidePopup();
            return;
        }

        String prefix = getCurrentPrefix();
        if (isJavaFile() && isAfterMemberDot()) {
            showCompletions(true);
        } else if (prefix.length() >= AUTO_TRIGGER_LENGTH) {
            // Ensure we have a parse result (use cached if fresh enough)
            if (!isJavaFile() && cachedProgram == null) {
                triggerParse();
            }
            showCompletions(false);
        } else if (isShowing) {
            hidePopup();
        }
    }

    private void showCompletions(boolean forceTrigger) {
        String prefix = getCurrentPrefix();

        if (!forceTrigger && prefix.length() < AUTO_TRIGGER_LENGTH) {
            hidePopup();
            return;
        }

        List<CompletionItem> completions = getCompletions(prefix);
        if (completions.isEmpty()) {
            hidePopup();
            return;
        }

        listModel.clear();
        for (CompletionItem item : completions) {
            listModel.addElement(item);
        }
        completionList.setSelectedIndex(0);

        try {
            Rectangle caretRect = textArea.modelToView(textArea.getCaretPosition());
            if (caretRect == null) return;

            Point screenPos = textArea.getLocationOnScreen();
            int x = screenPos.x + caretRect.x;
            int y = screenPos.y + caretRect.y + caretRect.height + 2;

            JWindow win = getPopupWindow();
            win.pack();

            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            if (y + win.getHeight() > screenSize.height) {
                y = screenPos.y + caretRect.y - win.getHeight() - 2;
            }
            if (x + win.getWidth() > screenSize.width) {
                x = screenSize.width - win.getWidth();
            }

            win.setLocation(x, y);
            win.setVisible(true);
            isShowing = true;
        } catch (BadLocationException | IllegalComponentStateException e) {
            // ignore
        }
    }

    private void hidePopup() {
        if (popupWindow != null) {
            popupWindow.setVisible(false);
        }
        isShowing = false;
    }

    private void markUserEditIntent() {
        lastUserEditIntentTime = System.currentTimeMillis();
    }

    private boolean wasCurrentChangeUserInitiated() {
        return textArea.hasFocus()
                && (System.currentTimeMillis() - lastUserEditIntentTime) <= USER_EDIT_INTENT_WINDOW_MS;
    }

    private boolean isEditKey(KeyEvent e) {
        int keyCode = e.getKeyCode();
        if (keyCode == KeyEvent.VK_BACK_SPACE || keyCode == KeyEvent.VK_DELETE) {
            return true;
        }
        return isShortcutDown(e) && (keyCode == KeyEvent.VK_V || keyCode == KeyEvent.VK_X);
    }

    private boolean isTextInputKey(KeyEvent e) {
        return !e.isAltDown()
                && !isShortcutDown(e)
                && !Character.isISOControl(e.getKeyChar());
    }

    private boolean isShortcutDown(KeyEvent e) {
        return e.isControlDown() || e.isMetaDown();
    }

    private void moveSelection(int direction) {
        int idx = completionList.getSelectedIndex() + direction;
        if (idx >= 0 && idx < listModel.size()) {
            completionList.setSelectedIndex(idx);
            completionList.ensureIndexIsVisible(idx);
        }
    }

    private void insertSelectedCompletion() {
        CompletionItem selected = completionList.getSelectedValue();
        if (selected == null) return;

        hidePopup();

        String prefix = getCurrentPrefix();
        int caretPos = textArea.getCaretPosition();
        int prefixStart = caretPos - prefix.length();

        try {
            isInserting = true;
            textArea.getDocument().remove(prefixStart, prefix.length());
            textArea.getDocument().insertString(prefixStart, selected.insertText, null);
        } catch (BadLocationException e) {
            // ignore
        } finally {
            isInserting = false;
        }
    }

    private String getCurrentPrefix() {
        int caretPos = textArea.getCaretPosition();
        if (caretPos == 0) return "";

        try {
            String text = textArea.getText(0, caretPos);
            int start = caretPos - 1;

            while (start >= 0) {
                char c = text.charAt(start);
                if (Character.isLetterOrDigit(c) || c == '_' || c == '@') {
                    start--;
                } else {
                    break;
                }
            }
            start++;

            return text.substring(start, caretPos);
        } catch (BadLocationException e) {
            return "";
        }
    }

    /**
     * Invalidates the cached ProgramDef so the next autocomplete trigger re-parses.
     * Call this when the user saves a file or switches tabs.
     */
    public void invalidateCache() {
        cachedProgram = null;
        cachedLocalProgram = null;
        lastParseTime = 0;
        lastLocalParseTime = 0;
        localParseDirty = true;
        lastUserEditIntentTime = 0;
        hidePopup();
    }

    // ---- Building completion list from ProgramDef ----

    private List<CompletionItem> getCompletions(String prefix) {
        if (isJavaFile()) {
            return getJavaCompletions(prefix);
        }

        List<CompletionItem> results = new ArrayList<>();
        String lowerPrefix = prefix.toLowerCase();
        boolean isExprPointer = prefix.startsWith("@");
        String filterPrefix = isExprPointer ? lowerPrefix.substring(1) : lowerPrefix;

        Set<String> addedNames = new HashSet<>();

        // Collect symbols from the parsed program (main + all included code files)
        collectFromProgramDef(cachedProgram, results, addedNames, filterPrefix, isExprPointer);

        // Also parse current editor for local/in-progress symbols not yet saved
        ProgramDef localPrg = parseCurrentEditor();
        collectFromProgramDef(localPrg, results, addedNames, filterPrefix, isExprPointer);

        // For @ prefix, prepend @ to insertion text
        if (isExprPointer) {
            for (CompletionItem item : results) {
                if (!item.insertText.startsWith("@")) {
                    item.insertText = "@" + item.insertText;
                }
            }
        }

        // Add static completions (keywords, built-ins, colors, etc.) — only for non-@ context
        if (!isExprPointer) {
            for (String func : BUILTIN_FUNCTIONS) {
                if (func.toLowerCase().startsWith(filterPrefix) && addedNames.add(func.toLowerCase() + ":builtin")) {
                    results.add(new CompletionItem(func, func + "()", TYPE_BUILTIN_FUNC, "Built-in"));
                }
            }

            for (String keyword : KEYWORDS) {
                if (keyword.toLowerCase().startsWith(filterPrefix) && addedNames.add(keyword.toLowerCase() + ":kw")) {
                    results.add(new CompletionItem(keyword, keyword, TYPE_KEYWORD, "Keyword"));
                }
            }

            for (String color : COLORS) {
                if (color.toLowerCase().startsWith(filterPrefix) && addedNames.add(color.toLowerCase() + ":color")) {
                    results.add(new CompletionItem(color, color, TYPE_COLOR, "Color"));
                }
            }

            for (String effect : EFFECTS) {
                if (effect.toLowerCase().startsWith(filterPrefix) && addedNames.add(effect.toLowerCase() + ":effect")) {
                    results.add(new CompletionItem(effect, effect, TYPE_EFFECT, "Effect"));
                }
            }

            for (String key : INPUT_KEYS) {
                if (key.toLowerCase().startsWith(filterPrefix) && addedNames.add(key.toLowerCase() + ":key")) {
                    results.add(new CompletionItem(key, key, TYPE_INPUT_KEY, "Input Key"));
                }
            }
        }

        // Sort: symbols first, then keywords
        results.sort((a, b) -> {
            boolean aExact = a.displayText.toLowerCase().equals(lowerPrefix);
            boolean bExact = b.displayText.toLowerCase().equals(lowerPrefix);
            if (aExact != bExact) return aExact ? -1 : 1;

            boolean aSymbol = a.type != TYPE_KEYWORD;
            boolean bSymbol = b.type != TYPE_KEYWORD;
            if (aSymbol != bSymbol) return aSymbol ? -1 : 1;

            return a.displayText.compareToIgnoreCase(b.displayText);
        });

        return results;
    }

    private boolean isJavaFile() {
        String activeFilePath = activeFileProvider != null ? activeFileProvider.getActiveFilePath() : null;
        return activeFilePath != null && activeFilePath.toLowerCase(Locale.ROOT).endsWith(".java");
    }

    private boolean isAfterMemberDot() {
        int caretPos = textArea.getCaretPosition();
        String prefix = getCurrentPrefix();
        int dotIndex = caretPos - prefix.length() - 1;
        if (dotIndex < 0) {
            return false;
        }
        try {
            return ".".equals(textArea.getText(dotIndex, 1));
        } catch (BadLocationException e) {
            return false;
        }
    }

    private List<CompletionItem> getJavaCompletions(String prefix) {
        List<CompletionItem> results = new ArrayList<>();
        Set<String> added = new HashSet<>();
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);

        String receiver = getJavaMemberReceiver(prefix);
        if (receiver != null && !receiver.isBlank()) {
            Class<?> receiverType = inferJavaExpressionType(receiver, collectJavaVariables());
            if (receiverType != null) {
                collectJavaMembers(receiverType, lowerPrefix, results, added);
                results.sort(this::compareJavaCompletionItems);
                return results;
            }
        }

        collectJavaLocalVariables(lowerPrefix, results, added);
        collectJavaSnippets(lowerPrefix, results, added);
        collectJavaClasses(lowerPrefix, results, added);
        for (String keyword : JAVA_KEYWORDS) {
            if (keyword.startsWith(lowerPrefix) && added.add(keyword + ":kw")) {
                results.add(new CompletionItem(keyword, keyword, TYPE_KEYWORD, "Java Keyword"));
            }
        }

        results.sort(this::compareJavaCompletionItems);
        return results;
    }

    private String getJavaMemberReceiver(String prefix) {
        int caretPos = textArea.getCaretPosition();
        int dotIndex = caretPos - prefix.length() - 1;
        if (dotIndex < 0) {
            return null;
        }
        try {
            if (!".".equals(textArea.getText(dotIndex, 1))) {
                return null;
            }
            String beforeDot = textArea.getText(0, dotIndex);
            int start = beforeDot.length() - 1;
            int parenDepth = 0;
            while (start >= 0) {
                char c = beforeDot.charAt(start);
                if (c == ')') {
                    parenDepth++;
                } else if (c == '(') {
                    parenDepth--;
                }

                if (parenDepth == 0 && !(Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == ')')) {
                    break;
                }
                start--;
            }
            return beforeDot.substring(start + 1).trim();
        } catch (BadLocationException e) {
            return null;
        }
    }

    private Map<String, Class<?>> collectJavaVariables() {
        Map<String, Class<?>> vars = new HashMap<>();
        putJavaVar(vars, "app", "com.scenemaxeng.projector.SceneMaxApp");
        putJavaVar(vars, "sceneMaxApp", "com.scenemaxeng.projector.SceneMaxApp");
        putJavaVar(vars, "scope", "com.scenemaxeng.projector.SceneMaxScope");
        putJavaVar(vars, "sceneMaxScope", "com.scenemaxeng.projector.SceneMaxScope");
        putJavaVar(vars, "entity", "com.scenemaxeng.projector.EntityInstBase");
        putJavaVar(vars, "spatial", "com.jme3.scene.Spatial");
        putJavaVar(vars, "node", "com.jme3.scene.Node");
        putJavaVar(vars, "rootNode", "com.jme3.scene.Node");
        putJavaVar(vars, "assetManager", "com.jme3.asset.AssetManager");
        putJavaVar(vars, "stateManager", "com.jme3.app.state.AppStateManager");

        String code = textArea.getText();
        for (Class<?> type : getJavaCoreCompletionTypes()) {
            String simpleName = type.getSimpleName();
            java.util.regex.Pattern declaration = java.util.regex.Pattern.compile(
                    "\\b" + java.util.regex.Pattern.quote(simpleName) + "\\s+([A-Za-z_][A-Za-z0-9_]*)\\b");
            java.util.regex.Matcher matcher = declaration.matcher(code);
            while (matcher.find()) {
                vars.put(matcher.group(1), type);
            }
        }
        return vars;
    }

    private void putJavaVar(Map<String, Class<?>> vars, String name, String className) {
        Class<?> type = resolveJavaType(className);
        if (type != null) {
            vars.put(name, type);
        }
    }

    private void collectJavaLocalVariables(String lowerPrefix, List<CompletionItem> results, Set<String> added) {
        for (Map.Entry<String, Class<?>> entry : collectJavaVariables().entrySet()) {
            String name = entry.getKey();
            if (!name.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                continue;
            }
            if (added.add(name + ":java-var")) {
                results.add(new CompletionItem(
                        name + " : " + entry.getValue().getSimpleName(),
                        name,
                        TYPE_VARIABLE,
                        "Java Variable"));
            }
        }
    }

    private void collectJavaSnippets(String lowerPrefix, List<CompletionItem> results, Set<String> added) {
        addJavaSnippet(results, added, lowerPrefix,
                "getSceneMaxApp() : SceneMaxApp",
                "getSceneMaxApp()",
                "Extension Helper");
        addJavaSnippet(results, added, lowerPrefix,
                "getSceneMaxScope() : SceneMaxScope",
                "getSceneMaxScope()",
                "Extension Helper");
        addJavaSnippet(results, added, lowerPrefix,
                "onSceneMaxInitialize(SceneMaxApp app)",
                "@Override\nprotected void onSceneMaxInitialize(SceneMaxApp app) {\n    \n}",
                "Lifecycle");
        addJavaSnippet(results, added, lowerPrefix,
                "update(float tpf)",
                "@Override\npublic void update(float tpf) {\n    \n}",
                "Lifecycle");
        addJavaSnippet(results, added, lowerPrefix,
                "Spatial lookup by SceneMax name",
                "Spatial spatial = getEntitySpatial(\"\");",
                "SceneMax Native");
        addJavaSnippet(results, added, lowerPrefix,
                "Entity lookup by SceneMax name",
                "EntityInstBase entity = getEntity(\"\");",
                "SceneMax Native");
    }

    private void addJavaSnippet(List<CompletionItem> results, Set<String> added, String lowerPrefix,
                                String display, String insert, String category) {
        String searchable = display.toLowerCase(Locale.ROOT);
        int paren = searchable.indexOf('(');
        String nameOnly = paren >= 0 ? searchable.substring(0, paren) : searchable;
        if ((searchable.startsWith(lowerPrefix) || nameOnly.startsWith(lowerPrefix)) && added.add(display + ":snippet")) {
            results.add(new CompletionItem(display, insert, TYPE_JAVA_SNIPPET, category));
        }
    }

    private void collectJavaClasses(String lowerPrefix, List<CompletionItem> results, Set<String> added) {
        for (Class<?> type : getJavaCoreCompletionTypes()) {
            String simpleName = type.getSimpleName();
            if (simpleName.toLowerCase(Locale.ROOT).startsWith(lowerPrefix) && added.add(simpleName + ":class")) {
                results.add(new CompletionItem(
                        simpleName + "    " + type.getName(),
                        simpleName,
                        TYPE_JAVA_CLASS,
                        "Java Type"));
            }
        }
    }

    private List<Class<?>> getJavaCoreCompletionTypes() {
        List<Class<?>> types = new ArrayList<>();
        for (String className : JAVA_CORE_COMPLETION_CLASS_NAMES) {
            Class<?> type = resolveJavaType(className);
            if (type != null) {
                types.add(type);
            }
        }
        return types;
    }

    private Class<?> resolveJavaType(String className) {
        synchronized (JAVA_TYPE_CACHE) {
            if (JAVA_TYPE_CACHE.containsKey(className)) {
                return JAVA_TYPE_CACHE.get(className);
            }
            Class<?> type = loadJavaType(className);
            JAVA_TYPE_CACHE.put(className, type);
            return type;
        }
    }

    private Class<?> loadJavaType(String className) {
        try {
            return Class.forName(className);
        } catch (Throwable ignored) {
        }

        try {
            return Class.forName(className, false, getJavaReflectionClassLoader());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private ClassLoader getJavaReflectionClassLoader() {
        synchronized (JAVA_TYPE_CACHE) {
            if (javaReflectionClassLoader != null) {
                return javaReflectionClassLoader;
            }

            List<URL> urls = new ArrayList<>();
            addReflectionPath(urls, new File("out/artifacts/scenemax_projector-windows.jar"));
            addReflectionPath(urls, new File("out/artifacts/scenemax_win_projector.jar"));
            addReflectionPath(urls, new File("scenemax_win_projector/build/classes/java/main"));
            addReflectionPath(urls, new File("scenemax3d_common_types/build/classes/java/main"));
            addReflectionPath(urls, new File("scenemax3d_compiler/build/classes/java/main"));
            addReflectionPath(urls, new File("scenemax_effekseer_runtime/build/classes/java/main"));

            javaReflectionClassLoader = new URLClassLoader(
                    urls.toArray(new URL[0]),
                    SceneMaxAutoComplete.class.getClassLoader());
            return javaReflectionClassLoader;
        }
    }

    private void addReflectionPath(List<URL> urls, File file) {
        if (file == null || !file.exists()) {
            return;
        }
        try {
            urls.add(file.toURI().toURL());
        } catch (Exception ignored) {
        }
    }

    private Class<?> inferJavaExpressionType(String expression, Map<String, Class<?>> vars) {
        if (expression == null || expression.isBlank()) {
            return null;
        }

        List<String> chain = splitJavaExpressionChain(expression.trim());
        Class<?> currentType = null;
        for (int i = 0; i < chain.size(); i++) {
            String part = chain.get(i);
            if (part.endsWith("()")) {
                String methodName = part.substring(0, part.length() - 2);
                if (currentType == null) {
                    if ("getSceneMaxApp".equals(methodName)) {
                        currentType = resolveJavaType("com.scenemaxeng.projector.SceneMaxApp");
                    } else if ("getSceneMaxScope".equals(methodName)) {
                        currentType = resolveJavaType("com.scenemaxeng.projector.SceneMaxScope");
                    } else {
                        return null;
                    }
                } else {
                    Method method = findNoArgMethod(currentType, methodName);
                    if (method == null) {
                        return null;
                    }
                    currentType = method.getReturnType();
                }
            } else if (part.startsWith("getEntitySpatial(") && part.endsWith(")")) {
                currentType = resolveJavaType("com.jme3.scene.Spatial");
            } else if (part.startsWith("getEntity(") && part.endsWith(")")) {
                currentType = resolveJavaType("com.scenemaxeng.projector.EntityInstBase");
            } else {
                if (i == 0) {
                    if ("this".equals(part)) {
                        currentType = resolveJavaType("com.scenemaxeng.projector.SceneMaxBaseAppState");
                    } else {
                        currentType = vars.get(part);
                    }
                } else {
                    Field field = findField(currentType, part);
                    if (field == null) {
                        return null;
                    }
                    currentType = field.getType();
                }
            }
        }
        return currentType;
    }

    private List<String> splitJavaExpressionChain(String expression) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        int depth = 0;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == '.' && depth == 0) {
                parts.add(expression.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(expression.substring(start));
        return parts;
    }

    private Method findNoArgMethod(Class<?> type, String methodName) {
        if (type == null) {
            return null;
        }
        for (Method method : type.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == 0) {
                return method;
            }
        }
        return null;
    }

    private Field findField(Class<?> type, String fieldName) {
        if (type == null) {
            return null;
        }
        for (Field field : type.getFields()) {
            if (field.getName().equals(fieldName)) {
                return field;
            }
        }
        return null;
    }

    private void collectJavaMembers(Class<?> type, String lowerPrefix, List<CompletionItem> results, Set<String> added) {
        for (Method method : type.getMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            String name = method.getName();
            if (!name.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                continue;
            }
            String signature = javaMethodSignature(method);
            String key = signature + ":method";
            if (added.add(key)) {
                String insert = method.getParameterCount() == 0 ? name + "()" : name + "(";
                results.add(new CompletionItem(signature, insert, TYPE_JAVA_METHOD, declaringCategory(method)));
            }
        }

        for (Field field : type.getFields()) {
            if (!Modifier.isPublic(field.getModifiers())) {
                continue;
            }
            String name = field.getName();
            if (!name.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                continue;
            }
            if (added.add(name + ":field")) {
                results.add(new CompletionItem(
                        name + " : " + shortTypeName(field.getType()),
                        name,
                        TYPE_JAVA_FIELD,
                        field.getDeclaringClass().getSimpleName() + " Field"));
            }
        }
    }

    private String javaMethodSignature(Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append(method.getName()).append("(");
        Class<?>[] params = method.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(shortTypeName(params[i]));
        }
        sb.append(") : ").append(shortTypeName(method.getReturnType()));
        return sb.toString();
    }

    private String declaringCategory(Method method) {
        Class<?> owner = method.getDeclaringClass();
        if (owner == Object.class) {
            return "Object Method";
        }
        return owner.getSimpleName() + " Method";
    }

    private String shortTypeName(Class<?> type) {
        if (type == null) {
            return "";
        }
        if (type == Void.TYPE) {
            return "void";
        }
        if (type.isArray()) {
            return shortTypeName(type.getComponentType()) + "[]";
        }
        return type.getSimpleName();
    }

    private int compareJavaCompletionItems(CompletionItem a, CompletionItem b) {
        int pa = javaPriority(a);
        int pb = javaPriority(b);
        if (pa != pb) {
            return Integer.compare(pa, pb);
        }
        return a.displayText.compareToIgnoreCase(b.displayText);
    }

    private int javaPriority(CompletionItem item) {
        if (item.category.startsWith("SceneMaxApp")) return 0;
        if (item.type == TYPE_JAVA_SNIPPET) return 1;
        if (item.type == TYPE_VARIABLE) return 2;
        if (item.category.startsWith("Spatial") || item.category.startsWith("Node")) return 3;
        if (item.type == TYPE_JAVA_METHOD) return 4;
        if (item.type == TYPE_JAVA_FIELD) return 5;
        if (item.type == TYPE_JAVA_CLASS) return 6;
        if (item.category.startsWith("Object")) return 9;
        return 7;
    }

    /**
     * Extracts all symbols from a ProgramDef into the completion list.
     * Walks the full scope chain (parent scopes).
     */
    private void collectFromProgramDef(ProgramDef prg, List<CompletionItem> results,
                                       Set<String> addedNames, String filterPrefix, boolean isExprPointer) {
        if (prg == null) return;

        // Variables
        for (Map.Entry<String, VariableDef> entry : prg.vars_index.entrySet()) {
            String name = entry.getKey();
            VariableDef varDef = entry.getValue();

            if (!name.toLowerCase().startsWith(filterPrefix)) continue;

            String uniqueKey = name.toLowerCase() + ":var";
            if (!addedNames.add(uniqueKey)) continue;

            if (isExprPointer) {
                results.add(new CompletionItem(name, name, TYPE_EXPR_POINTER, "Expr Pointer"));
                continue;
            }

            int type;
            String category;
            switch (varDef.varType) {
                case VariableDef.VAR_TYPE_3D:
                    type = TYPE_OBJECT_3D;
                    category = "3D Object";
                    break;
                case VariableDef.VAR_TYPE_2D:
                    type = TYPE_OBJECT_2D;
                    category = "2D Sprite";
                    break;
                case VariableDef.VAR_TYPE_CAMERA:
                    type = TYPE_OBJECT_3D;
                    category = "Camera";
                    break;
                case VariableDef.VAR_TYPE_SPHERE:
                    type = TYPE_OBJECT_3D;
                    category = "Sphere";
                    break;
                case VariableDef.VAR_TYPE_BOX:
                    type = TYPE_OBJECT_3D;
                    category = "Box";
                    break;
                case VariableDef.VAR_TYPE_CYLINDER:
                case VariableDef.VAR_TYPE_HOLLOW_CYLINDER:
                    type = TYPE_OBJECT_3D;
                    category = "Cylinder";
                    break;
                case VariableDef.VAR_TYPE_QUAD:
                    type = TYPE_OBJECT_3D;
                    category = "Quad";
                    break;
                case VariableDef.VAR_TYPE_STRING:
                    type = TYPE_VARIABLE;
                    category = "String";
                    break;
                case VariableDef.VAR_TYPE_NUMBER:
                    type = TYPE_VARIABLE;
                    category = "Number";
                    break;
                case VariableDef.VAR_TYPE_ARRAY:
                    type = TYPE_VARIABLE;
                    category = "Array";
                    break;
                case VariableDef.VAR_TYPE_EXPR_POINTER:
                    type = TYPE_EXPR_POINTER;
                    category = "Expr Pointer";
                    break;
                case VariableDef.VAR_TYPE_OBJECT:
                    type = TYPE_VARIABLE;
                    category = "Parameter";
                    break;
                default:
                    type = TYPE_VARIABLE;
                    category = "Variable";
                    break;
            }

            results.add(new CompletionItem(name, name, type, category));
        }

        // Functions
        for (Map.Entry<String, FunctionBlockDef> entry : prg.functions.entrySet()) {
            String name = entry.getKey();
            if (!name.toLowerCase().startsWith(filterPrefix)) continue;
            // Skip implicit foreach functions
            if (name.startsWith("foreach_")) continue;

            String uniqueKey = name.toLowerCase() + ":func";
            if (!addedNames.add(uniqueKey)) continue;

            if (isExprPointer) {
                results.add(new CompletionItem(name, name, TYPE_EXPR_POINTER, "Expr Pointer"));
            } else {
                results.add(new CompletionItem(name, name, TYPE_FUNCTION, "Function"));
            }
        }

        // Models (resource names)
        if (!isExprPointer) {
            for (Map.Entry<String, ModelDef> entry : prg.models.entrySet()) {
                String name = entry.getKey();
                if (!name.toLowerCase().startsWith(filterPrefix)) continue;

                String uniqueKey = name.toLowerCase() + ":model";
                if (!addedNames.add(uniqueKey)) continue;

                results.add(new CompletionItem(name, name, TYPE_MODEL, "Model Resource"));
            }
        }

        // Sprites (resource names)
        if (!isExprPointer) {
            for (Map.Entry<String, SpriteDef> entry : prg.sprites.entrySet()) {
                String name = entry.getKey();
                if (!name.toLowerCase().startsWith(filterPrefix)) continue;

                String uniqueKey = name.toLowerCase() + ":sprite";
                if (!addedNames.add(uniqueKey)) continue;

                results.add(new CompletionItem(name, name, TYPE_SPRITE, "Sprite Resource"));
            }
        }

        // Groups
        if (!isExprPointer) {
            for (Map.Entry<String, GroupDef> entry : prg.groups.entrySet()) {
                String name = entry.getKey();
                if (!name.toLowerCase().startsWith(filterPrefix)) continue;

                String uniqueKey = name.toLowerCase() + ":group";
                if (!addedNames.add(uniqueKey)) continue;

                results.add(new CompletionItem(name, name, TYPE_GROUP, "Group"));
            }
        }

        // Function parameters (inParams)
        if (!isExprPointer && prg.inParams != null) {
            for (String param : prg.inParams) {
                if (!param.toLowerCase().startsWith(filterPrefix)) continue;

                String uniqueKey = param.toLowerCase() + ":param";
                if (!addedNames.add(uniqueKey)) continue;

                results.add(new CompletionItem(param, param, TYPE_PARAMETER, "Parameter"));
            }
        }
    }

    // ---- Inner classes ----

    private static class CompletionItem {
        final String displayText;
        String insertText;
        final int type;
        final String category;

        CompletionItem(String displayText, String insertText, int type, String category) {
            this.displayText = displayText;
            this.insertText = insertText;
            this.type = type;
            this.category = category;
        }

        @Override
        public String toString() {
            return displayText;
        }
    }

    private static class CompletionCellRenderer extends DefaultListCellRenderer {

        private static final Color KEYWORD_COLOR = new Color(204, 120, 50);
        private static final Color VARIABLE_COLOR = new Color(152, 118, 170);
        private static final Color OBJECT_3D_COLOR = new Color(106, 135, 89);
        private static final Color OBJECT_2D_COLOR = new Color(86, 156, 214);
        private static final Color FUNCTION_COLOR = new Color(255, 198, 109);
        private static final Color EXPR_PTR_COLOR = new Color(104, 151, 187);
        private static final Color BUILTIN_COLOR = new Color(220, 220, 170);
        private static final Color COLOR_COLOR = new Color(200, 200, 100);
        private static final Color EFFECT_COLOR = new Color(255, 150, 100);
        private static final Color INPUT_KEY_COLOR = new Color(150, 180, 200);
        private static final Color GROUP_COLOR = new Color(78, 201, 176);
        private static final Color MODEL_COLOR = new Color(106, 135, 89);
        private static final Color SPRITE_COLOR = new Color(86, 156, 214);
        private static final Color PARAM_COLOR = new Color(190, 140, 190);
        private static final Color JAVA_METHOD_COLOR = new Color(255, 198, 109);
        private static final Color JAVA_FIELD_COLOR = new Color(152, 118, 170);
        private static final Color JAVA_CLASS_COLOR = new Color(86, 156, 214);
        private static final Color JAVA_SNIPPET_COLOR = new Color(78, 201, 176);

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof CompletionItem) {
                CompletionItem item = (CompletionItem) value;
                String prefix = getTypePrefix(item.type);
                label.setText(prefix + "  " + item.displayText + "    " + item.category);

                if (!isSelected) {
                    label.setForeground(getTypeColor(item.type));
                }

                label.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
            }

            return label;
        }

        private String getTypePrefix(int type) {
            switch (type) {
                case TYPE_KEYWORD:      return "K";
                case TYPE_VARIABLE:     return "V";
                case TYPE_OBJECT_3D:    return "3D";
                case TYPE_OBJECT_2D:    return "2D";
                case TYPE_FUNCTION:     return "F";
                case TYPE_EXPR_POINTER: return "@";
                case TYPE_BUILTIN_FUNC: return "f";
                case TYPE_COLOR:        return "C";
                case TYPE_EFFECT:       return "E";
                case TYPE_INPUT_KEY:    return "I";
                case TYPE_GROUP:        return "G";
                case TYPE_MODEL:        return "M";
                case TYPE_SPRITE:       return "S";
                case TYPE_PARAMETER:    return "P";
                case TYPE_JAVA_METHOD:  return "m";
                case TYPE_JAVA_FIELD:   return "v";
                case TYPE_JAVA_CLASS:   return "J";
                case TYPE_JAVA_SNIPPET: return "{}";
                default:                return " ";
            }
        }

        private Color getTypeColor(int type) {
            switch (type) {
                case TYPE_KEYWORD:      return KEYWORD_COLOR;
                case TYPE_VARIABLE:     return VARIABLE_COLOR;
                case TYPE_OBJECT_3D:    return OBJECT_3D_COLOR;
                case TYPE_OBJECT_2D:    return OBJECT_2D_COLOR;
                case TYPE_FUNCTION:     return FUNCTION_COLOR;
                case TYPE_EXPR_POINTER: return EXPR_PTR_COLOR;
                case TYPE_BUILTIN_FUNC: return BUILTIN_COLOR;
                case TYPE_COLOR:        return COLOR_COLOR;
                case TYPE_EFFECT:       return EFFECT_COLOR;
                case TYPE_INPUT_KEY:    return INPUT_KEY_COLOR;
                case TYPE_GROUP:        return GROUP_COLOR;
                case TYPE_MODEL:        return MODEL_COLOR;
                case TYPE_SPRITE:       return SPRITE_COLOR;
                case TYPE_PARAMETER:    return PARAM_COLOR;
                case TYPE_JAVA_METHOD:  return JAVA_METHOD_COLOR;
                case TYPE_JAVA_FIELD:   return JAVA_FIELD_COLOR;
                case TYPE_JAVA_CLASS:   return JAVA_CLASS_COLOR;
                case TYPE_JAVA_SNIPPET: return JAVA_SNIPPET_COLOR;
                default:                return Color.WHITE;
            }
        }
    }
}
