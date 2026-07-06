package io.casehub.platform.notification.dispatch;

import io.casehub.platform.api.notification.settings.MuteRule;
import io.casehub.platform.api.notification.settings.MuteScope;
import io.casehub.platform.api.notification.settings.QuietHours;
import io.casehub.platform.api.notification.settings.Snooze;
import io.casehub.platform.api.notification.settings.SuppressionResult;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Pure function that evaluates suppression state for a notification.
 *
 * <p>No injected dependencies — receives pre-fetched data and performs no queries.
 * Independently testable.
 *
 * <p>Evaluates three independent conditions:
 * <ul>
 *   <li><strong>Muted:</strong> matches mute rules against notification metadata</li>
 *   <li><strong>Snoozed:</strong> checks active snooze state</li>
 *   <li><strong>Quiet hours:</strong> checks if current time falls within configured quiet hours</li>
 * </ul>
 */
@ApplicationScoped
public class SuppressionEvaluator {

    /**
     * Evaluate suppression state.
     *
     * @param activeMutes  pre-fetched active mute rules for the user
     * @param activeSnooze pre-fetched active snooze for the user
     * @param quietHours   user's quiet hours configuration (nullable = no quiet hours)
     * @param entityType   notification entity type
     * @param entityId     notification entity ID
     * @param category     notification category
     * @return suppression result with independent flags for mute, snooze, and quiet hours
     */
    public SuppressionResult evaluate(final List<MuteRule> activeMutes,
                                      final Optional<Snooze> activeSnooze,
                                      final QuietHours quietHours,
                                      final String entityType,
                                      final String entityId,
                                      final String category) {
        final boolean isMuted = checkMuted(activeMutes, entityType, entityId, category);
        final boolean isSnoozed = checkSnoozed(activeSnooze);
        final boolean quietHoursActive = checkQuietHours(quietHours);

        return new SuppressionResult(isMuted, isSnoozed, quietHoursActive);
    }

    /**
     * Evaluate user-level suppression state (for digest batches with heterogeneous entities).
     *
     * <p>User-level suppression checks only snooze and quiet hours; muting is entity-specific
     * and cannot be applied to a digest containing multiple entities.
     *
     * @param activeSnooze pre-fetched active snooze for the user
     * @param quietHours   user's quiet hours configuration (nullable = no quiet hours)
     * @return suppression result with isMuted always false, isSnoozed and quietHoursActive as evaluated
     */
    public SuppressionResult evaluateUserLevel(final Optional<Snooze> activeSnooze,
                                               final QuietHours quietHours) {
        return new SuppressionResult(false, checkSnoozed(activeSnooze), checkQuietHours(quietHours));
    }

    private boolean checkMuted(final List<MuteRule> activeMutes,
                               final String entityType,
                               final String entityId,
                               final String category) {
        final Instant now = Instant.now();
        for (final MuteRule rule : activeMutes) {
            // Skip expired rules
            if (rule.expiresAt() != null && now.isAfter(rule.expiresAt())) {
                continue;
            }

            if (rule.scope() == MuteScope.ENTITY) {
                // ENTITY scope: entityType + entityId must match
                if (entityType.equals(rule.entityType()) && entityId.equals(rule.scopeId())) {
                    return true;
                }
            } else if (rule.scope() == MuteScope.CATEGORY) {
                // CATEGORY scope: category must match scopeId
                if (category.equals(rule.scopeId())) {
                    // If entityType is specified on the rule, also require match
                    if (rule.entityType() == null || rule.entityType().equals(entityType)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    boolean checkSnoozed(final Optional<Snooze> activeSnooze) {
        return activeSnooze
                .filter(snooze -> Instant.now().isBefore(snooze.until()))
                .isPresent();
    }

    boolean checkQuietHours(final QuietHours quietHours) {
        if (quietHours == null) {
            return false;
        }

        final LocalTime now = LocalTime.now(quietHours.timezone());
        final LocalTime start = quietHours.start();
        final LocalTime end = quietHours.end();

        if (start.isBefore(end)) {
            // Same-day window: start <= now < end
            return !now.isBefore(start) && now.isBefore(end);
        } else {
            // Cross-midnight: start >= end means now >= start || now < end
            return !now.isBefore(start) || now.isBefore(end);
        }
    }
}
