package com.scenemax.desktop;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.scenemaxeng.compiler.Utils;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

public class ActiveQuestionsDialog extends JDialog implements KeyListener, IServerEvents {
    private JPanel contentPane;
    private JButton btnSendAnswer;
    private JButton buttonCancel;
    private JPanel editorHostPanel;
    private JLabel lblQuestionText;
    private JTree treeQuestions;
    private List<JSONObject> roomQuestions;
    private RSyntaxTextArea textArea;
    private Question _activeQuestion = null;
    private String selectedParent;
    private String selectedChildNode;


    public ActiveQuestionsDialog() {

        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(btnSendAnswer);

        WebCommunication.getInstance().subscribe(this);

        btnSendAnswer.addActionListener(new ActionListener() {
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

        textArea = new RSyntaxTextArea(20, 60);
        textArea.addKeyListener(this);
        textArea.getFont().getSize();
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        Util.applyDarkTheme(textArea);
        textArea.setCodeFoldingEnabled(true);
        RTextScrollPane sp = new RTextScrollPane(textArea);
        editorHostPanel.add(sp);
        Font font = textArea.getFont();
        float size = font.getSize() + 6.0f;
        if (size < 72) {
            textArea.setFont(font.deriveFont(size));
        }

        addQuestionSelectionListener();

    }

    private void addQuestionSelectionListener() {

        treeQuestions.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                showSelectedNodeContent();
            }
        });

