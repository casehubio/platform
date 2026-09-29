package io.casehub.platform.expression.spring;

import io.casehub.platform.expression.PropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;

import java.util.*;

class EnvironmentPropertySource implements PropertySource {

    private final Environment environment;
    private final ConfigurableEnvironment configurableEnv;

    EnvironmentPropertySource(Environment environment) {
        this.environment = environment;
        this.configurableEnv = (environment instanceof ConfigurableEnvironment ce) ? ce : null;
    }

    @Override
    public Optional<String> getProperty(String name) {
        return Optional.ofNullable(environment.getProperty(name));
    }

    @Override
    public Iterable<String> getPropertyNames() {
        if (configurableEnv == null) return List.of();
        Set<String> names = new LinkedHashSet<>();
        for (var ps : configurableEnv.getPropertySources()) {
            if (ps instanceof EnumerablePropertySource<?> eps) {
                Collections.addAll(names, eps.getPropertyNames());
            }
        }
        return names;
    }
}
