package com.scenemaxeng.common.ui.widget;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.renderer.queue.RenderQueue;
import com.scenemaxeng.common.ui.layout.LayoutRect;
import com.scenemaxeng.common.ui.model.UIWidgetDef;

/**
 * A text display widget - renders text using JME's BitmapText.
 */
public class UITextViewNode extends UIWidgetNode {

    private BitmapText textNode;

    public UITextViewNode(String name, UIWidgetDef widgetDef, AssetManager assetManager,
                          float designCanvasWidth, float designCanvasHeight,
                          float runtimeCanvasWidth, float runtimeCanvasHeight) {
        super(name, widgetDef, assetManager, designCanvasWidth, designCanvasHeight, runtimeCanvasWidth, runtimeCanvasHeight);
    }

    @Override
    public void createVisual() {
        BitmapFont font = assetManager.loadFont("Interface/Fonts/Default.fnt");
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

    public String getText() {
        return widgetDef.getText();
    }
}
