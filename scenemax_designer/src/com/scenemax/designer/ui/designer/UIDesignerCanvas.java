package com.scenemax.designer.ui.designer;

import com.scenemaxeng.common.types.AssetsMapping;
import com.scenemaxeng.common.types.ResourceSetup2D;
import com.scenemaxeng.common.ui.layout.ConstraintLayoutEngine;
import com.scenemaxeng.common.ui.layout.LayoutRect;
import com.scenemaxeng.common.ui.model.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 2D Swing canvas that renders a preview of the UI layout.
 * This is used at design-time in the IDE — no JME required.
 *
 * Features:
 * - Renders all widgets as colored rectangles with labels
 * - Shows guidelines as dashed lines
 * - Highlights selected widget with a blue outline
 * - Shows constraint anchors as small circles
 * - Supports click-to-select
 * - Zooms and pans for large layouts
 */
public class UIDesignerCanvas extends JPanel {

    private UIDocument document;
    private UILayerDef activeLayer;
    private UIWidgetDef selectedWidget;
    private Map<String, LayoutRect> layoutResults = new LinkedHashMap<>();
    private ConstraintLayoutEngine layoutEngine = new ConstraintLayoutEngine();

    // Viewport transform
    private float zoom = 1.0f;
    private float panX = 20;
    private float panY = 20;

    // Drag state
    private Point lastMousePoint;
    private boolean isPanning;
    private boolean spaceHeld;  // Space key held = pan mode (like Figma/Photoshop)

    // Colors
    private static final Color COLOR_CANVAS_BG = new Color(45, 45, 48);
    private static final Color COLOR_CANVAS_BORDER = new Color(80, 80, 80);
    private static final Color COLOR_PANEL = new Color(60, 60, 65);
    private static final Color COLOR_BUTTON = new Color(68, 136, 255);
    private static final Color COLOR_TEXT = new Color(200, 200, 200);
    private static final Color COLOR_IMAGE = new Color(100, 80, 120);
    private static final Color COLOR_GUIDELINE = new Color(255, 200, 50, 120);
    private static final Color COLOR_SELECTION = new Color(0, 150, 255);
    private static final Color COLOR_CONSTRAINT_LINE = new Color(255, 100, 100, 160);
    private static final Color COLOR_WIDGET_LABEL = new Color(255, 255, 255, 200);
    private static final Color COLOR_GRID = new Color(55, 55, 58);

    // Sprite rendering support
    private AssetsMapping assetsMapping;
    private String projectPath;
    private Map<String, BufferedImage> spriteImageCache = new HashMap<>();

    // Listener for selection changes
    private SelectionListener selectionListener;

    public interface SelectionListener {
        void onWidgetSelected(UIWidgetDef widget);
    }

    public UIDesignerCanvas() {
        setBackground(COLOR_CANVAS_BG);
        setFocusable(true);

        // Space key toggles pan mode (hold Space + left-drag to pan, like Figma/Photoshop)
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE && !spaceHeld) {
                    spaceHeld = true;
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                    spaceHeld = false;
                    isPanning = false;
                    setCursor(Cursor.getDefaultCursor());
                }
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (SwingUtilities.isMiddleMouseButton(e) || SwingUtilities.isRightMouseButton(e)) {
                    // Middle/right-click always pans
                    isPanning = true;
                    lastMousePoint = e.getPoint();
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                } else if (SwingUtilities.isLeftMouseButton(e)) {
                    if (spaceHeld) {
                        // Space + left-click = pan
                        isPanning = true;
                        lastMousePoint = e.getPoint();
                    } else {
                        handleClick(e.getX(), e.getY());
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (isPanning) {
                    isPanning = false;
                    if (!spaceHeld) {
                        setCursor(Cursor.getDefaultCursor());
                    }
                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (isPanning && lastMousePoint != null) {
                    panX += e.getX() - lastMousePoint.x;
                    panY += e.getY() - lastMousePoint.y;
                    lastMousePoint = e.getPoint();
                    repaint();
                }
            }
        });

