package com.scenemaxeng.common.ui.widget;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.font.LineWrapMode;
import com.jme3.font.Rectangle;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;
import com.scenemaxeng.common.types.AssetsMapping;
import com.scenemaxeng.common.types.ResourceFont;
import com.scenemaxeng.common.ui.layout.LayoutRect;
import com.scenemaxeng.common.ui.model.UIWidgetDef;

import java.util.ArrayList;
import java.util.List;

/**
 * A selectable table-like list view with headers, rows, styles, and wrapped cell text.
 */
public class UIListViewNode extends UIWidgetNode {

    private static final float CELL_PADDING = 6f;
    private final AssetsMapping assetsMapping;
    private final List<Spatial> content = new ArrayList<>();

    public UIListViewNode(String name, UIWidgetDef widgetDef, AssetManager assetManager,
                          float designCanvasWidth, float designCanvasHeight,
                          float runtimeCanvasWidth, float runtimeCanvasHeight,
                          AssetsMapping assetsMapping) {
        super(name, widgetDef, assetManager, designCanvasWidth, designCanvasHeight, runtimeCanvasWidth, runtimeCanvasHeight);
        this.assetsMapping = assetsMapping;
    }

    @Override
    public void createVisual() {
        Quad quad = new Quad(widgetDef.getWidth(), widgetDef.getHeight());
        backgroundGeom = new Geometry(getName() + "_bg", quad);
        backgroundGeom.setMaterial(createColorMaterial(style().background));
        backgroundGeom.setQueueBucket(RenderQueue.Bucket.Gui);
        attachChild(backgroundGeom);
        rebuildContent();
    }

    @Override
    protected void onLayoutUpdated(LayoutRect rect) {
        rebuildContent();
    }

    public void setSelectedRowIndex(int rowIndex) {
        widgetDef.setListSelectedRowIndex(rowIndex);
        rebuildContent();
    }

    public int getSelectedRowIndex() {
        return widgetDef.getListSelectedRowIndex();
    }

    public void setColumnCount(int count) {
        widgetDef.setListColumnCount(count);
        rebuildContent();
    }

    public void setHeaders(List<String> headers) {
        widgetDef.setListHeaders(headers);
        rebuildContent();
    }

    public void setRows(List<List<String>> rows) {
        widgetDef.setListRows(rows);
        rebuildContent();
    }

    public void setColumnWidths(List<Float> widths) {
        widgetDef.setListColumnWidths(widths);
        rebuildContent();
    }

    public void addRow(List<String> row) {
        List<List<String>> rows = new ArrayList<>(widgetDef.getListRows());
        rows.add(row);
        widgetDef.setListRows(rows);
        rebuildContent();
    }

    public void clearRows() {
        widgetDef.setListRows(new ArrayList<>());
        widgetDef.setListSelectedRowIndex(-1);
        rebuildContent();
    }

    public void setHeaderFontName(String fontName) {
        widgetDef.setListHeaderFontName(fontName);
        rebuildContent();
    }

    public void setRowFontName(String fontName) {
        widgetDef.setListRowFontName(fontName);
        rebuildContent();
    }

    public void setHeaderFontSize(float size) {
        widgetDef.setListHeaderFontSize(size);
        rebuildContent();
    }

    public void setRowFontSize(float size) {
        widgetDef.setListRowFontSize(size);
        rebuildContent();
    }

    public void setListViewStyle(String styleName) {
        widgetDef.setListViewStyle(styleName);
        rebuildContent();
    }

    private void rebuildContent() {
        for (Spatial spatial : content) {
            detachChild(spatial);
        }
        content.clear();

        if (layoutRect == null) {
            return;
        }

        ListStyle s = style();
        if (backgroundGeom != null) {
            backgroundGeom.setMaterial(createColorMaterial(s.background));
        }

        int columns = Math.max(1, widgetDef.getListColumnCount());
        float scaleX = getScaleXFactor();
        float scaleY = getScaleYFactor();
        float width = Math.max(1f, layoutRect.width * scaleX);
        float height = Math.max(1f, layoutRect.height * scaleY);
        List<Float> columnWidths = scaledColumnWidths(width);
        float y = height;

        BitmapFont headerFont = loadFont(widgetDef.getListHeaderFontName());
        BitmapFont rowFont = loadFont(widgetDef.getListRowFontName());
        float headerFontSize = Math.max(1f, widgetDef.getListHeaderFontSize() * scaleY);
        float rowFontSize = Math.max(1f, widgetDef.getListRowFontSize() * scaleY);
        float headerHeight = measureRowHeight(widgetDef.getListHeaders(), headerFontSize, columnWidths);

        y -= headerHeight;
        addQuad("header_bg", 0, y, width, headerHeight, s.headerBackground, 0.02f);
        addCells(widgetDef.getListHeaders(), headerFont, headerFontSize, s.headerText, y, headerHeight, columnWidths, true);

        List<List<String>> rows = widgetDef.getListRows();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            float rowHeight = measureRowHeight(row, rowFontSize, columnWidths);
            if (y - rowHeight < 0) {
                break;
            }
            y -= rowHeight;
            ColorRGBA rowColor = rowIndex == widgetDef.getListSelectedRowIndex()
                    ? s.selectedBackground
                    : (rowIndex % 2 == 0 ? s.rowBackground : s.alternateRowBackground);
            addQuad("row_" + rowIndex + "_bg", 0, y, width, rowHeight, rowColor, 0.01f);
            addCells(row, rowFont, rowFontSize, s.rowText, y, rowHeight, columnWidths, false);
        }

