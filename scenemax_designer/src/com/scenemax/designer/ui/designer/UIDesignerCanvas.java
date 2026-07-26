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
import java.util.ArrayList;
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
    private UIWidgetDef columnResizeWidget;
    private int columnResizeDividerIndex = -1;
    private float columnResizeStartCanvasX;
    private List<Float> columnResizeStartWidths = new ArrayList<>();
    private static final float LIST_COLUMN_RESIZE_TOLERANCE = 5f;
    private static final float MIN_LIST_COLUMN_WIDTH = 24f;

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
    private WidgetEditListener widgetEditListener;

    public interface SelectionListener {
        void onWidgetSelected(UIWidgetDef widget);
    }

    public interface WidgetEditListener {
        void onWidgetEdited(UIWidgetDef widget);
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
                        int dividerIndex = findListColumnDividerAt(e.getX(), e.getY());
                        if (dividerIndex >= 0) {
                            startListColumnResize(e.getX(), dividerIndex);
                        } else {
                            handleClick(e.getX(), e.getY());
                        }
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (columnResizeWidget != null) {
                    columnResizeWidget = null;
                    columnResizeDividerIndex = -1;
                    columnResizeStartWidths.clear();
                    updateHoverCursor(e.getX(), e.getY());
                    return;
                }
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
                if (columnResizeWidget != null) {
                    updateListColumnResize(e.getX());
                    return;
                }
                if (isPanning && lastMousePoint != null) {
                    panX += e.getX() - lastMousePoint.x;
                    panY += e.getY() - lastMousePoint.y;
                    lastMousePoint = e.getPoint();
                    repaint();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                updateHoverCursor(e.getX(), e.getY());
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

    public void setWidgetEditListener(WidgetEditListener listener) {
        this.widgetEditListener = listener;
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

    public float getZoomLevel() {
        return zoom;
    }

    public float getPanX() {
        return panX;
    }

    public float getPanY() {
        return panY;
    }

    public UILayerDef getActiveLayer() {
        return activeLayer;
    }

    public void panBy(float dx, float dy) {
        panX += dx;
        panY += dy;
        repaint();
    }

    public void zoomBy(float factor) {
        zoomBy(factor, getWidth() / 2f, getHeight() / 2f);
    }

    public void zoomBy(float factor, float anchorX, float anchorY) {
        if (factor <= 0f) {
            throw new IllegalArgumentException("zoom factor must be greater than zero");
        }
        float oldZoom = zoom;
        zoom = Math.max(0.05f, Math.min(5.0f, zoom * factor));
        panX = anchorX - (anchorX - panX) * (zoom / oldZoom);
        panY = anchorY - (anchorY - panY) * (zoom / oldZoom);
        repaint();
    }

    public void fitDocumentToViewport(int padding) {
        if (document == null) {
            return;
        }

        float availableWidth = Math.max(1f, getWidth() - padding * 2f);
        float availableHeight = Math.max(1f, getHeight() - padding * 2f);
        float docWidth = Math.max(1f, document.getCanvasWidth());
        float docHeight = Math.max(1f, document.getCanvasHeight());

        zoom = Math.max(0.05f, Math.min(5.0f, Math.min(availableWidth / docWidth, availableHeight / docHeight)));
        panX = (getWidth() - docWidth * zoom) / 2f;
        panY = (getHeight() - docHeight * zoom) / 2f;
        repaint();
    }

    public BufferedImage createSnapshot(int width, int height) {
        int captureWidth = getWidth() > 0 ? getWidth() : (width > 0 ? width : 1280);
        int captureHeight = getHeight() > 0 ? getHeight() : (height > 0 ? height : 720);

        BufferedImage capture = new BufferedImage(captureWidth, captureHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = capture.createGraphics();
        graphics.setColor(getBackground());
        graphics.fillRect(0, 0, captureWidth, captureHeight);
        paint(graphics);
        graphics.dispose();

        if (width > 0 && height > 0 && (captureWidth != width || captureHeight != height)) {
            BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = scaled.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.drawImage(capture, 0, 0, width, height, null);
            g2.dispose();
            return scaled;
        }

        return capture;
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
            case EDIT_TEXT: bgColor = new Color(32, 36, 42); break;
            case LIST_VIEW: bgColor = new Color(235, 238, 242); break;
            default:      bgColor = COLOR_PANEL;  break;
        }

        if (widget.getType() == UIWidgetType.LIST_VIEW) {
            drawListView(g2, widget, rect);
            if (widget == selectedWidget) {
                g2.setColor(COLOR_SELECTION);
                g2.setStroke(new BasicStroke(2f));
                g2.draw(new RoundRectangle2D.Float(x - 1, y - 1, w + 2, h + 2, 6, 6));
            }
            return;
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

        if (!spriteDrawn && widget.getType() != UIWidgetType.PANEL) {
            // Panels are container-only in the designer: show the outline, not a filled background.
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

        // Draw text content for TEXT_VIEW, EDIT_TEXT and BUTTON
        String displayText = null;
        switch (widget.getType()) {
            case TEXT_VIEW: displayText = widget.getText(); break;
            case EDIT_TEXT:
                displayText = widget.getText();
                if ((displayText == null || displayText.isEmpty())
                        && widget.getEditTextPlaceholder() != null
                        && !widget.getEditTextPlaceholder().isEmpty()) {
                    displayText = widget.getEditTextPlaceholder();
                    g2.setColor(new Color(190, 200, 210, 160));
                }
                break;
            case BUTTON: displayText = widget.getButtonText(); break;
        }

        if (displayText != null && !displayText.isEmpty() && h > 14) {
            // Draw the content text centered
            if (widget.getType() != UIWidgetType.EDIT_TEXT || widget.getText() != null && !widget.getText().isEmpty()) {
                g2.setColor(new Color(255, 255, 255, 220));
            }
            float textWidth = fm.stringWidth(displayText);
            float tx = widget.getType() == UIWidgetType.EDIT_TEXT ? x + 8 : x + (w - textWidth) / 2;
            float ty = y + (h + fm.getAscent()) / 2 - 2;
            if (textWidth > w - 4) {
                // Truncate
                displayText = truncateText(displayText, fm, w - 8);
                textWidth = fm.stringWidth(displayText);
                tx = widget.getType() == UIWidgetType.EDIT_TEXT ? x + 8 : x + (w - textWidth) / 2;
            }
            g2.drawString(displayText, tx, ty);

            if (widget.getType() == UIWidgetType.EDIT_TEXT) {
                g2.setColor(new Color(255, 255, 255, 180));
                float caretX = x + Math.min(w - 8, 10 + textWidth);
                g2.draw(new Line2D.Float(caretX, y + 8, caretX, y + h - 8));
            }

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
            case EDIT_TEXT: return "I";
            case LIST_VIEW: return "\u2261";
            case IMAGE:     return "\u25A3"; // filled square
            default:        return "";
        }
    }

    private int findListColumnDividerAt(int sx, int sy) {
        if (selectedWidget == null || selectedWidget.getType() != UIWidgetType.LIST_VIEW) {
            return -1;
        }
        LayoutRect rect = layoutResults.get(selectedWidget.getName());
        if (rect == null) {
            return -1;
        }

        float cx = screenToCanvasX(sx);
        float cy = screenToCanvasY(sy);
        float tolerance = LIST_COLUMN_RESIZE_TOLERANCE / Math.max(0.05f, zoom);
        if (cy < rect.y || cy > rect.y + rect.height || cx < rect.x - tolerance || cx > rect.x + rect.width + tolerance) {
            return -1;
        }

        List<Float> widths = selectedWidget.getEffectiveListColumnWidths(rect.width);
        float boundaryX = rect.x;
        for (int i = 0; i < widths.size() - 1; i++) {
            boundaryX += widths.get(i);
            if (Math.abs(cx - boundaryX) <= tolerance) {
                return i;
            }
        }
        return -1;
    }

    private void startListColumnResize(int sx, int dividerIndex) {
        LayoutRect rect = layoutResults.get(selectedWidget.getName());
        if (rect == null) {
            return;
        }
        columnResizeWidget = selectedWidget;
        columnResizeDividerIndex = dividerIndex;
        columnResizeStartCanvasX = screenToCanvasX(sx);
        columnResizeStartWidths = new ArrayList<>(selectedWidget.getEffectiveListColumnWidths(rect.width));
        setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
    }

    private void updateListColumnResize(int sx) {
        if (columnResizeWidget == null || columnResizeDividerIndex < 0
                || columnResizeDividerIndex + 1 >= columnResizeStartWidths.size()) {
            return;
        }

        float delta = screenToCanvasX(sx) - columnResizeStartCanvasX;
        float leftStart = columnResizeStartWidths.get(columnResizeDividerIndex);
        float rightStart = columnResizeStartWidths.get(columnResizeDividerIndex + 1);
        float pairWidth = leftStart + rightStart;
        float minWidth = Math.min(MIN_LIST_COLUMN_WIDTH, pairWidth / 2f);
        float leftWidth = Math.max(minWidth, Math.min(pairWidth - minWidth, leftStart + delta));
        float rightWidth = pairWidth - leftWidth;

        List<Float> widths = new ArrayList<>(columnResizeStartWidths);
        widths.set(columnResizeDividerIndex, leftWidth);
        widths.set(columnResizeDividerIndex + 1, rightWidth);
        columnResizeWidget.setListColumnWidths(widths);

        if (widgetEditListener != null) {
            widgetEditListener.onWidgetEdited(columnResizeWidget);
        }
        repaint();
    }

    private void updateHoverCursor(int sx, int sy) {
        if (spaceHeld) {
            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        } else if (findListColumnDividerAt(sx, sy) >= 0) {
            setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
        } else {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    private float screenToCanvasX(int sx) {
        return (sx - panX) / zoom;
    }

    private float screenToCanvasY(int sy) {
        return (sy - panY) / zoom;
    }

    private void drawListView(Graphics2D g2, UIWidgetDef widget, LayoutRect rect) {
        ListViewPreviewStyle style = listViewStyle(widget.getListViewStyle());
        float transparency = widget.getListViewTransparency();
        Color background = withListTransparency(style.background, transparency);
        Color headerBackground = withListTransparency(style.headerBackground, transparency);
        Color rowBackground = withListTransparency(style.rowBackground, transparency);
        Color alternateRowBackground = withListTransparency(style.alternateRowBackground, transparency);
        Color selectedBackground = withListTransparency(style.selectedBackground, transparency);
        Color grid = withListTransparency(style.grid, transparency);
        float x = rect.x;
        float y = rect.y;
        float w = Math.max(1f, rect.width);
        float h = Math.max(1f, rect.height);
        int columns = Math.max(1, widget.getListColumnCount());
        List<Float> columnWidths = widget.getEffectiveListColumnWidths(w);
        float cursorY = y;

        g2.setColor(background);
        g2.fill(new RoundRectangle2D.Float(x, y, w, h, 4, 4));

        Font headerFont = g2.getFont().deriveFont(Font.BOLD, Math.max(1f, widget.getListHeaderFontSize()));
        Font rowFont = g2.getFont().deriveFont(Font.PLAIN, Math.max(1f, widget.getListRowFontSize()));
        float headerHeight = measureWrappedRowHeight(g2, headerFont, widget.getListHeaders(), columnWidths);
        headerHeight = Math.min(headerHeight, h);
        g2.setColor(headerBackground);
        g2.fill(new Rectangle2D.Float(x, cursorY, w, headerHeight));
        drawWrappedCells(g2, widget.getListHeaders(), headerFont, style.headerText, x, cursorY, headerHeight, columnWidths, true);
        cursorY += headerHeight;

        List<List<String>> rows = widget.getListRows();
        for (int rowIndex = 0; rowIndex < rows.size() && cursorY < y + h; rowIndex++) {
            List<String> row = rows.get(rowIndex);
            float rowHeight = measureWrappedRowHeight(g2, rowFont, row, columnWidths);
            rowHeight = Math.min(rowHeight, y + h - cursorY);
            g2.setColor(rowIndex == widget.getListSelectedRowIndex()
                    ? selectedBackground
                    : (rowIndex % 2 == 0 ? rowBackground : alternateRowBackground));
            g2.fill(new Rectangle2D.Float(x, cursorY, w, rowHeight));
            drawWrappedCells(g2, row, rowFont, style.rowText, x, cursorY, rowHeight, columnWidths, false);
            cursorY += rowHeight;
        }

        g2.setColor(grid);
        g2.setStroke(new BasicStroke(1f));
        float gridX = x;
        for (int i = 1; i < columns; i++) {
            gridX += columnWidths.get(i - 1);
            g2.draw(new Line2D.Float(gridX, y, gridX, y + h));
        }
        if (widget == selectedWidget) {
            g2.setColor(new Color(0, 150, 255, 115));
            g2.setStroke(new BasicStroke(2f));
            gridX = x;
            for (int i = 1; i < columns; i++) {
                gridX += columnWidths.get(i - 1);
                g2.draw(new Line2D.Float(gridX, y, gridX, y + h));
            }
        }
        if (widget != selectedWidget) {
            g2.setColor(grid);
        }
        g2.draw(new RoundRectangle2D.Float(x, y, w, h, 4, 4));

        g2.setColor(new Color(90, 96, 104, 180));
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 9f));
        g2.drawString(widget.getName(), x + 4, y + 11);
    }

    private Color withListTransparency(Color color, float transparency) {
        float clamped = Math.max(0f, Math.min(100f, transparency));
        int alpha = Math.round(color.getAlpha() * (1f - clamped / 100f));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    private void drawWrappedCells(Graphics2D g2, List<String> values, Font font, Color color,
                                  float x, float y, float rowHeight, List<Float> columnWidths, boolean center) {
        g2.setFont(font);
        g2.setColor(color);
        FontMetrics fm = g2.getFontMetrics();
        int padding = 6;
        int columns = Math.max(1, columnWidths.size());
        float cellX = x;
        for (int i = 0; i < columns; i++) {
            float columnWidth = columnWidths.get(i);
            String text = i < values.size() && values.get(i) != null ? values.get(i) : "";
            java.util.List<String> lines = wrapText(text, fm, Math.max(1, (int) columnWidth - padding * 2));
            int textHeight = lines.size() * fm.getHeight();
            int ty = (int) y + padding + fm.getAscent();
            if (center) {
                ty = (int) (y + Math.max(fm.getAscent() + padding, (rowHeight - textHeight) / 2f + fm.getAscent()));
            }
            for (String line : lines) {
                int tx = (int) (cellX + padding);
                if (center) {
                    tx = (int) (cellX + (columnWidth - fm.stringWidth(line)) / 2f);
                }
                g2.drawString(line, tx, ty);
                ty += fm.getHeight();
                if (ty > y + rowHeight) {
                    break;
                }
            }
            cellX += columnWidth;
        }
    }

    private float measureWrappedRowHeight(Graphics2D g2, Font font, List<String> values, List<Float> columnWidths) {
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        int maxLines = 1;
        for (int i = 0; i < columnWidths.size(); i++) {
            String value = i < values.size() ? values.get(i) : "";
            maxLines = Math.max(maxLines, wrapText(value == null ? "" : value, fm,
                    Math.max(1, (int) columnWidths.get(i).floatValue() - 12)).size());
        }
        return Math.max(fm.getHeight() + 12, maxLines * fm.getHeight() + 12);
    }

    private java.util.List<String> wrapText(String text, FontMetrics fm, int maxWidth) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        String[] paragraphs = text.split("\\R", -1);
        for (String paragraph : paragraphs) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split("\\s+")) {
                if (word.isEmpty()) {
                    continue;
                }
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (fm.stringWidth(candidate) <= maxWidth) {
                    line.setLength(0);
                    line.append(candidate);
                } else {
                    if (line.length() > 0) {
                        lines.add(line.toString());
                        line.setLength(0);
                    }
                    if (fm.stringWidth(word) <= maxWidth) {
                        line.append(word);
                    } else {
                        splitLongWord(word, fm, maxWidth, lines);
                    }
                }
            }
            lines.add(line.toString());
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    private void splitLongWord(String word, FontMetrics fm, int maxWidth, java.util.List<String> lines) {
        StringBuilder chunk = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            String candidate = chunk.toString() + word.charAt(i);
            if (fm.stringWidth(candidate) > maxWidth && chunk.length() > 0) {
                lines.add(chunk.toString());
                chunk.setLength(0);
            }
            chunk.append(word.charAt(i));
        }
        if (chunk.length() > 0) {
            lines.add(chunk.toString());
        }
    }

    private ListViewPreviewStyle listViewStyle(String name) {
        if ("dark".equalsIgnoreCase(name)) {
            return new ListViewPreviewStyle(
                    new Color(31, 35, 42), new Color(50, 57, 69),
                    new Color(37, 43, 52), new Color(32, 37, 45),
                    new Color(52, 91, 140), new Color(94, 107, 125),
                    Color.WHITE, new Color(231, 236, 242));
        }
        if ("blue".equalsIgnoreCase(name)) {
            return new ListViewPreviewStyle(
                    new Color(244, 248, 255), new Color(207, 226, 255),
                    new Color(234, 242, 255), new Color(221, 235, 255),
                    new Color(156, 195, 255), new Color(158, 183, 217),
                    new Color(17, 58, 107), new Color(23, 49, 79));
        }
        return new ListViewPreviewStyle(
                new Color(250, 250, 250), new Color(226, 230, 234),
                Color.WHITE, new Color(242, 244, 246),
                new Color(212, 231, 255), new Color(199, 205, 211),
                new Color(32, 36, 42), new Color(48, 52, 58));
    }

    private static class ListViewPreviewStyle {
        final Color background;
        final Color headerBackground;
        final Color rowBackground;
        final Color alternateRowBackground;
        final Color selectedBackground;
        final Color grid;
        final Color headerText;
        final Color rowText;

        ListViewPreviewStyle(Color background, Color headerBackground, Color rowBackground,
                             Color alternateRowBackground, Color selectedBackground, Color grid,
                             Color headerText, Color rowText) {
            this.background = background;
            this.headerBackground = headerBackground;
            this.rowBackground = rowBackground;
            this.alternateRowBackground = alternateRowBackground;
            this.selectedBackground = selectedBackground;
            this.grid = grid;
            this.headerText = headerText;
            this.rowText = rowText;
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
