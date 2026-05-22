package io.casehub.platform.api.identity;

public final class ActorTypeResolver {

    private ActorTypeResolver() {}

    /**
     * Resolves the {@link ActorType} from an actorId string.
     *
     * <p>Resolution rules (in priority order):
     * <ol>
     *   <li>null or blank → {@code SYSTEM}
     *   <li>{@code "system"} or {@code "system:*"} → {@code SYSTEM}
     *   <li>{@code "agent:*"} (prefix, not the bare role) → {@code AGENT}
     *   <li>Versioned persona format {@code word:word@version} (e.g. {@code "claude:analyst@v1"}) → {@code AGENT}
     *   <li>A2A role {@code "user"} → {@code HUMAN}
     *   <li>A2A role {@code "agent"} → {@code AGENT}
     *   <li>Everything else → {@code HUMAN}
     * </ol>
     */
    public static ActorType resolve(String actorId) {
        if (actorId == null || actorId.isBlank()) return ActorType.SYSTEM;
        if (actorId.equals("system") || actorId.startsWith("system:")) return ActorType.SYSTEM;
        if (actorId.startsWith("agent:")) return ActorType.AGENT;
        if (actorId.matches("[\\w-]+:[\\w-]+@[\\w.]+")) return ActorType.AGENT;
        if (actorId.equals("user")) return ActorType.HUMAN;
        if (actorId.equals("agent")) return ActorType.AGENT;
        return ActorType.HUMAN;
    }
}
