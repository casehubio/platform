package io.casehub.platform.mcp;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class DomainFilter {

    private final boolean discoverMode;
    private final Set<String> allowedDomains;

    public DomainFilter(
            @ConfigProperty(name = "casehub.mcp.mode", defaultValue = "discover") String mode,
            @ConfigProperty(name = "casehub.mcp.domains") Optional<String> domains) {
        this.discoverMode = !"explicit".equalsIgnoreCase(mode);
        this.allowedDomains = domains
                .map(d -> Arrays.stream(d.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toUnmodifiableSet()))
                .orElse(Set.of());
    }

    public boolean isAllowed(String domain) {
        if (discoverMode) return true;
        if (allowedDomains.contains(domain)) return true;
        int slash = domain.lastIndexOf('/');
        if (slash >= 0) {
            return allowedDomains.contains(domain.substring(slash + 1));
        }
        return false;
    }
}
