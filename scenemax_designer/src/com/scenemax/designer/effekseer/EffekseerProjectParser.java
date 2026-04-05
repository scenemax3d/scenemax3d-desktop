package com.scenemax.designer.effekseer;

import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class EffekseerProjectParser {

    private EffekseerProjectParser() {
    }

    public static EffekseerProject parse(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setIgnoringComments(true);
        factory.setIgnoringElementContentWhitespace(true);
        Document document = factory.newDocumentBuilder().parse(file);
        document.getDocumentElement().normalize();

        EffekseerProject project = new EffekseerProject();
        project.setName(file.getName());
        project.setLoop(parseBoolean(textOfFirst(document.getDocumentElement(), "IsLoop"), true));
        project.setEndFrame(parseFloat(textOfFirst(document.getDocumentElement(), "EndFrame"), 120f));
        Element behavior = firstChild(document.getDocumentElement(), "Behavior");
        if (behavior != null) {
            copyVectorFixed(firstChild(behavior, "TargetLocation"), project.getTargetLocation());
        }

        Element root = firstChild(document.getDocumentElement(), "Root");
        if (root != null) {
            project.setName(textOfFirst(root, "Name"));
            Element children = firstChild(root, "Children");
            if (children != null) {
                parseChildren(children, project, project.getName());
            }
        }
        return project;
    }

    private static void parseChildren(Element children, EffekseerProject project, String parentPath) {
        NodeList nodeList = children.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node instanceof Element && "Node".equals(node.getNodeName())) {
                Element element = (Element) node;
                String nodeName = textOfFirst(element, "Name");
                String nodePath = parentPath + "/" + (nodeName.isBlank() ? "Node" + (i + 1) : nodeName);
                EffekseerSpriteEmitter emitter = parseEmitterNode(element, project, nodePath);
                if (emitter != null) {
                    project.getEmitters().add(emitter);
                }
            }
        }
    }

    private static EffekseerSpriteEmitter parseEmitterNode(Element nodeElement, EffekseerProject project, String nodePath) {
        project.incrementParsedNodeCount();
        EffekseerSpriteEmitter emitter = new EffekseerSpriteEmitter();
        emitter.setName(textOfFirst(nodeElement, "Name"));
        List<String> localDiagnostics = new ArrayList<>();

        Element common = firstChild(nodeElement, "CommonValues");
        if (common != null) {
            Element maxGeneration = firstChild(common, "MaxGeneration");
            if (maxGeneration != null) {
                emitter.setInfinite(parseBoolean(textOfFirst(maxGeneration, "Infinite"), false));
                emitter.setMaxGeneration(Math.max(1, Math.round(parseFloat(textOfFirst(maxGeneration, "Value"), 30f))));
            }
            Element life = firstChild(common, "Life");
            if (life != null) {
                emitter.setHasExplicitLife(true);
                emitter.setLifeMinFrames(readRangeMin(life, 30f));
                emitter.setLifeMaxFrames(readRangeMax(life, emitter.getLifeMinFrames()));
            }
            Element generation = firstChild(common, "GenerationTime");
            if (generation != null) {
                emitter.setHasExplicitGenerationTime(true);
                emitter.setGenerationFrames(Math.max(0.1f, readRangeMin(generation, 1f)));
            }
            Element startDelay = firstChild(common, "GenerationTimeOffset");
            if (startDelay != null) {
                emitter.setStartDelayFrames(readRangeMin(startDelay, 0f));
            }
            emitter.setInheritParentPosition("1".equals(textOfFirst(common, "LocationEffectType")));
            if (parseBoolean(textOfFirst(common, "RemoveWhenLifeIsExtinct"), false)) {
                localDiagnostics.add("removes itself when life ends");
            }
        }

        Element locationValues = firstChild(nodeElement, "LocationValues");
        if (locationValues != null) {
            String locationType = textOfFirst(locationValues, "Type");
            if (!locationType.isBlank() && !"0".equals(locationType) && !"1".equals(locationType)) {
                localDiagnostics.add("uses unsupported LocationValues type " + locationType);
            }
            Element pva = firstChild(locationValues, "PVA");
            if (pva != null) {
                copyVectorRange(firstChild(pva, "Location"), emitter.getPositionMin(), emitter.getPositionMax());
                copyVectorRange(firstChild(pva, "Velocity"), emitter.getVelocityMin(), emitter.getVelocityMax());
                copyVectorCenter(firstChild(pva, "Acceleration"), emitter.getAcceleration());
            }
            if (firstChild(locationValues, "Easing") != null) {
                localDiagnostics.add("uses location easing that the embedded preview ignores");
            }
        }
        Element locationAbsValues = firstChild(nodeElement, "LocationAbsValues");
        if (locationAbsValues != null && "2".equals(textOfFirst(locationAbsValues, "Type"))) {
            Element attractiveForce = firstChild(locationAbsValues, "AttractiveForce");
            if (attractiveForce != null) {
                emitter.setAttractiveForce(parseFloat(textOfFirst(attractiveForce, "Force"), 0f));
                emitter.setAttractiveControl(parseFloat(textOfFirst(attractiveForce, "Control"), 0f));
            }
        } else if (locationAbsValues != null) {
            String locationAbsType = textOfFirst(locationAbsValues, "Type");
            if (!locationAbsType.isBlank() && !"0".equals(locationAbsType) && !"2".equals(locationAbsType)) {
                localDiagnostics.add("uses unsupported absolute motion type " + locationAbsType);
            }
            Element gravity = firstChild(locationAbsValues, "Gravity");
            if (gravity != null) {
                Vector3f gravityVector = new Vector3f();
                copyVectorFixed(firstChild(gravity, "Gravity"), gravityVector);
                emitter.getAcceleration().addLocal(gravityVector);
            }
        }

        Element rotationValues = firstChild(nodeElement, "RotationValues");
        if (rotationValues != null) {
            String rotationType = textOfFirst(rotationValues, "Type");
            if (!rotationType.isBlank() && !"0".equals(rotationType) && !"1".equals(rotationType)) {
                localDiagnostics.add("uses unsupported RotationValues type " + rotationType);
            }
            Element fixed = firstChild(rotationValues, "Fixed");
            if (fixed != null) {
                copyVectorFixed(firstChild(fixed, "Rotation"), emitter.getFixedRotationDeg());
            }
            Element pva = firstChild(rotationValues, "PVA");
            if (pva != null) {
                copyVectorFixed(firstChild(pva, "Rotation"), emitter.getFixedRotationDeg());
                emitter.setRotationVelocityDeg(readRangeCenter(firstChild(firstChild(pva, "Velocity"), "Z"), 0f));
            }
            if (firstChild(rotationValues, "Easing") != null) {
                localDiagnostics.add("uses rotation easing that the embedded preview ignores");
            }
        }

        Element scalingValues = firstChild(nodeElement, "ScalingValues");
        if (scalingValues != null) {
            String scalingType = textOfFirst(scalingValues, "Type");
            if (!scalingType.isBlank() && !"0".equals(scalingType) && !"1".equals(scalingType)) {
                localDiagnostics.add("uses scaling type " + scalingType + " that is only partially supported");
            }
            Element fixed = firstChild(scalingValues, "Fixed");
            if (fixed != null) {
                Element fixedScale = firstChild(fixed, "Scale");
                float sx = parseFloat(textOfFirst(fixedScale, "X"), 1f);
                float sy = parseFloat(textOfFirst(fixedScale, "Y"), 1f);
                emitter.setWidth(Math.max(0.1f, sx));
                emitter.setHeight(Math.max(0.1f, sy));
                emitter.setEndWidth(emitter.getWidth());
                emitter.setEndHeight(emitter.getHeight());
            }
            Element pva = firstChild(scalingValues, "PVA");
            if (pva != null) {
                copyVectorCenter(firstChild(pva, "Velocity"), emitter.getScaleVelocity());
            }
            Element easing = firstChild(scalingValues, "Easing");
            if (easing != null && "2".equals(scalingType)) {
                applyScaleEasing(easing, emitter);
            }
        }

        Element renderer = firstChild(nodeElement, "RendererCommonValues");
        if (renderer != null) {
            emitter.setTexturePath(textOfFirst(renderer, "ColorTexture"));
            emitter.setAdditiveBlend("2".equals(textOfFirst(renderer, "AlphaBlend")));
            Element fadeOut = firstChild(renderer, "FadeOut");
            if (fadeOut != null) {
                emitter.setFadeOutFrames(parseFloat(textOfFirst(fadeOut, "Frame"), 0f));
            }
        }

        Element drawing = firstChild(nodeElement, "DrawingValues");
        boolean supportedAsSprite = drawing == null;
        String unsupportedRenderer = "";
        if (drawing != null) {
            String type = textOfFirst(drawing, "Type");
            Element sprite = firstChild(drawing, "Sprite");
            if (sprite != null) {
                supportedAsSprite = true;
                emitter.setRendererType(EffekseerSpriteEmitter.RendererType.SPRITE);
                emitter.setBillboard(!"2".equals(textOfFirst(sprite, "Billboard")));
                if ((emitter.getTexturePath() == null || emitter.getTexturePath().isBlank())) {
                    emitter.setTexturePath(textOfFirst(sprite, "ColorTexture"));
                }
                String spriteAlphaBlend = textOfFirst(sprite, "AlphaBlend");
                if (!spriteAlphaBlend.isBlank()) {
                    emitter.setAdditiveBlend("2".equals(spriteAlphaBlend));
                }
                applySpriteSize(sprite, emitter);
                applySpriteColors(sprite, emitter);
            } else if ("3".equals(type) && firstChild(drawing, "Ribbon") != null) {
                supportedAsSprite = true;
                emitter.setRendererType(EffekseerSpriteEmitter.RendererType.RIBBON);
                applyRibbon(firstChild(drawing, "Ribbon"), emitter);
            } else if (type.isBlank() || "0".equals(type)) {
                supportedAsSprite = true;
                if (firstChild(nodeElement, "Children") != null && (emitter.getTexturePath() == null || emitter.getTexturePath().isBlank())) {
                    localDiagnostics.add("acts as a container node with children but has no renderable texture; current preview may skip its subtree");
                }
            } else {
                unsupportedRenderer = describeUnsupportedRenderer(type, drawing);
                localDiagnostics.add("uses unsupported renderer " + unsupportedRenderer);
            }
        }

        Element generationLocationValues = firstChild(nodeElement, "GenerationLocationValues");
        if (generationLocationValues != null) {
            String generationType = textOfFirst(generationLocationValues, "Type");
            if (!generationType.isBlank() && !"0".equals(generationType)) {
                localDiagnostics.add("uses generation shape type " + generationType + " that the embedded preview ignores");
            }
        }

        Element children = firstChild(nodeElement, "Children");
        if (children != null) {
            NodeList nodeList = children.getChildNodes();
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (node instanceof Element && "Node".equals(node.getNodeName())) {
                    Element childElement = (Element) node;
                    String childName = textOfFirst(childElement, "Name");
                    String childPath = nodePath + "/" + (childName.isBlank() ? "Node" + (i + 1) : childName);
                    EffekseerSpriteEmitter child = parseEmitterNode(childElement, project, childPath);
                    if (child != null) {
                        emitter.getChildren().add(child);
                    }
                }
            }
        }
        boolean hasTexture = emitter.getTexturePath() != null && !emitter.getTexturePath().isBlank();
        if (supportedAsSprite && hasTexture) {
            project.incrementPreviewableEmitterCount();
            addDiagnostics(project, nodePath, localDiagnostics);
            return emitter;
        }

        if (!supportedAsSprite) {
            project.incrementSkippedNodeCount();
            project.addDiagnostic(nodePath + " was skipped because the embedded preview does not support " + unsupportedRenderer + ".");
        } else if (!hasTexture) {
            project.incrementSkippedNodeCount();
            if (!emitter.getChildren().isEmpty()) {
                project.addDiagnostic(nodePath + " was skipped because it has no renderable texture; its " + emitter.getChildren().size() + " parsed child node(s) cannot currently be reattached under a non-renderable parent.");
            } else {
                project.addDiagnostic(nodePath + " was skipped because it has no renderable texture.");
            }
        }
        addDiagnostics(project, nodePath, localDiagnostics);
        return null;
    }

    private static void addDiagnostics(EffekseerProject project, String nodePath, List<String> localDiagnostics) {
        for (String diagnostic : localDiagnostics) {
            project.addDiagnostic(nodePath + ": " + diagnostic + ".");
        }
    }

    private static String describeUnsupportedRenderer(String type, Element drawing) {
        if (firstChild(drawing, "Track") != null) {
            return "Track";
        }
        if (firstChild(drawing, "Stripe") != null) {
            return "Stripe";
        }
        if (firstChild(drawing, "Ring") != null) {
            return "Ring";
        }
        if (firstChild(drawing, "Model") != null) {
            return "Model";
        }
        return "type " + type;
    }

    private static void applySpriteSize(Element sprite, EffekseerSpriteEmitter emitter) {
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        boolean sawCorner = false;
        String[][] corners = {
                {"Position_Fixed_LL", "-0.5", "0.0"},
                {"Position_Fixed_LR", "0.5", "0.0"},
                {"Position_Fixed_UL", "-0.5", "1.0"},
                {"Position_Fixed_UR", "0.5", "1.0"}
        };
        for (String[] corner : corners) {
            Element element = firstChild(sprite, corner[0]);
            if (element == null) {
                continue;
            }
            sawCorner = true;
            float x = parseFloat(textOfFirst(element, "X"), parseFloat(corner[1], 0f));
            float y = parseFloat(textOfFirst(element, "Y"), parseFloat(corner[2], 0f));
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        if (sawCorner) {
            float spriteWidth = Math.max(0.1f, maxX - minX);
            float spriteHeight = Math.max(0.1f, maxY - minY);
            emitter.setWidth(Math.max(emitter.getWidth(), spriteWidth));
            emitter.setHeight(Math.max(emitter.getHeight(), spriteHeight));
            emitter.setEndWidth(Math.max(emitter.getEndWidth(), spriteWidth));
            emitter.setEndHeight(Math.max(emitter.getEndHeight(), spriteHeight));
        }
    }

    private static void applySpriteColors(Element sprite, EffekseerSpriteEmitter emitter) {
        Element fixed = firstChild(sprite, "ColorAll_Fixed");
        if (fixed != null) {
            applyColor(readColor(fixed, ColorRGBA.White), emitter.getStartColor());
            applyColor(readColor(fixed, ColorRGBA.White), emitter.getEndColor());
        }

        Element random = firstChild(sprite, "ColorAll_Random");
        if (random != null) {
            applyColor(readColor(random, emitter.getStartColor()), emitter.getStartColor());
            applyColor(readColor(random, emitter.getEndColor()), emitter.getEndColor());
        }

        Element easing = firstChild(sprite, "ColorAll_Easing");
        if (easing != null) {
            Element start = firstChild(easing, "Start");
            if (start != null) {
                applyColor(readColor(start, emitter.getStartColor()), emitter.getStartColor());
            }
            Element end = firstChild(easing, "End");
            if (end != null) {
                applyColor(readColor(end, emitter.getEndColor()), emitter.getEndColor());
            }
        }
    }

    private static void applyRibbon(Element ribbon, EffekseerSpriteEmitter emitter) {
        emitter.setBillboard(parseBoolean(textOfFirst(ribbon, "ViewpointDependent"), true));
        Element fixed = firstChild(ribbon, "ColorAll_Fixed");
        if (fixed != null) {
            applyColor(readColor(fixed, ColorRGBA.White), emitter.getStartColor());
            applyColor(readColor(fixed, ColorRGBA.White), emitter.getEndColor());
        }
        Element random = firstChild(ribbon, "ColorAll_Random");
        if (random != null) {
            applyColor(readColor(random, emitter.getStartColor()), emitter.getStartColor());
            applyColor(readColor(random, emitter.getEndColor()), emitter.getEndColor());
        }
        emitter.setWidth(Math.max(0.2f, emitter.getWidth()));
        emitter.setHeight(Math.max(0.2f, emitter.getHeight()));
        emitter.setEndWidth(Math.max(0.2f, emitter.getEndWidth()));
        emitter.setEndHeight(Math.max(0.2f, emitter.getEndHeight()));
    }

    private static void applyScaleEasing(Element easing, EffekseerSpriteEmitter emitter) {
        Element start = firstChild(easing, "Start");
        Element end = firstChild(easing, "End");

        float startX = readRangeCenter(firstChild(start, "X"), emitter.getWidth());
        float startY = readRangeCenter(firstChild(start, "Y"), emitter.getHeight());
        float startZ = readRangeCenter(firstChild(start, "Z"), 1f);
        float endX = readRangeCenter(firstChild(end, "X"), startX);
        float endY = readRangeCenter(firstChild(end, "Y"), startY);
        float endZ = readRangeCenter(firstChild(end, "Z"), startZ);

        float resolvedStartWidth = Math.max(0.1f, Math.max(startX, startZ));
        float resolvedStartHeight = Math.max(0.1f, startY);
        float resolvedEndWidth = Math.max(0.1f, Math.max(endX, endZ));
        float resolvedEndHeight = Math.max(0.1f, endY);

        emitter.setWidth(resolvedStartWidth);
        emitter.setHeight(resolvedStartHeight);
        emitter.setEndWidth(resolvedEndWidth);
        emitter.setEndHeight(resolvedEndHeight);
    }

    private static ColorRGBA readColor(Element element, ColorRGBA fallback) {
        float r = parseColorComponent(element, "R", fallback.r);
        float g = parseColorComponent(element, "G", fallback.g);
        float b = parseColorComponent(element, "B", fallback.b);
        float a = parseColorComponent(element, "A", fallback.a);
        return new ColorRGBA(r, g, b, a);
    }

    private static float parseColorComponent(Element parent, String childName, float fallback) {
        Element child = firstChild(parent, childName);
        if (child == null) {
            return fallback;
        }
        if (child.getChildNodes().getLength() == 1 && child.getFirstChild() != null && child.getFirstChild().getNodeType() == Node.TEXT_NODE) {
            return toColor(parseFloat(child.getTextContent(), fallback * 255f));
        }
        return toColor(readRangeMax(child, fallback * 255f));
    }

    private static float toColor(float value255) {
        return FastMath.clamp(value255 / 255f, 0f, 1f);
    }

    private static void applyColor(ColorRGBA from, ColorRGBA to) {
        to.set(from.r, from.g, from.b, from.a);
    }

    private static void copyVectorRange(Element source, Vector3f min, Vector3f max) {
        if (source == null) {
            return;
        }
        min.set(readRangeMin(firstChild(source, "X"), 0f), readRangeMin(firstChild(source, "Y"), 0f), readRangeMin(firstChild(source, "Z"), 0f));
        max.set(readRangeMax(firstChild(source, "X"), min.x), readRangeMax(firstChild(source, "Y"), min.y), readRangeMax(firstChild(source, "Z"), min.z));
    }

    private static void copyVectorCenter(Element source, Vector3f target) {
        if (source == null) {
            return;
        }
        target.set(readRangeCenter(firstChild(source, "X"), 0f), readRangeCenter(firstChild(source, "Y"), 0f), readRangeCenter(firstChild(source, "Z"), 0f));
    }

    private static void copyVectorFixed(Element source, Vector3f target) {
        if (source == null) {
            return;
        }
        target.set(parseFloat(textOfFirst(source, "X"), target.x), parseFloat(textOfFirst(source, "Y"), target.y), parseFloat(textOfFirst(source, "Z"), target.z));
    }

    private static Element firstChild(Element parent, String childName) {
        if (parent == null) {
            return null;
        }
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element && childName.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        return null;
    }

    private static String textOfFirst(Element parent, String childName) {
        Element child = firstChild(parent, childName);
        return child != null ? child.getTextContent().trim() : "";
    }

    private static float readRangeMin(Element element, float fallback) {
        if (element == null) {
            return fallback;
        }
        String direct = directText(element);
        if (!direct.isBlank()) {
            return parseFloat(direct, fallback);
        }
        String text = textOfFirst(element, "Min");
        if (!text.isBlank()) {
            return parseFloat(text, fallback);
        }
        text = textOfFirst(element, "Center");
        if (!text.isBlank()) {
            return parseFloat(text, fallback);
        }
        text = textOfFirst(element, "Value");
        return parseFloat(text, fallback);
    }

    private static float readRangeMax(Element element, float fallback) {
        if (element == null) {
            return fallback;
        }
        String direct = directText(element);
        if (!direct.isBlank()) {
            return parseFloat(direct, fallback);
        }
        String text = textOfFirst(element, "Max");
        if (!text.isBlank()) {
            return parseFloat(text, fallback);
        }
        text = textOfFirst(element, "Center");
        if (!text.isBlank()) {
            return parseFloat(text, fallback);
        }
        text = textOfFirst(element, "Value");
        return parseFloat(text, fallback);
    }

    private static float readRangeCenter(Element element, float fallback) {
        if (element == null) {
            return fallback;
        }
        String direct = directText(element);
        if (!direct.isBlank()) {
            return parseFloat(direct, fallback);
        }
        String text = textOfFirst(element, "Center");
        if (!text.isBlank()) {
            return parseFloat(text, fallback);
        }
        float min = readRangeMin(element, fallback);
        float max = readRangeMax(element, min);
        return (min + max) * 0.5f;
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private static float parseFloat(String value, float fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String directText(Element element) {
        if (element == null) {
            return "";
        }
        NodeList nodes = element.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.TEXT_NODE) {
                String text = node.getTextContent();
                if (text != null && !text.trim().isBlank()) {
                    return text.trim();
                }
            }
        }
        return "";
    }
}
