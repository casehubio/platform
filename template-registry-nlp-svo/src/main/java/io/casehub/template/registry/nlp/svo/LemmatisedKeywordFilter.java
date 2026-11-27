package io.casehub.template.registry.nlp.svo;

import io.casehub.template.registry.AnnotatedToken;
import io.casehub.template.registry.NlpTokenizer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class LemmatisedKeywordFilter {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{[^}]+}");
    private static final Pattern OPTIONAL_SEGMENT = Pattern.compile("\\[[^\\]]*\\]");

    private LemmatisedKeywordFilter() {}

    public static Set<String> extract(String template, NlpTokenizer tokenizer,
                                       Set<String> stopWords) {
        String stripped = OPTIONAL_SEGMENT.matcher(template).replaceAll(" ");
        stripped = VAR_PATTERN.matcher(stripped).replaceAll(" ");
        String cleaned = stripped.trim();
        if (cleaned.isEmpty()) {
            return Set.of();
        }

        List<AnnotatedToken> tokens = tokenizer.tokenize(cleaned);
        Set<String> keywords = new LinkedHashSet<>();
        for (AnnotatedToken token : tokens) {
            String lemma = token.lemma().toLowerCase();
            if (!stopWords.contains(lemma) && !lemma.isBlank()) {
                keywords.add(lemma);
            }
        }
        return Set.copyOf(keywords);
    }

    public static double affinity(Set<String> keywords, List<AnnotatedToken> inputTokens,
                                   Set<String> stopWords) {
        if (keywords.isEmpty()) return 0.0;
        Set<String> inputLemmas = new LinkedHashSet<>();
        for (AnnotatedToken token : inputTokens) {
            inputLemmas.add(token.lemma().toLowerCase());
        }
        long matches = keywords.stream().filter(inputLemmas::contains).count();
        return (double) matches / keywords.size();
    }

    public static double affinity(Set<String> keywords, List<AnnotatedToken> inputTokens,
                                  Map<String, Set<String>> synonyms) {
        if (keywords.isEmpty()) {return 0.0;}
        Set<String> inputLemmas = new LinkedHashSet<>();
        for (AnnotatedToken token : inputTokens) {
            inputLemmas.add(token.lemma().toLowerCase());
        }
        long matches = 0;
        for (String keyword : keywords) {
            if (inputLemmas.contains(keyword)) {
                matches++;
            } else {
                Set<String> syns = synonyms.get(keyword);
                if (syns != null) {
                    for (String syn : syns) {
                        if (inputLemmas.contains(syn)) {
                            matches++;
                            break;
                        }
                    }
                }
            }
        }
        return (double) matches / keywords.size();
    }
}
