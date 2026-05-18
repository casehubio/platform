package io.casehub.platform.mock;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @DefaultBean mock for dev/test. Reads actor identity from config.
 *
 * Note: intentionally @ApplicationScoped (not @RequestScoped). No request context
 * exists in dev/test mode. Real implementations must be @RequestScoped, backed by
 * the active Quarkus SecurityIdentity.
 *
 * To simulate unauthenticated: casehub.platform.principal.actorId=anonymous
 *
 * Note: groups uses Optional<List<String>> because SmallRye Config rejects an empty
 * string ("") as a List value — it treats it as null and throws NoSuchElementException.
 * Optional absorbs the absent/empty case cleanly.
 */
@ApplicationScoped
@DefaultBean
public class MockCurrentPrincipal implements CurrentPrincipal {

    @ConfigProperty(name = "casehub.platform.principal.actorId", defaultValue = "system")
    String actorId;

    @ConfigProperty(name = "casehub.platform.principal.groups")
    Optional<List<String>> groups;

    @Override
    public String actorId() { return actorId; }

    @Override
    public Set<String> groups() {
        return groups.orElse(List.of()).stream()
            .filter(Predicate.not(String::isBlank))
            .collect(Collectors.toUnmodifiableSet());
    }
}
