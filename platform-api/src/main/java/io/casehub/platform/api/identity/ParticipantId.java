package io.casehub.platform.api.identity;

import java.util.Objects;

public record ParticipantId(ActorId actorId) implements Identity {

    public ParticipantId {
        Objects.requireNonNull(actorId, "actorId");
    }

    public static ParticipantId of(ActorId actor) {
        return new ParticipantId(actor);
    }

    public static ParticipantId parse(String value) {
        return new ParticipantId(ActorId.parse(value));
    }

    public PrincipalId principalId() {
        return actorId.principalId();
    }

    @Override
    public ActorType type() { return actorId.type(); }

    @Override
    public String id() { return actorId.id(); }

    @Override
    public String value() { return actorId.value(); }

    @Override
    public String toString() { return value(); }
}
