package io.casehub.platform.api.identity;

import java.util.Locale;

/**
 * Classifies identities as human, agent, or system.
 *
 * <p>Planned rename to {@code PrincipalType} — see
 * <a href="https://github.com/casehubio/platform/issues/272">#272</a>.
 */
public enum ActorType {
    HUMAN,
    AGENT,
    SYSTEM;

    public String prefix() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ActorType fromPrefix(String prefix) {
        for (ActorType t : values()) {
            if (t.name().equalsIgnoreCase(prefix)) return t;
        }
        throw new IllegalArgumentException("Unknown principal type: " + prefix);
    }
}
