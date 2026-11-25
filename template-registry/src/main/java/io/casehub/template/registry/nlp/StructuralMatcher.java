package io.casehub.template.registry.nlp;

import io.casehub.template.registry.AnnotatedToken;
import io.casehub.template.registry.ElementKind;
import io.casehub.template.registry.ElementTrace;
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
    private final NlpTokenizer tokenizer;

    private StructuralMatcher(List<Element> elements, NlpTokenizer tokenizer) {
        this.elements = List.copyOf(elements);
        this.tokenizer = tokenizer;
    }

    public static StructuralMatcher compile(String template, NlpTokenizer tokenizer) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(tokenizer, "tokenizer");

        List<TextSegment> segments = parseTextSegments(template);
        List<Element>     elements = new ArrayList<>();

        for (TextSegment segment : segments) {
            compileSegment(segment.text, segment.optional, tokenizer, elements);
        }

        validateNoAdjacentClauses(elements);
        return new StructuralMatcher(elements, tokenizer);
    }

    private static void validateNoAdjacentClauses(List<Element> elements) {
        for (int i = 1; i < elements.size(); i++) {
            if (elements.get(i).kind == ElementKind.CLAUSE
                && elements.get(i - 1).kind == ElementKind.CLAUSE) {
                throw new IllegalArgumentException(
                        "Template has adjacent variables '${" + elements.get(i - 1).variableName
                        + "}' and '${" + elements.get(i).variableName
                        + "}' with no literal between them — the structural matcher needs"
                        + " a literal anchor to know where one variable ends and the next begins");
            }
        }
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

    public TraceableMatchResult matchWithTrace(List<AnnotatedToken> tokens,
                                               Map<String, VariableConstraint> constraints) {
        List<ElementTrace>  traces   = new ArrayList<>();
        Map<String, String> fields   = new LinkedHashMap<>();
        int                 tokenIdx = 0;
        int                 matched  = 0;
        boolean             failed   = false;

        for (int i = 0; i < elements.size(); i++) {
            Element elem = elements.get(i);
            if (tokenIdx >= tokens.size()) {
                traces.add(new ElementTrace(elem.kind(), elementExpected(elem), null, false));
                continue;
            }

            AnnotatedToken token = tokens.get(tokenIdx);

            switch (elem.kind()) {
                case LITERAL -> {
                    boolean match = token.lemma().equalsIgnoreCase(elem.lemma())
                                    || token.surface().equalsIgnoreCase(elem.surface());
                    traces.add(new ElementTrace(elem.kind(), elem.lemma(), token.lemma(), match));
                    if (match) {
                        matched++;
                        tokenIdx++;
                    } else if (!elem.optional()) {
                        failed = true;
                    }
                }
                case VARIABLE -> {
                    String  value = resolveVariableValue(token, elem, constraints);
                    boolean match = value != null;
                    traces.add(new ElementTrace(elem.kind(), elem.variableName(),
                                                match ? value : token.surface(), match));
                    if (match) {
                        fields.put(elem.variableName(), value);
                        matched++;
                        tokenIdx++;
                    } else if (!elem.optional()) {
                        failed = true;
                    }
                }
                case CLAUSE -> {
                    ClauseCapture capture = captureClause(tokens, tokenIdx, i, constraints, elem.variableName());
                    boolean       match   = capture.value() != null && !capture.value().isBlank();
                    traces.add(new ElementTrace(elem.kind(), elem.variableName(),
                                                capture.value(), match));
                    if (match) {
                        fields.put(elem.variableName(), capture.value());
                        matched++;
                    }
                    tokenIdx = capture.endIdx();
                    if (!match && !elem.optional()) {
                        failed = true;
                    }
                }
            }
        }

        if (failed || matched == 0) {
            return new TraceableMatchResult(Optional.empty(), traces);
        }
        long requiredCount = elements.stream().filter(e -> !e.optional()).count();
        if (requiredCount == 0) {
            return new TraceableMatchResult(Optional.empty(), traces);
        }
        double score = Math.min((double) matched / requiredCount, 1.0);
        if (score <= 0.0) {
            return new TraceableMatchResult(Optional.empty(), traces);
        }
        return new TraceableMatchResult(
                Optional.of(new MatchResult(fields, score)), traces);
    }

    private String elementExpected(Element elem) {
        return elem.variableName() != null ? elem.variableName() : elem.lemma();
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
            StringBuilder lemmaClause = new StringBuilder();
            for (int t = startIdx; t < endIdx; t++) {
                if (!lemmaClause.isEmpty()) lemmaClause.append(' ');
                lemmaClause.append(tokens.get(t).lemma());
            }
            String capturedLemma = lemmaClause.toString();
            for (String enumValue : ec.values()) {
                List<AnnotatedToken> enumTokens = tokenizer.tokenize(enumValue);
                StringBuilder enumLemma = new StringBuilder();
                for (AnnotatedToken et : enumTokens) {
                    if (!enumLemma.isEmpty()) enumLemma.append(' ');
                    enumLemma.append(et.lemma());
                }
                if (capturedLemma.equalsIgnoreCase(enumLemma.toString())) {
                    return new ClauseCapture(enumValue, endIdx);
                }
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

    public record TraceableMatchResult(
            Optional<MatchResult> result,
            List<ElementTrace> elements
    ) {
        public TraceableMatchResult {
            Objects.requireNonNull(elements, "elements");
            elements = List.copyOf(elements);
        }
    }


    public record Element(
        ElementKind kind,
        String surface,
        String lemma,
        String pos,
        String variableName,
        boolean optional
    ) {}
}
