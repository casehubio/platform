package io.casehub.platform.api.datasource;

/**
 * Type discriminator for runtime object classification in the alpha network.
 *
 * <p>Implemented by {@link ClassObjectType} for standard Java class-based typing.
 * Extensible for custom type systems (e.g., schema-based, protocol buffers).
 *
 * <p>{@code getTypeKey()} returns an identity that must be stable, hashable, and
 * equality-comparable — used as map keys in the alpha network's type-based routing.
 *
 * @param <T> the constrained type this ObjectType recognizes
 */
public interface ObjectType<T> {

    /**
     * Returns {@code true} if {@code object} matches this type.
     *
     * <p>Implementations must return {@code false} for {@code null} objects.
     */
    boolean matches(Object object);

    /**
     * Returns a stable, hashable type identity used as a map key in the alpha network.
     *
     * <p>The returned object must have stable {@code equals()} and {@code hashCode()}
     * semantics — two {@code ObjectType} instances representing the same type must
     * return equal {@code getTypeKey()} values.
     */
    Object getTypeKey();
}
