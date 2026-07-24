package com.scenemaxeng.common.ui.widget;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.AbstractControl;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.shape.Quad;
import com.scenemaxeng.common.types.AssetsMapping;
import com.scenemaxeng.common.types.ResourceFont;
import com.scenemaxeng.common.ui.layout.LayoutRect;
import com.scenemaxeng.common.ui.model.UIWidgetDef;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;

/**
 * Editable text field rendered in JME GUI space.
 */
public class UIEditTextNode extends UIWidgetNode {

    private static final float PADDING_X = 8f;
    private static final float PADDING_Y = 6f;

    private final AssetsMapping assetsMapping;
    private final List<Spatial> editVisuals = new ArrayList<>();
    private BitmapFont font;
    private BitmapText textNode;
    private BitmapText placeholderNode;
    private BitmapText measureNode;
    private int caretIndex = 0;
    private int selectionAnchor = -1;
    private boolean focused;
    private float blinkTimer;
    private boolean caretVisible = true;

    public UIEditTextNode(String name, UIWidgetDef widgetDef, AssetManager assetManager,
                          float designCanvasWidth, float designCanvasHeight,
                          float runtimeCanvasWidth, float runtimeCanvasHeight,
                          AssetsMapping assetsMapping) {
        super(name, widgetDef, assetManager, designCanvasWidth, designCanvasHeight, runtimeCanvasWidth, runtimeCanvasHeight);
        this.assetsMapping = assetsMapping;
    }

    @Override
    public void createVisual() {
        font = loadFont(widgetDef.getFontName());
        backgroundGeom = new Geometry(getName() + "_bg", new Quad(widgetDef.getWidth(), widgetDef.getHeight()));
        backgroundGeom.setMaterial(createColorMaterial(parseColor(widgetDef.getEditTextBackgroundColor())));
        backgroundGeom.setQueueBucket(RenderQueue.Bucket.Gui);
        attachChild(backgroundGeom);

        textNode = new BitmapText(font, false);
        textNode.setQueueBucket(RenderQueue.Bucket.Gui);
        attachChild(textNode);

        placeholderNode = new BitmapText(font, false);
        placeholderNode.setQueueBucket(RenderQueue.Bucket.Gui);
        placeholderNode.setColor(new ColorRGBA(0.75f, 0.78f, 0.82f, 0.65f));
        attachChild(placeholderNode);

        measureNode = new BitmapText(font, false);

        addControl(new AbstractControl() {
            @Override
            protected void controlUpdate(float tpf) {
                UIEditTextNode.this.update(tpf);
            }

            @Override
            protected void controlRender(RenderManager rm, ViewPort vp) {
            }
        });

        caretIndex = clampIndex(widgetDef.getText() == null ? 0 : widgetDef.getText().length());
        refreshVisuals();
    }

    @Override
    protected void onLayoutUpdated(LayoutRect rect) {
        refreshVisuals();
    }

    public void update(float tpf) {
        if (!focused) {
            return;
        }
        blinkTimer += tpf;
        if (blinkTimer >= 0.5f) {
            blinkTimer = 0f;
            caretVisible = !caretVisible;
            refreshVisuals();
        }
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean focused) {
        if (this.focused == focused) {
            return;
        }
        this.focused = focused;
        blinkTimer = 0f;
        caretVisible = true;
        selectionAnchor = -1;
        if (backgroundGeom != null) {
            backgroundGeom.setMaterial(createColorMaterial(parseColor(
                    focused ? widgetDef.getEditTextFocusedColor() : widgetDef.getEditTextBackgroundColor())));
        }
        refreshVisuals();
    }

    public boolean isMultiline() {
        return widgetDef.isEditTextMultiline();
    }

    public void setMultiline(boolean multiline) {
        widgetDef.setEditTextMultiline(multiline);
        if (!multiline) {
            setText(getText().replace('\n', ' ').replace('\r', ' '));
        }
        refreshVisuals();
    }

    public String getText() {
        return widgetDef.getText() == null ? "" : widgetDef.getText();
    }

