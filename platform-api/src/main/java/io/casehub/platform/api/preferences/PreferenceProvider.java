package io.casehub.platform.api.preferences;

public interface PreferenceProvider {

    /**
     * Resolves preferences for the given scope.
     * Implementations must apply parent-scope inheritance before returning —
     * if a key is not set at the exact scope, walk up {@code scope.scope().parent()}
     * until a value is found or the root is exhausted.
     */
    Preferences resolve(SettingsScope scope);
}
