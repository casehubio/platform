package io.casehub.platform.coordination.protocol;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class TemplateKeywords {

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

    private TemplateKeywords() {}

    public static Set<String> extract(String template) {
        String stripped = VAR_PATTERN.matcher(template).replaceAll(" ");
        stripped = BRACKET_PATTERN.matcher(stripped).replaceAll(" ");
        String[] words = WHITESPACE.split(stripped.trim());
        Set<String> keywords = new LinkedHashSet<>();
        for (String word : words) {
            if (word.isEmpty()) continue;
            String lower = word.toLowerCase(Locale.ROOT);
            if (!STOP_WORDS.contains(lower)) {
                keywords.add(lower);
            }
        }
        return Set.copyOf(keywords);
    }

    public static double affinity(Set<String> keywords, String input) {
        if (keywords.isEmpty()) return 0.0;
        String[] words = WHITESPACE.split(input.trim());
        Set<String> inputWords = new LinkedHashSet<>();
        for (String word : words) {
            if (!word.isEmpty()) {
                inputWords.add(word.toLowerCase(Locale.ROOT));
            }
        }
        long matches = keywords.stream().filter(inputWords::contains).count();
        return (double) matches / keywords.size();
    }
}
