package com.scenemax.desktop;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.scenemaxeng.compiler.Utils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.swing.*;
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

public class GroupManagementDialog extends JDialog implements IServerEvents {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextField txtGroupName;
    private JButton btnConnect;
    private JButton btnDisconnect;
    private JTextField txtNickName;
    private JLabel lblNickName;
    private JLabel lblConnectMsg;
    private JPasswordField txtPwd;
    private JLabel lblModPwd;
    private JTree treeRoomMembers;

    public GroupManagementDialog() {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(btnConnect);

        WebCommunication.getInstance().subscribe(this);
        //lstGroupMembers.setListData(WebCommunication.getInstance().getRoomMembers().toArray());
        showRoomMembers();
        String lastRoomName = AppDB.getInstance().getParam("last_joined_room");
        txtNickName.setText(AppDB.getInstance().getParam("last_nick_name"));

        //String joinedRoom = AppDB.getInstance().getParam("joined_room");
        if (lastRoomName != null && lastRoomName.length() > 0) {
            txtGroupName.setText(lastRoomName);
        }

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

        btnDisconnect.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                btnDisconnect.setEnabled(false);
                GroupManagementDialog.this.disconnectFromClassroom();
            }
        });

        btnConnect.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                GroupManagementDialog.this.connectToClassroom();
            }
        });

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        refreshDialogState();


        treeRoomMembers.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {

                treeRoomMembers.setSelectionPath(e.getPath());
            }
        });


        treeRoomMembers.addMouseListener(new MouseAdapter() {

            public void myPopupEvent(MouseEvent e) {

                if (SwingUtilities.isRightMouseButton(e)) {
                    int x = e.getX();
                    int y = e.getY();

                    TreePath tp = treeRoomMembers.getPathForLocation(x, y);
                    if (tp == null)
                        return;

                    treeRoomMembers.setSelectionPath(tp);
                    String name = tp.getLastPathComponent().toString();

                    DefaultMutableTreeNode obj = (DefaultMutableTreeNode) treeRoomMembers.getLastSelectedPathComponent();
                    if (!(obj.getUserObject() instanceof RoomMember)) {
                        JPopupMenu popup = new JPopupMenu();
                        JMenuItem item = new JMenuItem("Pull User Folder");
                        item.setActionCommand("view_folder");
                        item.addActionListener(createRoomMemberActionsListener());
                        popup.add(item);
                        popup.show(treeRoomMembers, x, y);
                    } else {

                        JPopupMenu popup = new JPopupMenu();
                        JMenuItem item = new JMenuItem("View User Folders");

                        RoomMember rm = WebCommunication.getInstance().getThisMember();
                        item.setEnabled(rm.isModerator() && !rm.toString().equals(name));
                        item.setActionCommand("view_user_folders");
                        item.addActionListener(createRoomMemberActionsListener());
                        popup.add(item);
                        popup.show(treeRoomMembers, x, y);

                    }
                }
            }

            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) myPopupEvent(e);
            }

            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) myPopupEvent(e);
            }

        });
    }

    private void showRoomMembers() {

        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeRoomMembers.getModel().getRoot();
        root.removeAllChildren();
        treeRoomMembers.setRootVisible(false);
        treeRoomMembers.setShowsRootHandles(true);

        for (RoomMember rm : WebCommunication.getInstance().getRoomMembers()) {
            DefaultMutableTreeNode child = new DefaultMutableTreeNode(rm);
            root.add(child);
        }
        //

        treeRoomMembers.updateUI();
    }

    private ActionListener createRoomMemberActionsListener() {

        ActionListener popupActionListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                String cmd = actionEvent.getActionCommand();

                if (cmd.equals("view_folder")) {
                    DefaultMutableTreeNode obj = (DefaultMutableTreeNode) treeRoomMembers.getLastSelectedPathComponent();
                    RoomMember rm = (RoomMember) ((DefaultMutableTreeNode) obj.getParent()).getUserObject();

                    String room = AppDB.getInstance().getParam("joined_room");
                    String folderName = obj.toString();
                    folderName = folderName.substring(0, folderName.indexOf(":"));
                    folderName = folderName.trim();
                    WebCommunication.getInstance().askForUserFolderContent(room, rm, folderName);

                } else if (cmd.equals("view_user_folders")) {

                    DefaultMutableTreeNode obj = (DefaultMutableTreeNode) treeRoomMembers.getLastSelectedPathComponent();
                    RoomMember rm = (RoomMember) obj.getUserObject();
                    String room = AppDB.getInstance().getParam("joined_room");
                    WebCommunication.getInstance().askForUserFolders(room, rm);

                }
            }

        };

        return popupActionListener;

    }


    public void refreshDialogState() {
        lblConnectMsg.setText("");
        txtNickName.setText(AppDB.getInstance().getParam("last_nick_name"));
        String room = AppDB.getInstance().getParam("joined_room");
        if (room != null && room.length() > 0) {
            btnDisconnect.setEnabled(true);
            btnConnect.setEnabled(false);
        } else {
            btnDisconnect.setEnabled(false);
            btnConnect.setEnabled(true);
        }

        this.pack();
    }

    private void disconnectFromClassroom() {

        String room = AppDB.getInstance().getParam("joined_room");
        if (room != null && room.length() > 0) {
            WebCommunication.getInstance().leaveRoom(room);
        }
    }

    private void connectToClassroom() {

        String room = txtGroupName.getText().trim();
        String nickName = txtNickName.getText().trim();
        String pwd = txtPwd.getText().trim();

        if (room.length() == 0) {
            JOptionPane.showMessageDialog(null,
                    "Classroom name cannot be empty");
            return;
        }

        if (nickName.length() == 0) {
            JOptionPane.showMessageDialog(null,
                    "Nick name cannot be empty");
            return;
        }

        btnConnect.setEnabled(false);

        WebCommunication.getInstance().connect();
        WebCommunication.getInstance().joinRoom(room, nickName, pwd);
    }

    private void onOK() {
        // add your code here
        dispose();
    }

    private void onCancel() {

        dispose();
    }

    public static void main(String[] args) {
        GroupManagementDialog dialog = new GroupManagementDialog();
        dialog.pack();
        dialog.setVisible(true);
        System.exit(0);
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

        if (event.equals("connect")) {
            lblConnectMsg.setText("Connected to classrooms server");
            lblConnectMsg.setForeground(Color.BLACK);
            this.pack();
        } else if (event.equals("error-connect")) {
            lblConnectMsg.setText("Connect error. Check network connection");
            lblConnectMsg.setForeground(Color.RED);
            this.pack();
        } else if (event.equals("join-room-error")) {
            final String err = data.getString("err");
            lblConnectMsg.setText("Join room failed - " + err);
            lblConnectMsg.setForeground(Color.RED);
            btnConnect.setEnabled(true);
            this.pack();
        } else if (event.equals("user-joined")) {

            try {
                final String room = data.getString("room");
                if (!ownSocket) {// here i'm waiting for my own user-joined ack
                    return;
                }

                lblConnectMsg.setText("Join room succeeded.");
                lblConnectMsg.setForeground(Color.BLACK);
                this.pack();
                AppDB.getInstance().setParam("last_nick_name", txtNickName.getText().trim());
                AppDB.getInstance().setParam("joined_room", room);
                AppDB.getInstance().setParam("last_joined_room", room);

                showRoomMembers();

                //dispose();
            } catch (JSONException e) {
                e.printStackTrace();
            }

        } else if (event.equals("user-left")) {

            showRoomMembers();

            if (!ownSocket) {
                return;
            }

            //AppDB.getInstance().setParam("last_nick_name","");
            String room = AppDB.getInstance().getParam("joined_room");
            AppDB.getInstance().setParam("joined_room", "");

            JOptionPane.showMessageDialog(null,
                    "Success leaving room: " + room);

            WebCommunication.getInstance().clearRoomMembers();

            dispose();

        } else if (event.equals("push-request")) {

            if (ownSocket) {
                return;
            }

            String sendingUser = data.getString("sendingUserName");
            JSONObject folders = data.getJSONObject("folders");

            TreePath tp = find((DefaultMutableTreeNode) treeRoomMembers.getModel().getRoot(), sendingUser);
            if (tp != null) {
                treeRoomMembers.setSelectionPath(tp);
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) treeRoomMembers.getLastSelectedPathComponent();
                //RoomMember rm = (RoomMember)node.getUserObject();

                for (String key : folders.keySet()) {
                    int count = folders.getInt(key);
                    DefaultMutableTreeNode child = new DefaultMutableTreeNode(key + ": " + count);
                    node.add(child);
                }

                treeRoomMembers.updateUI();
            }
        }

    }

    private TreePath find(DefaultMutableTreeNode root, String s) {
        Enumeration<TreeNode> e = root.depthFirstEnumeration();
        while (e.hasMoreElements()) {
            TreeNode node = e.nextElement();
            if (node.toString().equalsIgnoreCase(s)) {
                return Utils.getPath(node);
            }
        }
        return null;
    }

    @Override
    public void dispose() {
        WebCommunication.getInstance().unsubscribe(this);
        super.dispose();
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
        contentPane.setLayout(new GridLayoutManager(4, 1, new Insets(10, 10, 10, 10), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel1, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1, true, false));
        panel1.add(panel2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        buttonOK = new JButton();
        buttonOK.setText("OK");
        panel2.add(buttonOK, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonCancel = new JButton();
        buttonCancel.setText("Exit");
        panel2.add(buttonCancel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(5, 3, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel3.add(spacer2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        txtGroupName = new JTextField();
        Font txtGroupNameFont = this.$$$getFont$$$(null, -1, 18, txtGroupName.getFont());
        if (txtGroupNameFont != null) txtGroupName.setFont(txtGroupNameFont);
        txtGroupName.setText("public-world");
        panel3.add(txtGroupName, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(2, 3, new Insets(0, 0, 0, 0), -1, -1));
        panel3.add(panel4, new GridConstraints(3, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        btnConnect = new JButton();
        btnConnect.setText("Connect");
        panel4.add(btnConnect, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer3 = new Spacer();
        panel4.add(spacer3, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        btnDisconnect = new JButton();
        btnDisconnect.setEnabled(false);
        btnDisconnect.setText("Disconnect");
        panel4.add(btnDisconnect, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer4 = new Spacer();
        panel4.add(spacer4, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("Classroom:");
        panel3.add(label1, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtNickName = new JTextField();
        Font txtNickNameFont = this.$$$getFont$$$(null, -1, 18, txtNickName.getFont());
        if (txtNickNameFont != null) txtNickName.setFont(txtNickNameFont);
        panel3.add(txtNickName, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        lblNickName = new JLabel();
        lblNickName.setText("Nick Name:");
        panel3.add(lblNickName, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        lblConnectMsg = new JLabel();
        lblConnectMsg.setText("");
        panel3.add(lblConnectMsg, new GridConstraints(4, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        txtPwd = new JPasswordField();
        panel3.add(txtPwd, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        lblModPwd = new JLabel();
        lblModPwd.setText("Password (optional):");
        panel3.add(lblModPwd, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel5, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JScrollPane scrollPane1 = new JScrollPane();
        contentPane.add(scrollPane1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(400, 400), null, 0, false));
        treeRoomMembers = new JTree();
        Font treeRoomMembersFont = this.$$$getFont$$$(null, -1, 20, treeRoomMembers.getFont());
        if (treeRoomMembersFont != null) treeRoomMembers.setFont(treeRoomMembersFont);
        scrollPane1.setViewportView(treeRoomMembers);
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
