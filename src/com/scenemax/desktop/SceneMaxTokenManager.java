package com.scenemax.desktop;

import org.fife.ui.rsyntaxtextarea.AbstractTokenMaker;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenMap;

import javax.swing.text.Segment;

public class SceneMaxTokenManager extends AbstractTokenMaker {
    private static final String[] LINE_COMMENT = {"//", null};

    private static final String[] SCOPE_WORDS = {
            "do", "end", "then", "if", "else", "when", "while", "for", "foreach", "switch"
    };

    private static final String[] KEYWORDS = {
            "var", "shared", "run", "call", "async", "wait", "seconds", "is", "a", "an",
            "having", "and", "in", "at", "from", "to", "with", "of", "loop", "once", "every",
            "move", "rotate", "scale", "animate", "play", "hide", "show", "delete", "turn",
            "roll", "look", "pos", "stop", "push", "pop", "clear", "print", "accelerate",
            "steer", "brake", "turbo", "reset", "attach", "detach", "record", "replay",
            "belongs", "group", "dynamic", "static", "collider", "vehicle", "material",
            "radius", "height", "size", "gravity", "shadow", "mode", "hidden", "collision",
            "shape", "calibrate", "joints", "data", "camera", "chase", "follow", "trailing",
            "dungeon", "default", "fighting", "third_person", "first_person", "racing",
            "platformer", "rts", "modifiers", "apply", "hit_modifier", "fall_modifier",
            "shooting_modifier", "accelerating_modifier", "decelerating_modifier", "bump_modifier",
            "landing_modifier", "earthquake_modifier", "explosion_modifier", "near_miss_modifier",
            "vertical", "horizontal", "rotation", "max", "min", "distance", "damping", "type",
            "solar", "system", "terrain", "water", "cloud", "flattening", "cloudiness", "hour",
            "depth", "strength", "audio", "sound", "volume", "logger", "info", "debug", "error",
            "lights", "light", "probe", "directional", "point", "spot", "sky", "ambient",
            "direction", "intensity", "lumens", "range", "preset", "exposure", "low", "medium",
            "high", "warm", "cool", "screen", "scene", "pause", "resume", "full", "window",
            "effects", "minimap", "using", "code", "add", "pressed", "released", "engine",
            "power", "breaking", "suspension", "compression", "stiffness", "length", "front",
            "rear", "input", "reverse", "horn", "forward", "backward", "left", "right", "up",
            "down", "billboard", "wireframe", "outline", "offset", "duration", "emissions",
            "start", "draw", "frames", "frame", "append", "color", "font", "cast", "receive",
            "protected", "new", "class", "save", "after", "collides", "ray", "check", "file",
            "name", "contains", "each", "where", "http", "get", "post", "put", "ui", "load",
            "message", "texteffect", "ease", "java", "plugins", "animation", "rows", "cols",
            "times", "inner", "transitions", "commands", "ignore", "jump", "speedo", "tacho",
            "angle", "json", "looking", "not"
    };

    private static final String[] DATA_TYPES = {
            "sprite", "model", "sphere", "box", "cylinder", "quad", "hollow", "skybox",
            "character", "ragdoll", "kinematic", "floating", "rigid", "body", "wedge",
            "cone", "stairs", "arch"
    };

    private static final String[] FUNCTIONS = {
            "function", "return", "abs", "rnd", "round"
    };

    @Override
    public TokenMap getWordsToHighlight() {
        TokenMap tokenMap = new TokenMap(true);
        putAll(tokenMap, SCOPE_WORDS, Token.RESERVED_WORD_2);
        putAll(tokenMap, KEYWORDS, Token.RESERVED_WORD);
        putAll(tokenMap, DATA_TYPES, Token.DATA_TYPE);
        putAll(tokenMap, FUNCTIONS, Token.FUNCTION);
        tokenMap.put("true", Token.LITERAL_BOOLEAN);
        tokenMap.put("false", Token.LITERAL_BOOLEAN);
        tokenMap.put("on", Token.LITERAL_BOOLEAN);
        tokenMap.put("off", Token.LITERAL_BOOLEAN);
        return tokenMap;
    }

