package com.scenemax.desktop;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public final class BitmapFontGenerator {

    public static final String BASIC_LATIN_CHARSET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789" +
            " !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";

    public static final String LATIN_AND_HEBREW_CHARSET =
            BASIC_LATIN_CHARSET + "אבגדהוזחטיכלמנסעפצקרשתךםןףץ";

    private BitmapFontGenerator() {
    }

    public static GeneratedFont generate(FontGeneratorOptions options, File outputDir, String assetName) throws IOException {
        FontAtlas atlas = buildAtlas(options);
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Failed creating output folder: " + outputDir.getAbsolutePath());
        }

        File pngFile = new File(outputDir, assetName + ".png");
        File fntFile = new File(outputDir, assetName + ".fnt");
        ImageIO.write(atlas.image, "png", pngFile);
        Files.writeString(fntFile.toPath(), buildFnt(assetName, atlas), StandardCharsets.UTF_8);
        return new GeneratedFont(pngFile, fntFile);
    }

    public static BufferedImage createPreview(FontGeneratorOptions options, String text, int width, int height) {
        String previewText = text == null || text.isBlank() ? "SceneMax Font" : text;
        Font font = options.createFont();
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        applyRenderingHints(g, options.isAntiAlias());
        g.setColor(new Color(18, 18, 22));
        g.fillRect(0, 0, width, height);
        g.setColor(new Color(46, 46, 56));
        g.fillRoundRect(8, 8, width - 16, height - 16, 18, 18);

        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D metricsGraphics = scratch.createGraphics();
        applyRenderingHints(metricsGraphics, options.isAntiAlias());
        metricsGraphics.setFont(font);
        FontMetrics metrics = metricsGraphics.getFontMetrics();
        int extraTop = options.getPadding() + options.getOutlineWidth() + Math.max(0, -options.getShadowOffsetY()) + 4;
        int baseline = Math.max(extraTop + metrics.getAscent(), (height + metrics.getAscent() - metrics.getDescent()) / 2);
        drawString(g, font, previewText, 24, Math.min(height - 16, baseline), options);
        metricsGraphics.dispose();
        g.dispose();
        return canvas;
    }

    public static FontAtlas buildAtlas(FontGeneratorOptions options) {
        Font font = options.createFont();
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scratch.createGraphics();
        applyRenderingHints(g, options.isAntiAlias());
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        FontRenderContext frc = g.getFontRenderContext();

        int padding = options.getPadding();
        int outlineWidth = options.getOutlineWidth();
        int shadowX = options.getShadowOffsetX();
        int shadowY = options.getShadowOffsetY();
        int extraLeft = padding + outlineWidth + Math.max(0, -shadowX);
        int extraRight = padding + outlineWidth + Math.max(0, shadowX);
        int extraTop = padding + outlineWidth + Math.max(0, -shadowY);
        int extraBottom = padding + outlineWidth + Math.max(0, shadowY);
        int lineHeight = metrics.getHeight() + extraTop + extraBottom;
        int base = extraTop + metrics.getAscent();

        List<GlyphSprite> glyphs = new ArrayList<>();
        String charset = options.getSanitizedCharset();
        for (int i = 0; i < charset.length(); i++) {
            glyphs.add(createGlyphSprite(charset.charAt(i), font, frc, metrics, options, extraLeft, extraRight, lineHeight, base));
        }
        g.dispose();

        PackedLayout layout = pack(glyphs, 1024);
        BufferedImage atlas = new BufferedImage(layout.width, layout.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D atlasGraphics = atlas.createGraphics();
        applyRenderingHints(atlasGraphics, options.isAntiAlias());
        for (GlyphSprite glyph : glyphs) {
            atlasGraphics.drawImage(glyph.image, glyph.x, glyph.y, null);
        }
        atlasGraphics.dispose();
        return new FontAtlas(atlas, glyphs, layout.width, layout.height, lineHeight, base);
    }

    private static GlyphSprite createGlyphSprite(char ch,
                                                 Font font,
                                                 FontRenderContext frc,
                                                 FontMetrics metrics,
                                                 FontGeneratorOptions options,
                                                 int extraLeft,
                                                 int extraRight,
                                                 int lineHeight,
                                                 int base) {
        GlyphVector glyphVector = font.createGlyphVector(frc, String.valueOf(ch));
        GlyphMetrics glyphMetrics = glyphVector.getGlyphMetrics(0);
        int xAdvance = Math.max(1, Math.round(glyphMetrics.getAdvanceX()));

        if (Character.isWhitespace(ch)) {
            return new GlyphSprite(ch, new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), 1, 1, 0, 0, xAdvance);
        }

        Rectangle rawBounds = glyphVector.getPixelBounds(frc, 0, 0);
        int drawX = extraLeft + Math.max(0, -rawBounds.x);
        int width = Math.max(
                xAdvance + extraLeft + extraRight,
                drawX + rawBounds.x + rawBounds.width + options.getOutlineWidth() + Math.max(0, options.getShadowOffsetX()) + options.getPadding()
        );
        width = Math.max(1, width);

        BufferedImage glyphImage = new BufferedImage(width, lineHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D glyphGraphics = glyphImage.createGraphics();
        applyRenderingHints(glyphGraphics, options.isAntiAlias());
        drawGlyph(glyphGraphics, glyphVector, drawX, base, options);
        glyphGraphics.dispose();

        return new GlyphSprite(ch, glyphImage, width, lineHeight, -drawX, 0, xAdvance);
    }

    private static PackedLayout pack(List<GlyphSprite> glyphs, int maxWidth) {
        int x = 0;
        int y = 0;
        int rowHeight = 0;
        int usedWidth = 0;

        for (GlyphSprite glyph : glyphs) {
            if (x + glyph.width > maxWidth && x > 0) {
                x = 0;
                y += rowHeight;
                rowHeight = 0;
            }

            glyph.x = x;
            glyph.y = y;
            x += glyph.width;
            rowHeight = Math.max(rowHeight, glyph.height);
            usedWidth = Math.max(usedWidth, x);
        }

        return new PackedLayout(nextPowerOfTwo(Math.max(1, usedWidth)), nextPowerOfTwo(y + rowHeight));
    }

    private static void drawString(Graphics2D graphics, Font font, String text, int x, int baseline, FontGeneratorOptions options) {
        GlyphVector glyphVector = font.createGlyphVector(graphics.getFontRenderContext(), text);
        drawGlyph(graphics, glyphVector, x, baseline, options);
    }

    private static void drawGlyph(Graphics2D graphics, GlyphVector glyphVector, int x, int baseline, FontGeneratorOptions options) {
        Shape shape = glyphVector.getOutline(x, baseline);

        if (options.hasShadow()) {
            graphics.setColor(options.getShadowColor());
            Shape shadow = AffineTransform.getTranslateInstance(options.getShadowOffsetX(), options.getShadowOffsetY())
                    .createTransformedShape(shape);
            graphics.fill(shadow);
        }

        if (options.getOutlineWidth() > 0) {
            graphics.setColor(options.getOutlineColor());
            graphics.setStroke(new BasicStroke(options.getOutlineWidth() * 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.draw(shape);
        }

        graphics.setPaint(options.createFillPaint(shape.getBounds2D()));
        graphics.fill(shape);
    }

    private static void applyRenderingHints(Graphics2D graphics, boolean antiAlias) {
        Object aa = antiAlias ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF;
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, aa);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antiAlias ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setComposite(AlphaComposite.SrcOver);
    }

    private static String buildFnt(String assetName, FontAtlas atlas) {
        StringBuilder builder = new StringBuilder();
        builder.append("info face=\"").append(assetName).append("\" size=").append(atlas.lineHeight)
                .append(" bold=0 italic=0 charset=\"\" unicode=1 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=1,1\n");
        builder.append("common lineHeight=").append(atlas.lineHeight)
                .append(" base=").append(atlas.base)
                .append(" scaleW=").append(atlas.width)
                .append(" scaleH=").append(atlas.height)
                .append(" pages=1 packed=0\n");
        builder.append("page id=0 file=\"").append(assetName).append(".png\"\n");
        builder.append("chars count=").append(atlas.glyphs.size()).append("\n");
        for (GlyphSprite glyph : atlas.glyphs) {
            builder.append("char id=").append((int) glyph.ch)
                    .append(" x=").append(glyph.x)
                    .append(" y=").append(glyph.y)
                    .append(" width=").append(glyph.width)
                    .append(" height=").append(glyph.height)
                    .append(" xoffset=").append(glyph.xOffset)
                    .append(" yoffset=").append(glyph.yOffset)
                    .append(" xadvance=").append(glyph.xAdvance)
                    .append(" page=0 chnl=15\n");
        }
        builder.append("kernings count=0\n");
        return builder.toString();
    }

    private static int nextPowerOfTwo(int value) {
        int result = 1;
        while (result < value) {
            result <<= 1;
        }
        return result;
    }

    public static final class FontGeneratorOptions {
        private String fontFamily = "Dialog";
        private int fontStyle = Font.PLAIN;
        private int fontSize = 64;
        private int padding = 6;
        private int outlineWidth = 3;
        private int shadowOffsetX = 3;
        private int shadowOffsetY = 3;
        private boolean antiAlias = true;
        private boolean gradientEnabled = true;
        private Color fillColor = new Color(255, 242, 163);
        private Color gradientColor = new Color(255, 127, 39);
        private Color outlineColor = new Color(51, 18, 77);
        private Color shadowColor = new Color(0, 0, 0, 140);
        private String characters = LATIN_AND_HEBREW_CHARSET;

        public Font createFont() {
            return new Font(fontFamily, fontStyle, fontSize);
        }

        public Paint createFillPaint(Rectangle2D bounds) {
            if (!gradientEnabled) {
                return fillColor;
            }
            return new GradientPaint(
                    (float) bounds.getCenterX(),
                    (float) bounds.getMinY(),
                    fillColor,
                    (float) bounds.getCenterX(),
                    (float) bounds.getMaxY(),
                    gradientColor
            );
        }

        public boolean hasShadow() {
            return shadowColor.getAlpha() > 0 && (shadowOffsetX != 0 || shadowOffsetY != 0);
        }

        public String getSanitizedCharset() {
            String source = characters == null ? "" : characters;
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < source.length(); i++) {
                char ch = source.charAt(i);
                if (builder.indexOf(String.valueOf(ch)) == -1) {
                    builder.append(ch);
                }
            }
            if (builder.indexOf(" ") == -1) {
                builder.append(' ');
            }
            return builder.toString();
        }

        public String getFontFamily() { return fontFamily; }
        public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }
        public int getFontStyle() { return fontStyle; }
        public void setFontStyle(int fontStyle) { this.fontStyle = fontStyle; }
        public int getFontSize() { return fontSize; }
        public void setFontSize(int fontSize) { this.fontSize = fontSize; }
        public int getPadding() { return padding; }
        public void setPadding(int padding) { this.padding = padding; }
        public int getOutlineWidth() { return outlineWidth; }
        public void setOutlineWidth(int outlineWidth) { this.outlineWidth = outlineWidth; }
        public int getShadowOffsetX() { return shadowOffsetX; }
        public void setShadowOffsetX(int shadowOffsetX) { this.shadowOffsetX = shadowOffsetX; }
        public int getShadowOffsetY() { return shadowOffsetY; }
        public void setShadowOffsetY(int shadowOffsetY) { this.shadowOffsetY = shadowOffsetY; }
        public boolean isAntiAlias() { return antiAlias; }
        public void setAntiAlias(boolean antiAlias) { this.antiAlias = antiAlias; }
        public boolean isGradientEnabled() { return gradientEnabled; }
        public void setGradientEnabled(boolean gradientEnabled) { this.gradientEnabled = gradientEnabled; }
        public Color getFillColor() { return fillColor; }
        public void setFillColor(Color fillColor) { this.fillColor = fillColor; }
        public Color getGradientColor() { return gradientColor; }
        public void setGradientColor(Color gradientColor) { this.gradientColor = gradientColor; }
        public Color getOutlineColor() { return outlineColor; }
        public void setOutlineColor(Color outlineColor) { this.outlineColor = outlineColor; }
        public Color getShadowColor() { return shadowColor; }
        public void setShadowColor(Color shadowColor) { this.shadowColor = shadowColor; }
        public String getCharacters() { return characters; }
        public void setCharacters(String characters) { this.characters = characters; }
    }

    public static final class GeneratedFont {
        private final File pngFile;
        private final File fntFile;

        public GeneratedFont(File pngFile, File fntFile) {
            this.pngFile = pngFile;
            this.fntFile = fntFile;
        }

        public File getPngFile() { return pngFile; }
        public File getFntFile() { return fntFile; }
    }

    public static final class FontAtlas {
        private final BufferedImage image;
        private final List<GlyphSprite> glyphs;
        private final int width;
        private final int height;
        private final int lineHeight;
        private final int base;

        public FontAtlas(BufferedImage image, List<GlyphSprite> glyphs, int width, int height, int lineHeight, int base) {
            this.image = image;
            this.glyphs = glyphs;
            this.width = width;
            this.height = height;
            this.lineHeight = lineHeight;
            this.base = base;
        }
    }

    private static final class GlyphSprite {
        private final char ch;
        private final BufferedImage image;
        private final int width;
        private final int height;
        private final int xOffset;
        private final int yOffset;
        private final int xAdvance;
        private int x;
        private int y;

        private GlyphSprite(char ch, BufferedImage image, int width, int height, int xOffset, int yOffset, int xAdvance) {
            this.ch = ch;
            this.image = image;
            this.width = width;
            this.height = height;
            this.xOffset = xOffset;
            this.yOffset = yOffset;
            this.xAdvance = xAdvance;
        }
    }

    private static final class PackedLayout {
        private final int width;
        private final int height;

        private PackedLayout(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }
}
