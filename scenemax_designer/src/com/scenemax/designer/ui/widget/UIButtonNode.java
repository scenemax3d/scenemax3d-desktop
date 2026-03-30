package com.scenemax.designer.ui.widget;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import com.scenemax.designer.ui.layout.LayoutRect;
import com.scenemax.designer.ui.model.UIWidgetDef;

/**
 * A button widget — a colored rectangle with a centered text label.
 * Supports normal and pressed color states.
 *
 * Properties:
 *   buttonText         - the label text
 *   buttonTextColor    - hex color for the text
 *   buttonColor        - hex color for the normal state background
 *   buttonPressedColor - hex color for the pressed state background
 */
public class UIButtonNode extends UIWidgetNode {

    private BitmapText textNode;
    private ColorRGBA normalColor;
    private ColorRGBA pressedColor;
    private boolean isPressed = false;

    public UIButtonNode(String name, UIWidgetDef widgetDef, AssetManager assetManager, float canvasHeight) {
        super(name, widgetDef, assetManager, canvasHeight);
    }

    @Override
    public void createVisual() {
        normalColor = parseColor(widgetDef.getButtonColor());
        pressedColor = parseColor(widgetDef.getButtonPressedColor());

        // Background quad
        Quad quad = new Quad(widgetDef.getWidth(), widgetDef.getHeight());
        backgroundGeom = new Geometry(getName() + "_bg", quad);
        backgroundGeom.setMaterial(createColorMaterial(normalColor));
        backgroundGeom.setQueueBucket(RenderQueue.Bucket.Gui);
        attachChild(backgroundGeom);

        // Text label
        BitmapFont font = assetManager.loadFont("Interface/Fonts/Default.fnt");
        textNode = new BitmapText(font, false);
        textNode.setSize(widgetDef.getFontSize());
        textNode.setText(widgetDef.getButtonText());
        textNode.setColor(parseColor(widgetDef.getButtonTextColor()));
        textNode.setQueueBucket(RenderQueue.Bucket.Gui);
        attachChild(textNode);

        // Center the text in the button
        centerText();
    }

    @Override
    protected void onLayoutUpdated(LayoutRect rect) {
        centerText();
    }

    private void centerText() {
        if (textNode == null || layoutRect == null) return;

        float textWidth = textNode.getLineWidth();
        float textHeight = textNode.getLineHeight();
        float tx = (layoutRect.width - textWidth) / 2f;
        float ty = (layoutRect.height + textHeight) / 2f; // BitmapText baseline is at bottom
        textNode.setLocalTranslation(tx, ty, 0.1f); // slightly in front of background
    }

    /**
     * Sets the pressed visual state (call from input handling).
     */
    public void setPressed(boolean pressed) {
        this.isPressed = pressed;
        if (backgroundGeom != null) {
            backgroundGeom.setMaterial(createColorMaterial(pressed ? pressedColor : normalColor));
        }
    }

    /**
     * Updates button text at runtime.
     */
    public void setButtonText(String text) {
        widgetDef.setButtonText(text);
        if (textNode != null) {
            textNode.setText(text);
            centerText();
        }
    }

    /**
     * Updates the text color at runtime.
     */
    public void setButtonTextColor(String hexColor) {
        widgetDef.setButtonTextColor(hexColor);
        if (textNode != null) {
            textNode.setColor(parseColor(hexColor));
        }
    }

    public boolean isPressed() { return isPressed; }
}