    public void setText(String text) {
        widgetDef.setText(sanitizeText(text == null ? "" : text));
        caretIndex = clampIndex(caretIndex);
        selectionAnchor = -1;
        refreshVisuals();
    }

    public void setTextColor(String hexColor) {
        widgetDef.setTextColor(hexColor);
        refreshVisuals();
    }

    public void setFontSize(float size) {
        widgetDef.setFontSize(size);
        refreshVisuals();
    }

    public void setFontName(String fontName) {
        widgetDef.setFontName(fontName);
        font = loadFont(fontName);
        detachChild(textNode);
        detachChild(placeholderNode);
        textNode = new BitmapText(font, false);
        textNode.setQueueBucket(RenderQueue.Bucket.Gui);
        attachChild(textNode);
        placeholderNode = new BitmapText(font, false);
        placeholderNode.setQueueBucket(RenderQueue.Bucket.Gui);
        placeholderNode.setColor(new ColorRGBA(0.75f, 0.78f, 0.82f, 0.65f));
        attachChild(placeholderNode);
        measureNode = new BitmapText(font, false);
        refreshVisuals();
    }

    public void setPlaceholder(String placeholder) {
        widgetDef.setEditTextPlaceholder(placeholder);
        refreshVisuals();
    }

    public void setBackgroundColor(String hexColor) {
        widgetDef.setEditTextBackgroundColor(hexColor);
        if (!focused && backgroundGeom != null) {
            backgroundGeom.setMaterial(createColorMaterial(parseColor(hexColor)));
        }
    }

    public void setFocusedColor(String hexColor) {
        widgetDef.setEditTextFocusedColor(hexColor);
        if (focused && backgroundGeom != null) {
            backgroundGeom.setMaterial(createColorMaterial(parseColor(hexColor)));
        }
    }

    public void setCursorColor(String hexColor) {
        widgetDef.setEditTextCursorColor(hexColor);
        refreshVisuals();
    }

    public void setSelectionColor(String hexColor) {
        widgetDef.setEditTextSelectionColor(hexColor);
        refreshVisuals();
    }

    public void setCaretFromLocal(float localX, float localY, boolean shift) {
        int newIndex = indexFromLocal(localX, localY);
        moveCaret(newIndex, shift);
    }

    public boolean handleKey(int keyCode, char keyChar, boolean shift, boolean ctrl) {
        switch (keyCode) {
            case KeyInput.KEY_LEFT:
                moveCaret(ctrl ? previousWordIndex() : Math.max(0, caretIndex - 1), shift);
                return true;
            case KeyInput.KEY_RIGHT:
                moveCaret(ctrl ? nextWordIndex() : Math.min(getText().length(), caretIndex + 1), shift);
                return true;
            case KeyInput.KEY_UP:
                moveCaret(verticalCaretMove(-1), shift);
                return true;
            case KeyInput.KEY_DOWN:
                moveCaret(verticalCaretMove(1), shift);
                return true;
            case KeyInput.KEY_HOME:
                moveCaret(lineStartFor(caretIndex), shift);
                return true;
            case KeyInput.KEY_END:
                moveCaret(lineEndFor(caretIndex), shift);
                return true;
            case KeyInput.KEY_BACK:
                backspace(ctrl);
                return true;
            case KeyInput.KEY_DELETE:
                deleteForward(ctrl);
                return true;
            case KeyInput.KEY_RETURN:
            case KeyInput.KEY_NUMPADENTER:
                if (widgetDef.isEditTextMultiline()) {
                    insertText("\n");
                    return true;
                }
                return false;
            case KeyInput.KEY_A:
                if (ctrl) {
                    selectAll();
                    return true;
                }
                break;
            case KeyInput.KEY_C:
                if (ctrl) {
                    copySelection();
                    return true;
                }
                break;
            case KeyInput.KEY_X:
                if (ctrl) {
                    copySelection();
                    deleteSelection();
                    return true;
                }
                break;
            case KeyInput.KEY_V:
                if (ctrl) {
                    insertText(readClipboard());
                    return true;
                }
                break;
            default:
                break;
        }

        if (!ctrl && keyChar >= 32 && keyChar != 127) {
            insertText(String.valueOf(keyChar));
            return true;
        }
        return false;
    }

