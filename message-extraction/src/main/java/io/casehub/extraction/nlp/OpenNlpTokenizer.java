package io.casehub.extraction.nlp;

import io.casehub.extraction.AnnotatedToken;
import io.casehub.extraction.NlpTokenizer;
import opennlp.tools.lemmatizer.DictionaryLemmatizer;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class OpenNlpTokenizer implements NlpTokenizer {

    private volatile SharedModels sharedModels;

    @Override
    public List<AnnotatedToken> tokenize(String text) {
        Objects.requireNonNull(text, "text");
        if (text.isBlank()) return List.of();

        SharedModels m = ensureModels();
        TokenizerME tokenizer = new TokenizerME(m.tokenizerModel);
        POSTaggerME posTagger = new POSTaggerME(m.posModel);

        opennlp.tools.util.Span[] tokenSpans = tokenizer.tokenizePos(text);
        String[] tokens = new String[tokenSpans.length];
        for (int i = 0; i < tokenSpans.length; i++) {
            tokens[i] = tokenSpans[i].getCoveredText(text).toString();
        }

        String[] tags = posTagger.tag(tokens);

        List<AnnotatedToken> result = new ArrayList<>(tokens.length);
        for (int i = 0; i < tokens.length; i++) {
            String lemma = resolveLemma(m.lemmatizer, tokens[i], tags[i]);
            result.add(new AnnotatedToken(
                    tokens[i],
                    lemma,
                    tags[i],
                    tokenSpans[i].getStart(),
                    tokenSpans[i].getEnd()));
        }
        return Collections.unmodifiableList(result);
    }

    private static final Map<String, List<String>> UD_TO_PTB = Map.of(
            "VERB", List.of("VB", "VBD", "VBG", "VBN", "VBP", "VBZ"),
            "NOUN", List.of("NN", "NNS", "NNP", "NNPS"),
            "ADJ",  List.of("JJ", "JJR", "JJS"),
            "ADV",  List.of("RB", "RBR", "RBS"),
            "DET",  List.of("DT", "PDT", "WDT"),
            "PRON", List.of("PRP", "PRP$", "WP", "WP$"),
            "ADP",  List.of("IN"),
            "CCONJ", List.of("CC"),
            "SCONJ", List.of("IN"),
            "NUM",  List.of("CD")
    );

    private static String resolveLemma(DictionaryLemmatizer lemmatizer, String token, String udTag) {
        List<String> ptbTags = UD_TO_PTB.get(udTag);
        if (ptbTags != null) {
            for (String ptbTag : ptbTags) {
                String[] result = lemmatizer.lemmatize(new String[]{token}, new String[]{ptbTag});
                if (result.length > 0 && !"O".equals(result[0])) {
                    return result[0];
                }
            }
        }
        String[] direct = lemmatizer.lemmatize(new String[]{token}, new String[]{udTag});
        if (direct.length > 0 && !"O".equals(direct[0])) {
            return direct[0];
        }
        return token.toLowerCase();
    }

    private SharedModels ensureModels() {
        SharedModels m = sharedModels;
        if (m != null) return m;
        synchronized (this) {
            m = sharedModels;
            if (m != null) return m;
            m = loadModels();
            sharedModels = m;
            return m;
        }
    }

    private static SharedModels loadModels() {
        TokenizerModel tokenizerModel = loadModel("opennlp/models/en-token.bin", TokenizerModel.class);
        POSModel posModel = loadModel("opennlp/models/en-pos.bin", POSModel.class);
        DictionaryLemmatizer lemmatizer = loadLemmatizer("opennlp/models/en-lemmatizer.dict");
        return new SharedModels(tokenizerModel, posModel, lemmatizer);
    }

    private static <T> T loadModel(String path, Class<T> modelClass) {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("OpenNLP model not found on classpath: " + path);
            try {
                return modelClass.getConstructor(InputStream.class).newInstance(is);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to load OpenNLP model: " + path, e);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read OpenNLP model: " + path, e);
        }
    }

    private static DictionaryLemmatizer loadLemmatizer(String path) {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("OpenNLP lemmatizer dict not found on classpath: " + path);
            return new DictionaryLemmatizer(is);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read OpenNLP lemmatizer dict: " + path, e);
        }
    }

    private record SharedModels(TokenizerModel tokenizerModel, POSModel posModel, DictionaryLemmatizer lemmatizer) {}
}
