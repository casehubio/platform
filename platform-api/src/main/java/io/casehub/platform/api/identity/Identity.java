package io.casehub.platform.api.identity;

/**
 * Sealed identity hierarchy — three levels of increasing context.
 *
 * <ul>
 *   <li>{@link PrincipalId} — stable identity for ownership, permissions, and accountability</li>
 *   <li>{@link ActorId} — a principal performing an action in a specific context</li>
 *   <li>{@link ParticipantId} — an actor involved in a multi-party interaction</li>
 * </ul>
 *
 * <p>All three share the {@code type:id} string format and are convertible
 * between levels via factory methods and accessors.
 */
public sealed interface Identity permits PrincipalId, ActorId, ParticipantId {

    ActorType type();

    String id();

    String value();
}
