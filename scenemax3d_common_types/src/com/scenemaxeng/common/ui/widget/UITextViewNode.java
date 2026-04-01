package com.scenemaxeng.common.ui.widget;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Spatial;
import com.scenemaxeng.common.types.AssetsMapping;
import com.scenemaxeng.common.types.ResourceFont;
import com.scenemaxeng.common.ui.layout.LayoutRect;
import com.scenemaxeng.common.ui.model.UIWidgetDef;

/**
 * A text display widget - renders text using JME's BitmapText.
 */
public class UITextViewNode extends UIWidgetNode {

    private BitmapText textNode;
    private AssetsMapping assetsMapping;

    public UITextViewNode(String name, UIWidgetDef widgetDef, AssetManager assetManager,
                          float designCanvasWidth, float designCanvasHeight,
                          float runtimeCanvasWidth, float runtimeCanvasHeight,
                          AssetsMapping assetsMapping) {
        super(name, widgetDef, assetManager, designCanvasWidth, designCanvasHeight, runtimeCanvasWidth, runtimeCanvasHeight);
        this.assetsMapping = assetsMapping;
    }

    @Override
    public void createVisual() {
        BitmapFont font = loadFont(widgetDef.getFontName());
        textNode = new BitmapText(font, false);
        textNode.setSize(widgetDef.getFontSize());
        textNode.setText(widgetDef.getText());
        textNode.setColor(parseColor(widgetDef.getTextColor()));
        textNode.setQueueBucket(RenderQueue.Bucket.Gui);
        attachChild(textNode);

        positionText();
    }

    @Override
    protected void onLayoutUpdated(LayoutRect rect) {
        positionText();
    }

    private BitmapFont loadFont(String fontName) {
        if (fontName != null && !fontName.isEmpty() && assetsMapping != null) {
            ResourceFont resource = assetsMapping.getFontsIndex().get(fontName.toLowerCase());
            if (resource != null) {
                try {
                    return assetManager.loadFont(resource.path);
                } catch (Exception e) {
                    System.err.println("[UIText] Failed to load font: " + fontName + " path=" + resource.path);
                }
            } else {
                System.err.println("[UIText] Font not found in AssetsMapping: " + fontName);
            }
        }
        return assetManager.loadFont("Interface/Fonts/Default.fnt");
    }

    private void positionText() {
        if (textNode == null || layoutRect == null) {
            return;
        }

        float textWidth = textNode.getLineWidth();
        float textHeight = textNode.getLineHeight();
        float tx;

        String align = widgetDef.getTextAlignment();
        if ("center".equals(align)) {
            tx = (layoutRect.width - textWidth) / 2f;
        } else if ("right".equals(align)) {
            tx = layoutRect.width - textWidth;
        } else {
            tx = 0;
        }

        float ty = (layoutRect.height + textHeight) / 2f;
        // Keep text slightly in front of UI quads so it doesn't get occluded.
        textNode.setLocalTranslation(tx, ty, 0.1f);
    }

    public void setText(String text) {
        widgetDef.setText(text);
        if (textNode != null) {
            textNode.setText(text);
            positionText();
        }
    }

    public void setTextColor(String hexColor) {
        widgetDef.setTextColor(hexColor);
        if (textNode != null) {
            textNode.setColor(parseColor(hexColor));
        }
    }

    public void setFontSize(float size) {
        widgetDef.setFontSize(size);
        if (textNode != null) {
            textNode.setSize(size);
            positionText();
        }
    }

    public void setFontName(String fontName) {
        widgetDef.setFontName(fontName);
        if (textNode != null) {
            // BitmapText doesn't support changing font after creation, so recreate it
            String currentText = textNode.getText();
            float currentSize = widgetDef.getFontSize();
            String currentColor = widgetDef.getTextColor();
            detachChild(textNode);

            BitmapFont font = loadFont(fontName);
            textNode = new BitmapText(font, false);
            textNode.setSize(currentSize);
            textNode.setText(currentText);
            textNode.setColor(parseColor(currentColor));
            textNode.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Gui);
            attachChild(textNode);
            positionText();
        }
    }

    public String getText() {
        return widgetDef.getText();
    }

    @Override
    public Spatial getShaderTarget() {
        return textNode != null ? textNode : super.getShaderTarget();
    }
}
