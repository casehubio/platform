package io.casehub.platform.api.subscription;

import java.util.Objects;

/**
 * Target for notification delivery.
 *
 * <p>Three target types:
 * <ul>
 *   <li>{@link TargetType#USER} — {@code id} is a literal userId. No expansion.</li>
 *   <li>{@link TargetType#GROUP} — {@code id} is a group name. Expanded via
 *       {@code GroupMembershipProvider.membersOf(groupName)} at dispatch time.
 *       Covers roles, teams, and application-level groups.</li>
 *   <li>{@link TargetType#EVENT_FIELD} — {@code id} is a POJO property name
 *       (e.g., {@code "assigneeId"}). Resolved at dispatch time via MethodHandle.
 *       Solves "notify the assignee."</li>
 * </ul>
 *
 * @param type target type
 * @param id   type-specific identifier (userId, group name, or POJO field name)
 */
public record NotificationTarget(
        TargetType type,
        String id
) {
    public NotificationTarget {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
    }
}
