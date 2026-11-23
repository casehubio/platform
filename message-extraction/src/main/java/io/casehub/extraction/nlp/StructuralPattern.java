package io.casehub.extraction.nlp;

import io.casehub.extraction.AnnotatedToken;
import io.casehub.extraction.VariableConstraint;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class StructuralPattern {

    private final List<Element> elements;

    StructuralPattern(List<Element> elements) {
        this.elements = List.copyOf(Objects.requireNonNull(elements));
    }

    Optional<MatchResult> match(List<AnnotatedToken> tokens, Map<String, VariableConstraint> constraints) {
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
                    if (token.lemma().equalsIgnoreCase(elem.lemma) || token.surface().equalsIgnoreCase(elem.surface)) {
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
                    String clauseValue = captureClause(tokens, tokenIdx, i);
                    if (!clauseValue.isBlank()) {
                        fields.put(elem.variableName, clauseValue);
                        matched++;
                    }
                    tokenIdx = tokens.size();
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

    private String resolveVariableValue(AnnotatedToken token, Element elem, Map<String, VariableConstraint> constraints) {
        VariableConstraint constraint = constraints.get(elem.variableName);
        if (constraint instanceof VariableConstraint.EnumConstraint ec) {
            if (ec.values().stream().anyMatch(v -> v.equalsIgnoreCase(token.lemma()))) {
                return token.lemma();
            }
            return null;
        }
        return token.surface();
    }

    private String captureClause(List<AnnotatedToken> tokens, int startIdx, int elementIdx) {
        Element nextLiteral = null;
        for (int j = elementIdx + 1; j < elements.size(); j++) {
            if (elements.get(j).kind == ElementKind.LITERAL) {
                nextLiteral = elements.get(j);
                break;
            }
        }

        StringBuilder clause = new StringBuilder();
        for (int t = startIdx; t < tokens.size(); t++) {
            if (nextLiteral != null && tokens.get(t).lemma().equalsIgnoreCase(nextLiteral.lemma)) {
                break;
            }
            if (!clause.isEmpty()) clause.append(' ');
            clause.append(tokens.get(t).surface());
        }
        return clause.toString();
    }

    record MatchResult(Map<String, String> fields, double score) {
        MatchResult {
            fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        }
    }

    enum ElementKind { LITERAL, VARIABLE, CLAUSE }

    record Element(ElementKind kind, String surface, String lemma, String pos,
                   String variableName, boolean optional) {}
}
