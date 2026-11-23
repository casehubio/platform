package io.casehub.extraction.nlp;

import io.casehub.extraction.MessageMatch;
import io.casehub.extraction.MessageMatcher;
import io.casehub.extraction.MessageSchema;
import io.casehub.extraction.MessageType;
import io.casehub.extraction.NlpTokenizer;
import io.casehub.extraction.RegexMessageMatcher;
import io.casehub.extraction.AnnotatedToken;
import io.casehub.extraction.VariableConstraint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TwoTierMessageMatcher implements MessageMatcher {

    private static final double DEFAULT_THRESHOLD = 0.5;
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private final RegexMessageMatcher regexMatcher;
    private final NlpTokenizer nlpTokenizer;
    private final List<CompiledNlpType> nlpTypes;
    private final double threshold;

    public TwoTierMessageMatcher(MessageSchema schema, NlpTokenizer tokenizer) {
        this(schema, tokenizer, DEFAULT_THRESHOLD);
    }

    public TwoTierMessageMatcher(MessageSchema schema, NlpTokenizer tokenizer, double threshold) {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(tokenizer, "tokenizer");
        this.regexMatcher = new RegexMessageMatcher(schema);
        this.nlpTokenizer = tokenizer;
        this.threshold = threshold;

        List<CompiledNlpType> types = new ArrayList<>();
        for (MessageType type : schema.types()) {
            List<StructuralPattern> patterns = new ArrayList<>();
            for (String template : type.templates()) {
                patterns.add(compileStructuralPattern(template, tokenizer));
            }
            types.add(new CompiledNlpType(type.name(), patterns, type.variables()));
        }
        this.nlpTypes = Collections.unmodifiableList(types);
    }

    @Override
    public MessageMatch match(String input) {
        Objects.requireNonNull(input, "input");

        MessageMatch regexResult = regexMatcher.match(input);
        if (regexResult instanceof MessageMatch.Matched) {
            return regexResult;
        }

        List<AnnotatedToken> tokens = nlpTokenizer.tokenize(input);

        MessageMatch.Matched best = null;
        for (CompiledNlpType type : nlpTypes) {
            for (int i = 0; i < type.patterns.size(); i++) {
                Optional<StructuralPattern.MatchResult> result = type.patterns.get(i).match(tokens, type.variables);
                if (result.isPresent() && result.get().score() >= threshold) {
                    var candidate = new MessageMatch.Matched(type.name, i, result.get().fields(), result.get().score());
                    if (best == null || candidate.score() > best.score()) {
                        best = candidate;
                    }
                }
            }
        }

        return best != null ? best : new MessageMatch.Unmatched(input);
    }

    private StructuralPattern compileStructuralPattern(String template, NlpTokenizer tokenizer) {
        String withoutOptional = template.replaceAll("\\[([^]]*)]", "$1");

        List<StructuralPattern.Element> elements = new ArrayList<>();

        String[] parts = withoutOptional.split("\\$\\{[^}]+}");
        List<String> vars = new ArrayList<>();
        Matcher vm = VAR_PATTERN.matcher(withoutOptional);
        while (vm.find()) vars.add(vm.group(1));

        for (int p = 0; p < parts.length; p++) {
            String part = parts[p].trim();
            if (!part.isEmpty()) {
                List<AnnotatedToken> partTokens = tokenizer.tokenize(part);
                for (AnnotatedToken pt : partTokens) {
                    if (!"PLACEHOLDER".equals(pt.surface())) {
                        elements.add(new StructuralPattern.Element(
                                StructuralPattern.ElementKind.LITERAL,
                                pt.surface(), pt.lemma(), pt.pos(), null, false));
                    }
                }
            }
            if (p < vars.size()) {
                String varName = vars.get(p);
                boolean isLastVar = (p == vars.size() - 1) && (p + 1 >= parts.length || parts[p + 1].trim().isEmpty());
                StructuralPattern.ElementKind kind = isLastVar
                        ? StructuralPattern.ElementKind.CLAUSE
                        : StructuralPattern.ElementKind.VARIABLE;
                elements.add(new StructuralPattern.Element(kind, null, null, null, varName, false));
            }
        }

        return new StructuralPattern(elements);
    }

    private record CompiledNlpType(String name, List<StructuralPattern> patterns,
                                   Map<String, VariableConstraint> variables) {}
}