    private void moveCaret(int newIndex, boolean extendSelection) {
        if (extendSelection) {
            if (selectionAnchor < 0) {
                selectionAnchor = caretIndex;
            }
        } else {
            selectionAnchor = -1;
        }
        caretIndex = clampIndex(newIndex);
        caretVisible = true;
        blinkTimer = 0f;
        refreshVisuals();
    }

    private void insertText(String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        String insert = sanitizeText(value);
        if (insert.isEmpty()) {
            return;
        }
        String text = getText();
        int start = selectionStart();
        int end = selectionEnd();
        String updated = text.substring(0, start) + insert + text.substring(end);
        widgetDef.setText(updated);
        caretIndex = start + insert.length();
        selectionAnchor = -1;
        refreshVisuals();
    }

    private void backspace(boolean word) {
        if (hasSelection()) {
            deleteSelection();
            return;
        }
        if (caretIndex <= 0) {
            return;
        }
        String text = getText();
        int start = word ? previousWordIndex() : caretIndex - 1;
        widgetDef.setText(text.substring(0, start) + text.substring(caretIndex));
        caretIndex = start;
        refreshVisuals();
    }

    private void deleteForward(boolean word) {
        if (hasSelection()) {
            deleteSelection();
            return;
        }
        String text = getText();
        if (caretIndex >= text.length()) {
            return;
        }
        int end = word ? nextWordIndex() : caretIndex + 1;
        widgetDef.setText(text.substring(0, caretIndex) + text.substring(end));
        refreshVisuals();
    }

    private void deleteSelection() {
        if (!hasSelection()) {
            return;
        }
        String text = getText();
        int start = selectionStart();
        int end = selectionEnd();
        widgetDef.setText(text.substring(0, start) + text.substring(end));
        caretIndex = start;
        selectionAnchor = -1;
        refreshVisuals();
    }

    private void selectAll() {
        selectionAnchor = 0;
        caretIndex = getText().length();
        refreshVisuals();
    }

    private void copySelection() {
        if (!hasSelection()) {
            return;
        }
        try {
            StringSelection selection = new StringSelection(getText().substring(selectionStart(), selectionEnd()));
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        } catch (Exception ignored) {
        }
    }