        float gridX = 0f;
        for (int i = 1; i < columns; i++) {
            gridX += columnWidths.get(i - 1);
            addQuad("grid_v_" + i, gridX, 0, 1f, height, s.grid, 0.03f);
        }
    }

    private void addCells(List<String> values, BitmapFont font, float fontSize, ColorRGBA color,
                          float y, float height, List<Float> columnWidths, boolean header) {
        int columns = Math.max(1, widgetDef.getListColumnCount());
        float x = 0f;
        for (int i = 0; i < columns; i++) {
            float columnWidth = columnWidths.get(i);
            String text = i < values.size() ? values.get(i) : "";
            BitmapText textNode = new BitmapText(font, false);
            textNode.setSize(fontSize);
            textNode.setText(text == null ? "" : text);
            textNode.setColor(color);
            textNode.setQueueBucket(RenderQueue.Bucket.Gui);
            textNode.setLineWrapMode(LineWrapMode.Word);
            textNode.setBox(new Rectangle(0, 0,
                    Math.max(1f, columnWidth - CELL_PADDING * 2f),
                    Math.max(1f, height - CELL_PADDING * 2f)));
            textNode.setAlignment(header ? BitmapFont.Align.Center : BitmapFont.Align.Left);
            textNode.setLocalTranslation(x + CELL_PADDING, y + height - CELL_PADDING, 0.12f);
            attachChild(textNode);
            content.add(textNode);
            x += columnWidth;
        }
    }

    private float measureRowHeight(List<String> values, float fontSize, List<Float> columnWidths) {
        float minHeight = fontSize * 1.6f + CELL_PADDING * 2f;
        float maxHeight = minHeight;
        for (int i = 0; i < columnWidths.size(); i++) {
            String value = i < values.size() ? values.get(i) : "";
            maxHeight = Math.max(maxHeight, measureCellHeight(value, fontSize, columnWidths.get(i)));
        }
        return maxHeight;
    }

    private float measureCellHeight(String text, float fontSize, float columnWidth) {
        String value = text == null ? "" : text;
        int charsPerLine = Math.max(1, (int) ((columnWidth - CELL_PADDING * 2f) / Math.max(1f, fontSize * 0.55f)));
        int lines = 0;
        for (String paragraph : value.split("\\R", -1)) {
            lines += Math.max(1, (int) Math.ceil(paragraph.length() / (double) charsPerLine));
        }
        return lines * fontSize * 1.25f + CELL_PADDING * 2f;
    }

    private List<Float> scaledColumnWidths(float totalWidth) {
        return widgetDef.getEffectiveListColumnWidths(totalWidth);
    }

    private void addQuad(String suffix, float x, float y, float width, float height, ColorRGBA color, float z) {
        Geometry geom = new Geometry(getName() + "_" + suffix, new Quad(Math.max(0.1f, width), Math.max(0.1f, height)));
        Material mat = createColorMaterial(color);
        geom.setMaterial(mat);
        geom.setQueueBucket(RenderQueue.Bucket.Gui);
        geom.setLocalTranslation(x, y, z);
        attachChild(geom);
        content.add(geom);
    }

    private BitmapFont loadFont(String fontName) {
        if (fontName != null && !fontName.isEmpty() && assetsMapping != null) {
            ResourceFont resource = assetsMapping.getFontsIndex().get(fontName.toLowerCase());
            if (resource != null) {
                try {
                    return assetManager.loadFont(resource.path);
                } catch (Exception e) {
                    System.err.println("[UIListView] Failed to load font: " + fontName + " path=" + resource.path);
                }
            }
        }
        return assetManager.loadFont("Interface/Fonts/Default.fnt");
    }

    private ListStyle style() {
        String name = widgetDef.getListViewStyle();
        if ("dark".equalsIgnoreCase(name)) {
            return new ListStyle("#1F232AFF", "#323945FF", "#252B34FF", "#20252DFF", "#345B8CFF", "#5E6B7DFF", "#FFFFFFFF", "#E7ECF2FF");
        }
        if ("blue".equalsIgnoreCase(name)) {
            return new ListStyle("#F4F8FFFF", "#CFE2FFFF", "#EAF2FFFF", "#DDEBFFFF", "#9CC3FFFF", "#9EB7D9FF", "#113A6BFF", "#17314FFF");
        }
        return new ListStyle("#FAFAFAFF", "#E2E6EAFF", "#FFFFFFFF", "#F2F4F6FF", "#D4E7FFFF", "#C7CDD3FF", "#20242AFF", "#30343AFF");
    }

    private static final class ListStyle {
        final ColorRGBA background;
        final ColorRGBA headerBackground;
        final ColorRGBA rowBackground;
        final ColorRGBA alternateRowBackground;
        final ColorRGBA selectedBackground;
        final ColorRGBA grid;
        final ColorRGBA headerText;
        final ColorRGBA rowText;

        ListStyle(String background, String headerBackground, String rowBackground, String alternateRowBackground,
                  String selectedBackground, String grid, String headerText, String rowText) {
            this.background = parseColor(background);
            this.headerBackground = parseColor(headerBackground);
            this.rowBackground = parseColor(rowBackground);
            this.alternateRowBackground = parseColor(alternateRowBackground);
            this.selectedBackground = parseColor(selectedBackground);
            this.grid = parseColor(grid);
            this.headerText = parseColor(headerText);
            this.rowText = parseColor(rowText);
        }
    }
}
