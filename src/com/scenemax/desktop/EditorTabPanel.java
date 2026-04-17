package com.scenemax.desktop;

import com.scenemax.designer.DesignerDocument;
import com.scenemax.designer.DesignerPanel;
import com.scenemax.designer.Import3DModelPanel;
import com.scenemax.designer.animation.ImportAnimationPanel;
import com.scenemax.designer.effekseer.EffekseerEffectDesignerPanel;
import com.scenemax.designer.material.MaterialDesignerPanel;
import com.scenemax.designer.shader.EnvironmentShaderDesignerPanel;
import com.scenemax.designer.shader.ShaderDesignerPanel;
import com.scenemax.designer.ui.designer.UIDesignerPanel;
import org.apache.commons.io.FileUtils;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
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
        boolean isEffekseerDesignerTab;
        EffekseerEffectDesignerPanel effekseerDesignerPanel;
        boolean isShaderDesignerTab;
        ShaderDesignerPanel shaderDesignerPanel;
        boolean isEnvironmentShaderDesignerTab;
        EnvironmentShaderDesignerPanel environmentShaderDesignerPanel;
        boolean isMaterialDesignerTab;
        MaterialDesignerPanel materialDesignerPanel;
        boolean isAnimationImportTab;
        ImportAnimationPanel animationImportPanel;

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
            this.isEffekseerDesignerTab = false;
            this.effekseerDesignerPanel = null;
            this.isShaderDesignerTab = false;
            this.shaderDesignerPanel = null;
            this.isEnvironmentShaderDesignerTab = false;
            this.environmentShaderDesignerPanel = null;
            this.isMaterialDesignerTab = false;
            this.materialDesignerPanel = null;
            this.isAnimationImportTab = false;
            this.animationImportPanel = null;
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
                    if (showPopupIfNeeded(e)) {
                        return;
                    }
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        switchToTab(tabData);
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    showPopupIfNeeded(e);
                }
            });

            setSelected(false);
        }

        private boolean showPopupIfNeeded(MouseEvent e) {
            if (!e.isPopupTrigger()) {
                return false;
            }
            JPopupMenu popup = new JPopupMenu();
            JMenuItem reloadItem = new JMenuItem("Reload from disk");
            reloadItem.addActionListener(evt -> reloadTabFromDisk(tabData.filePath, true, true));
            popup.add(reloadItem);
            JMenuItem closeItem = new JMenuItem("Close");
            closeItem.addActionListener(evt -> closeTab(tabData));
            popup.add(closeItem);
            popup.show(this, e.getX(), e.getY());
            return true;
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
            } else if (tabData.isEffekseerDesignerTab) {
                name = "\u2739 " + name; // effekseer designer icon prefix
            } else if (tabData.isShaderDesignerTab) {
                name = "\u2726 " + name; // shader icon prefix
            } else if (tabData.isEnvironmentShaderDesignerTab) {
                name = "\u2601 " + name; // cloud icon prefix
            } else if (tabData.isMaterialDesignerTab) {
                name = "\u25C8 " + name;
            } else if (tabData.isAnimationImportTab) {
                name = "\u25B6 " + name;
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

    public void openEffekseerDesignerFile(String filePath, EffekseerEffectDesignerPanel panel) {
        String normalizedPath = new File(filePath).getAbsolutePath();

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

        TabData tabData = new TabData(normalizedPath, "");
        tabData.isEffekseerDesignerTab = true;
        tabData.effekseerDesignerPanel = panel;
        tabs.add(tabData);

        TabButton btn = new TabButton(tabData);
        btn.updateTitle();
        tabButtons.put(normalizedPath, btn);
        tabBar.add(btn);
        tabBar.revalidate();
        tabBar.repaint();

        switchToTab(tabData);
    }

    public void openShaderDesignerFile(String filePath, ShaderDesignerPanel panel) {
        String normalizedPath = new File(filePath).getAbsolutePath();

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

        TabData tabData = new TabData(normalizedPath, "");
        tabData.isShaderDesignerTab = true;
        tabData.shaderDesignerPanel = panel;
        tabs.add(tabData);

        TabButton btn = new TabButton(tabData);
        btn.updateTitle();
        tabButtons.put(normalizedPath, btn);
        tabBar.add(btn);
        tabBar.revalidate();
        tabBar.repaint();

        switchToTab(tabData);
    }

    public void openEnvironmentShaderDesignerFile(String filePath, EnvironmentShaderDesignerPanel panel) {
        String normalizedPath = new File(filePath).getAbsolutePath();

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

        TabData tabData = new TabData(normalizedPath, "");
        tabData.isEnvironmentShaderDesignerTab = true;
        tabData.environmentShaderDesignerPanel = panel;
        tabs.add(tabData);

        TabButton btn = new TabButton(tabData);
        btn.updateTitle();
        tabButtons.put(normalizedPath, btn);
        tabBar.add(btn);
        tabBar.revalidate();
        tabBar.repaint();

        switchToTab(tabData);
    }

    public void openMaterialDesignerFile(String filePath, MaterialDesignerPanel panel) {
        String normalizedPath = new File(filePath).getAbsolutePath();

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

        TabData tabData = new TabData(normalizedPath, "");
        tabData.isMaterialDesignerTab = true;
        tabData.materialDesignerPanel = panel;
        tabs.add(tabData);

        TabButton btn = new TabButton(tabData);
        btn.updateTitle();
        tabButtons.put(normalizedPath, btn);
        tabBar.add(btn);
        tabBar.revalidate();
        tabBar.repaint();

        switchToTab(tabData);
    }

    public void openAnimationImportFile(String filePath, ImportAnimationPanel panel) {
        String normalizedPath = new File(filePath).getAbsolutePath();

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

        TabData tabData = new TabData(normalizedPath, "");
        tabData.isAnimationImportTab = true;
        tabData.animationImportPanel = panel;
        tabs.add(tabData);

        TabButton btn = new TabButton(tabData);
        btn.updateTitle();
        tabButtons.put(normalizedPath, btn);
        tabBar.add(btn);
        tabBar.revalidate();
        tabBar.repaint();

        switchToTab(tabData);
    }

    public void switchToTab(TabData newTab) {
        if (newTab == activeTab) return;

        // Save current state from editor into old tab (only for non-designer tabs)
        if (activeTab != null && !activeTab.isDesignerTab && !activeTab.isUIDesignerTab
                && !activeTab.isEffekseerDesignerTab
                && !activeTab.isShaderDesignerTab && !activeTab.isEnvironmentShaderDesignerTab
                && !activeTab.isMaterialDesignerTab
                && !activeTab.isAnimationImportTab) {
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
        } else if (newTab.isEffekseerDesignerTab) {
            if (newTab.effekseerDesignerPanel != null) {
                newTab.effekseerDesignerPanel.activatePanel();
                centerContainer.add(newTab.effekseerDesignerPanel, BorderLayout.CENTER);
            }
        } else if (newTab.isShaderDesignerTab) {
            if (newTab.shaderDesignerPanel != null) {
                newTab.shaderDesignerPanel.activatePanel();
                centerContainer.add(newTab.shaderDesignerPanel, BorderLayout.CENTER);
            }
        } else if (newTab.isEnvironmentShaderDesignerTab) {
            if (newTab.environmentShaderDesignerPanel != null) {
                newTab.environmentShaderDesignerPanel.activatePanel();
                centerContainer.add(newTab.environmentShaderDesignerPanel, BorderLayout.CENTER);
            }
        } else if (newTab.isMaterialDesignerTab) {
            if (newTab.materialDesignerPanel != null) {
                newTab.materialDesignerPanel.activatePanel();
                centerContainer.add(newTab.materialDesignerPanel, BorderLayout.CENTER);
            }
        } else if (newTab.isAnimationImportTab) {
            if (newTab.animationImportPanel != null) {
                centerContainer.add(newTab.animationImportPanel, BorderLayout.CENTER);
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
        } else if (tabData.isEffekseerDesignerTab) {
            if (tabData.effekseerDesignerPanel != null) {
                tabData.effekseerDesignerPanel.deactivatePanel();
            }
        } else if (tabData.isShaderDesignerTab) {
            if (tabData.shaderDesignerPanel != null) {
                tabData.shaderDesignerPanel.deactivatePanel();
            }
        } else if (tabData.isEnvironmentShaderDesignerTab) {
            if (tabData.environmentShaderDesignerPanel != null) {
                tabData.environmentShaderDesignerPanel.deactivatePanel();
            }
        } else if (tabData.isMaterialDesignerTab) {
            if (tabData.materialDesignerPanel != null) {
                tabData.materialDesignerPanel.deactivatePanel();
            }
        } else if (tabData.isAnimationImportTab) {
            // No shared JME context to deactivate.
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
        if (activeTab.isEffekseerDesignerTab) {
            if (activeTab.effekseerDesignerPanel != null) {
                activeTab.effekseerDesignerPanel.saveDocument();
            }
            activeTab.dirty = false;
            TabButton btn = tabButtons.get(activeTab.filePath);
            if (btn != null) {
                btn.updateTitle();
            }
            return;
        }
        if (activeTab.isShaderDesignerTab) {
            if (activeTab.shaderDesignerPanel != null) {
                activeTab.shaderDesignerPanel.saveDocument();
            }
            activeTab.dirty = false;
            TabButton btn = tabButtons.get(activeTab.filePath);
            if (btn != null) {
                btn.updateTitle();
            }
            return;
        }
        if (activeTab.isEnvironmentShaderDesignerTab) {
            if (activeTab.environmentShaderDesignerPanel != null) {
                activeTab.environmentShaderDesignerPanel.saveDocument();
            }
            activeTab.dirty = false;
            TabButton btn = tabButtons.get(activeTab.filePath);
            if (btn != null) {
                btn.updateTitle();
            }
            return;
        }
        if (activeTab.isMaterialDesignerTab) {
            if (activeTab.materialDesignerPanel != null) {
                activeTab.materialDesignerPanel.saveDocument();
            }
            activeTab.dirty = false;
            TabButton btn = tabButtons.get(activeTab.filePath);
            if (btn != null) {
                btn.updateTitle();
            }
            return;
        }
        if (activeTab.isAnimationImportTab) return;

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

    public void markTabClean(String filePath) {
        if (filePath == null) return;
        String normalizedPath = new File(filePath).getAbsolutePath();
        for (TabData tab : tabs) {
            if (tab.filePath.equals(normalizedPath)) {
                tab.dirty = false;
                TabButton btn = tabButtons.get(tab.filePath);
                if (btn != null) {
                    btn.updateTitle();
                }
                break;
            }
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

    public boolean isActiveTabDirty() {
        return activeTab != null && activeTab.dirty;
    }

    public String getActiveTabKind() {
        return activeTab != null ? getTabKind(activeTab) : null;
    }

    public boolean isTabDirty(String filePath) {
        TabData tab = findTabByPath(filePath);
        return tab != null && tab.dirty;
    }

    public String getTabKind(String filePath) {
        TabData tab = findTabByPath(filePath);
        return tab != null ? getTabKind(tab) : null;
    }

    public void refreshOrOpenTextTab(String filePath, String content) {
        TabData tab = findTabByPath(filePath);
        if (tab == null) {
            openFile(filePath, content);
            return;
        }
        if (!isTextEditorTab(tab)) {
            return;
        }
        refreshTabContent(filePath, content);
    }

    /**
     * Refreshes the content of an already-open tab (e.g. when an auto-generated
     * file like .code is updated on disk by the designer).  If the tab is
     * currently active, the visible text areas are also updated immediately.
     */
    public void refreshTabContent(String filePath, String newContent) {
        String normalizedPath = new File(filePath).getAbsolutePath();
        for (TabData td : tabs) {
            if (td.filePath.equals(normalizedPath) && !td.isDesignerTab && !td.isUIDesignerTab
                    && !td.isEffekseerDesignerTab
                    && !td.isShaderDesignerTab && !td.isEnvironmentShaderDesignerTab
                    && !td.isMaterialDesignerTab
                    && !td.isAnimationImportTab) {
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

    public boolean reloadTabFromDisk(String filePath, boolean activate, boolean promptIfDirty) {
        return reloadTabFromDisk(filePath, activate, promptIfDirty, true);
    }

    public boolean reloadTabFromDisk(String filePath, boolean activate, boolean promptIfDirty, boolean discardEditorState) {
        TabData tab = findTabByPath(filePath);
        if (tab == null) {
            return false;
        }
        if (promptIfDirty && tab.dirty) {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "Discard unsaved changes and reload from disk?",
                    "Reload from Disk",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return false;
            }
        }

        try {
            if (discardEditorState) {
                discardTabEditorState(tab);
            }
            if (tab.isDesignerTab) {
                if (tab.designerPanel == null) {
                    return false;
                }
                switchToTab(tab);
                tab.designerPanel.reloadFromDisk();
            } else if (tab.isUIDesignerTab) {
                if (tab.uiDesignerPanel == null) {
                    return false;
                }
                switchToTab(tab);
                tab.uiDesignerPanel.reloadFromDisk();
            } else if (tab.isEffekseerDesignerTab) {
                if (tab.effekseerDesignerPanel == null) {
                    return false;
                }
                switchToTab(tab);
                tab.effekseerDesignerPanel.reloadFromDisk();
            } else if (tab.isShaderDesignerTab) {
                if (tab.shaderDesignerPanel == null) {
                    return false;
                }
                switchToTab(tab);
                tab.shaderDesignerPanel.reloadFromDisk();
            } else if (tab.isEnvironmentShaderDesignerTab) {
                if (tab.environmentShaderDesignerPanel == null) {
                    return false;
                }
                switchToTab(tab);
                tab.environmentShaderDesignerPanel.reloadFromDisk();
            } else if (tab.isMaterialDesignerTab) {
                if (tab.materialDesignerPanel == null) {
                    return false;
                }
                switchToTab(tab);
                tab.materialDesignerPanel.reloadFromDisk();
            } else if (tab.isAnimationImportTab) {
                return false;
            } else {
                File file = new File(tab.filePath);
                if (!file.isFile()) {
                    return false;
                }
                String content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
                tab.content = content;
                if (activate || tab == activeTab) {
                    switchToTab(tab);
                    suppressDocumentEvents = true;
                    textArea.setText(content);
                    textAreaRTL.setText(content);
                    suppressDocumentEvents = false;
                }
            }

            tab.dirty = false;
            TabButton btn = tabButtons.get(tab.filePath);
            if (btn != null) {
                btn.updateTitle();
            }
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to reload from disk: " + ex.getMessage(),
                    "Reload Error",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void discardTabEditorState(TabData tab) {
        if (tab == null) {
            return;
        }
        tab.dirty = false;
        if (tab.isDesignerTab && tab.designerPanel != null) {
            tab.designerPanel.discardEditorState();
        } else if (tab.isUIDesignerTab && tab.uiDesignerPanel != null) {
            tab.uiDesignerPanel.discardEditorState();
        } else if (tab.isEffekseerDesignerTab && tab.effekseerDesignerPanel != null) {
            tab.effekseerDesignerPanel.discardEditorState();
        } else if (tab.isShaderDesignerTab && tab.shaderDesignerPanel != null) {
            tab.shaderDesignerPanel.discardEditorState();
        } else if (tab.isEnvironmentShaderDesignerTab && tab.environmentShaderDesignerPanel != null) {
            tab.environmentShaderDesignerPanel.discardEditorState();
        } else if (tab.isMaterialDesignerTab && tab.materialDesignerPanel != null) {
            tab.materialDesignerPanel.discardEditorState();
        }
    }

    private TabData findTabByPath(String filePath) {
        if (filePath == null) {
            return null;
        }
        String normalizedPath = new File(filePath).getAbsolutePath();
        for (TabData td : tabs) {
            if (td.filePath.equals(normalizedPath)) {
                return td;
            }
        }
        return null;
    }

    private boolean isTextEditorTab(TabData tab) {
        return tab != null
                && !tab.isDesignerTab
                && !tab.isUIDesignerTab
                && !tab.isEffekseerDesignerTab
                && !tab.isShaderDesignerTab
                && !tab.isEnvironmentShaderDesignerTab
                && !tab.isMaterialDesignerTab
                && !tab.isAnimationImportTab;
    }

    private String getTabKind(TabData tab) {
        if (tab == null) {
            return null;
        }
        if (tab.isDesignerTab) {
            return "scene_designer";
        }
        if (tab.isUIDesignerTab) {
            return "ui_designer";
        }
        if (tab.isEffekseerDesignerTab) {
            return "effect_designer";
        }
        if (tab.isShaderDesignerTab) {
            return "shader_designer";
        }
        if (tab.isEnvironmentShaderDesignerTab) {
            return "environment_shader_designer";
        }
        if (tab.isMaterialDesignerTab) {
            return "material_designer";
        }
        if (tab.isAnimationImportTab) {
            return "animation_import";
        }
        return "text";
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
            if (deleting && toClose.isEffekseerDesignerTab && toClose.effekseerDesignerPanel != null) {
                toClose.effekseerDesignerPanel.clearAndDeactivatePanel();
                toClose.effekseerDesignerPanel = null;
                toClose.isEffekseerDesignerTab = false;
            }
            if (deleting && toClose.isShaderDesignerTab && toClose.shaderDesignerPanel != null) {
                toClose.shaderDesignerPanel.clearAndDeactivatePanel();
                toClose.shaderDesignerPanel = null;
                toClose.isShaderDesignerTab = false;
            }
            if (deleting && toClose.isEnvironmentShaderDesignerTab && toClose.environmentShaderDesignerPanel != null) {
                toClose.environmentShaderDesignerPanel.clearAndDeactivatePanel();
                toClose.environmentShaderDesignerPanel = null;
                toClose.isEnvironmentShaderDesignerTab = false;
            }
            if (deleting && toClose.isMaterialDesignerTab && toClose.materialDesignerPanel != null) {
                toClose.materialDesignerPanel.clearAndDeactivatePanel();
                toClose.materialDesignerPanel = null;
                toClose.isMaterialDesignerTab = false;
            }
            if (deleting && toClose.isAnimationImportTab) {
                toClose.animationImportPanel = null;
                toClose.isAnimationImportTab = false;
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

    public File captureActiveTabSnapshot(File outputFile, int width, int height) throws Exception {
        if (outputFile == null) {
            throw new IOException("Output file is required.");
        }
        Component target = centerContainer.getComponentCount() > 0 ? centerContainer.getComponent(0) : editorPane;
        if (target == null || !target.isShowing() || target.getWidth() <= 0 || target.getHeight() <= 0) {
            throw new IOException("The active tab is not visible, so no snapshot could be captured.");
        }

        BufferedImage capture = new BufferedImage(target.getWidth(), target.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D captureGraphics = capture.createGraphics();
        captureGraphics.setColor(target.getBackground() != null ? target.getBackground() : UIManager.getColor("Panel.background"));
        captureGraphics.fillRect(0, 0, capture.getWidth(), capture.getHeight());
        // Paint the active SceneMax tab directly so other OS windows cannot contaminate the capture.
        target.printAll(captureGraphics);
        captureGraphics.dispose();

        BufferedImage finalImage = capture;
        if (width > 0 && height > 0 && (capture.getWidth() != width || capture.getHeight() != height)) {
            BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = scaled.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.drawImage(capture, 0, 0, width, height, null);
            g2.dispose();
            finalImage = scaled;
        }

        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        ImageIO.write(finalImage, "png", outputFile);
        return outputFile;
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
