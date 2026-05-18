package io.casehub.platform.api.preferences;

import java.util.Objects;

public record PreferenceKey<T extends Preference>(String namespace, String name) {

    public PreferenceKey {
        Objects.requireNonNull(namespace, "namespace must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }

    public String qualifiedName() { return namespace + "." + name; }
}
