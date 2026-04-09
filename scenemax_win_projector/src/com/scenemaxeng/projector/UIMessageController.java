package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.UIMessageCommand;
import com.scenemaxeng.common.ui.widget.UIManager;
import com.scenemaxeng.common.ui.widget.UITextViewNode;
import com.scenemaxeng.common.ui.widget.UIWidgetNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Runtime controller for animated UI text messages:
 *   UI.layer1.panel1.text1.message("Hello", TextEffect.typewriter, 2)
 */
public class UIMessageController extends SceneMaxBaseController {

    private UITextViewNode textView;
    private String fullText;
    private Set<String> effectNames;
    private float durationSeconds;
    private float elapsedSeconds;
    private int[] weightedCharThresholds;
    private int totalCharWeight;
    private int[] wordRevealEnds;
    private boolean initialized;

    public UIMessageController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, UIMessageCommand cmd) {
        super(app, prg, scope, cmd);
    }

    @Override
    public boolean run(float tpf) {
        if (forceStop) return true;

        if (!initialized) {
            if (!initializeMessage()) {
                return true;
            }
        }

        if (durationSeconds <= 0f) {
            applyFinishedState();
            return true;
        }

        elapsedSeconds = Math.min(durationSeconds, elapsedSeconds + tpf);
        float progress = Math.min(1f, elapsedSeconds / durationSeconds);
        applyProgress(progress);
        return progress >= 1f;
    }

    private boolean initializeMessage() {
        initialized = true;
        UIMessageCommand msgCmd = (UIMessageCommand) this.cmd;
        UIManager uiManager = app.getUIManager();

        if (uiManager == null) {
            app.handleRuntimeError("UI system not initialized");
            return false;
        }

        UIWidgetNode widget = uiManager.resolveWidget(msgCmd.uiName, msgCmd.layerName, msgCmd.widgetPath);
        String commandPathPrefix = msgCmd.uiName != null && !msgCmd.uiName.isEmpty()
                ? msgCmd.uiName + "." + msgCmd.layerName
                : msgCmd.layerName;

        if (widget == null) {
            app.handleRuntimeError("UI widget not found: " + commandPathPrefix + "." + msgCmd.widgetPath);
            return false;
        }
        if (!(widget instanceof UITextViewNode)) {
            app.handleRuntimeError("UI message target must be a text widget: " + commandPathPrefix + "." + msgCmd.widgetPath);
            return false;
        }

        textView = (UITextViewNode) widget;
        fullText = evaluateExpression(msgCmd.messageExpr);
        effectNames = new LinkedHashSet<>();
        for (String effectName : msgCmd.effectNames) {
            effectNames.add(normalizeEffectName(effectName));
        }
        durationSeconds = parseDurationSeconds(evaluateExpression(msgCmd.durationExpr));
        elapsedSeconds = 0f;

        prepareRevealData(fullText);
        textView.setWidgetVisible(true);
        textView.resetVisualScale();
        textView.resetVisualAlpha();
        applyProgress(durationSeconds <= 0f ? 1f : 0f);
        return true;
    }

    private String evaluateExpression(com.abware.scenemaxlang.parser.SceneMaxParser.Logical_expressionContext expr) {
        Object value = new ActionLogicalExpressionVm(expr, this.scope).evaluate();
        return value == null ? "" : value.toString();
    }

    private float parseDurationSeconds(String value) {
        try {
            return Math.max(0f, Float.parseFloat(value));
        } catch (NumberFormatException ex) {
            app.handleRuntimeError("Invalid UI message duration: " + value);
            return 0f;
        }
    }

    private String normalizeEffectName(String rawEffect) {
        return rawEffect == null ? "" : rawEffect.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private void prepareRevealData(String message) {
        weightedCharThresholds = new int[message.length()];
        int weightTotal = 0;
        for (int i = 0; i < message.length(); i++) {
            weightTotal += charWeight(message.charAt(i));
            weightedCharThresholds[i] = weightTotal;
        }
        totalCharWeight = weightTotal;

        List<Integer> wordEnds = new ArrayList<>();
        boolean inWord = false;
        for (int i = 0; i < message.length(); i++) {
            char ch = message.charAt(i);
            boolean whitespace = Character.isWhitespace(ch);
            if (!whitespace) {
                inWord = true;
            }
            if (inWord && (whitespace || i == message.length() - 1)) {
                wordEnds.add(whitespace ? i : i + 1);
                inWord = false;
            }
        }
        wordRevealEnds = new int[wordEnds.size()];
        for (int i = 0; i < wordEnds.size(); i++) {
            wordRevealEnds[i] = wordEnds.get(i);
        }
    }

    private int charWeight(char ch) {
        if (Character.isWhitespace(ch)) return 1;
        switch (ch) {
            case '.':
            case '!':
            case '?':
                return 4;
            case ',':
            case ';':
            case ':':
                return 3;
            default:
                return 1;
        }
    }

    private void applyProgress(float progress) {
        if (effectNames == null || effectNames.isEmpty() || effectNames.contains("none")) {
            applyFinishedState();
            return;
        }

        validateEffects();

        boolean hasTypewriter = effectNames.contains("typewriter");
        boolean hasWordReveal = effectNames.contains("word_reveal") || effectNames.contains("chunk_reveal");

        if (hasTypewriter) {
            int visibleChars = resolveVisibleChars(progress);
            textView.setText(fullText.substring(0, visibleChars));
        } else if (hasWordReveal) {
            textView.setText(fullText.substring(0, resolveVisibleWordEnd(progress)));
        } else {
            textView.setText(fullText);
        }

        textView.setVisualScale(resolveScale(progress));
        textView.setVisualAlpha(resolveAlpha(progress));
    }

    private void applyFinishedState() {
        textView.setText(fullText);
        textView.resetVisualScale();
        textView.resetVisualAlpha();
    }

    private void validateEffects() {
        List<String> unsupported = new ArrayList<>();
        for (String effectName : effectNames) {
            if (!isSupportedEffect(effectName)) {
                unsupported.add(effectName);
            }
        }
        if (!unsupported.isEmpty()) {
            for (String effectName : unsupported) {
                app.handleRuntimeError("Unsupported TextEffect '" + effectName + "'");
            }
            effectNames.removeAll(unsupported);
        }
    }

    private boolean isSupportedEffect(String effectName) {
        return "typewriter".equals(effectName)
                || "typewriter_zoom_in".equals(effectName)
                || "word_reveal".equals(effectName)
                || "chunk_reveal".equals(effectName)
                || "zoom_in".equals(effectName)
                || "zoom_out".equals(effectName)
                || "fade_in".equals(effectName)
                || "fade_out".equals(effectName)
                || "none".equals(effectName);
    }

    private float resolveScale(float progress) {
        if (effectNames.contains("typewriter_zoom_in")) {
            return lerp(0.82f, 1f, easeOut(progress));
        }
        if (effectNames.contains("zoom_in")) {
            return lerp(0.75f, 1f, easeOut(progress));
        }
        if (effectNames.contains("zoom_out")) {
            return lerp(1.35f, 1f, easeOut(progress));
        }
        return 1f;
    }

    private float resolveAlpha(float progress) {
        float alpha = 1f;
        if (effectNames.contains("fade_in")) {
            alpha *= progress;
        }
        if (effectNames.contains("fade_out")) {
            alpha *= (1f - progress);
        }

        boolean revealOnly = effectNames.contains("typewriter")
                || effectNames.contains("typewriter_zoom_in")
                || effectNames.contains("word_reveal")
                || effectNames.contains("chunk_reveal");
        if (alpha == 1f && revealOnly) {
            return progress <= 0f ? 0f : 1f;
        }
        return Math.max(0f, Math.min(1f, alpha));
    }

    private int resolveVisibleChars(float progress) {
        if (fullText.isEmpty()) {
            return 0;
        }
        if (progress >= 1f || totalCharWeight <= 0) {
            return fullText.length();
        }
        int threshold = Math.max(1, Math.round(totalCharWeight * progress));
        for (int i = 0; i < weightedCharThresholds.length; i++) {
            if (weightedCharThresholds[i] >= threshold) {
                return i + 1;
            }
        }
        return fullText.length();
    }

    private int resolveVisibleWordEnd(float progress) {
        if (fullText.isEmpty() || wordRevealEnds.length == 0) {
            return progress >= 1f ? fullText.length() : 0;
        }
        if (progress >= 1f) {
            return fullText.length();
        }
        int wordCount = Math.max(1, Math.round(wordRevealEnds.length * progress));
        return wordRevealEnds[Math.min(wordRevealEnds.length, wordCount) - 1];
    }

    private float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    private float easeOut(float progress) {
        float inv = 1f - progress;
        return 1f - inv * inv * inv;
    }
}
