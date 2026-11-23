package io.casehub.extraction;

import java.util.Objects;

public record AnnotatedToken(String surface, String lemma, String pos, int start, int end) {
    public AnnotatedToken {
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(lemma, "lemma");
        Objects.requireNonNull(pos, "pos");
    }
}
