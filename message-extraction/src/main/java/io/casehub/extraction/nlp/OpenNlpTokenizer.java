package io.casehub.extraction.nlp;

import io.casehub.extraction.AnnotatedToken;
import io.casehub.extraction.NlpTokenizer;
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
import java.util.List;
import java.util.Objects;

public final class OpenNlpTokenizer implements NlpTokenizer {

    private static final String TOKENIZER_MODEL  = "opennlp-en-ud-ewt-tokens-1.3-2.5.4.bin";
    private static final String POS_MODEL        = "opennlp-en-ud-ewt-pos-1.3-2.5.4.bin";
    private static final String LEMMATIZER_MODEL = "opennlp-en-ud-ewt-lemmas-1.3-2.5.4.bin";

    private volatile SharedModels sharedModels;

    @Override
    public List<AnnotatedToken> tokenize(String text) {
        Objects.requireNonNull(text, "text");
        if (text.isBlank()) {return List.of();}

        SharedModels m          = ensureModels();
        TokenizerME  tokenizer  = new TokenizerME(m.tokenizerModel);
        POSTaggerME  posTagger  = new POSTaggerME(m.posModel);
        LemmatizerME lemmatizer = new LemmatizerME(m.lemmatizerModel);

        opennlp.tools.util.Span[] tokenSpans = tokenizer.tokenizePos(text);
        String[]                  tokens     = new String[tokenSpans.length];
        for (int i = 0; i < tokenSpans.length; i++) {
            tokens[i] = tokenSpans[i].getCoveredText(text).toString();
        }

        String[] tags   = posTagger.tag(tokens);
        String[] lemmas = lemmatizer.lemmatize(tokens, tags);

        List<AnnotatedToken> result = new ArrayList<>(tokens.length);
        for (int i = 0; i < tokens.length; i++) {
            String lemma = "O".equals(lemmas[i]) ? tokens[i].toLowerCase() : lemmas[i];
            result.add(new AnnotatedToken(
                    tokens[i],
                    lemma,
                    tags[i],
                    tokenSpans[i].getStart(),
                    tokenSpans[i].getEnd()));
        }
        return Collections.unmodifiableList(result);
    }

    private SharedModels ensureModels() {
        SharedModels m = sharedModels;
        if (m != null) {return m;}
        synchronized (this) {
            m = sharedModels;
            if (m != null) {return m;}
            m            = loadModels();
            sharedModels = m;
            return m;
        }
    }

    private static SharedModels loadModels() {
        TokenizerModel  tokenizerModel  = loadModel(TOKENIZER_MODEL, TokenizerModel.class);
        POSModel        posModel        = loadModel(POS_MODEL, POSModel.class);
        LemmatizerModel lemmatizerModel = loadModel(LEMMATIZER_MODEL, LemmatizerModel.class);
        return new SharedModels(tokenizerModel, posModel, lemmatizerModel);
    }

    private static <T> T loadModel(String path, Class<T> modelClass) {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (is == null) {throw new IllegalStateException("OpenNLP model not found on classpath: " + path);}
            try {
                return modelClass.getConstructor(InputStream.class).newInstance(is);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to load OpenNLP model: " + path, e);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read OpenNLP model: " + path, e);
        }
    }

    private record SharedModels(TokenizerModel tokenizerModel, POSModel posModel, LemmatizerModel lemmatizerModel) {}
}
