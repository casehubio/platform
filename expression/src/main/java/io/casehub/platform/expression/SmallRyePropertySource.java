package io.casehub.platform.expression;

import org.eclipse.microprofile.config.ConfigProvider;

import java.util.Optional;

class SmallRyePropertySource implements PropertySource {

    @Override
    public Optional<String> getProperty(String name) {
        return ConfigProvider.getConfig().getOptionalValue(name, String.class);
    }

    @Override
    public Iterable<String> getPropertyNames() {
        return ConfigProvider.getConfig().getPropertyNames();
    }
}
