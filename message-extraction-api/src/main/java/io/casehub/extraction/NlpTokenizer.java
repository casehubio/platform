package io.casehub.extraction;

import java.util.List;

@FunctionalInterface
public interface NlpTokenizer {
    List<AnnotatedToken> tokenize(String text);
}
