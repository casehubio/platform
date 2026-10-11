package io.casehub.platform.api.registry;

import java.util.Objects;

public record RegistryEvent(
        EventKind kind,
        RegistryEntry entry,
        Relationship relationship
) {
    public enum EventKind {
        REGISTERED, DEREGISTERED, HEALTH_CHANGED,
        HEARTBEAT_EXPIRED, LINKED, UNLINKED,
        ORPHANED, CASCADE_NOTIFIED
    }

    public RegistryEvent {
        Objects.requireNonNull(kind, "kind");
    }

    public static RegistryEvent registered(RegistryEntry entry) {
        return new RegistryEvent(EventKind.REGISTERED, entry, null);
    }

    public static RegistryEvent deregistered(RegistryEntry entry) {
        return new RegistryEvent(EventKind.DEREGISTERED, entry, null);
    }

    public static RegistryEvent healthChanged(RegistryEntry entry) {
        return new RegistryEvent(EventKind.HEALTH_CHANGED, entry, null);
    }

    public static RegistryEvent heartbeatExpired(RegistryEntry entry) {
        return new RegistryEvent(EventKind.HEARTBEAT_EXPIRED, entry, null);
    }

    public static RegistryEvent linked(Relationship rel) {
        return new RegistryEvent(EventKind.LINKED, null, rel);
    }

    public static RegistryEvent unlinked(Relationship rel) {
        return new RegistryEvent(EventKind.UNLINKED, null, rel);
    }

    public static RegistryEvent orphaned(RegistryEntry entry) {
        return new RegistryEvent(EventKind.ORPHANED, entry, null);
    }

    public static RegistryEvent cascadeNotified(RegistryEntry entry, Relationship rel) {
        return new RegistryEvent(EventKind.CASCADE_NOTIFIED, entry, rel);
    }
}
