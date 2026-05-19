package io.casehub.platform.api.preferences;

import java.util.Objects;
import java.util.function.Function;

/**
 * Identifies a preference and carries its default value and string parser.
 *
 * <p><strong>Equality contract:</strong> {@code PreferenceKey} is a record whose fourth component
 * is a {@code Function}. Java records use all components for {@code equals}/{@code hashCode}, but
 * {@code Function} instances have only identity equality — two separately-created keys with the
 * same namespace and name will therefore <em>not</em> be {@code equals()}. The authoritative
 * equality check is {@link #qualifiedName()} comparison. Do not use {@code PreferenceKey} objects
 * directly as {@code Map} keys; use {@code qualifiedName()} as the key instead.
 */
public record PreferenceKey<T extends Preference>(String namespace, String name, T defaultValue, Function<String, T> parser) {

    public PreferenceKey {
        Objects.requireNonNull(namespace, "namespace must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(defaultValue, "defaultValue must not be null");
        Objects.requireNonNull(parser, "parser must not be null");
    }

    public String qualifiedName() { return namespace + "." + name; }

    /**
     * Converts a raw string value from a config source into a typed preference instance.
     * The caller is responsible for any {@code ${VAR}} interpolation before invoking this method;
     * the parser performs type conversion only.
     */
    public T parse(String raw) {
        Objects.requireNonNull(raw, "raw value must not be null");
        return parser.apply(raw);
    }
}
