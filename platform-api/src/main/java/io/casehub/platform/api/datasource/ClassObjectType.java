package io.casehub.platform.api.datasource;

import java.util.Objects;

/**
 * Java class-based {@link ObjectType} implementation.
 *
 * <p>Uses {@link Class#isInstance(Object)} for matching. Supports subtype matching
 * (e.g., {@code ClassObjectType<Number>} matches {@link Integer} and {@link Double}).
 *
 * <p>{@link #getTypeKey()} returns the {@link Class} instance — stable and hashable.
 */
public final class ClassObjectType<T> implements ObjectType<T> {

    private final Class<T> clazz;

    public ClassObjectType(Class<T> clazz) {
        this.clazz = Objects.requireNonNull(clazz, "clazz");
    }

    @Override
    public boolean matches(Object object) {
        return object != null && clazz.isInstance(object);
    }

    @Override
    public Object getTypeKey() {
        return clazz;
    }

    /**
     * Returns the {@link Class} instance this type represents.
     */
    public Class<T> getObjectClass() {
        return clazz;
    }
}
