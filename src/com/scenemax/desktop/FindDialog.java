package com.scenemax.desktop;

import org.apache.commons.io.FileUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class FindDialog extends JDialog {

    private final MainApp mainApp;
    private final List<String> files;

    private JTextField txtFind;
    private JTextField txtReplace;
    private JCheckBox chkCaseSensitive;
    private JLabel lblStatus;

    private int searchFileIndex;
    private int searchIndex;
    private boolean hasWrapped;

    public FindDialog(MainApp mainApp, String activeFilePath, List<String> files, String selectedText) {
        this.mainApp = mainApp;
        this.files = files;

        // Start searching from the active file
        searchFileIndex = 0;
        if (activeFilePath != null) {
            for (int i = 0; i < files.size(); i++) {
                if (files.get(i).equals(activeFilePath)) {
                    searchFileIndex = i;
                    break;
                }
            }
        }
        searchIndex = 0;
        hasWrapped = false;

        buildUI(selectedText);

        // Close on ESCAPE
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private void buildUI(String selectedText) {
        setTitle("Find and Replace");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel contentPane = new JPanel(new BorderLayout(8, 8));
        contentPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setContentPane(contentPane);

        // --- Top: Find/Replace fields ---
        JPanel fieldsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);

        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.EAST;
        fieldsPanel.add(new JLabel("Find:"), gbc);

        txtFind = new JTextField(30);
        if (selectedText != null && !selectedText.isEmpty()) {
            txtFind.setText(selectedText);
        }
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        fieldsPanel.add(txtFind, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        fieldsPanel.add(new JLabel("Replace:"), gbc);

        txtReplace = new JTextField(30);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        fieldsPanel.add(txtReplace, gbc);

        chkCaseSensitive = new JCheckBox("Case sensitive");
        gbc.gridx = 1; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        fieldsPanel.add(chkCaseSensitive, gbc);

        contentPane.add(fieldsPanel, BorderLayout.NORTH);

        // --- Status label ---
        lblStatus = new JLabel(" ");
        lblStatus.setHorizontalAlignment(SwingConstants.CENTER);
        contentPane.add(lblStatus, BorderLayout.CENTER);

        // --- Bottom: Buttons ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));

        JButton btnPrev = new JButton("Find Prev");
        JButton btnNext = new JButton("Find Next");
        JButton btnReplace = new JButton("Replace");
        JButton btnReplaceAll = new JButton("Replace All");
        JButton btnExit = new JButton("Exit");

        buttonPanel.add(btnPrev);
        buttonPanel.add(btnNext);
        buttonPanel.add(btnReplace);
        buttonPanel.add(btnReplaceAll);
        buttonPanel.add(btnExit);

        contentPane.add(buttonPanel, BorderLayout.SOUTH);

        // Default button
        getRootPane().setDefaultButton(btnNext);

        // Actions
        btnNext.addActionListener(e -> findNext());
        btnPrev.addActionListener(e -> findPrev());
        btnReplace.addActionListener(e -> replaceCurrent());
        btnReplaceAll.addActionListener(e -> replaceAll());
        btnExit.addActionListener(e -> dispose());

        // Enter in find field triggers Find Next
        txtFind.addActionListener(e -> findNext());
    }

    private String getSearchText() {
        return txtFind.getText();
    }

    private boolean isCaseSensitive() {
        return chkCaseSensitive.isSelected();
    }

    /**
     * Gets the content for the file at the given index.
     * For the active editor tab, reads from the editor (includes unsaved changes).
     * For other files, reads from disk.
     */
    private String getFileContent(int fileIndex) {
        String path = files.get(fileIndex);
        String activeFilePath = mainApp.getActiveFilePath();

        // If this is the active file, read from the editor to include unsaved changes
        if (activeFilePath != null && activeFilePath.equals(path)) {
            String editorText = mainApp.getActiveEditorText();
            if (editorText != null) {
                return editorText;
            }
        }

        // Read from disk
        try {
            return FileUtils.readFileToString(new File(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    private int indexOf(String content, String search, int fromIndex) {
        if (isCaseSensitive()) {
            return content.indexOf(search, fromIndex);
        } else {
            return content.toLowerCase().indexOf(search.toLowerCase(), fromIndex);
        }
    }

    private int lastIndexOf(String content, String search, int fromIndex) {
        if (isCaseSensitive()) {
            // String.lastIndexOf searches backwards from fromIndex
            return content.lastIndexOf(search, fromIndex);
        } else {
            return content.toLowerCase().lastIndexOf(search.toLowerCase(), fromIndex);
        }
    }

    private void findNext() {
        String searchText = getSearchText();
        if (searchText.isEmpty()) return;

        int startFileIndex = searchFileIndex;
        int startPos = searchIndex;

        // Search in the current file from current position
        for (int i = 0; i < files.size(); i++) {
            int fileIdx = (startFileIndex + i) % files.size();
            String content = getFileContent(fileIdx);

            int searchFrom = (fileIdx == startFileIndex) ? startPos : 0;
            int pos = indexOf(content, searchText, searchFrom);

            if (pos != -1) {
                searchFileIndex = fileIdx;
                searchIndex = pos + searchText.length();
                hasWrapped = false;

                String fileName = new File(files.get(fileIdx)).getName();
                lblStatus.setText("Found in: " + fileName);
                mainApp.showTextInFile(files.get(fileIdx), pos, searchText.length());
                return;
            }
        }

        // Not found anywhere
        lblStatus.setText("No matches found");
        Toolkit.getDefaultToolkit().beep();
    }

    private void findPrev() {
        String searchText = getSearchText();
        if (searchText.isEmpty()) return;

        int startFileIndex = searchFileIndex;
        // Search backwards: start from one character before current match start
        int startPos = searchIndex - searchText.length() - 1;

        for (int i = 0; i < files.size(); i++) {
            int fileIdx = (startFileIndex - i + files.size()) % files.size();
            String content = getFileContent(fileIdx);

            int searchFrom;
            if (i == 0) {
                searchFrom = Math.max(startPos, -1);
            } else {
                searchFrom = content.length();
            }

            int pos = lastIndexOf(content, searchText, searchFrom);

            if (pos != -1) {
                searchFileIndex = fileIdx;
                searchIndex = pos + searchText.length();
                hasWrapped = false;

                String fileName = new File(files.get(fileIdx)).getName();
                lblStatus.setText("Found in: " + fileName);
                mainApp.showTextInFile(files.get(fileIdx), pos, searchText.length());
                return;
            }
        }

        // Not found anywhere
        lblStatus.setText("No matches found");
        Toolkit.getDefaultToolkit().beep();
    }

    private void replaceCurrent() {
        String searchText = getSearchText();
        String replaceText = txtReplace.getText();
        if (searchText.isEmpty()) return;

        // Check if there's currently a selection matching the search text
        String activeFilePath = mainApp.getActiveFilePath();
        if (activeFilePath == null) return;

        String editorText = mainApp.getActiveEditorText();
        if (editorText == null) return;

        int selStart = mainApp.getActiveSelectionStart();
        int selEnd = mainApp.getActiveSelectionEnd();

        if (selStart >= 0 && selEnd > selStart) {
            String selected = editorText.substring(selStart, selEnd);
            boolean matches = isCaseSensitive()
                    ? selected.equals(searchText)
                    : selected.equalsIgnoreCase(searchText);

            if (matches) {
                mainApp.replaceActiveSelection(replaceText);

                // Adjust search position after replacement
                searchIndex = selStart + replaceText.length();

                lblStatus.setText("Replaced. Finding next...");
                // Automatically find next
                findNext();
                return;
            }
        }

        // No current match selected, just find next
        findNext();
    }

    private void replaceAll() {
        String searchText = getSearchText();
        String replaceText = txtReplace.getText();
        if (searchText.isEmpty()) return;

        int totalCount = 0;

        for (int fileIdx = 0; fileIdx < files.size(); fileIdx++) {
            String filePath = files.get(fileIdx);
            String content = getFileContent(fileIdx);
            String originalContent = content;

            // Perform all replacements in this file
            StringBuilder sb = new StringBuilder();
            int fileCount = 0;
            int pos = 0;

            while (pos < content.length()) {
                int found = indexOf(content, searchText, pos);
                if (found == -1) {
                    sb.append(content.substring(pos));
                    break;
                }
                sb.append(content, pos, found);
                sb.append(replaceText);
                fileCount++;
                pos = found + searchText.length();
            }

            if (fileCount > 0) {
                totalCount += fileCount;
                String newContent = sb.toString();

                // Write to disk
                try {
                    File f = new File(filePath);
                    String normalized = newContent.replaceAll("\r", "");
                    FileUtils.writeStringToFile(f, normalized, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // If this is the active file, update the editor
                String activeFilePath = mainApp.getActiveFilePath();
                if (activeFilePath != null && activeFilePath.equals(filePath)) {
                    mainApp.setActiveEditorText(newContent);
                }
            }
        }

        if (totalCount > 0) {
            lblStatus.setText("Replaced " + totalCount + " occurrence" + (totalCount != 1 ? "s" : ""));
        } else {
            lblStatus.setText("No matches found");
            Toolkit.getDefaultToolkit().beep();
        }
    }
}
