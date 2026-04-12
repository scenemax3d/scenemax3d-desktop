package com.scenemax.designer.material;

import com.jme3.math.ColorRGBA;

public enum MaterialTemplatePreset {
    MATTE_PAINT("Matte Paint") {
        @Override
        public void applyTo(MaterialDocument doc) {
            reset(doc);
            doc.setBaseColor(new ColorRGBA(0.85f, 0.24f, 0.18f, 1f));
            doc.setAmbientStrength(0.35f);
            doc.setSpecularStrength(0.12f);
            doc.setShininess(6f);
        }
    },
    BRICK_WALL("Brick Wall") {
        @Override
        public void applyTo(MaterialDocument doc) {
            reset(doc);
            doc.setDiffuseTexture("Textures/StarterMaterials/BrickWall/diffuse.png");
            doc.setNormalTexture("Textures/StarterMaterials/BrickWall/normal.png");
            doc.setGlowTexture("Textures/StarterMaterials/BrickWall/glow.png");
            doc.setBaseColor(ColorRGBA.White.clone());
            doc.setAmbientStrength(0.45f);
            doc.setSpecularStrength(0.35f);
            doc.setShininess(18f);
        }
    },
    STONE("Stone") {
        @Override
        public void applyTo(MaterialDocument doc) {
            reset(doc);
            doc.setDiffuseTexture("Textures/StarterMaterials/Rock/diffuse.png");
            doc.setNormalTexture("Textures/StarterMaterials/Rock/normal.png");
            doc.setGlowTexture("Textures/StarterMaterials/Rock/glow.png");
            doc.setBaseColor(new ColorRGBA(0.92f, 0.92f, 0.92f, 1f));
            doc.setAmbientStrength(0.5f);
            doc.setSpecularStrength(0.25f);
            doc.setShininess(14f);
        }
    },
    RETRO_CARPET("Retro Carpet") {
        @Override
        public void applyTo(MaterialDocument doc) {
            reset(doc);
            doc.setPreviewShape(MaterialPreviewShape.SPHERE);
            doc.setDiffuseTexture("Textures/StarterMaterials/BackroomsCarpet/diffuse.png");
            doc.setNormalTexture("Textures/StarterMaterials/BackroomsCarpet/normal.png");
            doc.setGlowTexture("Textures/StarterMaterials/BackroomsCarpet/glow.png");
            doc.setBaseColor(new ColorRGBA(1f, 0.97f, 0.82f, 1f));
            doc.setAmbientStrength(0.42f);
            doc.setSpecularStrength(0.16f);
            doc.setShininess(10f);
        }
    },
    LUMEN_PANEL("Lumen Panel") {
        @Override
        public void applyTo(MaterialDocument doc) {
            reset(doc);
            doc.setDiffuseTexture("Textures/StarterMaterials/BackroomsCeilingLight/diffuse.png");
            doc.setNormalTexture("Textures/StarterMaterials/BackroomsCeilingLight/normal.png");
            doc.setGlowTexture("Textures/StarterMaterials/BackroomsCeilingLight/glow.png");
            doc.setBaseColor(new ColorRGBA(0.95f, 0.97f, 1f, 1f));
            doc.setGlowColor(new ColorRGBA(0.9f, 0.97f, 1f, 1f));
            doc.setGlowStrength(1.45f);
            doc.setAmbientStrength(0.38f);
            doc.setSpecularStrength(0.55f);
            doc.setShininess(44f);
        }
    },
    MARBLE_WHITE("Marble White") {
        @Override
        public void applyTo(MaterialDocument doc) {
            reset(doc);
            doc.setPreviewShape(MaterialPreviewShape.SPHERE);
            doc.setDiffuseTexture("Textures/StarterMaterials/MarbleWhite/diffuse.png");
            doc.setNormalTexture("Textures/StarterMaterials/MarbleWhite/normal.png");
            doc.setGlowTexture("Textures/StarterMaterials/MarbleWhite/glow.png");
            doc.setBaseColor(new ColorRGBA(0.98f, 0.98f, 0.96f, 1f));
            doc.setAmbientStrength(0.3f);
            doc.setSpecularStrength(1.25f);
            doc.setShininess(72f);
        }
    },
    PARQUET_FLOOR("Parquet Floor") {
        @Override
        public void applyTo(MaterialDocument doc) {
            reset(doc);
            doc.setDiffuseTexture("Textures/StarterMaterials/WoodFloorParquet/diffuse.png");
            doc.setNormalTexture("Textures/StarterMaterials/WoodFloorParquet/normal.png");
            doc.setGlowTexture("Textures/StarterMaterials/WoodFloorParquet/glow.png");
            doc.setBaseColor(new ColorRGBA(1f, 0.98f, 0.94f, 1f));
            doc.setAmbientStrength(0.42f);
            doc.setSpecularStrength(0.42f);
            doc.setShininess(28f);
        }
    },
    GREEN_TILE("Green Tile") {
        @Override
        public void applyTo(MaterialDocument doc) {
            reset(doc);
            doc.setDiffuseTexture("Textures/StarterMaterials/SquareTileGreen/diffuse.png");
            doc.setNormalTexture("Textures/StarterMaterials/SquareTileGreen/normal.png");
            doc.setGlowTexture("Textures/StarterMaterials/SquareTileGreen/glow.png");
            doc.setBaseColor(ColorRGBA.White.clone());
            doc.setAmbientStrength(0.32f);
            doc.setSpecularStrength(0.72f);
            doc.setShininess(64f);
        }
    },
    LEATHER_UPHOLSTERY("Leather Upholstery") {
        @Override
        public void applyTo(MaterialDocument doc) {
            reset(doc);
            doc.setPreviewShape(MaterialPreviewShape.SPHERE);
            doc.setDiffuseTexture("Textures/StarterMaterials/LeatherStitched/diffuse.png");
            doc.setNormalTexture("Textures/StarterMaterials/LeatherStitched/normal.png");
            doc.setGlowTexture("Textures/StarterMaterials/LeatherStitched/glow.png");
            doc.setBaseColor(new ColorRGBA(0.9f, 0.82f, 0.74f, 1f));
            doc.setAmbientStrength(0.36f);
            doc.setSpecularStrength(0.8f);
            doc.setShininess(58f);
        }
    },
    WOVEN_FABRIC("Woven Fabric") {
        @Override
        public void applyTo(MaterialDocument doc) {
            reset(doc);
            doc.setPreviewShape(MaterialPreviewShape.SPHERE);
            doc.setDiffuseTexture("Textures/StarterMaterials/FabricWeaveWhite/diffuse.png");
            doc.setNormalTexture("Textures/StarterMaterials/FabricWeaveWhite/normal.png");
            doc.setGlowTexture("Textures/StarterMaterials/FabricWeaveWhite/glow.png");
            doc.setBaseColor(new ColorRGBA(0.96f, 0.95f, 0.93f, 1f));
            doc.setAmbientStrength(0.5f);
            doc.setSpecularStrength(0.12f);
            doc.setShininess(9f);
        }
    },
    BLACK_METAL_PANEL("Black Metal Panel") {
        @Override
        public void applyTo(MaterialDocument doc) {
            reset(doc);
            doc.setDiffuseTexture("Textures/StarterMaterials/MetalPaintedBlack/diffuse.png");
            doc.setNormalTexture("Textures/StarterMaterials/MetalPaintedBlack/normal.png");
            doc.setGlowTexture("Textures/StarterMaterials/MetalPaintedBlack/glow.png");
            doc.setBaseColor(new ColorRGBA(0.78f, 0.8f, 0.84f, 1f));
            doc.setAmbientStrength(0.22f);
            doc.setSpecularStrength(1.1f);
            doc.setShininess(78f);
        }
    },
    POLISHED_METAL("Polished Metal") {
        @Override
        public void applyTo(MaterialDocument doc) {
            reset(doc);
            doc.setBaseColor(new ColorRGBA(0.78f, 0.8f, 0.84f, 1f));
            doc.setAmbientStrength(0.28f);
            doc.setSpecularStrength(1.8f);
            doc.setShininess(96f);
        }
    },
    GLASS("Glass") {
        @Override
        public void applyTo(MaterialDocument doc) {
            reset(doc);
            doc.setBaseColor(new ColorRGBA(0.66f, 0.85f, 1f, 0.35f));
            doc.setAmbientStrength(0.18f);
            doc.setSpecularStrength(2f);
            doc.setShininess(120f);
            doc.setOpacity(0.35f);
            doc.setTransparent(true);
            doc.setDoubleSided(true);
        }
    },
    EMISSIVE_NEON("Emissive Neon") {
        @Override
        public void applyTo(MaterialDocument doc) {
            reset(doc);
            doc.setBaseColor(new ColorRGBA(0.08f, 0.12f, 0.16f, 1f));
            doc.setGlowColor(new ColorRGBA(0.1f, 1f, 0.95f, 1f));
            doc.setGlowStrength(1.7f);
            doc.setAmbientStrength(0.2f);
            doc.setSpecularStrength(0.8f);
            doc.setShininess(48f);
        }
    };

    private final String displayName;

    MaterialTemplatePreset(String displayName) {
        this.displayName = displayName;
    }

    public abstract void applyTo(MaterialDocument doc);

    protected void reset(MaterialDocument doc) {
        doc.setTemplate(this);
        doc.setPreviewShape(MaterialPreviewShape.BOX);
        doc.setPreviewScale(1f);
        doc.setBaseColor(ColorRGBA.White.clone());
        doc.setGlowColor(new ColorRGBA(0f, 0f, 0f, 1f));
        doc.setAmbientStrength(0.4f);
        doc.setSpecularStrength(1f);
        doc.setShininess(32f);
        doc.setOpacity(1f);
        doc.setGlowStrength(0f);
        doc.setAlphaDiscardThreshold(0f);
        doc.setTransparent(false);
        doc.setDoubleSided(false);
        doc.setDiffuseTexture("");
        doc.setNormalTexture("");
        doc.setGlowTexture("");
    }

    public static MaterialTemplatePreset fromName(String raw) {
        if (raw != null) {
            for (MaterialTemplatePreset preset : values()) {
                if (preset.name().equalsIgnoreCase(raw)) {
                    return preset;
                }
            }
        }
        return MATTE_PAINT;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
