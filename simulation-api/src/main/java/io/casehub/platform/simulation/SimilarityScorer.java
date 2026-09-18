package io.casehub.platform.simulation;

@FunctionalInterface
public interface SimilarityScorer<I> {

    double score(I query, I candidate);
}
