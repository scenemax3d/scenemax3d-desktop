package com.scenemax.desktop;

import com.scenemax.designer.DesignerDocument;
import com.scenemax.designer.DesignerPanel;
import com.scenemax.designer.Import3DModelPanel;
import com.scenemax.designer.ui.designer.UIDesignerPanel;
import org.apache.commons.io.FileUtils;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EditorTabPanel extends JPanel {

    public static class TabData {
        String filePath;
        String content;
        int caretPosition;
        boolean isRtlMode;
        boolean dirty;
        boolean isDesignerTab;
        DesignerPanel designerPanel;
        boolean isUIDesignerTab;
        UIDesignerPanel uiDesignerPanel;

        public TabData(String filePath, String content) {
            this.filePath = filePath;
            this.content = content;
            this.caretPosition = 0;
            this.isRtlMode = false;
            this.dirty = false;
            this.isDesignerTab = false;
            this.designerPanel = null;
            this.isUIDesignerTab = false;
            this.uiDesignerPanel = null;
        }

        public String getFileName() {
            return new File(filePath).getName();
        }
    }

    private class TabButton extends JPanel {
        private final TabData tabData;
        private final JLabel titleLabel;
        private boolean selected;

        TabButton(TabData tabData) {
            this.tabData = tabData;
            setLayout(new BorderLayout(4, 0));
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 4));

            titleLabel = new JLabel(tabData.getFileName());
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.PLAIN, 12f));
            add(titleLabel, BorderLayout.CENTER);

            JButton closeBtn = new JButton("\u00d7");
            closeBtn.setFont(closeBtn.getFont().deriveFont(Font.PLAIN, 14f));
            closeBtn.setMargin(new Insets(0, 4, 0, 4));
            closeBtn.setContentAreaFilled(false);
            closeBtn.setBorderPainted(false);
            closeBtn.setFocusPainted(false);
            closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            closeBtn.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    closeBtn.setForeground(Color.RED);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    closeBtn.setForeground(UIManager.getColor("Label.foreground"));
                }
            });
            closeBtn.addActionListener(e -> closeTab(tabData));
            add(closeBtn, BorderLayout.EAST);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        switchToTab(tabData);
                    }
                }
            });

            setSelected(false);
        }

        void setSelected(boolean selected) {
            this.selected = selected;
            if (selected) {
                setBackground(UIManager.getColor("TabbedPane.selectedBackground") != null
                        ? UIManager.getColor("TabbedPane.selectedBackground")
                        : UIManager.getColor("Panel.background").brighter());
            } else {
                setBackground(UIManager.getColor("Panel.background"));
            }
        }

        void updateTitle() {
            String name = tabData.getFileName();
            if (tabData.dirty) {
                name = "* " + name;
            }
            if (tabData.isDesignerTab) {
                name = "\u25A6 " + name; // square with fill icon prefix
            } else if (tabData.isUIDesignerTab) {
                name = "\u25A4 " + name; // UI designer icon prefix
            }
            titleLabel.setText(name);
        }
    }

    private final List<TabData> tabs = new ArrayList<>();
    private final Map<String, TabButton> tabButtons = new LinkedHashMap<>();
    private TabData activeTab = null;
    private final JPanel tabBar;

    private final RSyntaxTextArea textArea;
    private final JScrollPane textAreaSP;
    private final JTextArea textAreaRTL;
    private final JScrollPane textAreaRtlSP;

    // Center container that swaps between editor and designer panels
    private final JPanel centerContainer;
    private final JLayeredPane editorPane;

    private boolean suppressDocumentEvents = false;
    private Runnable onTabChangedCallback;

    public EditorTabPanel(JLayeredPane layeredPane, RSyntaxTextArea textArea, JScrollPane textAreaSP,
                          JTextArea textAreaRTL, JScrollPane textAreaRtlSP) {
        super(new BorderLayout());
        this.textArea = textArea;
        this.textAreaSP = textAreaSP;
        this.textAreaRTL = textAreaRTL;
        this.textAreaRtlSP = textAreaRtlSP;
        this.editorPane = layeredPane;

        tabBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")));
        add(tabBar, BorderLayout.NORTH);

        // Simple container that holds either the editor or a designer panel
        centerContainer = new JPanel(new BorderLayout());
        centerContainer.add(editorPane, BorderLayout.CENTER);
        add(centerContainer, BorderLayout.CENTER);
    }

    public void setOnTabChangedCallback(Runnable callback) {
        this.onTabChangedCallback = callback;
    }

    public void openFile(String filePath, String content) {
        // Normalize path for consistent lookup
        String normalizedPath = new File(filePath).getAbsolutePath();

        // If tab already exists, switch to it
        if (tabButtons.containsKey(normalizedPath)) {
            TabData existing = null;
            for (TabData td : tabs) {
                if (td.filePath.equals(normalizedPath)) {
                    existing = td;
                    break;
                }
            }
            if (existing != null) {
                // Refresh content from disk if the tab has no unsaved user edits
                if (!existing.dirty) {
                    existing.content = content;
                }
                switchToTab(existing);
                return;
            }
        }

        // Create new tab
        TabData tabData = new TabData(normalizedPath, content);
        tabs.add(tabData);

        TabButton btn = new TabButton(tabData);
        tabButtons.put(normalizedPath, btn);
        tabBar.add(btn);
        tabBar.revalidate();
        tabBar.repaint();

        switchToTab(tabData);
    }

    /**
     * Opens a designer document tab with the embedded 3D designer panel.
     */
    public void openDesignerFile(String filePath, DesignerPanel panel) {
        String normalizedPath = new File(filePath).getAbsolutePath();

        // If tab already exists, switch to it
        if (tabButtons.containsKey(normalizedPath)) {
            TabData existing = null;
            for (TabData td : tabs) {
                if (td.filePath.equals(normalizedPath)) {
                    existing = td;
                    break;
                }
            }
            if (existing != null) {
                switchToTab(existing);
                return;
            }
        }

        // Create new designer tab
        TabData tabData = new TabData(normalizedPath, "");
        tabData.isDesignerTab = true;
        tabData.designerPanel = panel;
        tabs.add(tabData);

        TabButton btn = new TabButton(tabData);
        btn.updateTitle(); // apply designer prefix
        tabButtons.put(normalizedPath, btn);
        tabBar.add(btn);
        tabBar.revalidate();
        tabBar.repaint();

        switchToTab(tabData);
    }

    /**
     * Opens a UI designer document tab with the embedded UI designer panel.
     */
    public void openUIDesignerFile(String filePath, UIDesignerPanel panel) {
        String normalizedPath = new File(filePath).getAbsolutePath();

        // If tab already exists, switch to it
        if (tabButtons.containsKey(normalizedPath)) {
            TabData existing = null;
            for (TabData td : tabs) {
                if (td.filePath.equals(normalizedPath)) {
                    existing = td;
                    break;
                }
            }
            if (existing != null) {
                switchToTab(existing);
                return;
            }
        }

        // Create new UI designer tab
        TabData tabData = new TabData(normalizedPath, "");
        tabData.isUIDesignerTab = true;
        tabData.uiDesignerPanel = panel;
        tabs.add(tabData);

        TabButton btn = new TabButton(tabData);
        btn.updateTitle(); // apply UI designer prefix
        tabButtons.put(normalizedPath, btn);
        tabBar.add(btn);
        tabBar.revalidate();
        tabBar.repaint();

        switchToTab(tabData);
    }

    public void switchToTab(TabData newTab) {
        if (newTab == activeTab) return;

        // Save current state from editor into old tab (only for non-designer tabs)
        if (activeTab != null && !activeTab.isDesignerTab && !activeTab.isUIDesignerTab) {
            activeTab.content = getCurrentEditorText();
            activeTab.caretPosition = getCurrentCaretPosition();
            activeTab.isRtlMode = textAreaRtlSP.isVisible();
        }

        activeTab = newTab;

        // Swap center content
        centerContainer.removeAll();

        if (newTab.isDesignerTab) {
            // Show the designer panel for this tab.
            // activatePanel() moves the shared JME canvas into this panel
            // and switches the DesignerApp to this document.
            if (newTab.designerPanel != null) {
                newTab.designerPanel.activatePanel();
                centerContainer.add(newTab.designerPanel, BorderLayout.CENTER);
            }
        } else if (newTab.isUIDesignerTab) {
            // Show the UI designer panel for this tab.
            if (newTab.uiDesignerPanel != null) {
                newTab.uiDesignerPanel.activatePanel();
                centerContainer.add(newTab.uiDesignerPanel, BorderLayout.CENTER);
            }
        } else {
            // Show the code editor
            centerContainer.add(editorPane, BorderLayout.CENTER);

            // Load new tab content into editor
            suppressDocumentEvents = true;
            textArea.setText(newTab.content);
            textAreaRTL.setText(newTab.content);
            suppressDocumentEvents = false;

            // Restore caret position
            try {
                if (newTab.caretPosition <= newTab.content.length()) {
                    if (newTab.isRtlMode) {
                        textAreaRTL.setCaretPosition(newTab.caretPosition);
                    } else {
                        textArea.setCaretPosition(newTab.caretPosition);
                    }
                }
            } catch (IllegalArgumentException e) {
                // caret position out of bounds, reset to 0
            }

            // Restore RTL/LTR mode
            if (newTab.isRtlMode) {
                textAreaSP.setVisible(false);
                textAreaRtlSP.setVisible(true);
            } else {
                textAreaRtlSP.setVisible(false);
                textAreaSP.setVisible(true);
            }
        }

        centerContainer.revalidate();
        centerContainer.repaint();

        // Update tab button selection states
        for (Map.Entry<String, TabButton> entry : tabButtons.entrySet()) {
            entry.getValue().setSelected(entry.getKey().equals(newTab.filePath));
        }

        if (onTabChangedCallback != null) {
            onTabChangedCallback.run();
        }
    }

    public void closeTab(TabData tabData) {
        if (tabData == null) return;

        // Handle designer tab close
        if (tabData.isDesignerTab) {
            // Import panels need rollback on close if not yet imported
            if (tabData.designerPanel instanceof Import3DModelPanel) {
                Import3DModelPanel importPanel = (Import3DModelPanel) tabData.designerPanel;
                importPanel.onTabClosed();
            }
            // Deactivate the panel (saves camera state) but don't stop
            // the shared JME context — it's reused across all designer tabs
            if (tabData.designerPanel != null) {
                tabData.designerPanel.deactivatePanel();
            }
        } else if (tabData.isUIDesignerTab) {
            // Handle UI designer tab close — save and deactivate
            if (tabData.uiDesignerPanel != null) {
                tabData.uiDesignerPanel.deactivatePanel();
            }
        } else {
            // Auto-save if dirty
            if (tabData.dirty) {
                // If this is the active tab, sync content from editor first
                if (tabData == activeTab) {
                    tabData.content = getCurrentEditorText();
                }
                writeFileToDisk(tabData.filePath, tabData.content);
            }
        }

        int index = tabs.indexOf(tabData);
        tabs.remove(tabData);

        TabButton btn = tabButtons.remove(tabData.filePath);
        if (btn != null) {
            tabBar.remove(btn);
            tabBar.revalidate();
            tabBar.repaint();
        }

        // Switch to adjacent tab
        if (tabData == activeTab) {
            activeTab = null;
            if (!tabs.isEmpty()) {
                int newIndex = Math.min(index, tabs.size() - 1);
                switchToTab(tabs.get(newIndex));
            } else {
                // No tabs left, show editor and clear it
                centerContainer.removeAll();
                centerContainer.add(editorPane, BorderLayout.CENTER);
                centerContainer.revalidate();
                centerContainer.repaint();

                suppressDocumentEvents = true;
                textArea.setText("");
                textAreaRTL.setText("");
                suppressDocumentEvents = false;
                textArea.setEnabled(false);
                textAreaRTL.setEnabled(false);

                if (onTabChangedCallback != null) {
                    onTabChangedCallback.run();
                }
            }
        }
    }

    public void saveActiveTab() {
        if (activeTab == null) return;
        if (activeTab.isDesignerTab) return; // designer handles its own saving
        if (activeTab.isUIDesignerTab) {
            // Trigger UI designer save
            if (activeTab.uiDesignerPanel != null) {
                activeTab.uiDesignerPanel.saveDocument();
            }
            activeTab.dirty = false;
            TabButton btn = tabButtons.get(activeTab.filePath);
            if (btn != null) {
                btn.updateTitle();
            }
            return;
        }

        // Sync content from editor
        activeTab.content = getCurrentEditorText();

        // Sync between LTR/RTL editors
        suppressDocumentEvents = true;
        if (textAreaSP.isVisible()) {
            textAreaRTL.setText(textArea.getText());
        } else {
            textArea.setText(textAreaRTL.getText());
        }
        suppressDocumentEvents = false;

        writeFileToDisk(activeTab.filePath, activeTab.content);
        activeTab.dirty = false;

        TabButton btn = tabButtons.get(activeTab.filePath);
        if (btn != null) {
            btn.updateTitle();
        }
    }

    public void markActiveTabDirty() {
        if (activeTab == null) return;
        activeTab.dirty = true;

        TabButton btn = tabButtons.get(activeTab.filePath);
        if (btn != null) {
            btn.updateTitle();
        }
    }

    public boolean isSuppressingDocumentEvents() {
        return suppressDocumentEvents;
    }

    public String getCurrentEditorText() {
        return textAreaSP.isVisible() ? textArea.getText() : textAreaRTL.getText();
    }

    private int getCurrentCaretPosition() {
        return textAreaSP.isVisible() ? textArea.getCaretPosition() : textAreaRTL.getCaretPosition();
    }

    public String getActiveFilePath() {
        return activeTab != null ? activeTab.filePath : null;
    }

    public TabData getActiveTab() {
        return activeTab;
    }

    public boolean hasOpenTabs() {
        return !tabs.isEmpty();
    }

    public boolean isFileOpen(String filePath) {
        String normalizedPath = new File(filePath).getAbsolutePath();
        return tabButtons.containsKey(normalizedPath);
    }

    /**
     * Refreshes the content of an already-open tab (e.g. when an auto-generated
     * file like .code is updated on disk by the designer).  If the tab is
     * currently active, the visible text areas are also updated immediately.
     */
    public void refreshTabContent(String filePath, String newContent) {
        String normalizedPath = new File(filePath).getAbsolutePath();
        for (TabData td : tabs) {
            if (td.filePath.equals(normalizedPath) && !td.isDesignerTab && !td.isUIDesignerTab) {
                td.content = newContent;
                td.dirty = false;
                if (td == activeTab) {
                    suppressDocumentEvents = true;
                    textArea.setText(newContent);
                    textAreaRTL.setText(newContent);
                    suppressDocumentEvents = false;
                }
                break;
            }
        }
    }

    /**
     * Stops the shared JME3 designer context.  Call on application shutdown
     * to release JME3 resources.
     */
    public void stopSharedDesigner() {
        DesignerPanel.stopSharedDesigner();
    }

    public void updateTabForRename(String oldPath, String newPath) {
        String normalizedOld = new File(oldPath).getAbsolutePath();
        String normalizedNew = new File(newPath).getAbsolutePath();

        TabButton btn = tabButtons.remove(normalizedOld);
        if (btn == null) return;

        btn.tabData.filePath = normalizedNew;
        tabButtons.put(normalizedNew, btn);
        btn.updateTitle();
    }

    public void closeTabByPath(String path) {
        closeTabByPath(path, false);
    }

    /**
     * Closes the tab for the given file path.
     *
     * @param path     absolute path of the file whose tab should be closed
     * @param deleting true when the underlying file is being deleted; for
     *                 designer tabs this clears the shared JME app's in-memory
     *                 state so stale entities are not accidentally saved later
     */
    public void closeTabByPath(String path, boolean deleting) {
        String normalizedPath = new File(path).getAbsolutePath();
        TabData toClose = null;
        for (TabData td : tabs) {
            if (td.filePath.equals(normalizedPath)) {
                toClose = td;
                break;
            }
        }
        if (toClose != null) {
            toClose.dirty = false; // file was deleted, don't try to save
            if (deleting && toClose.isDesignerTab && toClose.designerPanel != null) {
                toClose.designerPanel.clearAndDeactivatePanel();
                // Null out so closeTab() doesn't call deactivatePanel() again
                toClose.designerPanel = null;
                toClose.isDesignerTab = false;
            }
            if (deleting && toClose.isUIDesignerTab && toClose.uiDesignerPanel != null) {
                toClose.uiDesignerPanel.clearAndDeactivatePanel();
                toClose.uiDesignerPanel = null;
                toClose.isUIDesignerTab = false;
            }
            closeTab(toClose);
        }
    }

    /**
     * Returns the file paths of all open tabs, in order.
     */
    public List<String> getOpenFilePaths() {
        List<String> paths = new ArrayList<>();
        for (TabData td : tabs) {
            paths.add(td.filePath);
        }
        return paths;
    }

    /**
     * Closes all open tabs (auto-saving dirty ones). Used when switching projects.
     */
    public void closeAllTabs() {
        // Close tabs in reverse to avoid index shifting issues
        List<TabData> toClose = new ArrayList<>(tabs);
        for (TabData td : toClose) {
            closeTab(td);
        }
    }

    public void closeTabsUnderFolder(String folderPath) {
        String normalizedFolder = new File(folderPath).getAbsolutePath() + File.separator;
        List<TabData> toClose = new ArrayList<>();
        for (TabData td : tabs) {
            if (td.filePath.startsWith(normalizedFolder)) {
                td.dirty = false; // folder was deleted, don't try to save
                toClose.add(td);
            }
        }
        for (TabData td : toClose) {
            closeTab(td);
        }
    }

    private void writeFileToDisk(String path, String content) {
        content = content.replaceAll("\r", "");
        File f = new File(path);
        if (f.isFile()) {
            try {
                FileUtils.writeStringToFile(f, content, StandardCharsets.UTF_8);
            } catch (IOException e) {
                e.printStackTrace();
            }
            // If an _init.code or _end.code file was saved, regenerate the companion .code file
            String name = f.getName();
            if (name.endsWith("_init.code") || name.endsWith("_end.code")) {
                String baseName = name.endsWith("_init.code")
                        ? name.substring(0, name.length() - "_init.code".length())
                        : name.substring(0, name.length() - "_end.code".length());
                File smdesignFile = new File(f.getParentFile(), baseName + ".smdesign");
                if (smdesignFile.exists()) {
                    try {
                        DesignerDocument.regenerateCodeFileFromDisk(smdesignFile);
                        // Refresh the open .code tab if any
                        File codeFile = DesignerDocument.getCodeFile(smdesignFile);
                        if (codeFile.isFile()) {
                            String codeContent = FileUtils.readFileToString(codeFile, StandardCharsets.UTF_8);
                            refreshTabContent(codeFile.getAbsolutePath(), codeContent);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
