package com.scenemaxeng.common.ui.widget;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import com.scenemaxeng.common.ui.layout.LayoutRect;
import com.scenemaxeng.common.ui.model.UIWidgetDef;

/**
 * A button widget - a colored rectangle with a centered text label.
 */
public class UIButtonNode extends UIWidgetNode {

    private BitmapText textNode;
    private ColorRGBA normalColor;
    private ColorRGBA pressedColor;
    private boolean isPressed = false;

    public UIButtonNode(String name, UIWidgetDef widgetDef, AssetManager assetManager,
                        float designCanvasWidth, float designCanvasHeight,
                        float runtimeCanvasWidth, float runtimeCanvasHeight) {
        super(name, widgetDef, assetManager, designCanvasWidth, designCanvasHeight, runtimeCanvasWidth, runtimeCanvasHeight);
    }

    @Override
    public void createVisual() {
        normalColor = parseColor(widgetDef.getButtonColor());
        pressedColor = parseColor(widgetDef.getButtonPressedColor());

        Quad quad = new Quad(widgetDef.getWidth(), widgetDef.getHeight());
        backgroundGeom = new Geometry(getName() + "_bg", quad);
        backgroundGeom.setMaterial(createColorMaterial(normalColor));
        backgroundGeom.setQueueBucket(RenderQueue.Bucket.Gui);
        attachChild(backgroundGeom);

        BitmapFont font = assetManager.loadFont("Interface/Fonts/Default.fnt");
        textNode = new BitmapText(font, false);
        textNode.setSize(widgetDef.getFontSize());
        textNode.setText(widgetDef.getButtonText());
        textNode.setColor(parseColor(widgetDef.getButtonTextColor()));
        textNode.setQueueBucket(RenderQueue.Bucket.Gui);
        attachChild(textNode);

        centerText();
    }

    @Override
    protected void onLayoutUpdated(LayoutRect rect) {
        centerText();
    }

    private void centerText() {
        if (textNode == null || layoutRect == null) {
            return;
        }

        float textWidth = textNode.getLineWidth();
        float textHeight = textNode.getLineHeight();
        float tx = (layoutRect.width - textWidth) / 2f;
        float ty = (layoutRect.height + textHeight) / 2f;
        textNode.setLocalTranslation(tx, ty, 0.1f);
    }

    public void setPressed(boolean pressed) {
        this.isPressed = pressed;
        if (backgroundGeom != null) {
            backgroundGeom.setMaterial(createColorMaterial(pressed ? pressedColor : normalColor));
        }
    }

    public void setButtonText(String text) {
        widgetDef.setButtonText(text);
        if (textNode != null) {
            textNode.setText(text);
            centerText();
        }
    }

    public void setButtonTextColor(String hexColor) {
        widgetDef.setButtonTextColor(hexColor);
        if (textNode != null) {
            textNode.setColor(parseColor(hexColor));
        }
    }

    public void setBackgroundColor(String hexColor) {
        widgetDef.setButtonColor(hexColor);
        normalColor = parseColor(hexColor);
        if (!isPressed && backgroundGeom != null) {
            backgroundGeom.setMaterial(createColorMaterial(normalColor));
        }
    }

    public boolean isPressed() {
        return isPressed;
    }
}
