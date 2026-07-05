package io.casehub.platform.api.subscription;

/**
 * Notification target type.
 */
public enum TargetType {
    /**
     * Literal userId — direct delivery, no expansion.
     */
    USER,

    /**
     * Group name — expanded via {@code GroupMembershipProvider.membersOf()} at dispatch time.
     * Covers roles, teams, and application-level groups.
     */
    GROUP,

    /**
     * POJO field name — resolved at dispatch time via MethodHandle.
     */
    EVENT_FIELD
}
