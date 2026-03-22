package com.scenemax.desktop;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FindDialog extends JDialog {

    private final File currentFile;
    private final List<String> files;
    private final MainApp mainApp;

    private JPanel contentPane;
    private JButton btnNext;
    private JButton buttonCancel;
    private JTextField txtFind;
    private JButton btnPrev;
    private JButton btnSearch;
    private JSONObject searchResult;
    private int searchIndex;
    private int searchFileIndex = 0;
    private JSONArray searchResultIndex;
    private int searchResultLength;
    private String lastSearch = "";


    public FindDialog(MainApp mainApp, File currentFile, List<String> files, String selectedText) {
        setContentPane(contentPane);
        //
        getRootPane().setDefaultButton(btnNext);

        this.currentFile = currentFile;
        this.files = files;
        this.mainApp = mainApp;

        if (selectedText != null) {
            txtFind.setText(selectedText);
        }

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

        btnPrev.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                findPrev();
            }
        });
        btnNext.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                findNext();
            }
        });

    }

    private void findPrev() {
        find(-1);
    }

//    private void findPrev() {
//
//        initSearch();
//
//        searchIndex--;
//        if (searchIndex < 0) {
//            searchIndex = searchResultIndex.length() - 1;
//        }
//
//        showItem();
//
//    }

    private void find(int dir) {
        String searchText = txtFind.getText().trim().toLowerCase();
        if (searchText.length() == 0) {
            return;
        }

        String path = files.get(searchFileIndex);
        File f = new File(path);

        try {
            String code = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
            code = code.toLowerCase();
            int pos = code.indexOf(searchText, searchIndex);
            if (pos == -1) {
                searchFileIndex = searchFileIndex + 1 * dir;
                searchIndex = 0;
                if (dir == 1) {
                    if (searchFileIndex >= files.size()) {
                        searchFileIndex = 0;
                        find(dir);
                    }
                } else {
                    if (searchFileIndex <= 0) {
                        searchFileIndex = files.size() - 1;
                        find(dir);
                    }
                }
            } else {
                int selectLength = searchText.length();
                mainApp.showTextInFile(path, pos, selectLength);
                searchIndex = pos + selectLength;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void findNext() {
        find(1);
    }

//    private void findNext() {
//
//        if (txtFind.getText().trim().length() == 0) {
//            return;
//        }
//
//        initSearch();
//
//        searchIndex++;
//        if (searchIndex > searchResultIndex.length() - 1) {
//            searchIndex = 0;
//        }
//
//        showItem();
//
//    }

//    private void initSearch() {
//        if (searchResultIndex == null || !lastSearch.equals(txtFind.getText().trim().toLowerCase())) {
//            lastSearch = txtFind.getText().toLowerCase();
//            searchResult = search();
//            searchResultIndex = searchResult.getJSONArray("index");
//            searchResultLength = searchResult.getInt("length");
//            searchIndex = searchResult.getInt("findStartIndex") - 1;
//
//        }
//
//    }

    private void showItem() {
        JSONObject item = searchResultIndex.getJSONObject(searchIndex);
        String folder = item.getString("folder");
        String file = item.getString("file");
        int index = item.getInt("index");
        mainApp.showTextInFile(folder, file, index, searchResultLength);
    }

//    private JSONObject search() {
//
//        String search = txtFind.getText().trim().toLowerCase();
//        String currFileName = currentFile.getName();
//
//        JSONArray resultIndex = new JSONArray();
//        JSONObject findResult = new JSONObject();
//        findResult.put("length", search.length());
//        findResult.put("index", resultIndex);
//        int findStartIndex = -1;
//
//        String folderName = currentFile.getParentFile().getName();
//        List<String> sortedFiles = new ArrayList<>(files.keySet());
//        Collections.sort(sortedFiles);
//
//        for (String key : sortedFiles) {
//            String content = files.getString(key).toLowerCase();
//            int startIndex = 0, counter = 0;
//
//            int index = content.indexOf(search, startIndex);
//            while (index != -1) {
//
//                JSONObject item = new JSONObject();
//                item.put("folder", folderName);
//                item.put("file", key);
//                item.put("index", index);
//                resultIndex.put(item);
//
//                // we want the user to see first the results from the current selected file
//                if (findStartIndex == -1 && currFileName.equals(key)) {
//                    findStartIndex = resultIndex.length() - 1;
//                }
//
//                startIndex = index + search.length();
//                index = content.indexOf(search, startIndex);
//            }
//
//        }
//
//        findResult.put("findStartIndex", findStartIndex);
//        return findResult;
//
//    }

    private void onOK() {
        // add your code here
        dispose();
    }

    private void onCancel() {
        // add your code here if necessary
        dispose();
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
        panel1.setLayout(new GridLayoutManager(1, 4, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(panel2, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        buttonCancel = new JButton();
        buttonCancel.setText("Exit");
        panel2.add(buttonCancel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnPrev = new JButton();
        btnPrev.setText("Prev");
        panel1.add(btnPrev, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnNext = new JButton();
        btnNext.setText("Next");
        panel1.add(btnNext, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("Find:");
        panel3.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel3.add(spacer2, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        txtFind = new JTextField();
        panel3.add(txtFind, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }

}
