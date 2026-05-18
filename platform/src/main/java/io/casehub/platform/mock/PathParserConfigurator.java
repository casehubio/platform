package io.casehub.platform.mock;

import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.path.PathParser;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.annotation.PostConstruct;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Registers the platform-configured path separator as the default {@link PathParser}.
 * Runs at application startup before any beans use {@link Path#parse(String)}.
 *
 * <p>Configure via: {@code casehub.platform.path.separator=/} (default: {@code /})
 *
 * <p>This config is installation-wide and must NOT go through {@code PreferenceProvider} —
 * that would create a circular dependency since {@code SettingsScope} contains a {@code Path}.
 */
@Startup
@ApplicationScoped
public class PathParserConfigurator {

    @ConfigProperty(name = "casehub.platform.path.separator", defaultValue = "/")
    String separator;

    @PostConstruct
    void configure() {
        Path.setDefaultParser(PathParser.of(separator));
    }
}
