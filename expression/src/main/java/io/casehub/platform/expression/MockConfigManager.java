package io.casehub.platform.expression;

import io.casehub.platform.api.expression.ConfigManager;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * {@code @DefaultBean} config-backed {@link ConfigManager}.
 *
 * <p>Delegates to SmallRye Config (MicroProfile Config API) — application.properties,
 * env vars, and any registered ConfigSources. This is the appropriate default for
 * Quarkus deployments; no separate implementation is needed unless a non-Quarkus
 * config backend is required.
 *
 * <h3>Kubernetes integration</h3>
 *
 * <p>Add {@code quarkus-kubernetes-config} to the consumer application classpath and
 * configure it to read Kubernetes ConfigMaps:
 *
 * <pre>{@code
 * # application.properties (%prod profile)
 * %prod.quarkus.kubernetes-config.enabled=true
 * %prod.quarkus.kubernetes-config.config-maps=casehub-config
 * }</pre>
 *
 * <p>ConfigMap entries are injected into SmallRye Config and resolved by
 * {@link #configMap(String)} automatically. The {@code configMap()} method sweeps
 * properties with the {@code {configMapName}.} prefix, so ConfigMap keys should
 * use dotted names:
 *
 * <pre>{@code
 * apiVersion: v1
 * kind: ConfigMap
 * metadata:
 *   name: casehub-config
 * data:
 *   app-config.timeout: "5000"
 *   app-config.retries: "3"
 *   app-config.database.host: "postgres.default.svc.cluster.local"
 * }</pre>
 *
 * <p>The extension is disabled by default, so dev and test environments are not
 * affected. See the
 * <a href="https://quarkus.io/guides/kubernetes-config">Quarkus Kubernetes Config guide</a>.
 *
 * <p>Displaced automatically when a non-default {@code @ApplicationScoped} {@link ConfigManager}
 * is on the classpath.
 */
@DefaultBean
@ApplicationScoped
public class MockConfigManager implements ConfigManager {

    private final ConfigManagerCore delegate;

    public MockConfigManager() {
        this.delegate = new ConfigManagerCore(new SmallRyePropertySource());
    }

    @Override
    public <T> Optional<T> config(String propName, Class<T> propClass) {
        return delegate.config(propName, propClass);
    }

    @Override
    public <T> Collection<T> multiConfig(String propName, Class<T> propClass) {
        return delegate.multiConfig(propName, propClass);
    }

    @Override
    public Iterable<String> names() {
        return delegate.names();
    }

    @Override
    public Map<String, Object> configMap(String configMapName) {
        return delegate.configMap(configMapName);
    }
}
