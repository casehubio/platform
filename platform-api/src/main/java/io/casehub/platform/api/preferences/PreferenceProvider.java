package io.casehub.platform.api.preferences;

public interface PreferenceProvider {

    /**
     * Resolves preferences for the given scope, applying full ancestor-chain inheritance.
     *
     * <p>The resolution walk is: root scope ({@code ""}) → each intermediate ancestor →
     * the target scope. Child values override parent values. Root scope is always the
     * lowest-priority base — a value stored at root is visible to every resolution,
     * regardless of how specific the target scope is.
     *
     * <p><strong>Implementor contract:</strong> the ancestor chain must explicitly include
     * root scope ({@code Path.root().value() = ""}). {@link io.casehub.platform.api.path.Path#parent()}
     * returns {@code null} for single-segment paths, not root — so walking only via
     * {@code parent()} silently excludes root from the chain. Always prepend root when the
     * target path has depth {@literal >} 0.
     */
    Preferences resolve(SettingsScope scope);
}
