package io.casehub.platform.api.notification.settings;

import java.util.List;
import java.util.Optional;

/**
 * SPI for storing mute rules and snooze state.
 *
 * <p>Mute rules and snooze are both per-user notification suppression, queried
 * together in the same pipeline step. Different data shapes (multiple mutes vs
 * single snooze) but same domain.
 *
 * <p>No reactive variant — blocking on managed executor is acceptable. Future
 * reactive addition via default methods per {@code spi-evolution-default-methods}
 * protocol.
 */
public interface SuppressionStore {

    // ===== Mute rules =====

    /**
     * Add a mute rule.
     *
     * @param input mute rule input
     * @return created mute rule (with generated id and createdAt)
     */
    MuteRule addMute(MuteRuleInput input);

    /**
     * Get all active (non-expired) mute rules for a user.
     *
     * @param userId    user identifier
     * @param tenancyId tenant isolation
     * @return list of active mute rules (may be empty)
     */
    List<MuteRule> activeMutes(String userId, String tenancyId);

    /**
     * Remove a mute rule.
     *
     * @param muteId    mute rule identifier
     * @param userId    user identifier (ownership check)
     * @param tenancyId tenant isolation
     * @return true if removed, false if not found or not owned by user
     */
    boolean removeMute(String muteId, String userId, String tenancyId);

    // ===== Snooze =====

    /**
     * Activate snooze. Replaces any existing snooze for the user.
     *
     * @param input snooze input
     * @return created/updated snooze record
     */
    Snooze activateSnooze(SnoozeInput input);

    /**
     * Get active snooze for a user.
     *
     * @param userId    user identifier
     * @param tenancyId tenant isolation
     * @return active snooze if present and not expired, empty otherwise
     */
    Optional<Snooze> activeSnooze(String userId, String tenancyId);

    /**
     * Cancel snooze.
     *
     * @param userId    user identifier
     * @param tenancyId tenant isolation
     * @return true if cancelled, false if no active snooze
     */
    boolean cancelSnooze(String userId, String tenancyId);
}
