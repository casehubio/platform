package io.casehub.platform.api.memory;

import java.util.Locale;

/**
 * Reserved cross-domain attribute keys for {@link MemoryInput#attributes()}.
 *
 * <p>Platform-reserved keys use <b>kebab-case</b>. Consumer applications should
 * follow the same convention for domain-specific keys to avoid collisions.
 *
 * <p>These keys are conventions, not enforced constraints. Their purpose is to
 * allow tooling (GDPR sweeps, audit dashboards) to locate specific fact types
 * across domains without requiring domain knowledge. The <em>values</em> are
 * domain-specific and defined by each consumer application.
 */
public final class MemoryAttributeKeys {

    /**
     * Identity of the actor who produced this memory fact.
     * Use the OIDC subject (same as {@code CurrentPrincipal.actorId()}) when available.
     * This is the primary key for audit; {@link #ACTOR_ROLE} is supplementary.
     */
    public static final String ACTOR_ID = "actor-id";

    /**
     * Role of the actor within the domain (e.g. {@code "reviewer"}, {@code "investigator"},
     * {@code "clinician"}). Supplementary to {@link #ACTOR_ID}.
     */
    public static final String ACTOR_ROLE = "actor-role";

    /**
     * Outcome of the action or case from which this memory was emitted.
     * The key is reserved so tooling can locate outcome facts across domains;
     * values are domain-specific (e.g. {@code "DONE"}/{@code "DECLINE"} in devtown).
     */
    public static final String OUTCOME = "outcome";

    /**
     * Confidence score as a decimal string formatted to 4 decimal places.
     * Always use {@link #formatConfidence} to write and {@link #parseConfidence}
     * to read — do not format manually to avoid encoding variance.
     */
    public static final String CONFIDENCE = "confidence";

    private MemoryAttributeKeys() {}

    /**
     * Formats a confidence value in [0.0, 1.0] to the canonical 4-decimal-place string.
     *
     * @throws IllegalArgumentException if {@code v} is outside [0, 1]
     */
    public static String formatConfidence(double v) {
        if (v < 0 || v > 1)
            throw new IllegalArgumentException("confidence must be in [0,1], got: " + v);
        return String.format(Locale.ROOT, "%.4f", v);
    }

    /**
     * Parses a confidence string previously written by {@link #formatConfidence}.
     */
    public static double parseConfidence(String s) {
        return Double.parseDouble(s);
    }
}
