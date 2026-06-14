package io.casehub.platform.endpoints.config;

import io.casehub.platform.api.endpoints.EndpointDescriptor;
import io.casehub.platform.api.endpoints.EndpointRegistry;
import io.casehub.platform.api.path.PathParser;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Startup
@ApplicationScoped
public class EndpointConfigLoader {

    private static final Logger LOG = Logger.getLogger(EndpointConfigLoader.class);

    @Inject
    EndpointRegistry registry;

    @ConfigProperty(name = "casehub.platform.endpoints.files")
    Optional<List<String>> endpointFiles;

    @ConfigProperty(name = "casehub.platform.path.separator", defaultValue = "/")
    String pathSeparator;

    @PostConstruct
    void load() { throw new UnsupportedOperationException("not yet implemented"); }

    static EndpointDescriptor parseDescriptor(Map<String, Object> entry, PathParser parser) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    static String interpolate(String value) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    static InputStream openStream(String fileSpec) throws Exception {
        throw new UnsupportedOperationException("not yet implemented");
    }
}
