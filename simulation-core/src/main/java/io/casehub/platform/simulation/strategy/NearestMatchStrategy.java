package io.casehub.platform.simulation.strategy;

import io.casehub.platform.simulation.InvocationRecord;
import io.casehub.platform.simulation.SimilarityScorer;
import io.casehub.platform.simulation.SimulationCorpus;
import io.casehub.platform.simulation.SimulationNoMatchException;
import io.casehub.platform.simulation.SimulationStrategy;

import java.util.Comparator;

public final class NearestMatchStrategy<I, O> implements SimulationStrategy<I, O> {

    private final SimulationCorpus<I, O> corpus;
    private final String qualifiedName;
    private final SimilarityScorer<I> scorer;
    private final double threshold;

    public NearestMatchStrategy(final SimulationCorpus<I, O> corpus,
                                final String qualifiedName,
                                final SimilarityScorer<I> scorer,
                                final double threshold) {
        this.corpus = corpus;
        this.qualifiedName = qualifiedName;
        this.scorer = scorer;
        this.threshold = threshold;
    }

    @Override
    public O resolve(final I input) {
        return corpus.list(qualifiedName).stream()
                .map(r -> new ScoredMatch<>(r, scorer.score(input, r.input())))
                .filter(m -> m.score() >= threshold)
                .max(Comparator.comparingDouble(ScoredMatch::score))
                .map(m -> m.record().output())
                .orElseThrow(() -> new SimulationNoMatchException(qualifiedName, threshold));
    }

    @Override
    public boolean canResolve(final I input) {
        return corpus.list(qualifiedName).stream()
                .anyMatch(r -> scorer.score(input, r.input()) >= threshold);
    }

    private record ScoredMatch<I, O>(InvocationRecord<I, O> record, double score) {}
}
