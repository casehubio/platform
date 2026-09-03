package io.casehub.platform.api.identity;

import java.util.Objects;

public record PrincipalId(ActorType type, String id) implements Identity {

    public PrincipalId {
        Objects.requireNonNull(type, "type");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }

    public static PrincipalId parse(String value) {
        Objects.requireNonNull(value, "value");
        int colon = value.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException(
                    "Invalid principal format — expected 'type:id', got: " + value);
        }
        ActorType type = ActorType.fromPrefix(value.substring(0, colon));
        String id = value.substring(colon + 1);
        return new PrincipalId(type, id);
    }

    public static PrincipalId human(String id) {
        return new PrincipalId(ActorType.HUMAN, id);
    }

    public static PrincipalId agent(String id) {
        return new PrincipalId(ActorType.AGENT, id);
    }

    public static PrincipalId system(String id) {
        return new PrincipalId(ActorType.SYSTEM, id);
    }

    @Override
    public String value() {
        return type.prefix() + ":" + id;
    }

    @Override
    public String toString() {
        return value();
    }
}
