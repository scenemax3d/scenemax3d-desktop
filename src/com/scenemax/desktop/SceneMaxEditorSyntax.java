package com.scenemax.desktop;

import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Style;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.folding.FoldParserManager;

import java.awt.Color;
import java.io.File;
import java.util.Locale;

final class SceneMaxEditorSyntax {
    static final String SYNTAX_STYLE_SCENEMAX = "text/scenemax3d";

    private static boolean registered;

    private SceneMaxEditorSyntax() {
    }

    static void initialize(RSyntaxTextArea textArea) {
        registerSceneMaxTokenMaker();
        textArea.setUI(new GentleBracketMatchTextAreaUI(textArea));

        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        Util.applyDarkTheme(textArea);
        applyGentleBracketMatching(textArea);
        applySceneMax(textArea);
    }

    static void applyForFile(RSyntaxTextArea textArea, String filePath) {
        if (isJavaFile(filePath)) {
            applyJava(textArea);
        } else {
            applySceneMax(textArea);
        }
    }

    private static void registerSceneMaxTokenMaker() {
        if (registered) {
            return;
        }

        TokenMakerFactory factory = TokenMakerFactory.getDefaultInstance();
        if (factory instanceof AbstractTokenMakerFactory) {
            ((AbstractTokenMakerFactory) factory).putMapping(
                    SYNTAX_STYLE_SCENEMAX,
                    SceneMaxTokenManager.class.getName());
            FoldParserManager.get().addFoldParserMapping(SYNTAX_STYLE_SCENEMAX, new SceneMaxFoldParser());
            registered = true;
        }
    }

    private static boolean isJavaFile(String filePath) {
        if (filePath == null) {
            return false;
        }
        return new File(filePath).getName().toLowerCase(Locale.ROOT).endsWith(".java");
    }

    private static void applyJava(RSyntaxTextArea textArea) {
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        Util.applyDarkTheme(textArea);
        applyGentleBracketMatching(textArea);
    }

    private static void applySceneMax(RSyntaxTextArea textArea) {
        textArea.setSyntaxEditingStyle(SYNTAX_STYLE_SCENEMAX);
        textArea.setBackground(new Color(31, 34, 40));
        textArea.setForeground(new Color(202, 207, 216));
        textArea.setCaretColor(new Color(218, 222, 229));
        textArea.setSelectionColor(new Color(63, 70, 82));
        textArea.setCurrentLineHighlightColor(new Color(37, 41, 48));
        applyGentleBracketMatching(textArea);
        textArea.setSyntaxScheme(createSceneMaxScheme(textArea));
    }

    private static void applyGentleBracketMatching(RSyntaxTextArea textArea) {
        textArea.setAnimateBracketMatching(false);
        textArea.setPaintMatchedBracketPair(true);
        textArea.setMatchedBracketBGColor(null);
        textArea.setMatchedBracketBorderColor(new Color(166, 187, 194, 135));
    }

    private static SyntaxScheme createSceneMaxScheme(RSyntaxTextArea textArea) {
        SyntaxScheme scheme = (SyntaxScheme) textArea.getSyntaxScheme().clone();

        scheme.setStyle(Token.IDENTIFIER, new Style(new Color(202, 207, 216)));
        scheme.setStyle(Token.RESERVED_WORD, new Style(new Color(183, 168, 217)));
        scheme.setStyle(Token.RESERVED_WORD_2, new Style(new Color(174, 199, 207)));
        scheme.setStyle(Token.DATA_TYPE, new Style(new Color(193, 185, 145)));
        scheme.setStyle(Token.FUNCTION, new Style(new Color(184, 199, 161)));
        scheme.setStyle(Token.VARIABLE, new Style(new Color(197, 205, 214)));
        scheme.setStyle(Token.LITERAL_BOOLEAN, new Style(new Color(172, 187, 209)));
        scheme.setStyle(Token.LITERAL_NUMBER_DECIMAL_INT, new Style(new Color(172, 187, 209)));
        scheme.setStyle(Token.LITERAL_NUMBER_FLOAT, new Style(new Color(172, 187, 209)));
        scheme.setStyle(Token.LITERAL_STRING_DOUBLE_QUOTE, new Style(new Color(201, 178, 143)));
        scheme.setStyle(Token.LITERAL_CHAR, new Style(new Color(201, 178, 143)));
        scheme.setStyle(Token.COMMENT_EOL, new Style(new Color(126, 135, 148)));
        scheme.setStyle(Token.SEPARATOR, new Style(new Color(174, 199, 207)));
        scheme.setStyle(Token.OPERATOR, new Style(new Color(185, 167, 161)));
        scheme.setStyle(Token.ERROR_STRING_DOUBLE, new Style(new Color(207, 146, 146)));
        scheme.setStyle(Token.ERROR_CHAR, new Style(new Color(207, 146, 146)));

        return scheme;
    }
}
