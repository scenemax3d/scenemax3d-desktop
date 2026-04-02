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
    private static final ImageIcon ICON_UI_DESIGNER = createUIDesignerIcon();
    private static final ImageIcon ICON_SHADER_DESIGNER = createShaderDesignerIcon();
    private static final ImageIcon ICON_ENVIRONMENT_SHADER_DESIGNER = createEnvironmentShaderDesignerIcon();

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
        } else if (name.endsWith(".smui")) {
            label.setIcon(ICON_UI_DESIGNER);
        } else if (name.endsWith(".smshader")) {
            label.setIcon(ICON_SHADER_DESIGNER);
        } else if (name.endsWith(".smenvshader")) {
            label.setIcon(ICON_ENVIRONMENT_SHADER_DESIGNER);
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

    /**
     * Creates a 24x24 icon representing a UI designer document (layout grid).
     */
    private static ImageIcon createUIDesignerIcon() {
        BufferedImage img = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw a UI layout icon in cyan/teal
        g.setColor(new Color(0, 188, 212));
        g.setStroke(new BasicStroke(1.5f));

        // Outer frame
        g.drawRoundRect(2, 2, 20, 20, 3, 3);

        // Inner layout dividers (horizontal + vertical)
        g.drawLine(2, 9, 22, 9);   // horizontal divider
        g.drawLine(12, 9, 12, 22); // vertical divider (bottom half)

        // Small filled rectangle (button representation)
        g.setColor(new Color(0, 150, 180));
        g.fillRoundRect(4, 4, 16, 3, 1, 1);

        g.dispose();
        return new ImageIcon(img);
    }

    /**
     * Creates a 24x24 icon representing a shader/effect document (spark + gradient tile).
     */
    private static ImageIcon createShaderDesignerIcon() {
        BufferedImage img = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        GradientPaint paint = new GradientPaint(3, 3, new Color(0, 215, 210), 21, 21, new Color(255, 160, 65));
        g.setPaint(paint);
        g.fillRoundRect(3, 3, 18, 18, 5, 5);

        g.setColor(new Color(14, 24, 32, 180));
        g.fillRoundRect(5, 6, 14, 10, 3, 3);

        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1.5f));
        Path2D spark = new Path2D.Float();
        spark.moveTo(12, 4);
        spark.lineTo(13.5, 9.5);
        spark.lineTo(19, 11);
        spark.lineTo(13.5, 12.5);
        spark.lineTo(12, 18);
        spark.lineTo(10.5, 12.5);
        spark.lineTo(5, 11);
        spark.lineTo(10.5, 9.5);
        spark.closePath();
        g.draw(spark);

        g.dispose();
        return new ImageIcon(img);
    }

    private static ImageIcon createEnvironmentShaderDesignerIcon() {
        BufferedImage img = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        GradientPaint paint = new GradientPaint(4, 4, new Color(114, 182, 255), 20, 20, new Color(58, 116, 196));
        g.setPaint(paint);
        g.fillRoundRect(3, 5, 18, 14, 7, 7);

        g.setColor(new Color(244, 248, 252));
        g.fillOval(6, 6, 7, 7);

        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(10, 14, 8, 18);
        g.drawLine(14, 14, 12, 18);
        g.drawLine(18, 14, 16, 18);

        g.dispose();
        return new ImageIcon(img);
    }
}
