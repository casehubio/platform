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
    EVENT_FIELD,

    /**
     * Users watching the entity involved in the event — expanded via
     * {@code EntityWatcherProvider.watchersOf(entityType, entityId, tenancyId)} at dispatch time.
     */
    ENTITY_WATCHERS,

    /**
     * Agent target — resolved to {@link TargetKind#NON_USER}. No suppression, fire-and-forget
     * delivery via CDI event.
     */
    AGENT,

    /**
     * System/operational target — resolved to {@link TargetKind#NON_USER}. For operational alerts,
     * dashboards, and monitoring systems.
     */
    SYSTEM
}
