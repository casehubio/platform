package io.casehub.platform.acl.worker;

import io.casehub.platform.api.acl.ResourceId;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import java.util.Optional;

@DefaultBean
@ApplicationScoped
public class FailClosedWorkerScopeExtractor implements WorkerScopeExtractor {

    private static final ResourceId NEVER_MATCH =
        new ResourceId("__deny__", "__no_scope_extractor_configured__");

    @Override
    public Optional<ResourceId> extractResourceId(ContainerRequestContext ctx) {
        return Optional.of(NEVER_MATCH);
    }
}
