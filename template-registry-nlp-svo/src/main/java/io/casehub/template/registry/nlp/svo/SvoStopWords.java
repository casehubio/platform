package io.casehub.template.registry.nlp.svo;

import java.util.Map;
import java.util.Set;

public final class SvoStopWords {

    private static final Map<String, Set<String>> STOP_WORDS = Map.of(
        "en", Set.of(
            "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
            "i", "you", "he", "she", "it", "we", "they", "me", "my", "your",
            "this", "that", "these", "those", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "as", "or", "and", "but", "not",
            "do", "does", "did", "have", "has", "had", "will", "would",
            "can", "could", "shall", "should", "may", "might"),
        "fr", Set.of(
            "le", "la", "les", "un", "une", "des", "de", "du", "au", "aux",
            "je", "tu", "il", "elle", "nous", "vous", "ils", "elles", "me", "te", "se",
            "ce", "cette", "ces", "mon", "ton", "son", "ma", "ta", "sa",
            "et", "ou", "mais", "donc", "ni", "car", "que", "qui", "quoi",
            "ne", "pas", "plus", "dans", "sur", "sous", "avec", "pour", "par",
            "en", "y", "est", "sont", "a", "ont", "être", "avoir", "faire"),
        "es", Set.of(
            "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "al",
            "yo", "tú", "él", "ella", "nosotros", "ellos", "ellas", "me", "te", "se",
            "este", "esta", "estos", "estas", "ese", "esa", "mi", "tu", "su",
            "y", "o", "pero", "sino", "que", "como", "cuando",
            "no", "en", "con", "por", "para", "sin", "sobre",
            "es", "son", "ha", "han", "ser", "estar", "haber", "hacer"),
        "it", Set.of(
            "il", "lo", "la", "i", "gli", "le", "un", "uno", "una", "di", "del", "dello",
            "io", "tu", "lui", "lei", "noi", "voi", "loro", "mi", "ti", "si", "ci", "vi",
            "questo", "questa", "questi", "queste", "quello", "quella", "mio", "tuo", "suo",
            "e", "o", "ma", "che", "come", "quando", "perché",
            "non", "in", "con", "per", "su", "tra", "fra", "da", "a",
            "è", "sono", "ha", "hanno", "essere", "avere", "fare"),
        "pt", Set.of(
            "o", "a", "os", "as", "um", "uma", "uns", "umas", "de", "do", "da", "dos", "das",
            "eu", "tu", "ele", "ela", "nós", "eles", "elas", "me", "te", "se", "nos",
            "este", "esta", "estes", "estas", "esse", "essa", "meu", "teu", "seu",
            "e", "ou", "mas", "que", "como", "quando",
            "não", "em", "com", "por", "para", "sem", "sobre",
            "é", "são", "tem", "têm", "ser", "estar", "ter", "fazer")
    );

    private SvoStopWords() {}

    public static Set<String> forLanguage(String languageTag) {
        Set<String> words = STOP_WORDS.get(languageTag);
        if (words == null) {
            throw new UnsupportedOperationException(
                    "No stop words for language '" + languageTag
                    + "'. Supported: " + STOP_WORDS.keySet());
        }
        return words;
    }
}
