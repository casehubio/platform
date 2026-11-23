package io.casehub.extraction;

import java.util.List;

public interface NlpTokenizer {
    List<AnnotatedToken> tokenize(String text);
}
