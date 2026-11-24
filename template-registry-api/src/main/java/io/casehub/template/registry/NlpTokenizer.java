package io.casehub.template.registry;

import java.util.List;

@FunctionalInterface
public interface NlpTokenizer {
    List<AnnotatedToken> tokenize(String text);
}
