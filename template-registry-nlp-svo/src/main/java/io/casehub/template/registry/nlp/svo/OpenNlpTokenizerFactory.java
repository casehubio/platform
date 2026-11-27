package io.casehub.template.registry.nlp.svo;

import io.casehub.template.registry.AnnotatedToken;
import io.casehub.template.registry.NlpTokenizer;
import io.casehub.template.registry.NlpTokenizerFactory;
import opennlp.tools.lemmatizer.LemmatizerME;
import opennlp.tools.lemmatizer.LemmatizerModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class OpenNlpTokenizerFactory implements NlpTokenizerFactory {

    private static final Map<String, String> TREEBANK = Map.of(
            "en", "ewt", "fr", "gsd", "es", "gsd", "it", "isdt", "pt", "bosque");

    private final Set<String> supported;
    private final ConcurrentMap<String, NlpTokenizer> cache = new ConcurrentHashMap<>();

    public OpenNlpTokenizerFactory() {
        Map<String, Boolean> available = new LinkedHashMap<>();
        for (String lang : TREEBANK.keySet()) {
            available.put(lang, modelsAvailable(lang));
        }
        this.supported = Set.copyOf(
                available.entrySet().stream()
                        .filter(Map.Entry::getValue)
                        .map(Map.Entry::getKey)
                        .toList());
    }

    @Override
    public NlpTokenizer create(String languageTag) {
        Objects.requireNonNull(languageTag, "languageTag");
        if (!supported.contains(languageTag)) {
            throw new UnsupportedOperationException(
                    "Language '" + languageTag + "' is not supported. "
                    + "Available: " + supported + ". "
                    + "Add the OpenNLP model artifacts for '" + languageTag
                    + "' to the classpath.");
        }
        return cache.computeIfAbsent(languageTag, this::buildTokenizer);
    }

    @Override
    public Set<String> supportedLanguages() {
        return supported;
    }

    private boolean modelsAvailable(String lang) {
        String tb = TREEBANK.get(lang);
        if (tb == null) return false;
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return cl.getResource(modelPath(lang, tb, "tokens")) != null
                && cl.getResource(modelPath(lang, tb, "pos")) != null
                && cl.getResource(modelPath(lang, tb, "lemmas")) != null;
    }

    private static String modelPath(String lang, String treebank, String kind) {
        return "opennlp-" + lang + "-ud-" + treebank + "-" + kind + "-1.3-2.5.4.bin";
    }

    private NlpTokenizer buildTokenizer(String lang) {
        String tb = TREEBANK.get(lang);
        TokenizerModel tokModel = loadModel(modelPath(lang, tb, "tokens"), TokenizerModel.class);
        POSModel posModel = loadModel(modelPath(lang, tb, "pos"), POSModel.class);
        LemmatizerModel lemModel = loadModel(modelPath(lang, tb, "lemmas"), LemmatizerModel.class);

        return text -> {
            Objects.requireNonNull(text, "text");
            if (text.isBlank()) return List.of();

            TokenizerME tokenizer = new TokenizerME(tokModel);
            POSTaggerME posTagger = new POSTaggerME(posModel);
            LemmatizerME lemmatizer = new LemmatizerME(lemModel);

            opennlp.tools.util.Span[] spans = tokenizer.tokenizePos(text);
            String[] tokens = new String[spans.length];
            for (int i = 0; i < spans.length; i++) {
                tokens[i] = spans[i].getCoveredText(text).toString();
            }

            String[] tags = posTagger.tag(tokens);
            String[] lemmas = lemmatizer.lemmatize(tokens, tags);

            List<AnnotatedToken> result = new ArrayList<>(tokens.length);
            for (int i = 0; i < tokens.length; i++) {
                String lemma = "O".equals(lemmas[i]) ? tokens[i].toLowerCase() : lemmas[i];
                result.add(new AnnotatedToken(tokens[i], lemma, tags[i],
                        spans[i].getStart(), spans[i].getEnd()));
            }
            return Collections.unmodifiableList(result);
        };
    }

    private static <T> T loadModel(String path, Class<T> modelClass) {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("OpenNLP model not found on classpath: " + path);
            }
            try {
                return modelClass.getConstructor(InputStream.class).newInstance(is);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to load OpenNLP model: " + path, e);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read OpenNLP model: " + path, e);
        }
    }
}
