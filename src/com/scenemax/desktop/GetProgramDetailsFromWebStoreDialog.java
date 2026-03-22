package com.scenemax.desktop;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class GetProgramDetailsFromWebStoreDialog extends JDialog {
    private JPanel contentPane;
    private JButton btnDownload;
    private JButton buttonCancel;
    private JTextField txtName;
    private JTextPane textPaneDesc;
    private JTextPane textPaneShortDesc;
    private JTextField txtDateCreated;
    private JPanel panelImage;
    private DownloadFileTask task;
    private String url = null;
    private Runnable done;
    private String downloadFile;

    public GetProgramDetailsFromWebStoreDialog(String url, final Runnable done) {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(btnDownload);

        this.url = url;
        this.done = done;
        //this.setMinimumSize(new Dimension(600,600));
        this.setPreferredSize(new Dimension(600, 600));
        getProgramDetailsFromWebStore();


        btnDownload.addActionListener(new ActionListener() {
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
    }


    private String getDownloadFile(JSONObject item) {
        JSONArray downloads = item.getJSONArray("downloads");
        if (downloads.length() > 0) {
            JSONObject file = downloads.getJSONObject(0);
            return file.getString("file");
        } else {
            return null;
        }
    }

    private void getProgramDetailsFromWebStore() {

        String sku = url.trim();

        WooProduct item = Util.getProductFromWebStore("sku", sku);
        if (item != null) {

            txtName.setText(item.name);
            textPaneDesc.setText(item.desc);
            textPaneShortDesc.setText(item.shortDesc);
            txtDateCreated.setText(item.dateCreated);
            this.downloadFile = item.downloadFile;

            showProductImage(item.json);
            //this.pack();

        } else {
            return;
        }

    }

    private void showProductImage(JSONObject item) {

        String src = null;
        JSONArray images = item.getJSONArray("images");
        if (images.length() > 0) {
            JSONObject file = images.getJSONObject(0);
            src = file.getString("src");
        } else {
            return;
        }

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        Image image = null;
        URL url = null;
        try {
            url = new URL(src);
            image = ImageIO.read(url);
        } catch (MalformedURLException ex) {
            System.out.println("Malformed URL");
        } catch (IOException iox) {
            System.out.println("Can not load file");
        }
        JLabel label = new JLabel(new ImageIcon(image));
        panel.add(label, BorderLayout.CENTER);

        this.panelImage.add(panel, BorderLayout.CENTER);

    }

    private void onOK() {

        btnDownload.setEnabled(false);
        buttonCancel.setEnabled(false);

        task = new DownloadFileTask(GetProgramDetailsFromWebStoreDialog.this.downloadFile, new Runnable() {
            @Override
            public void run() {
                if (task.error != null) {
                    JOptionPane.showMessageDialog(null, task.error, "Download Error", JOptionPane.INFORMATION_MESSAGE);
                } else if (task.message != null) {
                    JOptionPane.showMessageDialog(null, task.message, "Download Information", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(null, "Download finished successfully\r\n" +
                            "Any replaced files were marked with '.bkup' extension", "Download Information", JOptionPane.INFORMATION_MESSAGE);
                }

                GetProgramDetailsFromWebStoreDialog.this.dispose();
                done.run();
            }
        });

        task.execute();

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
        contentPane.setLayout(new GridLayoutManager(2, 3, new Insets(10, 10, 10, 10), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel1, new GridConstraints(1, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1, true, false));
        panel1.add(panel2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        btnDownload = new JButton();
        btnDownload.setText("Download");
        panel2.add(btnDownload, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonCancel = new JButton();
        buttonCancel.setText("Cancel");
        panel2.add(buttonCancel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(5, 3, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel3, new GridConstraints(0, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("Name:");
        panel3.add(label1, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtName = new JTextField();
        panel3.add(txtName, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Description:");
        panel3.add(label2, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        textPaneDesc = new JTextPane();
        textPaneDesc.setContentType("text/html");
        textPaneDesc.setEditable(false);
        panel3.add(textPaneDesc, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(150, 50), null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("Short Desc:");
        panel3.add(label3, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        textPaneShortDesc = new JTextPane();
        textPaneShortDesc.setContentType("text/html");
        textPaneShortDesc.setEditable(false);
        panel3.add(textPaneShortDesc, new GridConstraints(3, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(150, 50), null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("Date Created:");
        panel3.add(label4, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtDateCreated = new JTextField();
        panel3.add(txtDateCreated, new GridConstraints(4, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        panelImage = new JPanel();
        panelImage.setLayout(new BorderLayout(0, 0));
        panel3.add(panelImage, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }

}
