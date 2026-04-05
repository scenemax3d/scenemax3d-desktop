package com.scenemax.designer.effekseer;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class EffekseerSpriteEmitter {

    public enum RendererType {
        SPRITE,
        RIBBON
    }

    private String name = "Emitter";
    private String texturePath = "";
    private RendererType rendererType = RendererType.SPRITE;
    private boolean additiveBlend;
    private boolean billboard = true;
    private boolean infinite;
    private boolean inheritParentPosition;
    private boolean hasExplicitLife;
    private boolean hasExplicitGenerationTime;
    private int maxGeneration = 30;
    private float lifeMinFrames = 30f;
    private float lifeMaxFrames = 30f;
    private float generationFrames = 1f;
    private float startDelayFrames = 0f;
    private float attractiveForce;
    private float attractiveControl;
    private final Vector3f positionMin = new Vector3f();
    private final Vector3f positionMax = new Vector3f();
    private final Vector3f velocityMin = new Vector3f();
    private final Vector3f velocityMax = new Vector3f();
    private final Vector3f acceleration = new Vector3f();
    private final Vector3f scaleVelocity = new Vector3f();
    private final Vector3f fixedRotationDeg = new Vector3f();
    private float rotationVelocityDeg = 0f;
    private float width = 1f;
    private float height = 1f;
    private float endWidth = 1f;
    private float endHeight = 1f;
    private float fadeOutFrames = 0f;
    private final ColorRGBA startColor = ColorRGBA.White.clone();
    private final ColorRGBA endColor = ColorRGBA.White.clone();
    private final List<EffekseerSpriteEmitter> children = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name : "Emitter";
    }

    public String getTexturePath() {
        return texturePath;
    }

    public void setTexturePath(String texturePath) {
        this.texturePath = texturePath != null ? texturePath.replace("\\", "/") : "";
    }

    public RendererType getRendererType() {
        return rendererType;
    }

    public void setRendererType(RendererType rendererType) {
        this.rendererType = rendererType != null ? rendererType : RendererType.SPRITE;
    }

    public boolean isAdditiveBlend() {
        return additiveBlend;
    }

    public void setAdditiveBlend(boolean additiveBlend) {
        this.additiveBlend = additiveBlend;
    }

    public boolean isBillboard() {
        return billboard;
    }

    public void setBillboard(boolean billboard) {
        this.billboard = billboard;
    }

    public boolean isInfinite() {
        return infinite;
    }

    public void setInfinite(boolean infinite) {
        this.infinite = infinite;
    }

    public boolean isInheritParentPosition() {
        return inheritParentPosition;
    }

    public void setInheritParentPosition(boolean inheritParentPosition) {
        this.inheritParentPosition = inheritParentPosition;
    }

    public boolean hasExplicitLife() {
        return hasExplicitLife;
    }

    public void setHasExplicitLife(boolean hasExplicitLife) {
        this.hasExplicitLife = hasExplicitLife;
    }

    public boolean hasExplicitGenerationTime() {
        return hasExplicitGenerationTime;
    }

    public void setHasExplicitGenerationTime(boolean hasExplicitGenerationTime) {
        this.hasExplicitGenerationTime = hasExplicitGenerationTime;
    }

    public int getMaxGeneration() {
        return maxGeneration;
    }

    public void setMaxGeneration(int maxGeneration) {
        this.maxGeneration = maxGeneration;
    }

    public float getLifeMinFrames() {
        return lifeMinFrames;
    }

    public void setLifeMinFrames(float lifeMinFrames) {
        this.lifeMinFrames = lifeMinFrames;
    }

    public float getLifeMaxFrames() {
        return lifeMaxFrames;
    }

    public void setLifeMaxFrames(float lifeMaxFrames) {
        this.lifeMaxFrames = lifeMaxFrames;
    }

    public float getGenerationFrames() {
        return generationFrames;
    }

    public void setGenerationFrames(float generationFrames) {
        this.generationFrames = generationFrames;
    }

    public float getStartDelayFrames() {
        return startDelayFrames;
    }

    public void setStartDelayFrames(float startDelayFrames) {
        this.startDelayFrames = startDelayFrames;
    }

    public float getAttractiveForce() {
        return attractiveForce;
    }

    public void setAttractiveForce(float attractiveForce) {
        this.attractiveForce = attractiveForce;
    }

    public float getAttractiveControl() {
        return attractiveControl;
    }

    public void setAttractiveControl(float attractiveControl) {
        this.attractiveControl = attractiveControl;
    }

    public Vector3f getPositionMin() {
        return positionMin;
    }

    public Vector3f getPositionMax() {
        return positionMax;
    }

    public Vector3f getVelocityMin() {
        return velocityMin;
    }

    public Vector3f getVelocityMax() {
        return velocityMax;
    }

    public Vector3f getAcceleration() {
        return acceleration;
    }

    public Vector3f getScaleVelocity() {
        return scaleVelocity;
    }

    public Vector3f getFixedRotationDeg() {
        return fixedRotationDeg;
    }

    public float getRotationVelocityDeg() {
        return rotationVelocityDeg;
    }

    public void setRotationVelocityDeg(float rotationVelocityDeg) {
        this.rotationVelocityDeg = rotationVelocityDeg;
    }

    public float getWidth() {
        return width;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public float getHeight() {
        return height;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    public float getEndWidth() {
        return endWidth;
    }

    public void setEndWidth(float endWidth) {
        this.endWidth = endWidth;
    }

    public float getEndHeight() {
        return endHeight;
    }

    public void setEndHeight(float endHeight) {
        this.endHeight = endHeight;
    }

    public float getFadeOutFrames() {
        return fadeOutFrames;
    }

    public void setFadeOutFrames(float fadeOutFrames) {
        this.fadeOutFrames = fadeOutFrames;
    }

    public ColorRGBA getStartColor() {
        return startColor;
    }

    public ColorRGBA getEndColor() {
        return endColor;
    }

    public List<EffekseerSpriteEmitter> getChildren() {
        return children;
    }
}
