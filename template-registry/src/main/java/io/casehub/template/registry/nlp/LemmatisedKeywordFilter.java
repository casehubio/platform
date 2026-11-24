package io.casehub.template.registry.nlp;

import io.casehub.template.registry.AnnotatedToken;
import io.casehub.template.registry.NlpTokenizer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class LemmatisedKeywordFilter {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{[^}]+}");
    private static final Pattern BRACKET_PATTERN = Pattern.compile("[\\[\\]]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final Set<String> STOP_WORDS = Set.of(
        "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
        "i", "you", "he", "she", "it", "we", "they", "me", "my", "your",
        "this", "that", "these", "those", "in", "on", "at", "to", "for",
        "of", "with", "by", "from", "as", "or", "and", "but", "not",
        "do", "does", "did", "have", "has", "had", "will", "would",
        "can", "could", "shall", "should", "may", "might"
    );

    private LemmatisedKeywordFilter() {}

    public static Set<String> extract(String template, NlpTokenizer tokenizer) {
        String stripped = VAR_PATTERN.matcher(template).replaceAll(" ");
        stripped = BRACKET_PATTERN.matcher(stripped).replaceAll(" ");
        String cleaned = stripped.trim();
        if (cleaned.isEmpty()) {
            return Set.of();
        }

        List<AnnotatedToken> tokens = tokenizer.tokenize(cleaned);
        Set<String> keywords = new LinkedHashSet<>();
        for (AnnotatedToken token : tokens) {
            String lemma = token.lemma().toLowerCase();
            if (!STOP_WORDS.contains(lemma) && !lemma.isBlank()) {
                keywords.add(lemma);
            }
        }
        return Set.copyOf(keywords);
    }

    public static Set<String> extractRaw(String template) {
        String stripped = VAR_PATTERN.matcher(template).replaceAll(" ");
        stripped = BRACKET_PATTERN.matcher(stripped).replaceAll(" ");
        String[] words = WHITESPACE.split(stripped.trim());
        Set<String> keywords = new LinkedHashSet<>();
        for (String word : words) {
            if (word.isEmpty()) continue;
            String lower = word.toLowerCase();
            if (!STOP_WORDS.contains(lower)) {
                keywords.add(lower);
            }
        }
        return Set.copyOf(keywords);
    }

    public static double affinity(Set<String> keywords, List<AnnotatedToken> inputTokens) {
        if (keywords.isEmpty()) return 0.0;
        Set<String> inputLemmas = new LinkedHashSet<>();
        for (AnnotatedToken token : inputTokens) {
            inputLemmas.add(token.lemma().toLowerCase());
        }
        long matches = keywords.stream().filter(inputLemmas::contains).count();
        return (double) matches / keywords.size();
    }
}
