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

    private final RSyntaxTextArea textArea;
    private JWindow popupWindow;
    private JList<CompletionItem> completionList;
    private DefaultListModel<CompletionItem> listModel;
    private boolean isShowing = false;
    private boolean isInserting = false;

    // Cached ProgramDef from last parse
    private ProgramDef cachedProgram = null;
    private long lastParseTime = 0;
    private static final long PARSE_COOLDOWN_MS = 2000; // re-parse at most every 2 seconds

    private static final int AUTO_TRIGGER_LENGTH = 2;

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
        "Lights", "Light", "Probe",
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
            cachedProgram = parser.parse(code);
            lastParseTime = now;
        } catch (Exception e) {
            // Parsing failed — keep old cache if available
            e.printStackTrace();
        }
    }

    /**
     * Does a lightweight parse of just the current editor text to capture
     * local scope symbols that may not be in the main file yet.
     */
    private ProgramDef parseCurrentEditor() {
        try {
            String code = textArea.getText();
            if (code == null || code.trim().isEmpty()) return null;

            // Strip project metadata
            code = code.replaceAll("//\\$\\[project\\]=(.+?);", "");

            File scriptFolder = findScriptFolder();
            String codePath = scriptFolder != null ? scriptFolder.getAbsolutePath() : "";

            SceneMaxLanguageParser parser = new SceneMaxLanguageParser(null, codePath);
            parser.enableChildParserMode(true); // don't clear static collections
            return parser.parse(code);
        } catch (Exception e) {
            return null;
        }
    }

    // ---- Completion logic ----

    private void onTextChanged() {
        String prefix = getCurrentPrefix();
        if (prefix.length() >= AUTO_TRIGGER_LENGTH) {
            // Ensure we have a parse result (use cached if fresh enough)
            if (cachedProgram == null) {
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
        lastParseTime = 0;
    }

    // ---- Building completion list from ProgramDef ----

    private List<CompletionItem> getCompletions(String prefix) {
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
                default:                return Color.WHITE;
            }
        }
    }
}
