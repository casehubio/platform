package io.casehub.platform.api.actor;

import java.time.Instant;
import java.util.UUID;

/**
 * Visitor interface for assembling actor state data from independent backends.
 *
 * <p>All parameters use only {@code java.util.*}, {@code java.time.*}, and {@code java.util.UUID} —
 * no domain types cross the platform-api boundary.
 *
 * <p>Thread-safety: implementations must be thread-safe — contributors run concurrently.
 */
public interface ActorStateAccumulator {

    /**
     * Set the global trust score for this actor.
     *
     * @param score the global trust score, or {@code null} when no score has been computed yet
     *              (distinct from {@code 0.0}, which would mean zero trust)
     */
    void trustScore(Double score);

    /**
     * Set a per-capability trust score for this actor.
     *
     * @param capability non-null capability tag (e.g. {@code "sar-drafting"})
     * @param score      the trust score for this capability
     */
    void capabilityScore(String capability, double score);

    /**
     * An active WorkItem assigned to or owned by the actor.
     *
     * @param title    may be null for externally created WorkItems
     * @param category may be null for externally created WorkItems
     * @param caseId   may be null when WorkItem was not created by the engine
     */
    void workItem(UUID id, String title, String status, String category, UUID caseId);

    /**
     * An open Commitment where this actor is the obligor.
     *
     * @param caseId may be null when channel does not follow {@code case-{caseId}/...} naming
     */
    void commitment(UUID commitmentId, UUID channelId, UUID caseId, String state, Instant expiresAt);

    /**
     * A Quartz job case UUID where this actor is currently executing.
     * Best-effort snapshot — may include cases whose work completed since the scan started.
     */
    void engineActiveCaseId(UUID caseId);
}
