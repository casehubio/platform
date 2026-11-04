package io.casehub.yaml.core.annotation;

import java.util.ArrayList;
import java.util.List;

public final class AnnotationParser {

    private AnnotationParser() {}

    public static Annotation parse(String line) {
        if (line == null) return null;
        String trimmed = line.stripLeading();
        if (!trimmed.startsWith("#@")) return null;

        String content = trimmed.substring(2);
        int parenStart = content.indexOf('(');
        if (parenStart < 0) {
            String name = content.trim();
            if (name.isEmpty() || !isValidName(name)) return null;
            return new Annotation(name, List.of());
        }

        String name = content.substring(0, parenStart).trim();
        if (name.isEmpty() || !isValidName(name)) return null;

        int parenEnd = content.lastIndexOf(')');
        if (parenEnd <= parenStart) {
            throw new IllegalArgumentException("Unclosed parenthesis in annotation: " + line);
        }

        String argsStr = content.substring(parenStart + 1, parenEnd).trim();
        if (argsStr.isEmpty()) {
            return new Annotation(name, List.of());
        }

        List<AnnotationArg> args = parseArgs(argsStr);
        return new Annotation(name, args);
    }

    private static List<AnnotationArg> parseArgs(String argsStr) {
        List<AnnotationArg> result = new ArrayList<>();
        int i = 0;
        while (i < argsStr.length()) {
            i = skipWhitespace(argsStr, i);
            if (i >= argsStr.length()) break;

            String token;
            int tokenEnd;
            if (argsStr.charAt(i) == '"') {
                tokenEnd = findClosingQuote(argsStr, i);
                token = argsStr.substring(i + 1, tokenEnd);
                tokenEnd++;
            } else {
                tokenEnd = findTokenEnd(argsStr, i);
                token = argsStr.substring(i, tokenEnd).trim();
            }

            i = skipWhitespace(argsStr, tokenEnd);

            if (i < argsStr.length() && argsStr.charAt(i) == ':') {
                i = skipWhitespace(argsStr, i + 1);
                String value;
                int valueEnd;
                if (i < argsStr.length() && argsStr.charAt(i) == '"') {
                    valueEnd = findClosingQuote(argsStr, i);
                    value = argsStr.substring(i + 1, valueEnd);
                    valueEnd++;
                } else {
                    valueEnd = findTokenEnd(argsStr, i);
                    value = argsStr.substring(i, valueEnd).trim();
                }
                result.add(new AnnotationArg.Named(token, value));
                i = valueEnd;
            } else {
                result.add(new AnnotationArg.Positional(token));
            }

            i = skipWhitespace(argsStr, i);
            if (i < argsStr.length() && argsStr.charAt(i) == ',') {
                i++;
            }
        }
        return result;
    }

    private static int findClosingQuote(String s, int openPos) {
        for (int i = openPos + 1; i < s.length(); i++) {
            if (s.charAt(i) == '\\' && i + 1 < s.length()) { i++; continue; }
            if (s.charAt(i) == '"') return i;
        }
        throw new IllegalArgumentException("Unclosed quote starting at position " + openPos);
    }

    private static int findTokenEnd(String s, int start) {
        int i = start;
        while (i < s.length() && s.charAt(i) != ',' && s.charAt(i) != ':' && s.charAt(i) != ')') {
            i++;
        }
        return i;
    }

    private static int skipWhitespace(String s, int pos) {
        while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        return pos;
    }

    private static boolean isValidName(String name) {
        if (name.isEmpty()) return false;
        char c = name.charAt(0);
        if (!Character.isLetter(c) && c != '_') return false;
        for (int i = 1; i < name.length(); i++) {
            c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-' && c != '.') return false;
        }
        return true;
    }
}
