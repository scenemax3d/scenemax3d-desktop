package com.scenemax.desktop;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.folding.Fold;
import org.fife.ui.rsyntaxtextarea.folding.FoldParser;
import org.fife.ui.rsyntaxtextarea.folding.FoldType;

import javax.swing.text.BadLocationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SceneMaxFoldParser implements FoldParser {
    private static final String SCOPE_BRACE = "brace";
    private static final String SCOPE_DO = "do";

    @Override
    public List<Fold> getFolds(RSyntaxTextArea textArea) {
        List<Fold> folds = new ArrayList<>();
        Fold currentFold = null;
        List<String> scopeTypes = new ArrayList<>();

        try {
            for (int line = 0; line < textArea.getLineCount(); line++) {
                boolean sawEnd = false;

                for (Token token = textArea.getTokenListForLine(line);
                     token != null && token.isPaintable();
                     token = token.getNextToken()) {

                    if (!isFoldToken(token)) {
                        continue;
                    }

                    String lexeme = token.getLexeme();
                    String lower = lexeme.toLowerCase(Locale.ROOT);

                    if ("{".equals(lexeme)) {
                        currentFold = openFold(textArea, folds, scopeTypes, currentFold, token.getOffset(), SCOPE_BRACE);
                    } else if ("}".equals(lexeme)) {
                        currentFold = closeFold(folds, scopeTypes, currentFold, token.getOffset(), SCOPE_BRACE);
                    } else if ("end".equals(lower)) {
                        sawEnd = true;
                    } else if ("do".equals(lower)) {
                        if (sawEnd) {
                            currentFold = closeFold(folds, scopeTypes, currentFold, token.getEndOffset() - 1, SCOPE_DO);
                            sawEnd = false;
                        } else {
                            currentFold = openFold(textArea, folds, scopeTypes, currentFold, token.getOffset(), SCOPE_DO);
                        }
                    } else if ("while".equals(lower)) {
                        currentFold = closeFold(folds, scopeTypes, currentFold, token.getOffset(), SCOPE_DO);
                    } else if (!token.isCommentOrWhitespace()) {
                        sawEnd = false;
                    }
                }
            }
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }

        return folds;
    }

    private static Fold openFold(RSyntaxTextArea textArea, List<Fold> folds, List<String> scopeTypes,
                                 Fold currentFold, int offset, String scopeType)
            throws BadLocationException {
        scopeTypes.add(scopeType);
        if (currentFold == null) {
            Fold fold = new Fold(FoldType.CODE, textArea, offset);
            folds.add(fold);
            return fold;
        }
        return currentFold.createChild(FoldType.CODE, offset);
    }

    private static Fold closeFold(List<Fold> folds, List<String> scopeTypes, Fold currentFold, int offset,
                                  String expectedScopeType) throws BadLocationException {
        if (currentFold == null) {
            return null;
        }
        if (scopeTypes.isEmpty() || !expectedScopeType.equals(scopeTypes.get(scopeTypes.size() - 1))) {
            return currentFold;
        }
        scopeTypes.remove(scopeTypes.size() - 1);

        currentFold.setEndOffset(offset);
        Fold parent = currentFold.getParent();
        if (currentFold.isOnSingleLine()) {
            if (!currentFold.removeFromParent() && !folds.isEmpty()) {
                folds.remove(folds.size() - 1);
            }
        }
        return parent;
    }

    private static boolean isFoldToken(Token token) {
        if (token == null || token.isCommentOrWhitespace()) {
            return false;
        }

        int type = token.getType();
        return type != Token.LITERAL_STRING_DOUBLE_QUOTE
                && type != Token.LITERAL_CHAR
                && type != Token.ERROR_STRING_DOUBLE
                && type != Token.ERROR_CHAR;
    }
}
