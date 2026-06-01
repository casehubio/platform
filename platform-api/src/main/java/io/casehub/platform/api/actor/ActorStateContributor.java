package io.casehub.platform.api.actor;

/**
 * SPI for contributing to a unified actor state view.
 *
 * <p>Implementations must collect all results before calling any accumulator method
 * (atomic contribution contract). If contribute() throws, the aggregator records
 * the failure and excludes this source — no partial data reaches the accumulator.
 *
 * <p>CDI discovery: annotate implementations {@code @ApplicationScoped}.
 * The aggregator collects via {@code @Inject @Any Instance<ActorStateContributor>}.
 */
public interface ActorStateContributor {

    /** Stable name for this source — appears in the response {@code sources} and {@code sourceWarnings} fields. */
    String sourceName();

    /**
     * Contribute actor state data to the accumulator.
     *
     * @param actorId     the actor identity string — must be the same string across all backends
     * @param accumulator the thread-safe accumulator collecting this session's state
     */
    void contribute(String actorId, ActorStateAccumulator accumulator);
}
