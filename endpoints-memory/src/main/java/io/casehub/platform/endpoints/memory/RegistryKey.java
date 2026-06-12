package io.casehub.platform.endpoints.memory;

/**
 * Composite key for the in-memory endpoint store.
 *
 * <p>Uses {@code pathValue} (the raw string from {@link io.casehub.platform.api.path.Path#value()})
 * rather than the {@link io.casehub.platform.api.path.Path} record itself. {@code Path}
 * equality depends on both {@code value} and {@code segments}, and segments are derived
 * from a configurable parser separator. Using the string value directly gives stable
 * identity independent of parser configuration.
 */
record RegistryKey(String pathValue, String tenancyId) {}