        treeQuestions.addMouseListener(new MouseAdapter() {

            public void mouseClickHandler(MouseEvent e) {

                int x = e.getX();
                int y = e.getY();

                TreePath tp = treeQuestions.getPathForLocation(x, y);
                if (tp == null)
                    return;

                treeQuestions.setSelectionPath(tp);

            }

            public void mousePressed(MouseEvent e) {
                mouseClickHandler(e);
            }

        });

    }

    protected void showSelectedNodeContent() {

        RoomMember rm = WebCommunication.getInstance().getThisMember();
        Question activeQuestion = null;

        DefaultMutableTreeNode obj = (DefaultMutableTreeNode) treeQuestions.getLastSelectedPathComponent();
        if (obj.getUserObject() instanceof TreeNodeQuestionFileObject) {
            TreeNodeQuestionFileObject tn = (TreeNodeQuestionFileObject) obj.getUserObject();
            textArea.setText(tn.data.toString());
            String folder = tn.question.folder == null ? "" : "Folder: " + tn.question.folder + " - ";
            lblQuestionText.setText(folder + tn.question.question);
            _activeQuestion = tn.question;
            activeQuestion = tn.question;
        } else {
            Question q = (Question) obj.getUserObject();
            lblQuestionText.setText(q.question);
            activeQuestion = q;
        }

        btnSendAnswer.setEnabled(!activeQuestion.sourceId.equals(rm.getUserId()));

    }

    @Override
    public void keyTyped(KeyEvent e) {

    }

    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_EQUALS && e.isControlDown()) {
            Font font = textArea.getFont();
            float size = font.getSize() + 1.0f;
            if (size < 72) {
                textArea.setFont(font.deriveFont(size));
            }
        } else if (e.getKeyCode() == KeyEvent.VK_MINUS && e.isControlDown()) {
            Font font = textArea.getFont();
            float size = font.getSize() - 1.0f;
            if (size >= 10) {
                textArea.setFont(font.deriveFont(size));
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {

    }

    private void onOK() {

        if (_activeQuestion == null) {
            return;
        }

        String answer = JOptionPane.showInputDialog(
                null,
                "Type your answer to: " + _activeQuestion.asker,
                "Answer Question",
                JOptionPane.PLAIN_MESSAGE
        );

        if (answer == null) {
            return;
        }

        answer = answer.trim();
        String room = AppDB.getInstance().getParam("joined_room");
        DefaultMutableTreeNode fileNode = (DefaultMutableTreeNode) treeQuestions.getLastSelectedPathComponent();
        if (fileNode.getUserObject() instanceof TreeNodeQuestionFileObject) {
            TreeNodeQuestionFileObject obj = (TreeNodeQuestionFileObject) fileNode.getUserObject();
            String fileName = obj.toString();
            WebCommunication.getInstance().answerQuestion(room, _activeQuestion.sourceId, answer, textArea.getText().trim(), _activeQuestion.folder, fileName);
        } else {

        }

        //dispose();
    }

    private void onCancel() {
        // add your code here if necessary
        dispose();
    }

    @Override
    public void dispose() {
        WebCommunication.getInstance().unsubscribe(this);
        super.dispose();
    }

    public static void main(String[] args) {
        ActiveQuestionsDialog dialog = new ActiveQuestionsDialog();
        dialog.pack();
        dialog.setVisible(true);
        System.exit(0);
    }

    public void setQuestions(List<JSONObject> roomQuestions) {
        this.roomQuestions = roomQuestions;
        List<Question> questions = new ArrayList<>();
        for (int i = 0; i < roomQuestions.size(); ++i) {
            Question q = new Question();
            JSONObject item = roomQuestions.get(i);
            q.asker = item.getString("asker");
            q.question = item.getString("question");
            q.sourceId = item.getString("sourceId");
            if (item.has("folder")) {
                q.folder = item.getString("folder");
            }
            if (item.has("files")) {
                q.files = item.getJSONObject("files");
            }
            if (item.has("script")) {
                q.script = getFullSscript(item.getJSONArray("script"));
            }
            questions.add(q);
        }

        showQuestions(questions);

    }

    private String getFullSscript(JSONArray script) {
        String retval = "";
        for (int i = 0; i < script.length(); ++i) {
            retval += script.getString(i) + "\r\n";
        }

        return retval;
    }

    private void showQuestions(List<Question> questions) {

        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeQuestions.getModel().getRoot();
        root.removeAllChildren();
        treeQuestions.setRootVisible(false);
        treeQuestions.setShowsRootHandles(true);

        for (Question q : questions) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(q);
            root.add(node);

            showQuestionFiles(node, q);
        }

        treeQuestions.updateUI();

    }

    private void showQuestionFiles(DefaultMutableTreeNode node, Question q) {

        if (q.files == null || q.files.keySet().size() == 0) {
            String code = q.script;
            TreeNodeQuestionFileObject file = new TreeNodeQuestionFileObject("Active Script");
            file.data = code;
            file.question = q;
            DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(file);
            node.add(fileNode);

            return;
        }


        for (String key : q.files.keySet()) {
            String code = q.files.getString(key);
            TreeNodeQuestionFileObject file = new TreeNodeQuestionFileObject(key);
            file.data = code;
            file.question = q;
            DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(file);
            node.add(fileNode);
        }

    }


    @Override
    public void onServerResponse(String event, JSONObject data, boolean ownSocket, boolean targettingMe) {

        if (event.equals("answer-accepted")) {

            updateSelectionComponents();
            showQuestions(data);

//            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) treeQuestions.getLastSelectedPathComponent();
//            if(selectedNode==null || selectedNode.getUserObject()==null) {
//                return;
//            }

            openLastTreeNode();
        }

    }

    protected void updateSelectionComponents() {
        selectedParent = null;
        selectedChildNode = null;

        TreePath tp = treeQuestions.getSelectionPath();
        if (tp != null) {
            Object[] pathComp = tp.getPath();
            if (pathComp.length > 1) {
                selectedParent = pathComp[1].toString();
            }

            if (pathComp.length > 2) {
                selectedChildNode = pathComp[2].toString();
            }

        }
    }

    private void openLastTreeNode() {

        if (selectedChildNode == null) {
            return;
        }

        TreeNode root = selectedParent == null || selectedParent.length() == 0 ? (TreeNode) treeQuestions.getModel().getRoot() : findFolderNode(selectedParent);
        TreePath tp = find((DefaultMutableTreeNode) root, selectedChildNode);

        if (tp != null) {
            treeQuestions.setSelectionPath(tp);
            treeQuestions.scrollPathToVisible(tp);
        }

    }

    private TreeNode findFolderNode(String nodeName) {

        TreeNode root = (TreeNode) treeQuestions.getModel().getRoot();
        for (int i = 0; i < root.getChildCount(); ++i) {
            TreeNode n = root.getChildAt(i);
            if (n.toString().equals(nodeName)) {
                return n;
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

    private void showQuestions(final JSONObject room) {

        List<JSONObject> questions = new ArrayList<>();

        try {
            JSONArray members = room.getJSONArray("members");

            for (int i = 0; i < members.length(); ++i) {
                JSONObject member = members.getJSONObject(i);

                if (member.has("question") && !member.isNull("question")) {
                    JSONObject question = member.getJSONObject("question");
                    if (question != null) {
                        question.put("asker", member.get("name"));
                        questions.add(question);
                    }
                }
            }

            setQuestions(questions);

        } catch (JSONException e) {
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
        btnSendAnswer = new JButton();
        btnSendAnswer.setText("Reply");
        panel2.add(btnSendAnswer, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonCancel = new JButton();
        buttonCancel.setText("Exit");
        panel2.add(buttonCancel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JSplitPane splitPane1 = new JSplitPane();
        splitPane1.setDividerLocation(350);
        panel3.add(splitPane1, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(200, 200), null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        splitPane1.setLeftComponent(panel4);
        final JScrollPane scrollPane1 = new JScrollPane();
        panel4.add(scrollPane1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        treeQuestions = new JTree();
        Font treeQuestionsFont = this.$$$getFont$$$(null, -1, 20, treeQuestions.getFont());
        if (treeQuestionsFont != null) treeQuestions.setFont(treeQuestionsFont);
        treeQuestions.setRootVisible(false);
        scrollPane1.setViewportView(treeQuestions);
        editorHostPanel = new JPanel();
        editorHostPanel.setLayout(new BorderLayout(0, 0));
        splitPane1.setRightComponent(editorHostPanel);
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        Font panel5Font = this.$$$getFont$$$(null, -1, 18, panel5.getFont());
        if (panel5Font != null) panel5.setFont(panel5Font);
        panel3.add(panel5, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_NORTHEAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
        lblQuestionText = new JLabel();
        lblQuestionText.setText("");
        panel5.add(lblQuestionText, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel3.add(spacer2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
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