        addMouseWheelListener(e -> {
            float oldZoom = zoom;
            // Use precise rotation for smooth trackpad/high-res mouse support.
            // Clamp to avoid huge jumps from fast flicks.
            double rotation = e.getPreciseWheelRotation();
            rotation = Math.max(-2.0, Math.min(2.0, rotation));

            // Consistent exponential zoom: same speed in both directions.
            // Negative rotation = zoom in, positive = zoom out.
            float factor = (float) Math.pow(1.08, -rotation);
            zoom = Math.max(0.05f, Math.min(5.0f, zoom * factor));

            // Zoom toward mouse position
            float mx = e.getX();
            float my = e.getY();
            panX = mx - (mx - panX) * (zoom / oldZoom);
            panY = my - (my - panY) * (zoom / oldZoom);
            repaint();
        });
    }

    // --- Public API ---

    public void setDocument(UIDocument document) {
        this.document = document;
        if (document != null && !document.getLayers().isEmpty()) {
            this.activeLayer = document.getLayers().get(0);
        } else {
            this.activeLayer = null;
        }
        runLayout();
        repaint();
    }

    public void setActiveLayer(UILayerDef layer) {
        this.activeLayer = layer;
        runLayout();
        repaint();
    }

    public void setSelectedWidget(UIWidgetDef widget) {
        this.selectedWidget = widget;
        repaint();
    }

    public UIWidgetDef getSelectedWidget() {
        return selectedWidget;
    }

    public void setSelectionListener(SelectionListener listener) {
        this.selectionListener = listener;
    }

    public void setSpriteResources(AssetsMapping assetsMapping, String projectPath) {
        this.assetsMapping = assetsMapping;
        this.projectPath = projectPath;
        this.spriteImageCache.clear();
    }

    /**
     * Re-runs the layout engine and repaints. Call after constraint or size changes.
     */
    public void refreshLayout() {
        runLayout();
        repaint();
    }

    // --- Layout ---

    private void runLayout() {
        layoutResults.clear();
        if (document == null || activeLayer == null) return;

        float w = document.getCanvasWidth();
        float h = document.getCanvasHeight();

        Map<String, LayoutRect> topResults = layoutEngine.solve(activeLayer.getWidgets(), w, h);
        layoutResults.putAll(topResults);

        // Recursively solve children
        for (UIWidgetDef widget : activeLayer.getWidgets()) {
            if (!widget.getChildren().isEmpty()) {
                LayoutRect parentRect = topResults.get(widget.getName());
                if (parentRect != null) {
                    layoutEngine.solveChildren(widget, parentRect, layoutResults);
                }
            }
        }
    }

    // --- Click handling ---

    private void handleClick(int mx, int my) {
        if (activeLayer == null) return;

        // Convert screen coords to canvas coords
        float cx = (mx - panX) / zoom;
        float cy = (my - panY) / zoom;

        // Search widgets in reverse order (top z-order first)
        UIWidgetDef hit = null;
        for (UIWidgetDef widget : activeLayer.getWidgets()) {
            UIWidgetDef found = hitTestRecursive(widget, cx, cy);
            if (found != null) hit = found;
        }

        selectedWidget = hit;
        repaint();

        if (selectionListener != null) {
            selectionListener.onWidgetSelected(hit);
        }
    }

    private UIWidgetDef hitTestRecursive(UIWidgetDef widget, float cx, float cy) {
        UIWidgetDef result = null;

        LayoutRect rect = layoutResults.get(widget.getName());
        if (rect != null && widget.getType() != UIWidgetType.GUIDELINE) {
            if (cx >= rect.x && cx <= rect.x + rect.width
                    && cy >= rect.y && cy <= rect.y + rect.height) {
                result = widget;
            }
        }

        // Check children (deeper children take priority)
        for (UIWidgetDef child : widget.getChildren()) {
            UIWidgetDef childHit = hitTestRecursive(child, cx, cy);
            if (childHit != null) result = childHit;
        }

        return result;
    }

    // --- Painting ---

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        if (document == null) {
            g2.setColor(COLOR_TEXT);
            g2.setFont(g2.getFont().deriveFont(14f));
            g2.drawString("No UI document loaded", 20, 30);
            g2.dispose();
            return;
        }

        // Apply viewport transform
        g2.translate(panX, panY);
        g2.scale(zoom, zoom);

        float cw = document.getCanvasWidth();
        float ch = document.getCanvasHeight();

        // Draw canvas background
        g2.setColor(new Color(35, 35, 38));
        g2.fill(new Rectangle2D.Float(0, 0, cw, ch));

        // Draw grid
        drawGrid(g2, cw, ch);

        // Draw canvas border
        g2.setColor(COLOR_CANVAS_BORDER);
        g2.setStroke(new BasicStroke(2f));
        g2.draw(new Rectangle2D.Float(0, 0, cw, ch));

        // Draw canvas size label
        g2.setColor(new Color(150, 150, 150));
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 11f));
        g2.drawString((int) cw + " x " + (int) ch, 4, ch + 14);

        // Draw widgets
        if (activeLayer != null) {
            for (UIWidgetDef widget : activeLayer.getWidgets()) {
                drawWidgetRecursive(g2, widget);
            }

            // Draw constraint lines for selected widget
            if (selectedWidget != null) {
                drawConstraintLines(g2, selectedWidget);
            }
        }

        g2.dispose();
    }

    private void drawGrid(Graphics2D g2, float cw, float ch) {
        g2.setColor(COLOR_GRID);
        g2.setStroke(new BasicStroke(0.5f));
        float gridSize = 50;
        for (float x = gridSize; x < cw; x += gridSize) {
            g2.draw(new Line2D.Float(x, 0, x, ch));
        }
        for (float y = gridSize; y < ch; y += gridSize) {
            g2.draw(new Line2D.Float(0, y, cw, y));
        }
    }

    private void drawWidgetRecursive(Graphics2D g2, UIWidgetDef widget) {
        LayoutRect rect = layoutResults.get(widget.getName());
        if (rect == null) return;

        if (widget.getType() == UIWidgetType.GUIDELINE) {
            drawGuideline(g2, widget, rect);
        } else {
            drawWidget(g2, widget, rect);
        }

        // Draw children
        for (UIWidgetDef child : widget.getChildren()) {
            drawWidgetRecursive(g2, child);
        }
    }

    private void drawWidget(Graphics2D g2, UIWidgetDef widget, LayoutRect rect) {
        float x = rect.x;
        float y = rect.y;
        float w = rect.width;
        float h = rect.height;

        // Background color based on type
        Color bgColor;
        switch (widget.getType()) {
            case BUTTON:  bgColor = COLOR_BUTTON; break;
            case IMAGE:   bgColor = COLOR_IMAGE;  break;
            case TEXT_VIEW: bgColor = new Color(50, 50, 55, 100); break;
            default:      bgColor = COLOR_PANEL;  break;
        }

        // Try to draw sprite image for IMAGE widgets
        boolean spriteDrawn = false;
        if (widget.getType() == UIWidgetType.IMAGE && widget.getSpriteName() != null && !widget.getSpriteName().isEmpty()) {
            BufferedImage spriteImg = loadSpriteImage(widget.getSpriteName());
            if (spriteImg != null) {
                // Draw first frame of the sprite sheet
                ResourceSetup2D res = assetsMapping.getSpriteSheetsIndex().get(widget.getSpriteName().toLowerCase());
                if (res != null && res.cols > 0 && res.rows > 0) {
                    int frameW = spriteImg.getWidth() / res.cols;
                    int frameH = spriteImg.getHeight() / res.rows;
                    g2.drawImage(spriteImg,
                            (int) x, (int) y, (int) (x + w), (int) (y + h),
                            0, 0, frameW, frameH,
                            null);
                    spriteDrawn = true;
                }
            }
        }

        if (!spriteDrawn) {
            // Fill
            g2.setColor(bgColor);
            g2.fill(new RoundRectangle2D.Float(x, y, w, h, 4, 4));
        }

        // Border
        g2.setColor(bgColor.brighter());
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Float(x, y, w, h, 4, 4));

        // Selection highlight
        if (widget == selectedWidget) {
            g2.setColor(COLOR_SELECTION);
            g2.setStroke(new BasicStroke(2f));
            g2.draw(new RoundRectangle2D.Float(x - 1, y - 1, w + 2, h + 2, 6, 6));
        }

        // Widget label (name + type indicator)
        g2.setColor(COLOR_WIDGET_LABEL);
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, Math.min(11f, h * 0.4f)));
        String label = widget.getName();
        FontMetrics fm = g2.getFontMetrics();

        // Draw text content for TEXT_VIEW and BUTTON
        String displayText = null;
        switch (widget.getType()) {
            case TEXT_VIEW: displayText = widget.getText(); break;
            case BUTTON: displayText = widget.getButtonText(); break;
        }

        if (displayText != null && !displayText.isEmpty() && h > 14) {
            // Draw the content text centered
            g2.setColor(new Color(255, 255, 255, 220));
            float textWidth = fm.stringWidth(displayText);
            float tx = x + (w - textWidth) / 2;
            float ty = y + (h + fm.getAscent()) / 2 - 2;
            if (textWidth > w - 4) {
                // Truncate
                displayText = truncateText(displayText, fm, w - 8);
                textWidth = fm.stringWidth(displayText);
                tx = x + (w - textWidth) / 2;
            }
            g2.drawString(displayText, tx, ty);

            // Draw name as small label above
            g2.setColor(new Color(180, 180, 180, 160));
            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 9f));
            g2.drawString(label, x + 3, y + 10);

            // Show font name for TEXT_VIEW at bottom
            if (widget.getType() == UIWidgetType.TEXT_VIEW
                    && widget.getFontName() != null && !widget.getFontName().isEmpty()
                    && h > 24) {
                g2.setColor(new Color(160, 200, 255, 150));
                g2.setFont(g2.getFont().deriveFont(Font.ITALIC, 9f));
                g2.drawString("\u266A " + widget.getFontName(), x + 3, y + h - 3);
            }
        } else {
            // Just draw the name centered
            float textWidth = fm.stringWidth(label);
            float tx = x + (w - textWidth) / 2;
            float ty = y + (h + fm.getAscent()) / 2 - 2;
            g2.drawString(label, tx, ty);
        }

        // Type badge (small icon in top-right)
        String badge = getTypeBadge(widget.getType());
        g2.setColor(new Color(255, 255, 255, 100));
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 9f));
        g2.drawString(badge, x + w - fm.stringWidth(badge) - 3, y + 10);
    }

    private void drawGuideline(Graphics2D g2, UIWidgetDef widget, LayoutRect rect) {
        g2.setColor(COLOR_GUIDELINE);
        float[] dash = {6f, 4f};
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));

        if (widget.isGuidelineHorizontal()) {
            g2.draw(new Line2D.Float(rect.x, rect.y, rect.x + rect.width, rect.y));
        } else {
            g2.draw(new Line2D.Float(rect.x, rect.y, rect.x, rect.y + rect.height));
        }

        // Label
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 9f));
        g2.drawString(widget.getName(), rect.x + 2, rect.y - 3);
    }

    private void drawConstraintLines(Graphics2D g2, UIWidgetDef widget) {
        LayoutRect widgetRect = layoutResults.get(widget.getName());
        if (widgetRect == null) return;

        g2.setColor(COLOR_CONSTRAINT_LINE);
        float[] dash = {4f, 3f};
        g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));

        for (UIConstraint constraint : widget.getConstraints()) {
            float fromX = getConstraintAnchorX(constraint.getSide(), widgetRect);
            float fromY = getConstraintAnchorY(constraint.getSide(), widgetRect);

            float toX, toY;
            if (constraint.isParentConstraint()) {
                toX = getParentAnchorX(constraint.getTargetSide(), document.getCanvasWidth());
                toY = getParentAnchorY(constraint.getTargetSide(), document.getCanvasHeight());
            } else {
                LayoutRect targetRect = layoutResults.get(constraint.getTargetName());
                if (targetRect == null) continue;
                toX = getConstraintAnchorX(constraint.getTargetSide(), targetRect);
                toY = getConstraintAnchorY(constraint.getTargetSide(), targetRect);
            }

            g2.draw(new Line2D.Float(fromX, fromY, toX, toY));

            // Draw small anchor circles
            g2.fillOval((int) fromX - 3, (int) fromY - 3, 6, 6);
            g2.fillOval((int) toX - 3, (int) toY - 3, 6, 6);
        }

        // Draw center constraint indicators
        float canvasW = document.getCanvasWidth();
        float canvasH = document.getCanvasHeight();
        g2.setColor(new Color(100, 200, 255, 160));

        if (widget.isCenterHorizontal()) {
            float cx = widgetRect.x + widgetRect.width / 2;
            float cy = widgetRect.y + widgetRect.height / 2;
            // Draw dashed line from widget center to parent center (horizontal)
            g2.draw(new Line2D.Float(cx, cy, canvasW / 2, cy));
            g2.fillOval((int) cx - 3, (int) cy - 3, 6, 6);
            g2.fillOval((int) (canvasW / 2) - 3, (int) cy - 3, 6, 6);
        }

        if (widget.isCenterVertical()) {
            float cx = widgetRect.x + widgetRect.width / 2;
            float cy = widgetRect.y + widgetRect.height / 2;
            // Draw dashed line from widget center to parent center (vertical)
            g2.draw(new Line2D.Float(cx, cy, cx, canvasH / 2));
            g2.fillOval((int) cx - 3, (int) cy - 3, 6, 6);
            g2.fillOval((int) cx - 3, (int) (canvasH / 2) - 3, 6, 6);
        }
    }

    // --- Helpers ---

    private float getConstraintAnchorX(UIConstraintSide side, LayoutRect rect) {
        switch (side) {
            case LEFT:   return rect.x;
            case RIGHT:  return rect.x + rect.width;
            case TOP:
            case BOTTOM: return rect.x + rect.width / 2;
            default:     return rect.x;
        }
    }

    private float getConstraintAnchorY(UIConstraintSide side, LayoutRect rect) {
        switch (side) {
            case TOP:    return rect.y;
            case BOTTOM: return rect.y + rect.height;
            case LEFT:
            case RIGHT:  return rect.y + rect.height / 2;
            default:     return rect.y;
        }
    }

    private float getParentAnchorX(UIConstraintSide side, float canvasWidth) {
        switch (side) {
            case LEFT:  return 0;
            case RIGHT: return canvasWidth;
            default:    return canvasWidth / 2;
        }
    }

    private float getParentAnchorY(UIConstraintSide side, float canvasHeight) {
        switch (side) {
            case TOP:    return 0;
            case BOTTOM: return canvasHeight;
            default:     return canvasHeight / 2;
        }
    }

    private String getTypeBadge(UIWidgetType type) {
        switch (type) {
            case PANEL:     return "\u25A1"; // square
            case BUTTON:    return "\u25C9"; // circle with dot
            case TEXT_VIEW: return "T";
            case IMAGE:     return "\u25A3"; // filled square
            default:        return "";
        }
    }

    private String truncateText(String text, FontMetrics fm, float maxWidth) {
        String ellipsis = "...";
        int ellipsisWidth = fm.stringWidth(ellipsis);
        if (fm.stringWidth(text) <= maxWidth) return text;

        for (int i = text.length() - 1; i > 0; i--) {
            if (fm.stringWidth(text.substring(0, i)) + ellipsisWidth <= maxWidth) {
                return text.substring(0, i) + ellipsis;
            }
        }
        return ellipsis;
    }

    private BufferedImage loadSpriteImage(String spriteName) {
        if (assetsMapping == null) return null;

        if (spriteImageCache.containsKey(spriteName)) {
            return spriteImageCache.get(spriteName);
        }

        ResourceSetup2D res = assetsMapping.getSpriteSheetsIndex().get(spriteName.toLowerCase());
        if (res == null) {
            spriteImageCache.put(spriteName, null);
            return null;
        }

        // Try project resources first, then default resources
        String[] searchPaths;
        if (projectPath != null) {
            searchPaths = new String[]{
                    projectPath + "/resources/" + res.path,
                    "./resources/" + res.path
            };
        } else {
            searchPaths = new String[]{"./resources/" + res.path};
        }

        for (String path : searchPaths) {
            File file = new File(path);
            if (file.exists()) {
                try {
                    BufferedImage img = ImageIO.read(file);
                    spriteImageCache.put(spriteName, img);
                    return img;
                } catch (Exception e) {
                    System.err.println("[UIDesignerCanvas] Failed to load sprite image: " + path);
                }
            }
        }

        spriteImageCache.put(spriteName, null);
        return null;
    }
}
