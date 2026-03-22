package com.scenemax.desktop;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

public class ScriptsTreeCellRenderer extends DefaultTreeCellRenderer {

    private static final ImageIcon ICON_FOLDER   = new ImageIcon(ScriptsTreeCellRenderer.class.getResource("/images/folder_button2_24x24.png"));
    private static final ImageIcon ICON_CSHARP   = new ImageIcon(ScriptsTreeCellRenderer.class.getResource("/images/c_sharp_1_24x24.png"));
    private static final ImageIcon ICON_MAIN     = new ImageIcon(ScriptsTreeCellRenderer.class.getResource("/images/3d_script_2_24x24_blue.png"));
    private static final ImageIcon ICON_SCRIPT   = new ImageIcon(ScriptsTreeCellRenderer.class.getResource("/images/3d_script_2_24x24.png"));
    private static final ImageIcon ICON_DESIGNER = createDesignerIcon();

    private JLabel label;

    ScriptsTreeCellRenderer() {
        label = new JLabel();
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {

        label = (JLabel) super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        DefaultMutableTreeNode nodeObj = (DefaultMutableTreeNode) value;
        Object path = nodeObj.getUserObject();
        if (path instanceof ScriptPathNode) {
            ScriptPathNode spn = (ScriptPathNode) path;
            if (spn.isFolder) {
                label.setIcon(ICON_FOLDER);
                return label;
            }
        }

        String name = value.toString();
        if (name.endsWith(".smdesign")) {
            label.setIcon(ICON_DESIGNER);
        } else if (name.endsWith(".cs")) {
            label.setIcon(ICON_CSHARP);
        } else if (name.equals("main")) {
            label.setIcon(ICON_MAIN);
        } else if (leaf) {
            label.setIcon(ICON_SCRIPT);
        }

        return label;
    }

    /**
     * Creates a 24x24 icon representing a 3D designer document (cube wireframe).
     */
    private static ImageIcon createDesignerIcon() {
        BufferedImage img = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw a 3D cube wireframe in orange/amber
        g.setColor(new Color(255, 165, 0)); // orange
        g.setStroke(new BasicStroke(1.5f));

        // Front face
        int[] fxPoints = {4, 16, 16, 4};
        int[] fyPoints = {8, 8, 20, 20};
        g.drawPolygon(fxPoints, fyPoints, 4);

        // Top face
        int[] txPoints = {4, 10, 22, 16};
        int[] tyPoints = {8, 2, 2, 8};
        g.drawPolygon(txPoints, tyPoints, 4);

        // Right face
        int[] rxPoints = {16, 22, 22, 16};
        int[] ryPoints = {8, 2, 14, 20};
        g.drawPolygon(rxPoints, ryPoints, 4);

        g.dispose();
        return new ImageIcon(img);
    }
}
