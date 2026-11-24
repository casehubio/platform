package io.casehub.template.registry.nlp;

import io.casehub.template.registry.AnnotatedToken;
import io.casehub.template.registry.NlpTokenizer;
import io.casehub.yaml.core.resolver.VariableConstraint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StructuralMatcher {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private final List<Element> elements;

    private StructuralMatcher(List<Element> elements) {
        this.elements = List.copyOf(elements);
    }

    public static StructuralMatcher compile(String template, NlpTokenizer tokenizer) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(tokenizer, "tokenizer");

        List<TextSegment> segments = parseTextSegments(template);
        List<Element> elements = new ArrayList<>();

        for (TextSegment segment : segments) {
            compileSegment(segment.text, segment.optional, tokenizer, elements);
        }

        return new StructuralMatcher(elements);
    }

    private static void compileSegment(String text, boolean optional,
                                        NlpTokenizer tokenizer,
                                        List<Element> elements) {
        Matcher varMatcher = VAR_PATTERN.matcher(text);
        int lastEnd = 0;

        while (varMatcher.find()) {
            if (varMatcher.start() > lastEnd) {
                String literal = text.substring(lastEnd, varMatcher.start()).trim();
                if (!literal.isEmpty()) {
                    List<AnnotatedToken> literalTokens = tokenizer.tokenize(literal);
                    for (AnnotatedToken token : literalTokens) {
                        elements.add(new Element(
                            ElementKind.LITERAL, token.surface(), token.lemma(),
                            token.pos(), null, optional));
                    }
                }
            }

            String varName = varMatcher.group(1);
            elements.add(new Element(
                ElementKind.CLAUSE, null, null, null, varName, optional));

            lastEnd = varMatcher.end();
        }

        if (lastEnd < text.length()) {
            String trailing = text.substring(lastEnd).trim();
            if (!trailing.isEmpty()) {
                List<AnnotatedToken> trailingTokens = tokenizer.tokenize(trailing);
                for (AnnotatedToken token : trailingTokens) {
                    elements.add(new Element(
                        ElementKind.LITERAL, token.surface(), token.lemma(),
                        token.pos(), null, optional));
                }
            }
        }
    }

    private static List<TextSegment> parseTextSegments(String template) {
        List<TextSegment> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inOptional = false;
        int i = 0;

        while (i < template.length()) {
            char c = template.charAt(i);
            if (c == '\\' && i + 1 < template.length()) {
                char next = template.charAt(i + 1);
                if (next == '[' || next == ']') {
                    current.append(next);
                    i += 2;
                    continue;
                }
            }
            if (c == '[') {
                if (current.length() > 0) {
                    segments.add(new TextSegment(current.toString(), false));
                    current.setLength(0);
                }
                inOptional = true;
                i++;
            } else if (c == ']') {
                segments.add(new TextSegment(current.toString(), true));
                current.setLength(0);
                inOptional = false;
                i++;
            } else {
                current.append(c);
                i++;
            }
        }

        if (current.length() > 0) {
            segments.add(new TextSegment(current.toString(), inOptional));
        }

        return segments;
    }

    private record TextSegment(String text, boolean optional) {}

    public Optional<MatchResult> match(List<AnnotatedToken> tokens,
                                        Map<String, VariableConstraint> constraints) {
        Map<String, String> fields = new LinkedHashMap<>();
        int tokenIdx = 0;
        int matched = 0;

        for (int i = 0; i < elements.size(); i++) {
            Element elem = elements.get(i);
            if (tokenIdx >= tokens.size()) {
                if (elem.optional) continue;
                break;
            }

            AnnotatedToken token = tokens.get(tokenIdx);

            switch (elem.kind) {
                case LITERAL -> {
                    if (token.lemma().equalsIgnoreCase(elem.lemma)
                        || token.surface().equalsIgnoreCase(elem.surface)) {
                        matched++;
                        tokenIdx++;
                    } else if (!elem.optional) {
                        return Optional.empty();
                    }
                }
                case VARIABLE -> {
                    String value = resolveVariableValue(token, elem, constraints);
                    if (value != null) {
                        fields.put(elem.variableName, value);
                        matched++;
                        tokenIdx++;
                    } else if (!elem.optional) {
                        return Optional.empty();
                    }
                }
                case CLAUSE -> {
                    ClauseCapture capture = captureClause(tokens, tokenIdx, i, constraints, elem.variableName);
                    if (capture.value != null && !capture.value.isBlank()) {
                        fields.put(elem.variableName, capture.value);
                        matched++;
                    } else if (!elem.optional) {
                        return Optional.empty();
                    }
                    tokenIdx = capture.endIdx;
                }
            }
        }

        if (matched == 0) return Optional.empty();

        long requiredCount = elements.stream().filter(e -> !e.optional).count();
        if (requiredCount == 0) return Optional.empty();
        double score = Math.min((double) matched / requiredCount, 1.0);
        if (score <= 0.0) return Optional.empty();

        return Optional.of(new MatchResult(fields, score));
    }

    public List<Element> elements() {
        return elements;
    }

    private String resolveVariableValue(AnnotatedToken token, Element elem,
                                         Map<String, VariableConstraint> constraints) {
        VariableConstraint constraint = constraints.get(elem.variableName);
        if (constraint instanceof VariableConstraint.EnumConstraint ec) {
            if (ec.values().stream().anyMatch(v -> v.equalsIgnoreCase(token.lemma()))) {
                return token.lemma();
            }
            return null;
        }
        return token.surface();
    }

    private ClauseCapture captureClause(List<AnnotatedToken> tokens, int startIdx,
                                         int elementIdx,
                                         Map<String, VariableConstraint> constraints,
                                         String variableName) {
        Element nextLiteral = null;
        for (int j = elementIdx + 1; j < elements.size(); j++) {
            if (elements.get(j).kind == ElementKind.LITERAL) {
                nextLiteral = elements.get(j);
                break;
            }
        }

        StringBuilder clause = new StringBuilder();
        int endIdx = startIdx;
        for (int t = startIdx; t < tokens.size(); t++) {
            if (nextLiteral != null
                && tokens.get(t).lemma().equalsIgnoreCase(nextLiteral.lemma)) {
                break;
            }
            if (!clause.isEmpty()) clause.append(' ');
            clause.append(tokens.get(t).surface());
            endIdx = t + 1;
        }

        String value = clause.toString();
        VariableConstraint constraint = constraints.get(variableName);
        if (constraint instanceof VariableConstraint.EnumConstraint ec) {
            AnnotatedToken firstToken = tokens.get(startIdx);
            if (ec.values().stream().anyMatch(v -> v.equalsIgnoreCase(firstToken.lemma()))) {
                return new ClauseCapture(firstToken.lemma(), endIdx);
            }
            return new ClauseCapture(null, endIdx);
        }

        return new ClauseCapture(value, endIdx);
    }

    private record ClauseCapture(String value, int endIdx) {}

    public record MatchResult(Map<String, String> fields, double score) {
        public MatchResult {
            fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        }
    }

    public enum ElementKind { LITERAL, VARIABLE, CLAUSE }

    public record Element(
        ElementKind kind,
        String surface,
        String lemma,
        String pos,
        String variableName,
        boolean optional
    ) {}
}
