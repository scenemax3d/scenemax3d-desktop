package com.scenemax.desktop;

final class SceneMaxScriptFormatter {
    private static final int INDENT_SIZE = 2;

    private SceneMaxScriptFormatter() {
    }

    static String format(String source) {
        if (source == null || source.isEmpty()) {
            return source == null ? "" : source;
        }

        String eol = source.contains("\r\n") ? "\r\n" : "\n";
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        boolean endsWithNewline = normalized.endsWith("\n");
        String[] lines = normalized.split("\n", -1);
        int lineCount = endsWithNewline ? lines.length - 1 : lines.length;

        StringBuilder formatted = new StringBuilder(source.length());
        int indent = 0;
        for (int i = 0; i < lineCount; i++) {
            String trimmed = lines[i].trim();
            if (!trimmed.isEmpty()) {
                BraceCounts braceCounts = countBracesOutsideStrings(trimmed);
                int leadingClosingBraces = countLeadingClosingBraces(trimmed);
                int keywordDedent = startsDoBlockTerminator(trimmed) ? 1 : 0;
                int lineIndent = Math.max(0, indent - leadingClosingBraces - keywordDedent);

                appendSpaces(formatted, lineIndent * INDENT_SIZE);
                formatted.append(trimmed);

                indent = lineIndent
                        + braceCounts.open
                        - Math.max(0, braceCounts.close - leadingClosingBraces);
                if (startsDoBlock(trimmed) && braceCounts.open == 0) {
                    indent++;
                }
                indent = Math.max(0, indent);
            }

            if (i < lineCount - 1 || endsWithNewline) {
                formatted.append(eol);
            }
        }

        return formatted.toString();
    }

    private static boolean startsDoBlock(String line) {
        String lower = line.toLowerCase();
        return lower.equals("do") || lower.startsWith("do ");
    }

    private static boolean startsDoBlockTerminator(String line) {
        String lower = line.toLowerCase();
        return lower.equals("end do")
                || lower.startsWith("end do ")
                || lower.startsWith("while ");
    }

    private static int countLeadingClosingBraces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == '}') {
            count++;
        }
        return count;
    }

    private static BraceCounts countBracesOutsideStrings(String line) {
        int open = 0;
        int close = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escaped = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if ((inSingleQuote || inDoubleQuote) && ch == '\\') {
                escaped = true;
                continue;
            }
            if (!inSingleQuote && !inDoubleQuote && ch == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
                break;
            }
            if (!inSingleQuote && ch == '"') {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }
            if (!inDoubleQuote && ch == '\'') {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (!inSingleQuote && !inDoubleQuote) {
                if (ch == '{') {
                    open++;
                } else if (ch == '}') {
                    close++;
                }
            }
        }

        return new BraceCounts(open, close);
    }

    private static void appendSpaces(StringBuilder builder, int count) {
        for (int i = 0; i < count; i++) {
            builder.append(' ');
        }
    }

    private static class BraceCounts {
        final int open;
        final int close;

        BraceCounts(int open, int close) {
            this.open = open;
            this.close = close;
        }
    }
}
