package com.scenemax.desktop;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaUI;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

public class GentleBracketMatchTextAreaUI extends RSyntaxTextAreaUI {
    private static final Color BRACKET_UNDERLINE = new Color(166, 187, 194, 135);

    public GentleBracketMatchTextAreaUI(JComponent textArea) {
        super(textArea);
    }

    @Override
    protected void paintMatchedBracketImpl(Graphics g, RSyntaxTextArea textArea, Rectangle rect) {
        if (rect == null) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(BRACKET_UNDERLINE);
            int y = rect.y + rect.height - 2;
            g2.drawLine(rect.x, y, rect.x + Math.max(1, rect.width - 1), y);
        } finally {
            g2.dispose();
        }
    }
}