    private String readClipboard() {
        try {
            Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            return data == null ? "" : String.valueOf(data);
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean hasSelection() {
        return selectionAnchor >= 0 && selectionAnchor != caretIndex;
    }

    private int selectionStart() {
        return hasSelection() ? Math.min(selectionAnchor, caretIndex) : caretIndex;
    }

    private int selectionEnd() {
        return hasSelection() ? Math.max(selectionAnchor, caretIndex) : caretIndex;
    }

    private String sanitizeText(String value) {
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        return widgetDef.isEditTextMultiline() ? normalized : normalized.replace('\n', ' ');
    }

    private int clampIndex(int index) {
        return Math.max(0, Math.min(getText().length(), index));
    }

    private int verticalCaretMove(int direction) {
        CaretPosition pos = caretPosition(caretIndex);
        List<String> lines = lines();
        float currentX = xForColumn(lines.get(pos.line), pos.column);
        int targetLine = Math.max(0, Math.min(lines.size() - 1, pos.line + direction));
        int targetColumn = columnForX(lines.get(targetLine), currentX);
        return indexForLineColumn(targetLine, targetColumn);
    }

    private int lineStartFor(int index) {
        CaretPosition pos = caretPosition(index);
        return indexForLineColumn(pos.line, 0);
    }

    private int lineEndFor(int index) {
        CaretPosition pos = caretPosition(index);
        return indexForLineColumn(pos.line, lines().get(pos.line).length());
    }

    private int previousWordIndex() {
        String text = getText();
        int i = Math.max(0, caretIndex - 1);
        while (i > 0 && Character.isWhitespace(text.charAt(i))) {
            i--;
        }
        while (i > 0 && !Character.isWhitespace(text.charAt(i - 1))) {
            i--;
        }
        return i;
    }

    private int nextWordIndex() {
        String text = getText();
        int i = caretIndex;
        while (i < text.length() && !Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private int indexFromLocal(float localX, float localY) {
        float lineHeight = lineHeight();
        float contentTop = firstLineBaselineY(getRuntimeHeight(), lineHeight);
        int line = (int) Math.floor((contentTop - localY) / lineHeight);
        List<String> lines = lines();
        line = Math.max(0, Math.min(lines.size() - 1, line));
        int column = columnForX(lines.get(line), localX - PADDING_X);
        return indexForLineColumn(line, column);
    }

    private CaretPosition caretPosition(int index) {
        int remaining = clampIndex(index);
        List<String> lines = lines();
        for (int i = 0; i < lines.size(); i++) {
            int len = lines.get(i).length();
            if (remaining <= len) {
                return new CaretPosition(i, remaining);
            }
            remaining -= len + 1;
        }
        int last = Math.max(0, lines.size() - 1);
        return new CaretPosition(last, lines.get(last).length());
    }

    private int indexForLineColumn(int line, int column) {
        List<String> lines = lines();
        int targetLine = Math.max(0, Math.min(lines.size() - 1, line));
        int index = 0;
        for (int i = 0; i < targetLine; i++) {
            index += lines.get(i).length() + 1;
        }
        return clampIndex(index + Math.max(0, Math.min(lines.get(targetLine).length(), column)));
    }

    private List<String> lines() {
        String[] raw = getText().split("\n", -1);
        List<String> result = new ArrayList<>();
        for (String line : raw) {
            result.add(line);
        }
        if (result.isEmpty()) {
            result.add("");
        }
        return result;
    }

    private void refreshVisuals() {
        for (Spatial spatial : editVisuals) {
            detachChild(spatial);
        }
        editVisuals.clear();

        if (textNode == null || placeholderNode == null || layoutRect == null) {
            return;
        }

        float fontSize = scaledFontSize();
        float lineHeight = lineHeight();
        float width = Math.max(1f, getRuntimeWidth());
        float height = Math.max(1f, getRuntimeHeight());
        String text = getText();
        float firstBaselineY = firstLineBaselineY(height, lineHeight);

        textNode.setSize(fontSize);
        textNode.setText(text);
        textNode.setColor(parseColor(widgetDef.getTextColor()));
        textNode.setLocalTranslation(PADDING_X, firstBaselineY, 0.14f);

        placeholderNode.setSize(fontSize);
        placeholderNode.setText(text.isEmpty() && !focused ? widgetDef.getEditTextPlaceholder() : "");
        placeholderNode.setLocalTranslation(PADDING_X, firstBaselineY, 0.13f);

        if (hasSelection()) {
            addSelectionQuads(fontSize, lineHeight, height);
        }
        if (focused && caretVisible) {
            CaretPosition pos = caretPosition(caretIndex);
            List<String> visibleLines = lines();
            float caretX = PADDING_X + xForColumn(visibleLines.get(pos.line), pos.column);
            float caretHeight = caretHeight(lineHeight);
            float caretY = lineBaselineY(height, lineHeight, pos.line) - caretHeight;
            addQuad("caret", caretX, caretY, Math.max(1f, caretHeight * 0.08f), caretHeight,
                    parseColor(widgetDef.getEditTextCursorColor()), 0.18f);
        }
        if (backgroundGeom != null) {
            backgroundGeom.setMesh(new Quad(width, height));
        }
    }

    private void addSelectionQuads(float fontSize, float lineHeight, float height) {
        int start = selectionStart();
        int end = selectionEnd();
        CaretPosition startPos = caretPosition(start);
        CaretPosition endPos = caretPosition(end);
        List<String> lines = lines();
        for (int line = startPos.line; line <= endPos.line; line++) {
            int colStart = line == startPos.line ? startPos.column : 0;
            int colEnd = line == endPos.line ? endPos.column : lines.get(line).length();
            if (colEnd <= colStart) {
                continue;
            }
            String textLine = lines.get(line);
            float x = PADDING_X + xForColumn(textLine, colStart);
            float selectionHeight = caretHeight(lineHeight);
            float y = lineBaselineY(height, lineHeight, line) - selectionHeight;
            float w = Math.max(1f, xForColumn(textLine, colEnd) - xForColumn(textLine, colStart));
            addQuad("selection_" + line, x, y, w, selectionHeight,
                    parseColor(widgetDef.getEditTextSelectionColor()), 0.11f);
        }
    }

    private void addQuad(String suffix, float x, float y, float width, float height, ColorRGBA color, float z) {
        Geometry geom = new Geometry(getName() + "_" + suffix, new Quad(width, height));
        Material mat = createColorMaterial(color);
        geom.setMaterial(mat);
        geom.setQueueBucket(RenderQueue.Bucket.Gui);
        geom.setLocalTranslation(x, y, z);
        attachChild(geom);
        editVisuals.add(geom);
    }

    private float scaledFontSize() {
        return Math.max(1f, widgetDef.getFontSize() * getScaleYFactor());
    }

    private float lineHeight() {
        return Math.max(1f, measuredGlyphHeight() * 1.15f);
    }

    private float caretHeight(float lineHeight) {
        return Math.max(2f, Math.min(lineHeight, measuredGlyphHeight()));
    }

    private float firstLineBaselineY(float height, float lineHeight) {
        if (!widgetDef.isEditTextMultiline()) {
            return Math.max(PADDING_Y + caretHeight(lineHeight), (height + caretHeight(lineHeight)) / 2f);
        }
        return height - PADDING_Y;
    }

    private float lineBaselineY(float height, float lineHeight, int line) {
        return firstLineBaselineY(height, lineHeight) - line * lineHeight;
    }

    private float xForColumn(String line, int column) {
        String safeLine = line == null ? "" : line;
        int safeColumn = Math.max(0, Math.min(safeLine.length(), column));
        return measureWidth(safeLine.substring(0, safeColumn));
    }

    private int columnForX(String line, float x) {
        String safeLine = line == null ? "" : line;
        if (safeLine.isEmpty() || x <= 0f) {
            return 0;
        }
        for (int i = 1; i <= safeLine.length(); i++) {
            float previous = measureWidth(safeLine.substring(0, i - 1));
            float current = measureWidth(safeLine.substring(0, i));
            float midpoint = previous + (current - previous) / 2f;
            if (x < midpoint) {
                return i - 1;
            }
        }
        return safeLine.length();
    }

    private float measureWidth(String value) {
        if (measureNode == null) {
            return 0f;
        }
        measureNode.setSize(scaledFontSize());
        measureNode.setText(value == null ? "" : value);
        return Math.max(0f, measureNode.getLineWidth());
    }

    private float measuredGlyphHeight() {
        if (measureNode == null) {
            return 1f;
        }
        measureNode.setSize(scaledFontSize());
        measureNode.setText("Mg");
        float height = measureNode.getHeight();
        if (height <= 0f || Float.isNaN(height) || Float.isInfinite(height)) {
            height = measureNode.getLineHeight();
        }
        return Math.max(1f, height);
    }

    private BitmapFont loadFont(String fontName) {
        if (fontName != null && !fontName.isEmpty() && assetsMapping != null) {
            ResourceFont resource = assetsMapping.getFontsIndex().get(fontName.toLowerCase());
            if (resource != null) {
                try {
                    return assetManager.loadFont(resource.path);
                } catch (Exception e) {
                    System.err.println("[UIEditText] Failed to load font: " + fontName + " path=" + resource.path);
                }
            }
        }
        return assetManager.loadFont("Interface/Fonts/Default.fnt");
    }

    private static final class CaretPosition {
        final int line;
        final int column;

        CaretPosition(int line, int column) {
            this.line = line;
            this.column = column;
        }
    }
}