    @Override
    public void addToken(Segment segment, int start, int end, int tokenType, int startOffset) {
        if (tokenType == Token.IDENTIFIER) {
            int value = wordsToHighlight.get(segment, start, end);
            if (value != -1) {
                tokenType = value;
            }
        }
        super.addToken(segment, start, end, tokenType, startOffset);
    }

    @Override
    public boolean getCurlyBracesDenoteCodeBlocks(int languageIndex) {
        return true;
    }

    @Override
    public String[] getLineCommentStartAndEnd(int languageIndex) {
        return LINE_COMMENT;
    }

    @Override
    public Token getTokenList(Segment text, int startTokenType, int startOffset) {
        resetTokenList();

        char[] array = text.array;
        int offset = text.offset;
        int end = offset + text.count;
        int newStartOffset = startOffset - offset;
        int i = offset;

        while (i < end) {
            char c = array[i];
            int tokenStart = i;

            if (isWhitespace(c)) {
                i++;
                while (i < end && isWhitespace(array[i])) {
                    i++;
                }
                addToken(text, tokenStart, i - 1, Token.WHITESPACE, newStartOffset + tokenStart);
            } else if (c == '/' && i + 1 < end && array[i + 1] == '/') {
                addToken(text, tokenStart, end - 1, Token.COMMENT_EOL, newStartOffset + tokenStart);
                i = end;
            } else if (c == '"') {
                i = scanQuotedString(array, i + 1, end, '"');
                int tokenType = i <= end && array[i - 1] == '"' ? Token.LITERAL_STRING_DOUBLE_QUOTE : Token.ERROR_STRING_DOUBLE;
                addToken(text, tokenStart, i - 1, tokenType, newStartOffset + tokenStart);
            } else if (c == '\'') {
                i = scanQuotedString(array, i + 1, end, '\'');
                int tokenType = i <= end && array[i - 1] == '\'' ? Token.LITERAL_CHAR : Token.ERROR_CHAR;
                addToken(text, tokenStart, i - 1, tokenType, newStartOffset + tokenStart);
            } else if (Character.isDigit(c)) {
                i++;
                boolean seenDot = false;
                while (i < end) {
                    char ch = array[i];
                    if (Character.isDigit(ch)) {
                        i++;
                    } else if (ch == '.' && !seenDot) {
                        seenDot = true;
                        i++;
                    } else {
                        break;
                    }
                }
                addToken(text, tokenStart, i - 1,
                        seenDot ? Token.LITERAL_NUMBER_FLOAT : Token.LITERAL_NUMBER_DECIMAL_INT,
                        newStartOffset + tokenStart);
            } else if (isIdentifierStart(c)) {
                i++;
                while (i < end && isIdentifierPart(array[i])) {
                    i++;
                }
                addToken(text, tokenStart, i - 1, Token.IDENTIFIER, newStartOffset + tokenStart);
            } else if (isSeparator(c)) {
                addToken(text, tokenStart, tokenStart, Token.SEPARATOR, newStartOffset + tokenStart);
                i++;
            } else if (isOperator(c)) {
                i++;
                while (i < end && isOperator(array[i])) {
                    i++;
                }
                addToken(text, tokenStart, i - 1, Token.OPERATOR, newStartOffset + tokenStart);
            } else {
                addToken(text, tokenStart, tokenStart, Token.IDENTIFIER, newStartOffset + tokenStart);
                i++;
            }
        }

        addNullToken();
        return firstToken;
    }

    private static void putAll(TokenMap tokenMap, String[] words, int tokenType) {
        for (String word : words) {
            tokenMap.put(word, tokenType);
        }
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    private static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static boolean isSeparator(char c) {
        return "{}()[],:;.".indexOf(c) >= 0;
    }

    private static boolean isOperator(char c) {
        return "+-*=<>!&|/%".indexOf(c) >= 0;
    }

    private static int scanQuotedString(char[] array, int index, int end, char quote) {
        boolean escaped = false;
        while (index < end) {
            char c = array[index++];
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == quote) {
                break;
            }
        }
        return index;
    }
}
