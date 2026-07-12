package io.casehub.platform.api.datasource;

import java.util.Optional;

/**
 * Registry for named {@link Marshaller} instances.
 *
 * <p>Implementations must populate during bean initialization ({@code @PostConstruct}),
 * not during {@code @Observes StartupEvent}. The JPA DataSourceRegistry reconciles
 * persisted descriptors at StartupEvent and calls {@link #resolve(String)} for each
 * marshallerKey (fail-fast). If this registry also populates at StartupEvent, ordering
 * is nondeterministic and reconciliation may fail with spurious errors.
 */
public interface MarshallerRegistry {

    void register(String key, Marshaller<?, ?> marshaller);

    Optional<Marshaller<?, ?>> resolve(String key);
}
