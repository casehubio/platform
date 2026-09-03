package io.casehub.platform.api.identity;

import java.util.Objects;

public record ActorId(PrincipalId principalId) implements Identity {

    public ActorId {
        Objects.requireNonNull(principalId, "principalId");
    }

    public static ActorId of(PrincipalId principal) {
        return new ActorId(principal);
    }

    public static ActorId parse(String value) {
        return new ActorId(PrincipalId.parse(value));
    }

    @Override
    public ActorType type() { return principalId.type(); }

    @Override
    public String id() { return principalId.id(); }

    @Override
    public String value() { return principalId.value(); }

    @Override
    public String toString() { return value(); }
}
